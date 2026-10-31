package dev.idefix.app.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.idefix.app.domain.model.IndexState
import dev.idefix.app.domain.model.LectureInfo
import dev.idefix.app.domain.model.ThemeMode
import dev.idefix.app.domain.model.VaultInfo
import dev.idefix.app.ui.AppUiState
import dev.idefix.app.ui.components.SectionLabel
import dev.idefix.app.ui.components.relativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    appState: AppUiState,
    onSwitchVault: () -> Unit,
    onBrowseFiles: () -> Unit,
    onChooseLecturesFolder: () -> Unit,
    onClearLecturesFolder: () -> Unit,
    onRebuild: () -> Unit,
    onCancel: () -> Unit,
    onTheme: (ThemeMode) -> Unit,
    onIncludeHidden: (Boolean) -> Unit,
    onCaseSensitive: (Boolean) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onRecentLimit: (Int) -> Unit,
    onClearHistory: () -> Unit,
    onMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = appState.settings
    val context = LocalContext.current
    val indexState = appState.indexState

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = appState.vault?.displayName ?: "No vault",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            windowInsets = WindowInsets(0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 14.dp,
                end = 14.dp,
                top = 4.dp,
                bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item { SectionLabel("Vault") }
            item {
                SettingsCard {
                    VaultRow(
                        vault = appState.vault,
                        onSwitchVault = onSwitchVault,
                        onBrowseFiles = onBrowseFiles,
                    )
                }
            }

            item { SectionLabel("Lectures", Modifier.padding(top = 4.dp)) }
            item {
                SettingsCard {
                    LectureRow(
                        lecture = appState.lecture,
                        configured = settings.hasLecture,
                        onChooseFolder = onChooseLecturesFolder,
                        onClearFolder = onClearLecturesFolder,
                    )
                }
            }

            item { SectionLabel("Index", Modifier.padding(top = 4.dp)) }
            item {
                SettingsCard {
                    IndexSection(
                        state = indexState,
                        summaryCount = appState.indexSummary.fileCount,
                        lastIndexed = appState.indexSummary.lastIndexedAt,
                        failures = appState.indexSummary.failedCount,
                        onRebuild = onRebuild,
                        onCancel = onCancel,
                    )
                }
            }

            item { SectionLabel("Search", Modifier.padding(top = 4.dp)) }
            item {
                SettingsCard {
                    SwitchRow(
                        title = "Case sensitive by default",
                        description = "Used by literal and regex search. Toggle it per search from the search bar.",
                        checked = settings.caseSensitiveSearch,
                        onCheckedChange = onCaseSensitive,
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SwitchRow(
                        title = "Include hidden folders",
                        description = "Index folders such as .archive. .obsidian is always skipped. Reindexes the vault when changed.",
                        checked = settings.includeHidden,
                        onCheckedChange = onIncludeHidden,
                    )
                }
            }

            item { SectionLabel("Appearance", Modifier.padding(top = 4.dp)) }
            item {
                SettingsCard {
                    Text(
                        text = "Theme",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { onTheme(mode) },
                                label = {
                                    Text(
                                        when (mode) {
                                            ThemeMode.SYSTEM -> "System"
                                            ThemeMode.DARK -> "Dark"
                                            ThemeMode.LIGHT -> "Light"
                                            ThemeMode.MATCHA -> "Matcha"
                                        },
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    SwitchRow(
                        title = "Dynamic color",
                        description = "Use the wallpaper palette on Android 12 and newer. The Matcha theme always keeps its own palette.",
                        checked = settings.dynamicColor,
                        onCheckedChange = onDynamicColor,
                    )
                }
            }

            item { SectionLabel("Recent notes", Modifier.padding(top = 4.dp)) }
            item {
                SettingsCard {
                    Text("Recent notes limit", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "How many notes stay in the recent list on the Notes page. History is stored only in app storage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(25, 50, 100, 200).forEach { limit ->
                            FilterChip(
                                selected = settings.recentLimit == limit,
                                onClick = { onRecentLimit(limit) },
                                label = { Text("$limit") },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                    selectedLabelColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = onClearHistory) {
                        Text("Clear recent history")
                    }
                }
            }

            item { SectionLabel("Diagnostics", Modifier.padding(top = 4.dp)) }
            item {
                SettingsCard {
                    DiagnosticRow("Version", appState.versionName)
                    DiagnosticRow("Package", "dev.idefix.app")
                    DiagnosticRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    DiagnosticRow("Indexed notes", appState.indexSummary.fileCount.toString())
                    DiagnosticRow(
                        "Last scan",
                        appState.indexSummary.lastIndexedAt?.let(::relativeTime) ?: "never",
                    )
                    DiagnosticRow("Vault URI", appState.vault?.uri ?: "none", maxLines = 2)
                    DiagnosticRow("Lectures", appState.lecture?.displayName ?: "none")
                    DiagnosticRow("Lectures URI", appState.lecture?.uri ?: "none", maxLines = 2)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val text = buildString {
                                appendLine("Idefix ${appState.versionName}")
                                appendLine("Android ${Build.VERSION.RELEASE} API ${Build.VERSION.SDK_INT}")
                                appendLine("Vault: ${appState.vault?.displayName ?: "none"}")
                                appendLine("Vault URI: ${appState.vault?.uri ?: "none"}")
                                appendLine("Lectures: ${appState.lecture?.displayName ?: "none"}")
                                appendLine("Lectures URI: ${appState.lecture?.uri ?: "none"}")
                                appendLine("Indexed: ${appState.indexSummary.fileCount}")
                                appendLine("Failures: ${appState.indexSummary.failedCount}")
                                appendLine("Theme: ${settings.themeMode}")
                                appendLine("Include hidden: ${settings.includeHidden}")
                            }
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Idefix diagnostics", text))
                            onMessage("Diagnostics copied")
                        },
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.height(16.dp))
                        Spacer(Modifier.height(0.dp))
                        Text("  Copy diagnostics")
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = null,
                        modifier = Modifier.height(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "  Idefix is local-only. Files stay in your vault; the index lives in app storage.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            content()
        }
    }
}

@Composable
private fun VaultRow(
    vault: VaultInfo?,
    onSwitchVault: () -> Unit,
    onBrowseFiles: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = vault?.displayName ?: "No vault selected",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = vault?.uri ?: "Select a folder to begin.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onBrowseFiles) {
            Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.height(16.dp))
            Text("  Browse files")
        }
        Button(onClick = onSwitchVault) {
            Text("Switch vault")
        }
    }
}

@Composable
private fun LectureRow(
    lecture: LectureInfo?,
    configured: Boolean,
    onChooseFolder: () -> Unit,
    onClearFolder: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = lecture?.displayName ?: if (configured) "Lectures folder unavailable" else "No lectures folder",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = lecture?.uri ?: if (configured) {
                    "The permission is no longer available. Choose the folder again."
                } else {
                    "A separate folder for lecture PDFs and code, for example ~/Vorlesungen. Its subfolders become lectures."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onChooseFolder) {
            Icon(Icons.Outlined.FolderOpen, contentDescription = null, modifier = Modifier.height(16.dp))
            Text(if (configured) "  Change folder" else "  Choose folder")
        }
        if (configured) {
            TextButton(onClick = onClearFolder) {
                Text("Remove")
            }
        }
    }
}

