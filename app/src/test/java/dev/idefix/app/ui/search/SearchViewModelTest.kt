package dev.idefix.app.ui.search

import dev.idefix.app.domain.model.AppSettings
import dev.idefix.app.domain.model.FileSummary
import dev.idefix.app.domain.model.IndexSummary
import dev.idefix.app.domain.model.RecentFile
import dev.idefix.app.domain.model.RecentSearch
import dev.idefix.app.domain.model.SearchMode
import dev.idefix.app.domain.model.SearchOutcome
import dev.idefix.app.domain.model.VaultFile
import dev.idefix.app.domain.repository.RecentFilesRepository
import dev.idefix.app.domain.repository.SearchRepository
import dev.idefix.app.domain.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun grepKeyIsConsumedAndArmsFullTextSearch() = runTest(dispatcher.scheduler) {
        val search = RecordingSearchRepository()
        val viewModel = viewModel(search)
        advanceUntilIdle()

        viewModel.setQuery("g")
        advanceUntilIdle()
        assertEquals("", viewModel.state.value.query)
        assertEquals(SearchMode.TEXT, viewModel.state.value.mode)
        assertFalse(viewModel.state.value.commandArmed)

        viewModel.setQuery("walrus")
        advanceUntilIdle()
        assertEquals("walrus", viewModel.state.value.query)
        assertEquals("walrus", search.lastQuery)
        assertEquals(SearchMode.TEXT, search.lastMode)
    }

    @Test
    fun filenameKeyIsTypedWhenFilenameModeIsAlreadyActive() = runTest(dispatcher.scheduler) {
        val search = RecordingSearchRepository()
        val viewModel = viewModel(search)
        advanceUntilIdle()

        // Filename is the default mode, so f is a normal query character here.
        viewModel.setQuery("f")
        advanceUntilIdle()
        assertEquals("f", viewModel.state.value.query)
        assertEquals(SearchMode.FILENAME, viewModel.state.value.mode)
        assertEquals("f", search.lastQuery)
        assertEquals(SearchMode.FILENAME, search.lastMode)
    }

    @Test
    fun filenameKeySwitchesBackFromFullTextAndIsConsumed() = runTest(dispatcher.scheduler) {
        val viewModel = viewModel(RecordingSearchRepository())
        advanceUntilIdle()

        viewModel.setQuery("g")
        advanceUntilIdle()
        assertEquals(SearchMode.TEXT, viewModel.state.value.mode)

        viewModel.rearmCommand()
        viewModel.setQuery("f")
        advanceUntilIdle()
        assertEquals("", viewModel.state.value.query)
        assertEquals(SearchMode.FILENAME, viewModel.state.value.mode)
    }

    @Test
    fun grepKeyIsTypedWhenFullTextModeIsAlreadyActive() = runTest(dispatcher.scheduler) {
        val viewModel = viewModel(RecordingSearchRepository())
        advanceUntilIdle()

        viewModel.setQuery("g")
        advanceUntilIdle()
        assertEquals(SearchMode.TEXT, viewModel.state.value.mode)

        viewModel.rearmCommand()
        viewModel.setQuery("g")
        advanceUntilIdle()
        assertEquals("g", viewModel.state.value.query)
        assertEquals(SearchMode.TEXT, viewModel.state.value.mode)
    }

    @Test
    fun modulKeyPresetsThePrefixAndUsesStrictPrefixSearch() = runTest(dispatcher.scheduler) {
        val search = RecordingSearchRepository()
        val viewModel = viewModel(search)
        advanceUntilIdle()

        viewModel.setQuery("m")
        advanceUntilIdle()
        assertEquals("Modul_", viewModel.state.value.query)
        assertEquals(SearchMode.FILENAME, viewModel.state.value.mode)
        assertTrue(viewModel.state.value.prefixOnly)
        assertEquals("Modul_", search.lastQuery)
        assertEquals(true, search.lastPrefixOnly)
    }

    @Test
    fun newNoteKeyTurnsTheQueryIntoASlugifiedTitle() = runTest(dispatcher.scheduler) {
        val viewModel = viewModel(RecordingSearchRepository())
        advanceUntilIdle()

        viewModel.setQuery("n")
        advanceUntilIdle()
        assertTrue(viewModel.state.value.creatingNote)
        assertEquals("", viewModel.state.value.query)

        viewModel.setQuery("Über Größe")
        advanceUntilIdle()
        assertTrue(viewModel.state.value.creatingNote)
        assertEquals("Ueber_Groesse", viewModel.state.value.newNoteName)
    }

    @Test
    fun aNormalFirstKeyStartsAFilenameSearchWithoutBeingConsumed() = runTest(dispatcher.scheduler) {
        val search = RecordingSearchRepository()
        val viewModel = viewModel(search)
        advanceUntilIdle()

        viewModel.setQuery("w")
        advanceUntilIdle()
        assertEquals("w", viewModel.state.value.query)
        assertEquals("w", search.lastQuery)
        assertEquals(SearchMode.FILENAME, search.lastMode)
    }

    @Test
    fun deletingTheLastCharacterResetsTheMode() = runTest(dispatcher.scheduler) {
        val viewModel = viewModel(RecordingSearchRepository())
        advanceUntilIdle()

        viewModel.setQuery("g")
        advanceUntilIdle()
        viewModel.setQuery("walrus")
        advanceUntilIdle()
        assertEquals(SearchMode.TEXT, viewModel.state.value.mode)

        viewModel.setQuery("")
        advanceUntilIdle()
        assertEquals(SearchMode.FILENAME, viewModel.state.value.mode)
        assertTrue(viewModel.state.value.commandArmed)
    }

    @Test
    fun backspaceOnAnEmptyFieldResetsTheActiveFilter() = runTest(dispatcher.scheduler) {
        val viewModel = viewModel(RecordingSearchRepository())
        advanceUntilIdle()

        viewModel.setQuery("g")
        advanceUntilIdle()
        assertEquals(SearchMode.TEXT, viewModel.state.value.mode)

        viewModel.resetFilter()
        advanceUntilIdle()
        assertEquals(SearchMode.FILENAME, viewModel.state.value.mode)
        assertTrue(viewModel.state.value.commandArmed)
    }

    @Test
    fun activeCommandGlyphFollowsTheSelectedCommand() = runTest(dispatcher.scheduler) {
        val viewModel = viewModel(RecordingSearchRepository())
        advanceUntilIdle()
        assertEquals('f', viewModel.state.value.activeCommandGlyph)

        viewModel.setQuery("g")
        advanceUntilIdle()
        assertEquals('g', viewModel.state.value.activeCommandGlyph)

        viewModel.resetFilter()
        advanceUntilIdle()
        viewModel.setQuery("m")
        advanceUntilIdle()
        assertEquals('m', viewModel.state.value.activeCommandGlyph)

        viewModel.resetFilter()
        advanceUntilIdle()
        viewModel.setQuery("n")
        advanceUntilIdle()
        assertEquals('n', viewModel.state.value.activeCommandGlyph)
    }

    @Test
    fun clearingTheQueryRearmsTheCommandsAndDropsPrefixMode() = runTest(dispatcher.scheduler) {
        val viewModel = viewModel(RecordingSearchRepository())
        advanceUntilIdle()

        viewModel.setQuery("m")
        advanceUntilIdle()
        assertTrue(viewModel.state.value.prefixOnly)

        viewModel.setQuery("")
        advanceUntilIdle()
        assertTrue(viewModel.state.value.commandArmed)
        assertFalse(viewModel.state.value.prefixOnly)
    }

    private fun viewModel(search: RecordingSearchRepository) = SearchViewModel(
        searchRepository = search,
        recentFilesRepository = NoopRecentFilesRepository(),
        settingsRepository = FakeSettingsRepository(),
    )
}

