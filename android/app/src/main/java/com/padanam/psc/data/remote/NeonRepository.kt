package com.padanam.psc.data.remote

import com.padanam.psc.data.model.SyncOutcome
import com.padanam.psc.util.AppJson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.util.concurrent.TimeUnit

class NeonException(message: String, cause: Throwable? = null) : Exception(message, cause)

data class NeonQuery(
    val sql: String,
    val params: List<String?> = emptyList()
)

data class NeonResult(
    val fields: List<String> = emptyList(),
    val rows: List<List<JsonElement>> = emptyList(),
    val rowCount: Int = rows.size
) {
    fun row(index: Int): Map<String, JsonElement> {
        val values = rows.getOrNull(index) ?: return emptyMap()
        return fields.mapIndexedNotNull { position, name ->
            values.getOrNull(position)?.let { name to it }
        }.toMap()
    }

    fun string(rowIndex: Int, field: String): String? =
        row(rowIndex)[field].asStringOrNull()
}

fun JsonElement?.asStringOrNull(): String? = when (this) {
    null, JsonNull -> null
    is JsonPrimitive -> if (isString) content else content
    else -> toString()
}

fun JsonElement?.asLongOrNull(): Long? = asStringOrNull()?.toLongOrNull()

/**
 * Minimal client for Neon's HTTP query endpoint. The endpoint is derived from
 * the pooled connection string exactly like @neondatabase/serverless: replace
 * the first host label with "api" and POST to /sql.
 *
 * This is intentionally a "bring your own credentials" personal app; there is
 * no shared server in the middle. See README.md before distributing the APK.
 */
