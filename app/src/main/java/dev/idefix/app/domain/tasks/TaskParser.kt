package dev.idefix.app.domain.tasks

import dev.idefix.app.domain.model.TaskItem
import dev.idefix.app.util.MarkdownLines
import java.time.LocalDate
import java.time.format.DateTimeParseException

data class ParsedTaskLine(
    val indent: String,
    val marker: Char,
    val completed: Boolean,
    val text: String,
    val checkboxIndexInLine: Int,
)

object TaskParser {

    private val taskRegex = Regex("""^([ \t]*)([-*+])[ \t]+\[([ xX])][ \t]?(.*)$""")
    private val dueRegex = Regex("""📅\s*(\d{4}-\d{2}-\d{2})""")
    private val tagRegex = Regex("""(?<![\p{L}\p{N}_])#([\p{L}\p{N}_/-]+)""")
    private val priorityRegex = Regex("""(⏫|🔼|🔽|⏬|🔺|🔻)""")

    fun parseLine(line: String): ParsedTaskLine? {
        val match = taskRegex.matchEntire(line) ?: return null
        val indent = match.groupValues[1]
        val marker = match.groupValues[2].first()
        val state = match.groupValues[3]
        val rawText = match.groupValues[4]
        val bracket = line.indexOf('[', startIndex = indent.length + 2)
        return ParsedTaskLine(
            indent = indent,
            marker = marker,
            completed = state.equals("x", ignoreCase = true),
            text = rawText.trim(),
            checkboxIndexInLine = bracket,
        )
    }

    fun parse(content: String, fileId: Long, uri: String, relativePath: String, filename: String): List<TaskItem> {
        val tasks = ArrayList<TaskItem>()
        var inFence = false
        var fenceChar = ""
        var lineNumber = 0
        for (line in content.lineSequence()) {
            lineNumber++
            val trimmed = line.trimStart()
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                val char = trimmed.take(3)
                if (!inFence) {
                    inFence = true
                    fenceChar = char
                } else if (trimmed.startsWith(fenceChar)) {
                    inFence = false
                }
                continue
            }
            if (inFence) continue
            val parsed = parseLine(line) ?: continue
            val rawText = parsed.text
            val due = dueRegex.find(rawText)?.groupValues?.get(1)?.let { iso ->
                try {
                    LocalDate.parse(iso)
                } catch (_: DateTimeParseException) {
                    null
                }
            }
            val tags = tagRegex.findAll(rawText).map { it.groupValues[1] }.toList()
            val priority = priorityRegex.find(rawText)?.value
            val displayText = cleanTaskText(rawText)
            tasks += TaskItem(
                fileId = fileId,
                uri = uri,
                relativePath = relativePath,
                filename = filename,
                lineNumber = lineNumber,
                text = displayText,
                completed = parsed.completed,
                dueDate = due,
                depth = indentationDepth(parsed.indent),
                tags = tags.distinct(),
                priority = priority,
            )
        }
        return tasks
    }

    /** Removes Obsidian scheduling/priority markers from a task's display text. */
    fun cleanTaskText(rawText: String): String = rawText
        .replace(dueRegex, "")
        .replace(priorityRegex, "")
        .trim()

    fun indentationDepth(indent: String): Int {
        var columns = 0
        for (ch in indent) {
            columns += if (ch == '\t') 4 else 1
        }
        return columns / 2
    }

    /** Used by the editor to verify that the source line still describes the task. */
    fun normaliseText(text: String): String = text.replace(Regex("\\s+"), " ").trim()
}
