package app.streammog.android.ui.control

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.BluetoothSearching
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.CastConnected
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CropFree
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.VideocamOff
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.streammog.android.app.AppBrand
import app.streammog.android.coordinator.StreamingCoordinator
import app.streammog.android.domain.model.ConnectionState
import app.streammog.android.domain.model.DeviceStatus
import app.streammog.android.domain.model.SessionState
import app.streammog.android.domain.model.StreamHealth
import app.streammog.android.domain.model.StreamPreset
import app.streammog.android.domain.model.StreamSessionDuration
import app.streammog.android.domain.model.StreamingState
import app.streammog.android.domain.model.VideoTransformSettings
import app.streammog.android.ui.theme.AvaLensError
import app.streammog.android.ui.theme.AvaLensTeal
import app.streammog.android.ui.theme.AvaLensWarning
import kotlin.math.roundToInt

// ── Main screen ──────────────────────────────────────────────────────────────

@Composable
fun ControlScreen(coordinator: StreamingCoordinator) {
    val streamingState by coordinator.streamingState.collectAsState()
    val deviceStatus by coordinator.deviceStatus.collectAsState()
    val streamHealth by coordinator.streamHealth.collectAsState()
    val streamDuration by coordinator.streamDuration.collectAsState()
    val previewSnapshot by coordinator.previewSnapshot.collectAsState()
    val previewVideoSize by coordinator.previewVideoSize.collectAsState()
    val selectedPreset by coordinator.selectedPreset.collectAsState()
    val systemWarning by coordinator.systemWarningMessage.collectAsState()
    val exportAlert by coordinator.exportAlertMessage.collectAsState()
    val testResult by coordinator.streamTestResultMessage.collectAsState()
    val previewSettings by coordinator.previewSettings.collectAsState()

    var showPreviewTools by remember { mutableStateOf(false) }
    var showStatusTray by remember { mutableStateOf(false) }
    var isPreviewExpanded by remember { mutableStateOf(false) }
    var cardSurface by remember { mutableStateOf<Surface?>(null) }

    // Inline status: error takes priority over warning
    val statusMessage = deviceStatus.lastError ?: systemWarning
    val statusIsError = deviceStatus.lastError != null

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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            PreviewCard(
                modifier = Modifier.weight(1f),
                deviceStatus = deviceStatus,
                previewSnapshot = previewSnapshot,
                videoSize = previewVideoSize,
                streamHealth = streamHealth,
                streamDuration = streamDuration,
                previewSettings = previewSettings,
                selectedPreset = selectedPreset,
                onExpand = { isPreviewExpanded = true },
                onSurfaceAvailable = { surface ->
                    cardSurface = surface
                    coordinator.setPreviewSurface(surface)
                },
                onSurfaceDestroyed = {
                    cardSurface = null
                    coordinator.setPreviewSurface(null)
                },
            )

            RunCard(
                streamingState = streamingState,
                deviceStatus = deviceStatus,
                selectedPreset = selectedPreset,
                statusMessage = statusMessage,
                statusIsError = statusIsError,
                onConnect = { coordinator.connectGlasses() },
                onStartSession = { coordinator.startSession() },
                onStartStreaming = { coordinator.startStreaming() },
                onStopStreaming = { coordinator.stopStreaming() },
                onStopAll = { coordinator.stopAll() },
                onResetSession = { coordinator.resetSession() },
                onLooks = { showPreviewTools = true },
                onStatus = { showStatusTray = true },
            )
        }

        AnimatedVisibility(
            visible = isPreviewExpanded,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            ExpandedPreviewOverlay(
                previewSnapshot = previewSnapshot,
                videoSize = previewVideoSize,
                streamHealth = streamHealth,
                streamDuration = streamDuration,
                previewSettings = previewSettings,
                selectedPreset = selectedPreset,
                onSurfaceAvailable = { surface -> coordinator.setPreviewSurface(surface) },
                onSurfaceDestroyed = { coordinator.setPreviewSurface(cardSurface) },
                onDismiss = { isPreviewExpanded = false },
                onLooks = { showPreviewTools = true },
                onStatus = { showStatusTray = true },
            )
        }
    }

    if (showPreviewTools) {
        PreviewToolsTray(
            settings = previewSettings,
            onDismiss = { showPreviewTools = false },
            onUpdate = { coordinator.updatePreviewSettings(it) },
        )
    }
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

// ── Preview card ─────────────────────────────────────────────────────────────

