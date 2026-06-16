package app.streammog.android.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class StreamPreset(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var transport: Transport,
    var audioSource: AudioSource,
    var host: String,
    var appPath: String,
    var streamKey: String,
    var streamingService: StreamingService? = null,
    var isStreamKeyLocked: Boolean = true,
    var requiresBiometricsForStreamKey: Boolean = false,
    var recordWhileStreaming: Boolean = false,
    var localBestQualityMode: Boolean = false,
    var recordingSplitMinutes: Int = 0,
    var autoDeleteRecordingsWhenLowStorage: Boolean = false,
    var recordingRetentionDays: Int = 0,
    var recordingStorageLimitGB: Int = 0,
    var recordingName: String = "",
    var recordingTags: String = "",
    var isAudioMuted: Boolean = false,
    var audioGain: Double = 1.0,
    var audioDelayMs: Int = 0,
    var keepAwakeWhileStreaming: Boolean = true,
    var targetResolution: String,
    var targetBitrateKbps: Int,
    var targetFPS: Int,
    var targetKeyframeIntervalSeconds: Int = 2,
    var qualityPreset: QualityPreset? = null,
) {
    @Serializable
    enum class StreamingService(val displayName: String) {
        CUSTOM("Custom"),
        YOUTUBE("YouTube"),
        TWITCH("Twitch"),
        KICK("Kick"),
        FACEBOOK("Facebook"),
        RESTREAM("Restream"),
        INSTAGRAM("Instagram"),
        TIKTOK("TikTok"),
        LINKEDIN("LinkedIn"),
        VIMEO("Vimeo"),
        RUMBLE("Rumble");

        val host: String get() = when (this) {
            CUSTOM -> ""
            YOUTUBE -> "rtmp://a.rtmp.youtube.com"
            TWITCH -> "rtmp://live.twitch.tv"
            KICK -> "rtmps://fa723fc1b171.global-contribute.live-video.net:443"
            FACEBOOK -> "rtmps://live-api-s.facebook.com:443"
            RESTREAM -> "rtmp://live.restream.io"
            RUMBLE -> "rtmp://live.rumble.com"
            INSTAGRAM, TIKTOK, LINKEDIN, VIMEO -> ""
        }

        val appPath: String get() = when (this) {
            CUSTOM -> ""
            YOUTUBE -> "live2"
            TWITCH, KICK -> "app"
            FACEBOOK -> "rtmp"
            RESTREAM, RUMBLE -> "live"
            INSTAGRAM, TIKTOK, LINKEDIN, VIMEO -> ""
        }

        val defaultPresetName: String get() = when (this) {
            CUSTOM -> "Custom RTMP"
            YOUTUBE -> "YouTube Live"
            TWITCH -> "Twitch"
            KICK -> "Kick"
            FACEBOOK -> "Facebook Live"
            RESTREAM -> "Restream"
            INSTAGRAM -> "Instagram Live"
            TIKTOK -> "TikTok Live"
            LINKEDIN -> "LinkedIn Live"
            VIMEO -> "Vimeo Live"
            RUMBLE -> "Rumble"
        }

        val usesProviderGeneratedUrl: Boolean get() = when (this) {
            INSTAGRAM, TIKTOK, LINKEDIN, VIMEO -> true
            else -> false
        }

        val recommendedBitrateKbps: Int? get() = when (this) {
            YOUTUBE, TWITCH -> 6000
            KICK -> 8000
            FACEBOOK -> 4000
            RESTREAM -> 6000
            RUMBLE -> 6000
            else -> null
        }

        val recommendedFps: Int? get() = when (this) {
            YOUTUBE, TWITCH, KICK, FACEBOOK, RESTREAM, RUMBLE -> 30
            else -> null
        }

        val recommendedKeyframeIntervalSeconds: Int? get() = when (this) {
            YOUTUBE, TWITCH, KICK, FACEBOOK, RESTREAM, RUMBLE -> 2
            else -> null
        }

        companion object {
            val selectableCases get() = entries.filter { it != TIKTOK }
        }
    }

    @Serializable
    enum class Transport(val displayName: String) {
        RTMP("RTMP"),
        LOCAL_RECORDING("Local Recording"),
        SRT("SRT"),
    }

    @Serializable
    enum class AudioSource(val displayName: String) {
        NONE("Off"),
        PHONE_MICROPHONE("Phone Mic"),
        BLUETOOTH_MICROPHONE("Bluetooth Mic"),
    }

    @Serializable
    enum class QualityPreset(val displayName: String) {
        LOW("Low"),
        STANDARD("Standard"),
        HIGH("High"),
        MAX("Max"),
        ARCHIVE("Archive"),
    }

    data class ReadinessItem(
        val id: String,
        val title: String,
        val detail: String,
        val severity: Severity,
    ) {
        enum class Severity { READY, WARNING, BLOCKING }
    }

    fun makePublishUrl(): Result<String> {
        if (transport != Transport.RTMP) return Result.failure(Exception("Only RTMP presets are supported by this publisher."))
        val trimmedKey = streamKey.trim()
        if (trimmedKey.isEmpty()) return Result.failure(Exception("The stream key is missing."))
        val trimmedHost = host.trim()
        if (trimmedHost.isEmpty()) return Result.failure(Exception("The RTMP host is invalid."))

        val base = trimmedHost.trimEnd('/')
        val parts = mutableListOf<String>()
        val trimmedApp = appPath.trim()
        if (trimmedApp.isNotEmpty()) parts.add(trimmedApp.trim('/'))
        parts.addAll(trimmedKey.split("/"))
        return Result.success("$base/${parts.joinToString("/")}")
    }

    fun makeSrtUrl(): Result<String> {
        val url = host.trim()
        if (url.isEmpty()) return Result.failure(Exception("SRT URL is required (e.g. srt://host:9000)."))
        if (!url.startsWith("srt://")) return Result.failure(Exception("SRT URL must start with srt://"))
        return Result.success(url)
    }

    fun readinessItems(): List<ReadinessItem> = buildList {
        when (transport) {
            Transport.RTMP -> {
                val urlResult = makePublishUrl()
                val url = urlResult.getOrNull()
                val isPlaceholder = url?.contains("example.com") == true || streamKey.trim() == "replace-me"
                add(ReadinessItem(
                    id = "rtmp-url",
                    title = "RTMP destination",
                    detail = when {
                        urlResult.isFailure -> urlResult.exceptionOrNull()!!.message ?: "Invalid URL"
                        isPlaceholder -> "Replace the default ingest URL and stream key."
                        else -> url!!
                    },
                    severity = if (urlResult.isFailure || isPlaceholder) ReadinessItem.Severity.BLOCKING else ReadinessItem.Severity.READY,
                ))
            }
            Transport.LOCAL_RECORDING -> add(ReadinessItem(
                id = "local-recording",
                title = "Recording destination",
                detail = "Saves native DAT frames to this device with local encoding. Current SDK feed is $META_DAT_SOURCE_RESOLUTION.",
                severity = ReadinessItem.Severity.READY,
            ))
            Transport.SRT -> {
                val urlResult = makeSrtUrl()
                add(ReadinessItem(
                    id = "srt-url",
                    title = "SRT destination",
                    detail = urlResult.getOrNull() ?: (urlResult.exceptionOrNull()?.message ?: "Invalid SRT URL"),
                    severity = if (urlResult.isFailure) ReadinessItem.Severity.BLOCKING else ReadinessItem.Severity.READY,
                ))
            }
        }

        add(ReadinessItem(
            id = "resolution",
            title = "Resolution",
            detail = META_DAT_SOURCE_RESOLUTION,
            severity = ReadinessItem.Severity.READY,
        ))

        add(ReadinessItem(
            id = "bitrate",
            title = "Bitrate",
            detail = "$targetBitrateKbps kbps",
            severity = when {
                targetBitrateKbps < 1000 -> ReadinessItem.Severity.WARNING
                transport == Transport.RTMP && targetBitrateKbps > 12_000 -> ReadinessItem.Severity.WARNING
                transport == Transport.LOCAL_RECORDING && targetBitrateKbps > 30_000 -> ReadinessItem.Severity.WARNING
                else -> ReadinessItem.Severity.READY
            },
        ))

        add(ReadinessItem(
            id = "fps",
            title = "Frame rate",
            detail = "$targetFPS fps",
            severity = if (targetFPS < 24 || targetFPS > 30) ReadinessItem.Severity.WARNING else ReadinessItem.Severity.READY,
        ))

        add(ReadinessItem(
            id = "keyframe",
            title = "Keyframe interval",
            detail = "${targetKeyframeIntervalSeconds}s",
            severity = if (targetKeyframeIntervalSeconds < 1 || targetKeyframeIntervalSeconds > 4)
                ReadinessItem.Severity.WARNING else ReadinessItem.Severity.READY,
        ))

        add(ReadinessItem(
            id = "audio",
            title = "Audio",
            detail = if (isAudioMuted) "${audioSource.displayName}, muted" else audioSource.displayName,
            severity = if (audioSource == AudioSource.NONE || isAudioMuted) ReadinessItem.Severity.WARNING else ReadinessItem.Severity.READY,
        ))
    }

    val hasBlockingReadinessIssues: Boolean get() = readinessItems().any { it.severity == ReadinessItem.Severity.BLOCKING }

    val resolvedQualityPreset: QualityPreset get() = qualityPreset ?: QualityPreset.STANDARD

    fun resolvingQualityPreset(preset: QualityPreset): StreamPreset = copy(
        targetResolution = META_DAT_SOURCE_RESOLUTION,
        qualityPreset = preset,
        targetBitrateKbps = bitrateKbps(preset, transport, META_DAT_SOURCE_RESOLUTION, targetFPS),
    )

    fun resolvingCurrentQualityPreset(): StreamPreset = resolvingQualityPreset(resolvedQualityPreset)

    fun normalizedSourceResolution(): StreamPreset = copy(targetResolution = META_DAT_SOURCE_RESOLUTION)

    companion object {
        const val META_DAT_SOURCE_RESOLUTION = "720x1280"

        fun bitrateKbps(qualityPreset: QualityPreset, transport: Transport, resolution: String, fps: Int): Int {
            val (w, h) = parseResolution(resolution) ?: (720 to 1280)
            val pixels = w * h
            val highFps = fps > 30
            return when (transport) {
                Transport.RTMP -> if (pixels >= 3_000_000) {
                    when (qualityPreset) {
                        QualityPreset.LOW -> if (highFps) 5000 else 4000
                        QualityPreset.STANDARD -> if (highFps) 7000 else 6000
                        QualityPreset.HIGH -> if (highFps) 9000 else 8000
                        QualityPreset.MAX -> if (highFps) 11_000 else 10_000
                        QualityPreset.ARCHIVE -> 12_000
                    }
                } else {
                    when (qualityPreset) {
                        QualityPreset.LOW -> if (highFps) 4000 else 3000
                        QualityPreset.STANDARD -> if (highFps) 7000 else 6000
                        QualityPreset.HIGH -> if (highFps) 9000 else 8000
                        QualityPreset.MAX -> if (highFps) 11_000 else 10_000
                        QualityPreset.ARCHIVE -> 12_000
                    }
                }
                Transport.LOCAL_RECORDING -> when {
                    pixels >= 6_000_000 -> when (qualityPreset) {
                        QualityPreset.LOW -> 8_000; QualityPreset.STANDARD -> 12_000
                        QualityPreset.HIGH -> 16_000; QualityPreset.MAX -> 18_000; QualityPreset.ARCHIVE -> 30_000
                    }
                    pixels >= 3_000_000 -> when (qualityPreset) {
                        QualityPreset.LOW -> 8_000; QualityPreset.STANDARD -> 12_000
                        QualityPreset.HIGH -> 16_000; QualityPreset.MAX -> 20_000; QualityPreset.ARCHIVE -> 30_000
                    }
                    else -> when (qualityPreset) {
                        QualityPreset.LOW -> 6_000; QualityPreset.STANDARD -> 10_000
                        QualityPreset.HIGH -> 14_000; QualityPreset.MAX -> 18_000; QualityPreset.ARCHIVE -> 30_000
                    }
                }
                Transport.SRT -> if (pixels >= 3_000_000) {
                    when (qualityPreset) {
                        QualityPreset.LOW -> if (highFps) 5000 else 4000
                        QualityPreset.STANDARD -> if (highFps) 7000 else 6000
                        QualityPreset.HIGH -> if (highFps) 9000 else 8000
                        QualityPreset.MAX -> if (highFps) 11_000 else 10_000
                        QualityPreset.ARCHIVE -> 12_000
                    }
                } else {
                    when (qualityPreset) {
                        QualityPreset.LOW -> if (highFps) 4000 else 3000
                        QualityPreset.STANDARD -> if (highFps) 7000 else 6000
                        QualityPreset.HIGH -> if (highFps) 9000 else 8000
                        QualityPreset.MAX -> if (highFps) 11_000 else 10_000
                        QualityPreset.ARCHIVE -> 12_000
                    }
                }
            }
        }

        private fun parseResolution(raw: String): Pair<Int, Int>? {
            val parts = raw.lowercase().split("x")
            if (parts.size != 2) return null
            val w = parts[0].toIntOrNull() ?: return null
            val h = parts[1].toIntOrNull() ?: return null
            if (w <= 0 || h <= 0) return null
            return w to h
        }

        val default = StreamPreset(
            id = UUID.randomUUID().toString(),
            name = "Primary RTMP",
            transport = Transport.RTMP,
            audioSource = AudioSource.PHONE_MICROPHONE,
            host = "rtmp://example.com/live",
            appPath = "pov",
            streamKey = "replace-me",
            streamingService = StreamingService.CUSTOM,
            isStreamKeyLocked = true,
            targetResolution = META_DAT_SOURCE_RESOLUTION,
            targetBitrateKbps = 4000,
            targetFPS = 30,
            targetKeyframeIntervalSeconds = 2,
            qualityPreset = QualityPreset.STANDARD,
        )
    }
}
