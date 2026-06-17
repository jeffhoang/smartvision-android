package app.streammog.android.ui.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.streammog.android.app.AppBrand
import app.streammog.android.app.AppEntitlements
import app.streammog.android.app.AppRuntimeMode
import app.streammog.android.domain.protocol.GlassesSessionClient
import app.streammog.android.shared.diagnostics.DiagnosticsEntry
import app.streammog.android.shared.diagnostics.DiagnosticsStore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    diagnosticsStore: DiagnosticsStore,
    glassesClient: GlassesSessionClient,
    runtimeMode: AppRuntimeMode,
    entitlements: AppEntitlements,
    onLaunchUpgrade: () -> Unit,
) {
    val entries by diagnosticsStore.entries.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    var showUpgradeDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Logs") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                navigationIcon = {
                    IconButton(
                        onClick = { diagnosticsStore.clear() },
                        enabled = entries.isNotEmpty(),
                    ) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "Clear logs")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (!entitlements.canExportDiagnostics) {
                                showUpgradeDialog = true
                            } else {
                                copyAllToClipboard(context, entries)
                            }
                        },
                        enabled = entries.isNotEmpty(),
                    ) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy all")
                    }
                    IconButton(
                        onClick = {
                            if (!entitlements.canExportDiagnostics) {
                                showUpgradeDialog = true
                                return@IconButton
                            }
                            val file = diagnosticsStore.exportDiagnosticsBundle()
                            if (file != null) {
                                val uri = androidx.core.content.FileProvider.getUriForFile(
                                    context, "${context.packageName}.fileprovider", file
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Diagnostics"))
                            }
                        },
                        enabled = entries.isNotEmpty(),
                    ) {
                        Icon(Icons.Outlined.IosShare, contentDescription = "Export bundle")
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Runtime", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        InfoRow("Mode", runtimeMode.displayName)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Text("Meta AI", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(
                            onClick = { scope.launch { glassesClient.resetRegistration() } },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Reset DAT Registration", color = MaterialTheme.colorScheme.error)
                        }
                        TextButton(
                            onClick = { scope.launch { glassesClient.openDATGlassesAppUpdate() } },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Open DAT App Update")
                        }
                        TextButton(
                            onClick = { scope.launch { glassesClient.openFirmwareUpdate() } },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Open Firmware Update")
                        }
                    }
                }
            }

            item {
                Text(
                    "Events (${entries.size})",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (entries.isEmpty()) {
                item {
                    Text(
                        "No log entries yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            } else {
                items(entries, key = { it.id }) { entry ->
                    DiagnosticsEntryRow(entry = entry, timeFormat = timeFormat)
                }
            }

            item { /* bottom padding */ }
        }
    }

    if (showUpgradeDialog) {
        AlertDialog(
            onDismissRequest = { showUpgradeDialog = false },
            title = { Text("Creator Feature") },
            text = {
                Text(
                    "Diagnostics export requires a Creator subscription. " +
                        "Upgrade to unlock export, unlimited streaming, multiple saved destinations, and more.",
                )
            },
            confirmButton = {
                TextButton(onClick = { showUpgradeDialog = false; onLaunchUpgrade() }) { Text("Upgrade") }
            },
            dismissButton = {
                TextButton(onClick = { showUpgradeDialog = false }) { Text("Not Now") }
            },
        )
    }
}

@Composable
private fun DiagnosticsEntryRow(entry: DiagnosticsEntry, timeFormat: SimpleDateFormat) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    entry.category.name.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = categoryColor(entry.category),
                )
                Text(
                    timeFormat.format(Date(entry.timestampMs)),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                entry.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun categoryColor(category: DiagnosticsEntry.Category): Color = when (category) {
    DiagnosticsEntry.Category.app -> Color(0xFF64B5F6)
    DiagnosticsEntry.Category.glasses -> Color(0xFF81C784)
    DiagnosticsEntry.Category.stream -> Color(0xFFFFB74D)
    DiagnosticsEntry.Category.error -> Color(0xFFEF5350)
}

private fun copyAllToClipboard(context: Context, entries: List<DiagnosticsEntry>) {
    val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    val text = entries.joinToString("\n") { entry ->
        "${timeFormat.format(Date(entry.timestampMs))} [${entry.category}] ${entry.message}"
    }
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(AppBrand.DIAGNOSTICS_TITLE, text))
}
