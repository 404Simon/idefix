package dev.idefix.app.domain.repository

import dev.idefix.app.domain.model.ScanProgress
import dev.idefix.app.domain.model.ScanResult
import dev.idefix.app.domain.model.VaultFile
import dev.idefix.app.domain.model.VaultInfo
import kotlinx.coroutines.flow.StateFlow

interface VaultRepository {
    val vaultInfo: StateFlow<VaultInfo?>

    /** Validate an already-granted tree URI and make it the active vault. */
    suspend fun restoreVault(uri: String, displayName: String?): VaultInfo?

    /** Persist a URI returned by ACTION_OPEN_DOCUMENT_TREE and validate it. */
    suspend fun adoptVault(uri: String, displayName: String?): VaultInfo

    suspend fun clearVault()

    suspend fun validateAccess(): Boolean

    suspend fun scanMarkdownFiles(
        includeHidden: Boolean,
        onProgress: (ScanProgress) -> Unit = {},
    ): ScanResult

    suspend fun readText(file: VaultFile): String

    suspend fun writeText(file: VaultFile, content: String)

    suspend fun createMarkdownFile(
        parentRelativePath: String?,
        displayName: String,
        content: String = "",
    ): VaultFile

    suspend fun renameFile(file: VaultFile, newDisplayName: String): VaultFile

    suspend fun deleteFile(file: VaultFile)

    suspend fun resolve(relativePath: String): VaultFile?

    suspend fun refresh(file: VaultFile): VaultFile?

    suspend fun exists(file: VaultFile): Boolean
}
