package app.streammog.android.coordinator

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.RingtoneManager
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.streammog.android.app.AppBrand
import app.streammog.android.app.AppEntitlements
import app.streammog.android.domain.model.ConnectionState
import app.streammog.android.domain.model.DeviceStatus
import app.streammog.android.domain.model.PreviewSnapshot
import app.streammog.android.domain.model.SessionState
import app.streammog.android.domain.model.StreamDestination
import app.streammog.android.domain.model.StreamHealth
import app.streammog.android.domain.model.StreamPreflightCheck
import app.streammog.android.domain.model.StreamPreflightReport
import app.streammog.android.domain.model.StreamPreset
import app.streammog.android.domain.model.StreamSessionDuration
import app.streammog.android.domain.model.StreamSessionRecord
import app.streammog.android.domain.model.StreamingState
import app.streammog.android.domain.model.VideoTransformSettings
import app.streammog.android.domain.protocol.GlassesSessionClient
import app.streammog.android.domain.protocol.StreamingTransport
import app.streammog.android.domain.protocol.StreamingTransportEvent
import app.streammog.android.shared.diagnostics.DiagnosticsEntry
import app.streammog.android.shared.diagnostics.DiagnosticsLogging
import app.streammog.android.shared.persistence.SessionHistoryStore
import app.streammog.android.shared.persistence.StreamPresetStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class StreamingCoordinator(
    val glassesClient: GlassesSessionClient,
    private val streamTransport: StreamingTransport,
    private val diagnosticsStore: DiagnosticsLogging,
    val presetStore: StreamPresetStore,
    val sessionHistoryStore: SessionHistoryStore,
    private var entitlements: AppEntitlements,
    private val appContext: Context,
) : ViewModel() {

    private val _entitlementsFlow = MutableStateFlow(entitlements)
    val entitlementsFlow: StateFlow<AppEntitlements> = _entitlementsFlow.asStateFlow()

    private val _streamingState = MutableStateFlow<StreamingState>(StreamingState.Idle)
    val streamingState: StateFlow<StreamingState> = _streamingState.asStateFlow()

    private val _deviceStatus = MutableStateFlow(DeviceStatus.initial)
    val deviceStatus: StateFlow<DeviceStatus> = _deviceStatus.asStateFlow()

    private val _streamHealth = MutableStateFlow(StreamHealth.initial)
    val streamHealth: StateFlow<StreamHealth> = _streamHealth.asStateFlow()

    private val _streamDuration = MutableStateFlow(StreamSessionDuration.notStreaming)
    val streamDuration: StateFlow<StreamSessionDuration> = _streamDuration.asStateFlow()

    private val _previewSnapshot = MutableStateFlow<PreviewSnapshot?>(null)
    val previewSnapshot: StateFlow<PreviewSnapshot?> = _previewSnapshot.asStateFlow()

    private val _exportAlertMessage = MutableStateFlow<String?>(null)
    val exportAlertMessage: StateFlow<String?> = _exportAlertMessage.asStateFlow()

    private val _preflightReport = MutableStateFlow(StreamPreflightReport.empty)
    val preflightReport: StateFlow<StreamPreflightReport> = _preflightReport.asStateFlow()

    private val _streamTestResultMessage = MutableStateFlow<String?>(null)
    val streamTestResultMessage: StateFlow<String?> = _streamTestResultMessage.asStateFlow()

    private val _systemWarningMessage = MutableStateFlow<String?>(null)
    val systemWarningMessage: StateFlow<String?> = _systemWarningMessage.asStateFlow()

    private val _creatorDefaultSummary = MutableStateFlow(
        creatorDefaultSummary(presetStore.loadCreatorDefaultPreset())
    )
    val creatorDefaultSummary: StateFlow<String?> = _creatorDefaultSummary.asStateFlow()

    private val _sessionHistory = MutableStateFlow(
        sessionHistoryStore.loadEntries(maxCount = entitlements.maxSessionHistoryEntries)
    )
    val sessionHistory: StateFlow<List<StreamSessionRecord>> = _sessionHistory.asStateFlow()

    private val _selectedPreset = MutableStateFlow(presetStore.loadSelectedPreset())
    val selectedPreset: StateFlow<StreamPreset> = _selectedPreset.asStateFlow()

    private val _savedDestinations = MutableStateFlow(
        presetStore.loadSavedDestinations(maxCount = entitlements.maxSavedDestinations)
    )
    val savedDestinations: StateFlow<List<StreamDestination>> = _savedDestinations.asStateFlow()

    private val _previewSettings = MutableStateFlow(VideoTransformSettings())
    val previewSettings: StateFlow<VideoTransformSettings> = _previewSettings.asStateFlow()

    val keepScreenOn: StateFlow<Boolean> get() = _keepScreenOn
    private val _keepScreenOn = MutableStateFlow(false)

    private var connectJob: Job? = null
    private var sessionJob: Job? = null
    private var streamStartJob: Job? = null
    private var streamTestJob: Job? = null
    private var cleanupJob: Job? = null
    private var durationJob: Job? = null

    private var cleanupSequence = 0
    private val loggedMilestones = mutableSetOf<Int>()
    private var didChimeForCurrentConnection = false
    private var activeSessionStartedAtMs: Long? = null
    private var pendingSessionStopReason: String? = null
    private var isRunningTestStream = false

    init {
        glassesClient.statusDidChange = { status ->
            viewModelScope.launch {
                val wasConnected = _deviceStatus.value.connectionState == ConnectionState.CONNECTED
                _deviceStatus.value = status
                if (status.connectionState == ConnectionState.CONNECTED) {
                    if (!wasConnected && !didChimeForCurrentConnection) {
                        playConnectionChime()
                        didChimeForCurrentConnection = true
                    }
                    if (_streamingState.value == StreamingState.ConnectingGlasses) {
                        _streamingState.value = StreamingState.Ready
                    }
                } else {
                    if (wasConnected) playDisconnectAlert()
                    didChimeForCurrentConnection = false
                }
                updateSystemWarnings()
            }
        }

        glassesClient.previewDidUpdate = { snapshot ->
            viewModelScope.launch { _previewSnapshot.value = snapshot }
        }

        glassesClient.fpsDidUpdate = { fps ->
            viewModelScope.launch {
                _streamHealth.value = _streamHealth.value.copy(fps = fps)
            }
        }

        glassesClient.videoBufferDidOutput = { frame ->
            streamTransport.appendVideoBuffer(frame)
        }

        streamTransport.healthDidChange = { health ->
            viewModelScope.launch { _streamHealth.value = health }
        }

        streamTransport.eventDidChange = { event ->
            viewModelScope.launch { handleTransportEvent(event) }
        }

        viewModelScope.launch {
            streamTransport.updateVideoTransform(_previewSettings.value)
        }

        viewModelScope.launch {
            _streamingState.collect { state ->
                manageForegroundService(state)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        durationJob?.cancel()
        connectJob?.cancel()
        sessionJob?.cancel()
        streamStartJob?.cancel()
        streamTestJob?.cancel()
        cleanupJob?.cancel()
    }

    fun connectGlasses() {
        if (_streamingState.value == StreamingState.ConnectingGlasses) return
        if (sessionJob != null || streamStartJob != null) {
            diagnosticsStore.log("Connect ignored because a session or stream request is already running", DiagnosticsEntry.Category.app)
            return
        }
        if (connectJob != null) {
            diagnosticsStore.log("Connect ignored because a connect request is already running", DiagnosticsEntry.Category.app)
            return
        }
        _streamingState.value = StreamingState.ConnectingGlasses
        diagnosticsStore.log("Connecting to glasses", DiagnosticsEntry.Category.app)

        connectJob = viewModelScope.launch {
            try {
                glassesClient.connect()
            } catch (e: CancellationException) {
                diagnosticsStore.log("Connect request cancelled", DiagnosticsEntry.Category.app)
                throw e
            } catch (e: Exception) {
                fail("Failed to connect glasses: ${e.message ?: e.toString()}")
            } finally {
                connectJob = null
            }
        }
    }

    fun startSession() {
        if (sessionJob != null) {
            diagnosticsStore.log("Start Session ignored because a session request is already running", DiagnosticsEntry.Category.app)
            return
        }
        if (streamStartJob != null) {
            diagnosticsStore.log("Start Session ignored because Go Live is already starting", DiagnosticsEntry.Category.app)
            return
        }
        diagnosticsStore.log("Requesting device media session", DiagnosticsEntry.Category.app)

        sessionJob = viewModelScope.launch {
            try {
                glassesClient.startSession()
                if (_streamingState.value == StreamingState.Idle) {
                    _streamingState.value = StreamingState.Ready
                }
            } catch (e: CancellationException) {
                diagnosticsStore.log("Device media session request cancelled", DiagnosticsEntry.Category.app)
                throw e
            } catch (e: Exception) {
                fail("Failed to start session: ${e.message ?: e.toString()}")
            } finally {
                sessionJob = null
            }
        }
    }

    fun startStreaming() {
        if (streamStartJob != null) {
            diagnosticsStore.log("Go Live ignored because streaming is already starting", DiagnosticsEntry.Category.app)
            return
        }
        if (cleanupJob != null) {
            diagnosticsStore.log("Go Live ignored because cleanup is still finishing", DiagnosticsEntry.Category.app)
            return
        }
        if (streamTestJob != null) {
            diagnosticsStore.log("Go Live ignored because a test stream is already running", DiagnosticsEntry.Category.app)
            return
        }
        if (sessionJob != null) {
            diagnosticsStore.log("Go Live ignored because a session request is already running", DiagnosticsEntry.Category.app)
            return
        }

        val report = makePreflightReport(requiresRunningSession = false)
        _preflightReport.value = report
        if (report.hasFailures) {
            val message = "Go Live blocked by preflight: ${report.summary}"
            _streamingState.value = StreamingState.Failed(message)
            diagnosticsStore.log(message, DiagnosticsEntry.Category.error)
            return
        }

        _deviceStatus.value = _deviceStatus.value.copy(lastError = null)
        _streamingState.value = StreamingState.StartingStream
        diagnosticsStore.log("Starting outbound stream", DiagnosticsEntry.Category.app)

        streamStartJob = viewModelScope.launch {
            try {
                _streamHealth.value = StreamHealth.initial
                val startedAtMs = System.currentTimeMillis()
                if (_deviceStatus.value.sessionState != SessionState.RUNNING) {
                    glassesClient.startSession()
                }
                waitForFreshPreviewFrame(afterMs = startedAtMs, timeoutSeconds = 8)
                streamTransport.startStreaming(_selectedPreset.value)
            } catch (e: CancellationException) {
                diagnosticsStore.log("Go Live request cancelled", DiagnosticsEntry.Category.app)
                throw e
            } catch (e: Exception) {
                fail("Failed to start streaming: ${e.message ?: e.toString()}")
            } finally {
                streamStartJob = null
            }
        }
    }

    fun runTestStream() {
        if (streamTestJob != null) {
            diagnosticsStore.log("Test Stream ignored because a test is already running", DiagnosticsEntry.Category.app)
            return
        }
        if (cleanupJob != null) {
            diagnosticsStore.log("Test Stream ignored because cleanup is still finishing", DiagnosticsEntry.Category.app)
            return
        }
        if (streamStartJob != null || sessionJob != null) {
            diagnosticsStore.log("Test Stream ignored because another stream request is already running", DiagnosticsEntry.Category.app)
            return
        }

        val report = makePreflightReport(requiresRunningSession = false)
        _preflightReport.value = report
        if (report.hasFailures) {
            val message = "Test Stream blocked by preflight: ${report.summary}"
            _streamTestResultMessage.value = message
            diagnosticsStore.log(message, DiagnosticsEntry.Category.error)
            return
        }

        _streamTestResultMessage.value = null
        _deviceStatus.value = _deviceStatus.value.copy(lastError = null)
        _streamingState.value = StreamingState.StartingStream
        diagnosticsStore.log("Starting 10 second RTMP test stream", DiagnosticsEntry.Category.app)
        isRunningTestStream = true

        streamTestJob = viewModelScope.launch {
            try {
                _streamHealth.value = StreamHealth.initial
                val startedAtMs = System.currentTimeMillis()
                if (_deviceStatus.value.sessionState != SessionState.RUNNING) {
                    glassesClient.startSession()
                }
                waitForFreshPreviewFrame(afterMs = startedAtMs, timeoutSeconds = 8)
                val startedFrameIndex = _previewSnapshot.value?.frameIndex ?: 0
                streamTransport.startStreaming(_selectedPreset.value)
                delay(10_000L)
                val result = testStreamResult(startedFrameIndex)
                streamTransport.stopStreaming()
                glassesClient.pauseSession()
                stopDurationTracking()
                _streamingState.value = StreamingState.Ready
                _streamTestResultMessage.value = result
                diagnosticsStore.log(result, DiagnosticsEntry.Category.stream)
            } catch (e: CancellationException) {
                diagnosticsStore.log("Test Stream cancelled", DiagnosticsEntry.Category.app)
                throw e
            } catch (e: Exception) {
                val message = "Test Stream failed: ${e.message ?: e.toString()}"
                streamTransport.stopStreaming()
                stopDurationTracking()
                _streamingState.value = StreamingState.Failed(message)
                _streamTestResultMessage.value = message
                diagnosticsStore.log(message, DiagnosticsEntry.Category.error)
            } finally {
                streamTestJob = null
                isRunningTestStream = false
            }
        }
    }

    fun stopStreaming() {
        diagnosticsStore.log("Stopping outbound stream", DiagnosticsEntry.Category.app)
        if (activeSessionStartedAtMs != null) {
            pendingSessionStopReason = "Stopped by user."
        }
        streamStartJob?.cancel()
        streamStartJob = null
        streamTestJob?.cancel()
        streamTestJob = null

        runCleanupJob("Stop stream") {
            streamTransport.stopStreaming()
            glassesClient.pauseSession()
            _streamingState.value = StreamingState.Ready
            updateKeepScreenOn()
        }
    }

    fun stopAll(reason: String? = null) {
        if (reason != null) {
            diagnosticsStore.log("Stopping all active work: $reason", DiagnosticsEntry.Category.app)
        } else {
            diagnosticsStore.log("Stopping all active work", DiagnosticsEntry.Category.app)
        }
        connectJob?.cancel()
        connectJob = null
        sessionJob?.cancel()
        sessionJob = null
        streamStartJob?.cancel()
        streamStartJob = null
        streamTestJob?.cancel()
        streamTestJob = null
        if (activeSessionStartedAtMs != null) {
            pendingSessionStopReason = reason ?: "Stopped all activity."
        }

        runCleanupJob("Stop all") {
            streamTransport.stopStreaming()
            glassesClient.stopSession()
            _streamingState.value = StreamingState.Idle
            updateKeepScreenOn()
        }
    }

    fun resetSession() {
        diagnosticsStore.log("Resetting stream session", DiagnosticsEntry.Category.app)
        if (activeSessionStartedAtMs != null) {
            pendingSessionStopReason = "Session reset."
        }
        streamStartJob?.cancel()
        streamStartJob = null
        streamTestJob?.cancel()
        streamTestJob = null
        sessionJob?.cancel()
        sessionJob = null

        runCleanupJob("Reset session") {
            streamTransport.stopStreaming()
            glassesClient.stopSession()
            stopDurationTracking()
            _previewSnapshot.value = null
            _streamHealth.value = StreamHealth.initial
            _streamingState.value = if (_deviceStatus.value.connectionState == ConnectionState.CONNECTED)
                StreamingState.Ready else StreamingState.Idle
            updateKeepScreenOn()
        }
    }

    fun toggleMute() {
        val muted = !_selectedPreset.value.isAudioMuted
        updatePreset(_selectedPreset.value.copy(isAudioMuted = muted))
        diagnosticsStore.log(if (muted) "Audio muted" else "Audio unmuted", DiagnosticsEntry.Category.app)
    }

    fun updatePreset(preset: StreamPreset) {
        val old = _selectedPreset.value
        _selectedPreset.value = preset
        presetStore.saveSelectedPreset(preset)
        viewModelScope.launch { streamTransport.updateAudioSettings(preset) }
        if (preset.targetResolution != old.targetResolution ||
            preset.targetBitrateKbps != old.targetBitrateKbps ||
            preset.targetFPS != old.targetFPS
        ) {
            diagnosticsStore.log(
                "Updated stream quality: resolution=${preset.targetResolution}, bitrate=${preset.targetBitrateKbps} kbps, fps=${preset.targetFPS}",
                DiagnosticsEntry.Category.app,
            )
        }
    }

    fun updatePreviewSettings(settings: VideoTransformSettings) {
        if (settings == _previewSettings.value) return
        _previewSettings.value = settings
        viewModelScope.launch { streamTransport.updateVideoTransform(settings) }
    }

    fun saveCurrentDestination() {
        val destination = StreamDestination(preset = _selectedPreset.value)
        val list = _savedDestinations.value.toMutableList()
        val existingIndex = list.indexOfFirst { it.name == destination.name }
        when {
            existingIndex >= 0 -> list[existingIndex] = destination
            list.size < entitlements.maxSavedDestinations -> list.add(destination)
            else -> {
                diagnosticsStore.log(
                    "Save destination blocked by ${entitlements.tier.displayName} tier limit",
                    DiagnosticsEntry.Category.app,
                )
                return
            }
        }
        persistDestinations(list)
        diagnosticsStore.log("Saved stream destination: ${destination.name}", DiagnosticsEntry.Category.app)
    }

    fun applyDestination(destination: StreamDestination) {
        updatePreset(destination.applying(to = _selectedPreset.value))
        diagnosticsStore.log("Selected stream destination: ${destination.name}", DiagnosticsEntry.Category.app)
    }

    fun deleteDestinations(indices: Set<Int>) {
        val list = _savedDestinations.value.toMutableList()
        val names = indices.map { list[it].name }.joinToString(", ")
        indices.sortedDescending().forEach { list.removeAt(it) }
        persistDestinations(list)
        diagnosticsStore.log("Deleted stream destination: $names", DiagnosticsEntry.Category.app)
    }

    fun saveCreatorDefaults() {
        presetStore.saveCreatorDefaultPreset(_selectedPreset.value)
        _creatorDefaultSummary.value = creatorDefaultSummary(_selectedPreset.value)
        diagnosticsStore.log("Saved creator defaults from current preset", DiagnosticsEntry.Category.app)
    }

    fun applyCreatorDefaults() {
        val preset = presetStore.loadCreatorDefaultPreset() ?: run {
            diagnosticsStore.log("Apply creator defaults ignored because no defaults are saved", DiagnosticsEntry.Category.app)
            return
        }
        updatePreset(preset.resolvingCurrentQualityPreset())
        diagnosticsStore.log("Applied creator defaults", DiagnosticsEntry.Category.app)
    }

    fun clearCreatorDefaults() {
        presetStore.clearCreatorDefaultPreset()
        _creatorDefaultSummary.value = null
        diagnosticsStore.log("Cleared creator defaults", DiagnosticsEntry.Category.app)
    }

    fun clearSessionHistory() {
        sessionHistoryStore.clear()
        _sessionHistory.value = emptyList()
        diagnosticsStore.log("Cleared session history", DiagnosticsEntry.Category.app)
    }

    fun applyRtmpImport(rawValue: String) {
        val value = rawValue.trim()
        if (value.isEmpty()) return
        val uri = runCatching { java.net.URI(value) }.getOrNull()
        if (uri != null && uri.scheme?.startsWith("rtmp") == true) {
            val pathParts = uri.path?.trimStart('/')?.split("/")?.filter { it.isNotEmpty() } ?: emptyList()
            val portSuffix = if (uri.port > 0) ":${uri.port}" else ""
            val host = "${uri.scheme}://${uri.host}$portSuffix"
            val preset = _selectedPreset.value.copy(
                transport = StreamPreset.Transport.RTMP,
                streamingService = StreamPreset.StreamingService.CUSTOM,
                host = host,
                streamKey = pathParts.lastOrNull() ?: _selectedPreset.value.streamKey,
                appPath = if (pathParts.size >= 2) pathParts.dropLast(1).joinToString("/") else _selectedPreset.value.appPath,
            )
            updatePreset(preset)
            diagnosticsStore.log("Imported RTMP URL from QR/text", DiagnosticsEntry.Category.app)
        } else {
            updatePreset(_selectedPreset.value.copy(streamKey = value))
            diagnosticsStore.log("Imported stream key from QR/text", DiagnosticsEntry.Category.app)
        }
    }

    fun resetPreset() {
        updatePreset(presetStore.resetSelectedPreset())
        diagnosticsStore.log("Reset stream preset to defaults", DiagnosticsEntry.Category.app)
    }

    fun updateEntitlements(updated: AppEntitlements) {
        entitlements = updated
        _entitlementsFlow.value = updated
        _savedDestinations.value = presetStore.loadSavedDestinations(maxCount = updated.maxSavedDestinations)
        _sessionHistory.value = sessionHistoryStore.loadEntries(maxCount = updated.maxSessionHistoryEntries)

        var preset = _selectedPreset.value
        if (preset.transport == StreamPreset.Transport.SRT && !updated.canUsePremiumProtocols) {
            preset = preset.copy(transport = StreamPreset.Transport.RTMP)
        }
        val service = preset.streamingService
        if (service != null && !updated.allows(service)) {
            preset = preset.copy(
                streamingService = StreamPreset.StreamingService.YOUTUBE,
                name = StreamPreset.StreamingService.YOUTUBE.defaultPresetName,
                host = StreamPreset.StreamingService.YOUTUBE.host,
                appPath = StreamPreset.StreamingService.YOUTUBE.appPath,
            )
        }
        if (preset != _selectedPreset.value) updatePreset(preset)
        diagnosticsStore.log("Applied ${updated.tier.displayName} entitlements", DiagnosticsEntry.Category.app)
    }

    fun resetAppState() {
        stopAll()
        updatePreset(presetStore.resetSelectedPreset())
        _savedDestinations.value = emptyList()
        _streamHealth.value = StreamHealth.initial
        _streamDuration.value = StreamSessionDuration.notStreaming
        _previewSnapshot.value = null
        _exportAlertMessage.value = null
        _streamTestResultMessage.value = null
        _systemWarningMessage.value = null
        diagnosticsStore.log("Reset ${AppBrand.DISPLAY_NAME} local app state", DiagnosticsEntry.Category.app)
    }

    fun dismissExportAlert() { _exportAlertMessage.value = null }
    fun dismissStreamTestResult() { _streamTestResultMessage.value = null }

    fun handleAppBackground() {
        if (_streamingState.value == StreamingState.Idle && cleanupJob == null) return
        _systemWarningMessage.value = "${AppBrand.DISPLAY_NAME} moved to background. Streaming may stop if Android suspends camera or accessory capture."
        diagnosticsStore.log("App moved to background", DiagnosticsEntry.Category.app)
    }

    fun handleAppForeground() {
        val current = _systemWarningMessage.value
        if (current?.contains("background") == true) _systemWarningMessage.value = null
        diagnosticsStore.log("App returned to foreground", DiagnosticsEntry.Category.app)
    }

    // ---- Private ----

    private fun fail(message: String) {
        val friendly = friendlyErrorMessage(message)
        diagnosticsStore.log(friendly, DiagnosticsEntry.Category.error)
        stopDurationTracking()
        _deviceStatus.value = _deviceStatus.value.copy(lastError = friendly)
        _streamingState.value = StreamingState.Failed(friendly)
        updateKeepScreenOn()
    }

    private fun runCleanupJob(label: String, block: suspend () -> Unit) {
        cleanupSequence++
        val seq = cleanupSequence
        cleanupJob?.cancel()
        cleanupJob = viewModelScope.launch {
            diagnosticsStore.log("$label cleanup started", DiagnosticsEntry.Category.app)
            block()
            if (!isActive || seq != cleanupSequence) {
                diagnosticsStore.log("$label cleanup superseded", DiagnosticsEntry.Category.app)
                return@launch
            }
            cleanupJob = null
            diagnosticsStore.log("$label cleanup finished", DiagnosticsEntry.Category.app)
        }
    }

    private suspend fun waitForFreshPreviewFrame(afterMs: Long, timeoutSeconds: Int) {
        val deadline = System.currentTimeMillis() + timeoutSeconds * 1_000L
        while (System.currentTimeMillis() < deadline) {
            val snapshot = _previewSnapshot.value
            if (snapshot != null && snapshot.timestampMs >= afterMs) {
                diagnosticsStore.log("Fresh camera frame ready for streaming: frame ${snapshot.frameIndex}", DiagnosticsEntry.Category.stream)
                return
            }
            delay(200L)
        }
        throw Exception("No fresh camera frames arrived after starting the DAT session.")
    }

    private fun handleTransportEvent(event: StreamingTransportEvent) {
        when (event) {
            is StreamingTransportEvent.Connecting -> {
                if (_streamingState.value is StreamingState.Failed) return
                if (_streamingState.value != StreamingState.StartingStream) {
                    _streamingState.value = StreamingState.StartingStream
                }
            }
            is StreamingTransportEvent.Reconnecting -> {
                _streamingState.value = StreamingState.Recovering
                diagnosticsStore.log(
                    "RTMP reconnecting: attempt ${event.attempt} in ${event.delaySeconds}s",
                    DiagnosticsEntry.Category.stream,
                )
            }
            is StreamingTransportEvent.Connected -> {
                if (_streamingState.value is StreamingState.Streaming) return
                if (_streamingState.value is StreamingState.Failed) return
                val startedAtMs = System.currentTimeMillis()
                activeSessionStartedAtMs = startedAtMs
                pendingSessionStopReason = null
                _streamingState.value = StreamingState.Streaming(startedAtMs = startedAtMs)
                updateKeepScreenOn()
                startDurationTracking(startedAtMs)
            }
            is StreamingTransportEvent.Disconnected -> {
                val friendly = friendlyErrorMessage(event.message)
                _deviceStatus.value = _deviceStatus.value.copy(lastError = friendly)
                when (_streamingState.value) {
                    is StreamingState.Streaming -> {
                        persistSessionRecord(StreamSessionRecord.Outcome.FAILED, detailMessage = friendly)
                        stopDurationTracking()
                        _streamingState.value = StreamingState.Failed(friendly)
                        updateKeepScreenOn()
                    }
                    StreamingState.StartingStream -> {
                        stopDurationTracking()
                        _streamingState.value = StreamingState.Failed(friendly)
                        updateKeepScreenOn()
                    }
                    else -> _streamingState.value = StreamingState.Recovering
                }
            }
            is StreamingTransportEvent.Stopped -> {
                persistSessionRecord(StreamSessionRecord.Outcome.STOPPED, detailMessage = pendingSessionStopReason)
                stopDurationTracking()
                if (_streamingState.value is StreamingState.Failed) return
                if (_streamingState.value != StreamingState.Idle &&
                    _streamingState.value != StreamingState.StartingStream
                ) {
                    _streamingState.value = StreamingState.Ready
                }
                updateKeepScreenOn()
            }
            is StreamingTransportEvent.ExportedToPhotos -> {
                _exportAlertMessage.value = "Recording exported to Photos."
            }
            is StreamingTransportEvent.PhotosExportFailed -> {
                _exportAlertMessage.value = "Could not export recording: ${event.message}"
            }
        }
    }

    private fun startDurationTracking(startedAtMs: Long) {
        durationJob?.cancel()
        loggedMilestones.clear()
        updateDuration(startedAtMs)
        durationJob = viewModelScope.launch {
            while (isActive) {
                delay(1_000L)
                if (!isActive) return@launch
                updateDuration(startedAtMs)
            }
        }
    }

    private fun stopDurationTracking() {
        durationJob?.cancel()
        durationJob = null
        _streamDuration.value = StreamSessionDuration.notStreaming
        loggedMilestones.clear()
        activeSessionStartedAtMs = null
        pendingSessionStopReason = null
    }

    private fun updateDuration(startedAtMs: Long) {
        _streamDuration.value = StreamSessionDuration.running(startedAtMs)
        enforceStreamDurationLimitIfNeeded()
        updateSystemWarnings()
        for (milestone in listOf(180, 600, 1800, 3600)) {
            if (_streamDuration.value.elapsedSeconds >= milestone && loggedMilestones.add(milestone)) {
                diagnosticsStore.log("Stream passed ${StreamSessionDuration.format(milestone)}", DiagnosticsEntry.Category.stream)
            }
        }
    }

    private fun enforceStreamDurationLimitIfNeeded() {
        val limit = entitlements.maxStreamDurationSeconds ?: return
        if (_streamDuration.value.elapsedSeconds < limit) return
        if (pendingSessionStopReason != null) return
        pendingSessionStopReason = "${entitlements.tier.displayName} stream limit reached (${StreamSessionDuration.format(limit)})."
        diagnosticsStore.log(pendingSessionStopReason!!, DiagnosticsEntry.Category.stream)
        stopStreaming()
    }

    private fun makePreflightReport(requiresRunningSession: Boolean): StreamPreflightReport {
        val preset = _selectedPreset.value
        val status = _deviceStatus.value
        val checks = mutableListOf<StreamPreflightCheck>()

        checks += StreamPreflightCheck(
            id = "glasses",
            title = "Glasses",
            detail = if (status.connectionState == ConnectionState.CONNECTED)
                "Connected to ${status.deviceName}" else "Connect glasses first.",
            status = if (status.connectionState == ConnectionState.CONNECTED)
                StreamPreflightCheck.Status.PASSED else StreamPreflightCheck.Status.FAILED,
        )

        val sessionPassed = status.sessionState == SessionState.RUNNING ||
            (!requiresRunningSession && status.connectionState == ConnectionState.CONNECTED)
        checks += StreamPreflightCheck(
            id = "session",
            title = "Session",
            detail = if (status.sessionState == SessionState.RUNNING)
                "Camera session is running."
            else
                "${AppBrand.DISPLAY_NAME} will request the DAT camera session.",
            status = if (sessionPassed) StreamPreflightCheck.Status.PASSED else StreamPreflightCheck.Status.FAILED,
        )

        if (preset.transport == StreamPreset.Transport.RTMP) {
            val urlResult = preset.makePublishUrl()
            val isPlaceholder = urlResult.getOrNull()?.contains("example.com") == true ||
                preset.streamKey.trim() == "replace-me"
            checks += StreamPreflightCheck(
                id = "destination",
                title = "RTMP URL/key",
                detail = when {
                    urlResult.isFailure -> urlResult.exceptionOrNull()?.message ?: "Invalid URL"
                    isPlaceholder -> "Replace the placeholder URL and key."
                    else -> "URL and stream key are set."
                },
                status = if (urlResult.isFailure || isPlaceholder)
                    StreamPreflightCheck.Status.FAILED else StreamPreflightCheck.Status.PASSED,
            )
        }

        checks += StreamPreflightCheck(
            id = "bitrate",
            title = "Bitrate",
            detail = "${preset.targetBitrateKbps} kbps",
            status = if (preset.targetBitrateKbps < 1000)
                StreamPreflightCheck.Status.WARNING else StreamPreflightCheck.Status.PASSED,
        )
        checks += StreamPreflightCheck(
            id = "fps",
            title = "FPS",
            detail = "${preset.targetFPS} fps",
            status = if (preset.transport == StreamPreset.Transport.RTMP && preset.targetFPS > 30)
                StreamPreflightCheck.Status.WARNING else StreamPreflightCheck.Status.PASSED,
        )
        checks += StreamPreflightCheck(
            id = "keyframe",
            title = "Keyframe",
            detail = "${preset.targetKeyframeIntervalSeconds}s",
            status = if (preset.targetKeyframeIntervalSeconds in 1..4)
                StreamPreflightCheck.Status.PASSED else StreamPreflightCheck.Status.WARNING,
        )
        checks += StreamPreflightCheck(
            id = "audio",
            title = "Audio",
            detail = preset.audioSource.displayName,
            status = if (preset.audioSource == StreamPreset.AudioSource.NONE)
                StreamPreflightCheck.Status.WARNING else StreamPreflightCheck.Status.PASSED,
        )

        return StreamPreflightReport(checks = checks)
    }

    private fun testStreamResult(startedFrameIndex: Int): String {
        val health = _streamHealth.value
        val preset = _selectedPreset.value
        val frameDelta = (_previewSnapshot.value?.frameIndex ?: 0) - startedFrameIndex
        val uploadPercent = if (preset.targetBitrateKbps > 0)
            (health.uploadBitrateKbps.toDouble() / preset.targetBitrateKbps * 100).toInt() else 0

        val warnings = mutableListOf<String>()
        if (frameDelta == 0) warnings += "No camera frames reached the publisher."
        if (preset.transport == StreamPreset.Transport.RTMP &&
            health.uploadBitrateKbps < maxOf(800, preset.targetBitrateKbps / 3)
        ) {
            warnings += "Upload bitrate is far below target; lower bitrate/FPS or change networks."
        }
        when {
            preset.audioSource == StreamPreset.AudioSource.NONE -> warnings += "Audio is turned off."
            health.audioSampleCount == 0 -> warnings += "No microphone samples were captured."
            health.isAudioSilent -> warnings += "Microphone samples are present but nearly silent."
        }
        if (health.reconnectCount > 0) warnings += "RTMP reconnected ${health.reconnectCount} time(s) during the test."

        val verdict = if (warnings.isEmpty()) "Pass" else "Needs attention"
        val serviceName = preset.streamingService?.displayName ?: "Custom"
        val audioLine = if (preset.audioSource == StreamPreset.AudioSource.NONE)
            "Audio: off"
        else
            "Audio: ${health.audioSampleCount} samples, level ${"%.2f".format(health.audioLevel)}"
        val actionLine = if (warnings.isEmpty()) "Next action: Go Live." else "Next action: ${warnings.joinToString(" ")}"

        return """Test Stream $verdict
Platform: $serviceName
Publish: connected for 10 seconds
Video: $frameDelta frames, ${health.fps.toInt()} fps
Upload: ${health.uploadBitrateKbps} kbps of ${preset.targetBitrateKbps} kbps target ($uploadPercent%)
$audioLine
Reconnects: ${health.reconnectCount}
$actionLine"""
    }

    private fun persistSessionRecord(outcome: StreamSessionRecord.Outcome, detailMessage: String?) {
        if (isRunningTestStream) return
        val startedAtMs = activeSessionStartedAtMs ?: return
        val preset = _selectedPreset.value
        val serviceName = preset.streamingService?.displayName ?: preset.transport.displayName
        val destinationLabel = if (preset.transport == StreamPreset.Transport.RTMP)
            preset.name else "${AppBrand.DISPLAY_NAME} Local Recordings"
        val entry = StreamSessionRecord(
            id = java.util.UUID.randomUUID().toString(),
            startedAtMs = startedAtMs,
            endedAtMs = System.currentTimeMillis(),
            transport = preset.transport,
            presetName = preset.name,
            destinationLabel = destinationLabel,
            serviceName = serviceName,
            targetResolution = preset.targetResolution,
            targetBitrateKbps = preset.targetBitrateKbps,
            averageUploadBitrateKbps = _streamHealth.value.averageUploadBitrateKbps,
            reconnectCount = _streamHealth.value.reconnectCount,
            audioSource = preset.audioSource,
            outcome = outcome,
            detailMessage = detailMessage,
        )
        _sessionHistory.value = sessionHistoryStore.append(entry, maxCount = entitlements.maxSessionHistoryEntries)
    }

    private fun persistDestinations(destinations: List<StreamDestination>) {
        _savedDestinations.value = destinations
        presetStore.saveDestinations(destinations, maxCount = entitlements.maxSavedDestinations)
    }

    private fun manageForegroundService(state: StreamingState) {
        val ctx = appContext
        when (state) {
            is StreamingState.StartingStream -> app.streammog.android.service.StreamingForegroundService.start(ctx, "Starting stream…")
            is StreamingState.Streaming -> app.streammog.android.service.StreamingForegroundService.start(ctx, "Live — ${AppBrand.DISPLAY_NAME}")
            is StreamingState.Recovering -> app.streammog.android.service.StreamingForegroundService.start(ctx, "Reconnecting…")
            is StreamingState.Idle, is StreamingState.Failed -> app.streammog.android.service.StreamingForegroundService.stop(ctx)
            else -> Unit
        }
    }

    private fun updateKeepScreenOn() {
        _keepScreenOn.value = _selectedPreset.value.keepAwakeWhileStreaming &&
            _streamingState.value is StreamingState.Streaming
    }

    private fun updateSystemWarnings() {
        val glassesBattery = _deviceStatus.value.batteryLevel
        if (glassesBattery != null && glassesBattery <= 20) {
            _systemWarningMessage.value = "Glasses battery is low at $glassesBattery%. Charge before a long stream."
            return
        }
        val phoneBattery = phoneBatteryPercent()
        if (phoneBattery in 0..19) {
            _systemWarningMessage.value = "Phone battery is low at $phoneBattery%. Plug in power before recording or streaming."
            return
        }
        val thermalWarning = thermalWarning()
        if (thermalWarning != null) {
            _systemWarningMessage.value = thermalWarning
            return
        }
        val current = _systemWarningMessage.value
        if (current?.contains("battery") == true || current?.contains("thermal") == true) {
            _systemWarningMessage.value = null
        }
    }

    private fun phoneBatteryPercent(): Int {
        val intent = appContext.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        return if (level >= 0 && scale > 0) level * 100 / scale else -1
    }

    @Suppress("DEPRECATION")
    private fun thermalWarning(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val pm = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return null
        return when (pm.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_SEVERE ->
                "Phone thermal state is serious. Lower bitrate or stop recording if frames drop."
            PowerManager.THERMAL_STATUS_CRITICAL,
            PowerManager.THERMAL_STATUS_EMERGENCY,
            PowerManager.THERMAL_STATUS_SHUTDOWN ->
                "Phone thermal state is critical. Stop streaming to avoid interruption."
            else -> null
        }
    }

    private fun playConnectionChime() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(appContext, uri)?.play()
            diagnosticsStore.log("Played glasses connection chime", DiagnosticsEntry.Category.app)
        } catch (_: Exception) {}
    }

    private fun playDisconnectAlert() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(appContext, uri)?.play()
            diagnosticsStore.log("Played glasses disconnect alert", DiagnosticsEntry.Category.app)
        } catch (_: Exception) {}
    }

    private fun friendlyErrorMessage(message: String): String {
        if (message.contains("No eligible device")) {
            return "No eligible glasses are available. Next: wake the glasses, verify they are connected in Meta AI, then tap Reset Session and Connect."
        }
        if (message.contains("Session ended by device")) {
            return "The glasses ended the camera session. Next: stop any Meta AI camera/live activity, hard-toggle the glasses if needed, then tap Reset Session."
        }
        if (message.contains("Device session did not reach started state") || message.contains("DAT session failed to start")) {
            return "The Meta DAT camera session never fully started. Next: keep ${AppBrand.DISPLAY_NAME} foregrounded, stop camera use in Meta AI, tap Reset Session, then Start Session again."
        }
        if (message.contains("capability of this type is already active")) {
            return "A camera capability is already active on the glasses. Next: tap Stop All Activity, wait 10 seconds, then Start Session once."
        }
        if (message.contains("DAT app is not registered") || message.contains("not registered with AI glasses")) {
            return "${AppBrand.DISPLAY_NAME} is not registered with Meta AI glasses. Next: open Meta AI, confirm ${AppBrand.DISPLAY_NAME} appears under App Connections, then Connect again."
        }
        if (message.contains("RTMP socket") || message.contains("Network is down")) {
            return "The RTMP network connection failed. Next: check Wi-Fi/cellular upload, verify the stream URL/key, then use Test Stream."
        }
        if (message.contains("publish rejected") || message.contains("connectRejected")) {
            return "The platform rejected the RTMP publish. Next: regenerate the stream key and confirm the selected platform profile."
        }
        if (message.contains("stream key") || message.contains("missingStreamKey")) {
            return "The stream key is missing or invalid. Next: paste a fresh key from the platform and run Test Stream."
        }
        if (message.contains("host is invalid") || message.contains("RTMP host is invalid")) {
            return "The RTMP server URL is invalid. Next: choose a platform profile or paste a full rtmp:// or rtmps:// ingest URL."
        }
        if (message.contains("Microphone permission")) {
            return "Microphone access is blocked. Next: enable Microphone permission for ${AppBrand.DISPLAY_NAME} in Android Settings."
        }
        if (message.contains("No frames") || message.contains("no video")) {
            return "No video frames reached the stream. Next: Start Session first, confirm Preview is moving, then Go Live."
        }
        return message
    }

    companion object {
        private fun creatorDefaultSummary(preset: StreamPreset?): String? {
            preset ?: return null
            val service = preset.streamingService?.displayName ?: preset.transport.displayName
            return "${preset.name} - $service, ${StreamPreset.META_DAT_SOURCE_RESOLUTION} source, ${preset.targetBitrateKbps} kbps, ${preset.targetFPS} fps"
        }
    }
}
