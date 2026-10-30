package dev.idefix.app.domain.repository

import dev.idefix.app.domain.model.AppSettings
import dev.idefix.app.domain.model.SearchMode
import dev.idefix.app.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setVault(uri: String, displayName: String)
    suspend fun clearVault()
    suspend fun setTheme(mode: ThemeMode)
    suspend fun setIncludeHidden(enabled: Boolean)
    suspend fun setCaseSensitive(enabled: Boolean)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setLastSearchMode(mode: SearchMode)
    suspend fun setRecentLimit(limit: Int)
}
