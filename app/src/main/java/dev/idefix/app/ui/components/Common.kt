package dev.idefix.app.ui.components

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.idefix.app.domain.model.IndexState
import dev.idefix.app.domain.model.IndexSummary
import dev.idefix.app.domain.model.SNIPPET_HIGHLIGHT_END
import dev.idefix.app.domain.model.SNIPPET_HIGHLIGHT_START

@Composable
fun FilePathLabel(
    path: String,
    modifier: Modifier = Modifier,
    muted: Boolean = true,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Outlined.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = path.ifBlank { "/" },
            style = MaterialTheme.typography.bodySmall,
            color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun LoadingState(text: String = "Loading…", modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ErrorState(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(modifier = modifier.padding(24.dp)) {
        Text(
            text = "Something went wrong",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (onRetry != null) {
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onRetry) { Text("Try again") }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
fun IndexStatusRow(
    indexState: IndexState,
    summary: IndexSummary,
    onRebuild: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        when (indexState) {
            is IndexState.Running -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when (indexState.phase) {
                                dev.idefix.app.domain.model.IndexPhase.SCANNING ->
                                    "Scanning vault · ${indexState.discovered} notes"

                                dev.idefix.app.domain.model.IndexPhase.READING ->
                                    "Indexing ${indexState.processed}/${indexState.discovered}"

                                dev.idefix.app.domain.model.IndexPhase.FINALISING -> "Finishing index…"
                                dev.idefix.app.domain.model.IndexPhase.CANCELLED -> "Indexing cancelled"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (indexState.currentPath != null) {
                            Text(
                                text = indexState.currentPath,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Outlined.StopCircle, contentDescription = "Cancel indexing", modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (indexState.discovered > 0) {
                    LinearProgressIndicator(
                        progress = { indexState.processed.toFloat() / indexState.discovered.coerceAtLeast(1) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            is IndexState.Failed -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Index failed: ${indexState.message}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onRebuild) { Text("Retry") }
                }
            }

            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        val count = if (indexState is IndexState.Ready) indexState.fileCount else summary.fileCount
                        val last = if (indexState is IndexState.Ready) indexState.lastIndexedAt else summary.lastIndexedAt
                        Text(
                            text = when {
                                count == 0 -> "No notes indexed"
                                else -> "$count notes indexed"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        val failures = if (indexState is IndexState.Ready) indexState.failures else summary.failedCount
                        if (failures > 0) {
                            Text(
                                text = "$failures file(s) could not be indexed",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        } else if (last != null && count > 0) {
                            Text(
                                text = "Updated ${relativeTime(last)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    IconButton(onClick = onRebuild, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Rebuild index", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

fun relativeTime(timestamp: Long): String = DateUtils.getRelativeTimeSpanString(
    timestamp,
    System.currentTimeMillis(),
    DateUtils.MINUTE_IN_MILLIS,
    DateUtils.FORMAT_ABBREV_RELATIVE,
).toString()

@Composable
fun HighlightedText(
    text: String,
    query: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    maxLines: Int = 3,
    highlightColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
) {
    Text(
        text = highlightSnippet(text, query, highlightColor),
        style = style,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

fun highlightSnippet(
    text: String,
    query: String,
    highlightColor: androidx.compose.ui.graphics.Color,
): AnnotatedString = buildAnnotatedString {
    val builder = StringBuilder()
    var highlighted = false
    for (char in text) {
        when (char) {
            SNIPPET_HIGHLIGHT_START -> highlighted = true
            SNIPPET_HIGHLIGHT_END -> highlighted = false
            else -> builder.append(char)
        }
    }
    val clean = builder.toString()
    append(clean)
    val style = SpanStyle(
        background = highlightColor.copy(alpha = 0.22f),
        color = highlightColor,
        fontWeight = FontWeight.SemiBold,
    )
    // Marker ranges first.
    var markerStart = -1
    var cleanIndex = 0
    for (char in text) {
        when (char) {
            SNIPPET_HIGHLIGHT_START -> markerStart = cleanIndex
            SNIPPET_HIGHLIGHT_END -> {
                if (markerStart >= 0) {
                    addStyle(style, markerStart, cleanIndex)
                    markerStart = -1
                }
            }
            else -> cleanIndex++
        }
    }
    if (query.isNotBlank()) {
        val terms = query.split(Regex("\\s+")).filter { it.length >= 2 }
        for (term in terms) {
            var index = clean.indexOf(term, ignoreCase = true)
            while (index >= 0) {
                addStyle(style, index, index + term.length)
                index = clean.indexOf(term, index + term.length, ignoreCase = true)
            }
        }
    }
}
