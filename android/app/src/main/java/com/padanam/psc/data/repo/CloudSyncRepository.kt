package com.padanam.psc.data.repo

import com.padanam.psc.data.auth.AuthSession
import com.padanam.psc.data.local.AppDao
import com.padanam.psc.data.local.AttemptEntity
import com.padanam.psc.data.local.QuestionEntity
import com.padanam.psc.data.local.SetEntity
import com.padanam.psc.data.model.AnswerRecord
import com.padanam.psc.data.model.QuizMode
import com.padanam.psc.data.model.SyncOutcome
import com.padanam.psc.data.prefs.AppSettings
import com.padanam.psc.data.prefs.SettingsRepository
import com.padanam.psc.data.remote.RemoteSnapshot
import com.padanam.psc.data.remote.ServerApi
import com.padanam.psc.data.remote.ServerException
import com.padanam.psc.data.remote.array
import com.padanam.psc.data.remote.jsonDate
import com.padanam.psc.data.remote.parseJsonDate
import com.padanam.psc.data.remote.string
import com.padanam.psc.util.AppJson
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put
import kotlin.math.roundToInt

/**
 * Two-way sync with the account workspace used by the web app. Neon remains
 * the cloud source of truth, but Room stays authoritative while offline.
 *
 * Push first? No: pull and merge first so an assigned library or a newer web
 * edit is visible, then push the union. Set timestamps decide the winner.
 */
