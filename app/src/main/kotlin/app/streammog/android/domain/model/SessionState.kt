package app.streammog.android.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class SessionState(val displayName: String) {
    IDLE("Idle"),
    PREPARING("Preparing"),
    RUNNING("Running"),
    PAUSED("Paused"),
    STOPPED("Stopped"),
    FAILED("Failed"),
}
