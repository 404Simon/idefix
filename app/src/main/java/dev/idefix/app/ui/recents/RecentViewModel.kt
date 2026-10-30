package dev.idefix.app.ui.recents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.idefix.app.domain.model.RecentFile
import dev.idefix.app.domain.model.RecentSearch
import dev.idefix.app.domain.repository.RecentFilesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RecentUiState(
    val message: String? = null,
)

class RecentViewModel(
    private val repository: RecentFilesRepository,
) : ViewModel() {

    val recentFiles: StateFlow<List<RecentFile>> = repository.recentFiles
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val recentSearches: StateFlow<List<RecentSearch>> = repository.recentSearches
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _state = MutableStateFlow(RecentUiState())
    val state: StateFlow<RecentUiState> = _state.asStateFlow()

    fun clearHistory() {
        viewModelScope.launch {
            repository.clearHistory()
            _state.update { it.copy(message = "History cleared") }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }
}
