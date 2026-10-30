package dev.idefix.app.ui.editor

import dev.idefix.app.domain.model.DocumentMode
import dev.idefix.app.domain.model.FileSummary
import dev.idefix.app.domain.model.IndexResult
import dev.idefix.app.domain.model.IndexState
import dev.idefix.app.domain.model.IndexSummary
import dev.idefix.app.domain.model.RecentFile
import dev.idefix.app.domain.model.RecentSearch
import dev.idefix.app.domain.model.ScanProgress
import dev.idefix.app.domain.model.ScanResult
import dev.idefix.app.domain.model.SearchMode
import dev.idefix.app.domain.model.SearchOutcome
import dev.idefix.app.domain.model.VaultFile
import dev.idefix.app.domain.model.VaultInfo
import dev.idefix.app.domain.repository.IndexRepository
import dev.idefix.app.domain.repository.RecentFilesRepository
import dev.idefix.app.domain.repository.SearchRepository
import dev.idefix.app.domain.repository.VaultRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DocumentViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val file = VaultFile(
        documentId = "doc-1",
        relativePath = "Notes/Test.md",
        displayName = "Test.md",
        uri = "content://test/1",
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun openingASearchResultAtALineStillStartsInPreviewMode() = runTest(dispatcher.scheduler) {
        val viewModel = DocumentViewModel(
            vaultRepository = FakeVaultRepository(file),
            indexRepository = FakeIndexRepository(),
            recentFilesRepository = FakeRecentFilesRepository(),
            searchRepository = FakeSearchRepository(),
        )

        viewModel.open(
            OpenDocumentRequest(
                fileId = 1,
                uri = file.uri,
                relativePath = file.relativePath,
                filename = file.displayName,
                line = 3,
            ),
        )
        advanceUntilIdle()

        assertEquals(DocumentMode.PREVIEW, viewModel.state.value.mode)
    }
}

private class FakeVaultRepository(private val file: VaultFile) : VaultRepository {
    override val vaultInfo: StateFlow<VaultInfo?> = MutableStateFlow(null)

    override suspend fun restoreVault(uri: String, displayName: String?): VaultInfo? = null
    override suspend fun adoptVault(uri: String, displayName: String?): VaultInfo = error("Not used")
    override suspend fun clearVault() = Unit
    override suspend fun validateAccess(): Boolean = true
    override suspend fun scanMarkdownFiles(
        includeHidden: Boolean,
        onProgress: (ScanProgress) -> Unit,
    ): ScanResult = ScanResult(emptyList())

    override suspend fun readText(file: VaultFile): String = "line 1\nline 2\nline 3\n"
    override suspend fun writeText(file: VaultFile, content: String) = Unit
    override suspend fun createMarkdownFile(
        parentRelativePath: String?,
        displayName: String,
        content: String,
    ): VaultFile = error("Not used")

    override suspend fun renameFile(file: VaultFile, newDisplayName: String): VaultFile =
        error("Not used")

    override suspend fun deleteFile(file: VaultFile) = Unit
    override suspend fun resolve(relativePath: String): VaultFile? =
        if (relativePath == file.relativePath) file else null

    override suspend fun refresh(file: VaultFile): VaultFile? = file
    override suspend fun exists(file: VaultFile): Boolean = true
}

private class FakeIndexRepository : IndexRepository {
    override val state: StateFlow<IndexState> = MutableStateFlow(IndexState.Idle)

    override suspend fun rebuild(includeHidden: Boolean): IndexResult =
        IndexResult(0, 0, emptyList())

    override suspend fun reconcile(includeHidden: Boolean): IndexResult =
        IndexResult(0, 0, emptyList())

    override suspend fun reindexFile(file: VaultFile) = Unit
    override suspend fun removeFile(fileId: Long) = Unit
    override fun cancel() = Unit
    override suspend fun summary(): IndexSummary = IndexSummary(0, null, 0)
    override suspend fun databaseFileCount(): Int = 0
    override suspend fun clearIndex() = Unit
}

private class FakeRecentFilesRepository : RecentFilesRepository {
    private val files = MutableStateFlow<List<RecentFile>>(emptyList())
    private val searches = MutableStateFlow<List<RecentSearch>>(emptyList())
    override val recentFiles: Flow<List<RecentFile>> = files
    override val recentSearches: Flow<List<RecentSearch>> = searches

    override suspend fun recordOpen(
        file: VaultFile,
        fileId: Long,
        line: Int?,
        cursorOffset: Int,
    ) = Unit

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

private class FakeSearchRepository : SearchRepository {
    override suspend fun search(
        query: String,
        mode: SearchMode,
        caseSensitive: Boolean,
        limit: Int,
        prefixOnly: Boolean,
    ): SearchOutcome = SearchOutcome(query, mode)

    override suspend fun browseFiles(query: String?): List<FileSummary> = emptyList()
    override suspend fun summary(): IndexSummary = IndexSummary(0, null, 0)
    override suspend fun fileIdForPath(relativePath: String): Long? = 1
}
