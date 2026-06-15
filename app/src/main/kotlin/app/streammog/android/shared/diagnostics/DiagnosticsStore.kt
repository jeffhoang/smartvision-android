package app.streammog.android.shared.diagnostics

import android.content.Context
import app.streammog.android.app.AppBrand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class DiagnosticsStore(private val context: Context) : DiagnosticsLogging {
    private val _entries = MutableStateFlow<List<DiagnosticsEntry>>(emptyList())
    val entries: StateFlow<List<DiagnosticsEntry>> = _entries.asStateFlow()

    private val prefs = context.getSharedPreferences("streammog_diagnostics", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var persistJob: Job? = null

    init {
        _entries.value = loadEntries()
    }

    override fun log(message: String, category: DiagnosticsEntry.Category) {
        val entry = DiagnosticsEntry(
            id = UUID.randomUUID().toString(),
            timestampMs = System.currentTimeMillis(),
            category = category,
            message = message,
        )
        val updated = (listOf(entry) + _entries.value).take(MAX_ENTRIES)
        _entries.value = updated
        schedulePersist()
    }

    fun clear() {
        _entries.value = emptyList()
        persistJob?.cancel()
        persist()
    }

    fun exportDiagnosticsBundle(): File? {
        val lines = _entries.value.asReversed().map { entry ->
            val ts = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
                .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                .format(java.util.Date(entry.timestampMs))
            "$ts [${entry.category}] ${entry.message}"
        }
        val payload = buildString {
            appendLine(AppBrand.DIAGNOSTICS_TITLE)
            appendLine("Created: ${System.currentTimeMillis()}")
            appendLine()
            lines.forEach { appendLine(it) }
        }
        return try {
            val dir = File(context.cacheDir, AppBrand.DIAGNOSTICS_DIRECTORY_NAME).also { it.mkdirs() }
            val file = File(dir, "${AppBrand.DIAGNOSTICS_FILENAME_PREFIX}-${System.currentTimeMillis()}.txt")
            file.writeText(payload)
            file
        } catch (_: Exception) {
            null
        }
    }

    private fun schedulePersist() {
        persistJob?.cancel()
        persistJob = scope.launch {
            delay(1_000)
            persist()
        }
    }

    private fun persist() {
        try {
            val data = json.encodeToString(_entries.value)
            prefs.edit().putString(STORAGE_KEY, data).apply()
        } catch (_: Exception) {}
    }

    private fun loadEntries(): List<DiagnosticsEntry> = try {
        val data = prefs.getString(STORAGE_KEY, null) ?: return emptyList()
        json.decodeFromString(data)
    } catch (_: Exception) {
        emptyList()
    }

    companion object {
        private const val STORAGE_KEY = "streamglassesapp.diagnostics.entries"
        private const val MAX_ENTRIES = 200
    }
}
