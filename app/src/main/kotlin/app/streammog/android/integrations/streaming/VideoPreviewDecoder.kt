package app.streammog.android.integrations.streaming

import android.media.MediaCodec
import android.media.MediaFormat
import android.view.Surface
import app.streammog.android.domain.model.VideoFrameData

class VideoPreviewDecoder {

    var onVideoSizeChanged: ((width: Int, height: Int) -> Unit)? = null

    private var codec: MediaCodec? = null
    private var pendingSurface: Surface? = null
    private var lastSps: ByteArray? = null
    private var lastPps: ByteArray? = null
    private var lastWidth: Int = 0
    private var lastHeight: Int = 0

    fun setSurface(surface: Surface) {
        releaseCodec()
        pendingSurface = surface
        val sps = lastSps
        val pps = lastPps
        if (sps != null && pps != null && lastWidth > 0 && lastHeight > 0) {
            initCodec(surface, sps, pps, lastWidth, lastHeight)
        }
    }

    fun clearSurface() {
        releaseCodec()
        pendingSurface = null
    }

    fun processFrame(frame: VideoFrameData) {
        if (!frame.isCompressed) return

        if (frame.isCodecConfig) {
            val surf = pendingSurface ?: return
            val (sps, pps) = H264Utils.parseSpsPps(frame.buffer) ?: return
            lastSps = sps
            lastPps = pps
            lastWidth = frame.width
            lastHeight = frame.height
            initCodec(surf, sps, pps, frame.width, frame.height)
            return
        }

        val c = codec ?: return
        try {
            val inputIdx = c.dequeueInputBuffer(0)
            if (inputIdx >= 0) {
                val buf = c.getInputBuffer(inputIdx) ?: return
                buf.clear()
                val src = frame.buffer.duplicate()
                val bytes = ByteArray(src.remaining())
                src.get(bytes)
                buf.put(bytes)
                val flags = if (H264Utils.isKeyFrame(frame.buffer)) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                c.queueInputBuffer(inputIdx, 0, bytes.size, frame.presentationTimeUs, flags)
            }
            val info = MediaCodec.BufferInfo()
            while (true) {
                val outIdx = c.dequeueOutputBuffer(info, 0)
                if (outIdx < 0) break
                c.releaseOutputBuffer(outIdx, true)
            }
        } catch (e: Exception) {
            // Surface or codec reset — next codec config frame will reinitialize
        }
    }

    fun release() {
        releaseCodec()
        pendingSurface = null
        lastSps = null
        lastPps = null
    }

    private fun initCodec(surface: Surface, sps: ByteArray, pps: ByteArray, width: Int, height: Int) {
        releaseCodec()
        try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
            format.setByteBuffer("csd-0", H264Utils.wrapWithStartCode(sps))
            format.setByteBuffer("csd-1", H264Utils.wrapWithStartCode(pps))
            val c = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            c.configure(format, surface, null, 0)
            c.start()
            codec = c
            onVideoSizeChanged?.invoke(width, height)
        } catch (e: Exception) {
            codec = null
        }
    }

    private fun releaseCodec() {
        try { codec?.stop() } catch (e: Exception) {}
        try { codec?.release() } catch (e: Exception) {}
        codec = null
    }
}
