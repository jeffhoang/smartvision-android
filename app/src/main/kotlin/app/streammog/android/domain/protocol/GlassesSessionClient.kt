package app.streammog.android.domain.protocol

import android.net.Uri
import app.streammog.android.domain.model.DeviceStatus
import app.streammog.android.domain.model.PreviewSnapshot
import app.streammog.android.domain.model.VideoFrameData

interface GlassesSessionClient {
    var statusDidChange: ((DeviceStatus) -> Unit)?
    var previewDidUpdate: ((PreviewSnapshot) -> Unit)?
    var fpsDidUpdate: ((Double) -> Unit)?
    var videoBufferDidOutput: (suspend (VideoFrameData) -> Unit)?

    suspend fun connect()
    suspend fun handleDeepLink(uri: Uri)
    suspend fun openDATGlassesAppUpdate()
    suspend fun openFirmwareUpdate()
    suspend fun resetRegistration()
    suspend fun startSession()
    suspend fun pauseSession()
    suspend fun stopSession()
}
