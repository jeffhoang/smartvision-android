package app.streammog.android.integrations.streaming

import app.streammog.android.domain.model.StreamHealth
import app.streammog.android.domain.model.StreamPreset
import app.streammog.android.domain.model.VideoFrameData
import app.streammog.android.domain.model.VideoTransformSettings
import app.streammog.android.domain.protocol.StreamingTransport
import app.streammog.android.domain.protocol.StreamingTransportEvent

class MockStreamTransport : StreamingTransport {
    override var healthDidChange: ((StreamHealth) -> Unit)? = null
    override var eventDidChange: ((StreamingTransportEvent) -> Unit)? = null

    override suspend fun startStreaming(preset: StreamPreset) {
        eventDidChange?.invoke(StreamingTransportEvent.Connected)
    }

    override suspend fun appendVideoBuffer(frame: VideoFrameData) {}

    override suspend fun updateAudioSettings(preset: StreamPreset) {}

    override suspend fun updateVideoTransform(settings: VideoTransformSettings) {}

    override suspend fun stopStreaming() {
        eventDidChange?.invoke(StreamingTransportEvent.Stopped)
    }
}
