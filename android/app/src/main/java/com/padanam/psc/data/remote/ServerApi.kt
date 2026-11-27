package com.padanam.psc.data.remote

import com.padanam.psc.data.auth.AuthUser
import com.padanam.psc.data.model.AiDeckScanResult
import com.padanam.psc.data.model.AiScanResult
import com.padanam.psc.data.model.Decks
import com.padanam.psc.data.model.RemoteDeckCard
import com.padanam.psc.data.model.TopicSuggestion
import com.padanam.psc.data.model.Topics
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
    val mainTopic: String = "",
    val subTopic: String = "",
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
    val tombstones: List<RemoteTombstone>,
    val deckProgress: List<JsonObject> = emptyList()
)

data class PushResult(
    val sets: Int,
    val attempts: Int,
    val deckCards: Int = 0
)

/** Provider readiness returned by `GET /api/scan`. */
data class ScanProviderStatus(
    val ready: Boolean,
    val provider: String,
    val model: String,
    val host: String,
    val error: String
)

data class ProviderModel(
    val id: String,
    val label: String
)

data class ProviderModels(
    val provider: String,
    val host: String,
    val model: String,
    val models: List<ProviderModel>
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
                    val title = item.string("title").ifBlank { "Untitled set" }
                    val subject = item.string("subject").ifBlank { "General" }
                    AdminSet(
                        id = item.string("id"),
                        title = title,
                        subject = subject,
                        mainTopic = Topics.normalize(item.string("mainTopic"), title, subject),
                        subTopic = Topics.subTopic(item.string("subTopic"), title),
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
                profileUpdatedMs = root["profileUpdatedMs"]?.let { longNumber(it) } ?: 0L,
                attemptsClearedMs = root["attemptsClearedMs"]?.let { longNumber(it) } ?: 0L,
                epochMs = root["epochMs"]?.let { longNumber(it) } ?: 0L,
                tombstones = root.array("tombstones").mapNotNull { element ->
                    (element as? JsonObject)?.let { item ->
                        RemoteTombstone(
                            kind = item.string("kind"),
                            id = item.string("id"),
                            deletedMs = item["deletedMs"]?.let { longNumber(it) } ?: 0L
                        )
                    }
                },
                deckProgress = root.array("deckProgress").mapNotNull { it as? JsonObject }
            )
        }

    suspend fun push(baseUrl: String, token: String, payload: JsonObject): PushResult =
        withContext(Dispatchers.IO) {
            val root = post(baseUrl, "/api/sync", token, payload)
            PushResult(
                sets = root["sets"]?.let { numberOrNull(it) } ?: 0,
                attempts = root["attempts"]?.let { numberOrNull(it) } ?: 0,
                deckCards = root["deckCards"]?.let { numberOrNull(it) } ?: 0
            )
        }

    /**
     * Reads the shared scanned deck cards from the optional second database.
     * A site without `DECK_NEON_CONNECTION_STRING` simply returns no cards.
     */
    suspend fun deckCards(baseUrl: String, token: String): List<RemoteDeckCard> =
        withContext(Dispatchers.IO) {
            val root = get(baseUrl, "/api/decks", token)
            root.array("cards").mapNotNull { element ->
                (element as? JsonObject)?.toRemoteDeckCard()
            }
        }

    /** Saves the reviewed scan draft into the shared scanned deck database. */
    suspend fun saveDeckCards(
        baseUrl: String,
        token: String,
        result: AiScanResult
    ): Int = withContext(Dispatchers.IO) {
        val letters = listOf("A", "B", "C", "D", "E", "F")
        val questions = buildJsonArray {
            result.questions.forEach { question ->
                val index = letters.indexOf(question.correctOption.trim().uppercase().take(1))
                val option = question.options.firstOrNull {
                    it.letter.trim().uppercase() == question.correctOption.trim().uppercase().take(1)
                } ?: question.options.getOrNull(index)
                val stem = question.questionEn.ifBlank { question.questionMl }
                val answer = option?.textEn.orEmpty().ifBlank { option?.textMl.orEmpty() }
                if (stem.isBlank() || answer.isBlank()) return@forEach
                add(buildJsonObject {
                    put("stem", stem)
                    put("answer", answer)
                    put("explanation", question.explanationEn)
                    put("stemMl", question.questionMl)
                    put("answerMl", option?.textMl.orEmpty())
                    put("explanationMl", question.explanationMl)
                })
            }
        }
        postDeckCards(baseUrl, token, result.title, result.mainTopic, result.subTopic, questions)
    }

    /** Saves a deck-mode scan: one question and one answer per card. */
    suspend fun saveDeckCards(
        baseUrl: String,
        token: String,
        result: AiDeckScanResult
    ): Int = withContext(Dispatchers.IO) {
        val questions = buildJsonArray {
            result.cards.forEach { card ->
                val stem = card.questionEn.ifBlank { card.questionMl }
                val answer = card.answerEn.ifBlank { card.answerMl }
                if (stem.isBlank() || answer.isBlank()) return@forEach
                add(buildJsonObject {
                    put("stem", stem)
                    put("answer", answer)
                    put("explanation", card.explanationEn)
                    put("stemMl", card.questionMl)
                    put("answerMl", card.answerMl)
                    put("explanationMl", card.explanationMl)
                })
            }
        }
        postDeckCards(baseUrl, token, result.title, result.mainTopic, result.subTopic, questions)
    }

    private suspend fun postDeckCards(
        baseUrl: String,
        token: String,
        title: String,
        mainTopic: String,
        subTopic: String,
        questions: JsonArray
    ): Int {
        if (questions.isEmpty()) {
            throw ServerException("There are no complete cards to save to decks.")
        }
        val cleanTitle = title.trim().ifBlank { "Scanned deck" }
        val root = post(baseUrl, "/api/decks", token, buildJsonObject {
            put("action", "save")
            put("title", cleanTitle.take(160))
            put("mainTopic", mainTopic.trim().take(80))
            put("subTopic", subTopic.trim().take(120).ifBlank { cleanTitle.take(120) })
            put("language", "both")
            put("questions", questions)
        })
        return root["saved"]?.let { numberOrNull(it) } ?: 0
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

    /** Asks the server-side provider to choose a topic pair for one set. */
    suspend fun classifyTopic(
        baseUrl: String,
        token: String?,
        title: String,
        subject: String,
        subTopic: String,
        subTopics: List<String>,
        samples: List<String>
    ): TopicSuggestion = withContext(Dispatchers.IO) {
        val root = post(baseUrl, "/api/scan", token, buildJsonObject {
            put("action", "classify")
            put("title", title.trim().take(160))
            put("subject", subject.trim().take(100))
            put("subTopic", subTopic.trim().take(120))
            put("subTopics", buildJsonArray {
                subTopics.map { it.trim().take(80) }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(30)
                    .forEach { add(JsonPrimitive(it)) }
            })
            put("samples", buildJsonArray {
                samples.map { it.trim().take(400) }
                    .filter { it.isNotBlank() }
                    .take(8)
                    .forEach { add(JsonPrimitive(it)) }
            })
        })
        val cleanTitle = title.trim().take(80)
        val cleanSubject = subject.trim().take(40)
        TopicSuggestion(
            mainTopic = Topics.normalize(root.string("mainTopic"), cleanTitle, cleanSubject),
            subTopic = Topics.subTopic(root.string("subTopic"), cleanTitle),
            source = "ai"
        )
    }

    /** Reads the provider readiness payload without exposing any secret. */
    suspend fun scanStatus(baseUrl: String): ScanProviderStatus = withContext(Dispatchers.IO) {
        val root = get(baseUrl, "/api/scan", null)
        ScanProviderStatus(
            ready = root.bool("ready"),
            provider = root.string("provider"),
            model = root.string("model"),
            host = root.string("host"),
            error = root.string("error")
        )
    }

    /** Asks the server to make a tiny provider call, proving proxy + key work. */
    suspend fun testScanProvider(baseUrl: String, token: String?): String =
        withContext(Dispatchers.IO) {
            val root = post(baseUrl, "/api/scan", token, buildJsonObject {
                put("action", "test")
            })
            root.string("text").ifBlank { "Provider responded." }
        }

    suspend fun providerModels(baseUrl: String): ProviderModels = withContext(Dispatchers.IO) {
        val root = get(baseUrl, "/api/models", null)
        ProviderModels(
            provider = root.string("provider"),
            host = root.string("host"),
            model = root.string("model"),
            models = root.array("models").mapNotNull { element ->
                (element as? JsonObject)?.let { item ->
                    val id = item.string("id")
                    if (id.isBlank()) null else ProviderModel(
                        id = id,
                        label = item.string("label").ifBlank { id }
                    )
                }
            }
        )
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
        return execute(request)
    }

    private fun get(
        baseUrl: String,
        path: String,
        token: String?
    ): JsonObject {
        val base = normalizeBase(baseUrl)
            ?: throw ServerException("Add the https address of your Padanam site first.")
        val request = try {
            Request.Builder()
                .url(base + path)
                .header("Accept", "application/json")
                .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer $token") }
                .get()
                .build()
        } catch (error: IllegalArgumentException) {
            throw ServerException("That server address is not a valid URL.", cause = error)
        }
        return execute(request)
    }

    private fun execute(request: Request): JsonObject {
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

        /** Epoch milliseconds exceed Int; never round-trip them through numberOrNull. */
        private fun longNumber(element: JsonElement): Long = when (element) {
            is JsonPrimitive -> element.longOrNull
                ?: element.contentOrNull?.toLongOrNull()
                ?: 0L
            else -> 0L
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

internal fun JsonObject.toRemoteDeckCard(): RemoteDeckCard? {
    val key = string("key").trim()
    val deckId = string("deckId").trim()
    val cardId = string("cardId").trim()
    if (!Decks.isRemoteKey(key) || deckId.isBlank() || cardId.isBlank()) return null
    if (key != "scan:$deckId:$cardId") return null

    val stem = string("stem").trim()
    val stemMl = string("stemMl").trim()
    val answer = string("answer").trim()
    val answerMl = string("answerMl").trim()
    if ((stem.isBlank() && stemMl.isBlank()) || (answer.isBlank() && answerMl.isBlank())) {
        return null
    }

    return RemoteDeckCard(
        key = key,
        deckId = deckId,
        cardId = cardId,
        deckTitle = string("deckTitle").trim(),
        mainTopic = string("mainTopic").trim(),
        subTopic = string("subTopic").trim(),
        language = if (string("language") == "ml") "ml" else "en",
        stem = stem,
        stemMl = stemMl,
        answer = answer,
        answerMl = answerMl,
        explanation = string("explanation").trim(),
        explanationMl = string("explanationMl").trim(),
        updatedAt = string("updatedAt").trim()
    )
}

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
