package com.padanam.psc.data.remote

import com.padanam.psc.data.auth.AuthUser
import com.padanam.psc.data.remote.Network.client
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.time.Instant
import java.time.OffsetDateTime

class ServerException(
    message: String,
    val statusCode: Int = 0,
    val expired: Boolean = false,
    cause: Throwable? = null
) : Exception(message, cause)

data class LoginResult(
    val token: String,
    val user: AuthUser,
    val expiresAt: Long
)

data class AdminUser(
    val id: String,
    val username: String,
    val displayName: String,
    val role: String,
    val createdAt: String = "",
    val setIds: List<String> = emptyList()
) {
    val label: String get() = displayName.ifBlank { username }
}

data class AdminSet(
    val id: String,
    val title: String,
    val subject: String,
    val questions: Int
)

data class AdminSnapshot(
    val users: List<AdminUser>,
    val sets: List<AdminSet>
)

data class RemoteTombstone(
    val kind: String,
    val id: String,
    val deletedMs: Long
)

data class RemoteSnapshot(
    val sets: List<JsonObject>,
    val attempts: List<JsonObject>,
    val profile: JsonObject?,
    val profileUpdatedMs: Long,
    val attemptsClearedMs: Long,
    val epochMs: Long,
    val tombstones: List<RemoteTombstone>
)

data class PushResult(
    val sets: Int,
    val attempts: Int
)

/**
 * Client for the deployed Padanam Cloudflare Pages functions. The APK carries
 * no Neon connection string and no AI key: every privileged call goes through
 * the same account-scoped API the web app uses.
 */
