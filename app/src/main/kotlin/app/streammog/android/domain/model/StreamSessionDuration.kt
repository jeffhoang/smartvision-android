package app.streammog.android.domain.model

data class StreamSessionDuration(
    val startedAtMs: Long?,
    val elapsedSeconds: Int,
) {
    val isRunning: Boolean get() = startedAtMs != null

    val hasPassedThreeMinuteLimit: Boolean get() = elapsedSeconds >= THREE_MINUTE_THRESHOLD_SECONDS

    val displayText: String get() = format(elapsedSeconds)

    companion object {
        const val THREE_MINUTE_THRESHOLD_SECONDS = 180

        val notStreaming = StreamSessionDuration(startedAtMs = null, elapsedSeconds = 0)

        fun running(startedAtMs: Long, nowMs: Long = System.currentTimeMillis()): StreamSessionDuration =
            StreamSessionDuration(
                startedAtMs = startedAtMs,
                elapsedSeconds = maxOf(0, ((nowMs - startedAtMs) / 1000).toInt()),
            )

        fun format(seconds: Int): String {
            val hours = seconds / 3600
            val minutes = (seconds % 3600) / 60
            val secs = seconds % 60
            return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, secs)
            else "%d:%02d".format(minutes, secs)
        }
    }
}
