package dev.idefix.app.domain.search

import dev.idefix.app.domain.model.SNIPPET_HIGHLIGHT_END
import dev.idefix.app.domain.model.SNIPPET_HIGHLIGHT_START
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.regex.PatternSyntaxException

data class ContentDocument(
    val fileId: Long,
    val uri: String,
    val relativePath: String,
    val filename: String,
    val content: String,
)

data class ContentHit(
    val document: ContentDocument,
    val lineNumber: Int,
    val snippet: String,
    val score: Double,
)

data class ContentSearchOutcome(
    val hits: List<ContentHit> = emptyList(),
    val error: String? = null,
)

object ContentSearchEngine {

    private const val MAX_REGEX_LINE_LENGTH = 20_000

    suspend fun literal(
        documents: List<ContentDocument>,
        query: String,
        caseSensitive: Boolean,
        limit: Int = 100,
        maxHitsPerFile: Int = 5,
    ): ContentSearchOutcome {
        if (query.isEmpty()) return ContentSearchOutcome(error = "Enter a search term")
        val hits = ArrayList<ContentHit>()
        for (document in documents) {
            currentCoroutineContext().ensureActive()
            if (hits.size >= limit) break
            var lineNumber = 0
            var fileHits = 0
            for (line in document.content.lineSequence()) {
                lineNumber++
                if (fileHits >= maxHitsPerFile) break
                val index = line.indexOf(query, ignoreCase = !caseSensitive)
                if (index >= 0) {
                    hits += ContentHit(
                        document = document,
                        lineNumber = lineNumber,
                        snippet = snippetAround(line, index, query.length),
                        score = 1000.0 - lineNumber,
                    )
                    fileHits++
                }
                if (hits.size >= limit) break
            }
        }
        return ContentSearchOutcome(hits = hits)
    }

    suspend fun regex(
        documents: List<ContentDocument>,
        pattern: String,
        caseSensitive: Boolean,
        limit: Int = 100,
        maxHitsPerFile: Int = 5,
    ): ContentSearchOutcome {
        if (pattern.isEmpty()) return ContentSearchOutcome(error = "Enter a regular expression")
        val regex = try {
            Regex(pattern, if (caseSensitive) emptySet() else setOf(RegexOption.IGNORE_CASE))
        } catch (e: PatternSyntaxException) {
            return ContentSearchOutcome(error = "Invalid regex: ${e.description}")
        } catch (e: IllegalArgumentException) {
            return ContentSearchOutcome(error = "Invalid regex: ${e.message}")
        }
        val hits = ArrayList<ContentHit>()
        for (document in documents) {
            currentCoroutineContext().ensureActive()
            if (hits.size >= limit) break
            var lineNumber = 0
            var fileHits = 0
            for (fullLine in document.content.lineSequence()) {
                lineNumber++
                if (fileHits >= maxHitsPerFile) break
                // Bound pathological single-line inputs. Markdown notes are line-oriented.
                val line = if (fullLine.length > MAX_REGEX_LINE_LENGTH) {
                    fullLine.take(MAX_REGEX_LINE_LENGTH)
                } else {
                    fullLine
                }
                val match = regex.find(line) ?: continue
                hits += ContentHit(
                    document = document,
                    lineNumber = lineNumber,
                    snippet = snippetAround(line, match.range.first, match.value.length),
                    score = 1000.0 - lineNumber,
                )
                fileHits++
                if (hits.size >= limit) break
            }
        }
        return ContentSearchOutcome(hits = hits)
    }

    fun snippetAround(line: String, matchStart: Int, matchLength: Int, radius: Int = 80): String {
        val start = (matchStart - radius).coerceAtLeast(0)
        val end = (matchStart + matchLength + radius).coerceAtMost(line.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < line.length) "…" else ""
        val visible = line.substring(start, end)
        val markerStart = (matchStart - start).coerceIn(0, visible.length)
        val markerEnd = (markerStart + matchLength).coerceIn(markerStart, visible.length)
        return prefix +
            visible.substring(0, markerStart) +
            SNIPPET_HIGHLIGHT_START +
            visible.substring(markerStart, markerEnd) +
            SNIPPET_HIGHLIGHT_END +
            visible.substring(markerEnd) +
            suffix
    }

    fun stripMarkers(text: String): String =
        text.replace(SNIPPET_HIGHLIGHT_START.toString(), "").replace(SNIPPET_HIGHLIGHT_END.toString(), "")
}
