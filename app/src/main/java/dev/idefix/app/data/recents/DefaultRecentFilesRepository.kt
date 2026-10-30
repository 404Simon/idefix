package dev.idefix.app.data.recents

import dev.idefix.app.data.database.RecentDao
import dev.idefix.app.data.database.RecentFileEntity
import dev.idefix.app.data.database.RecentSearchEntity
import dev.idefix.app.domain.model.RecentFile
import dev.idefix.app.domain.model.RecentSearch
import dev.idefix.app.domain.model.SearchMode
import dev.idefix.app.domain.model.VaultFile
import dev.idefix.app.domain.repository.RecentFilesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultRecentFilesRepository(
    private val recentDao: RecentDao,
    initialLimit: Int = 50,
) : RecentFilesRepository {

    private val limit = MutableStateFlow(initialLimit.coerceIn(10, 200))

    override val recentFiles: Flow<List<RecentFile>> = limit.flatMapLatest { limitValue ->
        recentDao.observeRecentFiles(limitValue).map { rows -> rows.map { it.toModel() } }
    }

    override val recentSearches: Flow<List<RecentSearch>> = limit.flatMapLatest { limitValue ->
        recentDao.observeRecentSearches(limitValue).map { rows ->
            rows.map { RecentSearch(it.query, SearchMode.fromStorage(it.mode), it.searchedAt) }
        }
    }

    override suspend fun recordOpen(
        file: VaultFile,
        fileId: Long,
        line: Int?,
        cursorOffset: Int,
    ) = withContext(Dispatchers.IO) {
        val existing = recentDao.getFile(fileId)
        val now = System.currentTimeMillis()
        recentDao.upsertFile(
            RecentFileEntity(
                fileId = fileId,
                documentUri = file.uri,
                relativePath = file.relativePath,
                filename = file.displayName,
                openedAt = now,
                cursorLine = line ?: existing?.cursorLine ?: 1,
                cursorOffset = if (line != null) cursorOffset else existing?.cursorOffset ?: 0,
                scrollOffset = existing?.scrollOffset ?: 0,
                lastMatchLine = line ?: existing?.lastMatchLine,
            ),
        )
        recentDao.trimFiles(limit.value)
        recentDao.trimSearches(limit.value)
    }

    override suspend fun positionFor(fileId: Long): RecentFile? = withContext(Dispatchers.IO) {
        recentDao.getFile(fileId)?.toModel()
    }

    override suspend fun updatePosition(
        fileId: Long,
        cursorLine: Int,
        cursorOffset: Int,
        scrollOffset: Int,
        matchLine: Int?,
    ) = withContext(Dispatchers.IO) {
        recentDao.updatePosition(fileId, cursorLine, cursorOffset, scrollOffset, matchLine)
    }

    override suspend fun recordSearch(query: String, mode: SearchMode) = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext
        recentDao.insertSearch(
            RecentSearchEntity(
                query = query,
                mode = mode.name,
                searchedAt = System.currentTimeMillis(),
            ),
        )
        recentDao.trimSearches(limit.value)
    }

    override suspend fun clearHistory() = withContext(Dispatchers.IO) {
        recentDao.clearFiles()
        recentDao.clearSearches()
    }

    override suspend fun removeStale() = withContext(Dispatchers.IO) {
        recentDao.removeOrphanedFiles()
    }

    override fun setLimit(limit: Int) {
        this.limit.value = limit.coerceIn(10, 200)
    }

    private fun RecentFileEntity.toModel() = RecentFile(
        fileId = fileId,
        uri = documentUri,
        relativePath = relativePath,
        filename = filename,
        openedAt = openedAt,
        cursorLine = cursorLine,
        cursorOffset = cursorOffset,
        scrollOffset = scrollOffset,
        lastMatchLine = lastMatchLine,
    )
}
