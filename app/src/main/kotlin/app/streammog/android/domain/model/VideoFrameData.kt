package app.streammog.android.domain.model

import java.nio.ByteBuffer

data class VideoFrameData(
    val buffer: ByteBuffer,
    val width: Int,
    val height: Int,
    val presentationTimeUs: Long,
    val isCompressed: Boolean,
    val isCodecConfig: Boolean,
)
