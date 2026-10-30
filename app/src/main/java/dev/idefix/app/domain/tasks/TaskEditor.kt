package dev.idefix.app.domain.tasks

import dev.idefix.app.util.MarkdownLines

sealed interface ToggleOutcome {
    data class Success(val content: String) : ToggleOutcome
    data class Conflict(val message: String) : ToggleOutcome
    data class NotATask(val message: String) : ToggleOutcome
}

object TaskEditor {

    /**
     * Changes exactly one checkbox character. The rest of the file, including
     * its newline convention, is byte-for-byte untouched.
     */
    fun setCompleted(
        content: String,
        lineNumber: Int,
        expectedText: String,
        expectedCompleted: Boolean,
        newCompleted: Boolean,
    ): ToggleOutcome {
        val range = MarkdownLines.lineRange(content, lineNumber)
            ?: return ToggleOutcome.Conflict("Line $lineNumber no longer exists")
        val parsed = TaskParser.parseLine(range.text)
            ?: return ToggleOutcome.Conflict("Line $lineNumber is no longer a task")
        if (parsed.completed != expectedCompleted) {
            return ToggleOutcome.Conflict("The task was changed in another app")
        }
        if (TaskParser.normaliseText(TaskParser.cleanTaskText(parsed.text)) !=
            TaskParser.normaliseText(TaskParser.cleanTaskText(expectedText))
        ) {
            return ToggleOutcome.Conflict("The task text was changed in another app")
        }
        val boxIndex = parsed.checkboxIndexInLine
        if (boxIndex < 0 || boxIndex + 2 >= range.text.length) {
            return ToggleOutcome.Conflict("The checkbox could not be located")
        }
        val current = range.text[boxIndex + 1]
        if (current != ' ' && current != 'x' && current != 'X') {
            return ToggleOutcome.Conflict("The checkbox is not a standard Markdown checkbox")
        }
        val replacement = if (newCompleted) 'x' else ' '
        if (current == replacement) return ToggleOutcome.Success(content)
        val absolute = range.start + boxIndex + 1
        val updated = StringBuilder(content).apply { setCharAt(absolute, replacement) }.toString()
        return ToggleOutcome.Success(updated)
    }

    fun appendTask(content: String, taskText: String): String {
        val clean = taskText.trim().ifEmpty { "Untitled task" }
        val newline = MarkdownLines.detectNewline(content)
        val taskLine = "- [ ] $clean"
        return when {
            content.isEmpty() -> "$taskLine$newline"
            content.endsWith("\n") -> "$content$taskLine$newline"
            else -> "$content$newline$taskLine$newline"
        }
    }
}