@Composable
private fun PreviewCard(
    deviceStatus: DeviceStatus,
    previewSnapshot: app.streammog.android.domain.model.PreviewSnapshot?,
    videoSize: Pair<Int, Int>?,
    streamHealth: StreamHealth,
    streamDuration: StreamSessionDuration,
    previewSettings: VideoTransformSettings,
    selectedPreset: StreamPreset,
    onExpand: () -> Unit,
    onSurfaceAvailable: (Surface) -> Unit,
    onSurfaceDestroyed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasSignal = previewSnapshot != null

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Outlined.CropFree,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Preview",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable(onClick = onExpand),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.OpenInFull,
                            contentDescription = "Expand",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    StatusPill(
                        title = if (hasSignal) "Signal" else "No signal",
                        color = if (hasSignal) AvaLensTeal else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Video area with HUD overlay — fills remaining card height
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.Black),
            ) {
                val surfaceModifier = videoSize
                    ?.takeIf { (w, h) -> w > 0 && h > 0 }
                    ?.let { (w, h) ->
                        val videoRatio = w.toFloat() / h
                        val areaRatio = constraints.maxWidth.toFloat() / constraints.maxHeight
                        if (areaRatio > videoRatio) {
                            Modifier.fillMaxHeight().aspectRatio(videoRatio).align(Alignment.Center)
                        } else {
                            Modifier.fillMaxWidth().aspectRatio(videoRatio).align(Alignment.Center)
                        }
                    } ?: Modifier.fillMaxSize()

                AndroidView(
                    factory = { ctx ->
                        SurfaceView(ctx).also { sv ->
                            sv.holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) =
                                    onSurfaceAvailable(holder.surface)
                                override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) = Unit
                                override fun surfaceDestroyed(holder: SurfaceHolder) = onSurfaceDestroyed()
                            })
                        }
                    },
                    modifier = surfaceModifier,
                )

                // Placeholder — shown until frames arrive
                if (!hasSignal) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            Icons.Outlined.VideocamOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = Color.White.copy(alpha = 0.28f),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "Waiting for camera frames",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.75f),
                        )
                        Text(
                            "Start Session to preview the DAT feed",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.45f),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }

                // HUD overlay (always shown)
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        HudChip("${streamHealth.fps.roundToInt()} fps")
                        HudChip(streamDuration.displayText)
                        val summary = previewSettings.summary
                        if (summary.isNotEmpty()) HudChip(summary)
                    }
                    HudInfoText(
                        "Output ${selectedPreset.targetBitrateKbps} kbps @ ${selectedPreset.targetFPS} fps"
                    )
                }
            }
        }
    }
}

@Composable
private fun HudChip(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White,
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun HudInfoText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = Color.White.copy(alpha = 0.85f),
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

// ── Expanded preview overlay ──────────────────────────────────────────────────

@Composable
private fun ExpandedPreviewOverlay(
    previewSnapshot: app.streammog.android.domain.model.PreviewSnapshot?,
    videoSize: Pair<Int, Int>?,
    streamHealth: StreamHealth,
    streamDuration: StreamSessionDuration,
    previewSettings: VideoTransformSettings,
    selectedPreset: StreamPreset,
    onSurfaceAvailable: (Surface) -> Unit,
    onSurfaceDestroyed: () -> Unit,
    onDismiss: () -> Unit,
    onLooks: () -> Unit,
    onStatus: () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val surfaceModifier = videoSize
            ?.takeIf { (w, h) -> w > 0 && h > 0 }
            ?.let { (w, h) ->
                val videoRatio = w.toFloat() / h
                val areaRatio = constraints.maxWidth.toFloat() / constraints.maxHeight
                if (areaRatio > videoRatio) {
                    Modifier.fillMaxHeight().aspectRatio(videoRatio).align(Alignment.Center)
                } else {
                    Modifier.fillMaxWidth().aspectRatio(videoRatio).align(Alignment.Center)
                }
            } ?: Modifier.fillMaxSize()

        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).also { sv ->
                    sv.holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) =
                            onSurfaceAvailable(holder.surface)
                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) = Unit
                        override fun surfaceDestroyed(holder: SurfaceHolder) = onSurfaceDestroyed()
                    })
                }
            },
            modifier = surfaceModifier,
        )

        // Placeholder — shown until frames arrive
        if (previewSnapshot == null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    Icons.Outlined.VideocamOff,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = Color.White.copy(alpha = 0.28f),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "Waiting for camera frames",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.75f),
                )
                Text(
                    "Start Session to preview the DAT feed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.45f),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        // HUD chips at top-left
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HudChip("${streamHealth.fps.roundToInt()} fps")
                HudChip(streamDuration.displayText)
                val summary = previewSettings.summary
                if (summary.isNotEmpty()) HudChip(summary)
            }
            HudInfoText(
                "Output ${selectedPreset.targetBitrateKbps} kbps @ ${selectedPreset.targetFPS} fps"
            )
        }

        // Top-right controls: close
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Close",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }

        // Bottom tool strip: Looks | Status
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OverlayIconButton(
                icon = Icons.Outlined.Tune,
                label = "Looks",
                onClick = onLooks,
            )
            OverlayIconButton(
                icon = Icons.Outlined.BarChart,
                label = "Status",
                onClick = onStatus,
            )
        }
    }
}

