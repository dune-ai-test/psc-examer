package com.padanam.psc.data.repo

import com.padanam.psc.data.local.AttemptEntity
import com.padanam.psc.data.local.DeckProgressEntity
import com.padanam.psc.data.local.QuestionEntity
import com.padanam.psc.data.local.SetEntity
import com.padanam.psc.data.model.AnswerRecord
import com.padanam.psc.data.model.DeckResult
import com.padanam.psc.data.model.DeckStatus
import com.padanam.psc.data.model.QuizMode
import com.padanam.psc.data.model.Topics
import com.padanam.psc.data.prefs.AppSettings
import com.padanam.psc.data.remote.array
import com.padanam.psc.data.remote.jsonDate
import com.padanam.psc.data.remote.parseJsonDate
import com.padanam.psc.data.remote.string
import com.padanam.psc.util.AppJson
import kotlinx.serialization.builtins.ListSerializer
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
import java.time.Instant
import java.util.UUID
import kotlin.math.roundToInt

data class ImportedProfile(
    val name: String,
    val target: String,
    val examDate: String,
    val dailyGoal: Int?,
    val language: String?,
    val reminders: Boolean?
)

data class BackupImport(
    val sets: List<Pair<SetEntity, List<QuestionEntity>>>,
    val attempts: List<AttemptEntity>,
    val profile: ImportedProfile?
) {
    val questionCount: Int get() = sets.sumOf { it.second.size }
}

/**
 * The JSON contract shared by the web backup file, the Android export and the
 * cloud sync payload. Questions use the web shape (`en` / `ml` objects) so a
 * backup made on either platform can be restored on the other.
 */
object WebBackup {

    private const val MAX_OPTIONS = 6
    private val LETTERS = listOf("A", "B", "C", "D", "E", "F")

    /** Mirrors the server: future card namespaces such as `scan:<deck>:<card>` fit too. */
    const val MAX_DECK_CARDS = 20_000
    private val DECK_KEY = Regex("^[a-z][a-z0-9_-]*:.+$")

    // ------------------------------------------------------------ export

    fun encode(
        sets: List<SetEntity>,
        questions: List<QuestionEntity>,
        attempts: List<AttemptEntity>,
        profile: AppSettings
    ): String {
        val questionsBySet = questions.groupBy { it.setId }
        val root = buildJsonObject {
            put("app", "padanam")
            put("kind", "backup")
            put("version", 1)
            put("exportedAt", Instant.now().toString())
            put("profile", buildJsonObject {
                put("name", profile.name)
                put("target", profile.target)
                put("examDate", profile.examDate)
                put("dailyGoal", profile.dailyGoal)
                put("language", profile.language)
                put("reminders", profile.reminders)
            })
            put("sets", buildJsonArray {
                sets.sortedByDescending { it.createdAt }.forEach { set ->
                    add(setToJson(set, questionsBySet[set.id].orEmpty()))
                }
            })
            put("attempts", buildJsonArray {
                attempts.sortedByDescending { it.completedAt }.forEach { attempt ->
                    add(attemptToJson(attempt, profile.language))
                }
            })
            put("meta", buildJsonObject {
                put("createdAt", jsonDate(sets.minOfOrNull { it.createdAt } ?: System.currentTimeMillis()))
            })
        }
        return AppJson.encodeToString(JsonObject.serializer(), root)
    }

    fun setToJson(set: SetEntity, questions: List<QuestionEntity>): JsonObject = buildJsonObject {
        put("id", set.id)
        put("title", set.title)
        put("titleMl", set.titleMl)
        put("subject", set.subject)
        put("mainTopic", Topics.normalize(set.mainTopic, set.title, set.subject))
        put("subTopic", Topics.subTopic(set.subTopic, set.title))
        put("source", set.source)
        put("createdAt", jsonDate(set.createdAt))
        put("updatedAt", jsonDate(set.updatedAt))
        put("questions", buildJsonArray {
            questions.sortedBy { it.order }.forEach { add(questionToJson(it)) }
        })
    }

    fun questionToJson(question: QuestionEntity): JsonObject {
        val options = exportOptions(
            en = question.optionsEn(),
            ml = question.optionsMl(),
            answerLetter = question.correctOption
        )

        return buildJsonObject {
            put("id", question.id)
            put("answer", options.answer)
            put("en", buildJsonObject {
                put("stem", question.questionEn.ifBlank { question.questionMl })
                put("options", buildJsonArray { options.en.forEach { add(JsonPrimitive(it)) } })
                put("explanation", question.explanationEn)
            })
            if (question.questionMl.isNotBlank() && options.ml.isNotEmpty()) {
                put("ml", buildJsonObject {
                    put("stem", question.questionMl)
                    put("options", buildJsonArray { options.ml.forEach { add(JsonPrimitive(it)) } })
                    put("explanation", question.explanationMl)
                })
            }
        }
    }

