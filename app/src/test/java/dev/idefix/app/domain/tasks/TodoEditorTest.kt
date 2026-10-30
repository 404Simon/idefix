package dev.idefix.app.domain.tasks

import dev.idefix.app.domain.model.TodoDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class TodoEditorTest {

    private val content = """
        # TODO

        - [ ] First
          due: 2026-10-30 18:00
          Body.

        - [ ] Second
          Keep me.
    """.trimIndent() + "\n"

    @Test
    fun appendsATaskWithMetadataAndBody() {
        val outcome = TodoEditor.appendTask(
            "# TODO\n",
            TodoDraft("New", "Some details.", LocalDateTime.of(2026, 12, 24, 20, 0)),
        ) as TodoWriteOutcome.Success

        assertEquals(
            "# TODO\n\n- [ ] New\n  due: 2026-12-24 20:00\n  Some details.\n",
            outcome.content,
        )
    }

    @Test
    fun updatesOnlyTheTargetedBlock() {
        val task = TodoParser.parse(content, 1, "content://1", "TODO.md", "TODO.md").first()

        val outcome = TodoEditor.updateTask(
            content,
            task,
            TodoDraft("Renamed", "New body.", LocalDateTime.of(2026, 11, 1, 9, 30)),
        ) as TodoWriteOutcome.Success

        assertTrue(outcome.content.contains("- [ ] Renamed\n  due: 2026-11-01 09:30\n  New body."))
        assertTrue(outcome.content.contains("- [ ] Second\n  Keep me."))
        assertTrue(outcome.content.startsWith("# TODO\n"))
    }

    @Test
    fun togglingPreservesTheRestOfTheFileAndCrlf() {
        val crlf = "- [ ] First\r\n  Body.\r\n\r\n- [ ] Second\r\n"
        val task = TodoParser.parse(crlf, 1, "content://1", "TODO.md", "TODO.md").first()

        val outcome = TodoEditor.setCompleted(crlf, task, true) as TodoWriteOutcome.Success

        assertEquals("- [x] First\r\n  Body.\r\n\r\n- [ ] Second\r\n", outcome.content)
    }

    @Test
    fun deletesTheBlockAndItsTrailingBlankLine() {
        val task = TodoParser.parse(content, 1, "content://1", "TODO.md", "TODO.md").first()

        val outcome = TodoEditor.deleteTask(content, task) as TodoWriteOutcome.Success

        assertTrue(outcome.content.startsWith("# TODO\n\n- [ ] Second"))
        assertTrue(outcome.content.endsWith("- [ ] Second\n  Keep me.\n"))
    }

    @Test
    fun reportsAConflictWhenTheTaskTitleChanged() {
        val task = TodoParser.parse(content, 1, "content://1", "TODO.md", "TODO.md").first()
        val renamed = content.replace("First", "Renamed externally")

        val outcome = TodoEditor.setCompleted(renamed, task, true)

        assertTrue(outcome is TodoWriteOutcome.Conflict)
    }
}
