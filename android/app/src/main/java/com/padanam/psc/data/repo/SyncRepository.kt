package com.padanam.psc.data.repo

import com.padanam.psc.data.local.AppDao
import com.padanam.psc.data.local.AttemptEntity
import com.padanam.psc.data.local.QuestionEntity
import com.padanam.psc.data.local.SetEntity
import com.padanam.psc.data.model.SyncOutcome
import com.padanam.psc.data.prefs.AppSettings
import com.padanam.psc.data.prefs.SettingsRepository
import com.padanam.psc.data.remote.NeonException
import com.padanam.psc.data.remote.NeonQuery
import com.padanam.psc.data.remote.NeonRepository
import com.padanam.psc.data.remote.asLongOrNull
import com.padanam.psc.data.remote.asStringOrNull

/**
 * Explicit two-way sync with Neon. Local Room remains the source of truth for
 * reading, so the app is fully usable offline. A sync pushes any rows that are
 * still marked pending and then pulls the complete remote library (single-user
 * app, so there is no conflict resolution beyond insert-or-ignore).
 */
class SyncRepository(
    private val dao: AppDao,
    private val neon: NeonRepository,
    private val settingsRepository: SettingsRepository
) {

    suspend fun sync(settings: AppSettings): SyncOutcome {
        if (!settings.neonConfigured) {
            return SyncOutcome(false, "Add your Neon connection string in Settings first.")
        }

        return try {
            ensureSchema(settings)

            if (settings.deletedSetIds.isNotEmpty()) {
                runChunked(
                    settings,
                    settings.deletedSetIds.map { setId ->
                        NeonQuery("DELETE FROM sets WHERE id = \$1", listOf(setId))
                    }
                )
                settingsRepository.clearDeletedSets()
            }

            val sets = dao.pendingSets()
            val questions = dao.pendingQuestions()
            val attempts = dao.pendingAttempts()

            runChunked(settings, sets.map { it.toQuery() })
            runChunked(settings, questions.map { it.toQuery() })
            runChunked(settings, attempts.map { it.toQuery() })

            sets.forEach { dao.markSetSynced(it.id) }
            questions.forEach { dao.markQuestionSynced(it.id) }
            attempts.forEach { dao.markAttemptSynced(it.id) }

            val remoteSets = neon.query(
                settings.neonUrl,
                settings.neonEndpoint,
                "SELECT id, title, subject, created_at, source, image_uris_json FROM sets ORDER BY created_at ASC"
            )
            val remoteQuestions = neon.query(
                settings.neonUrl,
                settings.neonEndpoint,
                "SELECT id, set_id, ord, question_en, question_ml, option_a_en, option_b_en, " +
                    "option_c_en, option_d_en, option_a_ml, option_b_ml, option_c_ml, option_d_ml, " +
                    "correct_option, explanation_en, explanation_ml, topic, difficulty, confidence, " +
                    "created_at FROM questions ORDER BY created_at ASC"
            )
            val remoteAttempts = neon.query(
                settings.neonUrl,
                settings.neonEndpoint,
                "SELECT id, set_id, set_title, subject, mode, total, correct, wrong, skipped, " +
                    "score_percent, duration_seconds, started_at, completed_at, answers_json " +
                    "FROM attempts ORDER BY completed_at ASC"
            )

            val pulledSetRows = remoteSets.rows.indices.mapNotNull { remoteSets.toSet(it) }
            val pulledQuestionRows = remoteQuestions.rows.indices.mapNotNull { remoteQuestions.toQuestion(it) }
            val pulledAttemptRows = remoteAttempts.rows.indices.mapNotNull { remoteAttempts.toAttempt(it) }

            val newSets = if (pulledSetRows.isEmpty()) 0 else
                dao.insertRemoteSets(pulledSetRows).count { it > 0L }
            val newQuestions = if (pulledQuestionRows.isEmpty()) 0 else
                dao.insertRemoteQuestions(pulledQuestionRows).count { it > 0L }
            val newAttempts = if (pulledAttemptRows.isEmpty()) 0 else
                dao.insertRemoteAttempts(pulledAttemptRows).count { it > 0L }

            val pushed = sets.size + questions.size + attempts.size
            val pulled = newSets + newQuestions + newAttempts
            val message = if (pushed == 0 && pulled == 0) {
                "Everything is up to date."
            } else {
                "Pushed ${sets.size} sets · ${questions.size} questions · ${attempts.size} attempts. " +
                    "Pulled $pulled new."
            }
            SyncOutcome(true, message, pushed = pushed, pulled = pulled)
        } catch (error: NeonException) {
            val hint = if (error.message?.contains("relation", ignoreCase = true) == true ||
                error.message?.contains("schema", ignoreCase = true) == true
            ) {
                "${error.message} Open android/neon/schema.sql once in the Neon SQL editor if setup failed."
            } else {
                error.message ?: "Neon sync failed."
            }
            SyncOutcome(false, hint)
        } catch (error: Exception) {
            SyncOutcome(false, error.message ?: "Neon sync failed.")
        }
    }

    /** Clears the remote copy when the user resets the local library. */
    suspend fun clearRemote(settings: AppSettings): SyncOutcome {
        if (!settings.neonConfigured) {
            return SyncOutcome(true, "No Neon backup configured.")
        }
        return try {
            ensureSchema(settings)
            runChunked(
                settings,
                listOf(
                    NeonQuery("DELETE FROM attempts"),
                    NeonQuery("DELETE FROM questions"),
                    NeonQuery("DELETE FROM sets")
                )
            )
            settingsRepository.clearDeletedSets()
            SyncOutcome(true, "Neon copy cleared.")
        } catch (error: Exception) {
            SyncOutcome(false, error.message ?: "Couldn't clear the Neon copy.")
        }
    }

    private suspend fun ensureSchema(settings: AppSettings) {
        val statements = listOf(
            """
            CREATE TABLE IF NOT EXISTS sets (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                subject TEXT NOT NULL,
                created_at BIGINT NOT NULL,
                source TEXT NOT NULL DEFAULT 'scan',
                image_uris_json TEXT
            )
            """.trimIndent(),
            """
            CREATE TABLE IF NOT EXISTS questions (
                id TEXT PRIMARY KEY,
                set_id TEXT NOT NULL REFERENCES sets(id) ON DELETE CASCADE,
                ord INTEGER NOT NULL,
                question_en TEXT NOT NULL,
                question_ml TEXT NOT NULL,
                option_a_en TEXT NOT NULL,
                option_b_en TEXT NOT NULL,
                option_c_en TEXT NOT NULL,
                option_d_en TEXT NOT NULL,
                option_a_ml TEXT NOT NULL,
                option_b_ml TEXT NOT NULL,
                option_c_ml TEXT NOT NULL,
                option_d_ml TEXT NOT NULL,
                correct_option TEXT NOT NULL,
                explanation_en TEXT NOT NULL,
                explanation_ml TEXT NOT NULL,
                topic TEXT NOT NULL,
                difficulty TEXT NOT NULL,
                confidence INTEGER NOT NULL,
                created_at BIGINT NOT NULL
            )
            """.trimIndent(),
            "CREATE INDEX IF NOT EXISTS questions_set_id_idx ON questions (set_id)",
            """
            CREATE TABLE IF NOT EXISTS attempts (
                id TEXT PRIMARY KEY,
                set_id TEXT,
                set_title TEXT NOT NULL,
                subject TEXT NOT NULL,
                mode TEXT NOT NULL,
                total INTEGER NOT NULL,
                correct INTEGER NOT NULL,
                wrong INTEGER NOT NULL,
                skipped INTEGER NOT NULL,
                score_percent INTEGER NOT NULL,
                duration_seconds INTEGER NOT NULL,
                started_at BIGINT NOT NULL,
                completed_at BIGINT NOT NULL,
                answers_json TEXT NOT NULL
            )
            """.trimIndent(),
            "CREATE INDEX IF NOT EXISTS attempts_set_id_idx ON attempts (set_id)",
            "CREATE INDEX IF NOT EXISTS attempts_completed_at_idx ON attempts (completed_at)"
        )
        runChunked(settings, statements.map { NeonQuery(it) })
    }

    private suspend fun runChunked(settings: AppSettings, queries: List<NeonQuery>) {
        if (queries.isEmpty()) return
        queries.chunked(BATCH_SIZE).forEach { chunk ->
            val results = neon.batch(settings.neonUrl, settings.neonEndpoint, chunk)
            if (results.size != chunk.size) {
                throw NeonException("Neon returned ${results.size} results for ${chunk.size} statements.")
            }
        }
    }

    private fun SetEntity.toQuery() = NeonQuery(
        sql = """
            INSERT INTO sets (id, title, subject, created_at, source, image_uris_json)
            VALUES ($1, $2, $3, $4, $5, $6)
            ON CONFLICT (id) DO UPDATE SET
                title = EXCLUDED.title,
                subject = EXCLUDED.subject,
                created_at = EXCLUDED.created_at,
                source = EXCLUDED.source,
                image_uris_json = EXCLUDED.image_uris_json
        """.trimIndent(),
        params = listOf(id, title, subject, createdAt.toString(), source, imageUrisJson)
    )

    private fun QuestionEntity.toQuery() = NeonQuery(
        sql = """
            INSERT INTO questions (
                id, set_id, ord, question_en, question_ml, option_a_en, option_b_en,
                option_c_en, option_d_en, option_a_ml, option_b_ml, option_c_ml,
                option_d_ml, correct_option, explanation_en, explanation_ml, topic,
                difficulty, confidence, created_at
            ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10,
                      $11, $12, $13, $14, $15, $16, $17, $18, $19, $20)
            ON CONFLICT (id) DO UPDATE SET
                set_id = EXCLUDED.set_id,
                ord = EXCLUDED.ord,
                question_en = EXCLUDED.question_en,
                question_ml = EXCLUDED.question_ml,
                option_a_en = EXCLUDED.option_a_en,
                option_b_en = EXCLUDED.option_b_en,
                option_c_en = EXCLUDED.option_c_en,
                option_d_en = EXCLUDED.option_d_en,
                option_a_ml = EXCLUDED.option_a_ml,
                option_b_ml = EXCLUDED.option_b_ml,
                option_c_ml = EXCLUDED.option_c_ml,
                option_d_ml = EXCLUDED.option_d_ml,
                correct_option = EXCLUDED.correct_option,
                explanation_en = EXCLUDED.explanation_en,
                explanation_ml = EXCLUDED.explanation_ml,
                topic = EXCLUDED.topic,
                difficulty = EXCLUDED.difficulty,
                confidence = EXCLUDED.confidence,
                created_at = EXCLUDED.created_at
        """.trimIndent(),
        params = listOf(
            id, setId, order.toString(), questionEn, questionMl, optionAEn, optionBEn,
            optionCEn, optionDEn, optionAMl, optionBMl, optionCMl, optionDMl,
            correctOption, explanationEn, explanationMl, topic, difficulty,
            confidence.toString(), createdAt.toString()
        )
    )

    private fun AttemptEntity.toQuery() = NeonQuery(
        sql = """
            INSERT INTO attempts (
                id, set_id, set_title, subject, mode, total, correct, wrong, skipped,
                score_percent, duration_seconds, started_at, completed_at, answers_json
            ) VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14)
            ON CONFLICT (id) DO UPDATE SET
                set_id = EXCLUDED.set_id,
                set_title = EXCLUDED.set_title,
                subject = EXCLUDED.subject,
                mode = EXCLUDED.mode,
                total = EXCLUDED.total,
                correct = EXCLUDED.correct,
                wrong = EXCLUDED.wrong,
                skipped = EXCLUDED.skipped,
                score_percent = EXCLUDED.score_percent,
                duration_seconds = EXCLUDED.duration_seconds,
                started_at = EXCLUDED.started_at,
                completed_at = EXCLUDED.completed_at,
                answers_json = EXCLUDED.answers_json
        """.trimIndent(),
        params = listOf(
            id, setId, setTitle, subject, mode, total.toString(), correct.toString(),
            wrong.toString(), skipped.toString(), scorePercent.toString(),
            durationSeconds.toString(), startedAt.toString(), completedAt.toString(), answersJson
        )
    )

    private fun com.padanam.psc.data.remote.NeonResult.toSet(index: Int): SetEntity? {
        val row = row(index)
        val id = row["id"].asStringOrNull() ?: return null
        return SetEntity(
            id = id,
            title = row["title"].asStringOrNull().orEmpty(),
            subject = row["subject"].asStringOrNull().orEmpty().ifBlank { "General" },
            createdAt = row["created_at"].asLongOrNull() ?: 0L,
            source = row["source"].asStringOrNull().orEmpty().ifBlank { "scan" },
            imageUrisJson = row["image_uris_json"].asStringOrNull(),
            synced = true
        )
    }

    private fun com.padanam.psc.data.remote.NeonResult.toQuestion(index: Int): QuestionEntity? {
        val row = row(index)
        val id = row["id"].asStringOrNull() ?: return null
        val setId = row["set_id"].asStringOrNull() ?: return null
        return QuestionEntity(
            id = id,
            setId = setId,
            order = row["ord"].asStringOrNull()?.toIntOrNull() ?: 0,
            questionEn = row["question_en"].asStringOrNull().orEmpty(),
            questionMl = row["question_ml"].asStringOrNull().orEmpty(),
            optionAEn = row["option_a_en"].asStringOrNull().orEmpty(),
            optionBEn = row["option_b_en"].asStringOrNull().orEmpty(),
            optionCEn = row["option_c_en"].asStringOrNull().orEmpty(),
            optionDEn = row["option_d_en"].asStringOrNull().orEmpty(),
            optionAMl = row["option_a_ml"].asStringOrNull().orEmpty(),
            optionBMl = row["option_b_ml"].asStringOrNull().orEmpty(),
            optionCMl = row["option_c_ml"].asStringOrNull().orEmpty(),
            optionDMl = row["option_d_ml"].asStringOrNull().orEmpty(),
            correctOption = row["correct_option"].asStringOrNull().orEmpty().ifBlank { "A" },
            explanationEn = row["explanation_en"].asStringOrNull().orEmpty(),
            explanationMl = row["explanation_ml"].asStringOrNull().orEmpty(),
            topic = row["topic"].asStringOrNull().orEmpty().ifBlank { "General" },
            difficulty = row["difficulty"].asStringOrNull().orEmpty().ifBlank { "medium" },
            confidence = row["confidence"].asStringOrNull()?.toIntOrNull() ?: 80,
            createdAt = row["created_at"].asLongOrNull() ?: 0L,
            synced = true
        )
    }

    private fun com.padanam.psc.data.remote.NeonResult.toAttempt(index: Int): AttemptEntity? {
        val row = row(index)
        val id = row["id"].asStringOrNull() ?: return null
        return AttemptEntity(
            id = id,
            setId = row["set_id"].asStringOrNull(),
            setTitle = row["set_title"].asStringOrNull().orEmpty().ifBlank { "Attempt" },
            subject = row["subject"].asStringOrNull().orEmpty().ifBlank { "General" },
            mode = row["mode"].asStringOrNull().orEmpty().ifBlank { "exam" },
            total = row["total"].asStringOrNull()?.toIntOrNull() ?: 0,
            correct = row["correct"].asStringOrNull()?.toIntOrNull() ?: 0,
            wrong = row["wrong"].asStringOrNull()?.toIntOrNull() ?: 0,
            skipped = row["skipped"].asStringOrNull()?.toIntOrNull() ?: 0,
            scorePercent = row["score_percent"].asStringOrNull()?.toIntOrNull() ?: 0,
            durationSeconds = row["duration_seconds"].asStringOrNull()?.toIntOrNull() ?: 0,
            startedAt = row["started_at"].asLongOrNull() ?: 0L,
            completedAt = row["completed_at"].asLongOrNull() ?: 0L,
            answersJson = row["answers_json"].asStringOrNull().orEmpty().ifBlank { "[]" },
            synced = true
        )
    }

    companion object {
        private const val BATCH_SIZE = 25
    }
}
