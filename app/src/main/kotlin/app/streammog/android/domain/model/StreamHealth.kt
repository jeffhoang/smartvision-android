package app.streammog.android.domain.model

data class StreamHealth(
    val uploadBitrateKbps: Int,
    val averageUploadBitrateKbps: Int,
    val uploadTrendKbps: Int,
    val droppedFrames: Int,
    val encoderLatencyMs: Int,
    val reconnectCount: Int,
    val audioDesyncMs: Int,
    val audioSampleCount: Int,
    val audioLevel: Float,
    val fps: Double,
) {
    val isAudioSilent: Boolean
        get() = audioSampleCount > 0 && audioLevel < 0.02f

    companion object {
        val initial = StreamHealth(
            uploadBitrateKbps = 0,
            averageUploadBitrateKbps = 0,
            uploadTrendKbps = 0,
            droppedFrames = 0,
            encoderLatencyMs = 0,
            reconnectCount = 0,
            audioDesyncMs = 0,
            audioSampleCount = 0,
            audioLevel = 0f,
            fps = 0.0,
        )
    }
}