private class RecordingSearchRepository : SearchRepository {
    var lastQuery: String? = null
    var lastMode: SearchMode? = null
    var lastPrefixOnly: Boolean? = null

    override suspend fun search(
        query: String,
        mode: SearchMode,
        caseSensitive: Boolean,
        limit: Int,
        prefixOnly: Boolean,
    ): SearchOutcome {
        lastQuery = query
        lastMode = mode
        lastPrefixOnly = prefixOnly
        return SearchOutcome(query = query, mode = mode)
    }

    override suspend fun browseFiles(query: String?): List<FileSummary> = emptyList()

    override suspend fun summary(): IndexSummary = IndexSummary(0, null, 0)

    override suspend fun fileIdForPath(relativePath: String): Long? = null
}

private class NoopRecentFilesRepository : RecentFilesRepository {
    private val files = MutableStateFlow<List<RecentFile>>(emptyList())
    private val searches = MutableStateFlow<List<RecentSearch>>(emptyList())
    override val recentFiles: Flow<List<RecentFile>> = files
    override val recentSearches: Flow<List<RecentSearch>> = searches

    override suspend fun recordOpen(file: VaultFile, fileId: Long, line: Int?, cursorOffset: Int) = Unit
    override suspend fun positionFor(fileId: Long): RecentFile? = null
    override suspend fun updatePosition(
        fileId: Long,
        cursorLine: Int,
        cursorOffset: Int,
        scrollOffset: Int,
        matchLine: Int?,
    ) = Unit

    override suspend fun recordSearch(query: String, mode: SearchMode) = Unit
    override suspend fun clearHistory() = Unit
    override suspend fun removeStale() = Unit
    override fun setLimit(limit: Int) = Unit
}

private class FakeSettingsRepository : SettingsRepository {
    private val state = MutableStateFlow(AppSettings(lastSearchMode = SearchMode.FILENAME))
    override val settings: Flow<AppSettings> = state

    override suspend fun setVault(uri: String, displayName: String) = Unit
    override suspend fun clearVault() = Unit
    override suspend fun setLecture(uri: String, displayName: String) = Unit
    override suspend fun clearLecture() = Unit
    override suspend fun setTheme(mode: dev.idefix.app.domain.model.ThemeMode) = Unit
    override suspend fun setIncludeHidden(enabled: Boolean) = Unit
    override suspend fun setCaseSensitive(enabled: Boolean) = Unit
    override suspend fun setDynamicColor(enabled: Boolean) = Unit
    override suspend fun setLastSearchMode(mode: SearchMode) {
        state.update { it.copy(lastSearchMode = mode) }
    }

    override suspend fun setRecentLimit(limit: Int) = Unit
}
