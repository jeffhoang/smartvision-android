package app.streammog.android.integrations.streaming

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import app.streammog.android.domain.model.StreamPreset
import app.streammog.android.shared.diagnostics.DiagnosticsEntry
import app.streammog.android.shared.diagnostics.DiagnosticsLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

class MicrophoneAudioCapture(private val diagnosticsStore: DiagnosticsLogging) {

    data class AacFrame(val buffer: ByteBuffer, val info: MediaCodec.BufferInfo)

    private val _audioFlow = MutableSharedFlow<AacFrame>(extraBufferCapacity = 512)
    val audioFlow: SharedFlow<AacFrame> = _audioFlow.asSharedFlow()

    private var audioRecord: AudioRecord? = null
    private var encoder: MediaCodec? = null
    private var captureScope: CoroutineScope? = null

    @Volatile var isMuted = false
    @Volatile var gainFactor = 1.0f

    companion object {
        const val SAMPLE_RATE = 44_100
        const val CHANNEL_COUNT = 1
        const val AUDIO_BITRATE = 96_000
    }

    fun configure(gain: Double, isMuted: Boolean, delayMs: Int, prefersBluetooth: Boolean) {
        gainFactor = gain.toFloat()
        this.isMuted = isMuted
    }

    fun start(preset: StreamPreset) {
        stop()
        try {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            ).coerceAtLeast(8192)

            @Suppress("MissingPermission")
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf * 4,
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                diagnosticsStore.log("AudioRecord failed to initialize", DiagnosticsEntry.Category.error)
                return
            }
            audioRecord = record

            val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, CHANNEL_COUNT)
            format.setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BITRATE)
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, minBuf * 2)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            encoder = codec

            isMuted = preset.isAudioMuted
            gainFactor = preset.audioGain.toFloat()

            record.startRecording()
            diagnosticsStore.log("Microphone capture started at ${SAMPLE_RATE}Hz", DiagnosticsEntry.Category.stream)

            val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
            captureScope = scope
            scope.launch { runCaptureLoop(record, codec, minBuf) }
        } catch (e: Exception) {
            diagnosticsStore.log("Microphone capture start error: ${e.message}", DiagnosticsEntry.Category.error)
            stop()
        }
    }

    fun stop() {
        captureScope?.cancel()
        captureScope = null
        try { audioRecord?.stop() } catch (_: Exception) {}
        try { audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null
        try { encoder?.stop() } catch (_: Exception) {}
        try { encoder?.release() } catch (_: Exception) {}
        encoder = null
    }

    private suspend fun runCaptureLoop(record: AudioRecord, codec: MediaCodec, bufSize: Int) {
        val pcm = ShortArray(bufSize / 2)
        val info = MediaCodec.BufferInfo()
        val timeoutUs = 5_000L

        while (currentCoroutineContext().isActive) {
            val samplesRead = withContext(Dispatchers.IO) {
                record.read(pcm, 0, pcm.size, AudioRecord.READ_BLOCKING)
            }
            if (samplesRead <= 0) continue

            if (isMuted) {
                pcm.fill(0.toShort(), 0, samplesRead)
            } else if (gainFactor != 1.0f) {
                val g = gainFactor
                for (i in 0 until samplesRead) {
                    pcm[i] = (pcm[i] * g).toInt().coerceIn(-32768, 32767).toShort()
                }
            }

            val inputIdx = codec.dequeueInputBuffer(timeoutUs)
            if (inputIdx >= 0) {
                val inputBuf = codec.getInputBuffer(inputIdx) ?: continue
                inputBuf.clear()
                inputBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(pcm, 0, samplesRead)
                val pts = System.nanoTime() / 1_000L
                codec.queueInputBuffer(inputIdx, 0, samplesRead * 2, pts, 0)
            }

            var outIdx = codec.dequeueOutputBuffer(info, 0)
            while (outIdx >= 0) {
                val isConfig = (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                if (!isConfig && info.size > 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)
                    if (outBuf != null) {
                        val copy = ByteBuffer.allocate(info.size)
                        outBuf.position(info.offset).limit(info.offset + info.size)
                        copy.put(outBuf)
                        copy.flip()
                        val infoCopy = MediaCodec.BufferInfo().apply {
                            set(0, info.size, info.presentationTimeUs, info.flags)
                        }
                        _audioFlow.tryEmit(AacFrame(copy, infoCopy))
                    }
                }
                codec.releaseOutputBuffer(outIdx, false)
                outIdx = codec.dequeueOutputBuffer(info, 0)
            }
        }
    }
}
