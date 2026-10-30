package dev.idefix.app.domain.repository

import dev.idefix.app.domain.model.RecentFile
import dev.idefix.app.domain.model.RecentSearch
import dev.idefix.app.domain.model.SearchMode
import dev.idefix.app.domain.model.VaultFile
import kotlinx.coroutines.flow.Flow

interface RecentFilesRepository {
    val recentFiles: Flow<List<RecentFile>>
    val recentSearches: Flow<List<RecentSearch>>

    suspend fun recordOpen(file: VaultFile, fileId: Long, line: Int?, cursorOffset: Int)

    suspend fun positionFor(fileId: Long): RecentFile?

    suspend fun updatePosition(fileId: Long, cursorLine: Int, cursorOffset: Int, scrollOffset: Int, matchLine: Int?)

    suspend fun recordSearch(query: String, mode: SearchMode)

    suspend fun clearHistory()

    suspend fun removeStale()

    fun setLimit(limit: Int)
}
