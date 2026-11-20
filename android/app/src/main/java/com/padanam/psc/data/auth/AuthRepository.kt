package com.padanam.psc.data.auth

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.authDataStore by preferencesDataStore(
    name = "padanam_auth",
    corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() }
)

@Serializable
data class AuthUser(
    val id: String = "",
    val username: String = "",
    val displayName: String = "",
    val role: String = "user"
) {
    val isAdmin: Boolean get() = role == "admin"
    val label: String get() = displayName.ifBlank { username }
}

@Serializable
data class AuthSession(
    val token: String = "",
    val user: AuthUser = AuthUser(),
    val expiresAt: Long = 0L,
    val serverUrl: String = ""
) {
    /** Stable identity for the account switcher (server plus account id). */
    val key: String get() = "${serverUrl.trim().trimEnd('/')}|${user.id}"

    fun expired(now: Long = System.currentTimeMillis()): Boolean =
        expiresAt > 0L && expiresAt <= now
}

/**
 * Persists every account that has been signed in on this phone. The web app
 * keeps one HMAC session token in localStorage; Android keeps a small list so
 * an administrator can sign a learner in and switch between them without
 * typing a password again. No database password or AI key is ever stored here.
 */
class AuthRepository(private val context: Context) {

    private object Keys {
        val accounts = stringPreferencesKey("accounts")
        val activeKey = stringPreferencesKey("active_key")

        /* Single-account keys from older APKs. They are read once and then
           kept in sync with the active account for a clean downgrade. */
        val token = stringPreferencesKey("token")
        val userId = stringPreferencesKey("user_id")
        val username = stringPreferencesKey("username")
        val displayName = stringPreferencesKey("display_name")
        val role = stringPreferencesKey("role")
        val expiresAt = longPreferencesKey("expires_at")
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val sessionsSerializer = ListSerializer(AuthSession.serializer())

    private fun decode(raw: String?): List<AuthSession> =
        if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            runCatching { json.decodeFromString(sessionsSerializer, raw) }.getOrElse { emptyList() }
        }

    private fun encode(sessions: List<AuthSession>): String =
        json.encodeToString(sessionsSerializer, sessions)

    private fun readSessions(prefs: Preferences): List<AuthSession> {
        val raw = prefs[Keys.accounts]
        if (raw != null) return decode(raw)

        /* Migrate the single session written by older APKs. */
        val token = prefs[Keys.token].orEmpty()
        if (token.isBlank()) return emptyList()
        return listOf(
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
        )
    }

    private fun activeOf(prefs: Preferences, sessions: List<AuthSession>): AuthSession? {
        if (sessions.isEmpty()) return null
        val key = prefs[Keys.activeKey].orEmpty()
        return sessions.firstOrNull { it.key == key } ?: sessions.first()
    }

    private fun writeLegacy(prefs: MutablePreferences, session: AuthSession) {
        prefs[Keys.token] = session.token
        prefs[Keys.userId] = session.user.id
        prefs[Keys.username] = session.user.username
        prefs[Keys.displayName] = session.user.displayName
        prefs[Keys.role] = session.user.role
        prefs[Keys.expiresAt] = session.expiresAt
    }

    /** All accounts saved on this phone, in the order they were added. */
    val sessions: Flow<List<AuthSession>> = context.authDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> readSessions(prefs) }

    /** The account whose workspace is currently open. */
    val session: Flow<AuthSession?> = context.authDataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs -> activeOf(prefs, readSessions(prefs)) }

    suspend fun list(): List<AuthSession> = sessions.first()

    suspend fun current(): AuthSession? = session.first()

    /**
     * Adds or refreshes one account and makes it active. An existing entry for
     * the same account keeps its place in the switcher list.
     */
    suspend fun save(
        token: String,
        user: AuthUser,
        expiresAt: Long,
        serverUrl: String = ""
    ): AuthSession {
        val normalizedServer = serverUrl.trim().trimEnd('/')
        var saved: AuthSession? = null
        context.authDataStore.edit { prefs ->
            val existing = readSessions(prefs)
            val inheritedServer = existing.firstOrNull { it.user.id == user.id }?.serverUrl.orEmpty()
            val session = AuthSession(
                token = token.trim(),
                user = user,
                expiresAt = expiresAt,
                serverUrl = normalizedServer.ifBlank { inheritedServer }
            )
            val index = existing.indexOfFirst { it.user.id == user.id }
            val updated = if (index >= 0) {
                existing.toMutableList().also { it[index] = session }
            } else {
                existing + session
            }
            prefs[Keys.accounts] = encode(updated)
            prefs[Keys.activeKey] = session.key
            writeLegacy(prefs, session)
            saved = session
        }
        return saved ?: AuthSession(token.trim(), user, expiresAt, normalizedServer)
    }

    /** Switches the active account without asking for a password again. */
    suspend fun setActive(key: String): AuthSession? {
        var chosen: AuthSession? = null
        context.authDataStore.edit { prefs ->
            val available = readSessions(prefs)
            val match = available.firstOrNull { it.key == key }
                ?: available.firstOrNull { it.user.id == key }
                ?: return@edit
            prefs[Keys.activeKey] = match.key
            writeLegacy(prefs, match)
            chosen = match
        }
        return chosen
    }

    /**
     * Removes one saved account. Returns the account that becomes active, or
     * null when the phone has no saved accounts left.
     */
    suspend fun remove(key: String): AuthSession? {
        var next: AuthSession? = null
        context.authDataStore.edit { prefs ->
            val remaining = readSessions(prefs)
                .filterNot { it.key == key || it.user.id == key }
            if (remaining.isEmpty()) {
                prefs.clear()
                return@edit
            }
            prefs[Keys.accounts] = encode(remaining)
            val activeKey = prefs[Keys.activeKey].orEmpty()
            val active = remaining.firstOrNull { it.key == activeKey } ?: remaining.first()
            prefs[Keys.activeKey] = active.key
            writeLegacy(prefs, active)
            next = active
        }
        return next
    }

    /** Drops saved logins whose token has already expired. */
    suspend fun pruneExpired(now: Long = System.currentTimeMillis()) {
        context.authDataStore.edit { prefs ->
            val available = readSessions(prefs)
            if (available.none { it.expired(now) }) return@edit
            val remaining = available.filterNot { it.expired(now) }
            if (remaining.isEmpty()) {
                prefs.clear()
                return@edit
            }
            prefs[Keys.accounts] = encode(remaining)
            val activeKey = prefs[Keys.activeKey].orEmpty()
            val active = remaining.firstOrNull { it.key == activeKey } ?: remaining.first()
            prefs[Keys.activeKey] = active.key
            writeLegacy(prefs, active)
        }
    }

    suspend fun clear() {
        context.authDataStore.edit { prefs -> prefs.clear() }
    }
}