class CloudSyncRepository(
    private val dao: AppDao,
    private val serverApi: ServerApi,
    private val settingsRepository: SettingsRepository
) {

    suspend fun sync(
        baseUrl: String,
        session: AuthSession,
        settings: AppSettings
    ): SyncOutcome {
        if (baseUrl.isBlank()) {
            return SyncOutcome(false, "Add the address of your Padanam site first.")
        }
        if (session.token.isBlank()) {
            return SyncOutcome(false, "Sign in to sync your account.")
        }

        return try {
            val remote = serverApi.pull(baseUrl, session.token)
            val pulled = applyRemote(remote)

            val current = settingsRepository.current()
            val sets = dao.setsOnce()
            val questionsBySet = dao.allQuestionsOnce().groupBy { it.setId }
            val attempts = dao.allAttemptsOnce()
            val pushProfileAt = current.profileUpdatedAt.takeIf { it > 0L }
                ?: System.currentTimeMillis().also { stamp ->
                    settingsRepository.applyRemoteProfile(
                        current.name,
                        current.language,
                        current.dailyGoal,
                        stamp
                    )
                }

            val payload = buildJsonObject {
                put("action", "push")
                put("sets", buildJsonArray {
                    sets.forEach { set ->
                        add(set.toWebSet(questionsBySet[set.id].orEmpty()))
                    }
                })
                put("attempts", buildJsonArray {
                    attempts.forEach { attempt -> add(attempt.toWebAttempt(current.language)) }
                })
                put("profile", buildJsonObject {
                    put("name", current.name)
                    put("target", "Kerala PSC")
                    put("examDate", "")
                    put("dailyGoal", current.dailyGoal)
                    put("language", current.language)
                    put("reminders", current.reminders)
                })
                put("profileUpdatedAt", jsonDate(pushProfileAt))
                put("attemptsClearedAt", "")
                put("tombstones", buildJsonArray {})
            }

            val pushed = serverApi.push(baseUrl, session.token, payload)
            val message = buildString {
                append("Synced ")
                append(sets.size)
                append(if (sets.size == 1) " set" else " sets")
                append(" · ")
                append(attempts.size)
                append(if (attempts.size == 1) " attempt" else " attempts")
                if (pulled > 0) {
                    append(" · pulled ")
                    append(pulled)
                    append(" new")
                }
            }
            SyncOutcome(
                ok = true,
                message = message,
                pushed = pushed.sets + pushed.attempts,
                pulled = pulled
            )
        } catch (error: ServerException) {
            SyncOutcome(false, error.message ?: "Cloud sync failed.")
        } catch (error: Exception) {
            SyncOutcome(false, error.message ?: "Cloud sync failed.")
        }
    }

    private suspend fun applyRemote(remote: RemoteSnapshot): Int {
        var pulled = 0
        val localSets = dao.setsOnce().associateBy { it.id }
        remote.sets.forEach { payload ->
            val parsed = payload.toLocalSet() ?: return@forEach
            val incoming = parsed.first
            val existing = localSets[incoming.id]
            if (existing == null || incoming.updatedAt > existing.updatedAt) {
                dao.replaceSetWithQuestions(incoming, parsed.second)
                pulled += 1
            }
        }

        val knownAttempts = dao.allAttemptsOnce().map { it.id }.toHashSet()
        val incomingAttempts = remote.attempts.mapNotNull { it.toLocalAttempt() }
            .filter { knownAttempts.add(it.id) }
        if (incomingAttempts.isNotEmpty()) {
            dao.insertRemoteAttempts(incomingAttempts)
            pulled += incomingAttempts.size
        }

        val current = settingsRepository.current()
        if (remote.profileUpdatedMs > current.profileUpdatedAt && remote.profile != null) {
            val profile = remote.profile
            val goal = (profile["dailyGoal"] as? JsonPrimitive)?.intOrNull
            settingsRepository.applyRemoteProfile(
                name = profile.string("name").takeIf { it.isNotBlank() },
                language = profile.string("language").takeIf { it.isNotBlank() },
                dailyGoal = goal,
                updatedAtMs = remote.profileUpdatedMs
            )
        }
        return pulled
    }

    /* ------------------------------------------------------- local → web */

    private fun SetEntity.toWebSet(questions: List<QuestionEntity>): JsonObject = buildJsonObject {
        put("id", id)
        put("title", title)
        put("titleMl", "")
        put("subject", subject)
        put("source", source)
        put("createdAt", jsonDate(createdAt))
        put("updatedAt", jsonDate(updatedAt))
        put("questions", buildJsonArray {
            questions.sortedBy { it.order }.forEach { add(it.toWebQuestion()) }
        })
    }

    private fun QuestionEntity.toWebQuestion(): JsonObject = buildJsonObject {
        put("id", id)
        put("answer", answerIndex(correctOption))
        put("en", buildJsonObject {
            put("stem", questionEn.ifBlank { questionMl })
            put("options", buildJsonArray {
                optionsEn().forEach { add(JsonPrimitive(it)) }
            })
            put("explanation", explanationEn)
        })
        val mlOptions = optionsMl()
        if (questionMl.isNotBlank() && mlOptions.all { it.isNotBlank() }) {
            put("ml", buildJsonObject {
                put("stem", questionMl)
                put("options", buildJsonArray {
                    mlOptions.forEach { add(JsonPrimitive(it)) }
                })
                put("explanation", explanationMl)
            })
        }
    }

    private fun AnswerRecord.toWebQuestion(): JsonObject = buildJsonObject {
        put("id", questionId)
        put("answer", answerIndex(correctOption))
        put("en", buildJsonObject {
            put("stem", questionEn.ifBlank { questionMl })
            put("options", buildJsonArray {
                optionsEn.forEach { add(JsonPrimitive(it)) }
            })
            put("explanation", explanationEn)
        })
        if (questionMl.isNotBlank() && optionsMl.all { it.isNotBlank() }) {
            put("ml", buildJsonObject {
                put("stem", questionMl)
                put("options", buildJsonArray {
                    optionsMl.forEach { add(JsonPrimitive(it)) }
                })
                put("explanation", explanationMl)
            })
        }
    }

    private fun AttemptEntity.toWebAttempt(language: String): JsonObject {
        val records = decodeAnswers(answersJson)
        return buildJsonObject {
            put("id", id)
            put("setId", setId.orEmpty())
            put("setTitle", setTitle)
            put("titleMl", "")
            put("subject", subject)
            put("mode", if (mode == QuizMode.PRACTICE) "practice" else "mock")
            put("lang", language)
            put("startedAt", jsonDate(startedAt))
            put("finishedAt", jsonDate(completedAt))
            put("durationSec", durationSeconds)
            put("timeLimitSec", JsonNull)
            put("questions", buildJsonArray {
                records.forEach { add(it.toWebQuestion()) }
            })
            put("responses", buildJsonArray {
                records.forEach { record ->
                    val index = letterIndex(record.selectedOption)
                    if (index == null) add(JsonNull) else add(JsonPrimitive(index))
                }
            })
            put("flags", buildJsonArray {
                records.forEach { add(JsonPrimitive(it.flagged)) }
            })
        }
    }

    /* ------------------------------------------------------- web → local */

    private fun JsonObject.toLocalSet(): Pair<SetEntity, List<QuestionEntity>>? {
        val id = string("id")
        if (id.isBlank()) return null
        val now = System.currentTimeMillis()
        val created = parseJsonDate(string("createdAt")).takeIf { it > 0L }
            ?: parseJsonDate(string("updatedAt")).takeIf { it > 0L }
            ?: now
        val updated = parseJsonDate(string("updatedAt")).takeIf { it > 0L } ?: created
        val questions = array("questions").mapIndexedNotNull { index, element ->
            (element as? JsonObject)?.toLocalQuestion(id, index, created + index)
        }
        val set = SetEntity(
            id = id,
            title = string("title").ifBlank { "Untitled set" }.take(120),
            subject = string("subject").ifBlank { "General" }.take(60),
            createdAt = created,
            updatedAt = updated,
            source = string("source").ifBlank { "scan" }.take(20),
            imageUrisJson = null,
            synced = true
        )
        return set to questions
    }

    private fun JsonObject.toLocalQuestion(
        setId: String,
        order: Int,
        createdAt: Long
    ): QuestionEntity? {
        val en = this["en"] as? JsonObject ?: return null
        val rawOptions = (en["options"] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
            .orEmpty()
        if (rawOptions.size < 2) return null

        val options = rawOptions.take(4).toMutableList()
        while (options.size < 4) options.add("")
        val answer = ((this["answer"] as? JsonPrimitive)?.intOrNull ?: 0)
            .coerceIn(0, options.lastIndex)

        val ml = this["ml"] as? JsonObject
        val mlStem = ml?.string("stem").orEmpty()
        val mlRaw = (ml?.get("options") as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
            .orEmpty()
        val hasMl = mlStem.isNotBlank() && mlRaw.size == rawOptions.size
        val mlOptions = if (hasMl) mlRaw else emptyList()

        return QuestionEntity(
            id = string("id").ifBlank { "q_${setId}_$order" },
            setId = setId,
            order = order,
            questionEn = en.string("stem").ifBlank { mlStem },
            questionMl = mlStem,
            optionAEn = options[0],
            optionBEn = options[1],
            optionCEn = options[2],
            optionDEn = options[3],
            optionAMl = mlOptions.getOrElse(0) { "" },
            optionBMl = mlOptions.getOrElse(1) { "" },
            optionCMl = mlOptions.getOrElse(2) { "" },
            optionDMl = mlOptions.getOrElse(3) { "" },
            correctOption = LETTERS[answer],
            explanationEn = en.string("explanation"),
            explanationMl = ml?.string("explanation").orEmpty(),
            topic = string("topic").ifBlank { "General" }.take(60),
            difficulty = string("difficulty").lowercase()
                .takeIf { it in setOf("easy", "medium", "hard") } ?: "medium",
            confidence = 80,
            createdAt = createdAt,
            synced = true
        )
    }

    private fun JsonObject.toLocalAttempt(): AttemptEntity? {
        val id = string("id")
        if (id.isBlank()) return null
        val questions = array("questions").mapNotNull { it as? JsonObject }
        if (questions.isEmpty()) return null
        val responses = array("responses")
        val flags = array("flags")

        val records = questions.mapIndexedNotNull { index, question ->
            val en = question["en"] as? JsonObject ?: return@mapIndexedNotNull null
            val options = (en["options"] as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
                .orEmpty()
            if (options.size < 2) return@mapIndexedNotNull null
            val padded = options.take(4).toMutableList()
            while (padded.size < 4) padded.add("")
            val answer = ((question["answer"] as? JsonPrimitive)?.intOrNull ?: 0)
                .coerceIn(0, padded.lastIndex)
            val selectedIndex = (responses.getOrNull(index) as? JsonPrimitive)?.intOrNull
            val selectedLetter = selectedIndex?.let { letterAt(it) }
            val ml = question["ml"] as? JsonObject
            val mlStem = ml?.string("stem").orEmpty()
            val mlRaw = (ml?.get("options") as? JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
                .orEmpty()
            val hasMl = mlStem.isNotBlank() && mlRaw.size == options.size
            val mlOptions = if (hasMl) mlRaw else emptyList()

            AnswerRecord(
                questionId = question.string("id").ifBlank { "q_${id}_$index" },
                questionEn = en.string("stem").ifBlank { mlStem },
                questionMl = mlStem,
                optionsEn = padded,
                optionsMl = List(4) { mlOptions.getOrElse(it) { "" } },
                correctOption = LETTERS[answer],
                selectedOption = selectedLetter,
                explanationEn = en.string("explanation"),
                explanationMl = ml?.string("explanation").orEmpty(),
                topic = question.string("topic").ifBlank { "General" },
                difficulty = question.string("difficulty").lowercase()
                    .takeIf { it in setOf("easy", "medium", "hard") } ?: "medium",
                timeSpentSeconds = 0,
                flagged = (flags.getOrNull(index) as? JsonPrimitive)
                    ?.contentOrNull?.toBooleanStrictOrNull() == true
            )
        }

        if (records.isEmpty()) return null
        val total = records.size
        val correct = records.count { it.isCorrect && !it.isSkipped }
        val skipped = records.count { it.isSkipped }
        val wrong = total - correct - skipped
        val score = ((correct * 100.0) / total).roundToInt()
        val finished = parseJsonDate(string("finishedAt")).takeIf { it > 0L }
            ?: System.currentTimeMillis()
        val started = parseJsonDate(string("startedAt")).takeIf { it > 0L } ?: finished

        return AttemptEntity(
            id = id,
            setId = string("setId").takeIf { it.isNotBlank() },
            setTitle = string("setTitle").ifBlank { "Attempt" },
            subject = string("subject").ifBlank { "General" },
            mode = if (string("mode") == "practice") QuizMode.PRACTICE else QuizMode.EXAM,
            total = total,
            correct = correct,
            wrong = wrong,
            skipped = skipped,
            scorePercent = score,
            durationSeconds = ((this["durationSec"] as? JsonPrimitive)?.intOrNull ?: 0)
                .coerceAtLeast(0),
            startedAt = started,
            completedAt = finished,
            answersJson = AppJson.encodeToString(
                ListSerializer(AnswerRecord.serializer()),
                records
            ),
            synced = true
        )
    }

    private fun decodeAnswers(raw: String): List<AnswerRecord> =
        runCatching {
            AppJson.decodeFromString(ListSerializer(AnswerRecord.serializer()), raw)
        }.getOrDefault(emptyList())

    private fun answerIndex(letter: String): Int =
        LETTERS.indexOf(letter.trim().uppercase()).takeIf { it >= 0 } ?: 0

    private fun letterIndex(letter: String?): Int? =
        letter?.trim()?.uppercase()?.let { LETTERS.indexOf(it).takeIf { index -> index >= 0 } }

    private fun letterAt(index: Int): String? = LETTERS.getOrNull(index)

    companion object {
        private val LETTERS = listOf("A", "B", "C", "D")
    }
}
