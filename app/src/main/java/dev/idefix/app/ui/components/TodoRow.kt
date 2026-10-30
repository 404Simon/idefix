package dev.idefix.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.idefix.app.domain.model.TodoItem
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

@Composable
fun TodoRow(
    todo: TodoItem,
    pending: Boolean,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 4.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(
            checked = todo.completed,
            onCheckedChange = onToggle,
            enabled = !pending,
            modifier = Modifier.semantics { contentDescription = "Todo: ${todo.title}" },
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 10.dp),
        ) {
            Text(
                text = todo.title.ifBlank { "(untitled todo)" },
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = if (todo.completed) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textDecoration = if (todo.completed) {
                    TextDecoration.LineThrough
                } else {
                    TextDecoration.None
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (todo.body.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = todo.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (todo.due != null) {
                Spacer(Modifier.height(5.dp))
                val dueColor = when {
                    todo.isOverdue() -> MaterialTheme.colorScheme.error
                    todo.completed -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.primary
                }
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(dueColor.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Event,
                        contentDescription = null,
                        tint = dueColor,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = formatTodoDue(todo.due),
                        style = MaterialTheme.typography.labelSmall,
                        color = dueColor,
                    )
                }
            }
        }
    }
}

private val dueTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val dueDayFormatter = DateTimeFormatter.ofPattern("d MMM")

fun formatTodoDue(due: LocalDateTime, now: LocalDateTime = LocalDateTime.now()): String {
    val days = ChronoUnit.DAYS.between(now.toLocalDate(), due.toLocalDate())
    val day = when (days) {
        0L -> "Today"
        1L -> "Tomorrow"
        -1L -> "Yesterday"
        else -> due.format(dueDayFormatter)
    }
    return "$day ${due.format(dueTimeFormatter)}"
}
