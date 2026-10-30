package dev.idefix.app.domain.tasks

import dev.idefix.app.domain.model.TodoDraft
import dev.idefix.app.domain.model.TodoItem
import dev.idefix.app.util.MarkdownLines

sealed interface TodoWriteOutcome {
    data class Success(val content: String) : TodoWriteOutcome
    data class Conflict(val message: String) : TodoWriteOutcome
}

/**
 * Rewrites todo blocks while leaving every line outside the edited block
 * untouched. The format is described in [TodoParser].
 */
object TodoEditor {

    fun appendTask(content: String, draft: TodoDraft): TodoWriteOutcome {
        val newline = MarkdownLines.detectNewline(content)
        val block = renderBlock(draft, completed = false).joinToString(newline)
        val updated = when {
            content.isBlank() -> block + newline
            content.endsWith("\n") -> {
                val separator = if (content.endsWith("$newline$newline")) "" else newline
                content + separator + block + newline
            }

            else -> content + newline + newline + block + newline
        }
        return TodoWriteOutcome.Success(updated)
    }

    fun updateTask(
        content: String,
        task: TodoItem,
        draft: TodoDraft,
    ): TodoWriteOutcome = rewriteTask(content, task, draft, task.completed)

    fun setCompleted(
        content: String,
        task: TodoItem,
        completed: Boolean,
    ): TodoWriteOutcome = rewriteTask(
        content = content,
        task = task,
        draft = TodoDraft(task.title, task.body, task.due),
        completed = completed,
    )

    fun deleteTask(content: String, task: TodoItem): TodoWriteOutcome {
        val target = locateTask(content, task)
            ?: return TodoWriteOutcome.Conflict("The task was changed in another app")
        val ranges = MarkdownLines.split(content)
        val start = ranges[target.startLine - 1].start
        val end = ranges[target.endLine - 1].end
        val newline = MarkdownLines.detectNewline(content)
        var removeEnd = end
        if (content.startsWith(newline, removeEnd)) {
            removeEnd += newline.length
        }
        return TodoWriteOutcome.Success(content.removeRange(start, removeEnd))
    }

    private fun rewriteTask(
        content: String,
        task: TodoItem,
        draft: TodoDraft,
        completed: Boolean,
    ): TodoWriteOutcome {
        val target = locateTask(content, task)
            ?: return TodoWriteOutcome.Conflict("The task was changed in another app")
        val ranges = MarkdownLines.split(content)
        val start = ranges[target.startLine - 1].start
        val end = ranges[target.endLine - 1].end
        val newline = MarkdownLines.detectNewline(content)
        val trailingNewline = end < content.length || content.endsWith("\n")
        val block = renderBlock(draft, completed).joinToString(newline) +
            if (trailingNewline) newline else ""
        return TodoWriteOutcome.Success(content.substring(0, start) + block + content.substring(end))
    }

    private fun locateTask(content: String, task: TodoItem): TodoItem? {
        val tasks = TodoParser.parse(
            content = content,
            fileId = task.fileId,
            uri = task.uri,
            relativePath = task.relativePath,
            filename = task.filename,
        )
        return tasks.firstOrNull {
            it.startLine == task.startLine &&
                it.title == task.title &&
                it.completed == task.completed
        }
    }

    private fun renderBlock(draft: TodoDraft, completed: Boolean): List<String> {
        val lines = ArrayList<String>()
        lines += "- [${if (completed) "x" else " "}] ${draft.title.trim()}"
        draft.due?.let { lines += "  due: ${TodoParser.formatDue(it)}" }
        draft.body.replace("\r\n", "\n").trim('\n').lineSequence().forEach { line ->
            lines += if (line.isBlank()) "" else "  $line"
        }
        return lines
    }
}
