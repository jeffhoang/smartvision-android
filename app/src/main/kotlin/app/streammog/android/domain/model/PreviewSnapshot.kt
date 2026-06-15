package app.streammog.android.domain.model

data class PreviewSnapshot(
    val timestampMs: Long,
    val frameIndex: Int,
    val label: String,
)
