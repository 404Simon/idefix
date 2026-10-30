package dev.idefix.app.domain.model

import java.time.LocalDateTime

/**
 * A task list stored in a Markdown file named `TODO.md` or `<name>.TODO.md`.
 */
data class TodoList(
    val fileId: Long,
    val uri: String,
    val relativePath: String,
    val filename: String,
    val name: String,
    val openCount: Int = 0,
    val overdueCount: Int = 0,
) {
    companion object {
        private const val MAIN_FILE = "TODO.md"
        private const val SUFFIX = ".TODO.md"

        fun isTodoFile(filename: String): Boolean =
            filename.equals(MAIN_FILE, ignoreCase = true) ||
                filename.endsWith(SUFFIX, ignoreCase = true)

        fun displayName(filename: String): String {
            if (filename.equals(MAIN_FILE, ignoreCase = true)) return "Main"
            return filename.dropLast(SUFFIX.length)
                .replace('_', ' ')
                .replace('-', ' ')
                .trim()
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                .ifBlank { "Main" }
        }

        fun fileNameFor(name: String): String {
            val cleaned = name.trim()
                .replace('/', '-')
                .replace('\\', '-')
                .replace(Regex("""\s+"""), " ")
            return when {
                cleaned.isBlank() || cleaned.equals("main", ignoreCase = true) -> MAIN_FILE
                isTodoFile(cleaned) -> cleaned
                else -> "$cleaned$SUFFIX"
            }
        }
    }
}

/**
 * A single checkbox in a todo list. Its body is stored as an indented
 * continuation of the checkbox item, so the file stays readable in any editor.
 */
data class TodoItem(
    val fileId: Long,
    val uri: String,
    val relativePath: String,
    val filename: String,
    val startLine: Int,
    val endLine: Int,
    val title: String,
    val body: String,
    val completed: Boolean,
    val due: LocalDateTime?,
) {
    fun isOverdue(now: LocalDateTime = LocalDateTime.now()): Boolean =
        !completed && due != null && due.isBefore(now)

    fun isInTheFuture(now: LocalDateTime = LocalDateTime.now()): Boolean =
        due != null && due.isAfter(now)
}

data class TodoDraft(
    val title: String,
    val body: String = "",
    val due: LocalDateTime? = null,
)

sealed interface TodoUpdateResult {
    data class Success(
        val message: String,
        val relativePath: String? = null,
    ) : TodoUpdateResult

    data class Conflict(val message: String) : TodoUpdateResult
    data class Error(val message: String) : TodoUpdateResult
}
