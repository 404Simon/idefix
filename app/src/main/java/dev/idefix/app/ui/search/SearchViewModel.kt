package dev.idefix.app.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.idefix.app.domain.model.IndexSummary
import dev.idefix.app.domain.model.RecentFile
import dev.idefix.app.domain.model.RecentSearch
import dev.idefix.app.domain.model.SearchMode
import dev.idefix.app.domain.model.SearchResult
import dev.idefix.app.domain.repository.RecentFilesRepository
import dev.idefix.app.domain.repository.SearchRepository
import dev.idefix.app.domain.repository.SettingsRepository
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
    val firstVisibleItem: Int = 0,
    val firstVisibleOffset: Int = 0,
    val durationMs: Long = 0,
) {
    val selectedResult: SearchResult?
        get() = results.getOrNull(selectedIndex)
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

    init {
        viewModelScope.launch {
            settingsRepository.settings.collect { settings ->
                val previous = _state.value
                val modeChanged = previous.mode != settings.lastSearchMode
                val caseChanged = previous.caseSensitive != settings.caseSensitiveSearch
                _state.update {
                    it.copy(mode = settings.lastSearchMode, caseSensitive = settings.caseSensitiveSearch)
                }
                if (initialisedFromSettings && (modeChanged || caseChanged) && previous.query.isNotBlank()) {
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
        _state.update { it.copy(query = query, selectedIndex = 0) }
        scheduleSearch(immediate)
    }

    fun setMode(mode: SearchMode) {
        if (_state.value.mode == mode) return
        _state.update { it.copy(mode = mode) }
        viewModelScope.launch { settingsRepository.setLastSearchMode(mode) }
        scheduleSearch(immediate = true)
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
        searchJob?.cancel()
        _state.update {
            it.copy(
                query = "",
                results = emptyList(),
                isSearching = false,
                error = null,
                hasSearched = false,
                searchedQuery = "",
            )
        }
    }

    fun submit() {
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

    fun saveScroll(firstVisibleItem: Int, firstVisibleOffset: Int) {
        _state.update { it.copy(firstVisibleItem = firstVisibleItem, firstVisibleOffset = firstVisibleOffset) }
    }

    fun useRecentSearch(search: RecentSearch) {
        _state.update { it.copy(mode = search.mode) }
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
        searchJob = viewModelScope.launch {
            if (!immediate) delay(180)
            _state.update { it.copy(isSearching = true, error = null) }
            val outcome = searchRepository.search(query, mode, caseSensitive)
            if (_state.value.query.trim() != query || _state.value.mode != mode) return@launch
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
