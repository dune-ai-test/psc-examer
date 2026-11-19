package com.padanam.psc.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.padanam.psc.data.model.Lang
import com.padanam.psc.data.model.ThemeMode
import com.padanam.psc.data.model.ViewMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore by preferencesDataStore(name = "padanam_settings")

data class AppSettings(
    val name: String = "Maya",
    val target: String = "Kerala PSC",
    val examDate: String = "",
    val language: String = Lang.EN,
    val dailyGoal: Int = 30,
    val reminders: Boolean = true,
    val serverUrl: String = "",
    val autoSync: Boolean = true,
    val lastSyncAt: Long = 0L,
    val lastSyncMessage: String = "",
    val profileUpdatedAt: Long = 0L,
    val lastAccountId: String = "",
    val lastSavedSetId: String? = null,
    val lastSavedAt: Long = 0L,
    val themeMode: String = ThemeMode.SYSTEM,
    val viewMode: String = ViewMode.ADMIN,
    val offlineReadyAt: Long = 0L
) {
    val serverConfigured: Boolean get() = serverUrl.isNotBlank()
    val offlineReady: Boolean get() = offlineReadyAt > 0L
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val name = stringPreferencesKey("name")
        val target = stringPreferencesKey("target")
        val examDate = stringPreferencesKey("exam_date")
        val language = stringPreferencesKey("language")
        val dailyGoal = intPreferencesKey("daily_goal")
        val reminders = booleanPreferencesKey("reminders")
        val serverUrl = stringPreferencesKey("server_url")
        val autoSync = booleanPreferencesKey("auto_sync")
        val lastSyncAt = longPreferencesKey("last_sync_at")
        val lastSyncMessage = stringPreferencesKey("last_sync_message")
        val profileUpdatedAt = longPreferencesKey("profile_updated_at")
        val lastAccountId = stringPreferencesKey("last_account_id")
        val lastSavedSetId = stringPreferencesKey("last_saved_set_id")
        val lastSavedAt = longPreferencesKey("last_saved_at")
        val themeMode = stringPreferencesKey("theme_mode")
        val viewMode = stringPreferencesKey("view_mode")
        val offlineReadyAt = longPreferencesKey("offline_ready_at")

        /* Removed keys stay declared so old installs can be read without
           crashing and so the values can be erased on sign-out. */
        val legacyGeminiKey = stringPreferencesKey("gemini_key")
        val legacyNeonUrl = stringPreferencesKey("neon_url")
        val legacyNeonEndpoint = stringPreferencesKey("neon_endpoint")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs ->
            AppSettings(
                name = prefs[Keys.name] ?: "Maya",
                target = prefs[Keys.target].orEmpty().trim().ifBlank { "Kerala PSC" },
                examDate = prefs[Keys.examDate].orEmpty().trim(),
                language = Lang.normalize(prefs[Keys.language]),
                dailyGoal = (prefs[Keys.dailyGoal] ?: 30).coerceIn(5, 200),
                reminders = prefs[Keys.reminders] ?: true,
                serverUrl = prefs[Keys.serverUrl].orEmpty().trim().trimEnd('/'),
                autoSync = prefs[Keys.autoSync] ?: true,
                lastSyncAt = prefs[Keys.lastSyncAt] ?: 0L,
                lastSyncMessage = prefs[Keys.lastSyncMessage] ?: "",
                profileUpdatedAt = prefs[Keys.profileUpdatedAt] ?: 0L,
                lastAccountId = prefs[Keys.lastAccountId].orEmpty(),
                lastSavedSetId = prefs[Keys.lastSavedSetId],
                lastSavedAt = prefs[Keys.lastSavedAt] ?: 0L,
                themeMode = ThemeMode.normalize(prefs[Keys.themeMode]),
                viewMode = ViewMode.normalize(prefs[Keys.viewMode]),
                offlineReadyAt = prefs[Keys.offlineReadyAt] ?: 0L
            )
        }

    suspend fun current(): AppSettings = settings.first()

    suspend fun updateServerUrl(url: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.serverUrl] = url.trim().trimEnd('/')
        }
    }

    suspend fun updateName(name: String) {
        context.settingsDataStore.edit {
            it[Keys.name] = name.trim().ifBlank { "Maya" }
            it[Keys.profileUpdatedAt] = System.currentTimeMillis()
        }
    }

    suspend fun updateLanguage(language: String) {
        context.settingsDataStore.edit {
            it[Keys.language] = Lang.normalize(language)
            it[Keys.profileUpdatedAt] = System.currentTimeMillis()
        }
    }

    suspend fun updateReminders(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.reminders] = enabled }
    }

    suspend fun updateDailyGoal(goal: Int) {
        context.settingsDataStore.edit {
            it[Keys.dailyGoal] = goal.coerceIn(5, 200)
            it[Keys.profileUpdatedAt] = System.currentTimeMillis()
        }
    }

    suspend fun updateAutoSync(enabled: Boolean) {
        context.settingsDataStore.edit { it[Keys.autoSync] = enabled }
    }

    suspend fun updateThemeMode(mode: String) {
        context.settingsDataStore.edit { it[Keys.themeMode] = ThemeMode.normalize(mode) }
    }

    suspend fun updateViewMode(mode: String) {
        context.settingsDataStore.edit { it[Keys.viewMode] = ViewMode.normalize(mode) }
    }

    /** Marks the current Room library as a complete, usable offline copy. */
    suspend fun markOfflineReady() {
        context.settingsDataStore.edit { it[Keys.offlineReadyAt] = System.currentTimeMillis() }
    }

    /** Remembers which account owns the offline cache in Room. */
    suspend fun setAccountId(id: String) {
        context.settingsDataStore.edit { it[Keys.lastAccountId] = id.trim() }
    }

    /** Clears the previous learner's study preferences when accounts switch. */
    suspend fun resetProfile() {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.name] = "Maya"
            prefs[Keys.target] = "Kerala PSC"
            prefs.remove(Keys.examDate)
            prefs[Keys.language] = Lang.EN
            prefs[Keys.dailyGoal] = 30
            prefs[Keys.reminders] = true
            prefs[Keys.viewMode] = ViewMode.ADMIN
            prefs.remove(Keys.profileUpdatedAt)
            prefs.remove(Keys.offlineReadyAt)
        }
    }

    suspend fun saveProfile(
        name: String,
        target: String,
        examDate: String,
        language: String,
        dailyGoal: Int,
        reminders: Boolean
    ) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.name] = name.trim().ifBlank { "Maya" }
            prefs[Keys.target] = target.trim().take(70).ifBlank { "Kerala PSC" }
            prefs[Keys.examDate] = examDate.trim().take(10)
            prefs[Keys.language] = Lang.normalize(language)
            prefs[Keys.dailyGoal] = dailyGoal.coerceIn(5, 200)
            prefs[Keys.reminders] = reminders
            prefs[Keys.profileUpdatedAt] = System.currentTimeMillis()
        }
    }

    /** Applies a newer profile pulled from the shared web workspace. */
    suspend fun applyRemoteProfile(
        name: String?,
        target: String?,
        examDate: String?,
        language: String?,
        dailyGoal: Int?,
        updatedAtMs: Long
    ) {
        if (updatedAtMs <= 0L) return
        context.settingsDataStore.edit { prefs ->
            if (!name.isNullOrBlank()) prefs[Keys.name] = name.trim().take(40)
            if (!target.isNullOrBlank()) prefs[Keys.target] = target.trim().take(70)
            if (examDate != null) prefs[Keys.examDate] = examDate.trim().take(10)
            if (language != null) prefs[Keys.language] = Lang.normalize(language)
            if (dailyGoal != null) prefs[Keys.dailyGoal] = dailyGoal.coerceIn(5, 500)
            prefs[Keys.profileUpdatedAt] = updatedAtMs
        }
    }

    suspend fun markSavedToLibrary(setId: String) {
        context.settingsDataStore.edit { prefs ->
            prefs[Keys.lastSavedSetId] = setId
            prefs[Keys.lastSavedAt] = System.currentTimeMillis()
        }
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

    /** Removes credentials that older APKs cached in app preferences. */
    suspend fun clearLegacySecrets() {
        context.settingsDataStore.edit { prefs ->
            prefs.remove(Keys.legacyGeminiKey)
            prefs.remove(Keys.legacyNeonUrl)
            prefs.remove(Keys.legacyNeonEndpoint)
        }
    }

    suspend fun clearSyncStatus() {
        context.settingsDataStore.edit { prefs ->
            prefs.remove(Keys.lastSyncAt)
            prefs.remove(Keys.lastSyncMessage)
            prefs.remove(Keys.lastSavedSetId)
            prefs.remove(Keys.lastSavedAt)
            prefs.remove(Keys.offlineReadyAt)
            prefs[Keys.viewMode] = ViewMode.ADMIN
        }
    }
}