class ServerApi(
    private val client: OkHttpClient = Network.client
) {

    suspend fun login(baseUrl: String, username: String, password: String): LoginResult =
        withContext(Dispatchers.IO) {
            val root = post(baseUrl, "/api/auth", token = null, body = buildJsonObject {
                put("action", "login")
                put("username", username.trim().lowercase())
                put("password", password)
            })
            val token = root.string("token")
            val user = (root["user"] as? JsonObject)?.toAuthUser()
                ?: throw ServerException("The server did not return an account.")
            if (token.isBlank()) throw ServerException("The server did not return a session.")
            LoginResult(
                token = token,
                user = user,
                expiresAt = parseDate(root.string("expiresAt")) ?: 0L
            )
        }

    suspend fun session(baseUrl: String, token: String): AuthUser =
        withContext(Dispatchers.IO) {
            val root = post(baseUrl, "/api/auth", token, buildJsonObject {
                put("action", "session")
            })
            (root["user"] as? JsonObject)?.toAuthUser()
                ?: throw ServerException("The session did not include an account.", 401, expired = true)
        }

    suspend fun logout(baseUrl: String, token: String) = withContext(Dispatchers.IO) {
        runCatching {
            post(baseUrl, "/api/auth", token, buildJsonObject { put("action", "logout") })
        }
        Unit
    }

    suspend fun adminSnapshot(baseUrl: String, token: String): AdminSnapshot =
        withContext(Dispatchers.IO) {
            val root = post(baseUrl, "/api/admin", token, buildJsonObject {
                put("action", "users")
            })
            val users = root.array("users").mapNotNull { element ->
                (element as? JsonObject)?.toAdminUser()
            }
            val sets = root.array("sets").mapNotNull { element ->
                (element as? JsonObject)?.let { item ->
                    AdminSet(
                        id = item.string("id"),
                        title = item.string("title").ifBlank { "Untitled set" },
                        subject = item.string("subject").ifBlank { "General" },
                        questions = item["questions"]?.let { numberOrNull(it) } ?: 0
                    )
                }
            }.filter { it.id.isNotBlank() }
            AdminSnapshot(users = users, sets = sets)
        }

    suspend fun createUser(
        baseUrl: String,
        token: String,
        username: String,
        displayName: String,
        password: String
    ): AuthUser = withContext(Dispatchers.IO) {
        val root = post(baseUrl, "/api/admin", token, buildJsonObject {
            put("action", "create-user")
            put("username", username.trim().lowercase())
            put("displayName", displayName.trim())
            put("password", password)
        })
        (root["user"] as? JsonObject)?.toAuthUser()
            ?: throw ServerException("The server did not return the new account.")
    }

    suspend fun resetPassword(
        baseUrl: String,
        token: String,
        userId: String,
        password: String
    ) = withContext(Dispatchers.IO) {
        post(baseUrl, "/api/admin", token, buildJsonObject {
            put("action", "reset-password")
            put("userId", userId)
            put("password", password)
        })
        Unit
    }

    suspend fun assignSets(
        baseUrl: String,
        token: String,
        userId: String,
        setIds: List<String>
    ): Int = withContext(Dispatchers.IO) {
        val root = post(baseUrl, "/api/admin", token, buildJsonObject {
            put("action", "assign-sets")
            put("userId", userId)
            put("setIds", buildJsonArray { setIds.forEach { add(JsonPrimitive(it)) } })
        })
        root["assigned"]?.let { numberOrNull(it) } ?: 0
    }

    suspend fun pull(baseUrl: String, token: String): RemoteSnapshot =
        withContext(Dispatchers.IO) {
            val root = post(baseUrl, "/api/sync", token, buildJsonObject {
                put("action", "pull")
            })
            RemoteSnapshot(
                sets = root.array("sets").mapNotNull { it as? JsonObject },
                attempts = root.array("attempts").mapNotNull { it as? JsonObject },
                profile = root["profile"] as? JsonObject,
                profileUpdatedMs = root["profileUpdatedMs"]?.let { numberOrNull(it).toLong() } ?: 0L,
                attemptsClearedMs = root["attemptsClearedMs"]?.let { numberOrNull(it).toLong() } ?: 0L,
                epochMs = root["epochMs"]?.let { numberOrNull(it).toLong() } ?: 0L,
                tombstones = root.array("tombstones").mapNotNull { element ->
                    (element as? JsonObject)?.let { item ->
                        RemoteTombstone(
                            kind = item.string("kind"),
                            id = item.string("id"),
                            deletedMs = item["deletedMs"]?.let { numberOrNull(it).toLong() } ?: 0L
                        )
                    }
                }
            )
        }

    suspend fun push(baseUrl: String, token: String, payload: JsonObject): PushResult =
        withContext(Dispatchers.IO) {
            val root = post(baseUrl, "/api/sync", token, payload)
            PushResult(
                sets = root["sets"]?.let { numberOrNull(it) } ?: 0,
                attempts = root["attempts"]?.let { numberOrNull(it) } ?: 0
            )
        }

    suspend fun scan(
        baseUrl: String,
        token: String,
        prompt: String,
        images: List<ScanImage>
    ): String = withContext(Dispatchers.IO) {
        val root = post(baseUrl, "/api/scan", token, buildJsonObject {
            put("action", "extract")
            put("prompt", prompt)
            put("images", buildJsonArray {
                images.forEach { image ->
                    add(buildJsonObject {
                        put("mimeType", image.mimeType)
                        put("base64", android.util.Base64.encodeToString(image.bytes, android.util.Base64.NO_WRAP))
                    })
                }
            })
        })
        val text = root.string("text")
        if (text.isBlank()) throw ServerException("The scanning service returned an empty answer.")
        text
    }

    private fun post(
        baseUrl: String,
        path: String,
        token: String?,
        body: JsonObject
    ): JsonObject {
        val base = normalizeBase(baseUrl)
            ?: throw ServerException("Add the https address of your Padanam site first.")
        val request = try {
            Request.Builder()
                .url(base + path)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer $token") }
                .post(body.toString().toRequestBody(JSON_MEDIA))
                .build()
        } catch (error: IllegalArgumentException) {
            throw ServerException("That server address is not a valid URL.", cause = error)
        }

        val response = try {
            client.newCall(request).execute()
        } catch (error: java.io.IOException) {
            throw ServerException(
                "Couldn't reach the Padanam server. Check your internet connection.",
                cause = error
            )
        }

        response.use { result ->
            val text = result.body?.string().orEmpty()
            val json = runCatching {
                com.padanam.psc.util.AppJson.parseToJsonElement(text) as? JsonObject
            }.getOrNull()

            if (!result.isSuccessful) {
                val detail = json?.string("error").orEmpty()
                val expired = result.code == 401
                val message = when {
                    detail.isNotBlank() -> detail
                    result.code == 401 -> "Your session expired. Sign in again."
                    result.code == 403 -> "This server did not accept the request."
                    result.code == 404 -> "No API was found at that address. Check the site URL."
                    result.code == 429 -> "Too many attempts. Wait a few minutes and try again."
                    result.code in 500..599 -> "The Padanam server had a problem. Try again shortly."
                    else -> "The server returned HTTP ${result.code}."
                }
                throw ServerException(message, result.code, expired)
            }

            val error = json?.string("error").orEmpty()
            if (error.isNotBlank()) throw ServerException(error, result.code)
            return json ?: throw ServerException("The server returned an unexpected response.")
        }
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        /** Accepts a site URL or a pasted /api endpoint and returns its origin. */
        fun normalizeBase(raw: String): String? {
            var value = raw.trim()
            if (value.isBlank()) return null
            if (!value.startsWith("http://", true) && !value.startsWith("https://", true)) {
                value = "https://$value"
            }
            val uri = runCatching { URI(value) }.getOrNull() ?: return null
            val scheme = uri.scheme?.lowercase() ?: return null
            if (scheme != "http" && scheme != "https") return null
            val host = uri.host ?: return null
            if (host.isBlank()) return null
            val port = if (uri.port > 0) ":${uri.port}" else ""
            var path = uri.path.orEmpty().trimEnd('/')
            for (suffix in listOf("/api/auth", "/api/admin", "/api/sync", "/api/scan", "/api")) {
                if (path.endsWith(suffix)) {
                    path = path.dropLast(suffix.length).trimEnd('/')
                    break
                }
            }
            return "$scheme://$host$port$path".trimEnd('/')
        }

        private fun parseDate(value: String?): Long? {
            if (value.isNullOrBlank()) return null
            return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
                ?: runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
        }

        private fun numberOrNull(element: JsonElement): Int = when (element) {
            is JsonPrimitive -> element.intOrNull
                ?: element.longOrNull?.toInt()
                ?: element.contentOrNull?.toIntOrNull()
                ?: 0
            else -> 0
        }
    }
}