    fun answerToJson(record: AnswerRecord): JsonObject {
        val options = exportOptions(
            en = record.optionsEn,
            ml = record.optionsMl,
            answerLetter = record.correctOption
        )

        return buildJsonObject {
            put("id", record.questionId)
            put("answer", options.answer)
            put("en", buildJsonObject {
                put("stem", record.questionEn.ifBlank { record.questionMl })
                put("options", buildJsonArray { options.en.forEach { add(JsonPrimitive(it)) } })
                put("explanation", record.explanationEn)
            })
            if (record.questionMl.isNotBlank() && options.ml.isNotEmpty()) {
                put("ml", buildJsonObject {
                    put("stem", record.questionMl)
                    put("options", buildJsonArray { options.ml.forEach { add(JsonPrimitive(it)) } })
                    put("explanation", record.explanationMl)
                })
            }
        }
    }

    fun attemptToJson(attempt: AttemptEntity, language: String): JsonObject {
        val records = decodeAnswers(attempt.answersJson)
        return buildJsonObject {
            put("id", attempt.id)
            put("setId", attempt.setId.orEmpty())
            put("setTitle", attempt.setTitle)
            put("titleMl", "")
            put("subject", attempt.subject)
            put("mode", if (attempt.mode == QuizMode.PRACTICE) "practice" else "mock")
            put("lang", language)
            put("startedAt", jsonDate(attempt.startedAt))
            put("finishedAt", jsonDate(attempt.completedAt))
            put("durationSec", attempt.durationSeconds)
            put("timeLimitSec", JsonNull)
            put("questions", buildJsonArray {
                records.forEach { add(answerToJson(it)) }
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

    // --------------------------------------------------------- deck progress

    fun deckProgressToJson(entry: DeckProgressEntity): JsonObject {
        val status = DeckStatus.normalize(entry.status)
        val lastResult = when (status) {
            DeckStatus.KNOWN -> DeckResult.GOT_IT
            DeckStatus.SEEN -> DeckResult.AGAIN
            else -> ""
        }
        return buildJsonObject {
            put("key", entry.cardKey)
            put("status", status)
            put("lastResult", lastResult)
            put(
                "seenAt",
                if (status == DeckStatus.UNSEEN || entry.seenAt <= 0L) {
                    ""
                } else {
                    jsonDate(entry.seenAt)
                }
            )
            put(
                "updatedAt",
                jsonDate(entry.updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis())
            )
        }
    }

    fun parseDeckProgress(payload: JsonObject): DeckProgressEntity? {
        val key = payload.string("key").trim()
        if (!validDeckKey(key)) return null
        val updated = parseJsonDate(payload.string("updatedAt"))
        if (updated <= 0L) return null

        val result = DeckResult.normalize(payload.string("lastResult"))
        val status = when (val raw = payload.string("status")) {
            DeckStatus.KNOWN, DeckStatus.SEEN, DeckStatus.UNSEEN -> raw
            else -> when (result) {
                DeckResult.GOT_IT -> DeckStatus.KNOWN
                DeckResult.AGAIN -> DeckStatus.SEEN
                else -> DeckStatus.SEEN
            }
        }
        val seenAt = if (status == DeckStatus.UNSEEN) {
            0L
        } else {
            parseJsonDate(payload.string("seenAt")).takeIf { it > 0L } ?: updated
        }
        return DeckProgressEntity(
            cardKey = key,
            status = status,
            lastResult = when (status) {
                DeckStatus.KNOWN -> DeckResult.GOT_IT
                DeckStatus.SEEN -> DeckResult.AGAIN
                else -> ""
            },
            seenAt = seenAt,
            updatedAt = updated,
            synced = true
        )
    }

    fun validDeckKey(key: String): Boolean =
        key.length in 3..320 && DECK_KEY.matches(key)

    // ------------------------------------------------------------ import

    fun decode(raw: String): BackupImport? {
        val root = runCatching { AppJson.parseToJsonElement(raw) }.getOrNull() as? JsonObject
            ?: return null
        val source = (root["data"] as? JsonObject) ?: root
        val setArray = source["sets"] as? JsonArray
        val attemptArray = source["attempts"] as? JsonArray
        val profileObject = source["profile"] as? JsonObject
        if (setArray == null && attemptArray == null && profileObject == null) return null

        val sets = mutableListOf<Pair<SetEntity, List<QuestionEntity>>>()
        val attempts = mutableListOf<AttemptEntity>()

        setArray?.forEach { element ->
            val payload = element as? JsonObject ?: return@forEach
            val parsed = parseSet(payload) ?: return@forEach
            sets += parsed
            payload.array("attempts").forEach { attemptElement ->
                val attempt = attemptElement as? JsonObject ?: return@forEach
                parseLegacyAttempt(attempt, parsed.first.id, parsed.first.title)?.let(attempts::add)
            }
        }

        source.array("attempts").forEach { element ->
            (element as? JsonObject)?.let { payload ->
                parseAttempt(payload)?.let(attempts::add)
            }
        }
        source.array("orphanAttempts").forEach { element ->
            (element as? JsonObject)?.let { payload ->
                parseLegacyAttempt(payload, null, payload.string("title"))?.let(attempts::add)
            }
        }

        val profile = (source["profile"] as? JsonObject)?.takeIf { it.isNotEmpty() }?.let {
            ImportedProfile(
                name = it.string("name").trim(),
                target = it.string("target").trim().ifBlank { "Kerala PSC" },
                examDate = it.string("examDate").trim(),
                dailyGoal = (it["dailyGoal"] as? JsonPrimitive)?.intOrNull,
                language = it.string("language").takeIf { value -> value == "en" || value == "ml" },
                reminders = (it["reminders"] as? JsonPrimitive)
                    ?.contentOrNull
                    ?.toBooleanStrictOrNull()
            )
        }

        return BackupImport(
            sets = sets,
            attempts = attempts.distinctBy { it.id },
            profile = profile
        )
    }

    fun parseSet(payload: JsonObject): Pair<SetEntity, List<QuestionEntity>>? {
        val id = payload.string("id").ifBlank { "set_${UUID.randomUUID()}" }
        val created = dateValue(payload["createdAt"]).takeIf { it > 0L }
            ?: dateValue(payload["updatedAt"]).takeIf { it > 0L }
            ?: System.currentTimeMillis()
        val updated = dateValue(payload["updatedAt"]).takeIf { it > 0L } ?: created
        val questions = payload.array("questions").mapIndexedNotNull { index, element ->
            (element as? JsonObject)?.let { parseQuestion(it, id, index, created + index) }
        }
        val title = payload.string("title").ifBlank { "Untitled set" }.take(120)
        val subject = payload.string("subject").ifBlank { "General" }.take(60)
        val set = SetEntity(
            id = id,
            title = title,
            titleMl = payload.string("titleMl").take(120),
            subject = subject,
            mainTopic = Topics.normalize(payload.string("mainTopic"), title, subject),
            subTopic = Topics.subTopic(payload.string("subTopic"), title),
            createdAt = created,
            updatedAt = updated,
            source = payload.string("source")
                .takeIf { it in setOf("demo", "manual", "scan", "import") }
                ?: "scan",
            imageUrisJson = null,
            synced = true
        )
        return set to questions
    }

    fun parseAttempt(payload: JsonObject): AttemptEntity? {
        return if (payload["questions"] is JsonArray) {
            parseWebAttempt(payload)
        } else {
            parseLegacyAttempt(payload, payload.string("setId").ifBlank { null }, payload.string("setTitle"))
        }
    }

    private fun parseQuestion(
        payload: JsonObject,
        setId: String,
        order: Int,
        createdAt: Long
    ): QuestionEntity? {
        val en = payload["en"] as? JsonObject
        val enOptions = if (en != null) {
            stringArray(en["options"])
        } else {
            stringArray(payload["optionsEn"])
        }
        if (enOptions.size < 2) return null

        val options = enOptions.take(MAX_OPTIONS)
        val answerIndex = if (en != null) {
            ((payload["answer"] as? JsonPrimitive)?.intOrNull ?: 0)
                .coerceIn(0, options.lastIndex)
        } else {
            letterIndex(payload.string("correctOption")) ?: 0
        }
        val letter = LETTERS[answerIndex.coerceIn(0, options.lastIndex)]

        val ml = payload["ml"] as? JsonObject
        val mlStem = if (ml != null) ml.string("stem") else payload.string("questionMl")
        val rawMlOptions = if (ml != null) stringArray(ml["options"]) else stringArray(payload["optionsMl"])
        val hasMl = mlStem.isNotBlank() && rawMlOptions.size == enOptions.size
        val mlOptions = if (hasMl) rawMlOptions.take(MAX_OPTIONS) else emptyList()

        return QuestionEntity(
            id = payload.string("id").ifBlank { "q_${setId}_$order" },
            setId = setId,
            order = order,
            questionEn = (if (en != null) en.string("stem") else payload.string("questionEn"))
                .ifBlank { mlStem },
            questionMl = mlStem,
            optionAEn = options.getOrElse(0) { "" },
            optionBEn = options.getOrElse(1) { "" },
            optionCEn = options.getOrElse(2) { "" },
            optionDEn = options.getOrElse(3) { "" },
            optionEEn = options.getOrElse(4) { "" },
            optionFEn = options.getOrElse(5) { "" },
            optionAMl = mlOptions.getOrElse(0) { "" },
            optionBMl = mlOptions.getOrElse(1) { "" },
            optionCMl = mlOptions.getOrElse(2) { "" },
            optionDMl = mlOptions.getOrElse(3) { "" },
            optionEMl = mlOptions.getOrElse(4) { "" },
            optionFMl = mlOptions.getOrElse(5) { "" },
            optionCount = options.size.coerceIn(2, MAX_OPTIONS),
            correctOption = letter,
            explanationEn = if (en != null) en.string("explanation") else payload.string("explanationEn"),
            explanationMl = if (ml != null) ml.string("explanation") else payload.string("explanationMl"),
            topic = payload.string("topic").ifBlank { "General" }.take(60),
            difficulty = payload.string("difficulty").lowercase()
                .takeIf { it in setOf("easy", "medium", "hard") } ?: "medium",
            confidence = ((payload["confidence"] as? JsonPrimitive)?.intOrNull ?: 80)
                .coerceIn(0, 100),
            createdAt = createdAt,
            synced = true
        )
    }

    private fun parseWebAttempt(payload: JsonObject): AttemptEntity? {
        val questions = payload.array("questions").map { it as? JsonObject }
        if (questions.any { it == null }) return null
        val responses = payload.array("responses")
        val flags = payload.array("flags")

        val records = questions.mapIndexedNotNull { index, question ->
            val item = question ?: return@mapIndexedNotNull null
            val en = item["en"] as? JsonObject ?: return@mapIndexedNotNull null
            val options = stringArray(en["options"]).take(MAX_OPTIONS)
            if (options.size < 2) return@mapIndexedNotNull null

            val answer = ((item["answer"] as? JsonPrimitive)?.intOrNull ?: 0)
                .coerceIn(0, options.lastIndex)
            val selectedIndex = (responses.getOrNull(index) as? JsonPrimitive)?.intOrNull
            val selectedLetter = selectedIndex?.let { LETTERS.getOrNull(it) }
            val ml = item["ml"] as? JsonObject
            val mlStem = ml?.string("stem").orEmpty()
            val rawMlOptions = if (ml != null) stringArray(ml["options"]) else emptyList()
            val mlOptions = if (mlStem.isNotBlank() && rawMlOptions.size == options.size) {
                rawMlOptions.take(MAX_OPTIONS)
            } else {
                emptyList()
            }

            AnswerRecord(
                questionId = item.string("id").ifBlank { "q_${payload.string("id")}_$index" },
                questionEn = en.string("stem").ifBlank { mlStem },
                questionMl = mlStem,
                optionsEn = options,
                optionsMl = mlOptions,
                correctOption = LETTERS[answer],
                selectedOption = selectedLetter,
                explanationEn = en.string("explanation"),
                explanationMl = ml?.string("explanation").orEmpty(),
                topic = item.string("topic").ifBlank { "General" },
                difficulty = item.string("difficulty").lowercase()
                    .takeIf { it in setOf("easy", "medium", "hard") } ?: "medium",
                timeSpentSeconds = 0,
                flagged = (flags.getOrNull(index) as? JsonPrimitive)
                    ?.contentOrNull?.toBooleanStrictOrNull() == true
            )
        }
        if (records.isEmpty()) return null

        val finished = dateValue(payload["finishedAt"]).takeIf { it > 0L }
            ?: System.currentTimeMillis()
        val started = dateValue(payload["startedAt"]).takeIf { it > 0L } ?: finished
        return buildAttempt(
            id = payload.string("id").ifBlank { "att_${UUID.randomUUID()}" },
            setId = payload.string("setId").ifBlank { null },
            setTitle = payload.string("setTitle").ifBlank { "Attempt" },
            subject = payload.string("subject").ifBlank { "General" },
            mode = if (payload.string("mode") == "practice") QuizMode.PRACTICE else QuizMode.EXAM,
            records = records,
            startedAt = started,
            completedAt = finished,
            durationSeconds = ((payload["durationSec"] as? JsonPrimitive)?.intOrNull ?: 0)
                .coerceAtLeast(0)
        )
    }

    private fun parseLegacyAttempt(
        payload: JsonObject,
        setId: String?,
        setTitle: String?
    ): AttemptEntity? {
        val answers = payload["answers"] as? JsonArray ?: return null
        val records = runCatching {
            AppJson.decodeFromString(
                ListSerializer(AnswerRecord.serializer()),
                answers.toString()
            )
        }.getOrNull().orEmpty()
        if (records.isEmpty()) return null

        val started = dateValue(payload["startedAt"])
        val finished = dateValue(payload["completedAt"])
            .takeIf { it > 0L }
            ?: dateValue(payload["finishedAt"]).takeIf { it > 0L }
            ?: System.currentTimeMillis()
        val duration = ((payload["durationSeconds"] as? JsonPrimitive)?.intOrNull
            ?: (payload["durationSec"] as? JsonPrimitive)?.intOrNull
            ?: 0).coerceAtLeast(0)

        return buildAttempt(
            id = payload.string("id").ifBlank { "att_${UUID.randomUUID()}" },
            setId = setId?.takeIf { it.isNotBlank() },
            setTitle = setTitle?.takeIf { it.isNotBlank() }
                ?: payload.string("title").ifBlank { "Attempt" },
            subject = payload.string("subject").ifBlank { "General" },
            mode = if (payload.string("mode") == QuizMode.PRACTICE) QuizMode.PRACTICE else QuizMode.EXAM,
            records = records,
            startedAt = started.takeIf { it > 0L } ?: finished,
            completedAt = finished,
            durationSeconds = duration
        )
    }

    private fun buildAttempt(
        id: String,
        setId: String?,
        setTitle: String,
        subject: String,
        mode: String,
        records: List<AnswerRecord>,
        startedAt: Long,
        completedAt: Long,
        durationSeconds: Int
    ): AttemptEntity {
        val total = records.size
        val correct = records.count { it.isCorrect && !it.isSkipped }
        val skipped = records.count { it.isSkipped }
        val wrong = (total - correct - skipped).coerceAtLeast(0)
        return AttemptEntity(
            id = id,
            setId = setId,
            setTitle = setTitle,
            subject = subject,
            mode = mode,
            total = total,
            correct = correct,
            wrong = wrong,
            skipped = skipped,
            scorePercent = if (total == 0) 0 else ((correct * 100.0) / total).roundToInt(),
            durationSeconds = durationSeconds,
            startedAt = startedAt,
            completedAt = completedAt,
            answersJson = AppJson.encodeToString(
                ListSerializer(AnswerRecord.serializer()),
                records
            ),
            synced = true
        )
    }

    private fun stringArray(element: JsonElement?): List<String> =
        (element as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
            .orEmpty()

    private data class ExportedOptions(
        val en: List<String>,
        val ml: List<String>,
        val answer: Int
    )

    /**
     * The web drops blank options and expects both languages to have the same
     * number of entries. Keep only filled English slots, align Malayalam to
     * those slots, and remap the correct-answer index.
     */
    private fun exportOptions(
        en: List<String>,
        ml: List<String>,
        answerLetter: String
    ): ExportedOptions {
        val source = en.take(MAX_OPTIONS)
        val keep = source.indices.filter { source[it].isNotBlank() }
        if (keep.isEmpty()) {
            return ExportedOptions(en = listOf("—"), ml = emptyList(), answer = 0)
        }
        val enOut = keep.map { source[it] }
        val mlOut = keep.map { ml.getOrNull(it).orEmpty() }
        val answer = keep.indexOf(answerIndex(answerLetter)).takeIf { it >= 0 } ?: 0
        val hasMl = mlOut.size == enOut.size && mlOut.all { it.isNotBlank() }
        return ExportedOptions(
            en = enOut,
            ml = if (hasMl) mlOut else emptyList(),
            answer = answer.coerceIn(0, enOut.lastIndex)
        )
    }

    private fun decodeAnswers(raw: String): List<AnswerRecord> =
        runCatching {
            AppJson.decodeFromString(ListSerializer(AnswerRecord.serializer()), raw)
        }.getOrDefault(emptyList())

    private fun dateValue(element: JsonElement?): Long {
        val primitive = element as? JsonPrimitive ?: return 0L
        primitive.longOrNull?.let { return it }
        return parseJsonDate(primitive.contentOrNull)
    }

    fun answerIndex(letter: String): Int =
        LETTERS.indexOf(letter.trim().uppercase()).takeIf { it >= 0 } ?: 0

    private fun letterIndex(letter: String?): Int? =
        letter?.trim()?.uppercase()?.let { LETTERS.indexOf(it).takeIf { index -> index >= 0 } }
}
