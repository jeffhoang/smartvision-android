package app.streammog.android.ui.control

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.BluetoothSearching
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.CastConnected
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import app.streammog.android.ui.theme.StreamMogError
import app.streammog.android.ui.theme.StreamMogTeal
import app.streammog.android.ui.theme.StreamMogWarning

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
}

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

            // Preview placeholder / frame info
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
