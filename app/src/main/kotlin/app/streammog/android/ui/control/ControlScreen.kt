package app.streammog.android.ui.control

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.BluetoothSearching
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.CastConnected
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.streammog.android.coordinator.StreamingCoordinator
import app.streammog.android.domain.model.ConnectionState
import app.streammog.android.domain.model.DeviceStatus
import app.streammog.android.domain.model.SessionState
import app.streammog.android.domain.model.StreamHealth
import app.streammog.android.domain.model.StreamPreset
import app.streammog.android.domain.model.StreamSessionDuration
import app.streammog.android.domain.model.StreamingState
import app.streammog.android.domain.model.VideoTransformSettings
import app.streammog.android.ui.theme.StreamMogError
import app.streammog.android.ui.theme.StreamMogTeal
import app.streammog.android.ui.theme.StreamMogWarning
import kotlin.math.roundToInt

@Composable
fun ControlScreen(coordinator: StreamingCoordinator) {
    val streamingState by coordinator.streamingState.collectAsState()
    val deviceStatus by coordinator.deviceStatus.collectAsState()
    val streamHealth by coordinator.streamHealth.collectAsState()
    val streamDuration by coordinator.streamDuration.collectAsState()
    val previewSnapshot by coordinator.previewSnapshot.collectAsState()
    val selectedPreset by coordinator.selectedPreset.collectAsState()
    val systemWarning by coordinator.systemWarningMessage.collectAsState()
    val exportAlert by coordinator.exportAlertMessage.collectAsState()
    val testResult by coordinator.streamTestResultMessage.collectAsState()
    val previewSettings by coordinator.previewSettings.collectAsState()

    var showPreviewTools by remember { mutableStateOf(false) }
    var showStatusTray by remember { mutableStateOf(false) }

    // Alerts
    if (exportAlert != null) {
        AlertDialog(
            onDismissRequest = { coordinator.dismissExportAlert() },
            title = { Text("Recording") },
            text = { Text(exportAlert!!) },
            confirmButton = { TextButton(onClick = { coordinator.dismissExportAlert() }) { Text("OK") } },
        )
    }
    if (testResult != null) {
        AlertDialog(
            onDismissRequest = { coordinator.dismissStreamTestResult() },
            title = { Text("Test Stream") },
            text = { Text(testResult!!) },
            confirmButton = { TextButton(onClick = { coordinator.dismissStreamTestResult() }) { Text("OK") } },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── State Banner ──
        StateBanner(
            streamingState = streamingState,
            streamDuration = streamDuration,
        )

        // ── Preview / Snapshot card ──
        PreviewCard(
            deviceStatus = deviceStatus,
            previewSnapshot = previewSnapshot,
            streamingState = streamingState,
        )

        // ── Tray toggle buttons ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = { showPreviewTools = true },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (!previewSettings.isIdentityTransform) StreamMogTeal
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Icon(Icons.Outlined.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Preview Tools", style = MaterialTheme.typography.labelMedium)
                if (!previewSettings.isIdentityTransform) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier.size(6.dp).clip(CircleShape).background(StreamMogTeal),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = { showStatusTray = true },
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            ) {
                Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Status", style = MaterialTheme.typography.labelMedium)
            }
        }

        // ── System warning ──
        AnimatedVisibility(visible = systemWarning != null, enter = fadeIn(), exit = fadeOut()) {
            if (systemWarning != null) {
                WarningBanner(message = systemWarning!!)
            }
        }

        // ── Last error ──
        if (deviceStatus.lastError != null) {
            ErrorBanner(message = deviceStatus.lastError!!)
        }

        // ── Stream health (visible only while streaming/recovering) ──
        AnimatedVisibility(
            visible = streamingState is StreamingState.Streaming || streamingState is StreamingState.Recovering,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            StreamHealthCard(health = streamHealth, preset = selectedPreset)
        }

        // ── Action buttons ──
        ActionButtonsCard(
            streamingState = streamingState,
            deviceStatus = deviceStatus,
            preset = selectedPreset,
            onConnect = { coordinator.connectGlasses() },
            onStartSession = { coordinator.startSession() },
            onStartStreaming = { coordinator.startStreaming() },
            onStopStreaming = { coordinator.stopStreaming() },
            onStopAll = { coordinator.stopAll() },
            onResetSession = { coordinator.resetSession() },
            onRunTestStream = { coordinator.runTestStream() },
        )

        Spacer(Modifier.height(16.dp))
    }

    // ── Preview Tools tray ──
    if (showPreviewTools) {
        PreviewToolsTray(
            settings = previewSettings,
            onDismiss = { showPreviewTools = false },
            onUpdate = { coordinator.updatePreviewSettings(it) },
        )
    }

    // ── Status tray ──
    if (showStatusTray) {
        StatusTray(
            streamingState = streamingState,
            deviceStatus = deviceStatus,
            streamHealth = streamHealth,
            streamDuration = streamDuration,
            previewSnapshot = previewSnapshot,
            selectedPreset = selectedPreset,
            systemWarning = systemWarning,
            onRunTestStream = { coordinator.runTestStream() },
            onDismiss = { showStatusTray = false },
        )
    }
}

