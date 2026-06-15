package app.streammog.android.domain.protocol

import android.net.Uri
import app.streammog.android.domain.model.DeviceStatus
import app.streammog.android.domain.model.PreviewSnapshot
import java.nio.ByteBuffer

interface GlassesSessionClient {
    // Callbacks (mirrors iOS delegate-style protocol)
    var statusDidChange: ((DeviceStatus) -> Unit)?
    var previewDidUpdate: ((PreviewSnapshot) -> Unit)?
    var fpsDidUpdate: ((Double) -> Unit)?
    // ByteBuffer replaces CMSampleBuffer; suspend lambda matches iOS async closure
    var videoBufferDidOutput: (suspend (ByteBuffer) -> Unit)?

    suspend fun connect()
    // Called from MainActivity.onNewIntent when Meta AI returns via deep link
    suspend fun handleDeepLink(uri: Uri)
    suspend fun openDATGlassesAppUpdate()
    suspend fun openFirmwareUpdate()
    suspend fun resetRegistration()
    suspend fun startSession()
    suspend fun pauseSession()
    suspend fun stopSession()
}
