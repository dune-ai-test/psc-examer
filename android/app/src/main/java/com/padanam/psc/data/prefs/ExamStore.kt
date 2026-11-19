package com.padanam.psc.data.prefs

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.padanam.psc.data.model.ActiveExam
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.examDataStore by preferencesDataStore(
    name = "padanam_active_exam",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

/**
 * Persists the single in-progress exam. The timer is wall-clock based, so it
 * keeps running even when the app is closed.
 */
class ExamStore(private val context: Context) {

    private val key = stringPreferencesKey("active_exam_json")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val activeExam: Flow<ActiveExam?> = context.examDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            val raw = prefs[key] ?: return@map null
            runCatching { json.decodeFromString<ActiveExam>(raw) }.getOrNull()
        }

    suspend fun save(exam: ActiveExam) {
        context.examDataStore.edit { prefs ->
            prefs[key] = json.encodeToString(ActiveExam.serializer(), exam)
        }
    }

    suspend fun clear() {
        context.examDataStore.edit { prefs -> prefs.remove(key) }
    }
}
