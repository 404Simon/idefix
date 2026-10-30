package dev.idefix.app.domain.model

enum class SearchMode {
    FILENAME,
    TEXT,
    LITERAL,
    REGEX;

    val label: String
        get() = when (this) {
            FILENAME -> "Filename"
            TEXT -> "Full text"
            LITERAL -> "Literal"
            REGEX -> "Regex"
        }

    val hint: String
        get() = when (this) {
            FILENAME -> "Fuzzy file name search"
            TEXT -> "Indexed terms and \"exact phrases\""
            LITERAL -> "Exact characters, grep style"
            REGEX -> "Java regular expression"
        }

    companion object {
        fun fromStorage(value: String?): SearchMode =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: FILENAME
    }
}

const val SNIPPET_HIGHLIGHT_START = '\u0001'
const val SNIPPET_HIGHLIGHT_END = '\u0002'

data class SearchResult(
    val fileId: Long,
    val uri: String,
    val relativePath: String,
    val filename: String,
    val line: Int? = null,
    val snippet: String? = null,
    val score: Double = 0.0,
    val mode: SearchMode = SearchMode.FILENAME,
    val lastModified: Long? = null,
)

data class SearchOutcome(
    val query: String,
    val mode: SearchMode,
    val results: List<SearchResult> = emptyList(),
    val error: String? = null,
    val durationMs: Long = 0,
)

data class FtsHit(
    val fileId: Long,
    val filename: String,
    val path: String,
    val content: String,
    val snippet: String,
    val rank: Double,
)