@Composable
private fun IndexSection(
    state: IndexState,
    summaryCount: Int,
    lastIndexed: Long?,
    failures: Int,
    onRebuild: () -> Unit,
    onCancel: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when (state) {
                    is IndexState.Running -> "Indexing…"
                    is IndexState.Failed -> "Index failed"
                    else -> "$summaryCount notes"
                },
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = when (state) {
                    is IndexState.Running -> "${state.processed}/${state.discovered} · ${state.currentPath ?: ""}"
                    is IndexState.Failed -> state.message
                    else -> lastIndexed?.let { "Last scan ${relativeTime(it)}" } ?: "Not scanned yet"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (state is IndexState.Failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (failures > 0 && state !is IndexState.Running) {
                Text(
                    text = "$failures file(s) failed",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (state is IndexState.Running) {
            IconButton(onClick = onCancel) {
                Icon(Icons.Outlined.StopCircle, contentDescription = "Cancel indexing")
            }
        } else {
            IconButton(onClick = onRebuild) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Rebuild index")
            }
        }
    }
    if (state is IndexState.Running) {
        Spacer(Modifier.height(8.dp))
        if (state.discovered > 0) {
            LinearProgressIndicator(
                progress = { state.processed.toFloat() / state.discovered.coerceAtLeast(1) },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String, maxLines: Int = 1) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.5f),
        )
    }
}
