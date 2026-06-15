package app.streammog.android.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class StreamSessionRecord(
    val id: String,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val transport: StreamPreset.Transport,
    val presetName: String,
    val destinationLabel: String,
    val serviceName: String,
    val targetResolution: String,
    val targetBitrateKbps: Int,
    val averageUploadBitrateKbps: Int,
    val reconnectCount: Int,
    val audioSource: StreamPreset.AudioSource,
    val outcome: Outcome,
    val detailMessage: String?,
) {
    enum class Outcome { STOPPED, FAILED }

    val elapsedSeconds: Int get() = maxOf(0, ((endedAtMs - startedAtMs) / 1000).toInt())
    val durationText: String get() = StreamSessionDuration.format(elapsedSeconds)
    val summaryLine: String get() = "$destinationLabel | $targetResolution | ${averageUploadBitrateKbps} kbps avg"
}
