package app.streammog.android.domain.protocol

import app.streammog.android.domain.model.StreamHealth
import app.streammog.android.domain.model.StreamPreset
import app.streammog.android.domain.model.VideoFrameData
import app.streammog.android.domain.model.VideoTransformSettings

sealed class StreamingTransportEvent {
    object Connecting : StreamingTransportEvent()
    data class Reconnecting(val attempt: Int, val delaySeconds: Int) : StreamingTransportEvent()
    object Connected : StreamingTransportEvent()
    data class Disconnected(val message: String) : StreamingTransportEvent()
    object ExportedToPhotos : StreamingTransportEvent()
    data class PhotosExportFailed(val message: String) : StreamingTransportEvent()
    object Stopped : StreamingTransportEvent()
}

interface StreamingTransport {
    var healthDidChange: ((StreamHealth) -> Unit)?
    var eventDidChange: ((StreamingTransportEvent) -> Unit)?

    suspend fun startStreaming(preset: StreamPreset)
    suspend fun appendVideoBuffer(frame: VideoFrameData)
    suspend fun updateAudioSettings(preset: StreamPreset)
    suspend fun updateVideoTransform(settings: VideoTransformSettings)
    suspend fun stopStreaming()
}
