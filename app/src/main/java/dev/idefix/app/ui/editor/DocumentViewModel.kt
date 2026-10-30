package dev.idefix.app.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.idefix.app.domain.editor.SaveCheck
import dev.idefix.app.domain.editor.SaveGuard
import dev.idefix.app.domain.model.DocumentMode
import dev.idefix.app.domain.model.VaultFile
import dev.idefix.app.domain.repository.IndexRepository
import dev.idefix.app.domain.repository.RecentFilesRepository
import dev.idefix.app.domain.repository.SearchRepository
import dev.idefix.app.domain.repository.VaultRepository
import dev.idefix.app.util.Hashing
import dev.idefix.app.util.MarkdownLines
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OpenDocumentRequest(
    val fileId: Long?,
    val uri: String,
    val relativePath: String,
    val filename: String,
    /** A matching line to surface in the reader; does not change the mode. */
    val line: Int? = null,
    val mode: DocumentMode = DocumentMode.PREVIEW,
)

data class SelectionRequest(val id: Long, val offset: Int)

data class FindMatch(val offset: Int, val line: Int, val snippet: String)

data class SaveConflict(val currentContent: String, val message: String)

data class DocumentUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val file: VaultFile? = null,
    val fileId: Long? = null,
    val originalContent: String = "",
    val editContent: String = "",
    val fingerprint: String = "",
    val mode: DocumentMode = DocumentMode.PREVIEW,
    val isDirty: Boolean = false,
    val isSaving: Boolean = false,
    val saveMessage: String? = null,
    val cursorLine: Int = 1,
    val cursorOffset: Int = 0,
    val scrollOffset: Int = 0,
    val selectionRequest: SelectionRequest? = null,
    val findVisible: Boolean = false,
    val findQuery: String = "",
    val findMatches: List<FindMatch> = emptyList(),
    val findIndex: Int = -1,
    val conflict: SaveConflict? = null,
) {
    val hasDocument: Boolean get() = file != null
    val activeFindMatch: FindMatch? get() = findMatches.getOrNull(findIndex)
}

