package dev.idefix.app.domain.tasks

import dev.idefix.app.domain.model.TodoItem
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * Parses the todo-list Markdown format:
 *
 * ```markdown
 * - [ ] Buy milk
 *   due: 2026-10-30 18:00
 *   Oat milk, not soy.
 *
 * - [x] Finished task
 * ```
 *
 * A task starts with an unindented Markdown checkbox. Indented lines belong
 * to the task. The first indented `due:` line is metadata; all other indented
 * lines are the task body. Blank lines inside a body are preserved, while
 * trailing blank lines are not part of the task.
 */
object TodoParser {

    private val taskLine = Regex("""^[-*+][ \t]+\[([ xX])][ \t]+(.*)$""")
    private val dueLine = Regex("""(?i)^due:\s*(.+)$""")
    private val dueFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private val dueFormatterIso = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    private val dateOnlyFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    data class TaskHeader(
        val completed: Boolean,
        val title: String,
    )

    fun parseHeader(line: String): TaskHeader? {
        val match = taskLine.matchEntire(line) ?: return null
        return TaskHeader(
            completed = match.groupValues[1].equals("x", ignoreCase = true),
            title = match.groupValues[2].trim(),
        )
    }

    fun parse(
        content: String,
        fileId: Long,
        uri: String,
        relativePath: String,
        filename: String,
    ): List<TodoItem> {
        val tasks = ArrayList<BuiltTask>()
        var builder: Builder? = null
        var lastContentLine = 0
        var pendingBlankLines = 0
        var lineNumber = 0
        var fence: String? = null

        for (line in content.lineSequence()) {
            lineNumber++
            val topLevelFence = line == line.trimStart() &&
                (line.startsWith("```") || line.startsWith("~~~"))
            if (topLevelFence) {
                val marker = line.take(3)
                if (fence == null) {
                    builder?.let { tasks.add(it.finish(lastContentLine)) }
                    builder = null
                    pendingBlankLines = 0
                    fence = marker
                } else if (marker == fence) {
                    fence = null
                }
                continue
            }
            if (fence != null) continue

            val header = parseHeader(line)
            if (header != null) {
                builder?.let { tasks.add(it.finish(lastContentLine)) }
                builder = Builder(
                    startLine = lineNumber,
                    completed = header.completed,
                    title = header.title,
                )
                lastContentLine = lineNumber
                pendingBlankLines = 0
                continue
            }

            val current = builder ?: continue
            if (line.isBlank()) {
                pendingBlankLines++
                continue
            }
            if (line.first().isWhitespace()) {
                val stripped = stripIndent(line)
                val dueMatch = dueLine.matchEntire(stripped)
                if (current.body.isEmpty() && current.due == null && dueMatch != null) {
                    val parsedDue = parseDue(dueMatch.groupValues[1])
                    if (parsedDue != null) {
                        current.due = parsedDue
                        lastContentLine = lineNumber
                        pendingBlankLines = 0
                        continue
                    }
                }
                repeat(pendingBlankLines) { current.body.add("") }
                pendingBlankLines = 0
                current.body.add(stripped)
                lastContentLine = lineNumber
            } else {
                tasks.add(current.finish(lastContentLine))
                builder = null
                pendingBlankLines = 0
            }
        }
        builder?.let { tasks.add(it.finish(lastContentLine)) }

        return tasks.map {
            TodoItem(
                fileId = fileId,
                uri = uri,
                relativePath = relativePath,
                filename = filename,
                startLine = it.startLine,
                endLine = it.endLine,
                title = it.title,
                body = it.body.joinToString("\n").trimEnd(),
                completed = it.completed,
                due = it.due,
            )
        }
    }

    fun formatDue(value: LocalDateTime): String = value.format(dueFormatter)

    fun parseDue(value: String): LocalDateTime? {
        val trimmed = value.trim()
        return sequenceOf(
            { LocalDateTime.parse(trimmed, dueFormatter) },
            { LocalDateTime.parse(trimmed, dueFormatterIso) },
            { LocalDate.parse(trimmed, dateOnlyFormatter).atTime(LocalTime.of(23, 59)) },
        ).mapNotNull { candidate ->
            try {
                candidate()
            } catch (_: DateTimeParseException) {
                null
            }
        }.firstOrNull()
    }

    private fun stripIndent(line: String): String = when {
        line.startsWith("\t") -> line.substring(1)
        line.startsWith("  ") -> line.substring(2)
        else -> line.trimStart()
    }

    private data class Builder(
        val startLine: Int,
        val completed: Boolean,
        val title: String,
        var due: LocalDateTime? = null,
        val body: MutableList<String> = ArrayList(),
    ) {
        fun finish(endLine: Int): BuiltTask = BuiltTask(
            startLine = startLine,
            endLine = endLine.coerceAtLeast(startLine),
            title = title,
            body = body,
            completed = completed,
            due = due,
        )
    }

    private data class BuiltTask(
        val startLine: Int,
        val endLine: Int,
        val title: String,
        val body: List<String>,
        val completed: Boolean,
        val due: LocalDateTime?,
    )
}
