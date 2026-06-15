package app.streammog.android.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkAdded
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.streammog.android.app.AppBrand
import app.streammog.android.app.AppEntitlements
import app.streammog.android.coordinator.StreamingCoordinator
import app.streammog.android.domain.model.StreamDestination
import app.streammog.android.domain.model.StreamPreset
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(coordinator: StreamingCoordinator, entitlements: AppEntitlements) {
    val preset by coordinator.selectedPreset.collectAsState()
    val savedDestinations by coordinator.savedDestinations.collectAsState()
    val creatorDefaultSummary by coordinator.creatorDefaultSummary.collectAsState()

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Stream", "Quality", "App", "Info")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.background,
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        when (selectedTab) {
            0 -> StreamTab(preset = preset, onUpdate = coordinator::updatePreset)
            1 -> QualityTab(preset = preset, onUpdate = coordinator::updatePreset)
            2 -> AppTab(
                preset = preset,
                savedDestinations = savedDestinations,
                creatorDefaultSummary = creatorDefaultSummary,
                entitlements = entitlements,
                onUpdate = coordinator::updatePreset,
                onSaveDestination = { coordinator.saveCurrentDestination() },
                onApplyDestination = coordinator::applyDestination,
                onDeleteDestination = { coordinator.deleteDestinations(setOf(it)) },
                onSaveCreatorDefaults = { coordinator.saveCreatorDefaults() },
                onApplyCreatorDefaults = { coordinator.applyCreatorDefaults() },
                onClearCreatorDefaults = { coordinator.clearCreatorDefaults() },
                onResetPreset = { coordinator.resetPreset() },
            )
            3 -> InfoTab()
        }
    }
}

