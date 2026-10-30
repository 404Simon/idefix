package dev.idefix.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.NoteAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.ManageSearch
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import dev.idefix.app.domain.model.SearchCommand
import dev.idefix.app.domain.model.SearchMode
import dev.idefix.app.domain.model.SearchResult
import dev.idefix.app.ui.components.EmptyState
import dev.idefix.app.ui.components.ErrorState
import dev.idefix.app.ui.components.FilePathLabel
import dev.idefix.app.ui.components.HighlightedText
import dev.idefix.app.ui.components.IndexStatusRow
import dev.idefix.app.ui.components.LoadingState
import dev.idefix.app.ui.components.SearchField
import dev.idefix.app.ui.components.SearchResultDivider
import dev.idefix.app.ui.components.SearchResultRow
import dev.idefix.app.ui.components.SectionLabel
import dev.idefix.app.ui.components.relativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: SearchUiState,
    indexState: IndexState,
    vaultName: String,
    focusRequestToken: Int,
    onCreateNote: () -> Unit,
    onCreateNamedNote: (String) -> Unit,
    onOpenFileBrowser: () -> Unit,
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
    var autoFocused by remember { mutableStateOf(false) }

    LaunchedEffect(focusRequestToken) {
        if (focusRequestToken > 0) {
            viewModel.rearmCommand()
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    LaunchedEffect(state.summary.fileCount) {
        if (!autoFocused && state.summary.fileCount > 0) {
            autoFocused = true
            focusRequester.requestFocus()
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
                IconButton(onClick = onOpenFileBrowser) {
                    Icon(Icons.Outlined.FolderOpen, contentDescription = "Browse vault files")
                }
                IconButton(onClick = onCreateNote) {
                    Icon(Icons.Default.Add, contentDescription = "New note")
                }
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
                        DropdownMenuItem(
                            text = { Text("Rebuild index") },
                            onClick = {
                                menuOpen = false
                                onRebuild()
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
            )
            Spacer(Modifier.height(8.dp))
            CommandBar(
                state = state,
                onCommand = viewModel::applyCommand,
            )
            Spacer(Modifier.height(8.dp))
            IndexStatusRow(
                indexState = indexState,
                summary = state.summary,
                onRebuild = onRebuild,
                onCancel = onCancelIndex,
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
                onUseRecentSearch = viewModel::useRecentSearch,
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
private fun CommandBar(
    state: SearchUiState,
    onCommand: (SearchCommand) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchCommand.entries.forEach { command ->
            val selected = when (command) {
                SearchCommand.FILENAME -> state.mode == SearchMode.FILENAME && !state.prefixOnly && !state.creatingNote
                SearchCommand.GREP -> state.mode == SearchMode.TEXT && !state.creatingNote
                SearchCommand.NEW_NOTE -> state.creatingNote
                SearchCommand.MODUL -> state.prefixOnly
            }
            FilterChip(
                selected = selected,
                onClick = { onCommand(command) },
                label = {
                    Text(
                        text = "${command.key}  ${command.label}",
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                ),
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
    onUseRecentSearch: (dev.idefix.app.domain.model.RecentSearch) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.padding(horizontal = 12.dp)) {
        if (state.recentSearches.isNotEmpty()) {
            item {
                SectionLabel("Recent searches", Modifier.padding(top = 8.dp, bottom = 6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    state.recentSearches.take(12).forEach { search ->
                        AssistChip(
                            onClick = { onUseRecentSearch(search) },
                            label = {
                                Text(
                                    text = search.query,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        item {
            SectionLabel("Recent notes", Modifier.padding(top = 8.dp, bottom = 6.dp))
        }
        if (state.recentFiles.isEmpty()) {
            item {
                EmptyState(
                    icon = Icons.Default.Description,
                    title = "No recent notes yet",
                    message = if (state.summary.fileCount == 0) {
                        "Index a vault to search filenames or full text. Use the rebuild button above, or browse files directly."
                    } else {
                        "Open a note from search, the file browser, or tasks. It will appear here."
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        } else {
            items(state.recentFiles, key = { it.fileId }) { file ->
                RecentFileRow(file = file, onClick = { onOpenRecent(file) })
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun RecentFileRow(file: RecentFile, onClick: () -> Unit) {
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
                    text = relativeTime(file.openedAt),
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
    val savedScroll = remember(viewModel) { viewModel.scrollPosition() }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = savedScroll.first,
        initialFirstVisibleItemScrollOffset = savedScroll.second,
    )
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) -> viewModel.saveScroll(index, offset) }
    }
    LaunchedEffect(state.query, state.mode) {
        if (listState.firstVisibleItemIndex != 0) listState.scrollToItem(0)
    }
    LaunchedEffect(state.selectedIndex) {
        if (state.results.isNotEmpty()) {
            listState.animateScrollToItem(state.selectedIndex.coerceIn(0, state.results.lastIndex))
        }
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
                    else -> "No notes contain this text. The index may be stale — rebuild it from the status bar."
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
                    Column(modifier = Modifier.animateItem()) {
                        SearchResultRow(
                            result = result,
                            query = state.query,
                            selected = index == state.selectedIndex,
                            onClick = {
                                onClearFocus()
                                onOpenResult(result)
                            },
                        )
                        SearchResultDivider()
                    }
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
