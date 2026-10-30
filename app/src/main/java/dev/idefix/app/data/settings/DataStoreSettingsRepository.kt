package dev.idefix.app.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.idefix.app.domain.model.AppSettings
import dev.idefix.app.domain.model.SearchMode
import dev.idefix.app.domain.model.ThemeMode
import dev.idefix.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.idefixSettings: DataStore<Preferences> by preferencesDataStore(name = "idefix_settings")

class DataStoreSettingsRepository(context: Context) : SettingsRepository {

    private val dataStore = context.applicationContext.idefixSettings

    private object Keys {
        val vaultUri = stringPreferencesKey("vault_uri")
        val vaultName = stringPreferencesKey("vault_name")
        val theme = stringPreferencesKey("theme")
        val includeHidden = booleanPreferencesKey("include_hidden")
        val caseSensitive = booleanPreferencesKey("case_sensitive")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val lastSearchMode = stringPreferencesKey("last_search_mode")
        val recentLimit = intPreferencesKey("recent_limit")
    }

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(androidx.datastore.preferences.core.emptyPreferences()) else throw error
        }
        .map { prefs ->
            AppSettings(
                vaultUri = prefs[Keys.vaultUri],
                vaultName = prefs[Keys.vaultName],
                themeMode = ThemeMode.fromStorage(prefs[Keys.theme]),
                includeHidden = prefs[Keys.includeHidden] ?: false,
                caseSensitiveSearch = prefs[Keys.caseSensitive] ?: false,
                dynamicColor = prefs[Keys.dynamicColor] ?: true,
                lastSearchMode = SearchMode.fromStorage(prefs[Keys.lastSearchMode]),
                recentLimit = (prefs[Keys.recentLimit] ?: 50).coerceIn(10, 200),
            )
        }
        .distinctUntilChanged()

    override suspend fun setVault(uri: String, displayName: String) {
        dataStore.edit {
            it[Keys.vaultUri] = uri
            it[Keys.vaultName] = displayName
        }
    }

    override suspend fun clearVault() {
        dataStore.edit {
            it.remove(Keys.vaultUri)
            it.remove(Keys.vaultName)
        }
    }

    override suspend fun setTheme(mode: ThemeMode) {
        dataStore.edit { it[Keys.theme] = mode.name }
    }

    override suspend fun setIncludeHidden(enabled: Boolean) {
        dataStore.edit { it[Keys.includeHidden] = enabled }
    }

    override suspend fun setCaseSensitive(enabled: Boolean) {
        dataStore.edit { it[Keys.caseSensitive] = enabled }
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        dataStore.edit { it[Keys.dynamicColor] = enabled }
    }

    override suspend fun setLastSearchMode(mode: SearchMode) {
        dataStore.edit { it[Keys.lastSearchMode] = mode.name }
    }

    override suspend fun setRecentLimit(limit: Int) {
        dataStore.edit { it[Keys.recentLimit] = limit.coerceIn(10, 200) }
    }
}
