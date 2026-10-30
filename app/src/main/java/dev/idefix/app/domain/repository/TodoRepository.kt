package dev.idefix.app.domain.repository

import dev.idefix.app.domain.model.TodoDraft
import dev.idefix.app.domain.model.TodoItem
import dev.idefix.app.domain.model.TodoList
import dev.idefix.app.domain.model.TodoUpdateResult

interface TodoRepository {
    /** Finds `TODO.md` and `*.TODO.md` files and returns their current task counts. */
    suspend fun loadLists(): List<TodoList>

    suspend fun loadTasks(list: TodoList): List<TodoItem>

    suspend fun addTask(list: TodoList, draft: TodoDraft): TodoUpdateResult

    suspend fun updateTask(list: TodoList, task: TodoItem, draft: TodoDraft): TodoUpdateResult

    suspend fun setCompleted(list: TodoList, task: TodoItem, completed: Boolean): TodoUpdateResult

    suspend fun deleteTask(list: TodoList, task: TodoItem): TodoUpdateResult

    suspend fun createList(name: String): TodoUpdateResult
}
