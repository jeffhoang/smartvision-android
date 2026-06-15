package app.streammog.android.integrations.streaming

import android.content.Context
import app.streammog.android.domain.model.StreamHealth
import app.streammog.android.domain.model.StreamPreset
import app.streammog.android.domain.model.VideoFrameData
import app.streammog.android.domain.model.VideoTransformSettings
import app.streammog.android.domain.protocol.StreamingTransport
import app.streammog.android.domain.protocol.StreamingTransportEvent
import app.streammog.android.shared.diagnostics.DiagnosticsEntry
import app.streammog.android.shared.diagnostics.DiagnosticsLogging

// Dispatches to RtmpStreamTransport or LocalRecordingTransport based on preset.transport.
// When recordWhileStreaming is true alongside RTMP, both run concurrently.
class RoutingStreamTransport(
    private val diagnosticsStore: DiagnosticsLogging,
    private val context: Context,
) : StreamingTransport {

    override var healthDidChange: ((StreamHealth) -> Unit)? = null
    override var eventDidChange: ((StreamingTransportEvent) -> Unit)? = null

    private var primaryTransport: StreamingTransport? = null
    private var secondaryTransport: StreamingTransport? = null

    override suspend fun startStreaming(preset: StreamPreset) {
        stopStreaming()

        when (preset.transport) {
            StreamPreset.Transport.RTMP -> {
                val rtmp = RtmpStreamTransport(diagnosticsStore, context)
                rtmp.healthDidChange = { healthDidChange?.invoke(it) }
                rtmp.eventDidChange = { eventDidChange?.invoke(it) }
                primaryTransport = rtmp
                rtmp.startStreaming(preset)

                if (preset.recordWhileStreaming) {
                    val local = LocalRecordingTransport(diagnosticsStore, context)
                    local.eventDidChange = { event ->
                        // Surface save/error events from the secondary recording.
                        if (event is StreamingTransportEvent.ExportedToPhotos ||
                            event is StreamingTransportEvent.PhotosExportFailed
                        ) {
                            eventDidChange?.invoke(event)
                        }
                    }
                    secondaryTransport = local
                    local.startStreaming(preset)
                }
            }

            StreamPreset.Transport.LOCAL_RECORDING -> {
                val local = LocalRecordingTransport(diagnosticsStore, context)
                local.healthDidChange = { healthDidChange?.invoke(it) }
                local.eventDidChange = { eventDidChange?.invoke(it) }
                primaryTransport = local
                local.startStreaming(preset)
            }

            StreamPreset.Transport.SRT -> {
                diagnosticsStore.log("SRT transport not yet implemented", DiagnosticsEntry.Category.stream)
                eventDidChange?.invoke(StreamingTransportEvent.Disconnected("SRT transport is not yet implemented."))
            }
        }
    }

    override suspend fun appendVideoBuffer(frame: VideoFrameData) {
        primaryTransport?.appendVideoBuffer(frame)
        secondaryTransport?.appendVideoBuffer(frame)
    }

    override suspend fun updateAudioSettings(preset: StreamPreset) {
        primaryTransport?.updateAudioSettings(preset)
        secondaryTransport?.updateAudioSettings(preset)
    }

    override suspend fun updateVideoTransform(settings: VideoTransformSettings) {
        primaryTransport?.updateVideoTransform(settings)
        secondaryTransport?.updateVideoTransform(settings)
    }

    override suspend fun stopStreaming() {
        primaryTransport?.stopStreaming()
        primaryTransport = null
        secondaryTransport?.stopStreaming()
        secondaryTransport = null
    }
}