// ── Status Tray ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusTray(
    streamingState: StreamingState,
    deviceStatus: DeviceStatus,
    streamHealth: StreamHealth,
    streamDuration: StreamSessionDuration,
    previewSnapshot: app.streammog.android.domain.model.PreviewSnapshot?,
    selectedPreset: StreamPreset,
    systemWarning: String?,
    onRunTestStream: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isStreaming = streamingState is StreamingState.Streaming

    // ── Derived strings (mirror iOS RunSummaryCard logic) ──
    val healthTitle = when {
        streamingState is StreamingState.Recovering -> "Reconnect"
        !isStreaming -> "Standby"
        previewSnapshot == null || streamHealth.fps <= 0 -> "No Frames"
        selectedPreset.audioSource != StreamPreset.AudioSource.NONE && streamHealth.audioSampleCount == 0 -> "Audio Missing"
        selectedPreset.audioSource != StreamPreset.AudioSource.NONE && streamHealth.isAudioSilent -> "Silent Mic"
        selectedPreset.audioSource != StreamPreset.AudioSource.NONE && streamHealth.audioDesyncMs > 750 -> "Audio Delay"
        selectedPreset.transport == StreamPreset.Transport.RTMP && streamHealth.uploadBitrateKbps > 0 &&
            streamHealth.uploadBitrateKbps < maxOf(800, selectedPreset.targetBitrateKbps / 3) -> "Low Upload"
        selectedPreset.transport == StreamPreset.Transport.RTMP && streamHealth.averageUploadBitrateKbps > 0 &&
            streamHealth.averageUploadBitrateKbps < maxOf(1200, selectedPreset.targetBitrateKbps / 2) -> "Upload Unstable"
        streamHealth.reconnectCount > 0 -> "Recovered"
        else -> "Good"
    }

    val statusTitle = when {
        streamingState is StreamingState.Failed -> "Attention"
        isStreaming -> if (selectedPreset.transport == StreamPreset.Transport.LOCAL_RECORDING) "Recording" else "Live"
        streamingState is StreamingState.StartingStream || streamingState is StreamingState.Recovering ||
            deviceStatus.sessionState == SessionState.PREPARING -> "Starting"
        else -> deviceStatus.connectionState.displayName
    }

    val statusColor = when {
        streamingState is StreamingState.Failed -> StreamMogError
        isStreaming -> if (selectedPreset.transport == StreamPreset.Transport.LOCAL_RECORDING)
            MaterialTheme.colorScheme.tertiary else StreamMogTeal
        streamingState is StreamingState.StartingStream || streamingState is StreamingState.Recovering ||
            deviceStatus.connectionState == ConnectionState.CONNECTING -> StreamMogWarning
        deviceStatus.connectionState == ConnectionState.CONNECTED -> StreamMogTeal
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val healthDetail = when (healthTitle) {
        "Reconnect" -> "RTMP dropped; StreamMog is retrying automatically."
        "No Frames" -> "Camera session is active, but no video frames are reaching the stream."
        "Audio Missing" -> "Video is flowing, but no microphone samples have reached the publisher."
        "Silent Mic" -> "Audio samples are present, but the microphone level is near silence."
        "Audio Delay" -> "Video is flowing, but audio timing is drifting by ${streamHealth.audioDesyncMs} ms."
        "Low Upload" -> "${streamHealth.uploadBitrateKbps} kbps outbound is far below the target ${selectedPreset.targetBitrateKbps} kbps."
        "Upload Unstable" -> {
            val trend = if (streamHealth.uploadTrendKbps > 0) "+${streamHealth.uploadTrendKbps}" else "${streamHealth.uploadTrendKbps}"
            "Average upload is ${streamHealth.averageUploadBitrateKbps} kbps, trend $trend kbps."
        }
        "Recovered" -> "Stream is flowing after ${streamHealth.reconnectCount} reconnect attempt(s)."
        "Good" -> "${streamHealth.fps.roundToInt()} fps, ${streamHealth.uploadBitrateKbps} kbps now, ${streamHealth.averageUploadBitrateKbps} kbps avg."
        else -> "Connect glasses, start a session, then Go Live or run Test Stream."
    }

    val healthIcon = when (healthTitle) {
        "Good", "Recovered" -> Icons.Outlined.CheckCircle
        "Standby" -> Icons.Outlined.RadioButtonUnchecked
        "No Frames" -> Icons.Outlined.ErrorOutline
        else -> Icons.Outlined.Warning
    }

    val healthColor = when (healthTitle) {
        "Good", "Recovered" -> StreamMogTeal
        "Standby" -> MaterialTheme.colorScheme.onSurfaceVariant
        "No Frames" -> StreamMogError
        else -> StreamMogWarning
    }

    val audioLabel = when {
        selectedPreset.audioSource == StreamPreset.AudioSource.NONE -> "Off"
        selectedPreset.isAudioMuted -> "Muted"
        streamHealth.isAudioSilent -> "Silent"
        else -> "${selectedPreset.audioSource.displayName} ${(streamHealth.audioLevel.coerceIn(0f, 1f) * 100).toInt()}%"
    }

    val glassesBatteryLabel = when {
        deviceStatus.batteryLevel != null -> "${deviceStatus.batteryLevel}%"
        deviceStatus.connectionState == ConnectionState.CONNECTED -> deviceStatus.deviceName
        else -> "Not connected"
    }

    val context = LocalContext.current
    val phoneBatteryLabel = remember {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        if (level >= 0) "${((level.toFloat() / scale) * 100).roundToInt()}%" else "Unknown"
    }

    val focusMessage: String? = when {
        !deviceStatus.lastError.isNullOrEmpty() -> deviceStatus.lastError
        !systemWarning.isNullOrEmpty() -> systemWarning
        streamHealth.reconnectCount > 0 -> "Recovered after reconnect attempts. Watch the next minute closely."
        previewSnapshot == null -> "Start Session and wait for frames."
        else -> null
    }

    val focusColor = when {
        !deviceStatus.lastError.isNullOrEmpty() -> StreamMogError
        !systemWarning.isNullOrEmpty() -> StreamMogWarning
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val mode = if (selectedPreset.transport == StreamPreset.Transport.LOCAL_RECORDING) "local recording" else "RTMP"
    val summaryLine = "${deviceStatus.deviceName} | ${deviceStatus.sessionState.displayName} | $mode"

    val metrics = listOf(
        "Duration" to streamDuration.displayText,
        "FPS" to "${streamHealth.fps.roundToInt()}",
        "Upload" to "${streamHealth.uploadBitrateKbps} kbps",
        "Target" to "${selectedPreset.targetBitrateKbps} kbps",
        "Audio" to audioLabel,
        "Reconnects" to "${streamHealth.reconnectCount}",
        "Glasses" to glassesBatteryLabel,
        "Phone" to phoneBatteryLabel,
    )

    val showsConnectionTest = selectedPreset.transport == StreamPreset.Transport.RTMP
    val canRunConnectionTest = showsConnectionTest &&
        deviceStatus.connectionState == ConnectionState.CONNECTED &&
        !isStreaming &&
        streamingState !is StreamingState.StartingStream &&
        streamingState !is StreamingState.Recovering &&
        deviceStatus.sessionState != SessionState.PREPARING

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
            // Header: headline + status pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        "${streamingState.title} • $healthTitle",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        summaryLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                StatusPill(title = statusTitle, color = statusColor)
            }

            // Health row
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    healthIcon,
                    contentDescription = null,
                    tint = healthColor,
                    modifier = Modifier.size(16.dp).padding(top = 2.dp),
                )
                Text(
                    healthDetail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            // Metrics grid (2 columns)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                metrics.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { (title, value) ->
                            MetricTile(title = title, value = value, modifier = Modifier.weight(1f))
                        }
                        if (row.size < 2) Spacer(Modifier.weight(1f))
                    }
                }
            }

            // Focus message
            if (focusMessage != null) {
                Text(
                    focusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = focusColor,
                )
            }

            // Diagnostics section (RTMP only)
            if (showsConnectionTest) {
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Diagnostics",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Run a short RTMP check before going live. Verifies the destination and upload path, then stops automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = {
                            onRunTestStream()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = canRunConnectionTest,
                    ) {
                        Icon(Icons.Outlined.BugReport, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                        Text("10s RTMP Test")
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(title: String, color: Color) {
    Text(
        title,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), MaterialTheme.shapes.extraLarge)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun MetricTile(title: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ── Preview Tools Tray ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewToolsTray(
    settings: VideoTransformSettings,
    onDismiss: () -> Unit,
    onUpdate: (VideoTransformSettings) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Local state for sliders so dragging stays smooth
    var localBrightness by remember { mutableFloatStateOf(settings.brightness.toFloat()) }
    var localContrast by remember { mutableFloatStateOf(settings.contrast.toFloat()) }
    var localSharpness by remember { mutableFloatStateOf(settings.sharpness.toFloat()) }

    LaunchedEffect(settings.brightness) { localBrightness = settings.brightness.toFloat() }
    LaunchedEffect(settings.contrast) { localContrast = settings.contrast.toFloat() }
    LaunchedEffect(settings.sharpness) { localSharpness = settings.sharpness.toFloat() }

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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Preview Tools",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (!settings.isIdentityTransform) {
                    TextButton(onClick = { onUpdate(VideoTransformSettings.identity) }) {
                        Text("Reset", color = StreamMogError)
                    }
                }
            }

            // ── Zoom ──
            TrayRow("Zoom") {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    VideoTransformSettings.zoomOptions.forEachIndexed { i, zoom ->
                        SegmentedButton(
                            selected = settings.zoom == zoom,
                            onClick = { onUpdate(settings.copy(zoom = zoom)) },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = VideoTransformSettings.zoomOptions.size),
                            icon = {},
                            label = { Text(if (zoom == 1.0) "Fit" else "%.1f×".format(zoom)) },
                        )
                    }
                }
            }

            // ── Rotate ──
            TrayRow("Rotate") {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    VideoTransformSettings.rotationOptions.forEachIndexed { i, deg ->
                        SegmentedButton(
                            selected = settings.rotationDegrees == deg,
                            onClick = { onUpdate(settings.copy(rotationDegrees = deg)) },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = VideoTransformSettings.rotationOptions.size),
                            icon = {},
                            label = { Text("${deg}°") },
                        )
                    }
                }
            }

            // ── Mirror / Grid / Horizon ──
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = settings.isMirrored,
                    onClick = { onUpdate(settings.copy(isMirrored = !settings.isMirrored)) },
                    label = { Text("Mirror") },
                    leadingIcon = if (settings.isMirrored) {
                        { Icon(Icons.Outlined.Check, null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    } else null,
                )
                FilterChip(
                    selected = settings.showsGrid,
                    onClick = { onUpdate(settings.copy(showsGrid = !settings.showsGrid)) },
                    label = { Text("Grid") },
                    leadingIcon = if (settings.showsGrid) {
                        { Icon(Icons.Outlined.Check, null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    } else null,
                )
                FilterChip(
                    selected = settings.showsHorizon,
                    onClick = { onUpdate(settings.copy(showsHorizon = !settings.showsHorizon)) },
                    label = { Text("Horizon") },
                    leadingIcon = if (settings.showsHorizon) {
                        { Icon(Icons.Outlined.Check, null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    } else null,
                )
            }

            // ── Look (color enhancement) ──
            TrayRow("Look") {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    VideoTransformSettings.ColorEnhancementPreset.entries.forEachIndexed { i, preset ->
                        SegmentedButton(
                            selected = settings.colorEnhancement == preset,
                            onClick = { onUpdate(settings.copy(colorEnhancement = preset)) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = i,
                                count = VideoTransformSettings.ColorEnhancementPreset.entries.size,
                            ),
                            icon = {},
                            label = { Text(preset.displayName) },
                        )
                    }
                }
            }

            // ── Brightness ──
            val brightnessRange = VideoTransformSettings.brightnessRange
            SliderRow(
                label = "Bright",
                value = localBrightness,
                valueLabel = if (localBrightness > 0f) "+%.2f".format(localBrightness) else "%.2f".format(localBrightness),
                valueRange = brightnessRange.start.toFloat()..brightnessRange.endInclusive.toFloat(),
                onValueChange = { localBrightness = it },
                onValueChangeFinished = { onUpdate(settings.copy(brightness = localBrightness.toDouble())) },
            )

            // ── Contrast ──
            val contrastRange = VideoTransformSettings.contrastRange
            SliderRow(
                label = "Contrast",
                value = localContrast,
                valueLabel = "%.2f".format(localContrast),
                valueRange = contrastRange.start.toFloat()..contrastRange.endInclusive.toFloat(),
                onValueChange = { localContrast = it },
                onValueChangeFinished = { onUpdate(settings.copy(contrast = localContrast.toDouble())) },
            )

            // ── Sharpness ──
            val sharpnessRange = VideoTransformSettings.sharpnessRange
            SliderRow(
                label = "Sharp",
                value = localSharpness,
                valueLabel = "%.1f".format(localSharpness),
                valueRange = sharpnessRange.start.toFloat()..sharpnessRange.endInclusive.toFloat(),
                onValueChange = { localSharpness = it },
                onValueChangeFinished = { onUpdate(settings.copy(sharpness = localSharpness.toDouble())) },
            )

            // ── Apply to stream ──
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Apply to Stream",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        "Include transforms in the broadcast",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = settings.applyToStream,
                    onCheckedChange = { onUpdate(settings.copy(applyToStream = it)) },
                )
            }
        }
    }
}

