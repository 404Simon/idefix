package dev.idefix.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.idefix.app.AppContainer
import dev.idefix.app.BuildConfig
import dev.idefix.app.domain.model.AppSettings
import dev.idefix.app.domain.model.IndexState
import dev.idefix.app.domain.model.IndexSummary
import dev.idefix.app.domain.model.SearchMode
import dev.idefix.app.domain.model.ThemeMode
import dev.idefix.app.domain.model.VaultInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class StartupPhase { LOADING, WELCOME, READY }

data class AppUiState(
    val settings: AppSettings = AppSettings(),
    val vault: VaultInfo? = null,
    val phase: StartupPhase = StartupPhase.LOADING,
    val startupError: String? = null,
    val indexState: IndexState = IndexState.Idle,
    val indexSummary: IndexSummary = IndexSummary(0, null, 0),
    val message: String? = null,
    val versionName: String = BuildConfig.VERSION_NAME,
)

class AppViewModel(private val container: AppContainer) : ViewModel() {

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()

    private var initialized = false

    init {
        viewModelScope.launch {
            container.settings.settings.collect { settings ->
                _uiState.update { it.copy(settings = settings) }
                container.updateRecentLimit(settings.recentLimit)
                if (!initialized) {
                    initialized = true
                    initializeVault(settings)
                }
            }
        }
        viewModelScope.launch {
            container.indexRepository.state.collect { state ->
                _uiState.update { it.copy(indexState = state) }
                if (state is IndexState.Ready) {
                    refreshSummary()
                }
            }
        }
    }

    private fun initializeVault(settings: AppSettings) {
        viewModelScope.launch {
            val uri = settings.vaultUri
            if (uri == null) {
                _uiState.update { it.copy(phase = StartupPhase.WELCOME, vault = null) }
                return@launch
            }
            val info = container.vaultRepository.restoreVault(uri, settings.vaultName)
            if (info == null || !info.readable) {
                _uiState.update {
                    it.copy(
                        phase = StartupPhase.WELCOME,
                        startupError = "The vault permission is no longer available. Select the folder again.",
                    )
                }
                return@launch
            }
            _uiState.update { it.copy(phase = StartupPhase.READY, vault = info, startupError = null) }
            refreshSummary()
            container.recentFilesRepository.removeStale()
            val summary = container.indexRepository.summary()
            if (summary.fileCount > 0) {
                _uiState.update {
                    it.copy(indexState = IndexState.Ready(summary.fileCount, summary.lastIndexedAt ?: 0L))
                }
            }
            container.indexRepository.reconcile(settings.includeHidden)
        }
    }

    fun readyAfterVaultSelection(info: VaultInfo) {
        _uiState.update { it.copy(phase = StartupPhase.READY, vault = info, startupError = null) }
    }

    fun onVaultSelected(uri: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(phase = StartupPhase.LOADING, startupError = null) }
            val previous = runCatching { container.settings.settings.first().vaultUri }.getOrNull()
            try {
                val info = container.vaultRepository.adoptVault(uri, null)
                if (previous != null && previous != uri) {
                    container.indexRepository.clearIndex()
                }
                _uiState.update { it.copy(phase = StartupPhase.READY, vault = info) }
                refreshSummary()
                val hidden = container.settings.settings.first().includeHidden
                container.indexRepository.rebuild(hidden)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        phase = StartupPhase.WELCOME,
                        startupError = e.message ?: "The folder could not be opened",
                    )
                }
            }
        }
    }

    fun vaultSelectionCancelled() {
        if (_uiState.value.phase == StartupPhase.LOADING) {
            _uiState.update { it.copy(phase = if (it.vault == null) StartupPhase.WELCOME else StartupPhase.READY) }
        }
    }

    fun rebuildIndex() {
        viewModelScope.launch {
            val hidden = container.settings.settings.first().includeHidden
            container.indexRepository.rebuild(hidden)
            refreshSummary()
        }
    }

    fun cancelIndexing() {
        container.indexRepository.cancel()
    }

    fun setIncludeHidden(enabled: Boolean) {
        viewModelScope.launch {
            container.settings.setIncludeHidden(enabled)
            container.indexRepository.rebuild(enabled)
            refreshSummary()
        }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { container.settings.setTheme(mode) }
    }

    fun setCaseSensitive(enabled: Boolean) {
        viewModelScope.launch { container.settings.setCaseSensitive(enabled) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { container.settings.setDynamicColor(enabled) }
    }

    fun setLastSearchMode(mode: SearchMode) {
        viewModelScope.launch { container.settings.setLastSearchMode(mode) }
    }

    fun setRecentLimit(limit: Int) {
        viewModelScope.launch {
            container.settings.setRecentLimit(limit)
            container.updateRecentLimit(limit)
        }
    }

    fun switchVault() {
        viewModelScope.launch {
            container.vaultRepository.clearVault()
            container.indexRepository.clearIndex()
            _uiState.update {
                it.copy(phase = StartupPhase.WELCOME, vault = null, indexState = IndexState.Idle)
            }
        }
    }

    fun showMessage(message: String) {
        _uiState.update { it.copy(message = message) }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }

    private fun refreshSummary() {
        viewModelScope.launch {
            val summary = container.indexRepository.summary()
            _uiState.update { it.copy(indexSummary = summary) }
        }
    }
}
