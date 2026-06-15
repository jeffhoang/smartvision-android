package app.streammog.android.coordinator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.streammog.android.app.AppEntitlements
import app.streammog.android.domain.model.DeviceStatus
import app.streammog.android.domain.model.StreamHealth
import app.streammog.android.domain.model.StreamPreset
import app.streammog.android.domain.model.StreamingState
import app.streammog.android.domain.protocol.GlassesSessionClient
import app.streammog.android.domain.protocol.StreamingTransport
import app.streammog.android.domain.protocol.StreamingTransportEvent
import app.streammog.android.shared.diagnostics.DiagnosticsEntry
import app.streammog.android.shared.diagnostics.DiagnosticsLogging
import app.streammog.android.shared.persistence.SessionHistoryStore
import app.streammog.android.shared.persistence.StreamPresetStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// Phase 1 skeleton — mirrors iOS StreamingCoordinator's public surface.
// Full implementation lands in Phase 3.
class StreamingCoordinator(
    val glassesClient: GlassesSessionClient,
    private val streamTransport: StreamingTransport,
    private val diagnosticsStore: DiagnosticsLogging,
    val presetStore: StreamPresetStore,
    val sessionHistoryStore: SessionHistoryStore,
    private var entitlements: AppEntitlements,
) : ViewModel() {

    private val _streamingState = MutableStateFlow<StreamingState>(StreamingState.Idle)
    val streamingState: StateFlow<StreamingState> = _streamingState.asStateFlow()

    private val _deviceStatus = MutableStateFlow(DeviceStatus.initial)
    val deviceStatus: StateFlow<DeviceStatus> = _deviceStatus.asStateFlow()

    private val _streamHealth = MutableStateFlow(StreamHealth.initial)
    val streamHealth: StateFlow<StreamHealth> = _streamHealth.asStateFlow()

    private val _selectedPreset = MutableStateFlow(presetStore.loadSelectedPreset())
    val selectedPreset: StateFlow<StreamPreset> = _selectedPreset.asStateFlow()

    init {
        glassesClient.statusDidChange = { status ->
            _deviceStatus.value = status
        }
        glassesClient.videoBufferDidOutput = { frame ->
            streamTransport.appendVideoBuffer(frame)
        }
        streamTransport.healthDidChange = { health ->
            _streamHealth.value = health
        }
        streamTransport.eventDidChange = { event ->
            handleTransportEvent(event)
        }
    }

    fun connectGlasses() {
        viewModelScope.launch {
            _streamingState.value = StreamingState.ConnectingGlasses
            try {
                glassesClient.connect()
                _streamingState.value = StreamingState.Ready
            } catch (e: Exception) {
                _streamingState.value = StreamingState.Failed(e.message ?: "Connection failed")
            }
        }
    }

    fun startStream() {
        viewModelScope.launch {
            _streamingState.value = StreamingState.StartingStream
            try {
                glassesClient.startSession()
                streamTransport.startStreaming(_selectedPreset.value)
                _streamingState.value = StreamingState.Streaming(startedAtMs = System.currentTimeMillis())
            } catch (e: Exception) {
                _streamingState.value = StreamingState.Failed(e.message ?: "Stream start failed")
            }
        }
    }

    fun stopStream() {
        viewModelScope.launch {
            try {
                streamTransport.stopStreaming()
                glassesClient.stopSession()
            } catch (e: Exception) {
                diagnosticsStore.log("Stop stream error: ${e.message}", DiagnosticsEntry.Category.error)
            }
            _streamingState.value = StreamingState.Ready
        }
    }

    fun updatePreset(preset: StreamPreset) {
        _selectedPreset.value = preset
        presetStore.saveSelectedPreset(preset)
        viewModelScope.launch {
            streamTransport.updateAudioSettings(preset)
        }
    }

    fun updateEntitlements(updated: AppEntitlements) {
        entitlements = updated
    }

    private fun handleTransportEvent(event: StreamingTransportEvent) {
        when (event) {
            is StreamingTransportEvent.Reconnecting ->
                _streamingState.value = StreamingState.Recovering
            is StreamingTransportEvent.Disconnected ->
                _streamingState.value = StreamingState.Failed(event.message)
            else -> {}
        }
    }
}
