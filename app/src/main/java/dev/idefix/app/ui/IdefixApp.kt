package dev.idefix.app.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
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
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.School
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.idefix.app.AppContainer
import dev.idefix.app.domain.model.DocumentMode
import dev.idefix.app.domain.model.IndexState
import dev.idefix.app.domain.model.LectureEntry
import dev.idefix.app.domain.model.LectureEntryKind
import dev.idefix.app.domain.model.RecentFile
import dev.idefix.app.domain.model.SearchResult
import dev.idefix.app.domain.model.VaultFile
import dev.idefix.app.navigation.AppSection
import dev.idefix.app.ui.components.EmptyState
import dev.idefix.app.ui.editor.DocumentScreen
import dev.idefix.app.ui.editor.DocumentViewModel
import dev.idefix.app.ui.editor.OpenDocumentRequest
import dev.idefix.app.ui.lectures.LecturesScreen
import dev.idefix.app.ui.lectures.LecturesViewModel
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
    val context = LocalContext.current

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
            TasksViewModel(container.todoRepository, container.syncCoordinator)
        },
    )
    val lecturesViewModel: LecturesViewModel = viewModel(
        factory = idefixViewModelFactory {
            LecturesViewModel(container.lectureRepository)
        },
    )
    val searchState by searchViewModel.state.collectAsStateWithLifecycle()
    val documentState by documentViewModel.state.collectAsStateWithLifecycle()
    val tasksState by tasksViewModel.state.collectAsStateWithLifecycle()
    val lecturesState by lecturesViewModel.state.collectAsStateWithLifecycle()

    var section by rememberSaveable { mutableStateOf(AppSection.TASKS) }
    var showFileBrowser by rememberSaveable { mutableStateOf(false) }
    var showCreateNote by rememberSaveable { mutableStateOf(false) }
    var focusRequest by rememberSaveable { mutableStateOf(false) }
    val saveableStateHolder = rememberSaveableStateHolder()
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var discardDialogOpen by remember { mutableStateOf(false) }

    val vaultPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            appViewModel.onVaultSelected(uri.toString())
        } else {
            appViewModel.vaultSelectionCancelled()
        }
    }

    val lecturePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            appViewModel.onLectureSelected(uri.toString())
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
    // The task and lectures tabs own the whole window, so an open note must not
    // hide their navigation or intercept the system back gesture there.
    val documentPaneActive = documentVisible && section != AppSection.TASKS && section != AppSection.LECTURES

    LaunchedEffect(appState.indexState) {
        if (appState.indexState is IndexState.Ready) {
            searchViewModel.refreshSummary()
            tasksViewModel.refresh()
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
    LaunchedEffect(tasksState.message) {
        tasksState.message?.let {
            snackbarHostState.showSnackbar(it)
            tasksViewModel.consumeMessage()
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
        if (section == AppSection.SETTINGS) section = AppSection.NOTES
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
        section = AppSection.NOTES
        focusRequest = true
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

    fun openSearchResult(result: SearchResult) {
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

    fun openLectureExternally(entry: LectureEntry) {
        val mime = if (entry.kind != LectureEntryKind.OTHER) {
            entry.kind.defaultMimeType
        } else {
            entry.mimeType?.takeIf { it.isNotBlank() } ?: "*/*"
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(entry.uriObject, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri(entry.name, entry.uriObject)
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            appViewModel.showMessage("No app can open ${entry.name}")
        } catch (e: Exception) {
            appViewModel.showMessage(e.message ?: "Could not open ${entry.name}")
        }
    }

    fun createNote(name: String) {
        showCreateNote = false
        scope.launch {
            try {
                val parent = name.substringBeforeLast('/', "").ifBlank { null }
                val fileName = name.substringAfterLast('/')
                val file = container.vaultRepository.createMarkdownFile(parent, fileName, "")
                container.indexRepository.reindexFile(file)
                searchViewModel.refreshSummary()
                val fileId = container.searchRepository.fileIdForPath(file.relativePath)
                performOpen(
                    OpenDocumentRequest(
                        fileId = fileId,
                        uri = file.uri,
                        relativePath = file.relativePath,
                        filename = file.displayName,
                        line = 1,
                        mode = DocumentMode.SOURCE,
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
                            documentViewModel.setMode(DocumentMode.SOURCE)
                            documentViewModel.showFind()
                            true
                        } else {
                            false
                        }
                    }

                    else -> false
                }
            } else if (event.key == Key.Escape && documentPaneActive) {
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
                        if (!useRail && !documentPaneActive) {
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
                                        onClick = { section = item },
                                        icon = { Icon(sectionIcon(item), contentDescription = null) },
                                        label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                                    )
                                }
                            }
                        }
                        Box(Modifier.weight(1f).fillMaxSize()) {
                            saveableStateHolder.SaveableStateProvider(section.name) {
                                if (section == AppSection.SETTINGS) {
                                    SettingsScreen(
                                        appState = appState,
                                        onSwitchVault = { vaultPicker.launch(null) },
                                        onBrowseFiles = { showFileBrowser = true },
                                        onChooseLecturesFolder = { lecturePicker.launch(null) },
                                        onClearLecturesFolder = appViewModel::clearLecture,
                                        onRebuild = appViewModel::rebuildIndex,
                                        onCancel = appViewModel::cancelIndexing,
                                        onTheme = appViewModel::setTheme,
                                        onIncludeHidden = appViewModel::setIncludeHidden,
                                        onCaseSensitive = appViewModel::setCaseSensitive,
                                        onDynamicColor = appViewModel::setDynamicColor,
                                        onRecentLimit = appViewModel::setRecentLimit,
                                        onClearHistory = appViewModel::clearRecentHistory,
                                        onMessage = appViewModel::showMessage,
                                    )
                                } else if (section == AppSection.TASKS) {
                                    TasksScreen(
                                        state = tasksState,
                                        indexState = appState.indexState,
                                        isSyncing = appState.isSyncing,
                                        twoPane = twoPane,
                                        onSync = {
                                            appViewModel.reconcileIndex()
                                            tasksViewModel.refresh()
                                        },
                                        onCancelSync = appViewModel::cancelIndexing,
                                        viewModel = tasksViewModel,
                                    )
                                } else if (section == AppSection.LECTURES) {
                                    LecturesScreen(
                                        state = lecturesState,
                                        configured = appState.settings.hasLecture,
                                        twoPane = twoPane,
                                        onChooseFolder = { lecturePicker.launch(null) },
                                        onOpenExternal = ::openLectureExternally,
                                        viewModel = lecturesViewModel,
                                    )
                                } else {
                                    ListDetailPaneScaffold(
                                        directive = navigator.scaffoldDirective,
                                        value = navigator.scaffoldValue,
                                        listPane = {
                                            AnimatedPane(modifier = Modifier.fillMaxSize()) {
                                                SearchScreen(
                                                    state = searchState,
                                                    indexState = appState.indexState,
                                                    isSyncing = appState.isSyncing,
                                                    vaultName = appState.vault?.displayName ?: "Vault",
                                                    focusRequest = focusRequest,
                                                    onFocusRequestHandled = { focusRequest = false },
                                                    onCreateNamedNote = ::createNote,
                                                    onSwitchVault = { vaultPicker.launch(null) },
                                                    onOpenResult = ::openSearchResult,
                                                    onOpenRecent = ::openRecent,
                                                    onRebuild = appViewModel::rebuildIndex,
                                                    onCancelIndex = appViewModel::cancelIndexing,
                                                    viewModel = searchViewModel,
                                                )
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
                                                        icon = Icons.AutoMirrored.Outlined.Article,
                                                        title = "No note open",
                                                        message = "Select a search result to read it here. Ctrl+K returns to search.",
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
        }

        BackHandler(enabled = documentPaneActive && !discardDialogOpen) {
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
    AppSection.TASKS -> Icons.Default.CheckCircle
    AppSection.NOTES -> Icons.AutoMirrored.Outlined.Article
    AppSection.LECTURES -> Icons.Outlined.School
    AppSection.SETTINGS -> Icons.Default.Settings
}
