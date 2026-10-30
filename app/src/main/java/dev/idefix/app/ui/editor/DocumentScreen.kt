package dev.idefix.app.ui.editor

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.idefix.app.domain.model.DocumentMode
import dev.idefix.app.ui.components.EmptyState
import dev.idefix.app.ui.components.ErrorState
import dev.idefix.app.ui.components.LoadingState
import dev.idefix.app.ui.theme.MonoStyle
import dev.idefix.app.util.MarkdownLines
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScreen(
    state: DocumentUiState,
    showBack: Boolean,
    onBack: () -> Unit,
    onOpenInternalLink: (String) -> Unit,
    onDeleted: () -> Unit,
    viewModel: DocumentViewModel,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var deleteOpen by remember { mutableStateOf(false) }
    var renameText by remember { mutableStateOf("") }
    val findFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(state.findVisible) {
        if (state.findVisible) {
            delay(80)
            findFocus.requestFocus()
        }
    }
    LaunchedEffect(state.saveMessage) {
        if (state.saveMessage != null) {
            delay(4000)
            viewModel.clearSaveMessage()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                if (event.isCtrlPressed) {
                    when (event.key) {
                        Key.S -> {
                            if (state.isDirty) viewModel.save()
                            true
                        }

                        Key.F -> {
                            viewModel.setMode(DocumentMode.SOURCE)
                            viewModel.showFind()
                            true
                        }

                        else -> false
                    }
                } else if (event.key == Key.Escape) {
                    if (state.findVisible) {
                        viewModel.hideFind()
                        true
                    } else {
                        onBack()
                        true
                    }
                } else {
                    false
                }
            },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                navigationIcon = {
                    if (showBack) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                title = {
                    Column {
                        Text(
                            text = state.file?.displayName ?: "Note",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (state.file != null) {
                            Text(
                                text = state.file.relativePath,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.setMode(
                                if (state.mode == DocumentMode.PREVIEW) DocumentMode.SOURCE else DocumentMode.PREVIEW,
                            )
                        },
                    ) {
                        Icon(
                            imageVector = if (state.mode == DocumentMode.PREVIEW) {
                                Icons.Default.Edit
                            } else {
                                Icons.Outlined.Visibility
                            },
                            contentDescription = if (state.mode == DocumentMode.PREVIEW) "Edit source" else "Preview",
                        )
                    }
                    IconButton(onClick = { viewModel.save() }, enabled = state.isDirty && !state.isSaving) {
                        if (state.isSaving) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(
                                Icons.Outlined.Save,
                                contentDescription = "Save",
                                tint = if (state.isDirty) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Note actions")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Find in note") },
                                leadingIcon = { Icon(Icons.Outlined.FindReplace, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    viewModel.setMode(DocumentMode.SOURCE)
                                    viewModel.showFind()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Rename…") },
                                onClick = {
                                    menuOpen = false
                                    renameText = state.file?.displayName ?: ""
                                    renameOpen = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete…") },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    deleteOpen = true
                                },
                            )
                        }
                    }
                },
                windowInsets = WindowInsets(0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )

            if (state.saveMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Done,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = state.saveMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = viewModel::clearSaveMessage,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }

            if (state.findVisible) {
                FindBar(
                    state = state,
                    focusRequester = findFocus,
                    onQueryChange = viewModel::setFindQuery,
                    onNext = viewModel::findNext,
                    onPrevious = viewModel::findPrevious,
                    onClose = {
                        viewModel.hideFind()
                        keyboard?.hide()
                    },
                )
            }

            when {
                state.isLoading -> LoadingState("Opening note…", Modifier.padding(16.dp))
                state.error != null -> ErrorState(
                    message = state.error,
                    onRetry = viewModel::retry,
                    modifier = Modifier.fillMaxWidth(),
                )

                state.file == null -> EmptyState(
                    icon = Icons.Outlined.Article,
                    title = "No note selected",
                    message = "Choose a note from search, tasks, or recent files.",
                )

                state.mode == DocumentMode.PREVIEW -> {
                    androidx.compose.runtime.key(state.file?.uri) {
                        MarkdownPreview(
                            content = state.editContent,
                            onInternalLink = onOpenInternalLink,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            initialScroll = state.scrollOffset,
                            onScrollChanged = viewModel::onScrollChanged,
                        )
                    }
                }

                else -> SourceEditor(
                    state = state,
                    viewModel = viewModel,
                    modifier = Modifier.weight(1f),
                )
            }
            if (state.hasDocument) {
                SourceStatusBar(state)
            }
        }
    }

    if (state.conflict != null) {
        val conflict = state.conflict
        AlertDialog(
            onDismissRequest = viewModel::dismissConflict,
            title = { Text("Note changed on disk") },
            text = {
                Text(
                    conflict.message +
                        " Reloading discards your edits. Overwriting keeps your version and replaces the external one.",
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::reloadConflict) { Text("Reload") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = viewModel::dismissConflict) { Text("Cancel") }
                    TextButton(
                        onClick = viewModel::overwriteConflict,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Overwrite") }
                }
            },
        )
    }

    if (renameOpen) {
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("Rename note") },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("File name (include .md)") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (renameText.isNotBlank()) viewModel.rename(renameText)
                        renameOpen = false
                    },
                ) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { renameOpen = false }) { Text("Cancel") }
            },
        )
    }

    if (deleteOpen) {
        AlertDialog(
            onDismissRequest = { deleteOpen = false },
            title = { Text("Delete note?") },
            text = {
                Text(
                    "Delete ${state.file?.displayName ?: "this note"} from the vault? This cannot be undone and also removes it from Obsidian.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteOpen = false
                        viewModel.delete(onDeleted)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deleteOpen = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun FindBar(
    state: DocumentUiState,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.findQuery,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                singleLine = true,
                placeholder = { Text("Find in note") },
                textStyle = MaterialTheme.typography.bodySmall,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onNext() }),
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                },
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (state.findMatches.isEmpty()) {
                    "0/0"
                } else {
                    "${state.findIndex + 1}/${state.findMatches.size}"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconButton(onClick = onPrevious, enabled = state.findMatches.isNotEmpty()) {
                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Previous match")
            }
            IconButton(onClick = onNext, enabled = state.findMatches.isNotEmpty()) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Next match")
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close find")
            }
        }
    }
}

