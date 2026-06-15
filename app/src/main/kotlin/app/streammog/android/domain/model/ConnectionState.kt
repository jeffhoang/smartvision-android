package app.streammog.android.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class ConnectionState(val displayName: String) {
    DISCONNECTED("Disconnected"),
    CONNECTING("Connecting"),
    CONNECTED("Connected"),
    UNSUPPORTED("Unsupported"),
    FAILED("Failed"),
}