@Composable
private fun OverlayIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = 0.80f),
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

// ── Run card ─────────────────────────────────────────────────────────────────

@Composable
private fun RunCard(
    streamingState: StreamingState,
    deviceStatus: DeviceStatus,
    selectedPreset: StreamPreset,
    statusMessage: String?,
    statusIsError: Boolean,
    onConnect: () -> Unit,
    onStartSession: () -> Unit,
    onStartStreaming: () -> Unit,
    onStopStreaming: () -> Unit,
    onStopAll: () -> Unit,
    onResetSession: () -> Unit,
    onLooks: () -> Unit,
    onStatus: () -> Unit,
) {
    val isConnected = deviceStatus.connectionState == ConnectionState.CONNECTED
    val isStreaming = streamingState is StreamingState.Streaming
    val isRecovering = streamingState is StreamingState.Recovering
    val isBusy = streamingState is StreamingState.StartingStream ||
        streamingState is StreamingState.ConnectingGlasses
    val sessionRunning = deviceStatus.sessionState == SessionState.RUNNING ||
        deviceStatus.sessionState == SessionState.PREPARING
    val canStartStream = isConnected

    // "Ready in 1 tap" means connected AND session running AND not busy
    val canStartAction = canStartStream && !isStreaming && !isBusy &&
        !isRecovering && deviceStatus.sessionState == SessionState.RUNNING

    // Header pill
    val (pillText, pillColor) = when {
        canStartAction -> "Ready in 1 tap" to AvaLensTeal
        isStreaming -> (if (selectedPreset.transport == StreamPreset.Transport.LOCAL_RECORDING) "Recording now" else "Live now") to AvaLensTeal
        isRecovering || isBusy -> "Starting" to AvaLensWarning
        streamingState is StreamingState.Failed -> "Needs prep" to AvaLensError
        isConnected -> "Needs prep" to MaterialTheme.colorScheme.onSurfaceVariant
        else -> "Needs prep" to MaterialTheme.colorScheme.onSurfaceVariant
    }

    // Primary button content
    val primaryTitle: String
    val primarySubtitle: String
    val primaryColor: Color
    val primaryIcon: ImageVector
    val primaryEnabled: Boolean
    val primaryAction: () -> Unit

    when {
        isStreaming || isRecovering -> {
            primaryTitle = if (selectedPreset.transport == StreamPreset.Transport.LOCAL_RECORDING) "Stop Recording" else "Stop Stream"
            primarySubtitle = if (selectedPreset.transport == StreamPreset.Transport.LOCAL_RECORDING)
                "Recording is active on this phone." else "Live stream is active. Tap to end it cleanly."
            primaryColor = AvaLensError
            primaryIcon = Icons.Outlined.StopCircle
            primaryEnabled = true
            primaryAction = onStopStreaming
        }
        isBusy -> {
            primaryTitle = if (selectedPreset.transport == StreamPreset.Transport.LOCAL_RECORDING) "Starting Recording…" else "Starting Live…"
            primarySubtitle = if (selectedPreset.transport == StreamPreset.Transport.LOCAL_RECORDING)
                "Preparing local recording…" else "Handshaking with the live destination…"
            primaryColor = MaterialTheme.colorScheme.primary
            primaryIcon = Icons.Outlined.Cast
            primaryEnabled = false
            primaryAction = {}
        }
        else -> {
            primaryTitle = if (selectedPreset.transport == StreamPreset.Transport.LOCAL_RECORDING) "Start Recording" else "Go Live"
            primarySubtitle = when {
                !canStartStream -> "Connect glasses first. This becomes one tap once the session is ready."
                deviceStatus.sessionState != SessionState.RUNNING ->
                    "Start Session first. Once frames are flowing, the main button is your one-tap start."
                else -> if (selectedPreset.transport == StreamPreset.Transport.LOCAL_RECORDING)
                    "Session is ready. Tap to begin recording." else "Session is ready. Tap to go live."
            }
            primaryColor = if (selectedPreset.transport == StreamPreset.Transport.LOCAL_RECORDING)
                MaterialTheme.colorScheme.tertiary else AvaLensTeal
            primaryIcon = Icons.Outlined.Cast
            primaryEnabled = canStartAction
            primaryAction = onStartStreaming
        }
    }

    var showMoreMenu by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Header: "Run" label + status pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        if (isConnected) Icons.Outlined.CastConnected else Icons.AutoMirrored.Outlined.BluetoothSearching,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = pillColor,
                    )
                    Text(
                        "Run",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                StatusPill(title = pillText, color = pillColor)
            }

            // Inline status/warning (if any)
            AnimatedVisibility(visible = statusMessage != null, enter = fadeIn(), exit = fadeOut()) {
                if (statusMessage != null) {
                    InlineControlStatus(message = statusMessage, isError = statusIsError)
                }
            }

            // Primary run button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(primaryColor.copy(alpha = if (primaryEnabled) 1f else 0.5f))
                    .clickable(enabled = primaryEnabled, onClick = primaryAction)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        primaryIcon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            primaryTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                        Text(
                            primarySubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = if (primaryEnabled) 0.85f else 0.70f),
                        )
                    }
                }
            }

            // Icon button row: Connect | Session | Looks | Status | More
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OperatorIconButton(
                    icon = if (isConnected) Icons.Outlined.CastConnected else Icons.AutoMirrored.Outlined.BluetoothSearching,
                    label = "Connect",
                    isActive = canStartStream,
                    onClick = onConnect,
                    modifier = Modifier.weight(1f),
                )
                OperatorIconButton(
                    icon = Icons.Outlined.PlayArrow,
                    label = "Session",
                    isActive = sessionRunning,
                    onClick = onStartSession,
                    enabled = isConnected && !sessionRunning && !isBusy,
                    modifier = Modifier.weight(1f),
                )
                OperatorIconButton(
                    icon = Icons.Outlined.Tune,
                    label = "Looks",
                    onClick = onLooks,
                    modifier = Modifier.weight(1f),
                )
                OperatorIconButton(
                    icon = Icons.Outlined.BarChart,
                    label = "Status",
                    onClick = onStatus,
                    modifier = Modifier.weight(1f),
                )
                Box(modifier = Modifier.weight(1f)) {
                    OperatorIconButton(
                        icon = Icons.Outlined.MoreHoriz,
                        label = "More",
                        onClick = { showMoreMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Reset Session") },
                            onClick = { onResetSession(); showMoreMenu = false },
                        )
                        if (isStreaming || isRecovering || isBusy) {
                            DropdownMenuItem(
                                text = { Text("Stop All Activity", color = AvaLensError) },
                                onClick = { onStopAll(); showMoreMenu = false },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OperatorIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    enabled: Boolean = true,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isActive) AvaLensTeal.copy(alpha = 0.15f)
                    else MaterialTheme.colorScheme.surfaceVariant,
                )
                .then(
                    if (isActive) Modifier.border(1.dp, AvaLensTeal.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                    else Modifier
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = label,
                modifier = Modifier.size(20.dp),
                tint = when {
                    isActive -> AvaLensTeal
                    !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (isActive) AvaLensTeal else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun InlineControlStatus(message: String, isError: Boolean) {
    val color = if (isError) AvaLensError else AvaLensWarning
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            if (isError) Icons.Outlined.ErrorOutline else Icons.Outlined.Warning,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(14.dp).padding(top = 1.dp),
        )
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ── Status Tray ───────────────────────────────────────────────────────────────

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
        streamingState is StreamingState.Failed -> AvaLensError
        isStreaming -> if (selectedPreset.transport == StreamPreset.Transport.LOCAL_RECORDING)
            MaterialTheme.colorScheme.tertiary else AvaLensTeal
        streamingState is StreamingState.StartingStream || streamingState is StreamingState.Recovering ||
            deviceStatus.connectionState == ConnectionState.CONNECTING -> AvaLensWarning
        deviceStatus.connectionState == ConnectionState.CONNECTED -> AvaLensTeal
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val healthDetail = when (healthTitle) {
        "Reconnect" -> "RTMP dropped; ${AppBrand.DISPLAY_NAME} is retrying automatically."
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
        "Good", "Recovered" -> AvaLensTeal
        "Standby" -> MaterialTheme.colorScheme.onSurfaceVariant
        "No Frames" -> AvaLensError
        else -> AvaLensWarning
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
        !deviceStatus.lastError.isNullOrEmpty() -> AvaLensError
        !systemWarning.isNullOrEmpty() -> AvaLensWarning
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
        "iPhone" to phoneBatteryLabel,
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
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Sheet title
            Text(
                "Recording Status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "Live health and telemetry",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(12.dp))

            // Status headline + pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        "${streamingState.title} • $healthTitle",
                        style = MaterialTheme.typography.titleSmall,
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

            Spacer(Modifier.height(8.dp))

            // Health icon + detail
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    healthIcon,
                    contentDescription = null,
                    tint = healthColor,
                    modifier = Modifier.size(16.dp).padding(top = 1.dp),
                )
                Text(
                    healthDetail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Metrics grid — plain text, 2 columns, generous row spacing
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                metrics.chunked(2).forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        row.forEach { (title, value) ->
                            MetricCell(title = title, value = value, modifier = Modifier.weight(1f))
                        }
                        if (row.size < 2) Spacer(Modifier.weight(1f))
                    }
                }
            }

            // Focus message
            if (focusMessage != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    focusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = focusColor,
                )
            }

            // Diagnostics (RTMP only)
            if (showsConnectionTest) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Diagnostics",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Run a short RTMP check before going live. This verifies the destination and upload path, then stops automatically.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onRunTestStream(); onDismiss() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = canRunConnectionTest,
                ) {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp).padding(end = 6.dp),
                    )
                    Text("10s RTMP Test")
                }
            }
        }
    }
}

