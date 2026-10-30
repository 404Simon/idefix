package dev.idefix.app.domain.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskParserTest {

    private fun parse(content: String) = TaskParser.parse(
        content = content,
        fileId = 7,
        uri = "content://vault/7",
        relativePath = "notes/Tasks.md",
        filename = "Tasks.md",
    )

    @Test
    fun parsesOpenTask() {
        val task = parse("- [ ] Buy milk").single()
        assertFalse(task.completed)
        assertEquals("Buy milk", task.text)
        assertEquals(1, task.lineNumber)
    }

    @Test
    fun parsesCompletedTask() {
        assertTrue(parse("- [x] Done thing").single().completed)
        assertTrue(parse("- [X] Also done").single().completed)
    }

    @Test
    fun parsesNestedDepth() {
        val tasks = parse(
            """
            - [ ] Parent
              - [ ] Child
                - [x] Grandchild
            """.trimIndent(),
        )
        assertEquals(listOf(0, 1, 2), tasks.map { it.depth })
        assertEquals(3, tasks.size)
    }

    @Test
    fun parsesDueDateAndCleansText() {
        val task = parse("- [ ] Prepare plan 📅 2026-10-30").single()
        assertEquals("Prepare plan", task.text)
        assertEquals(java.time.LocalDate.of(2026, 10, 30), task.dueDate)
    }

    @Test
    fun invalidDueDateIsIgnored() {
        val task = parse("- [ ] Prepare plan 📅 2026-99-99").single()
        assertNull(task.dueDate)
    }

    @Test
    fun parsesTagsAndPriority() {
        val task = parse("- [ ] Ship release #work #v1 ⏫").single()
        assertEquals(listOf("work", "v1"), task.tags)
        assertEquals("⏫", task.priority)
        assertFalse(task.text.contains("⏫"))
    }

    @Test
    fun skipsCheckboxesInsideCodeFences() {
        val tasks = parse(
            """
            - [ ] Real task
            ```
            - [ ] Fake task in code
            ```
            ~~~
            - [x] Another fake
            ~~~
            - [ ] Trailing real
            """.trimIndent(),
        )
        assertEquals(listOf("Real task", "Trailing real"), tasks.map { it.text })
    }

    @Test
    fun ignoresNonTaskCheckboxText() {
        assertEquals(0, parse("Some [ ] text\n[ ] no bullet\n- [] malformed").size)
    }

    @Test
    fun handlesUnusualCharacters() {
        val text = "Résumé ✅ “quoted” <tag> & stuff \\ backslash"
        val task = parse("- [ ] $text").single()
        assertEquals(text, task.text)
    }

    @Test
    fun handlesCrlfAndLineNumbers() {
        val tasks = parse("intro\r\n- [ ] one\r\n\r\n- [x] two\r\n")
        assertEquals(listOf(2, 4), tasks.map { it.lineNumber })
    }

    @Test
    fun supportsStarAndPlusMarkers() {
        val tasks = parse("* [ ] star\n+ [x] plus\n- [ ] dash").toList()
        assertEquals(3, tasks.size)
        assertTrue(tasks[1].completed)
    }

    @Test
    fun supportsMultipleSpacesAfterMarker() {
        val task = parse("-   [ ] spaced out").single()
        assertEquals("spaced out", task.text)
    }
}
