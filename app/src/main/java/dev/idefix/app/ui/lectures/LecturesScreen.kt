package dev.idefix.app.ui.lectures

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Audiotrack
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.FolderZip
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.idefix.app.domain.model.LectureEntry
import dev.idefix.app.domain.model.LectureEntryKind
import dev.idefix.app.ui.components.EmptyState
import dev.idefix.app.ui.components.ErrorState
import dev.idefix.app.ui.components.LoadingState
import dev.idefix.app.ui.components.SearchField
import dev.idefix.app.ui.components.SectionLabel
import dev.idefix.app.ui.components.relativeTime
import dev.idefix.app.ui.theme.MonoStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LecturesScreen(
    state: LecturesUiState,
    configured: Boolean,
    twoPane: Boolean,
    onChooseFolder: () -> Unit,
    onOpenExternal: (LectureEntry) -> Unit,
    viewModel: LecturesViewModel,
    modifier: Modifier = Modifier,
) {
    val viewer = state.viewer
    if (viewer != null) {
        LectureViewerScreen(
            viewer = viewer,
            onBack = viewModel::closeViewer,
            onRetry = viewModel::retryViewer,
            onOpenExternal = { onOpenExternal(viewer.entry) },
            modifier = modifier,
        )
        return
    }

    BackHandler(enabled = state.canGoBack) { viewModel.goBack() }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            navigationIcon = {
                if (state.canGoBack) {
                    IconButton(onClick = viewModel::goBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            },
            title = {
                Column {
                    Text(
                        text = state.currentFolder?.name ?: "Lectures",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = when {
                            state.currentFolder != null ->
                                "${state.entries.size} items · ${state.lecture?.displayName ?: "Lectures"}"

                            state.lecture != null ->
                                "${state.rootFolders.size} folders · ${state.lecture.displayName}"

                            else -> "Not set up"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            actions = {
                IconButton(onClick = viewModel::refresh) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "Refresh lectures")
                }
                IconButton(onClick = onChooseFolder) {
                    Icon(Icons.Outlined.FolderOpen, contentDescription = "Choose lectures folder")
                }
            },
            windowInsets = WindowInsets(0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )

        if (state.lecture == null) {
            EmptyState(
                icon = Icons.Outlined.School,
                title = if (configured) "Lectures folder unavailable" else "Set up lectures",
                message = if (configured) {
                    "The permission for the lectures folder is no longer available. Select the folder again."
                } else {
                    "Select a separate folder with your lecture material, for example the local copy of ~/Vorlesungen. Every subfolder becomes a lecture."
                },
                actionLabel = "Choose lectures folder",
                onAction = onChooseFolder,
                modifier = Modifier.fillMaxSize(),
            )
            return
        }

        if (twoPane) {
            Row(Modifier.fillMaxSize()) {
                RootPane(
                    state = state,
                    selectedDocumentId = state.selectedLecture?.documentId,
                    showHeader = true,
                    onSelect = viewModel::selectLecture,
                    onChooseFolder = onChooseFolder,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier
                        .widthIn(min = 260.dp, max = 340.dp)
                        .fillMaxHeight(),
                )
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                if (state.currentFolder == null) {
                    EmptyState(
                        icon = Icons.Outlined.School,
                        title = "Select a lecture",
                        message = "Choose a lecture on the left to browse its PDFs and code.",
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    BrowserPane(
                        state = state,
                        onBreadcrumb = viewModel::openBreadcrumb,
                        onOpenFolder = viewModel::openFolder,
                        onOpenText = viewModel::openText,
                        onOpenExternal = onOpenExternal,
                        onRetry = viewModel::refresh,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else if (state.currentFolder == null) {
            RootPane(
                state = state,
                selectedDocumentId = null,
                showHeader = false,
                onSelect = viewModel::selectLecture,
                onChooseFolder = onChooseFolder,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            BrowserPane(
                state = state,
                onBreadcrumb = viewModel::openBreadcrumb,
                onOpenFolder = viewModel::openFolder,
                onOpenText = viewModel::openText,
                onOpenExternal = onOpenExternal,
                onRetry = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun RootPane(
    state: LecturesUiState,
    selectedDocumentId: String?,
    showHeader: Boolean,
    onSelect: (LectureEntry) -> Unit,
    onChooseFolder: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoadingFolders && state.rootFolders.isEmpty() -> {
            LoadingState("Reading lecture folders…", modifier)
        }

        state.error != null && state.rootFolders.isEmpty() -> {
            ErrorState(
                message = state.error,
                onRetry = onRefresh,
                modifier = modifier.fillMaxSize(),
            )
        }

        state.rootFolders.isEmpty() -> {
            EmptyState(
                icon = Icons.Outlined.School,
                title = "No lecture folders",
                message = "Add subfolders to ${state.lecture?.displayName ?: "the lectures folder"}, sync them, then refresh.",
                actionLabel = "Choose another folder",
                onAction = onChooseFolder,
                modifier = modifier.fillMaxSize(),
            )
        }

        else -> {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                if (showHeader) {
                    item {
                        SectionLabel(
                            text = "Lecture folders",
                            modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 6.dp),
                        )
                    }
                }
                items(state.rootFolders, key = { it.documentId }) { folder ->
                    LectureFolderRow(
                        folder = folder,
                        selected = folder.documentId == selectedDocumentId,
                        onClick = { onSelect(folder) },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 60.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowserPane(
    state: LecturesUiState,
    onBreadcrumb: (Int) -> Unit,
    onOpenFolder: (LectureEntry) -> Unit,
    onOpenText: (LectureEntry) -> Unit,
    onOpenExternal: (LectureEntry) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember(state.currentFolder?.documentId) { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val visibleEntries = remember(state.entries, query) {
        if (query.isBlank()) {
            state.entries
        } else {
            state.entries.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.relativePath.contains(query, ignoreCase = true)
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        BreadcrumbBar(
            stack = state.folderStack,
            onClick = onBreadcrumb,
        )
        if (query.isNotBlank() || state.entries.size >= 8) {
            Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    onSubmit = {},
                    onClear = { query = "" },
                    focusRequester = focusRequester,
                    placeholder = "Filter in ${state.currentFolder?.name ?: "folder"}…",
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

        when {
            state.isLoadingEntries && state.entries.isEmpty() -> {
                LoadingState("Reading ${state.currentFolder?.name ?: "folder"}…", Modifier.fillMaxWidth())
            }

            state.error != null -> {
                ErrorState(
                    message = state.error,
                    onRetry = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            visibleEntries.isEmpty() -> {
                EmptyState(
                    icon = Icons.Outlined.FolderOpen,
                    title = if (query.isBlank()) "Empty folder" else "No matches",
                    message = if (query.isBlank()) {
                        "This folder has no files or subfolders."
                    } else {
                        "Try a shorter filter."
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                ) {
                    items(visibleEntries, key = { it.documentId }) { entry ->
                        LectureEntryRow(
                            entry = entry,
                            onClick = {
                                when {
                                    entry.isDirectory -> onOpenFolder(entry)
                                    entry.kind.isText -> onOpenText(entry)
                                    else -> onOpenExternal(entry)
                                }
                            },
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 60.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BreadcrumbBar(
    stack: List<LectureEntry>,
    onClick: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        stack.forEachIndexed { index, folder ->
            if (index == stack.lastIndex) {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            } else {
                Text(
                    text = folder.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onClick(index) }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LectureFolderRow(
    folder: LectureEntry,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EntryIcon(kind = LectureEntryKind.DIRECTORY)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = folder.lastModified?.let { "Folder · ${relativeTime(it)}" } ?: "Folder",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LectureEntryRow(
    entry: LectureEntry,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EntryIcon(kind = entry.kind)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entrySubtitle(entry, context),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (entry.isDirectory) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EntryIcon(kind: LectureEntryKind) {
    val tint = when (kind) {
        LectureEntryKind.DIRECTORY -> MaterialTheme.colorScheme.primary
        LectureEntryKind.PDF -> MaterialTheme.colorScheme.error
        LectureEntryKind.CODE -> MaterialTheme.colorScheme.tertiary
        LectureEntryKind.TEXT -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = iconFor(kind),
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = tint,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LectureViewerScreen(
    viewer: LectureViewerState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpenExternal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            title = {
                Column {
                    Text(
                        text = viewer.entry.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = viewer.entry.relativePath,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            actions = {
                IconButton(onClick = onOpenExternal) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open with another app")
                }
            },
            windowInsets = WindowInsets(0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )

        when {
            viewer.isLoading -> LoadingState("Opening ${viewer.entry.name}…")
            viewer.error != null -> ErrorState(
                message = viewer.error,
                onRetry = onRetry,
                modifier = Modifier.fillMaxWidth(),
            )

            viewer.content != null -> {
                CodeViewer(
                    content = viewer.content,
                    entry = viewer.entry,
                    modifier = Modifier.weight(1f),
                )
            }

            else -> LoadingState("Opening ${viewer.entry.name}…")
        }
    }
}

@Composable
private fun CodeViewer(
    content: String,
    entry: LectureEntry,
    modifier: Modifier = Modifier,
) {
    val horizontal = rememberScrollState()
    val vertical = rememberScrollState()
    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                .horizontalScroll(horizontal),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(vertical)
                    .padding(14.dp),
            ) {
                SelectionContainer {
                    Text(
                        text = content,
                        style = MonoStyle,
                        softWrap = false,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        ViewerStatusBar(
            entry = entry,
            content = content,
            isLoading = false,
        )
    }
}

@Composable
private fun ViewerStatusBar(
    entry: LectureEntry,
    content: String?,
    isLoading: Boolean,
) {
    val context = LocalContext.current
    val lineCount = remember(content) {
        content?.takeIf { it.isNotEmpty() }?.count { it == '\n' }?.plus(1) ?: 0
    }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (isLoading) "Loading…" else "$lineCount lines",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = buildString {
                    append(entry.kind.label)
                    entry.size?.let { append(" · ${Formatter.formatFileSize(context, it)}") }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun entrySubtitle(entry: LectureEntry, context: android.content.Context): String {
    val parts = buildList {
        add(if (entry.isDirectory) "Folder" else entry.kind.label)
        if (!entry.isDirectory) {
            entry.size?.let { add(Formatter.formatFileSize(context, it)) }
        }
        entry.lastModified?.let { add(relativeTime(it)) }
    }
    return parts.joinToString(" · ")
}

private fun iconFor(kind: LectureEntryKind): ImageVector = when (kind) {
    LectureEntryKind.DIRECTORY -> Icons.Outlined.FolderOpen
    LectureEntryKind.PDF -> Icons.Outlined.PictureAsPdf
    LectureEntryKind.CODE -> Icons.Outlined.Code
    LectureEntryKind.TEXT -> Icons.Outlined.Description
    LectureEntryKind.IMAGE -> Icons.Outlined.Image
    LectureEntryKind.AUDIO -> Icons.Outlined.Audiotrack
    LectureEntryKind.VIDEO -> Icons.Outlined.Movie
    LectureEntryKind.ARCHIVE -> Icons.Outlined.FolderZip
    LectureEntryKind.OTHER -> Icons.AutoMirrored.Outlined.InsertDriveFile
}