class DocumentViewModel(
    private val vaultRepository: VaultRepository,
    private val indexRepository: IndexRepository,
    private val recentFilesRepository: RecentFilesRepository,
    private val searchRepository: SearchRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(DocumentUiState())
    val state: StateFlow<DocumentUiState> = _state.asStateFlow()

    private var openJob: Job? = null
    private var positionJob: Job? = null
    private var selectionId = 0L

    fun open(request: OpenDocumentRequest) {
        openJob?.cancel()
        savePositionNow()
        _state.value = DocumentUiState(isLoading = true, fileId = request.fileId)
        openJob = viewModelScope.launch {
            try {
                val resolved = vaultRepository.resolve(request.relativePath)
                val file = resolved ?: VaultFile(
                    documentId = "",
                    relativePath = request.relativePath,
                    displayName = request.filename,
                    uri = request.uri,
                )
                val content = vaultRepository.readText(file)
                val fingerprint = Hashing.sha256(content)
                val fileId = request.fileId ?: searchRepository.fileIdForPath(file.relativePath)
                val position = fileId?.let { recentFilesRepository.positionFor(it) }
                val cursorOffset = if (request.line != null) {
                    MarkdownLines.offsetForLine(content, request.line)
                } else {
                    (position?.cursorOffset ?: 0).coerceIn(0, content.length)
                }
                val cursorLine = if (request.line != null) {
                    request.line
                } else {
                    position?.cursorLine ?: 1
                }
                selectionId++
                _state.value = DocumentUiState(
                    isLoading = false,
                    file = file,
                    fileId = fileId,
                    originalContent = content,
                    editContent = content,
                    fingerprint = fingerprint,
                    mode = request.mode,
                    cursorLine = cursorLine,
                    cursorOffset = cursorOffset,
                    scrollOffset = position?.scrollOffset ?: 0,
                    selectionRequest = SelectionRequest(selectionId, cursorOffset),
                )
                if (fileId != null) {
                    recentFilesRepository.recordOpen(file, fileId, request.line, cursorOffset)
                    // Keep an index entry fresh when another app changed the file since indexing.
                    viewModelScope.launch { runCatching { indexRepository.reindexFile(file) } }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isLoading = false, error = e.message ?: "Could not open the note")
                }
            }
        }
    }

    fun retry() {
        val current = _state.value
        val file = current.file ?: return
        open(
            OpenDocumentRequest(
                fileId = current.fileId,
                uri = file.uri,
                relativePath = file.relativePath,
                filename = file.displayName,
                mode = current.mode,
            ),
        )
    }

    fun setMode(mode: DocumentMode) {
        if (_state.value.mode == mode) return
        selectionId++
        _state.update {
            it.copy(
                mode = mode,
                selectionRequest = SelectionRequest(selectionId, it.cursorOffset),
            )
        }
    }

    fun onTextChanged(text: String, cursorOffset: Int) {
        val current = _state.value
        val matches = if (current.findQuery.isNotBlank()) findMatches(text, current.findQuery) else current.findMatches
        _state.update {
            it.copy(
                editContent = text,
                isDirty = text != it.originalContent,
                cursorOffset = cursorOffset.coerceIn(0, text.length),
                cursorLine = MarkdownLines.lineNumberForOffset(text, cursorOffset),
                findMatches = matches,
                findIndex = if (matches.isEmpty()) -1 else it.findIndex.coerceIn(0, matches.lastIndex),
            )
        }
        schedulePositionSave()
    }

    fun onCursorMoved(offset: Int) {
        val content = _state.value.editContent
        _state.update {
            it.copy(
                cursorOffset = offset.coerceIn(0, content.length),
                cursorLine = MarkdownLines.lineNumberForOffset(content, offset),
            )
        }
        schedulePositionSave()
    }

    fun onScrollChanged(offset: Int) {
        _state.update { it.copy(scrollOffset = offset) }
        schedulePositionSave()
    }

    fun save(onSaved: (() -> Unit)? = null) {
        val current = _state.value
        val file = current.file ?: return
        if (current.isSaving) return
        if (!current.isDirty && onSaved != null) {
            onSaved()
            return
        }
        _state.update { it.copy(isSaving = true, saveMessage = null) }
        viewModelScope.launch {
            try {
                val diskContent = vaultRepository.readText(file)
                when (val check = SaveGuard.check(current.fingerprint, diskContent)) {
                    SaveCheck.Clean -> writeAndFinish(file, _state.value.editContent, "Saved", onSaved)
                    is SaveCheck.ExternalChange -> {
                        _state.update {
                            it.copy(
                                isSaving = false,
                                conflict = SaveConflict(
                                    currentContent = check.currentContent,
                                    message = "This note was changed outside Idefix since you opened it.",
                                ),
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(isSaving = false, saveMessage = e.message ?: "Could not save the note")
                }
            }
        }
    }

    fun overwriteConflict() {
        val current = _state.value
        val file = current.file ?: return
        if (current.conflict == null) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val content = _state.value.editContent
                vaultRepository.writeText(file, content)
                val newFingerprint = Hashing.sha256(content)
                _state.update {
                    it.copy(
                        originalContent = content,
                        fingerprint = newFingerprint,
                        isDirty = it.editContent != content,
                        isSaving = false,
                        conflict = null,
                        saveMessage = "Saved; external changes were overwritten",
                    )
                }
                runCatching { indexRepository.reindexFile(file) }
                savePositionNow()
            } catch (e: Exception) {
                _state.update {
                    it.copy(isSaving = false, saveMessage = e.message ?: "Could not overwrite the note")
                }
            }
        }
    }

    fun reloadConflict() {
        val conflict = _state.value.conflict ?: return
        selectionId++
        _state.update {
            it.copy(
                originalContent = conflict.currentContent,
                editContent = conflict.currentContent,
                fingerprint = Hashing.sha256(conflict.currentContent),
                isDirty = false,
                conflict = null,
                selectionRequest = SelectionRequest(selectionId, 0),
                cursorOffset = 0,
                cursorLine = 1,
                saveMessage = "Reloaded the version from disk",
                findMatches = if (it.findQuery.isBlank()) emptyList() else findMatches(conflict.currentContent, it.findQuery),
            )
        }
    }

    fun dismissConflict() {
        _state.update { it.copy(conflict = null) }
    }

    fun discardChanges() {
        _state.update {
            it.copy(
                editContent = it.originalContent,
                isDirty = false,
                conflict = null,
                saveMessage = "Edits discarded",
            )
        }
        savePositionNow()
    }

    fun close() {
        savePositionNow()
        openJob?.cancel()
        _state.value = DocumentUiState()
    }

    fun showFind() {
        _state.update { it.copy(findVisible = true) }
    }

    fun hideFind() {
        _state.update { it.copy(findVisible = false) }
    }

    fun setFindQuery(query: String) {
        val matches = if (query.isBlank()) emptyList() else findMatches(_state.value.editContent, query)
        _state.update {
            it.copy(
                findQuery = query,
                findMatches = matches,
                findIndex = if (matches.isEmpty()) -1 else 0,
            )
        }
        matches.firstOrNull()?.let(::jumpTo)
    }

    fun findNext() {
        val current = _state.value
        if (current.findMatches.isEmpty()) return
        val next = (current.findIndex + 1).mod(current.findMatches.size)
        _state.update { it.copy(findIndex = next) }
        jumpTo(current.findMatches[next])
    }

    fun findPrevious() {
        val current = _state.value
        if (current.findMatches.isEmpty()) return
        val previous = if (current.findIndex <= 0) current.findMatches.lastIndex else current.findIndex - 1
        _state.update { it.copy(findIndex = previous) }
        jumpTo(current.findMatches[previous])
    }

    fun clearSaveMessage() {
        _state.update { it.copy(saveMessage = null) }
    }

    fun rename(newName: String, onRenamed: (VaultFile) -> Unit = {}) {
        val current = _state.value
        val file = current.file ?: return
        if (current.isDirty) {
            _state.update { it.copy(saveMessage = "Save your changes before renaming") }
            return
        }
        viewModelScope.launch {
            try {
                val oldId = current.fileId
                val renamed = vaultRepository.renameFile(file, newName)
                if (oldId != null) indexRepository.removeFile(oldId)
                indexRepository.reindexFile(renamed)
                val newId = searchRepository.fileIdForPath(renamed.relativePath)
                _state.update {
                    it.copy(file = renamed, fileId = newId, saveMessage = "Renamed to ${renamed.displayName}")
                }
                if (newId != null) {
                    recentFilesRepository.recordOpen(renamed, newId, current.cursorLine, current.cursorOffset)
                }
                onRenamed(renamed)
            } catch (e: Exception) {
                _state.update { it.copy(saveMessage = e.message ?: "Could not rename the note") }
            }
        }
    }

    fun delete(onDeleted: () -> Unit = {}) {
        val current = _state.value
        val file = current.file ?: return
        viewModelScope.launch {
            try {
                vaultRepository.deleteFile(file)
                current.fileId?.let { indexRepository.removeFile(it) }
                _state.update { it.copy(saveMessage = "Deleted ${file.displayName}") }
                onDeleted()
            } catch (e: Exception) {
                _state.update { it.copy(saveMessage = e.message ?: "Could not delete the note") }
            }
        }
    }

    private suspend fun writeAndFinish(
        file: VaultFile,
        content: String,
        message: String,
        onSaved: (() -> Unit)? = null,
    ) {
        try {
            vaultRepository.writeText(file, content)
            val fingerprint = Hashing.sha256(content)
            _state.update {
                it.copy(
                    originalContent = content,
                    fingerprint = fingerprint,
                    isDirty = it.editContent != content,
                    isSaving = false,
                    saveMessage = message,
                )
            }
            val warning = runCatching { indexRepository.reindexFile(file) }.exceptionOrNull()
            if (warning != null) {
                _state.update {
                    it.copy(saveMessage = "Saved; index refresh failed: ${warning.message}")
                }
            }
            savePositionNow()
            onSaved?.invoke()
        } catch (e: Exception) {
            _state.update {
                it.copy(isSaving = false, saveMessage = e.message ?: "Could not save the note")
            }
        }
    }

    private fun schedulePositionSave() {
        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            delay(500)
            savePositionNow()
        }
    }

    private fun savePositionNow() {
        val current = _state.value
        val fileId = current.fileId ?: return
        if (!current.hasDocument) return
        viewModelScope.launch {
            runCatching {
                recentFilesRepository.updatePosition(
                    fileId = fileId,
                    cursorLine = current.cursorLine,
                    cursorOffset = current.cursorOffset,
                    scrollOffset = current.scrollOffset,
                    matchLine = null,
                )
            }
        }
    }

    private fun jumpTo(match: FindMatch) {
        selectionId++
        _state.update {
            it.copy(
                cursorLine = match.line,
                cursorOffset = match.offset,
                selectionRequest = SelectionRequest(selectionId, match.offset),
            )
        }
        savePositionNow()
    }

    private fun findMatches(content: String, query: String): List<FindMatch> {
        if (query.isEmpty()) return emptyList()
        val matches = ArrayList<FindMatch>()
        var index = content.indexOf(query, ignoreCase = true)
        while (index >= 0 && matches.size < 500) {
            val line = MarkdownLines.lineNumberForOffset(content, index)
            val lineText = MarkdownLines.lineText(content, line) ?: ""
            matches += FindMatch(offset = index, line = line, snippet = lineText.trim().take(160))
            index = content.indexOf(query, index + query.length.coerceAtLeast(1), ignoreCase = true)
        }
        return matches
    }
}
