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

/**
 * Commands bound to the first key typed into an empty search field. The key is
 * consumed and arms the matching search mode, mirroring the Neovim dashboard.
 */
enum class SearchCommand(
    val key: Char,
    val label: String,
    val description: String,
) {
    FILENAME('f', "Filename", "Search file names"),
    GREP('g', "Grep", "Search note contents"),
    NEW_NOTE('n', "New note", "Create a note from the title"),
    MODUL('m', "Modul_", "Files starting with Modul_"),
    ;

    companion object {
        fun fromKey(key: Char): SearchCommand? = entries.firstOrNull { it.key == key.lowercaseChar() }
    }
}

const val MODUL_PREFIX = "Modul_"

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
