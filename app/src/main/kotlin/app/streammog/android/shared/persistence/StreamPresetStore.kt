package app.streammog.android.shared.persistence

import android.content.Context
import app.streammog.android.domain.model.StreamDestination
import app.streammog.android.domain.model.StreamPreset
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class StreamPresetStore(context: Context) {
    private val prefs = context.getSharedPreferences("streammog_presets", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    fun loadSelectedPreset(): StreamPreset {
        val data = prefs.getString(KEY_SELECTED_PRESET, null)
            ?: return loadCreatorDefaultPreset() ?: StreamPreset.default
        var preset = try {
            json.decodeFromString<StreamPreset>(data)
        } catch (_: Exception) {
            return loadCreatorDefaultPreset() ?: StreamPreset.default
        }
        // Migrate: ensure no NONE audio slips through
        if (preset.audioSource == StreamPreset.AudioSource.NONE) {
            preset = preset.copy(audioSource = StreamPreset.AudioSource.PHONE_MICROPHONE)
        }
        preset = preset.resolvingCurrentQualityPreset().normalizedSourceResolution()
        saveSelectedPreset(preset)
        return preset
    }

    fun saveSelectedPreset(preset: StreamPreset) {
        try {
            prefs.edit().putString(KEY_SELECTED_PRESET, json.encodeToString(preset)).apply()
        } catch (_: Exception) {}
    }

    fun loadSavedDestinations(maxCount: Int? = null): List<StreamDestination> {
        val data = prefs.getString(KEY_SAVED_DESTINATIONS, null) ?: return emptyList()
        val destinations = try {
            json.decodeFromString<List<StreamDestination>>(data)
        } catch (_: Exception) {
            return emptyList()
        }
        return trim(destinations, maxCount)
    }

    fun saveDestinations(destinations: List<StreamDestination>, maxCount: Int? = null) {
        try {
            val trimmed = trim(destinations, maxCount)
            prefs.edit().putString(KEY_SAVED_DESTINATIONS, json.encodeToString(trimmed)).apply()
        } catch (_: Exception) {}
    }

    fun resetSelectedPreset(): StreamPreset {
        prefs.edit().remove(KEY_SELECTED_PRESET).apply()
        return loadCreatorDefaultPreset() ?: StreamPreset.default
    }

    fun saveCreatorDefaultPreset(preset: StreamPreset) {
        try {
            prefs.edit().putString(KEY_CREATOR_DEFAULT_PRESET, json.encodeToString(preset)).apply()
        } catch (_: Exception) {}
    }

    fun loadCreatorDefaultPreset(): StreamPreset? {
        val data = prefs.getString(KEY_CREATOR_DEFAULT_PRESET, null) ?: return null
        return try {
            json.decodeFromString<StreamPreset>(data)
                .resolvingCurrentQualityPreset()
                .normalizedSourceResolution()
        } catch (_: Exception) {
            null
        }
    }

    fun clearCreatorDefaultPreset() {
        prefs.edit().remove(KEY_CREATOR_DEFAULT_PRESET).apply()
    }

    private fun trim(list: List<StreamDestination>, maxCount: Int?): List<StreamDestination> =
        if (maxCount == null || maxCount < 0) list else list.take(maxCount)

    companion object {
        private const val KEY_SELECTED_PRESET = "streamglassesapp.selectedPreset"
        private const val KEY_SAVED_DESTINATIONS = "streamglassesapp.savedDestinations"
        private const val KEY_CREATOR_DEFAULT_PRESET = "streamglassesapp.creatorDefaultPreset"
    }
}
