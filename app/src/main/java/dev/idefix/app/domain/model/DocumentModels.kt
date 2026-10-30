package dev.idefix.app.domain.model

data class DocumentSnapshot(
    val file: VaultFile,
    val content: String,
    val fingerprint: String,
    val loadedAt: Long = System.currentTimeMillis(),
)

enum class DocumentMode { PREVIEW, SOURCE }

data class RecentFile(
    val fileId: Long,
    val uri: String,
    val relativePath: String,
    val filename: String,
    val openedAt: Long,
    val cursorLine: Int = 1,
    val cursorOffset: Int = 0,
    val scrollOffset: Int = 0,
    val lastMatchLine: Int? = null,
)

data class RecentSearch(
    val query: String,
    val mode: SearchMode,
    val searchedAt: Long,
)
