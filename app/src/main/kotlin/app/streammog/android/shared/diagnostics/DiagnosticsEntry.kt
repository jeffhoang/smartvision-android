package app.streammog.android.shared.diagnostics

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class DiagnosticsEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestampMs: Long = System.currentTimeMillis(),
    val category: Category,
    val message: String,
) {
    @Serializable
    enum class Category { app, glasses, stream, error }
}
