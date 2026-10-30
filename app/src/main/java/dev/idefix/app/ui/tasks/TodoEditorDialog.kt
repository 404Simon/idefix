package dev.idefix.app.ui.tasks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.idefix.app.domain.model.TodoDraft
import dev.idefix.app.domain.model.TodoItem
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodoEditorDialog(
    todo: TodoItem?,
    onDismiss: () -> Unit,
    onSave: (TodoDraft) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var title by remember(todo) { mutableStateOf(todo?.title.orEmpty()) }
    var body by remember(todo) { mutableStateOf(todo?.body.orEmpty()) }
    var due by remember(todo) { mutableStateOf(todo?.due) }
    var datePickerOpen by remember { mutableStateOf(false) }
    var timePickerOpen by remember { mutableStateOf(false) }
    var pendingDate by remember { mutableStateOf<LocalDate?>(null) }
    val titleFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(todo) {
        delay(120)
        titleFocus.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (todo == null) "New todo" else "Edit todo") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(titleFocus),
                    label = { Text("Title") },
                    singleLine = true,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Details") },
                    placeholder = { Text("Optional notes") },
                    minLines = 3,
                    maxLines = 6,
                )
                Spacer(Modifier.height(8.dp))
                DueRow(
                    due = due,
                    onPick = {
                        keyboard?.hide()
                        datePickerOpen = true
                    },
                    onClear = { due = null },
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(TodoDraft(title.trim(), body.trim(), due)) },
                enabled = title.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text("Delete") }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )

    if (datePickerOpen) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = due?.toLocalDate()?.toUtcMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { datePickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            pendingDate = Instant.ofEpochMilli(millis)
                                .atZone(ZoneOffset.UTC)
                                .toLocalDate()
                            datePickerOpen = false
                            timePickerOpen = true
                        }
                    },
                    enabled = datePickerState.selectedDateMillis != null,
                ) { Text("Next") }
            },
            dismissButton = {
                TextButton(onClick = { datePickerOpen = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (timePickerOpen) {
        val timePickerState = rememberTimePickerState(
            initialHour = due?.hour ?: 9,
            initialMinute = due?.minute ?: 0,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = {
                timePickerOpen = false
                pendingDate = null
            },
            title = { Text("Due time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDate?.let { date ->
                            due = date.atTime(timePickerState.hour, timePickerState.minute)
                        }
                        timePickerOpen = false
                        pendingDate = null
                    },
                ) { Text("Set") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        timePickerOpen = false
                        pendingDate = null
                    },
                ) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun DueRow(
    due: LocalDateTime?,
    onPick: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Event,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = due?.format(dueFormatter) ?: "No due date",
            style = MaterialTheme.typography.bodyMedium,
            color = if (due == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (due != null) {
            IconButton(onClick = onClear) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Clear due date",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        TextButton(onClick = onPick) {
            Text(if (due == null) "Set" else "Change")
        }
    }
}

private val dueFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")

private fun LocalDate.toUtcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
