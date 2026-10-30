package dev.idefix.app

import dev.idefix.app.domain.model.AppSettings
import dev.idefix.app.domain.model.SearchMode
import dev.idefix.app.domain.model.ThemeMode
import dev.idefix.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class FakeSettingsRepository(initial: AppSettings = AppSettings()) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state

    override suspend fun setVault(uri: String, displayName: String) {
        state.update { it.copy(vaultUri = uri, vaultName = displayName) }
    }

    override suspend fun clearVault() {
        state.update { it.copy(vaultUri = null, vaultName = null) }
    }

    override suspend fun setTheme(mode: ThemeMode) {
        state.update { it.copy(themeMode = mode) }
    }

    override suspend fun setIncludeHidden(enabled: Boolean) {
        state.update { it.copy(includeHidden = enabled) }
    }

    override suspend fun setCaseSensitive(enabled: Boolean) {
        state.update { it.copy(caseSensitiveSearch = enabled) }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        state.update { it.copy(dynamicColor = enabled) }
    }

    override suspend fun setLastSearchMode(mode: SearchMode) {
        state.update { it.copy(lastSearchMode = mode) }
    }

    override suspend fun setRecentLimit(limit: Int) {
        state.update { it.copy(recentLimit = limit) }
    }
}