// ── Stream Tab ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StreamTab(preset: StreamPreset, onUpdate: (StreamPreset) -> Unit) {
    var presetName by rememberSaveable(preset.id) { mutableStateOf(preset.name) }
    var host by rememberSaveable(preset.id) { mutableStateOf(preset.host) }
    var appPath by rememberSaveable(preset.id) { mutableStateOf(preset.appPath) }
    var streamKey by rememberSaveable(preset.id) { mutableStateOf(preset.streamKey) }
    var streamKeyVisible by remember { mutableStateOf(false) }

    // Debounce text field saves
    LaunchedEffect(presetName) { delay(600); if (presetName != preset.name) onUpdate(preset.copy(name = presetName)) }
    LaunchedEffect(host) { delay(600); if (host != preset.host) onUpdate(preset.copy(host = host)) }
    LaunchedEffect(appPath) { delay(600); if (appPath != preset.appPath) onUpdate(preset.copy(appPath = appPath)) }
    LaunchedEffect(streamKey) { delay(600); if (streamKey != preset.streamKey) onUpdate(preset.copy(streamKey = streamKey)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsSection("Destination") {
            SettingsTextField("Preset name", presetName, onValueChange = { presetName = it })

            // Transport picker
            SettingsDropdown(
                label = "Transport",
                selected = preset.transport.displayName,
                options = StreamPreset.Transport.values().map { it to it.displayName },
                onSelect = { onUpdate(preset.copy(transport = it)) },
            )

            // Service picker (RTMP only)
            if (preset.transport == StreamPreset.Transport.RTMP) {
                SettingsDropdown(
                    label = "Service",
                    selected = preset.streamingService?.displayName ?: "Custom",
                    options = StreamPreset.StreamingService.selectableCases.map { it to it.displayName },
                    onSelect = { service ->
                        onUpdate(
                            preset.copy(
                                streamingService = service,
                                host = service.host.ifEmpty { preset.host },
                                appPath = service.appPath.ifEmpty { preset.appPath },
                                name = if (service != StreamPreset.StreamingService.CUSTOM) service.defaultPresetName else preset.name,
                            )
                        )
                        host = if (service.host.isNotEmpty()) service.host else host
                        appPath = if (service.appPath.isNotEmpty()) service.appPath else appPath
                    },
                )

                SettingsTextField(
                    "Host",
                    host,
                    onValueChange = { host = it },
                    keyboardType = KeyboardType.Uri,
                )
                SettingsTextField("App path", appPath, onValueChange = { appPath = it })

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    OutlinedTextField(
                        value = streamKey,
                        onValueChange = { streamKey = it },
                        label = { Text("Stream key") },
                        modifier = Modifier.weight(1f),
                        visualTransformation = if (streamKeyVisible) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                        singleLine = true,
                        colors = outlinedFieldColors(),
                    )
                    IconButton(onClick = { streamKeyVisible = !streamKeyVisible }) {
                        Text(
                            if (streamKeyVisible) "Hide" else "Show",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            SettingsToggle(
                label = "Record while streaming",
                checked = preset.recordWhileStreaming,
                onCheckedChange = { onUpdate(preset.copy(recordWhileStreaming = it)) },
            )
        }

        // Audio source
        SettingsSection("Audio") {
            SettingsDropdown(
                label = "Audio source",
                selected = preset.audioSource.displayName,
                options = StreamPreset.AudioSource.values().map { it to it.displayName },
                onSelect = { onUpdate(preset.copy(audioSource = it)) },
            )
            SettingsToggle(
                label = "Mute audio",
                checked = preset.isAudioMuted,
                onCheckedChange = { onUpdate(preset.copy(isAudioMuted = it)) },
            )
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ── Quality Tab ────────────────────────────────────────────────────────────

@Composable
private fun QualityTab(preset: StreamPreset, onUpdate: (StreamPreset) -> Unit) {
    var bitrateText by rememberSaveable(preset.id) { mutableStateOf(preset.targetBitrateKbps.toString()) }
    LaunchedEffect(bitrateText) {
        delay(600)
        val parsed = bitrateText.toIntOrNull()
        if (parsed != null && parsed != preset.targetBitrateKbps) onUpdate(preset.copy(targetBitrateKbps = parsed))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsSection("Quality Preset") {
            SettingsDropdown(
                label = "Preset",
                selected = preset.resolvedQualityPreset.displayName,
                options = StreamPreset.QualityPreset.values().map { it to it.displayName },
                onSelect = { onUpdate(preset.resolvingQualityPreset(it)) },
            )
        }

        SettingsSection("Custom") {
            SettingsTextField(
                label = "Target bitrate (kbps)",
                value = bitrateText,
                onValueChange = { bitrateText = it.filter { c -> c.isDigit() } },
                keyboardType = KeyboardType.Number,
            )
            SettingsDropdown(
                label = "Frame rate",
                selected = "${preset.targetFPS} fps",
                options = listOf(24, 30).map { it to "$it fps" },
                onSelect = { onUpdate(preset.copy(targetFPS = it)) },
            )
            SettingsDropdown(
                label = "Keyframe interval",
                selected = "${preset.targetKeyframeIntervalSeconds}s",
                options = listOf(1, 2, 3, 4).map { it to "${it}s" },
                onSelect = { onUpdate(preset.copy(targetKeyframeIntervalSeconds = it)) },
            )
            InfoRow("Source resolution", preset.targetResolution)
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ── App Tab ────────────────────────────────────────────────────────────────

@Composable
private fun AppTab(
    preset: StreamPreset,
    savedDestinations: List<StreamDestination>,
    creatorDefaultSummary: String?,
    entitlements: AppEntitlements,
    onUpdate: (StreamPreset) -> Unit,
    onSaveDestination: () -> Unit,
    onApplyDestination: (StreamDestination) -> Unit,
    onDeleteDestination: (Int) -> Unit,
    onSaveCreatorDefaults: () -> Unit,
    onApplyCreatorDefaults: () -> Unit,
    onClearCreatorDefaults: () -> Unit,
    onResetPreset: () -> Unit,
) {
    var showConfirmReset by remember { mutableStateOf(false) }

    if (showConfirmReset) {
        AlertDialog(
            onDismissRequest = { showConfirmReset = false },
            title = { Text("Reset Preset?") },
            text = { Text("This will restore all stream settings to defaults.") },
            confirmButton = {
                TextButton(onClick = { onResetPreset(); showConfirmReset = false }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmReset = false }) { Text("Cancel") }
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsSection("Behavior") {
            SettingsToggle(
                label = "Keep screen on while streaming",
                checked = preset.keepAwakeWhileStreaming,
                onCheckedChange = { onUpdate(preset.copy(keepAwakeWhileStreaming = it)) },
            )
        }

        // Saved destinations
        SettingsSection(
            title = "Saved Destinations",
            trailing = {
                IconButton(onClick = onSaveDestination) {
                    Icon(Icons.Outlined.BookmarkAdd, contentDescription = "Save current", tint = MaterialTheme.colorScheme.primary)
                }
            }
        ) {
            if (savedDestinations.isEmpty()) {
                Text(
                    "No saved destinations. Tap the bookmark button to save the current preset.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                savedDestinations.forEachIndexed { index, dest ->
                    if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(dest.name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                            Text(
                                "${dest.transport.displayName} · ${dest.host}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Row {
                            IconButton(onClick = { onApplyDestination(dest) }) {
                                Icon(Icons.Outlined.ChevronRight, contentDescription = "Apply", tint = MaterialTheme.colorScheme.primary)
                            }
                            IconButton(onClick = { onDeleteDestination(index) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        }

        // Creator defaults (entitlement-gated)
        if (entitlements.canUseCreatorDefaults) {
            SettingsSection("Creator Defaults") {
                if (creatorDefaultSummary != null) {
                    Text(creatorDefaultSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = onApplyCreatorDefaults) {
                            Icon(Icons.Outlined.BookmarkAdded, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                            Text("Apply")
                        }
                        TextButton(onClick = onSaveCreatorDefaults) { Text("Update") }
                        TextButton(onClick = onClearCreatorDefaults) { Text("Clear", color = MaterialTheme.colorScheme.error) }
                    }
                } else {
                    Text(
                        "Save your current preset as a creator default to quickly restore settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onSaveCreatorDefaults) {
                        Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                        Text("Save current as default")
                    }
                }
            }
        }

        // Reset
        SettingsSection("Reset") {
            TextButton(
                onClick = { showConfirmReset = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.RestartAlt, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text("Reset preset to defaults", color = MaterialTheme.colorScheme.error)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ── Info Tab ───────────────────────────────────────────────────────────────

@Composable
private fun InfoTab() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsSection("About") {
            InfoRow("App", AppBrand.DISPLAY_NAME)
            InfoRow("Version", "1.0.0")
            InfoRow("Support", AppBrand.SUPPORT_EMAIL)
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(Icons.Outlined.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    "${AppBrand.DISPLAY_NAME} streams from Meta AI Glasses using the MWDAT SDK. " +
                        "Configure your RTMP destination in the Stream tab. " +
                        "Use Test Stream to verify your connection before going live.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ── Shared Components ──────────────────────────────────────────────────────

@Composable
private fun SettingsSection(
    title: String,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                trailing?.invoke()
            }
            content()
        }
    }
}

@Composable
private fun SettingsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        colors = outlinedFieldColors(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SettingsDropdown(
    label: String,
    selected: String,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
            colors = outlinedFieldColors(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, display) ->
                DropdownMenuItem(
                    text = { Text(display) },
                    onClick = { onSelect(value); expanded = false },
                )
            }
        }
    }
}

@Composable
private fun SettingsToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun outlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    cursorColor = MaterialTheme.colorScheme.primary,
)
