package dev.idefix.app.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.idefix.app.domain.model.FileSummary
import dev.idefix.app.domain.model.TaskFilter
import dev.idefix.app.domain.model.TaskItem
import dev.idefix.app.domain.model.TaskUpdateResult
import dev.idefix.app.domain.repository.SearchRepository
import dev.idefix.app.domain.repository.TaskRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TasksUiState(
    val tasks: List<TaskItem> = emptyList(),
    val filter: TaskFilter = TaskFilter.ALL,
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val pendingIds: Set<Long> = emptySet(),
    val files: List<FileSummary> = emptyList(),
    val filesLoading: Boolean = false,
) {
    val visibleTasks: List<TaskItem>
        get() = when (filter) {
            TaskFilter.ALL -> tasks
            TaskFilter.OPEN -> tasks.filter { !it.completed }
            TaskFilter.COMPLETED -> tasks.filter { it.completed }
            TaskFilter.TODAY -> tasks.filter { it.isDueToday }
            TaskFilter.OVERDUE -> tasks.filter { it.isOverdue }
        }

    val openCount: Int get() = tasks.count { !it.completed }
    val completedCount: Int get() = tasks.count { it.completed }
    val overdueCount: Int get() = tasks.count { it.isOverdue }
}

class TasksViewModel(
    private val taskRepository: TaskRepository,
    private val searchRepository: SearchRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TasksUiState())
    val state: StateFlow<TasksUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        refresh()
        loadFiles()
    }

    fun refresh() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { taskRepository.loadTasks() }
                .onSuccess { tasks ->
                    _state.update { it.copy(tasks = tasks, isLoading = false, error = null) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(isLoading = false, error = error.message ?: "Could not load tasks")
                    }
                }
        }
    }

    fun loadFiles() {
        viewModelScope.launch {
            _state.update { it.copy(filesLoading = true) }
            val files = runCatching { searchRepository.browseFiles() }.getOrDefault(emptyList())
            _state.update { it.copy(files = files, filesLoading = false) }
        }
    }

    fun setFilter(filter: TaskFilter) {
        _state.update { it.copy(filter = filter) }
    }

    fun toggle(task: TaskItem, completed: Boolean) {
        val key = taskKey(task)
        if (key in _state.value.pendingIds) return
        _state.update { it.copy(pendingIds = it.pendingIds + key) }
        viewModelScope.launch {
            when (val result = taskRepository.setCompleted(task, completed)) {
                is TaskUpdateResult.Success -> {
                    _state.update { current ->
                        current.copy(
                            tasks = current.tasks.map {
                                if (taskKey(it) == key) it.copy(completed = completed) else it
                            },
                            pendingIds = current.pendingIds - key,
                            message = result.message,
                        )
                    }
                    refresh()
                }

                is TaskUpdateResult.Conflict -> {
                    _state.update {
                        it.copy(pendingIds = it.pendingIds - key, message = result.message)
                    }
                    refresh()
                }

                is TaskUpdateResult.Error -> {
                    _state.update {
                        it.copy(pendingIds = it.pendingIds - key, message = result.message)
                    }
                }
            }
        }
    }

    fun addTask(relativePath: String, text: String, createIfMissing: Boolean) {
        if (text.isBlank()) {
            _state.update { it.copy(message = "Enter a task description") }
            return
        }
        viewModelScope.launch {
            when (val result = taskRepository.addTask(relativePath, text, createIfMissing)) {
                is TaskUpdateResult.Success -> {
                    _state.update { it.copy(message = result.message) }
                    refresh()
                    loadFiles()
                }

                is TaskUpdateResult.Conflict -> _state.update { it.copy(message = result.message) }
                is TaskUpdateResult.Error -> _state.update { it.copy(message = result.message) }
            }
        }
    }

    fun isPending(task: TaskItem): Boolean = taskKey(task) in _state.value.pendingIds

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun taskKey(task: TaskItem): Long =
        (task.fileId * 1_000_003L) + task.lineNumber
}
