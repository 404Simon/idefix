package dev.idefix.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class TodoModelsTest {

    @Test
    fun recognisesMainAndNamedTodoFiles() {
        assertTrue(TodoList.isTodoFile("TODO.md"))
        assertTrue(TodoList.isTodoFile("job.TODO.md"))
        assertTrue(TodoList.isTodoFile("Job.Todo.MD"))
        assertFalse(TodoList.isTodoFile("job.md"))
        assertFalse(TodoList.isTodoFile("TODO.md.bak"))
    }

    @Test
    fun derivesReadableListNames() {
        assertEquals("Main", TodoList.displayName("TODO.md"))
        assertEquals("Job", TodoList.displayName("job.TODO.md"))
        assertEquals("Side project", TodoList.displayName("side-project.TODO.md"))
    }

    @Test
    fun buildsFileNamesFromDisplayNames() {
        assertEquals("TODO.md", TodoList.fileNameFor("main"))
        assertEquals("job.TODO.md", TodoList.fileNameFor("job"))
        assertEquals("job.TODO.md", TodoList.fileNameFor("job.TODO.md"))
        assertEquals("a-b.TODO.md", TodoList.fileNameFor("a/b"))
    }

    @Test
    fun detectsOverdueAndFutureTodos() {
        val now = LocalDateTime.of(2026, 10, 30, 12, 0)
        val item = TodoItem(
            fileId = 1,
            uri = "content://1",
            relativePath = "TODO.md",
            filename = "TODO.md",
            startLine = 1,
            endLine = 1,
            title = "Test",
            body = "",
            completed = false,
            due = now.minusMinutes(1),
        )
        assertTrue(item.isOverdue(now))
        assertFalse(item.isInTheFuture(now))
        assertFalse(item.copy(completed = true).isOverdue(now))
        assertTrue(item.copy(due = now.plusHours(1)).isInTheFuture(now))
    }
}
