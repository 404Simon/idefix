package dev.idefix.app

import dev.idefix.app.domain.model.FileSummary
import dev.idefix.app.domain.model.IndexSummary
import dev.idefix.app.domain.model.RecentFile
import dev.idefix.app.domain.model.RecentSearch
import dev.idefix.app.domain.model.SearchMode
import dev.idefix.app.domain.model.SearchOutcome
import dev.idefix.app.domain.model.SearchResult
import dev.idefix.app.domain.model.VaultFile
import dev.idefix.app.domain.repository.RecentFilesRepository
import dev.idefix.app.domain.repository.SearchRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeSearchRepository(
    var results: List<SearchResult> = emptyList(),
    var error: String? = null,
    private val fileCount: Int = results.size,
) : SearchRepository {
    var lastQuery: String? = null
    var lastMode: SearchMode? = null
    var lastPrefixOnly: Boolean? = null

    override suspend fun search(
        query: String,
        mode: SearchMode,
        caseSensitive: Boolean,
        limit: Int,
        prefixOnly: Boolean,
    ): SearchOutcome {
        lastQuery = query
        lastMode = mode
        lastPrefixOnly = prefixOnly
        return SearchOutcome(
            query = query,
            mode = mode,
            results = if (error == null) results else emptyList(),
            error = error,
        )
    }

    override suspend fun browseFiles(query: String?): List<FileSummary> = emptyList()

    override suspend fun summary(): IndexSummary = IndexSummary(fileCount, 1L, 0)

    override suspend fun fileIdForPath(relativePath: String): Long? =
        results.firstOrNull { it.relativePath == relativePath }?.fileId
}

class FakeRecentFilesRepository : RecentFilesRepository {
    private val files = MutableStateFlow<List<RecentFile>>(emptyList())
    private val searches = MutableStateFlow<List<RecentSearch>>(emptyList())
    override val recentFiles: Flow<List<RecentFile>> = files
    override val recentSearches: Flow<List<RecentSearch>> = searches

    override suspend fun recordOpen(file: VaultFile, fileId: Long, line: Int?, cursorOffset: Int) = Unit

    override suspend fun positionFor(fileId: Long): RecentFile? = files.value.firstOrNull { it.fileId == fileId }

    override suspend fun updatePosition(
        fileId: Long,
        cursorLine: Int,
        cursorOffset: Int,
        scrollOffset: Int,
        matchLine: Int?,
    ) = Unit

    override suspend fun recordSearch(query: String, mode: SearchMode) {
        searches.update { listOf(RecentSearch(query, mode, System.currentTimeMillis())) + it }
    }

    override suspend fun clearHistory() {
        files.value = emptyList()
        searches.value = emptyList()
    }

    override suspend fun removeStale() = Unit

    override fun setLimit(limit: Int) = Unit
}
