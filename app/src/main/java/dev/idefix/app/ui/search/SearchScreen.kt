package dev.idefix.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.idefix.app.domain.model.IndexState
import dev.idefix.app.domain.model.RecentFile
import dev.idefix.app.domain.model.SearchMode
import dev.idefix.app.domain.model.SearchResult
import dev.idefix.app.ui.components.EmptyState
import dev.idefix.app.ui.components.ErrorState
import dev.idefix.app.ui.components.FilePathLabel
import dev.idefix.app.ui.components.LoadingState
import dev.idefix.app.ui.components.SearchField
import dev.idefix.app.ui.components.SearchResultDivider
import dev.idefix.app.ui.components.SearchResultRow
import dev.idefix.app.ui.components.SectionLabel
import dev.idefix.app.ui.components.SyncAction
import dev.idefix.app.ui.components.relativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: SearchUiState,
    indexState: IndexState,
    isSyncing: Boolean,
    vaultName: String,
    focusRequest: Boolean,
    onFocusRequestHandled: () -> Unit,
    onCreateNamedNote: (String) -> Unit,
    onSwitchVault: () -> Unit,
    onOpenResult: (SearchResult) -> Unit,
    onOpenRecent: (RecentFile) -> Unit,
    onRebuild: () -> Unit,
    onCancelIndex: () -> Unit,
    viewModel: SearchViewModel,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    val resultsFocusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    var fieldFocused by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(focusRequest) {
        if (focusRequest) {
            viewModel.rearmCommand()
            focusRequester.requestFocus()
            keyboard?.show()
            onFocusRequestHandled()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        text = "Idefix",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = vaultName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            actions = {
                SyncAction(
                    isSyncing = isSyncing || indexState.isRunning,
                    onSync = onRebuild,
                    failed = indexState is IndexState.Failed,
                    onCancel = if (indexState.isRunning) onCancelIndex else null,
                )
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More actions")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Switch vault…") },
                            onClick = {
                                menuOpen = false
                                onSwitchVault()
                            },
                        )
                    }
                }
            },
            windowInsets = WindowInsets(0),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || !fieldFocused) return@onPreviewKeyEvent false
                    when {
                        state.query.isEmpty() &&
                            (event.key == Key.Backspace || event.key == Key.Delete) -> {
                            viewModel.resetFilter()
                            true
                        }

                        event.key == Key.Escape -> {
                            focusManager.clearFocus()
                            resultsFocusRequester.requestFocus()
                            true
                        }

                        else -> false
                    }
                },
        ) {
            SearchField(
                value = state.query,
                onValueChange = { viewModel.setQuery(it) },
                onSubmit = {
                    if (state.creatingNote) {
                        val name = state.newNoteName
                        if (name.isNotBlank()) {
                            viewModel.clearQuery()
                            onCreateNamedNote(name)
                        }
                    } else {
                        viewModel.submit()
                        if (!state.isSearching && state.searchedQuery == state.query.trim()) {
                            state.selectedResult?.let(onOpenResult)
                        }
                    }
                },
                onClear = {
                    viewModel.clearQuery()
                    focusRequester.requestFocus()
                },
                focusRequester = focusRequester,
                modifier = Modifier.onFocusChanged { fieldFocused = it.isFocused },
                placeholder = state.placeholder,
                resetToken = state.commandRevision,
                commandGlyph = state.activeCommandGlyph,
            )
            Spacer(Modifier.height(6.dp))
        }

        if (state.creatingNote) {
            CreateNotePanel(
                slug = state.newNoteName,
                onCreate = {
                    if (state.newNoteName.isNotBlank()) {
                        viewModel.clearQuery()
                        onCreateNamedNote(state.newNoteName)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        } else if (state.query.isBlank()) {
            SearchLanding(
                state = state,
                onOpenRecent = onOpenRecent,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            SearchResults(
                state = state,
                fieldFocused = fieldFocused,
                resultsFocusRequester = resultsFocusRequester,
                onOpenResult = onOpenResult,
                onClearFocus = { focusManager.clearFocus() },
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun CreateNotePanel(
    slug: String,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 12.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(14.dp),
            onClick = onCreate,
            enabled = slug.isNotBlank(),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.NoteAdd,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (slug.isBlank()) "Type a title" else "$slug.md",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Enter to create",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchLanding(
    state: SearchUiState,
    onOpenRecent: (RecentFile) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Format timestamps once per history update instead of during every row
    // composition while the list is scrolling.
    val timeLabels = remember(state.recentFiles) {
        state.recentFiles.associate { it.fileId to relativeTime(it.openedAt) }
    }
    LazyColumn(modifier = modifier.padding(horizontal = 12.dp)) {
        item {
            SectionLabel("Recent notes", Modifier.padding(top = 8.dp, bottom = 6.dp))
        }
        if (state.recentFiles.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.Description,
                    title = "No recent notes yet",
                    message = if (state.summary.fileCount == 0) {
                        "Index a vault to search filenames or full text. Use the sync button in the top bar, or browse files from Settings."
                    } else {
                        "Open a note from search, the file browser, or tasks. It will appear here."
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            items(
                state.recentFiles,
                key = { it.fileId },
                contentType = { "recent-file" },
            ) { file ->
                RecentFileRow(
                    file = file,
                    timeLabel = timeLabels[file.fileId] ?: "",
                    onClick = { onOpenRecent(file) },
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun RecentFileRow(file: RecentFile, timeLabel: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.filename,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                FilePathLabel(
                    path = file.relativePath.substringBeforeLast('/', "/"),
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                Text(
                    text = "line ${file.cursorLine}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SearchResults(
    state: SearchUiState,
    fieldFocused: Boolean,
    resultsFocusRequester: FocusRequester,
    onOpenResult: (SearchResult) -> Unit,
    onClearFocus: () -> Unit,
    viewModel: SearchViewModel,
    modifier: Modifier = Modifier,
) {
    val savedScroll = remember(viewModel) { viewModel.scrollPosition(state.query, state.mode) }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = savedScroll.first,
        initialFirstVisibleItemScrollOffset = savedScroll.second,
    )
    // The query/mode the list was last composed with, so disposing it saves the
    // offset under the search that actually produced these rows.
    val currentQuery by rememberUpdatedState(state.query)
    val currentMode by rememberUpdatedState(state.mode)
    // Save the scroll position when the list leaves composition instead of
    // observing the scroll state on every frame.
    DisposableEffect(listState) {
        onDispose {
            viewModel.saveScroll(
                currentQuery,
                currentMode,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
            )
        }
    }
    // Only react to real changes: on re-entry after a tab switch the restored
    // scroll position must survive, so first composition must not scroll.
    var lastQuery by remember { mutableStateOf(state.query) }
    var lastMode by remember { mutableStateOf(state.mode) }
    LaunchedEffect(state.query, state.mode) {
        if (state.query != lastQuery || state.mode != lastMode) {
            lastQuery = state.query
            lastMode = state.mode
            if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
                listState.scrollToItem(0)
            }
        }
    }
    var lastSelectedIndex by remember { mutableStateOf(state.selectedIndex) }
    LaunchedEffect(state.selectedIndex) {
        if (state.selectedIndex != lastSelectedIndex) {
            lastSelectedIndex = state.selectedIndex
            if (state.results.isNotEmpty()) {
                listState.animateScrollToItem(state.selectedIndex.coerceIn(0, state.results.lastIndex))
            }
        }
    }
    val timeLabels = remember(state.results) {
        state.results.mapNotNull { it.lastModified }
            .distinct()
            .associateWith(::relativeTime)
    }

    Box(
        modifier = modifier
            .focusRequester(resultsFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || fieldFocused) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown -> {
                        viewModel.moveSelection(1)
                        true
                    }

                    Key.DirectionUp -> {
                        viewModel.moveSelection(-1)
                        true
                    }

                    Key.Enter, Key.NumPadEnter -> {
                        state.selectedResult?.let(onOpenResult)
                        true
                    }

                    Key.Escape -> {
                        onClearFocus()
                        true
                    }

                    else -> false
                }
            },
    ) {
        when {
            state.isSearching && state.results.isEmpty() -> LoadingState("Searching…", Modifier.align(Alignment.TopStart))
            state.error != null -> ErrorState(state.error, Modifier.fillMaxWidth())
            state.hasSearched && state.results.isEmpty() -> EmptyState(
                icon = Icons.Outlined.SearchOff,
                title = "No matches",
                message = when (state.mode) {
                    SearchMode.REGEX -> "The regular expression matched nothing. Check the expression and case sensitivity."
                    SearchMode.FILENAME -> "No file names matched. Try a shorter fragment or a different spelling."
                    else -> "No notes contain this text. The index may be stale — sync it from the top bar."
                },
                modifier = Modifier.fillMaxSize(),
            )

            else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(
                    state.results,
                    // Keep identity stable across queries so unchanged rows are
                    // reused instead of being torn down on every keystroke.
                    key = { _, result -> "${result.fileId}-${result.line}-${result.relativePath}" },
                    contentType = { _, _ -> "search-result" },
                ) { index, result ->
                    SearchResultRow(
                        result = result,
                        query = state.query,
                        selected = index == state.selectedIndex,
                        onClick = {
                            onClearFocus()
                            onOpenResult(result)
                        },
                        timeLabel = result.lastModified?.let(timeLabels::get),
                    )
                    SearchResultDivider()
                }
                item {
                    Text(
                        text = "${state.results.size} results · ${state.durationMs} ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}