@Composable
private fun SourceEditor(
    state: DocumentUiState,
    viewModel: DocumentViewModel,
    modifier: Modifier = Modifier,
) {
    var value by remember { mutableStateOf(TextFieldValue(state.editContent)) }
    var lastSelectionId by remember { mutableStateOf(-1L) }
    val editorFocus = remember { FocusRequester() }

    LaunchedEffect(state.editContent) {
        if (value.text != state.editContent) {
            value = value.copy(text = state.editContent, selection = TextRange(state.editContent.length.coerceAtMost(value.selection.start)))
        }
    }
    LaunchedEffect(state.selectionRequest) {
        val request = state.selectionRequest ?: return@LaunchedEffect
        if (request.id != lastSelectionId) {
            lastSelectionId = request.id
            val offset = request.offset.coerceIn(0, value.text.length)
            value = value.copy(selection = TextRange(offset))
            editorFocus.requestFocus()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = {
                value = it
                viewModel.onTextChanged(it.text, it.selection.start)
            },
            modifier = Modifier
                .fillMaxSize()
                .focusRequester(editorFocus),
            textStyle = MonoStyle.copy(
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                lineHeight = 22.sp,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
            ),
        )
    }
}

@Composable
private fun SourceStatusBar(state: DocumentUiState) {
    val content = state.editContent
    val column = state.cursorOffset - MarkdownLines.offsetForLine(content, state.cursorLine) + 1
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Ln ${state.cursorLine}, Col ${column.coerceAtLeast(1)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "${MarkdownLines.lineCount(content)} lines · ${content.length} chars",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = when {
                    state.isSaving -> "Saving…"
                    state.isDirty -> "Unsaved *"
                    else -> "Saved"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (state.isDirty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}


