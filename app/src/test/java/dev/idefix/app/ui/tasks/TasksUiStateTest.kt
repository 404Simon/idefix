package dev.idefix.app.ui.tasks

import dev.idefix.app.domain.model.TodoItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class TasksUiStateTest {

    private val now = LocalDateTime.of(2026, 10, 30, 12, 0)
    private val overdue = todo("Overdue", now.minusHours(2))
    private val dueNow = todo("Due now", now.minusMinutes(1))
    private val future = todo("Future", now.plusHours(2))
    private val undated = todo("Undated", null)

    @Test
    fun hideFutureKeepsDueOverdueAndUndatedTodos() {
        val state = TasksUiState(
            tasks = listOf(overdue, dueNow, future, undated),
            hideFuture = true,
        )

        assertEquals(
            listOf("Overdue", "Due now", "Undated"),
            state.visibleTasks(now).map { it.title },
        )
    }

    @Test
    fun turningTheFilterOffShowsEverything() {
        val state = TasksUiState(
            tasks = listOf(overdue, dueNow, future, undated),
            hideFuture = false,
        )

        assertEquals(4, state.visibleTasks(now).size)
        assertEquals(2, state.overdueCountFor(now))
    }

    private fun TasksUiState.overdueCountFor(now: LocalDateTime): Int =
        tasks.count { it.isOverdue(now) }

    private fun todo(title: String, due: LocalDateTime?) = TodoItem(
        fileId = 1,
        uri = "content://1",
        relativePath = "TODO.md",
        filename = "TODO.md",
        startLine = 1,
        endLine = 1,
        title = title,
        body = "",
        completed = false,
        due = due,
    )
}