@Composable
private fun TrayRow(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueLabel: String,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                valueLabel,
                style = MaterialTheme.typography.labelMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ── Main screen composables ──────────────────────────────────────────────────

@Composable
private fun StateBanner(streamingState: StreamingState, streamDuration: StreamSessionDuration) {
    val isStreaming = streamingState is StreamingState.Streaming
    val bgColor by animateColorAsState(
        targetValue = when (streamingState) {
            is StreamingState.Streaming -> StreamMogTeal.copy(alpha = 0.15f)
            is StreamingState.Failed -> StreamMogError.copy(alpha = 0.15f)
            is StreamingState.Recovering -> StreamMogWarning.copy(alpha = 0.12f)
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        animationSpec = tween(400),
        label = "bannerBg",
    )
    val textColor by animateColorAsState(
        targetValue = when (streamingState) {
            is StreamingState.Streaming -> StreamMogTeal
            is StreamingState.Failed -> StreamMogError
            is StreamingState.Recovering -> StreamMogWarning
            else -> MaterialTheme.colorScheme.onSurface
        },
        animationSpec = tween(400),
        label = "bannerText",
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = bgColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when (streamingState) {
                    is StreamingState.ConnectingGlasses,
                    is StreamingState.StartingStream -> CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = textColor,
                    )
                    is StreamingState.Recovering -> CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = StreamMogWarning,
                    )
                    is StreamingState.Streaming -> Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(StreamMogTeal),
                    )
                    else -> {}
                }
                Text(
                    streamingState.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                )
            }
            if (isStreaming) {
                Text(
                    "● LIVE  ${streamDuration.displayText}",
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = FontFamily.Monospace,
                    color = StreamMogTeal,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        if (streamingState is StreamingState.StartingStream || streamingState is StreamingState.ConnectingGlasses) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )
        }
    }
}

