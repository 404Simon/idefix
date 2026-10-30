package dev.idefix.app.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.idefix.app.domain.model.FileSummary
import dev.idefix.app.domain.model.TaskFilter
import dev.idefix.app.domain.model.TaskItem
import dev.idefix.app.ui.components.EmptyState
import dev.idefix.app.ui.components.ErrorState
import dev.idefix.app.ui.components.LoadingState
import dev.idefix.app.ui.components.TaskRow
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    state: TasksUiState,
    onOpenTask: (TaskItem) -> Unit,
    onRefresh: () -> Unit,
    viewModel: TasksViewModel,
    modifier: Modifier = Modifier,
) {
    var addOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        if (state.message != null) {
            delay(3500)
            viewModel.consumeMessage()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Column {
                        Text("Tasks", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(
                            text = "${state.openCount} open · ${state.completedCount} done" +
                                if (state.overdueCount > 0) " · ${state.overdueCount} overdue" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.overdueCount > 0) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh tasks")
                    }
                },
                windowInsets = WindowInsets(0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TaskFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.filter == filter,
                        onClick = { viewModel.setFilter(filter) },
                        label = {
                            Text(
                                text = filter.label + when (filter) {
                                    TaskFilter.OPEN -> " ${state.openCount}"
                                    TaskFilter.COMPLETED -> " ${state.completedCount}"
                                    TaskFilter.OVERDUE -> if (state.overdueCount > 0) " ${state.overdueCount}" else ""
                                    else -> ""
                                },
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

            if (state.message != null) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = viewModel::consumeMessage, modifier = Modifier.size(30.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }

            when {
                state.isLoading && state.tasks.isEmpty() -> LoadingState("Extracting tasks…")
                state.error != null && state.tasks.isEmpty() -> ErrorState(state.error, onRetry = viewModel::refresh)
                state.visibleTasks.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.Checklist,
                    title = when (state.filter) {
                        TaskFilter.ALL -> "No tasks found"
                        TaskFilter.OPEN -> "Nothing open"
                        TaskFilter.COMPLETED -> "Nothing completed yet"
                        TaskFilter.TODAY -> "Nothing due today"
                        TaskFilter.OVERDUE -> "Nothing overdue"
                    },
                    message = if (state.tasks.isEmpty()) {
                        "Tasks are parsed from Markdown checkboxes in your vault. Add one with the + button."
                    } else {
                        "Tasks from other filters are still in the list — switch filters above."
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(state.visibleTasks, key = { "${it.fileId}:${it.lineNumber}" }) { task ->
                        TaskRow(
                            task = task,
                            pending = viewModel.isPending(task),
                            onToggle = { completed -> viewModel.toggle(task, completed) },
                            onOpen = { onOpenTask(task) },
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(start = 58.dp)
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
                        )
                    }
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                viewModel.loadFiles()
                addOpen = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(18.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add task")
        }
    }

    if (addOpen) {
        AddTaskDialog(
            files = state.files,
            filesLoading = state.filesLoading,
            onDismiss = { addOpen = false },
            onAdd = { path, text, create ->
                viewModel.addTask(path, text, create)
                addOpen = false
            },
        )
    }
}

@Composable
private fun AddTaskDialog(
    files: List<FileSummary>,
    filesLoading: Boolean,
    onDismiss: () -> Unit,
    onAdd: (path: String, text: String, create: Boolean) -> Unit,
) {
    var taskText by remember { mutableStateOf("") }
    var fileFilter by remember { mutableStateOf("") }
    var selectedPath by remember { mutableStateOf<String?>(null) }
    var createNew by remember { mutableStateOf(false) }
    var newPath by remember { mutableStateOf("") }
    val taskFocus = remember { FocusRequester() }
    val filtered = remember(files, fileFilter) {
        if (fileFilter.isBlank()) files else files.filter {
            it.relativePath.contains(fileFilter, ignoreCase = true)
        }
    }
    val target = if (createNew) newPath.trim() else selectedPath.orEmpty()

    LaunchedEffect(Unit) {
        delay(120)
        taskFocus.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add task") },
        text = {
            Column {
                OutlinedTextField(
                    value = taskText,
                    onValueChange = { taskText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(taskFocus),
                    label = { Text("Task") },
                    placeholder = { Text("What needs doing?") },
                    singleLine = false,
                    maxLines = 3,
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = createNew, onCheckedChange = { createNew = it })
                    Text("Create a new note", style = MaterialTheme.typography.bodyMedium)
                }
                if (createNew) {
                    OutlinedTextField(
                        value = newPath,
                        onValueChange = { newPath = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("New note path") },
                        placeholder = { Text("Inbox.md or Projects/Plan.md") },
                        singleLine = true,
                    )
                } else {
                    OutlinedTextField(
                        value = fileFilter,
                        onValueChange = { fileFilter = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Find note") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Outlined.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        },
                    )
                    Spacer(Modifier.height(6.dp))
                    if (filesLoading) {
                        LoadingState("Loading notes…")
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 190.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerLow),
                        ) {
                            items(filtered.take(200), key = { it.relativePath }) { file ->
                                val selected = file.relativePath == selectedPath
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedPath = file.relativePath }
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                            else MaterialTheme.colorScheme.surfaceContainerLow,
                                        )
                                        .padding(horizontal = 10.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Outlined.Checklist,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = file.filename,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = file.relativePath,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = taskText.isNotBlank() && target.isNotBlank(),
                onClick = { onAdd(target, taskText, createNew) },
            ) { Text("Add") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