class NeonRepository(
    private val client: OkHttpClient = Network.client.newBuilder()
        .callTimeout(45, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()
) {

    suspend fun testConnection(connectionString: String, endpoint: String): String =
        withContext(Dispatchers.IO) {
            val started = System.currentTimeMillis()
            val result = query(connectionString, endpoint, "SELECT 1 AS ok")
            val elapsed = System.currentTimeMillis() - started
            if (result.rows.isEmpty()) {
                throw NeonException("Neon connected but returned no rows. Check that the database is ready.")
            }
            "Connected · answered in ${elapsed}ms."
        }

    suspend fun query(
        connectionString: String,
        endpoint: String,
        sql: String,
        params: List<String?> = emptyList()
    ): NeonResult = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("query", sql)
            put("params", params.toJsonArray())
        }
        val root = post(connectionString, endpoint, body)
        parseResult(root)
    }

    suspend fun batch(
        connectionString: String,
        endpoint: String,
        queries: List<NeonQuery>
    ): List<NeonResult> = withContext(Dispatchers.IO) {
        if (queries.isEmpty()) return@withContext emptyList()
        val body = buildJsonObject {
            put("queries", buildJsonArray {
                queries.forEach { item ->
                    add(buildJsonObject {
                        put("query", item.sql)
                        put("params", item.params.toJsonArray())
                    })
                }
            })
        }
        val root = post(connectionString, endpoint, body)
        val results = root["results"]?.takeIf { it is JsonArray }?.jsonArray
            ?: throw NeonException(
                root["message"].asStringOrNull()
                    ?: "Neon did not return batch results. Check the schema is installed."
            )
        results.map { element ->
            if (element is JsonObject) parseResult(element) else NeonResult()
        }
    }

    private fun post(connectionString: String, endpoint: String, body: JsonObject): JsonObject {
        if (connectionString.isBlank()) {
            throw NeonException("Add your Neon connection string in Settings.")
        }
        val url = resolveEndpoint(connectionString, endpoint)
            ?: throw NeonException("Couldn't derive a Neon endpoint from that connection string.")

        val request = try {
            Request.Builder()
                .url(url)
                .header("Neon-Connection-String", connectionString)
                .header("Neon-Raw-Text-Output", "true")
                .header("Neon-Array-Mode", "true")
                .header("Accept", "application/json")
                .post(body.toString().toRequestBody(JSON_MEDIA))
                .build()
        } catch (error: IllegalArgumentException) {
            throw NeonException("The Neon endpoint is not a valid URL.", error)
        }

        val response = try {
            client.newCall(request).execute()
        } catch (error: java.io.IOException) {
            throw NeonException(
                "Couldn't reach Neon. Check your connection string, endpoint and internet.",
                error
            )
        }

        response.use { result ->
            val text = result.body?.string().orEmpty()
            val json = runCatching { AppJson.parseToJsonElement(text) as? JsonObject }.getOrNull()

            if (!result.isSuccessful) {
                throw NeonException(friendlyHttpMessage(result.code, json, text))
            }

            val errorMessage = json?.get("message").asStringOrNull()
                ?: json?.get("error").asStringOrNull()
            if (errorMessage != null && json?.get("results") == null && json?.get("rows") == null) {
                throw NeonException("Neon rejected the query: $errorMessage")
            }

            return json ?: throw NeonException("Neon returned an unexpected response.")
        }
    }

    private fun parseResult(root: JsonObject): NeonResult {
        val fields = root["fields"]?.takeIf { it is JsonArray }?.jsonArray?.mapNotNull { element ->
            (element as? JsonObject)?.get("name").asStringOrNull()
        }.orEmpty()

        val rawRows = root["rows"]?.takeIf { it is JsonArray }?.jsonArray
        val rows = rawRows?.map { row ->
            when (row) {
                is JsonArray -> row.toList()
                is JsonObject -> fields.map { row[it] ?: JsonNull }
                else -> emptyList()
            }
        }.orEmpty()

        val count = root["rowCount"].asStringOrNull()?.toIntOrNull() ?: rows.size
        return NeonResult(fields = fields, rows = rows, rowCount = count)
    }

    private fun friendlyHttpMessage(code: Int, json: JsonObject?, raw: String): String {
        val detail = json?.get("message").asStringOrNull()
            ?: json?.get("error").asStringOrNull()
            ?: raw.take(180).takeIf { it.isNotBlank() }
        return when (code) {
            400 -> "Neon rejected the query: ${detail ?: "check the SQL schema"}."
            401, 403 -> "Neon refused these credentials. Regenerate the connection string."
            404 -> "Neon endpoint or database not found. Check the connection string."
            429 -> "Neon is rate limiting requests. Try again in a moment."
            in 500..599 -> "Neon had a server error. Try again shortly."
            else -> "Neon error $code${detail?.let { ": $it" } ?: ""}."
        }
    }

    companion object {
        private val JSON_MEDIA = "application/json".toMediaType()

        /**
         * Neon's HTTP endpoint lives on api.<region> rather than on the pooled
         * compute host. Mirrors the official serverless driver behaviour.
         */
        fun deriveEndpoint(connectionString: String): String? {
            val host = runCatching { URI(connectionString.trim()).host }.getOrNull()
                ?: runCatching {
                    Regex("@([^/?#]+)").find(connectionString)?.groupValues?.getOrNull(1)
                }.getOrNull()
            if (host.isNullOrBlank() || !host.contains('.')) return null
            val apiHost = host.replaceFirst(Regex("^[^.]+\\.|^[^.]+$"), "api.")
            return "https://$apiHost/sql"
        }

        fun resolveEndpoint(connectionString: String, endpoint: String): String? {
            val configured = endpoint.trim().trimEnd('/')
            if (configured.isNotBlank()) {
                val withScheme = if (configured.startsWith("http://") ||
                    configured.startsWith("https://")
                ) configured else "https://$configured"
                return if (withScheme.endsWith("/sql")) withScheme else "$withScheme/sql"
            }
            return deriveEndpoint(connectionString)
        }
    }
}

private fun List<String?>.toJsonArray(): JsonArray = buildJsonArray {
    forEach { value ->
        if (value == null) add(JsonNull) else add(JsonPrimitive(value))
    }
}
