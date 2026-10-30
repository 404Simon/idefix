package dev.idefix.app.ui.tasks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.idefix.app.domain.model.IndexState
import dev.idefix.app.domain.model.TodoItem
import dev.idefix.app.domain.model.TodoList
import dev.idefix.app.ui.components.EmptyState
import dev.idefix.app.ui.components.ErrorState
import dev.idefix.app.ui.components.LoadingState
import dev.idefix.app.ui.components.SyncAction
import dev.idefix.app.ui.components.TodoRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    state: TasksUiState,
    indexState: IndexState,
    isSyncing: Boolean,
    twoPane: Boolean,
    onSync: () -> Unit,
    onCancelSync: () -> Unit,
    viewModel: TasksViewModel,
    modifier: Modifier = Modifier,
) {
    var editorOpen by remember { mutableStateOf(false) }
    var editingTodo by remember { mutableStateOf<TodoItem?>(null) }
    var newListOpen by remember { mutableStateOf(false) }

    LaunchedEffect(twoPane, state.lists, state.selectedList) {
        if (twoPane && state.selectedList == null && state.lists.isNotEmpty()) {
            viewModel.selectList(state.lists.first())
        }
    }
    BackHandler(enabled = !twoPane && state.selectedList != null) {
        viewModel.backToList()
    }

    fun openAddTodo() {
        editingTodo = null
        editorOpen = true
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                navigationIcon = {
                    if (!twoPane && state.selectedList != null) {
                        IconButton(onClick = viewModel::backToList) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to lists")
                        }
                    }
                },
                title = {
                    Column {
                        Text(
                            text = if (!twoPane && state.selectedList != null) {
                                state.selectedList.name
                            } else {
                                "Tasks"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = if (state.selectedList != null) {
                                "${state.selectedList.relativePath} · ${state.openCount} open" +
                                    if (state.overdueCount > 0) " · ${state.overdueCount} overdue" else ""
                            } else {
                                "${state.lists.size} lists"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (state.overdueCount > 0) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                actions = {
                    SyncAction(
                        isSyncing = isSyncing || indexState.isRunning,
                        onSync = onSync,
                        failed = indexState is IndexState.Failed,
                        onCancel = if (indexState.isRunning) onCancelSync else null,
                    )
                },
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )

            when {
                twoPane -> {
                    Row(modifier = Modifier.weight(1f)) {
                        ListsPane(
                            lists = state.lists,
                            selected = state.selectedList,
                            loading = state.isLoadingLists,
                            error = state.error.takeIf { state.selectedList == null },
                            creatingPath = state.creatingListPath,
                            onSelect = viewModel::selectList,
                            onCreateList = { newListOpen = true },
                            onRetry = viewModel::refresh,
                            showHeader = true,
                            modifier = Modifier
                                .widthIn(min = 260.dp, max = 340.dp)
                                .fillMaxHeight(),
                        )
                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        if (state.selectedList == null) {
                            EmptyState(
                                icon = Icons.Outlined.Checklist,
                                title = "Select a list",
                                message = "Choose a todo list on the left.",
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            TasksPane(
                                state = state,
                                showHeader = true,
                                onRetry = viewModel::refresh,
                                viewModel = viewModel,
                                onEdit = { todo ->
                                    if (todo.startLine > 0) {
                                        editingTodo = todo
                                        editorOpen = true
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }

                state.selectedList == null -> ListsPane(
                    lists = state.lists,
                    selected = null,
                    loading = state.isLoadingLists,
                    error = state.error,
                    creatingPath = state.creatingListPath,
                    onSelect = viewModel::selectList,
                    onCreateList = { newListOpen = true },
                    onRetry = viewModel::refresh,
                    showHeader = false,
                    modifier = Modifier.weight(1f),
                )

                else -> TasksPane(
                    state = state,
                    showHeader = false,
                    onRetry = viewModel::refresh,
                    viewModel = viewModel,
                    onEdit = { todo ->
                        if (todo.startLine > 0) {
                            editingTodo = todo
                            editorOpen = true
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        FloatingActionButton(
            onClick = {
                if (state.selectedList == null) {
                    newListOpen = true
                } else {
                    openAddTodo()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(18.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = if (state.selectedList == null) "New list" else "Add todo",
            )
        }
    }

    if (newListOpen) {
        NewListDialog(
            onDismiss = { newListOpen = false },
            onCreate = { name ->
                newListOpen = false
                viewModel.createList(name)
            },
        )
    }

    if (editorOpen) {
        TodoEditorDialog(
            todo = editingTodo,
            onDismiss = { editorOpen = false },
            onSave = { draft ->
                viewModel.saveTask(editingTodo, draft)
                editorOpen = false
            },
            onDelete = editingTodo?.let { todo ->
                {
                    viewModel.deleteTask(todo)
                    editorOpen = false
                }
            },
        )
    }
}

@Composable
private fun ListsPane(
    lists: List<TodoList>,
    selected: TodoList?,
    loading: Boolean,
    error: String?,
    creatingPath: String?,
    onSelect: (TodoList) -> Unit,
    onCreateList: () -> Unit,
    onRetry: () -> Unit,
    showHeader: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (showHeader) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Lists",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onCreateList) {
                    Icon(Icons.Default.Add, contentDescription = "New list")
                }
            }
        }
        when {
            loading && lists.isEmpty() -> LoadingState("Loading lists…")
            error != null && lists.isEmpty() -> ErrorState(error, onRetry = onRetry)
            lists.isEmpty() -> EmptyState(
                icon = Icons.Outlined.Checklist,
                title = "No todo lists",
                message = "Create TODO.md, job.TODO.md, or add a list here.",
                actionLabel = "Create list",
                onAction = onCreateList,
                modifier = Modifier.fillMaxSize(),
            )

            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(lists, key = { it.relativePath }) { list ->
                    TodoListCard(
                        list = list,
                        selected = selected?.relativePath == list.relativePath,
                        creating = creatingPath == list.relativePath,
                        onClick = { onSelect(list) },
                    )
                }
                item { Spacer(Modifier.height(88.dp)) }
            }
        }
    }
}

@Composable
private fun TodoListCard(
    list: TodoList,
    selected: Boolean,
    creating: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        shape = RoundedCornerShape(14.dp),
        onClick = { if (!creating) onClick() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (selected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Checklist,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(21.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = list.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = list.relativePath,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                if (creating) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(13.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = "Creating…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = if (list.openCount == 0) "No open todos" else "${list.openCount} open",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (list.overdueCount > 0) {
                            Text(
                                text = "${list.overdueCount} overdue",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun TasksPane(
    state: TasksUiState,
    showHeader: Boolean,
    onRetry: () -> Unit,
    viewModel: TasksViewModel,
    onEdit: (TodoItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val list = state.selectedList ?: return
    val visible = state.visibleTasks()

    Column(modifier = modifier) {
        if (showHeader) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = list.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = list.relativePath,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = "${state.openCount} open",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = state.hideFuture,
                onClick = { viewModel.setHideFuture(!state.hideFuture) },
                label = { Text("Hide future", style = MaterialTheme.typography.labelMedium) },
                leadingIcon = {
                    Icon(
                        Icons.Outlined.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                    selectedLeadingIconColor = MaterialTheme.colorScheme.primary,
                ),
            )
            Spacer(Modifier.weight(1f))
            if (state.overdueCount > 0) {
                Text(
                    text = "${state.overdueCount} overdue",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        when {
            state.isLoadingTasks && state.tasks.isEmpty() -> LoadingState("Loading todos…")
            state.error != null && state.tasks.isEmpty() -> ErrorState(state.error, onRetry = onRetry)
            visible.isEmpty() -> EmptyState(
                icon = Icons.Outlined.Checklist,
                title = if (state.hideFuture) "Nothing due right now" else "No todos yet",
                message = if (state.hideFuture) {
                    "Future todos are hidden."
                } else {
                    "Add your first todo with the + button."
                },
                modifier = Modifier.fillMaxSize(),
            )

            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(
                    visible,
                    key = { it.startLine },
                    contentType = { "todo" },
                ) { todo ->
                    TodoRow(
                        todo = todo,
                        onToggle = { completed -> viewModel.toggle(todo, completed) },
                        onClick = { onEdit(todo) },
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    )
                }
                item { Spacer(Modifier.height(88.dp)) }
            }
        }
    }
}

@Composable
private fun NewListDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New todo list") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("List name") },
                    placeholder = { Text("job") },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Creates job.TODO.md. Use main for TODO.md.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name) },
                enabled = name.isNotBlank(),
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
