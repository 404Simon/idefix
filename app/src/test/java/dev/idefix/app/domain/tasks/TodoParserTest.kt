package dev.idefix.app.domain.tasks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class TodoParserTest {

    private val content = """
        # TODO

        - [ ] First
          due: 2026-10-30 18:00
          Body one.
          Body two.

        - [x] Second

        Text after the list.
    """.trimIndent()

    @Test
    fun parsesTitleBodyDueAndCompletion() {
        val tasks = TodoParser.parse(content, 1, "content://1", "TODO.md", "TODO.md")

        assertEquals(2, tasks.size)
        val first = tasks.first()
        assertEquals("First", first.title)
        assertEquals("Body one.\nBody two.", first.body)
        assertEquals(LocalDateTime.of(2026, 10, 30, 18, 0), first.due)
        assertFalse(first.completed)
        assertEquals(3, first.startLine)
        assertEquals(6, first.endLine)

        val second = tasks.last()
        assertEquals("Second", second.title)
        assertTrue(second.completed)
        assertEquals("", second.body)
        assertNull(second.due)
        assertEquals(8, second.startLine)
        assertEquals(8, second.endLine)
    }

    @Test
    fun ignoresCheckboxesInsideCodeFences() {
        val text = """
            - [ ] Real

            ```
            - [ ] Not a todo
            ```

            - [ ] Another
        """.trimIndent()

        val tasks = TodoParser.parse(text, 1, "content://1", "TODO.md", "TODO.md")

        assertEquals(listOf("Real", "Another"), tasks.map { it.title })
    }

    @Test
    fun acceptsDateOnlyAndIsoDueValues() {
        assertEquals(LocalDateTime.of(2026, 10, 30, 23, 59), TodoParser.parseDue("2026-10-30"))
        assertEquals(
            LocalDateTime.of(2026, 10, 30, 18, 15),
            TodoParser.parseDue("2026-10-30T18:15"),
        )
        assertNull(TodoParser.parseDue("not a date"))
    }

    @Test
    fun crlfContentKeepsItsLineNumbers() {
        val text = "- [ ] One\r\n  Body\r\n\r\n- [ ] Two\r\n"
        val tasks = TodoParser.parse(text, 1, "content://1", "TODO.md", "TODO.md")
        assertEquals(2, tasks.size)
        assertEquals("Body", tasks.first().body)
        assertEquals(4, tasks.last().startLine)
    }
}
