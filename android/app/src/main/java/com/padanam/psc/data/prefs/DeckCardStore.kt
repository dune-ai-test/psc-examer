package com.padanam.psc.data.prefs

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.padanam.psc.data.model.RemoteDeckCard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.deckCardDataStore by preferencesDataStore(
    name = "padanam_deck_cards",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

/**
 * Cached scanned deck cards from the optional second database. The cards are
 * shared study content, so this cache is not part of the account workspace;
 * learned state still syncs separately through `deck_progress`.
 */
class DeckCardStore(private val context: Context) {

    private val key = stringPreferencesKey("remote_deck_cards_json")
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val cards: Flow<List<RemoteDeckCard>> = context.deckCardDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            val raw = prefs[key] ?: return@map emptyList()
            runCatching {
                json.decodeFromString<List<RemoteDeckCard>>(raw)
            }.getOrDefault(emptyList())
        }

    suspend fun save(cards: List<RemoteDeckCard>) {
        context.deckCardDataStore.edit { prefs ->
            prefs[key] = json.encodeToString(ListSerializer(RemoteDeckCard.serializer()), cards)
        }
    }

    suspend fun clear() {
        context.deckCardDataStore.edit { prefs -> prefs.remove(key) }
    }
}
