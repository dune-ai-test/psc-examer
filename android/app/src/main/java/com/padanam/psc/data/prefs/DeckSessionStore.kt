package com.padanam.psc.data.prefs

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.padanam.psc.data.model.DeckSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.deckDataStore by preferencesDataStore(
    name = "padanam_deck_session",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

/**
 * The current deck round deliberately stays on this phone. Only the learned
 * card state syncs, so opening the same topic on the web does not jump the
 * card order under the learner's thumb.
 */
class DeckSessionStore(private val context: Context) {

    private val key = stringPreferencesKey("active_deck_session_json")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val session: Flow<DeckSession?> = context.deckDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            val raw = prefs[key] ?: return@map null
            runCatching { json.decodeFromString<DeckSession>(raw) }.getOrNull()
        }

    suspend fun save(session: DeckSession) {
        context.deckDataStore.edit { prefs ->
            prefs[key] = json.encodeToString(DeckSession.serializer(), session)
        }
    }

    suspend fun clear() {
        context.deckDataStore.edit { prefs -> prefs.remove(key) }
    }
}
