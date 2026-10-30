package dev.idefix.app.domain.model

import java.time.LocalDate

data class TaskItem(
    val fileId: Long,
    val uri: String,
    val relativePath: String,
    val filename: String,
    val lineNumber: Int,
    val text: String,
    val completed: Boolean,
    val dueDate: LocalDate? = null,
    val depth: Int = 0,
    val tags: List<String> = emptyList(),
    val priority: String? = null,
) {
    val isOverdue: Boolean
        get() = !completed && dueDate != null && dueDate.isBefore(LocalDate.now())

    val isDueToday: Boolean
        get() = !completed && dueDate == LocalDate.now()
}

enum class TaskFilter(val label: String) {
    ALL("All"),
    OPEN("Open"),
    COMPLETED("Completed"),
    TODAY("Due today"),
    OVERDUE("Overdue"),
}

sealed interface TaskUpdateResult {
    data class Success(val message: String, val relativePath: String) : TaskUpdateResult
    data class Conflict(val message: String) : TaskUpdateResult
    data class Error(val message: String) : TaskUpdateResult
}
