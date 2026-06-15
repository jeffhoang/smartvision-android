package app.streammog.android.integrations.streaming

import java.nio.ByteBuffer

internal object H264Utils {
    // Returns (spsBytes, ppsBytes) without start codes, or null if not found.
    fun parseSpsPps(buffer: ByteBuffer): Pair<ByteArray, ByteArray>? {
        val bytes = buffer.toByteArray()
        val sps = findNal(bytes, nalType = 7) ?: return null
        val pps = findNal(bytes, nalType = 8) ?: return null
        return sps to pps
    }

    // Returns a ByteBuffer with Annex B start code prepended (for MediaMuxer csd-0/csd-1).
    fun wrapWithStartCode(nal: ByteArray): ByteBuffer {
        val buf = ByteBuffer.allocate(nal.size + 4)
        buf.put(0); buf.put(0); buf.put(0); buf.put(1)
        buf.put(nal)
        buf.flip()
        return buf
    }

    // Returns true if the buffer begins with an IDR (keyframe) NAL unit.
    fun isKeyFrame(buffer: ByteBuffer): Boolean {
        val peek = ByteArray(minOf(32, buffer.remaining()))
        buffer.duplicate().get(peek)
        return firstNalType(peek) == 5
    }

    // Returns NAL unit bytes WITHOUT start code, or null if nalType not found.
    private fun findNal(data: ByteArray, nalType: Int): ByteArray? {
        var i = 0
        while (i < data.size - 5) {
            if (isStartCode(data, i)) {
                val type = data[i + 4].toInt() and 0x1F
                if (type == nalType) {
                    val start = i + 4
                    var end = data.size
                    for (j in (start + 1) until (data.size - 4)) {
                        if (isStartCode(data, j)) { end = j; break }
                    }
                    return data.copyOfRange(start, end)
                }
            }
            i++
        }
        return null
    }

    private fun isStartCode(data: ByteArray, i: Int): Boolean =
        data[i] == 0.toByte() && data[i + 1] == 0.toByte() &&
            data[i + 2] == 0.toByte() && data[i + 3] == 1.toByte()

    private fun firstNalType(data: ByteArray): Int {
        for (i in 0 until (data.size - 4)) {
            if (isStartCode(data, i)) return data[i + 4].toInt() and 0x1F
        }
        return -1
    }

    private fun ByteBuffer.toByteArray(): ByteArray {
        val arr = ByteArray(remaining())
        duplicate().get(arr)
        return arr
    }
}
