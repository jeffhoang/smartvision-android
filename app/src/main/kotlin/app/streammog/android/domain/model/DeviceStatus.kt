package app.streammog.android.domain.model

data class DeviceStatus(
    val connectionState: ConnectionState,
    val batteryLevel: Int?,
    val sessionState: SessionState,
    val deviceName: String,
    val lastError: String?,
) {
    companion object {
        val initial = DeviceStatus(
            connectionState = ConnectionState.DISCONNECTED,
            batteryLevel = null,
            sessionState = SessionState.IDLE,
            deviceName = "No glasses connected",
            lastError = null,
        )
    }
}
