package dev.idefix.app.domain.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskEditorTest {

    @Test
    fun checksOnlyTheCheckboxCharacter() {
        val original = "# Notes\n\n- [ ] Buy milk\n- Keep this line untouched\n"
        val outcome = TaskEditor.setCompleted(original, 3, "Buy milk", false, true)
        assertTrue(outcome is ToggleOutcome.Success)
        val updated = (outcome as ToggleOutcome.Success).content
        assertEquals("# Notes\n\n- [x] Buy milk\n- Keep this line untouched\n", updated)
    }

    @Test
    fun unchecksUppercaseCompletion() {
        val original = "- [X] Done\n"
        val outcome = TaskEditor.setCompleted(original, 1, "Done", true, false) as ToggleOutcome.Success
        assertEquals("- [ ] Done\n", outcome.content)
    }

    @Test
    fun preservesCrlf() {
        val original = "Intro\r\n- [ ] Task\r\nAfter\r\n"
        val outcome = TaskEditor.setCompleted(original, 2, "Task", false, true) as ToggleOutcome.Success
        assertEquals("Intro\r\n- [x] Task\r\nAfter\r\n", outcome.content)
    }

    @Test
    fun dueDateDoesNotCauseFalseConflict() {
        val original = "- [ ] Prepare plan 📅 2026-10-30\n"
        val outcome = TaskEditor.setCompleted(original, 1, "Prepare plan", false, true)
        assertTrue(outcome is ToggleOutcome.Success)
        assertEquals("- [x] Prepare plan 📅 2026-10-30\n", (outcome as ToggleOutcome.Success).content)
    }

    @Test
    fun textChangedConflict() {
        val original = "- [ ] Different text\n"
        val outcome = TaskEditor.setCompleted(original, 1, "Expected text", false, true)
        assertTrue(outcome is ToggleOutcome.Conflict)
    }

    @Test
    fun stateChangedConflict() {
        val original = "- [x] Task\n"
        val outcome = TaskEditor.setCompleted(original, 1, "Task", false, true)
        assertTrue(outcome is ToggleOutcome.Conflict)
    }

    @Test
    fun missingLineConflict() {
        val outcome = TaskEditor.setCompleted("- [ ] Task\n", 5, "Task", false, true)
        assertTrue(outcome is ToggleOutcome.Conflict)
    }

    @Test
    fun nonTaskLineConflict() {
        val outcome = TaskEditor.setCompleted("plain line\n", 1, "plain line", false, true)
        assertTrue(outcome is ToggleOutcome.Conflict)
    }

    @Test
    fun appendToEmptyFile() {
        assertEquals("- [ ] New task\n", TaskEditor.appendTask("", "New task"))
    }

    @Test
    fun appendKeepsExistingContent() {
        assertEquals("old\n- [ ] New task\n", TaskEditor.appendTask("old\n", "New task"))
    }

    @Test
    fun appendAddsMissingNewline() {
        assertEquals("old\n- [ ] New task\n", TaskEditor.appendTask("old", "New task"))
    }

    @Test
    fun appendUsesCrlf() {
        assertEquals("old\r\n- [ ] New task\r\n", TaskEditor.appendTask("old\r\n", "New task"))
    }
}
