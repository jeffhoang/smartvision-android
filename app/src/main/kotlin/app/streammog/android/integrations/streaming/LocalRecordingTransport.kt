package app.streammog.android.integrations.streaming

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.os.Environment
import android.provider.MediaStore
import app.streammog.android.domain.model.StreamHealth
import app.streammog.android.domain.model.StreamPreset
import app.streammog.android.domain.model.VideoFrameData
import app.streammog.android.domain.model.VideoTransformSettings
import app.streammog.android.domain.protocol.StreamingTransport
import app.streammog.android.domain.protocol.StreamingTransportEvent
import app.streammog.android.shared.diagnostics.DiagnosticsEntry
import app.streammog.android.shared.diagnostics.DiagnosticsLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LocalRecordingTransport(
    private val diagnosticsStore: DiagnosticsLogging,
    private val context: Context,
) : StreamingTransport {

    override var healthDidChange: ((StreamHealth) -> Unit)? = null
    override var eventDidChange: ((StreamingTransportEvent) -> Unit)? = null

    private val micCapture = MicrophoneAudioCapture(diagnosticsStore)
    private val muxerMutex = Mutex()

    private var muxer: MediaMuxer? = null
    private var videoTrack = -1
    private var audioTrack = -1
    private var isMuxerStarted = false
    private val pendingAudio = ArrayDeque<MicrophoneAudioCapture.AacFrame>()
    private var outputFile: File? = null
    private var activePreset: StreamPreset? = null
    private var streamingScope: CoroutineScope? = null
    private var audioJob: Job? = null
    private var frameCount = 0
    private var startedAtMs = 0L
    private var lastHealthMs = 0L

    override suspend fun startStreaming(preset: StreamPreset) {
        stopStreaming()
        activePreset = preset

        val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir
        if (!dir.exists()) dir.mkdirs()
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(dir, "streammog-$timestamp.mp4")
        outputFile = file

        try {
            val newMuxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = newMuxer
        } catch (e: Exception) {
            diagnosticsStore.log("Failed to create recording muxer: ${e.message}", DiagnosticsEntry.Category.error)
            eventDidChange?.invoke(StreamingTransportEvent.Disconnected(e.message ?: "Recording init failed"))
            return
        }

        frameCount = 0
        startedAtMs = System.currentTimeMillis()
        lastHealthMs = 0L
        isMuxerStarted = false
        videoTrack = -1
        audioTrack = -1
        pendingAudio.clear()

        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        streamingScope = scope

        if (preset.audioSource != StreamPreset.AudioSource.NONE) {
            micCapture.configure(
                gain = preset.audioGain,
                isMuted = preset.isAudioMuted,
                delayMs = preset.audioDelayMs,
                prefersBluetooth = preset.audioSource == StreamPreset.AudioSource.BLUETOOTH_MICROPHONE,
            )
            micCapture.start(preset)
            audioJob = scope.launch {
                micCapture.audioFlow.collect { frame -> appendAudioFrame(frame) }
            }
        }

        diagnosticsStore.log("Local recording started: ${file.name}", DiagnosticsEntry.Category.stream)
        eventDidChange?.invoke(StreamingTransportEvent.Connected)
    }

    override suspend fun appendVideoBuffer(frame: VideoFrameData) {
        val m = muxer ?: return
        if (!frame.isCompressed) return

        withContext(Dispatchers.IO) {
            muxerMutex.withLock {
                if (frame.isCodecConfig) {
                    if (videoTrack >= 0) return@withLock
                    val (sps, pps) = H264Utils.parseSpsPps(frame.buffer) ?: return@withLock

                    val videoFormat = MediaFormat.createVideoFormat(
                        MediaFormat.MIMETYPE_VIDEO_AVC, frame.width, frame.height
                    )
                    videoFormat.setByteBuffer("csd-0", H264Utils.wrapWithStartCode(sps))
                    videoFormat.setByteBuffer("csd-1", H264Utils.wrapWithStartCode(pps))
                    videoTrack = m.addTrack(videoFormat)

                    if (activePreset?.audioSource != StreamPreset.AudioSource.NONE) {
                        val audioFormat = MediaFormat.createAudioFormat(
                            MediaFormat.MIMETYPE_AUDIO_AAC,
                            MicrophoneAudioCapture.SAMPLE_RATE,
                            MicrophoneAudioCapture.CHANNEL_COUNT,
                        )
                        audioFormat.setInteger(MediaFormat.KEY_BIT_RATE, MicrophoneAudioCapture.AUDIO_BITRATE)
                        audioTrack = m.addTrack(audioFormat)
                    }

                    m.start()
                    isMuxerStarted = true
                    diagnosticsStore.log(
                        "Recording muxer started: ${frame.width}x${frame.height}",
                        DiagnosticsEntry.Category.stream,
                    )

                    for (pending in pendingAudio) {
                        if (audioTrack >= 0) {
                            try { m.writeSampleData(audioTrack, pending.buffer, pending.info) } catch (_: Exception) {}
                        }
                    }
                    pendingAudio.clear()
                    return@withLock
                }

                if (!isMuxerStarted || videoTrack < 0) return@withLock

                val flags = if (H264Utils.isKeyFrame(frame.buffer)) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                val info = MediaCodec.BufferInfo().apply {
                    set(frame.buffer.position(), frame.buffer.remaining(), frame.presentationTimeUs, flags)
                }
                try {
                    m.writeSampleData(videoTrack, frame.buffer, info)
                    frameCount++
                    publishHealthIfNeeded()
                } catch (e: Exception) {
                    diagnosticsStore.log("Recording video write error: ${e.message}", DiagnosticsEntry.Category.error)
                }
            }
        }
    }

    override suspend fun updateAudioSettings(preset: StreamPreset) {
        activePreset = preset
        micCapture.configure(
            gain = preset.audioGain,
            isMuted = preset.isAudioMuted,
            delayMs = preset.audioDelayMs,
            prefersBluetooth = preset.audioSource == StreamPreset.AudioSource.BLUETOOTH_MICROPHONE,
        )
    }

    override suspend fun updateVideoTransform(settings: VideoTransformSettings) {}

    override suspend fun stopStreaming() {
        audioJob?.cancel()
        audioJob = null
        micCapture.stop()

        withContext(Dispatchers.IO) {
            muxerMutex.withLock {
                val m = muxer ?: return@withLock
                try {
                    if (isMuxerStarted) m.stop()
                    m.release()
                } catch (e: Exception) {
                    diagnosticsStore.log("Recording finalize error: ${e.message}", DiagnosticsEntry.Category.error)
                }
                muxer = null
                videoTrack = -1
                audioTrack = -1
                isMuxerStarted = false
                pendingAudio.clear()
            }
        }

        streamingScope?.cancel()
        streamingScope = null
        val file = outputFile
        activePreset = null
        outputFile = null

        if (file != null && file.exists() && file.length() > 0) {
            val sizeKb = file.length() / 1024
            diagnosticsStore.log("Recording finalized: ${file.name} ($sizeKb KB)", DiagnosticsEntry.Category.stream)
            val exported = exportToGallery(file)
            if (exported) {
                file.delete()
                diagnosticsStore.log("Gallery export successful, temp file removed", DiagnosticsEntry.Category.stream)
                eventDidChange?.invoke(StreamingTransportEvent.ExportedToPhotos)
            } else {
                diagnosticsStore.log("Gallery export failed, temp file kept: ${file.absolutePath}", DiagnosticsEntry.Category.error)
                eventDidChange?.invoke(StreamingTransportEvent.PhotosExportFailed("Failed to save recording to gallery. File kept at: ${file.name}"))
            }
        } else {
            diagnosticsStore.log("Recording stopped with no output", DiagnosticsEntry.Category.stream)
        }
        eventDidChange?.invoke(StreamingTransportEvent.Stopped)
    }

    private suspend fun exportToGallery(file: File): Boolean = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val details = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES)
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, details) ?: return@withContext false
            try {
                resolver.openOutputStream(uri)?.use { output ->
                    file.inputStream().use { input -> input.copyTo(output) }
                }
                details.clear()
                details.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, details, null, null)
                true
            } catch (e: Exception) {
                diagnosticsStore.log("Gallery export write error: ${e.message}", DiagnosticsEntry.Category.error)
                resolver.delete(uri, null, null)
                false
            }
        } catch (e: Exception) {
            diagnosticsStore.log("Gallery export error: ${e.message}", DiagnosticsEntry.Category.error)
            false
        }
    }

    private suspend fun appendAudioFrame(frame: MicrophoneAudioCapture.AacFrame) {
        muxerMutex.withLock {
            val m = muxer ?: return
            if (!isMuxerStarted) {
                pendingAudio.addLast(frame)
                return
            }
            if (audioTrack < 0) return
            try { m.writeSampleData(audioTrack, frame.buffer, frame.info) } catch (_: Exception) {}
        }
    }

    private fun publishHealthIfNeeded() {
        val now = System.currentTimeMillis()
        if (now - lastHealthMs < 1_000) return
        lastHealthMs = now
        val elapsed = ((now - startedAtMs) / 1_000.0).coerceAtLeast(1.0)
        healthDidChange?.invoke(
            StreamHealth.initial.copy(fps = frameCount / elapsed)
        )
    }
}
