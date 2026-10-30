package dev.idefix.app

import android.content.Context
import dev.idefix.app.data.database.IdefixDatabase
import dev.idefix.app.data.index.DefaultIndexRepository
import dev.idefix.app.data.recents.DefaultRecentFilesRepository
import dev.idefix.app.data.search.DefaultSearchRepository
import dev.idefix.app.data.settings.DataStoreSettingsRepository
import dev.idefix.app.data.tasks.DefaultTodoRepository
import dev.idefix.app.data.vault.SafVaultRepository
import dev.idefix.app.domain.repository.IndexRepository
import dev.idefix.app.domain.repository.RecentFilesRepository
import dev.idefix.app.domain.repository.SearchRepository
import dev.idefix.app.domain.repository.SettingsRepository
import dev.idefix.app.domain.repository.TodoRepository
import dev.idefix.app.domain.repository.VaultRepository

/** Manual dependency container. One instance per application process. */
class AppContainer(context: Context) {
    val settings: SettingsRepository = DataStoreSettingsRepository(context)
    val database: IdefixDatabase = IdefixDatabase.build(context)
    val vaultRepository: VaultRepository = SafVaultRepository(context, settings)
    val indexRepository: IndexRepository = DefaultIndexRepository(database, vaultRepository)
    val searchRepository: SearchRepository = DefaultSearchRepository(
        fileDao = database.fileDao(),
        ftsDao = database.ftsDao(),
    )
    val todoRepository: TodoRepository = DefaultTodoRepository(
        searchRepository = searchRepository,
        ftsDao = database.ftsDao(),
        vaultRepository = vaultRepository,
        indexRepository = indexRepository,
    )
    val recentFilesRepository: RecentFilesRepository = DefaultRecentFilesRepository(database.recentDao())
    fun updateRecentLimit(limit: Int) {
        recentFilesRepository.setLimit(limit)
    }
}
