package dev.idefix.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.idefix.app.domain.model.IndexSummary
import dev.idefix.app.domain.model.RecentFile
import dev.idefix.app.domain.model.MODUL_PREFIX
import dev.idefix.app.domain.model.RecentSearch
import dev.idefix.app.domain.model.SearchCommand
import dev.idefix.app.domain.model.SearchMode
import dev.idefix.app.domain.model.SearchResult
import dev.idefix.app.domain.repository.RecentFilesRepository
import dev.idefix.app.domain.repository.SearchRepository
import dev.idefix.app.domain.repository.SettingsRepository
import dev.idefix.app.util.NoteSlug
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val mode: SearchMode = SearchMode.FILENAME,
    val caseSensitive: Boolean = false,
    val results: List<SearchResult> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null,
    val hasSearched: Boolean = false,
    val searchedQuery: String = "",
    val selectedIndex: Int = 0,
    val summary: IndexSummary = IndexSummary(0, null, 0),
    val recentFiles: List<RecentFile> = emptyList(),
    val recentSearches: List<RecentSearch> = emptyList(),
    val durationMs: Long = 0,
    /** True while the next key typed into an empty field should act as a command. */
    val commandArmed: Boolean = true,
    /** True while the query is the title of a note that does not exist yet. */
    val creatingNote: Boolean = false,
    /** True when file-name results must start with the query (the Modul_ command). */
    val prefixOnly: Boolean = false,
    /** Bumped whenever a command rewrites the field so the text caret can follow. */
    val commandRevision: Int = 0,
) {
    val selectedResult: SearchResult?
        get() = results.getOrNull(selectedIndex)

    val newNoteName: String
        get() = NoteSlug.slugify(query)

    val placeholder: String
        get() = when {
            creatingNote -> "Title for new note…"
            mode == SearchMode.TEXT -> "Grep note contents…"
            mode == SearchMode.FILENAME -> "Search file names…"
            mode == SearchMode.LITERAL -> "Exact text…"
            else -> "Regular expression…"
        }
}

