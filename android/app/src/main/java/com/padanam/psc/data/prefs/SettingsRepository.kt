package com.padanam.psc.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.padanam.psc.BuildConfig
import com.padanam.psc.data.model.Lang
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore by preferencesDataStore(name = "padanam_settings")

data class AppSettings(
    val name: String = "Maya",
    val language: String = Lang.EN,
    val dailyGoal: Int = 30,
    val reminders: Boolean = true,
    val geminiKey: String = BuildConfig.GEMINI_API_KEY,
    val geminiModel: String = BuildConfig.GEMINI_MODEL,
    val neonUrl: String = BuildConfig.NEON_CONNECTION_STRING,
    val neonEndpoint: String = BuildConfig.NEON_HTTP_ENDPOINT,
    val autoSync: Boolean = true,
    val lastSyncAt: Long = 0L,
    val lastSyncMessage: String = "",
    val lastSavedSetId: String? = null,
    val lastSavedAt: Long = 0L,
    val deletedSetIds: Set<String> = emptySet()
) {
    val aiConfigured: Boolean get() = geminiKey.isNotBlank()
    val neonConfigured: Boolean get() = neonUrl.isNotBlank()
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val name = stringPreferencesKey("name")
        val language = stringPreferencesKey("language")
        val dailyGoal = intPreferencesKey("daily_goal")
        val reminders = booleanPreferencesKey("reminders")
        val geminiKey = stringPreferencesKey("gemini_key")
        val geminiModel = stringPreferencesKey("gemini_model")
        val neonUrl = stringPreferencesKey("neon_url")
        val neonEndpoint = stringPreferencesKey("neon_endpoint")
        val autoSync = booleanPreferencesKey("auto_sync")
        val lastSyncAt = longPreferencesKey("last_sync_at")
        val lastSyncMessage = stringPreferencesKey("last_sync_message")
        val lastSavedSetId = stringPreferencesKey("last_saved_set_id")
        val lastSavedAt = longPreferencesKey("last_saved_at")
        val deletedSetIds = stringSetPreferencesKey("deleted_set_ids")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs ->
            AppSettings(
                name = prefs[Keys.name] ?: "Maya",
                language = Lang.normalize(prefs[Keys.language]),
                dailyGoal = (prefs[Keys.dailyGoal] ?: 30).coerceIn(5, 200),
                reminders = prefs[Keys.reminders] ?: true,
                geminiKey = prefs[Keys.geminiKey] ?: BuildConfig.GEMINI_API_KEY,
                geminiModel = prefs[Keys.geminiModel] ?: BuildConfig.GEMINI_MODEL,
                neonUrl = prefs[Keys.neonUrl] ?: BuildConfig.NEON_CONNECTION_STRING,
                neonEndpoint = prefs[Keys.neonEndpoint] ?: BuildConfig.NEON_HTTP_ENDPOINT,
                autoSync = prefs[Keys.autoSync] ?: true,
                lastSyncAt = prefs[Keys.lastSyncAt] ?: 0L,
                lastSyncMessage = prefs[Keys.lastSyncMessage] ?: "",
                lastSavedSetId = prefs[Keys.lastSavedSetId],
                lastSavedAt = prefs[Keys.lastSavedAt] ?: 0L,
                deletedSetIds = prefs[Keys.deletedSetIds].orEmpty()
            )
        }

    suspend fun current(): AppSettings = settings.first()

    suspend fun updateName(name: String) {
        context.settingsDataStore.edit { it[Keys.name] = name.trim().ifBlank { "Maya" } }
    }

    suspend fun updateLanguage(language: String) {
        context.settingsDataStore.edit { it[Keys.language] = Lang.normalize(language) }
    }

    suspend fun updateReminders(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.reminders] = enabled }
    }

    suspend fun updateDailyGoal(goal: Int) {
        context.settingsDataStore.edit { it[Keys.dailyGoal] = goal.coerceIn(5, 200) }
    }

    suspend fun saveProfile(
        name: String,
        language: String,
        dailyGoal: Int,
        reminders: Boolean
    ) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.name] = name.trim().ifBlank { "Maya" }
            prefs[Keys.language] = Lang.normalize(language)
            prefs[Keys.dailyGoal] = dailyGoal.coerceIn(5, 200)
            prefs[Keys.reminders] = reminders
        }
    }

    suspend fun saveAi(geminiKey: String, geminiModel: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.geminiKey] = geminiKey.trim()
            prefs[Keys.geminiModel] = geminiModel.trim().ifBlank { "gemini-2.5-flash" }
        }
    }

    suspend fun saveDatabase(neonUrl: String, neonEndpoint: String, autoSync: Boolean) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.neonUrl] = neonUrl.trim()
            prefs[Keys.neonEndpoint] = neonEndpoint.trim()
            prefs[Keys.autoSync] = autoSync
        }
    }

    suspend fun markSavedToLibrary(setId: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.lastSavedSetId] = setId
            prefs[Keys.lastSavedAt] = System.currentTimeMillis()
        }
    }

    suspend fun markDeletedSet(setId: String) {
        context.settingsDataStore.edit { prefs ->
            val current = prefs[Keys.deletedSetIds].orEmpty()
            prefs[Keys.deletedSetIds] = current + setId
        }
    }

    suspend fun clearDeletedSets() {
        context.settingsDataStore.edit { prefs -> prefs.remove(Keys.deletedSetIds) }
    }

    suspend fun markSynced(message: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.lastSyncAt] = System.currentTimeMillis()
            prefs[Keys.lastSyncMessage] = message
        }
    }

    suspend fun markSyncFailed(message: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.lastSyncMessage] = message
        }
    }
}