internal fun JsonObject.array(name: String): JsonArray =
    this[name] as? JsonArray ?: JsonArray(emptyList())

internal fun JsonObject.string(name: String): String =
    (this[name] as? JsonPrimitive)?.contentOrNull.orEmpty()

internal fun JsonObject.bool(name: String): Boolean =
    (this[name] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull() ?: false

internal fun JsonObject.toAuthUser(): AuthUser = AuthUser(
    id = string("id"),
    username = string("username"),
    displayName = string("displayName").ifBlank { string("username") },
    role = string("role").ifBlank { "user" }
)

internal fun JsonObject.toAdminUser(): AdminUser? {
    val id = string("id")
    if (id.isBlank()) return null
    return AdminUser(
        id = id,
        username = string("username"),
        displayName = string("displayName").ifBlank { string("username") },
        role = string("role").ifBlank { "user" },
        createdAt = string("createdAt"),
        setIds = array("setIds").mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
    )
}

internal fun jsonDate(epochMs: Long): String =
    if (epochMs > 0L) Instant.ofEpochMilli(epochMs).toString() else Instant.now().toString()

internal fun parseJsonDate(value: String?): Long {
    if (value.isNullOrBlank()) return 0L
    return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
        ?: 0L
}

internal fun jsonNull(): JsonNull = JsonNull
