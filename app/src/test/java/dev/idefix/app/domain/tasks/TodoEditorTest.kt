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

        assertEquals("- [x] First\r\n  Body.\r\n- [ ] Second\r\n", outcome.content)
    }

    @Test
    fun deletesTheBlockAndItsTrailingBlankLine() {
        val task = TodoParser.parse(content, 1, "content://1", "TODO.md", "TODO.md").first()

        val outcome = TodoEditor.deleteTask(content, task) as TodoWriteOutcome.Success

        assertTrue(outcome.content.startsWith("# TODO\n\n- [ ] Second"))
        assertTrue(outcome.content.endsWith("- [ ] Second\n  Keep me.\n"))
    }

    @Test
    fun togglingKeepsExternalBodyAndDueChanges() {
        val task = TodoParser.parse(content, 1, "content://1", "TODO.md", "TODO.md").first()
        val externallyEdited = content
            .replace("Body.", "Edited on desktop.")
            .replace("2026-10-30 18:00", "2026-11-02 07:15")

        val outcome = TodoEditor.setCompleted(
            externallyEdited,
            task,
            true,
        ) as TodoWriteOutcome.Success

        assertTrue(outcome.content.contains("- [x] First"))
        assertTrue(outcome.content.contains("  due: 2026-11-02 07:15"))
        assertTrue(outcome.content.contains("  Edited on desktop."))
    }

    @Test
    fun editingConflictsWhenTheBodyChangedExternally() {
        val task = TodoParser.parse(content, 1, "content://1", "TODO.md", "TODO.md").first()
        val externallyEdited = content.replace("Body.", "Edited on desktop.")

        val outcome = TodoEditor.updateTask(
            externallyEdited,
            task,
            TodoDraft("First", "New body.", null),
        )

        assertTrue(outcome is TodoWriteOutcome.Conflict)
    }

    @Test
    fun normalisesMessySpacingAroundTheEditedBlock() {
        val messy = "- [ ] First\n\n\n\n- [ ] Second\n\n\n- [ ] Third\n"
        val task = TodoParser.parse(messy, 1, "content://1", "TODO.md", "TODO.md").first()

        val outcome = TodoEditor.setCompleted(messy, task, true) as TodoWriteOutcome.Success

        assertEquals("- [x] First\n- [ ] Second\n- [ ] Third\n", outcome.content)
    }

    @Test
    fun reportsAConflictWhenTheTaskTitleChanged() {
        val task = TodoParser.parse(content, 1, "content://1", "TODO.md", "TODO.md").first()
        val renamed = content.replace("First", "Renamed externally")

        val outcome = TodoEditor.setCompleted(renamed, task, true)

        assertTrue(outcome is TodoWriteOutcome.Conflict)
    }
}
