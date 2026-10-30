package dev.idefix.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.idefix.app.domain.model.AppSettings
import dev.idefix.app.domain.model.IndexState
import dev.idefix.app.domain.model.SearchMode
import dev.idefix.app.domain.model.SearchResult
import dev.idefix.app.domain.model.ThemeMode
import dev.idefix.app.domain.model.TodoItem
import dev.idefix.app.ui.components.EmptyState
import dev.idefix.app.ui.components.SyncAction
import dev.idefix.app.ui.components.TodoRow
import dev.idefix.app.ui.search.SearchScreen
import dev.idefix.app.ui.search.SearchViewModel
import dev.idefix.app.ui.theme.IdefixTheme
import dev.idefix.app.ui.welcome.WelcomeScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class UiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun firstRunShowsWelcomeAndVaultAction() {
        var clicked = false
        composeRule.setContent {
            IdefixTheme(ThemeMode.DARK, dynamicColor = false) {
                WelcomeScreen(
                    error = "Permission revoked",
                    versionName = "1.0.0",
                    onSelectVault = { clicked = true },
                )
            }
        }
        composeRule.onNodeWithText("Idefix").assertIsDisplayed()
        composeRule.onNodeWithText("Select vault folder").assertIsDisplayed()
        composeRule.onNodeWithText("Permission revoked").assertIsDisplayed()
        composeRule.onNodeWithText("Select vault folder").performClick()
        assertTrue(clicked)
    }

    @Test
    fun welcomeSupportsLightAndDarkThemes() {
        var theme by mutableStateOf(ThemeMode.LIGHT)
        composeRule.setContent {
            IdefixTheme(theme, dynamicColor = false) {
                WelcomeScreen(error = null, versionName = "1.0.0", onSelectVault = {})
            }
        }
        composeRule.onNodeWithText("Select vault folder").assertIsDisplayed()
        composeRule.runOnIdle { theme = ThemeMode.DARK }
        composeRule.onNodeWithText("Select vault folder").assertIsDisplayed()
    }

    @Test
    fun todoCheckboxTogglesAndRowOpensEditor() {
        var completedValue: Boolean? = null
        var opened = false
        val todo = TodoItem(
            fileId = 1,
            uri = "content://test/1",
            relativePath = "TODO.md",
            filename = "TODO.md",
            startLine = 3,
            endLine = 4,
            title = "Buy milk",
            body = "Oat milk, not soy.",
            completed = false,
            due = LocalDate.now().plusDays(1).atTime(18, 0),
        )
        composeRule.setContent {
            IdefixTheme(ThemeMode.DARK, dynamicColor = false) {
                TodoRow(
                    todo = todo,
                    onToggle = { completedValue = it },
                    onClick = { opened = true },
                )
            }
        }
        composeRule.onNodeWithContentDescription("Todo: Buy milk").assertIsOff().performClick()
        assertEquals(true, completedValue)
        composeRule.onNodeWithText("Buy milk").performClick()
        assertTrue(opened)
    }

    @Test
    fun completedTodoIsRenderedChecked() {
        val todo = TodoItem(
            fileId = 1,
            uri = "content://test/1",
            relativePath = "TODO.md",
            filename = "TODO.md",
            startLine = 1,
            endLine = 1,
            title = "Finished",
            body = "",
            completed = true,
            due = null,
        )
        composeRule.setContent {
            IdefixTheme { TodoRow(todo = todo, onToggle = {}, onClick = {}) }
        }
        composeRule.onNodeWithContentDescription("Todo: Finished").assertIsOn()
    }

    @Test
    fun syncActionDistinguishesActiveSyncAndFailure() {
        var syncing by mutableStateOf(true)
        var cancellable by mutableStateOf(false)
        var failed by mutableStateOf(false)
        composeRule.setContent {
            IdefixTheme {
                SyncAction(
                    isSyncing = syncing,
                    onSync = {},
                    failed = failed,
                    onCancel = if (cancellable) ({}) else null,
                )
            }
        }
        composeRule.onNodeWithContentDescription("Syncing").assertIsDisplayed()

        composeRule.runOnIdle { cancellable = true }
        composeRule.onNodeWithContentDescription("Cancel sync").assertIsDisplayed()

        composeRule.runOnIdle {
            cancellable = false
            syncing = false
            failed = true
        }
        composeRule.onNodeWithContentDescription("Retry sync").assertIsDisplayed()
    }

    @Test
    fun emptyStateIsAccessible() {
        composeRule.setContent {
            IdefixTheme {
                EmptyState(
                    icon = Icons.Outlined.History,
                    title = "No matches",
                    message = "Try another query.",
                    actionLabel = "Reset",
                    onAction = {},
                )
            }
        }
        composeRule.onNodeWithText("No matches").assertIsDisplayed()
        composeRule.onNodeWithText("Reset").assertIsDisplayed()
    }

    @Test
    fun firstKeyGSelectsGrepAndIsNotPartOfTheQuery() {
        val searchRepo = FakeSearchRepository(fileCount = 1)
        val viewModel = SearchViewModel(
            searchRepository = searchRepo,
            recentFilesRepository = FakeRecentFilesRepository(),
            settingsRepository = FakeSettingsRepository(AppSettings()),
        )
        composeRule.setContent {
            val state by viewModel.state.collectAsState()
            IdefixTheme(ThemeMode.DARK, dynamicColor = false) {
                SearchScreen(
                    state = state,
                    indexState = IndexState.Ready(1, System.currentTimeMillis()),
                    isSyncing = false,
                    vaultName = "Test vault",
                    focusRequestToken = 0,
                    onCreateNamedNote = {},
                    onSwitchVault = {},
                    onOpenResult = {},
                    onOpenRecent = {},
                    onRebuild = {},
                    onCancelIndex = {},
                    viewModel = viewModel,
                )
            }
        }
        composeRule.onNode(hasSetTextAction()).performTextInput("g")
        composeRule.onNode(hasSetTextAction()).performTextInput("walrus")
        composeRule.waitUntil(timeoutMillis = 5_000) { searchRepo.lastQuery == "walrus" }
        assertEquals(SearchMode.TEXT, searchRepo.lastMode)
        assertEquals("walrus", searchRepo.lastQuery)
    }

    @Test
    fun firstKeyMSearchesTheModulPrefix() {
        val searchRepo = FakeSearchRepository(fileCount = 1)
        val viewModel = SearchViewModel(
            searchRepository = searchRepo,
            recentFilesRepository = FakeRecentFilesRepository(),
            settingsRepository = FakeSettingsRepository(AppSettings()),
        )
        composeRule.setContent {
            val state by viewModel.state.collectAsState()
            IdefixTheme(ThemeMode.DARK, dynamicColor = false) {
                SearchScreen(
                    state = state,
                    indexState = IndexState.Ready(1, System.currentTimeMillis()),
                    isSyncing = false,
                    vaultName = "Test vault",
                    focusRequestToken = 0,
                    onCreateNamedNote = {},
                    onSwitchVault = {},
                    onOpenResult = {},
                    onOpenRecent = {},
                    onRebuild = {},
                    onCancelIndex = {},
                    viewModel = viewModel,
                )
            }
        }
        composeRule.onNode(hasSetTextAction()).performTextInput("m")
        composeRule.waitUntil(timeoutMillis = 5_000) { searchRepo.lastQuery == "Modul_" }
        assertEquals(SearchMode.FILENAME, searchRepo.lastMode)
        assertEquals(true, searchRepo.lastPrefixOnly)
    }

    @Test
    fun firstKeyNCreatesASlugifiedNote() {
        val searchRepo = FakeSearchRepository(fileCount = 1)
        val viewModel = SearchViewModel(
            searchRepository = searchRepo,
            recentFilesRepository = FakeRecentFilesRepository(),
            settingsRepository = FakeSettingsRepository(AppSettings()),
        )
        var created: String? = null
        composeRule.setContent {
            val state by viewModel.state.collectAsState()
            IdefixTheme(ThemeMode.DARK, dynamicColor = false) {
                SearchScreen(
                    state = state,
                    indexState = IndexState.Ready(1, System.currentTimeMillis()),
                    isSyncing = false,
                    vaultName = "Test vault",
                    focusRequestToken = 0,
                    onCreateNamedNote = { created = it },
                    onSwitchVault = {},
                    onOpenResult = {},
                    onOpenRecent = {},
                    onRebuild = {},
                    onCancelIndex = {},
                    viewModel = viewModel,
                )
            }
        }
        composeRule.onNode(hasSetTextAction()).performTextInput("n")
        composeRule.onNode(hasSetTextAction()).performTextInput("Über Größe")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Ueber_Groesse.md").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Ueber_Groesse.md").performClick()
        assertEquals("Ueber_Groesse", created)
    }

    @Test
    fun searchInteractionShowsResultsAndOpensThem() {
        val result = SearchResult(
            fileId = 9,
            uri = "content://test/9",
            relativePath = "Notes/Walrus.md",
            filename = "Walrus.md",
            line = 3,
            snippet = "The \u0001walrus\u0002 dances at dawn.",
            mode = SearchMode.TEXT,
        )
        val searchRepo = FakeSearchRepository(results = listOf(result), fileCount = 1)
        val viewModel = SearchViewModel(
            searchRepository = searchRepo,
            recentFilesRepository = FakeRecentFilesRepository(),
            settingsRepository = FakeSettingsRepository(AppSettings()),
        )
        var opened: SearchResult? = null
        composeRule.setContent {
            val state by viewModel.state.collectAsState()
            IdefixTheme(ThemeMode.DARK, dynamicColor = false) {
                SearchScreen(
                    state = state,
                    indexState = IndexState.Ready(1, System.currentTimeMillis()),
                    isSyncing = false,
                    vaultName = "Test vault",
                    focusRequestToken = 0,
                    onCreateNamedNote = {},
                    onSwitchVault = {},
                    onOpenResult = { opened = it },
                    onOpenRecent = {},
                    onRebuild = {},
                    onCancelIndex = {},
                    viewModel = viewModel,
                )
            }
        }
        composeRule.onNode(hasSetTextAction()).performTextInput("walrus")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("Walrus.md").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Walrus.md").assertIsDisplayed()
        composeRule.onNodeWithText("Walrus.md").performClick()
        assertEquals("Walrus.md", opened?.filename)
        assertEquals("walrus", searchRepo.lastQuery)
    }
}
