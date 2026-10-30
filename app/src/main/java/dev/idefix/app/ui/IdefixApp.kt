package dev.idefix.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowDpSize
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffold
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.window.core.layout.WindowSizeClass
import dev.idefix.app.AppContainer
import dev.idefix.app.domain.model.IndexState
import dev.idefix.app.domain.model.RecentFile
import dev.idefix.app.domain.model.SearchResult
import dev.idefix.app.domain.model.TaskItem
import dev.idefix.app.domain.model.VaultFile
import dev.idefix.app.navigation.AppSection
import dev.idefix.app.ui.components.EmptyState
import dev.idefix.app.ui.editor.DocumentScreen
import dev.idefix.app.ui.editor.DocumentViewModel
import dev.idefix.app.ui.editor.OpenDocumentRequest
import dev.idefix.app.ui.recents.RecentScreen
import dev.idefix.app.ui.recents.RecentViewModel
import dev.idefix.app.ui.search.SearchScreen
import dev.idefix.app.ui.search.SearchViewModel
import dev.idefix.app.ui.settings.SettingsScreen
import dev.idefix.app.ui.tasks.TasksScreen
import dev.idefix.app.ui.tasks.TasksViewModel
import dev.idefix.app.ui.welcome.WelcomeScreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun IdefixApp(
    appState: AppUiState,
    appViewModel: AppViewModel,
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val rootFocus = remember { FocusRequester() }

    val searchViewModel: SearchViewModel = viewModel(
        factory = idefixViewModelFactory {
            SearchViewModel(
                searchRepository = container.searchRepository,
                recentFilesRepository = container.recentFilesRepository,
                settingsRepository = container.settings,
            )
        },
    )
    val documentViewModel: DocumentViewModel = viewModel(
        factory = idefixViewModelFactory {
            DocumentViewModel(
                vaultRepository = container.vaultRepository,
                indexRepository = container.indexRepository,
                recentFilesRepository = container.recentFilesRepository,
                searchRepository = container.searchRepository,
            )
        },
    )
    val tasksViewModel: TasksViewModel = viewModel(
        factory = idefixViewModelFactory {
            TasksViewModel(container.taskRepository, container.searchRepository)
        },
    )
    val recentViewModel: RecentViewModel = viewModel(
        factory = idefixViewModelFactory {
            RecentViewModel(container.recentFilesRepository)
        },
    )

    val searchState by searchViewModel.state.collectAsStateWithLifecycle()
    val documentState by documentViewModel.state.collectAsStateWithLifecycle()
    val tasksState by tasksViewModel.state.collectAsStateWithLifecycle()
    val recentFiles by recentViewModel.recentFiles.collectAsStateWithLifecycle()
    val recentMessages by recentViewModel.state.collectAsStateWithLifecycle()

    var section by rememberSaveable { mutableStateOf(AppSection.SEARCH) }
    var showFileBrowser by rememberSaveable { mutableStateOf(false) }
    var showCreateNote by rememberSaveable { mutableStateOf(false) }
    var searchFocusToken by rememberSaveable { mutableIntStateOf(0) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var discardDialogOpen by remember { mutableStateOf(false) }

    val vaultPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            appViewModel.onVaultSelected(uri.toString())
        } else {
            appViewModel.vaultSelectionCancelled()
        }
    }

    val adaptiveInfo = currentWindowAdaptiveInfo()
    val windowWidthDp = currentWindowDpSize().width.value
    val twoPane = AdaptiveLayoutPolicy.useTwoPane(windowWidthDp)
    val useRail = AdaptiveLayoutPolicy.useNavigationRail(windowWidthDp)
    val directive = remember(adaptiveInfo, twoPane) {
        val base = calculatePaneScaffoldDirective(adaptiveInfo)
        if (twoPane) base else base.copy(maxHorizontalPartitions = 1)
    }
    val navigator = rememberListDetailPaneScaffoldNavigator<Long>(
        scaffoldDirective = directive,
        isDestinationHistoryAware = false,
    )

    val documentVisible = documentState.hasDocument || documentState.isLoading || documentState.error != null

    LaunchedEffect(appState.indexState) {
        if (appState.indexState is IndexState.Ready) {
            searchViewModel.refreshSummary()
            tasksViewModel.refresh()
        }
    }
    LaunchedEffect(section) {
        when (section) {
            AppSection.TASKS -> tasksViewModel.refresh()
            AppSection.SEARCH -> searchViewModel.refreshSummary()
            else -> Unit
        }
    }
    LaunchedEffect(documentState.saveMessage) {
        val message = documentState.saveMessage
        if (message != null && listOf("Saved", "Renamed", "Deleted", "Reloaded").any(message::startsWith)) {
            searchViewModel.refreshResults()
            searchViewModel.refreshSummary()
            tasksViewModel.refresh()
        }
    }
    LaunchedEffect(appState.message) {
        appState.message?.let {
            snackbarHostState.showSnackbar(it)
            appViewModel.consumeMessage()
        }
    }
    LaunchedEffect(recentMessages.message) {
        recentMessages.message?.let {
            snackbarHostState.showSnackbar(it)
            recentViewModel.consumeMessage()
        }
    }
    LaunchedEffect(Unit) {
        rootFocus.requestFocus()
    }

    fun closeDetailPane() {
        scope.launch {
            var guard = 0
            while (navigator.canNavigateBack() && guard++ < 10) {
                navigator.navigateBack()
            }
        }
    }

    fun performOpen(request: OpenDocumentRequest) {
        if (section == AppSection.SETTINGS) section = AppSection.SEARCH
        documentViewModel.open(request)
        scope.launch {
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, request.fileId ?: -1L)
        }
    }

    fun requestOpen(request: OpenDocumentRequest) {
        if (documentState.isDirty) {
            pendingAction = { performOpen(request) }
            discardDialogOpen = true
        } else {
            performOpen(request)
        }
    }

    fun requestCloseDocument() {
        if (documentState.isDirty) {
            pendingAction = {
                documentViewModel.close()
                closeDetailPane()
            }
            discardDialogOpen = true
        } else {
            documentViewModel.close()
            closeDetailPane()
        }
    }

    fun handleInternalLink(raw: String) {
        val target = raw.substringBefore('#')
        if (target.isBlank()) return
        val decoded = android.net.Uri.decode(target)
        val currentPath = documentState.file?.relativePath ?: ""
        val parent = currentPath.substringBeforeLast('/', "")
        val candidates = buildList {
            if (decoded.startsWith("/")) {
                add(decoded.trimStart('/'))
            } else {
                if (parent.isNotBlank()) add("$parent/$decoded")
                add(decoded)
            }
            if (!decoded.endsWith(".md", ignoreCase = true) && !decoded.endsWith(".markdown", ignoreCase = true)) {
                add(if (parent.isNotBlank()) "$parent/$decoded.md" else "$decoded.md")
            }
        }
        scope.launch {
            var file: VaultFile? = null
            for (candidate in candidates.distinct()) {
                file = container.vaultRepository.resolve(candidate)
                if (file != null) break
            }
            val resolved = file
            if (resolved == null) {
                snackbarHostState.showSnackbar("Linked note not found: $raw")
                return@launch
            }
            val fileId = container.searchRepository.fileIdForPath(resolved.relativePath)
            requestOpen(
                OpenDocumentRequest(
                    fileId = fileId,
                    uri = resolved.uri,
                    relativePath = resolved.relativePath,
                    filename = resolved.displayName,
                ),
            )
        }
    }

    fun openSearch() {
        if (!twoPane && documentVisible) requestCloseDocument()
        section = AppSection.SEARCH
        searchFocusToken++
    }

    fun openRecent(file: RecentFile) {
        requestOpen(
            OpenDocumentRequest(
                fileId = file.fileId,
                uri = file.uri,
                relativePath = file.relativePath,
                filename = file.filename,
                line = null,
            ),
        )
    }

    fun openTask(task: TaskItem) {
        requestOpen(
            OpenDocumentRequest(
                fileId = task.fileId,
                uri = task.uri,
                relativePath = task.relativePath,
                filename = task.filename,
                line = task.lineNumber,
            ),
        )
    }

    fun openSearchResult(result: SearchResult) {
        searchViewModel.recordOpened()
        requestOpen(
            OpenDocumentRequest(
                fileId = result.fileId,
                uri = result.uri,
                relativePath = result.relativePath,
                filename = result.filename,
                line = result.line,
            ),
        )
    }

    fun createNote(name: String) {
        showCreateNote = false
        scope.launch {
            try {
                val parent = name.substringBeforeLast('/', "").ifBlank { null }
                val fileName = name.substringAfterLast('/')
                val file = container.vaultRepository.createMarkdownFile(parent, fileName, "")
                container.indexRepository.reindexFile(file)
                val fileId = container.searchRepository.fileIdForPath(file.relativePath)
                performOpen(
                    OpenDocumentRequest(
                        fileId = fileId,
                        uri = file.uri,
                        relativePath = file.relativePath,
                        filename = file.displayName,
                        line = 1,
                    ),
                )
                snackbarHostState.showSnackbar("Created ${file.displayName}")
            } catch (e: Exception) {
                snackbarHostState.showSnackbar(e.message ?: "Could not create the note")
            }
        }
    }

    val rootModifier = modifier
        .fillMaxSize()
        .focusRequester(rootFocus)
        .focusable()
        .onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            if (event.isCtrlPressed) {
                when (event.key) {
                    Key.K -> {
                        openSearch()
                        true
                    }

                    Key.S -> {
                        if (documentState.hasDocument && documentState.isDirty) {
                            documentViewModel.save()
                            true
                        } else {
                            false
                        }
                    }

                    Key.F -> {
                        if (documentState.hasDocument) {
                            documentViewModel.setMode(dev.idefix.app.domain.model.DocumentMode.SOURCE)
                            documentViewModel.showFind()
                            true
                        } else {
                            false
                        }
                    }

                    else -> false
                }
            } else if (event.key == Key.Escape && documentVisible) {
                if (documentState.findVisible) {
                    documentViewModel.hideFind()
                } else {
                    requestCloseDocument()
                }
                true
            } else {
                false
            }
        }

    Box(modifier = rootModifier.background(MaterialTheme.colorScheme.background)) {
        when (appState.phase) {
            StartupPhase.LOADING -> {
                Surface(Modifier.fillMaxSize()) {
                    Box(contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator()
                    }
                }
            }

            StartupPhase.WELCOME -> WelcomeScreen(
                error = appState.startupError,
                versionName = appState.versionName,
                onSelectVault = { vaultPicker.launch(null) },
            )

            StartupPhase.READY -> {
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    bottomBar = {
                        if (!useRail && !documentVisible) {
                            BottomNavigation(section) { selected ->
                                section = selected
                            }
                        }
                    },
                ) { padding ->
                    Row(
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                    ) {
                        if (useRail) {
                            NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                                Spacer(Modifier.size(8.dp))
                                NavigationRailItem(
                                    selected = false,
                                    onClick = { openSearch() },
                                    icon = { Text("I", fontWeight = FontWeight.Bold) },
                                    label = null,
                                )
                                AppSection.entries.forEach { item ->
                                    NavigationRailItem(
                                        selected = section == item,
                                        onClick = {
                                            section = item
                                            if (item == AppSection.SEARCH) searchFocusToken++
                                        },
                                        icon = { Icon(sectionIcon(item), contentDescription = null) },
                                        label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                                    )
                                }
                            }
                        }
                        Box(Modifier.weight(1f).fillMaxSize()) {
                            if (section == AppSection.SETTINGS) {
                                SettingsScreen(
                                    appState = appState,
                                    onSwitchVault = { vaultPicker.launch(null) },
                                    onBrowseFiles = { showFileBrowser = true },
                                    onRebuild = appViewModel::rebuildIndex,
                                    onCancel = appViewModel::cancelIndexing,
                                    onTheme = appViewModel::setTheme,
                                    onIncludeHidden = appViewModel::setIncludeHidden,
                                    onCaseSensitive = appViewModel::setCaseSensitive,
                                    onDynamicColor = appViewModel::setDynamicColor,
                                    onRecentLimit = appViewModel::setRecentLimit,
                                    onMessage = appViewModel::showMessage,
                                )
                            } else {
                                ListDetailPaneScaffold(
                                    directive = navigator.scaffoldDirective,
                                    value = navigator.scaffoldValue,
                                    listPane = {
                                        AnimatedPane(modifier = Modifier.fillMaxSize()) {
                                            when (section) {
                                                AppSection.SEARCH -> SearchScreen(
                                                    state = searchState,
                                                    indexState = appState.indexState,
                                                    vaultName = appState.vault?.displayName ?: "Vault",
                                                    focusRequestToken = searchFocusToken,
                                                    onCreateNote = { showCreateNote = true },
                                                    onCreateNamedNote = ::createNote,
                                                    onOpenFileBrowser = { showFileBrowser = true },
                                                    onSwitchVault = { vaultPicker.launch(null) },
                                                    onOpenResult = ::openSearchResult,
                                                    onOpenRecent = ::openRecent,
                                                    onRebuild = appViewModel::rebuildIndex,
                                                    onCancelIndex = appViewModel::cancelIndexing,
                                                    viewModel = searchViewModel,
                                                )

                                                AppSection.TASKS -> TasksScreen(
                                                    state = tasksState,
                                                    onOpenTask = ::openTask,
                                                    onRefresh = tasksViewModel::refresh,
                                                    viewModel = tasksViewModel,
                                                )

                                                AppSection.RECENT -> RecentScreen(
                                                    files = recentFiles,
                                                    onOpen = ::openRecent,
                                                    onClear = recentViewModel::clearHistory,
                                                )

                                                AppSection.SETTINGS -> Unit
                                            }
                                        }
                                    },
                                    detailPane = {
                                        AnimatedPane(modifier = Modifier.fillMaxSize()) {
                                            if (documentVisible) {
                                                DocumentScreen(
                                                    state = documentState,
                                                    showBack = !twoPane || documentState.error != null,
                                                    onBack = ::requestCloseDocument,
                                                    onOpenInternalLink = ::handleInternalLink,
                                                    onDeleted = {
                                                        documentViewModel.close()
                                                        closeDetailPane()
                                                        searchViewModel.refreshResults()
                                                        searchViewModel.refreshSummary()
                                                        tasksViewModel.refresh()
                                                        appViewModel.showMessage("Note deleted")
                                                    },
                                                    viewModel = documentViewModel,
                                                )
                                            } else {
                                                EmptyState(
                                                    icon = Icons.Outlined.Article,
                                                    title = "No note open",
                                                    message = if (section == AppSection.SEARCH) {
                                                        "Select a search result to read it here. Ctrl+K returns to search."
                                                    } else {
                                                        "Select a note to open it in this pane."
                                                    },
                                                    modifier = Modifier.fillMaxSize(),
                                                )
                                            }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }

        BackHandler(enabled = documentVisible && !discardDialogOpen) {
            if (documentState.findVisible) {
                documentViewModel.hideFind()
            } else {
                requestCloseDocument()
            }
        }
    }

    if (showFileBrowser) {
        FileBrowserDialog(
            searchRepository = container.searchRepository,
            onOpenFile = { file ->
                showFileBrowser = false
                requestOpen(
                    OpenDocumentRequest(
                        fileId = file.fileId,
                        uri = file.uri,
                        relativePath = file.relativePath,
                        filename = file.filename,
                    ),
                )
            },
            onCreateNote = {
                showFileBrowser = false
                showCreateNote = true
            },
            onDismiss = { showFileBrowser = false },
        )
    }

    if (showCreateNote) {
        CreateNoteDialog(
            onDismiss = { showCreateNote = false },
            onCreate = ::createNote,
        )
    }

    if (discardDialogOpen) {
        AlertDialog(
            onDismissRequest = {
                discardDialogOpen = false
                pendingAction = null
            },
            title = { Text("Unsaved changes") },
            text = { Text("Save your changes before leaving this note?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val action = pendingAction
                        discardDialogOpen = false
                        pendingAction = null
                        if (action != null) {
                            documentViewModel.save { action() }
                        } else {
                            documentViewModel.save()
                        }
                    },
                ) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            val action = pendingAction
                            discardDialogOpen = false
                            pendingAction = null
                            documentViewModel.discardChanges()
                            action?.invoke()
                        },
                    ) { Text("Discard") }
                    TextButton(
                        onClick = {
                            discardDialogOpen = false
                            pendingAction = null
                        },
                    ) { Text("Cancel") }
                }
            },
        )
    }

}

@Composable
private fun BottomNavigation(current: AppSection, onSelect: (AppSection) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        AppSection.entries.forEach { section ->
            NavigationBarItem(
                selected = current == section,
                onClick = { onSelect(section) },
                icon = { Icon(sectionIcon(section), contentDescription = null) },
                label = { Text(section.label, style = MaterialTheme.typography.labelSmall) },
            )
        }
    }
}

private fun sectionIcon(section: AppSection) = when (section) {
    AppSection.SEARCH -> Icons.Default.Search
    AppSection.TASKS -> Icons.Default.CheckCircle
    AppSection.RECENT -> Icons.Default.DateRange
    AppSection.SETTINGS -> Icons.Default.Settings
}