@Composable
private fun MetricCell(title: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

// ── Shared pill ───────────────────────────────────────────────────────────────

@Composable
private fun StatusPill(title: String, color: Color) {
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), MaterialTheme.shapes.extraLarge)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
    }
}

// ── Preview Tools Tray ────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewToolsTray(
    settings: VideoTransformSettings,
    onDismiss: () -> Unit,
    onUpdate: (VideoTransformSettings) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

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
        scrimColor = Color.Black.copy(alpha = 0.30f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Sheet title
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
                            Text("Reset", color = AvaLensError)
                        }
                    }
                }
                Text(
                    "Framing and look changes",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Zoom
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

            // Rotate
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

            // Mirror / Grid / Horizon
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

            // Look
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

            // Brightness
            SliderRow(
                label = "Bright",
                value = localBrightness,
                valueLabel = if (localBrightness > 0f) "+%.2f".format(localBrightness) else "%.2f".format(localBrightness),
                valueRange = VideoTransformSettings.brightnessRange.start.toFloat()..VideoTransformSettings.brightnessRange.endInclusive.toFloat(),
                onValueChange = { localBrightness = it },
                onValueChangeFinished = { onUpdate(settings.copy(brightness = localBrightness.toDouble())) },
            )

            // Contrast
            SliderRow(
                label = "Contrast",
                value = localContrast,
                valueLabel = "%.2f".format(localContrast),
                valueRange = VideoTransformSettings.contrastRange.start.toFloat()..VideoTransformSettings.contrastRange.endInclusive.toFloat(),
                onValueChange = { localContrast = it },
                onValueChangeFinished = { onUpdate(settings.copy(contrast = localContrast.toDouble())) },
            )

            // Sharpness
            SliderRow(
                label = "Sharp",
                value = localSharpness,
                valueLabel = "%.1f".format(localSharpness),
                valueRange = VideoTransformSettings.sharpnessRange.start.toFloat()..VideoTransformSettings.sharpnessRange.endInclusive.toFloat(),
                onValueChange = { localSharpness = it },
                onValueChangeFinished = { onUpdate(settings.copy(sharpness = localSharpness.toDouble())) },
            )

            // Apply to Stream — amber callout card
            Card(
                colors = CardDefaults.cardColors(containerColor = AvaLensWarning.copy(alpha = 0.10f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(AvaLensWarning.copy(alpha = 0.20f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Tune,
                            contentDescription = null,
                            tint = AvaLensWarning,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Apply to Stream",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "Preview-only right now. Turn this on to push framing and look changes live.",
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
