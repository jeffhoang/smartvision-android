package app.streammog.android.shared.persistence

import android.content.Context
import app.streammog.android.domain.model.StreamSessionRecord
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SessionHistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("streammog_history", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun loadEntries(maxCount: Int? = null): List<StreamSessionRecord> {
        val data = prefs.getString(KEY_ENTRIES, null) ?: return emptyList()
        val entries = try {
            json.decodeFromString<List<StreamSessionRecord>>(data)
        } catch (_: Exception) {
            return emptyList()
        }
        return trim(entries, maxCount)
    }

    fun append(entry: StreamSessionRecord, maxCount: Int? = null): List<StreamSessionRecord> {
        val entries = (listOf(entry) + loadEntries(maxCount = null))
        val trimmed = trim(entries, maxCount)
        save(trimmed)
        return trimmed
    }

    fun clear() {
        prefs.edit().remove(KEY_ENTRIES).apply()
    }

    private fun save(entries: List<StreamSessionRecord>) {
        try {
            prefs.edit().putString(KEY_ENTRIES, json.encodeToString(entries)).apply()
        } catch (_: Exception) {}
    }

    private fun trim(entries: List<StreamSessionRecord>, maxCount: Int?): List<StreamSessionRecord> =
        if (maxCount == null || maxCount < 0) entries else entries.take(maxCount)

    companion object {
        private const val KEY_ENTRIES = "streamglassesapp.sessionHistory.entries"
    }
}
