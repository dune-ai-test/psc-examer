package com.padanam.psc.data.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.authDataStore by preferencesDataStore(name = "padanam_auth")

data class AuthUser(
    val id: String = "",
    val username: String = "",
    val displayName: String = "",
    val role: String = "user"
) {
    val isAdmin: Boolean get() = role == "admin"
    val label: String get() = displayName.ifBlank { username }
}

data class AuthSession(
    val token: String,
    val user: AuthUser,
    val expiresAt: Long = 0L
) {
    fun expired(now: Long = System.currentTimeMillis()): Boolean =
        expiresAt > 0L && expiresAt <= now
}

/**
 * Persists the signed-in account. The web app keeps an HMAC session token in
 * localStorage; the Android app mirrors that in DataStore. No database
 * password or AI key is ever stored here.
 */
class AuthRepository(private val context: Context) {

    private object Keys {
        val token = stringPreferencesKey("token")
        val userId = stringPreferencesKey("user_id")
        val username = stringPreferencesKey("username")
        val displayName = stringPreferencesKey("display_name")
        val role = stringPreferencesKey("role")
        val expiresAt = longPreferencesKey("expires_at")
    }

    val session: Flow<AuthSession?> = context.authDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs ->
            val token = prefs[Keys.token].orEmpty()
            if (token.isBlank()) return@map null
            AuthSession(
                token = token,
                user = AuthUser(
                    id = prefs[Keys.userId].orEmpty(),
                    username = prefs[Keys.username].orEmpty(),
                    displayName = prefs[Keys.displayName].orEmpty(),
                    role = prefs[Keys.role].orEmpty().ifBlank { "user" }
                ),
                expiresAt = prefs[Keys.expiresAt] ?: 0L
            )
        }

    suspend fun current(): AuthSession? = session.first()

    suspend fun save(token: String, user: AuthUser, expiresAt: Long) {
        context.authDataStore.edit { prefs ->
            prefs[Keys.token] = token.trim()
            prefs[Keys.userId] = user.id
            prefs[Keys.username] = user.username
            prefs[Keys.displayName] = user.displayName
            prefs[Keys.role] = user.role
            prefs[Keys.expiresAt] = expiresAt
        }
    }

    suspend fun clear() {
        context.authDataStore.edit { prefs -> prefs.clear() }
    }
}
