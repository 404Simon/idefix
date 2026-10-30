package dev.idefix.app.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.idefix.app.domain.model.TodoDraft
import dev.idefix.app.domain.model.TodoItem
import dev.idefix.app.domain.model.TodoList
import dev.idefix.app.domain.model.TodoUpdateResult
import dev.idefix.app.domain.repository.TodoRepository
import dev.idefix.app.sync.SyncCoordinator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDateTime

data class TasksUiState(
    val lists: List<TodoList> = emptyList(),
    val selectedList: TodoList? = null,
    val tasks: List<TodoItem> = emptyList(),
    val hideFuture: Boolean = false,
    val isLoadingLists: Boolean = false,
    val isLoadingTasks: Boolean = false,
    val error: String? = null,
    val message: String? = null,
    val pendingLines: Set<Int> = emptySet(),
    val creatingListPath: String? = null,
) {
    fun visibleTasks(now: LocalDateTime = LocalDateTime.now()): List<TodoItem> =
        if (hideFuture) tasks.filter { !it.isInTheFuture(now) } else tasks

    val openCount: Int get() = tasks.count { !it.completed }
    val overdueCount: Int get() = tasks.count { it.isOverdue() }
}

class TasksViewModel(
    private val todoRepository: TodoRepository,
    private val syncCoordinator: SyncCoordinator,
) : ViewModel() {

    private val _state = MutableStateFlow(TasksUiState(isLoadingLists = true))
    val state: StateFlow<TasksUiState> = _state.asStateFlow()

    private val writeMutex = Mutex()
    private var listsJob: Job? = null
    private var tasksJob: Job? = null
    private var nextTemporaryLine = -1

    init {
        refresh()
    }

    fun refresh() {
        loadLists(
            selectPath = _state.value.selectedList?.relativePath,
            reloadTasks = _state.value.selectedList != null,
        )
    }

    fun selectList(list: TodoList) {
        if (_state.value.selectedList?.relativePath == list.relativePath) return
        _state.update {
            it.copy(
                selectedList = list,
                tasks = emptyList(),
                error = null,
                pendingLines = emptySet(),
                isLoadingTasks = true,
            )
        }
        loadTasks(list)
    }

    fun backToList() {
        tasksJob?.cancel()
        _state.update {
            it.copy(
                selectedList = null,
                tasks = emptyList(),
                error = null,
                pendingLines = emptySet(),
                isLoadingTasks = false,
            )
        }
    }

    fun setHideFuture(enabled: Boolean) {
        _state.update { it.copy(hideFuture = enabled) }
    }

    fun toggle(task: TodoItem, completed: Boolean) {
        val list = _state.value.selectedList ?: return
        val key = task.startLine
        if (key <= 0 || key in _state.value.pendingLines) return
        _state.update { current ->
            current.withTasks(
                tasks = current.tasks.map {
                    if (it.startLine == key) it.copy(completed = completed) else it
                },
                pendingLines = current.pendingLines + key,
            )
        }
        launchWrite {
            try {
                handleResult(todoRepository.setCompleted(list, task, completed))
            } finally {
                _state.update { it.copy(pendingLines = it.pendingLines - key) }
                reconcileCurrent()
            }
        }
    }

    fun saveTask(existing: TodoItem?, draft: TodoDraft) {
        val list = _state.value.selectedList ?: return
        if (draft.title.isBlank()) {
            _state.update { it.copy(message = "Enter a todo title") }
            return
        }
        if (existing != null &&
            (existing.startLine <= 0 || existing.startLine in _state.value.pendingLines)
        ) {
            return
        }
        if (existing == null) {
            addOptimistically(list, draft)
        } else {
            updateOptimistically(list, existing, draft)
        }
    }

    fun deleteTask(task: TodoItem) {
        val list = _state.value.selectedList ?: return
        if (task.startLine <= 0 || task.startLine in _state.value.pendingLines) return
        _state.update { current ->
            current.withTasks(current.tasks.filterNot { it.startLine == task.startLine })
        }
        launchWrite {
            try {
                handleResult(todoRepository.deleteTask(list, task))
            } finally {
                reconcileCurrent()
            }
        }
    }

    fun createList(name: String) {
        val cleaned = name.trim()
        if (cleaned.isBlank()) {
            _state.update { it.copy(message = "Enter a list name") }
            return
        }
        val filename = TodoList.fileNameFor(cleaned)
        if (_state.value.lists.any { it.relativePath.equals(filename, ignoreCase = true) }) {
            _state.update {
                it.copy(message = "${TodoList.displayName(filename)} already exists")
            }
            return
        }
        val provisional = TodoList(
            fileId = 0,
            uri = "",
            relativePath = filename,
            filename = filename,
            name = TodoList.displayName(filename),
        )
        _state.update {
            it.copy(
                lists = (it.lists + provisional).sortedForDisplay(),
                creatingListPath = filename,
                message = null,
            )
        }
        launchWrite {
            try {
                when (val result = todoRepository.createList(cleaned)) {
                    is TodoUpdateResult.Success -> {
                        val path = result.relativePath ?: filename
                        val createdFilename = path.substringAfterLast('/')
                        val created = TodoList(
                            fileId = 0,
                            uri = "",
                            relativePath = path,
                            filename = createdFilename,
                            name = TodoList.displayName(createdFilename),
                        )
                        _state.update {
                            it.copy(
                                lists = it.lists.map { list ->
                                    if (list.relativePath == filename) created else list
                                }.sortedForDisplay(),
                                creatingListPath = null,
                                error = null,
                            )
                        }
                        if (result.message.contains("index refresh failed", ignoreCase = true)) {
                            _state.update { it.copy(message = result.message) }
                        }
                        if (_state.value.selectedList == null) {
                            _state.update {
                                it.copy(
                                    selectedList = created,
                                    tasks = emptyList(),
                                    pendingLines = emptySet(),
                                    isLoadingTasks = true,
                                )
                            }
                            loadTasks(created)
                        }
                    }

                    is TodoUpdateResult.Conflict -> {
                        _state.update {
                            it.copy(
                                lists = it.lists.filterNot { list ->
                                    list.relativePath == filename
                                },
                                creatingListPath = null,
                                message = result.message,
                            )
                        }
                    }

                    is TodoUpdateResult.Error -> {
                        _state.update {
                            it.copy(
                                lists = it.lists.filterNot { list ->
                                    list.relativePath == filename
                                },
                                creatingListPath = null,
                                message = result.message,
                            )
                        }
                    }
                }
            } finally {
                _state.update {
                    if (it.creatingListPath == filename) {
                        it.copy(creatingListPath = null)
                    } else {
                        it
                    }
                }
            }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun addOptimistically(list: TodoList, draft: TodoDraft) {
        val tempLine = nextTemporaryLine--
        val optimistic = TodoItem(
            fileId = list.fileId,
            uri = list.uri,
            relativePath = list.relativePath,
            filename = list.filename,
            startLine = tempLine,
            endLine = tempLine,
            title = draft.title.trim(),
            body = draft.body.trim(),
            completed = false,
            due = draft.due,
        )
        _state.update { current ->
            current.withTasks(
                tasks = current.tasks + optimistic,
                pendingLines = current.pendingLines + tempLine,
            )
        }
        launchWrite {
            try {
                handleResult(todoRepository.addTask(list, draft))
            } finally {
                _state.update { current ->
                    current.withTasks(
                        tasks = current.tasks.filterNot { it.startLine == tempLine },
                        pendingLines = current.pendingLines - tempLine,
                    )
                }
                reconcileCurrent()
            }
        }
    }

    private fun updateOptimistically(
        list: TodoList,
        existing: TodoItem,
        draft: TodoDraft,
    ) {
        val optimistic = existing.copy(
            title = draft.title.trim(),
            body = draft.body.trim(),
            due = draft.due,
        )
        _state.update { current ->
            current.withTasks(
                tasks = current.tasks.map {
                    if (it.startLine == existing.startLine) optimistic else it
                },
                pendingLines = current.pendingLines + existing.startLine,
            )
        }
        launchWrite {
            try {
                handleResult(todoRepository.updateTask(list, existing, draft))
            } finally {
                _state.update {
                    it.copy(pendingLines = it.pendingLines - existing.startLine)
                }
                reconcileCurrent()
            }
        }
    }

    private fun loadLists(selectPath: String?, reloadTasks: Boolean) {
        listsJob?.cancel()
        listsJob = viewModelScope.launch {
            _state.update { it.copy(isLoadingLists = true, error = null) }
            try {
                val lists = todoRepository.loadLists()
                val selected = selectPath?.let { path ->
                    lists.firstOrNull { it.relativePath == path }
                }
                _state.update {
                    it.copy(
                        lists = lists,
                        selectedList = selected,
                        tasks = if (selected == null) emptyList() else it.tasks,
                        isLoadingLists = false,
                        error = null,
                    )
                }
                if (selected != null && reloadTasks) loadTasks(selected)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoadingLists = false,
                        error = e.message ?: "Could not load todo lists",
                    )
                }
            }
        }
    }

    private fun loadTasks(list: TodoList) {
        tasksJob?.cancel()
        tasksJob = viewModelScope.launch {
            _state.update {
                it.copy(isLoadingTasks = true, error = null)
            }
            try {
                val tasks = todoRepository.loadTasks(list)
                if (_state.value.selectedList?.relativePath != list.relativePath) return@launch
                _state.update {
                    val merged = it.mergeOptimistic(tasks)
                    it.withTasks(
                        tasks = merged,
                        pendingLines = it.pendingLines.filter { line ->
                            merged.any { todo -> todo.startLine == line }
                        }.toSet(),
                    ).copy(
                        isLoadingTasks = false,
                        error = null,
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_state.value.selectedList?.relativePath == list.relativePath) {
                    _state.update {
                        it.copy(
                            isLoadingTasks = false,
                            error = e.message ?: "Could not load the todo list",
                        )
                    }
                }
            }
        }
    }

    /**
     * Re-reads only the selected file after a write. Optimistic rows that are
     * still waiting behind the write mutex are kept so rapid additions do not
     * flicker out of the list.
     */
    private suspend fun reconcileCurrent() {
        val list = _state.value.selectedList ?: return
        val loaded = try {
            todoRepository.loadTasks(list)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            return
        }
        _state.update { current ->
            val merged = current.mergeOptimistic(loaded)
            current.withTasks(
                tasks = merged,
                pendingLines = current.pendingLines.filter { line ->
                    merged.any { todo -> todo.startLine == line }
                }.toSet(),
            )
        }
    }

    /**
     * Keeps optimistic rows on top of freshly loaded file content. Positive
     * lines are in-flight edits of existing todos; negative lines are pending
     * additions that do not exist in the file yet.
     */
    private fun TasksUiState.mergeOptimistic(loaded: List<TodoItem>): List<TodoItem> {
        val optimistic = tasks.filter { it.startLine in pendingLines }
        if (optimistic.isEmpty()) return loaded
        val byLine = optimistic.associateBy { it.startLine }
        val merged = loaded.map { byLine[it.startLine] ?: it }
        return merged + optimistic.filter { it.startLine < 0 }
    }

    /**
     * Serialises writes and keeps the sync indicator alive until the write and
     * its reconciliation have both finished.
     */
    private fun launchWrite(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                syncCoordinator.track {
                    writeMutex.withLock { block() }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update {
                    it.copy(message = e.message ?: "Could not save the todo")
                }
            }
        }
    }

    private fun handleResult(result: TodoUpdateResult) {
        when (result) {
            is TodoUpdateResult.Success -> {
                if (result.message.contains("index refresh failed", ignoreCase = true)) {
                    _state.update { it.copy(message = result.message) }
                }
            }

            is TodoUpdateResult.Conflict -> _state.update { it.copy(message = result.message) }
            is TodoUpdateResult.Error -> _state.update { it.copy(message = result.message) }
        }
    }

    private fun TasksUiState.withTasks(
        tasks: List<TodoItem>,
        pendingLines: Set<Int> = this.pendingLines,
    ): TasksUiState {
        val list = selectedList ?: return copy(tasks = tasks, pendingLines = pendingLines)
        val updatedList = list.copy(
            openCount = tasks.count { !it.completed },
            overdueCount = tasks.count { it.isOverdue() },
        )
        return copy(
            tasks = tasks,
            pendingLines = pendingLines,
            selectedList = updatedList,
            lists = lists.map {
                if (it.relativePath == updatedList.relativePath) updatedList else it
            },
        )
    }

    private fun List<TodoList>.sortedForDisplay(): List<TodoList> = sortedWith(
        compareBy<TodoList>(
            { !it.filename.equals("TODO.md", ignoreCase = true) },
            { it.filename.lowercase() },
            { it.relativePath.lowercase() },
        ),
    )
}
