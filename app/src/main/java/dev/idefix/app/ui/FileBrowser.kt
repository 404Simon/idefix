package dev.idefix.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.idefix.app.domain.model.FileSummary
import dev.idefix.app.domain.repository.SearchRepository
import dev.idefix.app.ui.components.EmptyState
import dev.idefix.app.ui.components.ErrorState
import dev.idefix.app.ui.components.FilePathLabel
import dev.idefix.app.ui.components.LoadingState
import dev.idefix.app.ui.components.SearchField
import dev.idefix.app.ui.components.relativeTime
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserDialog(
    searchRepository: SearchRepository,
    onOpenFile: (FileSummary) -> Unit,
    onCreateNote: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var files by remember { mutableStateOf<List<FileSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryToken by remember { mutableStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(query, retryToken) {
        if (query.isNotBlank()) delay(160)
        loading = true
        error = null
        runCatching { searchRepository.browseFiles(query) }
            .onSuccess { files = it }
            .onFailure { error = it.message ?: "Could not list files" }
        loading = false
    }
    LaunchedEffect(Unit) {
        delay(120)
        focusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close file browser")
                        }
                    },
                    title = {
                        Column {
                            Text("Vault files", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "${files.size} Markdown files",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onCreateNote) {
                            Icon(Icons.Default.Add, contentDescription = "New note")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
                Box(Modifier.padding(horizontal = 12.dp)) {
                    SearchField(
                        value = query,
                        onValueChange = { query = it },
                        onSubmit = {},
                        onClear = { query = "" },
                        focusRequester = focusRequester,
                        placeholder = "Filter by filename or path…",
                    )
                }
                Spacer(Modifier.height(8.dp))
                when {
                    loading && files.isEmpty() -> LoadingState("Listing notes…")
                    error != null -> ErrorState(error.orEmpty(), onRetry = { retryToken++ })
                    files.isEmpty() -> EmptyState(
                        icon = Icons.Outlined.FolderOpen,
                        title = if (query.isBlank()) "No indexed files" else "No files match",
                        message = if (query.isBlank()) {
                            "Build the index from search or settings, then try again."
                        } else {
                            "Try a shorter filter."
                        },
                        modifier = Modifier.fillMaxSize(),
                    )

                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        items(files, key = { it.fileId }) { file ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onOpenFile(file) }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
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
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        text = file.filename,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    FilePathLabel(path = file.relativePath, modifier = Modifier.padding(top = 1.dp))
                                }
                                if (file.lastModified != null) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = relativeTime(file.lastModified),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 54.dp)
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CreateNoteDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(100)
        focusRequester.requestFocus()
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New note") },
        text = {
            Column {
                Text(
                    text = "Create a Markdown file at the vault root. You can choose a folder by typing a path, for example Projects/Plan.md.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    label = { Text("File name") },
                    placeholder = { Text("Inbox.md") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name) },
                enabled = name.isNotBlank(),
            ) { Text("Create") }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
