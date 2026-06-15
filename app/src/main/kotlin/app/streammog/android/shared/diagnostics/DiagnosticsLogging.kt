package app.streammog.android.shared.diagnostics

interface DiagnosticsLogging {
    fun log(message: String, category: DiagnosticsEntry.Category = DiagnosticsEntry.Category.app)
}
