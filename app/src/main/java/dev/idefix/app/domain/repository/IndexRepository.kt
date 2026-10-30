package dev.idefix.app.domain.repository

import dev.idefix.app.domain.model.IndexResult
import dev.idefix.app.domain.model.IndexState
import dev.idefix.app.domain.model.VaultFile
import kotlinx.coroutines.flow.StateFlow

interface IndexRepository {
    val state: StateFlow<IndexState>

    suspend fun rebuild(includeHidden: Boolean): IndexResult

    suspend fun reconcile(includeHidden: Boolean): IndexResult

    suspend fun reindexFile(file: VaultFile)

    suspend fun removeFile(fileId: Long)

    fun cancel()

    suspend fun summary(): dev.idefix.app.domain.model.IndexSummary

    suspend fun databaseFileCount(): Int

    /** Drops index metadata only. Vault files are never touched. */
    suspend fun clearIndex()
}
