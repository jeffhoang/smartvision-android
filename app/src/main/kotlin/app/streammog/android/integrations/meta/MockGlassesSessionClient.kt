package app.streammog.android.integrations.meta

import android.net.Uri
import app.streammog.android.domain.model.ConnectionState
import app.streammog.android.domain.model.DeviceStatus
import app.streammog.android.domain.model.PreviewSnapshot
import app.streammog.android.domain.model.SessionState
import app.streammog.android.domain.model.VideoFrameData
import app.streammog.android.domain.protocol.GlassesSessionClient
import app.streammog.android.shared.diagnostics.DiagnosticsEntry
import app.streammog.android.shared.diagnostics.DiagnosticsLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MockGlassesSessionClient(
    private val diagnosticsStore: DiagnosticsLogging,
) : GlassesSessionClient {
    override var statusDidChange: ((DeviceStatus) -> Unit)? = null
    override var previewDidUpdate: ((PreviewSnapshot) -> Unit)? = null
    override var fpsDidUpdate: ((Double) -> Unit)? = null
    override var videoBufferDidOutput: (suspend (VideoFrameData) -> Unit)? = null

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var frameJob: Job? = null
    private var frameIndex = 0

    override suspend fun connect() {
        statusDidChange?.invoke(DeviceStatus(
            connectionState = ConnectionState.CONNECTING,
            batteryLevel = null,
            sessionState = SessionState.IDLE,
            deviceName = "Searching for Meta glasses",
            lastError = null,
        ))
        diagnosticsStore.log("Attempting mock glasses connection", DiagnosticsEntry.Category.glasses)
        delay(1_000)
        statusDidChange?.invoke(DeviceStatus(
            connectionState = ConnectionState.CONNECTED,
            batteryLevel = 84,
            sessionState = SessionState.IDLE,
            deviceName = "Meta AI Glasses (Mock)",
            lastError = null,
        ))
        diagnosticsStore.log("Mock glasses connected", DiagnosticsEntry.Category.glasses)
    }

    override suspend fun handleDeepLink(uri: Uri) {
        diagnosticsStore.log("Mock client ignored deep link: $uri", DiagnosticsEntry.Category.glasses)
    }

    override suspend fun openDATGlassesAppUpdate() {
        diagnosticsStore.log("Mock client ignored DAT glasses app update action", DiagnosticsEntry.Category.glasses)
    }

    override suspend fun openFirmwareUpdate() {
        diagnosticsStore.log("Mock client ignored firmware update action", DiagnosticsEntry.Category.glasses)
    }

    override suspend fun resetRegistration() {
        stopSession()
        diagnosticsStore.log("Mock client reset registration state", DiagnosticsEntry.Category.glasses)
    }

    override suspend fun startSession() {
        diagnosticsStore.log("Starting mock media session", DiagnosticsEntry.Category.glasses)
        statusDidChange?.invoke(DeviceStatus(
            connectionState = ConnectionState.CONNECTED,
            batteryLevel = 82,
            sessionState = SessionState.RUNNING,
            deviceName = "Meta AI Glasses (Mock)",
            lastError = null,
        ))

        frameJob?.cancel()
        frameIndex = 0
        frameJob = scope.launch {
            while (isActive) {
                delay(42) // ~24 fps
                frameIndex++
                previewDidUpdate?.invoke(PreviewSnapshot(
                    timestampMs = System.currentTimeMillis(),
                    frameIndex = frameIndex,
                    label = "Mock frame $frameIndex",
                ))
                fpsDidUpdate?.invoke(24.0)
            }
        }
    }

    override suspend fun pauseSession() {
        diagnosticsStore.log("Pausing mock media session", DiagnosticsEntry.Category.glasses)
        frameJob?.cancel()
        frameJob = null
        statusDidChange?.invoke(DeviceStatus(
            connectionState = ConnectionState.CONNECTED,
            batteryLevel = 82,
            sessionState = SessionState.PAUSED,
            deviceName = "Meta AI Glasses (Mock)",
            lastError = null,
        ))
    }

    override suspend fun stopSession() {
        diagnosticsStore.log("Stopping mock media session", DiagnosticsEntry.Category.glasses)
        frameJob?.cancel()
        frameJob = null
        statusDidChange?.invoke(DeviceStatus(
            connectionState = ConnectionState.CONNECTED,
            batteryLevel = 81,
            sessionState = SessionState.STOPPED,
            deviceName = "Meta AI Glasses (Mock)",
            lastError = null,
        ))
    }
}
