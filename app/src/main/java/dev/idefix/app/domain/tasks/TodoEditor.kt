package dev.idefix.app.domain.tasks

import dev.idefix.app.domain.model.TodoDraft
import dev.idefix.app.domain.model.TodoItem
import dev.idefix.app.util.MarkdownLines

sealed interface TodoWriteOutcome {
    data class Success(val content: String) : TodoWriteOutcome
    data class Conflict(val message: String) : TodoWriteOutcome
}

/**
 * Rewrites todo blocks while leaving non-todo content untouched. Todo blocks
 * are kept compact: consecutive tasks are separated by a single newline, and
 * blank lines only survive inside a task body or around non-task content.
 */
object TodoEditor {

    fun appendTask(content: String, draft: TodoDraft): TodoWriteOutcome {
        val newline = MarkdownLines.detectNewline(content)
        val block = renderBlock(draft, completed = false).joinToString(newline)
        val existing = content.trimEnd()
        if (existing.isEmpty()) {
            return TodoWriteOutcome.Success(block + newline)
        }
        val lastTask = TodoParser.blocks(content).lastOrNull()
        val lastLine = MarkdownLines.split(existing).size
        val separator = if (lastTask?.endLine == lastLine) newline else newline + newline
        val updated = existing + separator + block + newline
        return TodoWriteOutcome.Success(normaliseSpacing(updated))
    }

    fun updateTask(
        content: String,
        task: TodoItem,
        draft: TodoDraft,
    ): TodoWriteOutcome {
        val target = locateTask(content, task)
            ?: return TodoWriteOutcome.Conflict("The task was changed in another app")
        if (target.body != task.body || target.due != task.due) {
            return TodoWriteOutcome.Conflict("The task was changed in another app")
        }
        return rewriteLocated(content, target, draft, target.completed)
    }

    fun setCompleted(
        content: String,
        task: TodoItem,
        completed: Boolean,
    ): TodoWriteOutcome {
        val target = locateTask(content, task)
            ?: return TodoWriteOutcome.Conflict("The task was changed in another app")
        return rewriteLocated(
            content = content,
            target = target,
            draft = TodoDraft(target.title, target.body, target.due),
            completed = completed,
        )
    }

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
        return TodoWriteOutcome.Success(
            normaliseSpacing(content.removeRange(start, removeEnd)),
        )
    }

    private fun rewriteLocated(
        content: String,
        target: TodoItem,
        draft: TodoDraft,
        completed: Boolean,
    ): TodoWriteOutcome {
        val ranges = MarkdownLines.split(content)
        val start = ranges[target.startLine - 1].start
        val end = ranges[target.endLine - 1].end
        val newline = MarkdownLines.detectNewline(content)
        val trailingNewline = end < content.length || content.endsWith("\n")
        val block = renderBlock(draft, completed).joinToString(newline) +
            if (trailingNewline) newline else ""
        return TodoWriteOutcome.Success(
            normaliseSpacing(content.substring(0, start) + block + content.substring(end)),
        )
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

    /**
     * Keeps task blocks adjacent while preserving headings, paragraphs and any
     * other content that a user may have placed around the list.
     */
    private fun normaliseSpacing(content: String): String {
        val blocks = TodoParser.blocks(content)
        if (blocks.isEmpty()) return content
        val newline = MarkdownLines.detectNewline(content)
        val lines = MarkdownLines.split(content).map { it.text }
        val output = ArrayList<String>(lines.size)
        var cursor = 0

        blocks.forEachIndexed { index, block ->
            val start = block.startLine - 1
            val end = block.endLine - 1
            val gap = lines.subList(cursor, start)
            if (gap.any { it.isNotBlank() }) {
                output += gap
            }
            output += lines.subList(start, end + 1)
            cursor = end + 1
        }

        val trailing = lines.subList(cursor, lines.size)
        if (trailing.any { it.isNotBlank() }) {
            output += trailing
        }

        val joined = output.joinToString(newline)
        return if (content.endsWith("\n")) joined + newline else joined
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
