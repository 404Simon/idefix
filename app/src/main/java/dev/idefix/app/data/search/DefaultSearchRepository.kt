package dev.idefix.app.data.search

import android.database.sqlite.SQLiteException
import dev.idefix.app.data.database.FileDao
import dev.idefix.app.data.database.FtsDao
import dev.idefix.app.data.database.IndexedDocumentRow
import dev.idefix.app.domain.model.FileSummary
import dev.idefix.app.domain.model.IndexSummary
import dev.idefix.app.domain.model.SearchMode
import dev.idefix.app.domain.model.SearchOutcome
import dev.idefix.app.domain.model.SearchResult
import dev.idefix.app.domain.repository.SearchRepository
import dev.idefix.app.domain.search.ContentDocument
import dev.idefix.app.domain.search.ContentSearchEngine
import dev.idefix.app.domain.search.FilenameMatcher
import dev.idefix.app.domain.search.FtsQueryBuilder
import dev.idefix.app.util.MarkdownLines
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.math.max

class DefaultSearchRepository(
    private val fileDao: FileDao,
    private val ftsDao: FtsDao,
    private val resultLimit: Int = 100,
) : SearchRepository {

    override suspend fun search(
        query: String,
        mode: SearchMode,
        caseSensitive: Boolean,
        limit: Int,
        prefixOnly: Boolean,
    ): SearchOutcome = withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return@withContext SearchOutcome(query = query, mode = mode)
        }
        try {
            currentCoroutineContext().ensureActive()
            val results = when (mode) {
                SearchMode.FILENAME -> filenameSearch(trimmed, limit, prefixOnly)
                SearchMode.TEXT -> fullTextSearch(trimmed, limit)
                SearchMode.LITERAL -> contentSearch(trimmed, caseSensitive, limit, regex = false)
                SearchMode.REGEX -> contentSearch(trimmed, caseSensitive, limit, regex = true)
            }
            SearchOutcome(
                query = query,
                mode = mode,
                results = results,
                durationMs = System.currentTimeMillis() - started,
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: SQLiteException) {
            SearchOutcome(
                query = query,
                mode = mode,
                error = "Search syntax error: ${e.message ?: "invalid query"}",
                durationMs = System.currentTimeMillis() - started,
            )
        } catch (e: Exception) {
            SearchOutcome(
                query = query,
                mode = mode,
                error = e.message ?: "Search failed",
                durationMs = System.currentTimeMillis() - started,
            )
        }
    }

    private suspend fun filenameSearch(query: String, limit: Int, prefixOnly: Boolean): List<SearchResult> {
        val files = fileDao.getAll()
        val scored = ArrayList<Pair<dev.idefix.app.data.database.FileEntity, Double>>()
        for (file in files) {
            // Typing quickly cancels the previous search; abandon this loop as
            // soon as that happens so old work never delays the fresh query.
            currentCoroutineContext().ensureActive()
            if (prefixOnly && !file.filename.startsWith(query, ignoreCase = true)) continue
            val score = FilenameMatcher.score(query, file.filename, file.relativePath)
            if (score >= FilenameMatcher.MIN_SCORE) scored += file to score
        }
        scored.sortWith(
            compareByDescending<Pair<dev.idefix.app.data.database.FileEntity, Double>> { it.second }
                .thenBy { it.first.relativePath.lowercase() },
        )
        return scored.take(limit).map { (file, score) ->
            SearchResult(
                fileId = file.id,
                uri = file.documentUri,
                relativePath = file.relativePath,
                filename = file.filename,
                score = score,
                mode = SearchMode.FILENAME,
                lastModified = file.lastModified,
            )
        }
    }

    private suspend fun fullTextSearch(query: String, limit: Int): List<SearchResult> {
        val built = FtsQueryBuilder.build(query)
        if (built.error != null || built.query == null) {
            throw IllegalArgumentException(built.error ?: "Invalid search")
        }
        val hits = ftsDao.search(built.query, limit)
        if (hits.isEmpty()) return emptyList()
        val files = fileDao.getAll().associateBy { it.id }
        val results = ArrayList<SearchResult>()
        for (hit in hits) {
            currentCoroutineContext().ensureActive()
            val file = files[hit.fileId] ?: continue
            var line: Int? = null
            var snippet = hit.snippet
            if (built.plainTerms.isNotEmpty()) {
                val content = ftsDao.contentFor(hit.fileId)
                if (content != null) {
                    line = built.plainTerms.mapNotNull { term ->
                        MarkdownLines.findLine(content, term, ignoreCase = true)
                    }.minOrNull()
                    if (snippet.isBlank() && line != null) {
                        snippet = MarkdownLines.lineText(content, line)?.trim()?.take(300) ?: ""
                    }
                }
            }
            results += SearchResult(
                fileId = file.id,
                uri = file.documentUri,
                relativePath = file.relativePath,
                filename = file.filename,
                line = line,
                snippet = snippet,
                score = -hit.score,
                mode = SearchMode.TEXT,
                lastModified = file.lastModified,
            )
        }
        return results
    }

    private suspend fun contentSearch(
        query: String,
        caseSensitive: Boolean,
        limit: Int,
        regex: Boolean,
    ): List<SearchResult> {
        val results = ArrayList<SearchResult>()
        var afterId = 0L
        val pageSize = 20
        while (results.size < limit) {
            currentCoroutineContext().ensureActive()
            val page = ftsDao.contentPage(afterId, pageSize)
            if (page.isEmpty()) break
            afterId = page.last().fileId
            val documents = page.map { it.toContentDocument() }
            val outcome = if (regex) {
                ContentSearchEngine.regex(
                    documents,
                    query,
                    caseSensitive,
                    limit = limit - results.size,
                    maxHitsPerFile = 5,
                )
            } else {
                ContentSearchEngine.literal(
                    documents,
                    query,
                    caseSensitive,
                    limit = limit - results.size,
                    maxHitsPerFile = 5,
                )
            }
            if (outcome.error != null) throw IllegalArgumentException(outcome.error)
            for (hit in outcome.hits) {
                results += SearchResult(
                    fileId = hit.document.fileId,
                    uri = hit.document.uri,
                    relativePath = hit.document.relativePath,
                    filename = hit.document.filename,
                    line = hit.lineNumber,
                    snippet = hit.snippet,
                    score = hit.score,
                    mode = if (regex) SearchMode.REGEX else SearchMode.LITERAL,
                )
            }
            if (page.size < pageSize) break
        }
        return results.take(limit)
    }

    override suspend fun browseFiles(query: String?): List<FileSummary> = withContext(Dispatchers.IO) {
        val files = fileDao.getAll()
        val filtered = if (query.isNullOrBlank()) {
            files
        } else {
            files.filter {
                it.filename.contains(query, ignoreCase = true) ||
                    it.relativePath.contains(query, ignoreCase = true)
            }
        }
        filtered.map {
            FileSummary(
                fileId = it.id,
                relativePath = it.relativePath,
                filename = it.filename,
                uri = it.documentUri,
                lastModified = it.lastModified,
                size = it.size,
            )
        }
    }

    override suspend fun summary(): IndexSummary = withContext(Dispatchers.IO) {
        IndexSummary(
            fileCount = fileDao.count(),
            lastIndexedAt = fileDao.lastIndexedAt(),
            failedCount = fileDao.failedCount(),
        )
    }

    override suspend fun fileIdForPath(relativePath: String): Long? = withContext(Dispatchers.IO) {
        fileDao.getByPath(relativePath)?.id
    }

    private fun IndexedDocumentRow.toContentDocument() = ContentDocument(
        fileId = fileId,
        uri = uri,
        relativePath = relativePath,
        filename = filename,
        content = content,
    )
}
