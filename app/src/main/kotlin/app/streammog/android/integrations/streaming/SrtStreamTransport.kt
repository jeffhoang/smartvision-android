package app.streammog.android.integrations.streaming

import android.content.Context
import android.media.MediaCodec
import app.streammog.android.domain.model.StreamHealth
import app.streammog.android.domain.model.StreamPreset
import app.streammog.android.domain.model.VideoFrameData
import app.streammog.android.domain.model.VideoTransformSettings
import app.streammog.android.domain.protocol.StreamingTransport
import app.streammog.android.domain.protocol.StreamingTransportEvent
import app.streammog.android.shared.diagnostics.DiagnosticsEntry
import app.streammog.android.shared.diagnostics.DiagnosticsLogging
import com.pedro.common.ConnectChecker
import com.pedro.srt.srt.SrtClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

class SrtStreamTransport(
    private val diagnosticsStore: DiagnosticsLogging,
    @Suppress("UnusedPrivateProperty") private val context: Context,
) : StreamingTransport {

    override var healthDidChange: ((StreamHealth) -> Unit)? = null
    override var eventDidChange: ((StreamingTransportEvent) -> Unit)? = null

    private val micCapture = MicrophoneAudioCapture(diagnosticsStore)
    private var srtClient: SrtClient? = null
    private var streamingScope: CoroutineScope? = null
    private var audioJob: Job? = null
    private var activePreset: StreamPreset? = null
    private var reconnectAttempt = 0

    private var pendingSps: ByteArray? = null
    private var pendingPps: ByteArray? = null

    override suspend fun startStreaming(preset: StreamPreset) {
        stopStreaming()
        activePreset = preset
        reconnectAttempt = 0
        pendingSps = null
        pendingPps = null

        val url = preset.makeSrtUrl().getOrElse {
            eventDidChange?.invoke(StreamingTransportEvent.Disconnected(it.message ?: "Invalid SRT URL"))
            return
        }

        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        streamingScope = scope

        val client = buildSrtClient(scope)
        client.setAudioInfo(MicrophoneAudioCapture.SAMPLE_RATE, isStereo = false)
        srtClient = client

        if (preset.audioSource != StreamPreset.AudioSource.NONE) {
            micCapture.configure(
                gain = preset.audioGain,
                isMuted = preset.isAudioMuted,
                delayMs = preset.audioDelayMs,
                prefersBluetooth = preset.audioSource == StreamPreset.AudioSource.BLUETOOTH_MICROPHONE,
            )
            micCapture.start(preset)
            audioJob = scope.launch {
                micCapture.audioFlow.collect { frame ->
                    if (client.isStreaming) {
                        client.sendAudio(frame.buffer, frame.info)
                    }
                }
            }
        }

        eventDidChange?.invoke(StreamingTransportEvent.Connecting)
        diagnosticsStore.log("SRT connecting: ${redactedUrl(url)}", DiagnosticsEntry.Category.stream)
        withContext(Dispatchers.IO) { client.connect(url) }
    }

    override suspend fun appendVideoBuffer(frame: VideoFrameData) {
        val client = srtClient ?: return
        if (!frame.isCompressed) return

        withContext(Dispatchers.IO) {
            if (frame.isCodecConfig) {
                val (sps, pps) = H264Utils.parseSpsPps(frame.buffer) ?: return@withContext
                pendingSps = sps
                pendingPps = pps
                client.setVideoInfo(ByteBuffer.wrap(sps), ByteBuffer.wrap(pps), null)
                return@withContext
            }
            if (!client.isStreaming) return@withContext

            val flags = if (H264Utils.isKeyFrame(frame.buffer)) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
            val info = MediaCodec.BufferInfo().apply {
                set(frame.buffer.position(), frame.buffer.remaining(), frame.presentationTimeUs, flags)
            }
            client.sendVideo(frame.buffer, info)
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
            try { srtClient?.disconnect() } catch (_: Exception) {}
        }
        srtClient = null
        streamingScope?.cancel()
        streamingScope = null
        activePreset = null
        pendingSps = null
        pendingPps = null
        eventDidChange?.invoke(StreamingTransportEvent.Stopped)
    }

    private fun buildSrtClient(scope: CoroutineScope): SrtClient =
        SrtClient(object : ConnectChecker {
            override fun onConnectionStarted(url: String) {}

            override fun onConnectionSuccess() {
                reconnectAttempt = 0
                diagnosticsStore.log("SRT connected", DiagnosticsEntry.Category.stream)
                val sps = pendingSps
                val pps = pendingPps
                if (sps != null && pps != null) {
                    srtClient?.setVideoInfo(ByteBuffer.wrap(sps.copyOf()), ByteBuffer.wrap(pps.copyOf()), null)
                }
                eventDidChange?.invoke(StreamingTransportEvent.Connected)
            }

            override fun onConnectionFailed(reason: String) {
                diagnosticsStore.log("SRT connection failed: $reason", DiagnosticsEntry.Category.stream)
                scope.launch { handleConnectionFailed(reason) }
            }

            override fun onNewBitrate(bitrate: Long) {
                healthDidChange?.invoke(
                    StreamHealth.initial.copy(uploadBitrateKbps = (bitrate / 1000L).toInt())
                )
            }

            override fun onDisconnect() {
                diagnosticsStore.log("SRT disconnected", DiagnosticsEntry.Category.stream)
            }

            override fun onAuthError() {
                diagnosticsStore.log("SRT auth error", DiagnosticsEntry.Category.error)
                eventDidChange?.invoke(StreamingTransportEvent.Disconnected("SRT authentication failed"))
            }

            override fun onAuthSuccess() {}
        })

    private suspend fun handleConnectionFailed(reason: String) {
        reconnectAttempt++
        val maxAttempts = 5
        if (reconnectAttempt > maxAttempts) {
            eventDidChange?.invoke(StreamingTransportEvent.Disconnected("Stream failed after $maxAttempts reconnect attempts"))
            return
        }
        val delaySecs = reconnectDelaySecs(reconnectAttempt)
        eventDidChange?.invoke(StreamingTransportEvent.Reconnecting(reconnectAttempt, delaySecs))
        diagnosticsStore.log(
            "SRT reconnect $reconnectAttempt/$maxAttempts in ${delaySecs}s",
            DiagnosticsEntry.Category.stream,
        )
        delay(delaySecs * 1_000L)
        val url = activePreset?.makeSrtUrl()?.getOrNull() ?: return
        val client = srtClient ?: return
        val sps = pendingSps
        val pps = pendingPps
        if (sps != null && pps != null) {
            client.setVideoInfo(ByteBuffer.wrap(sps.copyOf()), ByteBuffer.wrap(pps.copyOf()), null)
        }
        withContext(Dispatchers.IO) { client.connect(url, true) }
    }

    private fun reconnectDelaySecs(attempt: Int): Int = when (attempt) {
        1 -> 1; 2 -> 2; 3 -> 4; 4 -> 8; 5 -> 16; else -> 30
    }

    private fun redactedUrl(url: String): String {
        val idx = url.indexOf('?')
        return if (idx > 0) "${url.substring(0, idx)}?***" else url
    }
}
