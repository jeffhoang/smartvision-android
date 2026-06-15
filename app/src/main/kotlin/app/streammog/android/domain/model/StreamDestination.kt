package app.streammog.android.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class StreamDestination(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var transport: StreamPreset.Transport,
    var host: String,
    var appPath: String,
    var streamKey: String,
    var streamingService: StreamPreset.StreamingService? = null,
) {
    constructor(preset: StreamPreset) : this(
        name = preset.name,
        transport = preset.transport,
        host = preset.host,
        appPath = preset.appPath,
        streamKey = preset.streamKey,
        streamingService = preset.streamingService,
    )

    fun applying(to: StreamPreset): StreamPreset = to.copy(
        name = name,
        transport = transport,
        host = host,
        appPath = appPath,
        streamKey = streamKey,
        streamingService = streamingService,
    )
}
