package app.streammog.android.domain.model

data class StreamPreflightCheck(
    val id: String,
    val title: String,
    val detail: String,
    val status: Status,
) {
    enum class Status { PASSED, WARNING, FAILED }
}

data class StreamPreflightReport(
    val checks: List<StreamPreflightCheck>,
) {
    val hasFailures: Boolean
        get() = checks.any { it.status == StreamPreflightCheck.Status.FAILED }

    val summary: String
        get() {
            val failures = checks.count { it.status == StreamPreflightCheck.Status.FAILED }
            val warnings = checks.count { it.status == StreamPreflightCheck.Status.WARNING }
            return when {
                failures > 0 -> "$failures failed, $warnings warnings"
                warnings > 0 -> "Ready with $warnings warnings"
                else -> "Ready"
            }
        }

    companion object {
        val empty = StreamPreflightReport(checks = emptyList())
    }
}
