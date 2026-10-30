package dev.idefix.app.data.index

import androidx.room.Transactor
import androidx.room.useWriterConnection
import dev.idefix.app.data.database.FileEntity
import dev.idefix.app.data.database.IdefixDatabase
import dev.idefix.app.domain.model.IndexPhase
import dev.idefix.app.domain.model.IndexResult
import dev.idefix.app.domain.model.IndexState
import dev.idefix.app.domain.model.IndexSummary
import dev.idefix.app.domain.model.ScanProgress
import dev.idefix.app.domain.model.VaultFile
import dev.idefix.app.domain.repository.IndexRepository
import dev.idefix.app.domain.repository.VaultRepository
import dev.idefix.app.sync.SyncCoordinator
import dev.idefix.app.util.Hashing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

private class IndexCancelledException : Exception()

class DefaultIndexRepository(
    private val database: IdefixDatabase,
    private val vaultRepository: VaultRepository,
    private val syncCoordinator: SyncCoordinator,
) : IndexRepository {

    private val _state = MutableStateFlow<IndexState>(IndexState.Idle)
    override val state: StateFlow<IndexState> = _state.asStateFlow()

    private val runLock = Mutex()
    private val cancelRequested = AtomicBoolean(false)

    private val fileDao get() = database.fileDao()
    private val ftsDao get() = database.ftsDao()

    private suspend fun <T> writeTransaction(block: suspend () -> T): T =
        database.useWriterConnection { transactor ->
            transactor.withTransaction(Transactor.SQLiteTransactionType.IMMEDIATE) { block() }
        }

    override suspend fun rebuild(includeHidden: Boolean): IndexResult =
        runIndex(includeHidden, force = true)

    override suspend fun reconcile(includeHidden: Boolean): IndexResult =
        runIndex(includeHidden, force = false)

    private suspend fun runIndex(includeHidden: Boolean, force: Boolean): IndexResult =
        syncCoordinator.track {
            withContext(Dispatchers.IO) {
                if (!runLock.tryLock()) {
                    return@withContext IndexResult(0, 0, listOf("Indexing is already running"))
                }
                try {
                    cancelRequested.set(false)
                    _state.value = IndexState.Running(IndexPhase.SCANNING)
                    val scan = vaultRepository.scanMarkdownFiles(includeHidden) { progress: ScanProgress ->
                        if (cancelRequested.get()) throw IndexCancelledException()
                        val current = _state.value
                        val processed = if (current is IndexState.Running) current.processed else 0
                        _state.value = IndexState.Running(
                            phase = IndexPhase.SCANNING,
                            discovered = progress.discovered,
                            processed = processed,
                            currentPath = progress.currentPath,
                        )
                    }
                    val failures = scan.failures.toMutableList()
                    if (cancelRequested.get()) {
                        _state.value = IndexState.Cancelled
                        return@withContext IndexResult(0, 0, failures, cancelled = true)
                    }

                    val existing = fileDao.getAll()
                    val byUri = existing.associateBy { it.documentUri }
                    val byPath = existing.associateBy { it.relativePath }
                    val ftsCount = ftsDao.count()
                    val healthyFts = ftsCount >= existing.size
                    val seen = HashSet<Long>()
                    val freshIds = ArrayList<Long>()
                    var indexed = 0
                    var processed = 0

                    _state.value = IndexState.Running(
                        phase = IndexPhase.READING,
                        discovered = scan.files.size,
                    )

                    for (file in scan.files) {
                        currentCoroutineContext().ensureActive()
                        if (cancelRequested.get()) {
                            _state.value = IndexState.Cancelled
                            return@withContext IndexResult(indexed, 0, failures, cancelled = true)
                        }
                        val old = byUri[file.uri] ?: byPath[file.relativePath]
                        val unchanged = !force && healthyFts && old != null &&
                            old.indexStatus == "ok" &&
                            old.lastModified == file.lastModified &&
                            old.size == file.size &&
                            file.lastModified != null
                        if (unchanged && old != null) {
                            seen += old.id
                            processed++
                        } else {
                            try {
                                val content = vaultRepository.readText(file)
                                val fingerprint = Hashing.sha256(content)
                                val now = System.currentTimeMillis()
                                val id = writeTransaction {
                                    val entity = FileEntity(
                                        id = old?.id ?: 0,
                                        documentUri = file.uri,
                                        documentId = file.documentId,
                                        relativePath = file.relativePath,
                                        filename = file.displayName,
                                        lastModified = file.lastModified,
                                        size = file.size,
                                        fingerprint = fingerprint,
                                        indexStatus = "ok",
                                        lastIndexedAt = now,
                                    )
                                    val rowId = if (old == null) {
                                        fileDao.insert(entity)
                                    } else {
                                        fileDao.update(entity)
                                        old.id
                                    }
                                    ftsDao.delete(rowId)
                                    ftsDao.insert(rowId, file.displayName, file.relativePath, content)
                                    rowId
                                }
                                seen += id
                                if (old == null) freshIds += id
                                indexed++
                                processed++
                            } catch (e: Exception) {
                                currentCoroutineContext().ensureActive()
                                failures += "${file.relativePath}: ${e.message ?: e.javaClass.simpleName}"
                                if (old != null) {
                                    seen += old.id
                                    fileDao.update(old.copy(indexStatus = "error"))
                                }
                                processed++
                            }
                        }
                        _state.value = IndexState.Running(
                            phase = IndexPhase.READING,
                            discovered = scan.files.size,
                            processed = processed,
                            indexed = indexed,
                            failures = failures.size,
                            currentPath = file.relativePath,
                        )
                    }

                    _state.value = IndexState.Running(
                        phase = IndexPhase.FINALISING,
                        discovered = scan.files.size,
                        processed = processed,
                        indexed = indexed,
                        failures = failures.size,
                    )

                    var removed = 0
                    writeTransaction {
                        var all = fileDao.getAll()
                        var stale = all.filter { it.id !in seen }
                        val fresh = all.filter { it.id in freshIds }
                        val renames = RenameMatcher.detectRenames(
                            stale.map {
                                RenameMatcher.Candidate(it.id, it.relativePath, it.documentUri, it.fingerprint, it.size)
                            },
                            fresh.map {
                                RenameMatcher.Candidate(it.id, it.relativePath, it.documentUri, it.fingerprint, it.size)
                            },
                        )
                        if (renames.isNotEmpty()) {
                            for (rename in renames) {
                                val old = stale.firstOrNull { it.id == rename.oldId } ?: continue
                                ftsDao.delete(rename.oldId)
                                ftsDao.copyRow(rename.newId, rename.oldId)
                                ftsDao.delete(rename.newId)
                                fileDao.deleteById(rename.newId)
                                fileDao.update(
                                    old.copy(
                                        documentUri = rename.documentUri,
                                        relativePath = rename.relativePath,
                                        filename = rename.relativePath.substringAfterLast('/'),
                                    ),
                                )
                            }
                            all = fileDao.getAll()
                            stale = all.filter { it.id !in seen && it.id !in renames.map { r -> r.oldId } }
                        }
                        for (row in stale) {
                            ftsDao.delete(row.id)
                            fileDao.deleteById(row.id)
                            removed++
                        }
                        database.recentDao().removeOrphanedFiles()
                    }

                    val summary = IndexSummary(
                        fileCount = fileDao.count(),
                        lastIndexedAt = System.currentTimeMillis(),
                        failedCount = failures.size,
                    )
                    _state.value = IndexState.Ready(
                        fileCount = summary.fileCount,
                        lastIndexedAt = summary.lastIndexedAt ?: System.currentTimeMillis(),
                        failures = failures.size,
                        message = failures.firstOrNull(),
                    )
                    IndexResult(indexed = indexed, removed = removed, failures = failures)
                } catch (_: IndexCancelledException) {
                    _state.value = IndexState.Cancelled
                    IndexResult(0, 0, emptyList(), cancelled = true)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    _state.value = IndexState.Cancelled
                    throw e
                } catch (e: Exception) {
                    _state.value = IndexState.Failed(e.message ?: e.javaClass.simpleName)
                    IndexResult(0, 0, listOf(e.message ?: "Indexing failed"))
                } finally {
                    runLock.unlock()
                }
            }
        }

    override suspend fun reindexFile(file: VaultFile) = syncCoordinator.track {
        withContext(Dispatchers.IO) {
            runLock.withLock {
                val content = vaultRepository.readText(file)
                val fingerprint = Hashing.sha256(content)
                writeTransaction {
                    val old = fileDao.getByUri(file.uri) ?: fileDao.getByPath(file.relativePath)
                    if (old != null &&
                        old.indexStatus == "ok" &&
                        old.fingerprint == fingerprint &&
                        old.relativePath == file.relativePath &&
                        old.filename == file.displayName
                    ) {
                        if (old.lastModified != file.lastModified || old.size != file.size) {
                            fileDao.update(
                                old.copy(
                                    lastModified = file.lastModified,
                                    size = file.size,
                                ),
                            )
                        }
                        return@writeTransaction
                    }
                    val id = if (old == null) {
                        fileDao.insert(
                            FileEntity(
                                documentUri = file.uri,
                                documentId = file.documentId,
                                relativePath = file.relativePath,
                                filename = file.displayName,
                                lastModified = file.lastModified,
                                size = file.size,
                                fingerprint = fingerprint,
                            ),
                        )
                    } else {
                        fileDao.update(
                            old.copy(
                                documentUri = file.uri,
                                documentId = file.documentId,
                                relativePath = file.relativePath,
                                filename = file.displayName,
                                lastModified = file.lastModified,
                                size = file.size,
                                fingerprint = fingerprint,
                                indexStatus = "ok",
                                lastIndexedAt = System.currentTimeMillis(),
                            ),
                        )
                        old.id
                    }
                    ftsDao.delete(id)
                    ftsDao.insert(id, file.displayName, file.relativePath, content)
                }
            }
        }
    }

    override suspend fun removeFile(fileId: Long) = syncCoordinator.track {
        withContext(Dispatchers.IO) {
            runLock.withLock {
                writeTransaction {
                    ftsDao.delete(fileId)
                    fileDao.deleteById(fileId)
                    database.recentDao().deleteFile(fileId)
                }
            }
        }
    }

    override fun cancel() {
        cancelRequested.set(true)
        if (_state.value is IndexState.Running) {
            _state.value = IndexState.Cancelled
        }
    }

    override suspend fun summary(): IndexSummary = withContext(Dispatchers.IO) {
        IndexSummary(
            fileCount = fileDao.count(),
            lastIndexedAt = fileDao.lastIndexedAt(),
            failedCount = fileDao.failedCount(),
        )
    }

    override suspend fun databaseFileCount(): Int = withContext(Dispatchers.IO) { fileDao.count() }

    override suspend fun clearIndex() = syncCoordinator.track {
        withContext(Dispatchers.IO) {
            runLock.withLock {
                writeTransaction {
                    ftsDao.deleteAll()
                    fileDao.deleteAll()
                    database.recentDao().clearFiles()
                }
                _state.value = IndexState.Idle
            }
        }
    }
}
