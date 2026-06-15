package app.streammog.android.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.StatFs
import android.text.format.Formatter
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkAdded
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.VideocamOff
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
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
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
            0 -> StreamTab(
                preset = preset,
                savedDestinations = savedDestinations,
                entitlements = entitlements,
                onUpdate = coordinator::updatePreset,
                onSaveDestination = { coordinator.saveCurrentDestination() },
                onApplyDestination = coordinator::applyDestination,
                onDeleteDestination = { coordinator.deleteDestinations(setOf(it)) },
            )
            1 -> QualityTab(preset = preset, entitlements = entitlements, onUpdate = coordinator::updatePreset)
            2 -> AppTab(
                preset = preset,
                creatorDefaultSummary = creatorDefaultSummary,
                entitlements = entitlements,
                onUpdate = coordinator::updatePreset,
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
private fun StreamTab(
    preset: StreamPreset,
    savedDestinations: List<StreamDestination>,
    entitlements: AppEntitlements,
    onUpdate: (StreamPreset) -> Unit,
    onSaveDestination: () -> Unit,
    onApplyDestination: (StreamDestination) -> Unit,
    onDeleteDestination: (Int) -> Unit,
) {
    var presetName by rememberSaveable(preset.id) { mutableStateOf(preset.name) }
    var host by rememberSaveable(preset.id) { mutableStateOf(preset.host) }
    var appPath by rememberSaveable(preset.id) { mutableStateOf(preset.appPath) }
    var streamKey by rememberSaveable(preset.id) { mutableStateOf(preset.streamKey) }
    var streamKeyVisible by remember { mutableStateOf(false) }

    LaunchedEffect(presetName) { delay(600); if (presetName != preset.name) onUpdate(preset.copy(name = presetName)) }
    LaunchedEffect(host) { delay(600); if (host != preset.host) onUpdate(preset.copy(host = host)) }
    LaunchedEffect(appPath) { delay(600); if (appPath != preset.appPath) onUpdate(preset.copy(appPath = appPath)) }
    LaunchedEffect(streamKey) { delay(600); if (streamKey != preset.streamKey) onUpdate(preset.copy(streamKey = streamKey)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── Destination ───────────────────────────────────────────────────

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionHeader("Destination")
            SectionCard {
                // Preset name
                CardTextRow(presetName, { presetName = it }, "Preset name")
                CardDivider()

                // Transport
                CardPickerRow(
                    label = "Transport",
                    selected = preset.transport.displayName,
                    options = StreamPreset.Transport.values().map { it to it.displayName },
                    onSelect = { t ->
                        val updatedName = when (t) {
                            StreamPreset.Transport.LOCAL_RECORDING -> "Phone Local Recording"
                            StreamPreset.Transport.SRT -> "SRT Stream"
                            else -> preset.streamingService?.defaultPresetName ?: preset.name
                        }
                        onUpdate(preset.copy(transport = t, name = updatedName))
                        if (t != StreamPreset.Transport.RTMP) presetName = updatedName
                    },
                )

                if (preset.transport == StreamPreset.Transport.RTMP) {
                    CardDivider()
                    // Service
                    CardPickerRow(
                        label = "Service",
                        selected = preset.streamingService?.displayName ?: "Custom",
                        options = StreamPreset.StreamingService.selectableCases.map { it to it.displayName },
                        onSelect = { service ->
                            val newName = if (service != StreamPreset.StreamingService.CUSTOM) service.defaultPresetName else preset.name
                            onUpdate(
                                preset.copy(
                                    streamingService = service,
                                    host = service.host.ifEmpty { preset.host },
                                    appPath = service.appPath.ifEmpty { preset.appPath },
                                    name = newName,
                                )
                            )
                            presetName = newName
                            if (service.host.isNotEmpty()) host = service.host
                            if (service.appPath.isNotEmpty()) appPath = service.appPath
                        },
                    )
                    CardDivider()
                    // Host
                    CardTextRow(host, { host = it }, "Host", keyboardType = KeyboardType.Uri)
                    CardDivider()
                    // App path
                    CardTextRow(appPath, { appPath = it }, "App path")
                    CardDivider()
                    // Stream key — hidden or revealed
                    if (preset.isStreamKeyLocked && !streamKeyVisible) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                Icons.Outlined.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                "Stream key hidden",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { streamKeyVisible = true }) {
                                Text("Reveal", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    } else {
                        CardTextRow(
                            value = streamKey,
                            onValueChange = { streamKey = it },
                            placeholder = "Stream key",
                            keyboardType = KeyboardType.Password,
                            visualTransformation = PasswordVisualTransformation(),
                            trailingContent = if (preset.isStreamKeyLocked) {
                                {
                                    TextButton(onClick = { streamKeyVisible = false }) {
                                        Text("Hide", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            } else null,
                        )
                    }
                    CardDivider()
                    CardToggleRow(
                        label = "Hide stream key",
                        checked = preset.isStreamKeyLocked,
                        onCheckedChange = { locked ->
                            onUpdate(preset.copy(isStreamKeyLocked = locked))
                            if (!locked) streamKeyVisible = true
                        },
                    )
                    CardDivider()
                    CardToggleRow(
                        label = "Require biometrics to reveal",
                        checked = preset.requiresBiometricsForStreamKey,
                        onCheckedChange = { onUpdate(preset.copy(requiresBiometricsForStreamKey = it)) },
                        enabled = preset.isStreamKeyLocked,
                    )
                    CardDivider()
                    CardActionRow(Icons.Outlined.QrCode, "Scan RTMP QR") { /* TODO: QR scanner */ }
                    CardDivider()
                    // Publish URL + service hint
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            "Publish URL",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        val urlResult = preset.makePublishUrl()
                        Text(
                            urlResult.getOrNull() ?: "Invalid configuration",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = if (urlResult.isSuccess) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.error,
                        )
                        val hint = serviceHelpText(preset.streamingService)
                        if (hint != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                hint,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else {
                    // Local recording / SRT
                    CardDivider()
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        InfoRow("Target", "Phone storage")
                        InfoRow("Format", "MP4 (H.264/AAC)")
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Local recordings save native DAT frames on this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // ── Saved Destinations ─────────────────────────────────────────────

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionHeader("Saved Destinations")
            SectionCard {
                if (savedDestinations.isEmpty()) {
                    Text(
                        "Save the current destination after entering its host and stream key.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                    )
                } else {
                    savedDestinations.forEachIndexed { index, dest ->
                        if (index > 0) CardDivider()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onApplyDestination(dest) }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(dest.name, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    destinationLabel(dest),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { onDeleteDestination(index) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
                CardDivider()
                CardActionRow(Icons.Outlined.Add, "Save Current Destination", onClick = onSaveDestination)
            }
        }

        // ── Audio ──────────────────────────────────────────────────────────

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionHeader("Audio")
            SectionCard {
                CardPickerRow(
                    label = "Source",
                    selected = preset.audioSource.displayName,
                    options = StreamPreset.AudioSource.values().map { it to it.displayName },
                    onSelect = { onUpdate(preset.copy(audioSource = it)) },
                )
                CardDivider()
                CardToggleRow(
                    label = "Muted",
                    checked = preset.isAudioMuted,
                    onCheckedChange = { onUpdate(preset.copy(isAudioMuted = it)) },
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

private fun destinationLabel(dest: StreamDestination): String = when (dest.transport) {
    StreamPreset.Transport.RTMP -> "${dest.host}/${dest.appPath}".trimEnd('/')
    StreamPreset.Transport.LOCAL_RECORDING -> "Phone storage"
    StreamPreset.Transport.SRT -> dest.host
}

private fun serviceHelpText(service: StreamPreset.StreamingService?): String? = when (service) {
    null -> null
    StreamPreset.StreamingService.CUSTOM -> "Enter the RTMP or RTMPS server details from your streaming provider."
    StreamPreset.StreamingService.YOUTUBE -> "Use the stream key from YouTube Studio Live Control Room."
    StreamPreset.StreamingService.TWITCH -> "Use your Twitch stream key. The default ingest uses Twitch's global ingest host."
    StreamPreset.StreamingService.KICK -> "Use your Kick stream key. Kick currently provides an RTMPS server URL."
    StreamPreset.StreamingService.FACEBOOK -> "Use the stream key from Facebook Live Producer. Facebook supports secure RTMPS ingest."
    StreamPreset.StreamingService.RESTREAM -> "Use your Restream stream key to send one ${AppBrand.DISPLAY_NAME} feed to multiple platforms."
    StreamPreset.StreamingService.INSTAGRAM -> "Paste the RTMP URL and stream key from Instagram Live Producer. Access is account-limited."
    StreamPreset.StreamingService.TIKTOK -> "Paste the RTMP URL and stream key from TikTok Live Studio or your creator live tools."
    StreamPreset.StreamingService.LINKEDIN -> "Paste the Stream URL and Stream Key from LinkedIn Live Studio for the selected event."
    StreamPreset.StreamingService.VIMEO -> "Paste the RTMP/RTMPS stream URL and stream ID from your Vimeo live event."
    StreamPreset.StreamingService.RUMBLE -> "Use your Rumble stream key with the Rumble live ingest URL, or paste the URL Rumble provides."
}

// ── Quality Tab ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QualityTab(
    preset: StreamPreset,
    entitlements: AppEntitlements,
    onUpdate: (StreamPreset) -> Unit,
) {
    val context = LocalContext.current
    val availableStorageLabel = remember {
        try {
            val stat = StatFs(context.filesDir.path)
            val bytes = stat.availableBlocksLong * stat.blockSizeLong
            Formatter.formatShortFileSize(context, bytes)
        } catch (_: Exception) { "Unavailable" }
    }
    val service = preset.streamingService
    val profileSummary: String? = run {
        val b = service?.recommendedBitrateKbps ?: return@run null
        val f = service.recommendedFps ?: return@run null
        val k = service.recommendedKeyframeIntervalSeconds ?: return@run null
        "Recommended profile: $b kbps, $f fps, ${k}s keyframes."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── Quality ───────────────────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionHeader("Quality")
            SectionCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Source Resolution", style = MaterialTheme.typography.bodyMedium)
                    Text("720x1280", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                CardDivider()
                CardPickerRow(
                    label = "Quality",
                    selected = preset.resolvedQualityPreset.displayName,
                    options = StreamPreset.QualityPreset.values().map { it to it.displayName },
                    onSelect = { onUpdate(preset.resolvingQualityPreset(it)) },
                )
                CardDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Bitrate", style = MaterialTheme.typography.bodyMedium)
                    Text("${preset.targetBitrateKbps} kbps", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                CardDivider()
                CardPickerRow(
                    label = "FPS",
                    selected = "${preset.targetFPS}",
                    options = listOf(30).map { it to "$it" },
                    onSelect = { onUpdate(preset.copy(targetFPS = it)) },
                )
                CardDivider()
                CardPickerRow(
                    label = "Keyframe",
                    selected = "${preset.targetKeyframeIntervalSeconds}s",
                    options = listOf(1, 2, 3, 4).map { it to "${it}s" },
                    onSelect = { onUpdate(preset.copy(targetKeyframeIntervalSeconds = it)) },
                )
                if (profileSummary != null) {
                    CardDivider()
                    Text(
                        profileSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
                CardDivider()
                Text(
                    "720x1280 is the current maximum resolution available from Meta DAT, at up to 30 fps. Quality presets tune bitrate and output behavior, not source resolution.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }

        // ── Reliability ───────────────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionHeader("Reliability")
            SectionCard {
                CardToggleRow(
                    label = "Keep Awake While Streaming",
                    checked = preset.keepAwakeWhileStreaming,
                    onCheckedChange = { onUpdate(preset.copy(keepAwakeWhileStreaming = it)) },
                )
            }
            Text(
                "This prevents Android auto-lock while ${AppBrand.DISPLAY_NAME} is actively streaming or recording.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }

        // ── Recording ─────────────────────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionHeader("Recording")
            if (entitlements.canUseLocalRecording) {
                SectionCard {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Available Storage", style = MaterialTheme.typography.bodyMedium)
                        Text(availableStorageLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (preset.transport == StreamPreset.Transport.RTMP) {
                        CardDivider()
                        CardToggleRow(
                            label = "Record While Streaming",
                            checked = preset.recordWhileStreaming,
                            onCheckedChange = { onUpdate(preset.copy(recordWhileStreaming = it)) },
                        )
                    }
                    CardDivider()
                    CardToggleRow(
                        label = "Local Best Quality",
                        checked = preset.localBestQualityMode,
                        onCheckedChange = { onUpdate(preset.copy(localBestQualityMode = it)) },
                    )
                    CardDivider()
                    CardToggleRow(
                        label = "Auto-delete if storage is low",
                        checked = preset.autoDeleteRecordingsWhenLowStorage,
                        onCheckedChange = { onUpdate(preset.copy(autoDeleteRecordingsWhenLowStorage = it)) },
                    )
                    CardDivider()
                    CardStepperRow(
                        label = "Keep under ${if (preset.recordingStorageLimitGB == 0) "No app limit" else "${preset.recordingStorageLimitGB} GB"}",
                        value = preset.recordingStorageLimitGB,
                        range = 0..100,
                        onValueChange = { onUpdate(preset.copy(recordingStorageLimitGB = it)) },
                    )
                    CardDivider()
                    CardPickerRow(
                        label = "Keep for",
                        selected = retentionDaysLabel(preset.recordingRetentionDays),
                        options = listOf(0, 1, 3, 7, 14, 30).map { it to retentionDaysLabel(it) },
                        onSelect = { onUpdate(preset.copy(recordingRetentionDays = it)) },
                    )
                    CardDivider()
                    CardPickerRow(
                        label = "Auto Split",
                        selected = splitLabel(preset.recordingSplitMinutes),
                        options = listOf(0, 10, 20, 30).map { it to splitLabel(it) },
                        onSelect = { onUpdate(preset.copy(recordingSplitMinutes = it)) },
                    )
                    CardDivider()
                    CardTextRow(
                        value = preset.recordingName,
                        onValueChange = { onUpdate(preset.copy(recordingName = it)) },
                        placeholder = "Recording name",
                    )
                    CardDivider()
                    CardTextRow(
                        value = preset.recordingTags,
                        onValueChange = { onUpdate(preset.copy(recordingTags = it)) },
                        placeholder = "Tags, comma separated",
                        imeAction = ImeAction.Done,
                    )
                    CardDivider()
                    Text(
                        "Best quality uses local HEVC up to 30 Mbps. Auto-split creates separate MP4 files and exports each completed segment to Photos.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    CardDivider()
                    Text(
                        "Retention cleanup runs when Settings opens and removes the oldest app recordings plus their thumbnails and metadata. Photos exports are not deleted.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            } else {
                SectionCard {
                    Text(
                        "Local recording is available on Freemium, but sessions stop automatically after 4 minutes. Creator removes the limit.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }

        // ── Local Recordings ──────────────────────────────────────────────────
        if (entitlements.canUseLocalRecording) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                SectionHeader("Local Recordings")
                SectionCard {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Outlined.VideocamOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(36.dp),
                            )
                            Text("No Recordings", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Recordings appear here after Local Recording is stopped.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp),
                            )
                        }
                    }
                    CardDivider()
                    CardActionRow(Icons.Outlined.Refresh, "Refresh Recordings", onClick = {})
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ── App Tab ────────────────────────────────────────────────────────────────

@Composable
private fun AppTab(
    preset: StreamPreset,
    creatorDefaultSummary: String?,
    entitlements: AppEntitlements,
    onUpdate: (StreamPreset) -> Unit,
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
    val context = LocalContext.current
    var showAbout by remember { mutableStateOf(false) }
    var showPrivacy by remember { mutableStateOf(false) }
    var showTerms by remember { mutableStateOf(false) }

    val versionName = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.0" }
        catch (_: Exception) { "1.0.0" }
    }
    @Suppress("DEPRECATION")
    val versionCode = remember {
        try { context.packageManager.getPackageInfo(context.packageName, 0).versionCode.toString() }
        catch (_: Exception) { "1" }
    }

    fun openSupportEmail() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(AppBrand.SUPPORT_EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, "${AppBrand.DISPLAY_NAME} Support")
        }
        try { context.startActivity(intent) } catch (_: Exception) {}
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SettingsSection("About") {
            Text(
                "${AppBrand.DISPLAY_NAME} helps creators preview their smart glasses camera, " +
                    "fine-tune the shot, go live, and save recordings in one place.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            InfoRow("Version", versionName)
            InfoRow("Build", versionCode)
            InfoRow("Support", AppBrand.SUPPORT_EMAIL)
        }

        SettingsSection("Support & Legal") {
            SettingsLinkRow(
                icon = Icons.Outlined.Info,
                title = "About ${AppBrand.DISPLAY_NAME}",
                onClick = { showAbout = true },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            SettingsLinkRow(
                icon = Icons.Outlined.Security,
                title = "Privacy Policy",
                onClick = { showPrivacy = true },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            SettingsLinkRow(
                icon = Icons.Outlined.Description,
                title = "Terms of Use",
                onClick = { showTerms = true },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            SettingsLinkRow(
                icon = Icons.Outlined.Email,
                title = "Contact Support",
                onClick = { openSupportEmail() },
            )
        }

        Spacer(Modifier.height(24.dp))
    }

    if (showAbout) AboutSheet(onDismiss = { showAbout = false })
    if (showPrivacy) PrivacySheet(onDismiss = { showPrivacy = false })
    if (showTerms) TermsSheet(onDismiss = { showTerms = false })
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

// ── Card-row primitives (Stream tab) ──────────────────────────────────────

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column { content() }
    }
}

@Composable
private fun CardDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun CardTextRow(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    imeAction: ImeAction = ImeAction.Next,
    trailingContent: @Composable (() -> Unit)? = null,
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    val placeholderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    val cursorColor = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor),
            cursorBrush = SolidColor(cursorColor),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
            visualTransformation = visualTransformation,
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(placeholder, style = MaterialTheme.typography.bodyMedium, color = placeholderColor)
                    }
                    innerTextField()
                }
            },
        )
        trailingContent?.invoke()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> CardPickerRow(
    label: String,
    selected: String,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(selected, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            }
        }
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (v, display) ->
                DropdownMenuItem(text = { Text(display) }, onClick = { onSelect(v); expanded = false })
            }
        }
    }
}

@Composable
private fun CardToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun CardActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun CardStepperRow(label: String, value: Int, range: IntRange, onValueChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (value > range.first) onValueChange(value - 1) }, enabled = value > range.first) {
                Icon(Icons.Outlined.Remove, contentDescription = "Decrease", modifier = Modifier.size(18.dp))
            }
            VerticalDivider(modifier = Modifier.height(20.dp))
            IconButton(onClick = { if (value < range.last) onValueChange(value + 1) }, enabled = value < range.last) {
                Icon(Icons.Outlined.Add, contentDescription = "Increase", modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun retentionDaysLabel(days: Int): String =
    if (days == 0) "No age limit" else "$days day${if (days == 1) "" else "s"}"

private fun splitLabel(minutes: Int): String =
    if (minutes == 0) "Off" else "$minutes min"

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

@Composable
private fun SettingsLinkRow(icon: ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Icon(
            Icons.Outlined.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Info Sheets ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AboutSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "About ${AppBrand.DISPLAY_NAME}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${AppBrand.DISPLAY_NAME} is a simple way to turn your smart glasses into a live streaming and recording setup.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "It is built for streamers, creators, and influencers who want to see their shot, make quick adjustments, and start sharing without digging through a lot of menus.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(Modifier.height(4.dp))

            Text(
                "What It Does",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))

            AboutFeatureRow(
                title = "See your shot before you go live",
                detail = "Preview what your audience will see and quickly adjust zoom, rotation, mirror, brightness, contrast, and overall look.",
            )
            AboutFeatureRow(
                title = "Start streaming faster",
                detail = "Once your setup is ready, you can go live in a tap instead of bouncing between multiple apps and settings.",
            )
            AboutFeatureRow(
                title = "Keep a copy of your content",
                detail = "Save recordings on your phone so you can post clips later, review footage, or keep a backup of a live session.",
            )
            AboutFeatureRow(
                title = "Catch problems early",
                detail = "Check connection, stream health, and recording status in one place so you can spot issues before they ruin a stream.",
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(Modifier.height(4.dp))

            Text(
                "Support",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Need help, have feedback, or want to ask a privacy question? Contact ${AppBrand.SUPPORT_EMAIL}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AboutFeatureRow(title: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrivacySheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Privacy Policy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            PolicySection("Summary") {
                Text(
                    "${AppBrand.DISPLAY_NAME} is a smart-glasses live streaming app. This app is not positioned or marketed as a surveillance product.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "The app requests access only to the data needed for live preview, streaming, local recording, diagnostics, and saving local media.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PolicySection("Data We Access") {
                PolicyRow("Camera and glasses video", "Used to preview, stream, and optionally record the live session you start.")
                PolicyRow("Microphone audio", "Used when your selected audio path includes the phone microphone or another routed input.")
                PolicyRow("Local network", "Used to discover or communicate with supported glasses and streaming endpoints on your network.")
                PolicyRow("Saved destinations and defaults", "Stored on-device so you can reuse stream setups.")
                PolicyRow("Diagnostics logs and session history", "Stored on-device to help troubleshoot connection, streaming, and recording issues.")
            }

            PolicySection("How Data Is Used") {
                Text(
                    "Settings, diagnostics, and session history are stored locally on this device unless you explicitly export or share them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "The app does not require an account to use its core functionality.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PolicySection("Retention and Control") {
                Text(
                    "You can delete saved destinations, local recordings, diagnostics exports, and session history from within the app or by removing the app from your device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PolicySection("Contact") {
                Text(
                    "Questions about privacy or support can be sent to ${AppBrand.SUPPORT_EMAIL}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TermsSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Terms of Use", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            PolicySection("Use") {
                Text(
                    "${AppBrand.DISPLAY_NAME} is intended for creator, operator, and personal streaming workflows. You are responsible for using it lawfully and with appropriate notice or consent where required.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Do not use the app in ways that violate platform rules, local laws, privacy expectations, or the terms of any streaming or device provider you rely on.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PolicySection("Recordings and Streams") {
                Text(
                    "You control when streaming or recording begins. Check your destination, network, permissions, and device state before going live.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "You are responsible for the content you capture, record, store, export, or transmit with the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PolicySection("Availability") {
                Text(
                    "Features, limits, and supported transports may vary by app tier, device capability, and third-party service availability.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Streaming, recording, and export success can depend on external services, app permissions, network conditions, and connected hardware state.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            PolicySection("Contact") {
                Text(
                    "Questions about these terms can be sent to ${AppBrand.SUPPORT_EMAIL}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PolicySection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@Composable
private fun PolicyRow(title: String, detail: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
