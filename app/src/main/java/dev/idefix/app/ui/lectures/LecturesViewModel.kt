package dev.idefix.app.ui.lectures

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.idefix.app.domain.model.LectureEntry
import dev.idefix.app.domain.model.LectureInfo
import dev.idefix.app.domain.repository.LectureRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LectureViewerState(
    val entry: LectureEntry,
    val content: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class LecturesUiState(
    val lecture: LectureInfo? = null,
    val rootFolders: List<LectureEntry> = emptyList(),
    val folderStack: List<LectureEntry> = emptyList(),
    val entries: List<LectureEntry> = emptyList(),
    val isLoadingFolders: Boolean = false,
    val isLoadingEntries: Boolean = false,
    val error: String? = null,
    val viewer: LectureViewerState? = null,
) {
    val currentFolder: LectureEntry? get() = folderStack.lastOrNull()
    val selectedLecture: LectureEntry? get() = folderStack.firstOrNull()
    val canGoBack: Boolean get() = folderStack.isNotEmpty()
}

class LecturesViewModel(
    private val repository: LectureRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LecturesUiState())
    val state: StateFlow<LecturesUiState> = _state.asStateFlow()

    private var foldersJob: Job? = null
    private var entriesJob: Job? = null
    private var viewerJob: Job? = null

    init {
        viewModelScope.launch {
            repository.lectureInfo.collect { info ->
                if (info == null) {
                    _state.update {
                        LecturesUiState(lecture = null, isLoadingFolders = false)
                    }
                } else {
                    _state.update { it.copy(lecture = info) }
                    refresh()
                }
            }
        }
    }

    /** Rescan the lectures folder. External syncs show up immediately after this. */
    fun refresh() {
        if (repository.lectureInfo.value == null) return
        foldersJob?.cancel()
        loadRootFolders()
    }

    fun selectLecture(folder: LectureEntry) {
        if (!folder.isDirectory) return
        _state.update {
            it.copy(
                folderStack = listOf(folder),
                entries = emptyList(),
                isLoadingEntries = true,
                error = null,
            )
        }
        loadChildren()
    }

    fun openFolder(folder: LectureEntry) {
        if (!folder.isDirectory) return
        _state.update {
            it.copy(
                folderStack = it.folderStack + folder,
                entries = emptyList(),
                isLoadingEntries = true,
                error = null,
            )
        }
        loadChildren()
    }

    fun openBreadcrumb(index: Int) {
        val stack = _state.value.folderStack
        if (index < 0 || index >= stack.lastIndex) return
        _state.update {
            it.copy(
                folderStack = stack.take(index + 1),
                entries = emptyList(),
                isLoadingEntries = true,
                error = null,
            )
        }
        loadChildren()
    }

    fun goBack() {
        val stack = _state.value.folderStack
        when {
            stack.isEmpty() -> Unit
            stack.size == 1 -> {
                entriesJob?.cancel()
                _state.update {
                    it.copy(
                        folderStack = emptyList(),
                        entries = emptyList(),
                        isLoadingEntries = false,
                        error = null,
                    )
                }
            }

            else -> {
                _state.update {
                    it.copy(
                        folderStack = stack.dropLast(1),
                        entries = emptyList(),
                        isLoadingEntries = true,
                        error = null,
                    )
                }
                loadChildren()
            }
        }
    }

    fun openText(file: LectureEntry) {
        if (!file.kind.isText) return
        viewerJob?.cancel()
        _state.update {
            it.copy(viewer = LectureViewerState(entry = file, isLoading = true))
        }
        viewerJob = viewModelScope.launch {
            try {
                val content = repository.readText(file)
                _state.update { current ->
                    val viewer = current.viewer
                    if (viewer?.entry?.documentId == file.documentId) {
                        current.copy(
                            viewer = viewer.copy(
                                content = content,
                                isLoading = false,
                                error = null,
                            ),
                        )
                    } else {
                        current
                    }
                }
            } catch (e: Exception) {
                _state.update { current ->
                    val viewer = current.viewer
                    if (viewer?.entry?.documentId == file.documentId) {
                        current.copy(
                            viewer = viewer.copy(
                                isLoading = false,
                                error = e.message ?: "Could not read ${file.name}",
                            ),
                        )
                    } else {
                        current
                    }
                }
            }
        }
    }

    fun retryViewer() {
        _state.value.viewer?.entry?.let(::openText)
    }

    fun closeViewer() {
        viewerJob?.cancel()
        _state.update { it.copy(viewer = null) }
    }

    private fun loadRootFolders() {
        foldersJob?.cancel()
        foldersJob = viewModelScope.launch {
            _state.update { it.copy(isLoadingFolders = true, error = null) }
            try {
                val folders = repository.listLectures()
                val stack = _state.value.folderStack
                if (stack.isEmpty()) {
                    _state.update {
                        it.copy(rootFolders = folders, isLoadingFolders = false)
                    }
                    return@launch
                }
                val top = folders.firstOrNull { it.documentId == stack.first().documentId }
                if (top == null) {
                    _state.update {
                        it.copy(
                            rootFolders = folders,
                            folderStack = emptyList(),
                            entries = emptyList(),
                            isLoadingFolders = false,
                            isLoadingEntries = false,
                        )
                    }
                } else {
                    val newStack = listOf(top) + stack.drop(1).filter { it.isDirectory }
                    _state.update {
                        it.copy(
                            rootFolders = folders,
                            folderStack = newStack,
                            isLoadingFolders = false,
                        )
                    }
                    loadChildren()
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoadingFolders = false,
                        error = e.message ?: "Could not list lecture folders",
                    )
                }
            }
        }
    }

    private fun loadChildren() {
        val folder = _state.value.folderStack.lastOrNull() ?: return
        entriesJob?.cancel()
        entriesJob = viewModelScope.launch {
            _state.update { it.copy(isLoadingEntries = true, error = null) }
            try {
                val entries = repository.listChildren(folder)
                _state.update {
                    it.copy(entries = entries, isLoadingEntries = false)
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isLoadingEntries = false,
                        error = e.message ?: "Could not list ${folder.name}",
                    )
                }
            }
        }
    }
}
