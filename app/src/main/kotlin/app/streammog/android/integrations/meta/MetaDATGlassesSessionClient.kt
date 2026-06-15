package app.streammog.android.integrations.meta

import android.app.Activity
import android.content.Context
import android.net.Uri
import app.streammog.android.domain.model.ConnectionState
import app.streammog.android.domain.model.DeviceStatus
import app.streammog.android.domain.model.PreviewSnapshot
import app.streammog.android.domain.model.SessionState
import app.streammog.android.domain.model.VideoFrameData
import app.streammog.android.domain.protocol.GlassesSessionClient
import app.streammog.android.shared.diagnostics.DiagnosticsEntry
import app.streammog.android.shared.diagnostics.DiagnosticsLogging
import com.meta.wearable.dat.camera.Stream
import com.meta.wearable.dat.camera.addStream
import com.meta.wearable.dat.camera.removeStream
import com.meta.wearable.dat.camera.types.StreamConfiguration
import com.meta.wearable.dat.camera.types.StreamError
import com.meta.wearable.dat.camera.types.StreamState
import com.meta.wearable.dat.camera.types.VideoQuality
import com.meta.wearable.dat.core.Wearables
import com.meta.wearable.dat.core.selectors.AutoDeviceSelector
import com.meta.wearable.dat.core.selectors.SpecificDeviceSelector
import com.meta.wearable.dat.core.session.DeviceSession
import com.meta.wearable.dat.core.session.DeviceSessionState
import com.meta.wearable.dat.core.types.DatException
import com.meta.wearable.dat.core.types.DeviceCompatibility
import com.meta.wearable.dat.core.types.DeviceIdentifier
import com.meta.wearable.dat.core.types.DeviceSessionError
import com.meta.wearable.dat.core.types.LinkState
import com.meta.wearable.dat.core.types.Permission
import com.meta.wearable.dat.core.types.RegistrationState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MetaDATGlassesSessionClient(
    private val diagnosticsStore: DiagnosticsLogging,
    private val activityProvider: () -> Activity?,
) : GlassesSessionClient {

    override var statusDidChange: ((DeviceStatus) -> Unit)? = null
    override var previewDidUpdate: ((PreviewSnapshot) -> Unit)? = null
    override var fpsDidUpdate: ((Double) -> Unit)? = null
    override var videoBufferDidOutput: (suspend (VideoFrameData) -> Unit)? = null

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())

    private var registrationObserverJob: Job? = null
    private var deviceObserverJob: Job? = null
    private var registrationWatchdogJob: Job? = null
    private var streamListenerJob: Job? = null

    private var activeStream: Stream? = null
    private var deviceSession: DeviceSession? = null

    private var totalFrameCounter = 0
    private var fpsWindowFrameCounter = 0
    private var lastFpsWindowStartMs = System.currentTimeMillis()
    private var lastPreviewUpdateMs = 0L

    private var lastReportedStatus = DeviceStatus.initial
    private var registrationAttemptId = 0
    private var lastMetaCallbackAtMs: Long? = null

    init {
        // SDK is configured via AndroidManifest.xml meta-data; call Wearables.initialize(context)
        // before using REAL mode (see AppEnvironment.bootstrap).
        observeRegistration()
        observeDevices()
        publishStatus()
    }

    // region GlassesSessionClient

    override suspend fun connect() {
        logWearablesState("Before registration")

        if (Wearables.registrationState.value == RegistrationState.REGISTERED) {
            diagnosticsStore.log("Wearables already registered", DiagnosticsEntry.Category.glasses)
            publishStatus()
            return
        }

        diagnosticsStore.log("Starting Meta DAT registration flow", DiagnosticsEntry.Category.glasses)
        publishConnectingStatus()
        registrationAttemptId++
        val attemptId = registrationAttemptId
        lastMetaCallbackAtMs = null
        registrationWatchdogJob?.cancel()

        try {
            startRegistrationWithRecovery()
            diagnosticsStore.log("Meta DAT registration handoff completed", DiagnosticsEntry.Category.glasses)
            scheduleRegistrationWatchdog(attemptId)
        } catch (e: Exception) {
            val message = "Registration failed: ${diagnosticDescription(e)}"
            publishFailure(message)
            throw e
        }
    }

    // Android handles the deep link automatically via Intent delivery to onNewIntent.
    // The SDK detects the registration callback through its own broadcast receivers.
    override suspend fun handleDeepLink(uri: Uri) {
        val hasMetaAction = uri.getQueryParameter("metaWearablesAction") != null
        if (!hasMetaAction) {
            diagnosticsStore.log("Ignored non-Meta DAT deep link", DiagnosticsEntry.Category.glasses)
            return
        }
        lastMetaCallbackAtMs = System.currentTimeMillis()
        registrationWatchdogJob?.cancel()
        diagnosticsStore.log("Received Meta DAT deep link callback", DiagnosticsEntry.Category.glasses)
        logWearablesState("After Meta callback")
    }

    override suspend fun openDATGlassesAppUpdate() {
        try {
            val activity = activityProvider() ?: return
            Wearables.openDATGlassesAppUpdate(activity)
            diagnosticsStore.log("Opened Meta AI DAT glasses app update screen", DiagnosticsEntry.Category.glasses)
        } catch (e: Exception) {
            diagnosticsStore.log("Failed to open DAT glasses app update: ${diagnosticDescription(e)}", DiagnosticsEntry.Category.error)
        }
    }

    override suspend fun openFirmwareUpdate() {
        try {
            val activity = activityProvider() ?: return
            Wearables.openFirmwareUpdate(activity)
            diagnosticsStore.log("Opened Meta AI firmware update screen", DiagnosticsEntry.Category.glasses)
        } catch (e: Exception) {
            diagnosticsStore.log("Failed to open firmware update: ${diagnosticDescription(e)}", DiagnosticsEntry.Category.error)
        }
    }

    override suspend fun resetRegistration() {
        diagnosticsStore.log("Resetting Meta DAT registration", DiagnosticsEntry.Category.glasses)
        stopSession()
        registrationWatchdogJob?.cancel()
        registrationAttemptId = 0
        lastMetaCallbackAtMs = null
        try {
            val activity = activityProvider() ?: return
            Wearables.startUnregistration(activity)
            diagnosticsStore.log("Meta DAT unregistration handoff completed", DiagnosticsEntry.Category.glasses)
            publishStatus()
        } catch (e: Exception) {
            diagnosticsStore.log("Meta DAT unregistration failed: ${diagnosticDescription(e)}", DiagnosticsEntry.Category.error)
            publishStatus()
        }
    }

    override suspend fun startSession() {
        if (activeStream != null) {
            diagnosticsStore.log("Meta DAT camera stream already active", DiagnosticsEntry.Category.glasses)
            publishRunningStatus()
            return
        }

        logWearablesState("Before camera session")

        try {
            Wearables.checkPermissionStatus(Permission.CAMERA).getOrThrow()
        } catch (e: Exception) {
            val message = "Camera permission not granted: ${e.message}"
            publishFailure(message)
            throw e
        }

        val session = try {
            startDeviceSessionWithRecovery()
        } catch (e: Exception) {
            val message = "Device session failed: ${diagnosticDescription(e)}"
            publishFailure(message)
            throw e
        }

        if (session.state.value != DeviceSessionState.STARTED) {
            val message = "Device session did not reach STARTED state."
            publishFailure(message)
            throw Exception(message)
        }

        val config = StreamConfiguration(
            videoQuality = VideoQuality.HIGH,
            frameRate = 30,
        )
        diagnosticsStore.log("Requesting Meta DAT camera stream: quality=HIGH, fps=30", DiagnosticsEntry.Category.glasses)

        val newStream = try {
            session.addStream(config).getOrThrow()
        } catch (e: Exception) {
            val message = "Camera stream setup failed: ${diagnosticDescription(e)}"
            publishFailure(message)
            throw e
        }

        activeStream = newStream
        totalFrameCounter = 0
        fpsWindowFrameCounter = 0
        lastFpsWindowStartMs = System.currentTimeMillis()
        lastPreviewUpdateMs = 0L
        publishRunningStatus()

        attachStreamListeners(newStream)
        diagnosticsStore.log("Starting Meta DAT camera stream", DiagnosticsEntry.Category.glasses)
        newStream.start()
    }

    override suspend fun pauseSession() {
        val stream = activeStream ?: return
        activeStream = null
        streamListenerJob?.cancel()
        deviceSession?.removeStream()
        lastReportedStatus = lastReportedStatus.copy(sessionState = SessionState.PAUSED, lastError = null)
        statusDidChange?.invoke(lastReportedStatus)
        diagnosticsStore.log("Paused Meta DAT camera stream", DiagnosticsEntry.Category.glasses)
    }

    override suspend fun stopSession() {
        activeStream?.let {
            activeStream = null
            streamListenerJob?.cancel()
            deviceSession?.removeStream()
        }
        deviceSession?.stop()
        deviceSession = null
        lastReportedStatus = lastReportedStatus.copy(sessionState = SessionState.STOPPED, lastError = null)
        statusDidChange?.invoke(lastReportedStatus)
        diagnosticsStore.log("Stopped Meta DAT device session", DiagnosticsEntry.Category.glasses)
    }

    // endregion

    // region Registration

    private fun observeRegistration() {
        registrationObserverJob = scope.launch {
            Wearables.registrationState.collect { state ->
                diagnosticsStore.log("Registration state changed: $state", DiagnosticsEntry.Category.glasses)
                if (state == RegistrationState.REGISTERED) {
                    registrationWatchdogJob?.cancel()
                }
                publishStatus()
            }
        }
    }

    private fun observeDevices() {
        deviceObserverJob = scope.launch {
            Wearables.devices.collect { devices ->
                val summary = if (devices.isEmpty()) "none" else devices.joinToString { it.identifier }
                diagnosticsStore.log("Devices changed: $summary", DiagnosticsEntry.Category.glasses)
                publishStatus()
            }
        }
    }

    private suspend fun startRegistrationWithRecovery() {
        diagnosticsStore.log("Waiting ${REGISTRATION_START_DELAY_MS}ms before Meta DAT registration", DiagnosticsEntry.Category.glasses)
        delay(REGISTRATION_START_DELAY_MS)

        val activity = activityProvider() ?: run {
            diagnosticsStore.log("No foreground activity for registration", DiagnosticsEntry.Category.error)
            return
        }

        for ((index, retryDelayMs) in REGISTRATION_RETRY_DELAYS_MS.withIndex()) {
            try {
                Wearables.startRegistration(activity)
                return
            } catch (e: Exception) {
                if (!shouldRetryRegistrationStart(e)) throw e
                val attempt = index + 1
                diagnosticsStore.log(
                    "Transient Meta DAT registration failure, retry $attempt of ${REGISTRATION_RETRY_DELAYS_MS.size}: ${diagnosticDescription(e)}",
                    DiagnosticsEntry.Category.glasses,
                )
                delay(retryDelayMs)
            }
        }
        Wearables.startRegistration(activity)
    }

    private fun scheduleRegistrationWatchdog(attemptId: Int) {
        registrationWatchdogJob?.cancel()
        registrationWatchdogJob = scope.launch {
            delay(REGISTRATION_WATCHDOG_MS)
            if (registrationAttemptId != attemptId) return@launch
            if (Wearables.registrationState.value == RegistrationState.REGISTERED) return@launch

            val callbackSummary = lastMetaCallbackAtMs?.let { "lastCallback=$it" } ?: "noCallbackReceived"
            publishFailure(
                "Registration did not complete after Meta AI handoff ($callbackSummary). " +
                    "Check Meta developer app config, tester/release-channel access, " +
                    "App Connections/DAT update in Meta AI, and that glasses are connected."
            )
            logWearablesState("After registration timeout")
        }
    }

    // endregion

    // region Device session

    private suspend fun startDeviceSessionWithRecovery(): DeviceSession {
        var lastError: DatException? = null
        repeat(MAX_SESSION_ATTEMPTS) { attempt ->
            try {
                if (attempt > 0) {
                    diagnosticsStore.log("Retrying DAT device session start, attempt ${attempt + 1} of $MAX_SESSION_ATTEMPTS", DiagnosticsEntry.Category.glasses)
                }
                waitForReadyDeviceIfNeeded()
                return getOrCreateDeviceSession()
            } catch (e: DatException) {
                val datErr = e.error as? DeviceSessionError
                if (datErr == null || !shouldRetryDeviceSessionStart(datErr)) throw e
                lastError = e
                diagnosticsStore.log("DAT device session attempt ${attempt + 1} failed: ${datErr.description}", DiagnosticsEntry.Category.glasses)
                deviceSession?.stop()
                deviceSession = null
                if (attempt < MAX_SESSION_ATTEMPTS - 1) {
                    val delaySec = (attempt + 1) * 2
                    diagnosticsStore.log("Waiting ${delaySec}s before retrying", DiagnosticsEntry.Category.glasses)
                    delay(delaySec * 1_000L)
                }
            }
        }
        throw lastError ?: DatException(DeviceSessionError.UNEXPECTED_ERROR, null)
    }

    private suspend fun getOrCreateDeviceSession(): DeviceSession {
        deviceSession?.let { existing ->
            if (existing.state.value == DeviceSessionState.STARTED) return existing
            if (existing.state.value == DeviceSessionState.STOPPED) {
                deviceSession = null
            } else {
                withTimeoutOrNull(10_000) {
                    existing.state.first {
                        it == DeviceSessionState.STARTED || it == DeviceSessionState.STOPPED
                    }
                }
                if (existing.state.value == DeviceSessionState.STARTED) return existing
                deviceSession = null
            }
        }

        val selector = makeDeviceSelector()
        val session = Wearables.createSession(selector).getOrThrow()
        deviceSession = session
        session.start()

        if (session.state.value != DeviceSessionState.STARTED) {
            withTimeoutOrNull(10_000) {
                session.state.first {
                    it == DeviceSessionState.STARTED || it == DeviceSessionState.STOPPED
                }
            }
            if (session.state.value != DeviceSessionState.STARTED) {
                session.stop()
                deviceSession = null
                throw DatException(DeviceSessionError.UNEXPECTED_ERROR, null)
            }
        }
        return session
    }

    private fun makeDeviceSelector() =
        Wearables.devices.value.firstOrNull()
            ?.let { SpecificDeviceSelector(it) }
            ?: AutoDeviceSelector()

    private suspend fun waitForReadyDeviceIfNeeded() {
        if (Wearables.devices.value.isEmpty()) {
            diagnosticsStore.log("No DAT devices visible yet; waiting for discovery", DiagnosticsEntry.Category.glasses)
            val deadline = System.currentTimeMillis() + 8_000
            while (Wearables.devices.value.isEmpty() && System.currentTimeMillis() < deadline) {
                delay(500)
            }
            logWearablesState("After device discovery wait")
        }

        val deviceId = Wearables.devices.value.firstOrNull() ?: return
        val deadline = System.currentTimeMillis() + 8_000
        while (System.currentTimeMillis() < deadline) {
            val device = Wearables.devicesMetadata[deviceId]?.value
            val connected = device?.linkState == LinkState.CONNECTED
            val compatible = device?.compatibility == DeviceCompatibility.COMPATIBLE
            if (connected && compatible) return
            diagnosticsStore.log(
                "DAT device readiness: id=${device?.name ?: deviceId.identifier}, connected=$connected, compatible=$compatible",
                DiagnosticsEntry.Category.glasses,
            )
            delay(750)
        }
        logWearablesState("After device readiness wait")
    }

    // endregion

    // region Stream listeners

    private fun attachStreamListeners(stream: Stream) {
        streamListenerJob?.cancel()
        streamListenerJob = scope.launch {
            launch {
                stream.state.collect { state ->
                    handleStreamState(state)
                }
            }
            launch {
                stream.errorStream.collect { error ->
                    publishFailure("Stream error: ${error.description}")
                }
            }
            launch {
                stream.videoStream.collect { frame ->
                    handleVideoFrame(
                        VideoFrameData(
                            buffer = frame.buffer,
                            width = frame.width,
                            height = frame.height,
                            presentationTimeUs = frame.presentationTimeUs,
                            isCompressed = frame.isCompressed,
                            isCodecConfig = frame.isCodecConfig,
                        )
                    )
                }
            }
        }
    }

    private fun handleStreamState(state: StreamState) {
        lastReportedStatus = when (state) {
            StreamState.STREAMING ->
                currentBaseStatus().copy(sessionState = SessionState.RUNNING, lastError = null)
            StreamState.STOPPED, StreamState.CLOSED ->
                lastReportedStatus.copy(sessionState = SessionState.STOPPED, lastError = null)
            else ->
                lastReportedStatus.copy(sessionState = SessionState.PREPARING, lastError = null)
        }
        statusDidChange?.invoke(lastReportedStatus)
    }

    private suspend fun handleVideoFrame(frameData: VideoFrameData) {
        totalFrameCounter++
        fpsWindowFrameCounter++

        val nowMs = System.currentTimeMillis()
        if (nowMs - lastPreviewUpdateMs >= 500) {
            previewDidUpdate?.invoke(
                PreviewSnapshot(
                    timestampMs = nowMs,
                    frameIndex = totalFrameCounter,
                    label = "Live DAT frame $totalFrameCounter",
                )
            )
            lastPreviewUpdateMs = nowMs
        }

        val elapsedSec = (nowMs - lastFpsWindowStartMs) / 1_000.0
        if (elapsedSec >= 1.0) {
            fpsDidUpdate?.invoke(fpsWindowFrameCounter / elapsedSec)
            fpsWindowFrameCounter = 0
            lastFpsWindowStartMs = nowMs
        }

        videoBufferDidOutput?.invoke(frameData)
    }

    // endregion

    // region Status helpers

    private fun currentBaseStatus(): DeviceStatus {
        val devices = Wearables.devices.value
        val registered = Wearables.registrationState.value == RegistrationState.REGISTERED

        val rawName = devices.firstOrNull()
            ?.let { Wearables.devicesMetadata[it]?.value?.name }
            ?.trim()
        val deviceName = when {
            !rawName.isNullOrEmpty() -> rawName
            registered -> "Meta AI Glasses"
            else -> "No glasses connected"
        }

        val connectionState = when (Wearables.registrationState.value) {
            RegistrationState.REGISTERED -> ConnectionState.CONNECTED
            RegistrationState.REGISTERING -> ConnectionState.CONNECTING
            else -> ConnectionState.DISCONNECTED
        }

        val sessionState = when {
            activeStream != null -> when (activeStream!!.state.value) {
                StreamState.STREAMING -> SessionState.RUNNING
                StreamState.STOPPED, StreamState.CLOSED -> SessionState.STOPPED
                else -> SessionState.PREPARING
            }
            deviceSession?.state?.value == DeviceSessionState.STARTED -> SessionState.PREPARING
            else -> SessionState.IDLE
        }

        return DeviceStatus(
            connectionState = connectionState,
            batteryLevel = null,
            sessionState = sessionState,
            deviceName = deviceName,
            lastError = null,
        )
    }

    private fun publishStatus() {
        lastReportedStatus = currentBaseStatus()
        statusDidChange?.invoke(lastReportedStatus)
    }

    private fun publishConnectingStatus() {
        lastReportedStatus = DeviceStatus(
            connectionState = ConnectionState.CONNECTING,
            batteryLevel = null,
            sessionState = SessionState.IDLE,
            deviceName = "Connecting through Meta AI app",
            lastError = null,
        )
        statusDidChange?.invoke(lastReportedStatus)
    }

    private fun publishRunningStatus() {
        lastReportedStatus = currentBaseStatus().copy(sessionState = SessionState.RUNNING, lastError = null)
        statusDidChange?.invoke(lastReportedStatus)
    }

    private fun publishFailure(message: String) {
        diagnosticsStore.log(message, DiagnosticsEntry.Category.error)
        lastReportedStatus = currentBaseStatus().copy(
            connectionState = ConnectionState.FAILED,
            sessionState = SessionState.FAILED,
            lastError = message,
        )
        statusDidChange?.invoke(lastReportedStatus)
    }

    // endregion

    // region Diagnostics

    private fun logWearablesState(context: String) {
        val devices = Wearables.devices.value
        val deviceSummary = if (devices.isEmpty()) "none"
        else devices.joinToString { id ->
            Wearables.devicesMetadata[id]?.value?.name ?: id.identifier
        }
        diagnosticsStore.log(
            "$context: registration=${Wearables.registrationState.value}, devices=$deviceSummary",
            DiagnosticsEntry.Category.glasses,
        )
    }

    private fun diagnosticDescription(error: Throwable): String {
        val parts = mutableListOf("type=${error.javaClass.name}")
        error.message?.let { parts.add("message=$it") }
        error.cause?.let { parts.add("cause=${it.javaClass.name}: ${it.message}") }
        return parts.joinToString(" | ")
    }

    private fun shouldRetryRegistrationStart(error: Throwable): Boolean {
        val msg = "${error.message} ${error.javaClass.name}".lowercase()
        return msg.contains("bluetooth") || msg.contains("not ready") || msg.contains("powered on")
    }

    private fun shouldRetryDeviceSessionStart(error: DeviceSessionError): Boolean = when (error) {
        DeviceSessionError.NO_ELIGIBLE_DEVICE -> true
        DeviceSessionError.SESSION_ALREADY_STOPPED -> true
        DeviceSessionError.SESSION_IDLE -> true
        DeviceSessionError.DWA_UNAVAILABLE -> true
        DeviceSessionError.SESSION_ENDED_BY_DEVICE -> true
        else -> false
    }

    // endregion

    companion object {
        private const val REGISTRATION_START_DELAY_MS = 750L
        private val REGISTRATION_RETRY_DELAYS_MS = listOf(1_000L, 2_000L)
        private const val REGISTRATION_WATCHDOG_MS = 35_000L
        private const val MAX_SESSION_ATTEMPTS = 3
    }
}
