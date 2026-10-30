package dev.idefix.app.domain.model

import android.net.Uri

/** A directory selected through the Storage Access Framework. */
data class VaultInfo(
    val uri: String,
    val displayName: String,
    val readable: Boolean = true,
)

/**
 * A Markdown document inside the vault. The URI is a SAF document URI, never a
 * filesystem path. [relativePath] is human-readable and is not assumed stable.
 */
data class VaultFile(
    val documentId: String,
    val relativePath: String,
    val displayName: String,
    val uri: String,
    val mimeType: String? = null,
    val lastModified: Long? = null,
    val size: Long? = null,
) {
    val filename: String get() = displayName
    val parentPath: String
        get() = relativePath.substringBeforeLast('/', "")
    val uriObject: Uri get() = Uri.parse(uri)
}

data class ScanProgress(
    val discovered: Int,
    val currentPath: String,
)

data class ScanResult(
    val files: List<VaultFile>,
    val failures: List<String> = emptyList(),
    val cancelled: Boolean = false,
)

data class FileSummary(
    val fileId: Long,
    val relativePath: String,
    val filename: String,
    val uri: String,
    val lastModified: Long? = null,
    val size: Long? = null,
)
