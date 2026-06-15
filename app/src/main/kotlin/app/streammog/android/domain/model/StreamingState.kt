package app.streammog.android.domain.model

sealed class StreamingState {
    object Idle : StreamingState()
    object ConnectingGlasses : StreamingState()
    object Ready : StreamingState()
    object StartingStream : StreamingState()
    data class Streaming(val startedAtMs: Long) : StreamingState()
    object Recovering : StreamingState()
    data class Failed(val message: String) : StreamingState()

    val title: String
        get() = when (this) {
            is Idle -> "Idle"
            is ConnectingGlasses -> "Connecting Glasses"
            is Ready -> "Ready"
            is StartingStream -> "Starting Stream"
            is Streaming -> "Streaming"
            is Recovering -> "Recovering"
            is Failed -> "Failed"
        }
}