@Composable
private fun PreviewCard(
    deviceStatus: DeviceStatus,
    previewSnapshot: app.streammog.android.domain.model.PreviewSnapshot?,
    streamingState: StreamingState,
) {
    val connectionIcon = when (deviceStatus.connectionState) {
        ConnectionState.CONNECTED -> Icons.Outlined.CastConnected
        ConnectionState.CONNECTING -> Icons.AutoMirrored.Outlined.BluetoothSearching
        ConnectionState.DISCONNECTED -> Icons.Outlined.LinkOff
        else -> Icons.Outlined.PhoneAndroid
    }
    val connectionColor = when (deviceStatus.connectionState) {
        ConnectionState.CONNECTED -> StreamMogTeal
        ConnectionState.CONNECTING -> StreamMogWarning
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(connectionIcon, contentDescription = null, tint = connectionColor, modifier = Modifier.size(18.dp))
                    Text(
                        deviceStatus.deviceName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (deviceStatus.batteryLevel != null) {
                    Text(
                        "⚡ ${deviceStatus.batteryLevel}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (deviceStatus.batteryLevel <= 20) StreamMogError else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            DeviceStatusRow(status = deviceStatus)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (previewSnapshot != null) 100.dp else 56.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (previewSnapshot != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            Icons.Outlined.Cast,
                            contentDescription = null,
                            tint = StreamMogTeal,
                            modifier = Modifier.size(24.dp),
                        )
                        Text(
                            "Frame ${previewSnapshot.frameIndex}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            previewSnapshot.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Text(
                        "Camera preview unavailable",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceStatusRow(status: DeviceStatus) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        StatusChip(
            label = "Connection",
            value = status.connectionState.displayName,
            valueColor = when (status.connectionState) {
                ConnectionState.CONNECTED -> StreamMogTeal
                ConnectionState.CONNECTING -> StreamMogWarning
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        StatusChip(
            label = "Session",
            value = status.sessionState.displayName,
            valueColor = when (status.sessionState) {
                SessionState.RUNNING -> StreamMogTeal
                SessionState.FAILED -> StreamMogError
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun StatusChip(label: String, value: String, valueColor: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = valueColor)
    }
}

@Composable
private fun StreamHealthCard(health: StreamHealth, preset: StreamPreset) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Stream Health",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                HealthMetric(label = "Upload", value = "${health.uploadBitrateKbps} kbps")
                HealthMetric(label = "FPS", value = "%.1f".format(health.fps))
                HealthMetric(label = "Reconnects", value = "${health.reconnectCount}")
                HealthMetric(label = "Dropped", value = "${health.droppedFrames}")
            }
            if (preset.targetBitrateKbps > 0) {
                val fraction = (health.uploadBitrateKbps.toFloat() / preset.targetBitrateKbps).coerceIn(0f, 1f)
                val barColor = when {
                    fraction >= 0.8f -> StreamMogTeal
                    fraction >= 0.5f -> StreamMogWarning
                    else -> StreamMogError
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Bitrate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${(fraction * 100).toInt()}% of ${preset.targetBitrateKbps} kbps",
                            style = MaterialTheme.typography.labelSmall,
                            color = barColor,
                        )
                    }
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth(),
                        color = barColor,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun HealthMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ActionButtonsCard(
    streamingState: StreamingState,
    deviceStatus: DeviceStatus,
    preset: StreamPreset,
    onConnect: () -> Unit,
    onStartSession: () -> Unit,
    onStartStreaming: () -> Unit,
    onStopStreaming: () -> Unit,
    onStopAll: () -> Unit,
    onResetSession: () -> Unit,
    onRunTestStream: () -> Unit,
) {
    val isConnected = deviceStatus.connectionState == ConnectionState.CONNECTED
    val isStreaming = streamingState is StreamingState.Streaming
    val isBusy = streamingState is StreamingState.StartingStream ||
        streamingState is StreamingState.ConnectingGlasses
    val isActive = isStreaming || streamingState is StreamingState.Recovering || isBusy

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Controls",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Primary action
            when {
                streamingState is StreamingState.Streaming || streamingState is StreamingState.Recovering -> {
                    Button(
                        onClick = onStopStreaming,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = StreamMogError),
                    ) {
                        Icon(Icons.Outlined.StopCircle, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                        Text("Stop Stream", fontWeight = FontWeight.SemiBold)
                    }
                }
                streamingState is StreamingState.Idle || streamingState is StreamingState.Failed -> {
                    Button(
                        onClick = onConnect,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy,
                    ) {
                        Icon(Icons.AutoMirrored.Outlined.BluetoothSearching, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                        Text("Connect Glasses")
                    }
                }
                isConnected -> {
                    Button(
                        onClick = onStartStreaming,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy,
                    ) {
                        Icon(Icons.Outlined.Cast, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                        Text(
                            when (preset.transport) {
                                StreamPreset.Transport.RTMP -> "Go Live"
                                StreamPreset.Transport.LOCAL_RECORDING -> "Start Recording"
                                StreamPreset.Transport.SRT -> "Go Live (SRT)"
                            },
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // Secondary row: Start Session + Test Stream
            if (isConnected && !isStreaming && !isBusy) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = onStartSession,
                        modifier = Modifier.weight(1f),
                        enabled = deviceStatus.sessionState != SessionState.RUNNING,
                    ) {
                        Text("Start Session", style = MaterialTheme.typography.labelMedium)
                    }
                    if (preset.transport == StreamPreset.Transport.RTMP) {
                        FilledTonalButton(
                            onClick = onRunTestStream,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Outlined.BugReport, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                            Text("Test", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Tertiary row: Reset Session + Stop All
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onResetSession,
                    modifier = Modifier.weight(1f),
                    enabled = isConnected || isActive,
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text("Reset Session", style = MaterialTheme.typography.labelMedium)
                }
                if (isActive) {
                    OutlinedButton(
                        onClick = onStopAll,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = StreamMogError),
                    ) {
                        Text("Stop All", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun WarningBanner(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = StreamMogWarning.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Outlined.Warning, contentDescription = null, tint = StreamMogWarning, modifier = Modifier.size(18.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun ErrorBanner(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = StreamMogError.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = StreamMogError, modifier = Modifier.size(18.dp))
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Start)
        }
    }
}
