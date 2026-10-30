package dev.idefix.app.domain.repository

import dev.idefix.app.domain.model.TaskItem
import dev.idefix.app.domain.model.TaskUpdateResult

interface TaskRepository {
    suspend fun loadTasks(): List<TaskItem>

    suspend fun setCompleted(task: TaskItem, completed: Boolean): TaskUpdateResult

    suspend fun addTask(
        relativePath: String,
        text: String,
        createIfMissing: Boolean = false,
    ): TaskUpdateResult
}