class SearchViewModel(
    private val searchRepository: SearchRepository,
    private val recentFilesRepository: RecentFilesRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var initialisedFromSettings = false
    private var savedScrollIndex = 0
    private var savedScrollOffset = 0

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                val previous = _state.value
                val first = !initialisedFromSettings
                val interacted = previous.query.isNotEmpty() ||
                    previous.commandRevision > 0 ||
                    previous.creatingNote
                val caseChanged = previous.caseSensitive != settings.caseSensitiveSearch
                _state.update {
                    it.copy(
                        // Apply the stored mode once, unless the user already
                        // picked one while the settings were still loading.
                        mode = if (first && !interacted) settings.lastSearchMode else it.mode,
                        caseSensitive = settings.caseSensitiveSearch,
                    )
                }
                if (!first && caseChanged && previous.query.isNotBlank()) {
                    scheduleSearch(immediate = true)
                }
                initialisedFromSettings = true
            }
        }
        viewModelScope.launch {
            recentFilesRepository.recentFiles.collect { files ->
                _state.update { it.copy(recentFiles = files) }
            }
        }
        viewModelScope.launch {
            recentFilesRepository.recentSearches.collect { searches ->
                _state.update { it.copy(recentSearches = searches) }
            }
        }
        refreshSummary()
    }

    fun setQuery(query: String, immediate: Boolean = false) {
        val current = _state.value
        if (current.commandArmed && !current.creatingNote && current.query.isEmpty() && query.length == 1) {
            val command = SearchCommand.fromKey(query[0])
            if (command != null) {
                applyCommand(command)
                return
            }
        }
        if (query.isEmpty()) {
            resetFilter()
            return
        }
        _state.update {
            it.copy(
                query = query,
                selectedIndex = 0,
                commandArmed = false,
            )
        }
        scheduleSearch(immediate)
    }

    /**
     * Runs a first-key command. Called by the keyboard path in [setQuery] and by
     * the command chips shown while the field is empty.
     */
    fun applyCommand(command: SearchCommand) {
        _state.update { it.copy(commandRevision = it.commandRevision + 1) }
        when (command) {
            SearchCommand.FILENAME -> switchMode(SearchMode.FILENAME)
            SearchCommand.GREP -> switchMode(SearchMode.TEXT)
            SearchCommand.MODUL -> {
                switchMode(SearchMode.FILENAME)
                _state.update {
                    it.copy(query = MODUL_PREFIX, selectedIndex = 0, prefixOnly = true)
                }
                scheduleSearch(immediate = true)
            }

            SearchCommand.NEW_NOTE -> {
                searchJob?.cancel()
                _state.update {
                    it.copy(
                        query = "",
                        results = emptyList(),
                        isSearching = false,
                        error = null,
                        hasSearched = false,
                        searchedQuery = "",
                        selectedIndex = 0,
                        durationMs = 0,
                        creatingNote = true,
                        commandArmed = false,
                        prefixOnly = false,
                    )
                }
            }
        }
    }

    /** Re-arms the first-key commands when the search screen is opened again. */
    fun rearmCommand() {
        _state.update {
            if (it.query.isBlank() && !it.creatingNote) it.copy(commandArmed = true) else it
        }
    }

    fun setMode(mode: SearchMode) {
        val current = _state.value
        if (current.mode == mode && !current.prefixOnly && !current.creatingNote) return
        switchMode(mode)
        scheduleSearch(immediate = true)
    }

    private fun switchMode(mode: SearchMode) {
        _state.update {
            it.copy(
                mode = mode,
                creatingNote = false,
                commandArmed = false,
                prefixOnly = false,
            )
        }
        viewModelScope.launch { settingsRepository.setLastSearchMode(mode) }
    }

    fun setCaseSensitive(enabled: Boolean) {
        _state.update { it.copy(caseSensitive = enabled) }
        viewModelScope.launch { settingsRepository.setCaseSensitive(enabled) }
        if (_state.value.query.isNotBlank()) scheduleSearch(immediate = true)
    }

    fun selectIndex(index: Int) {
        val size = _state.value.results.size
        if (size == 0) return
        _state.update { it.copy(selectedIndex = index.coerceIn(0, size - 1)) }
    }

    fun moveSelection(delta: Int) {
        selectIndex(_state.value.selectedIndex + delta)
    }

    fun clearQuery() {
        resetFilter()
    }

    /** Returns to the empty command state; used when backspace has no text left. */
    fun resetFilter() {
        searchJob?.cancel()
        _state.update {
            it.copy(
                query = "",
                mode = SearchMode.FILENAME,
                results = emptyList(),
                isSearching = false,
                error = null,
                hasSearched = false,
                searchedQuery = "",
                selectedIndex = 0,
                durationMs = 0,
                creatingNote = false,
                commandArmed = true,
                prefixOnly = false,
                commandRevision = it.commandRevision + 1,
            )
        }
        viewModelScope.launch { settingsRepository.setLastSearchMode(SearchMode.FILENAME) }
    }

    fun submit() {
        if (_state.value.creatingNote) return
        if (_state.value.query.isNotBlank()) {
            scheduleSearch(immediate = true)
            viewModelScope.launch {
                recentFilesRepository.recordSearch(_state.value.query.trim(), _state.value.mode)
            }
        }
    }

    fun recordOpened() {
        val current = _state.value
        if (current.query.isNotBlank()) {
            viewModelScope.launch {
                recentFilesRepository.recordSearch(current.query.trim(), current.mode)
            }
        }
    }

    fun refreshResults() {
        if (_state.value.query.isNotBlank()) scheduleSearch(immediate = true)
    }

    fun refreshSummary() {
        viewModelScope.launch {
            val summary = runCatching { searchRepository.summary() }.getOrDefault(IndexSummary(0, null, 0))
            _state.update { it.copy(summary = summary) }
        }
    }

    /**
     * Scroll position is kept outside [SearchUiState] on purpose: writing it to
     * the state flow on every scroll frame would recompose the whole screen and
     * make the result list stutter.
     */
    fun scrollPosition(): Pair<Int, Int> = savedScrollIndex to savedScrollOffset

    fun saveScroll(firstVisibleItem: Int, firstVisibleOffset: Int) {
        savedScrollIndex = firstVisibleItem
        savedScrollOffset = firstVisibleOffset
    }

    fun useRecentSearch(search: RecentSearch) {
        _state.update {
            it.copy(mode = search.mode, creatingNote = false, commandArmed = false, prefixOnly = false)
        }
        setQuery(search.query, immediate = true)
    }

    private fun scheduleSearch(immediate: Boolean) {
        searchJob?.cancel()
        val query = _state.value.query.trim()
        if (query.isEmpty()) {
            _state.update {
                it.copy(
                    results = emptyList(),
                    isSearching = false,
                    error = null,
                    hasSearched = false,
                    searchedQuery = "",
                    durationMs = 0,
                )
            }
            return
        }
        val mode = _state.value.mode
        val caseSensitive = _state.value.caseSensitive
        val prefixOnly = _state.value.prefixOnly
        searchJob = viewModelScope.launch {
            // Filename matching is cheap and should track typing closely. Content
            // searches stay debounced because they scan every indexed document.
            if (!immediate) delay(if (mode == SearchMode.FILENAME) 60 else 160)
            _state.update { it.copy(isSearching = true, error = null) }
            val outcome = searchRepository.search(query, mode, caseSensitive, prefixOnly = prefixOnly)
            if (_state.value.query.trim() != query ||
                _state.value.mode != mode ||
                _state.value.prefixOnly != prefixOnly
            ) {
                return@launch
            }
            _state.update {
                it.copy(
                    results = outcome.results,
                    isSearching = false,
                    error = outcome.error,
                    hasSearched = true,
                    searchedQuery = query,
                    selectedIndex = 0,
                    durationMs = outcome.durationMs,
                )
            }
        }
    }
}
