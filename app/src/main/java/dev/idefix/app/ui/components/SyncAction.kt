package dev.idefix.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * One sync indicator for the whole app. It spins for full index reconciliation
 * as well as queued todo writes. A spinner is only cancellable when the caller
 * provides [onCancel] (currently the index reconciliation).
 */
@Composable
fun SyncAction(
    isSyncing: Boolean,
    onSync: () -> Unit,
    failed: Boolean = false,
    onCancel: (() -> Unit)? = null,
) {
    when {
        isSyncing -> {
            if (onCancel != null) {
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.semantics { contentDescription = "Cancel sync" },
                ) {
                    SyncSpinner()
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .semantics { contentDescription = "Syncing" },
                    contentAlignment = Alignment.Center,
                ) {
                    SyncSpinner()
                }
            }
        }

        failed -> IconButton(onClick = onSync) {
            Icon(
                Icons.Default.Sync,
                contentDescription = "Retry sync",
                tint = MaterialTheme.colorScheme.error,
            )
        }

        else -> IconButton(onClick = onSync) {
            Icon(Icons.Default.Sync, contentDescription = "Sync vault")
        }
    }
}

@Composable
private fun SyncSpinner() {
    CircularProgressIndicator(
        modifier = Modifier.size(20.dp),
        strokeWidth = 2.dp,
    )
}
