package com.padanam.psc.data.repo

import com.padanam.psc.data.local.AppDao
import com.padanam.psc.data.local.AttemptEntity
import com.padanam.psc.data.local.QuestionEntity
import com.padanam.psc.data.model.ActiveExam
import com.padanam.psc.data.model.AnswerRecord
import com.padanam.psc.data.model.QuizMode
import com.padanam.psc.util.AppJson
import kotlinx.serialization.builtins.ListSerializer

class AttemptRepository(private val dao: AppDao) {

    suspend fun recordAttempt(exam: ActiveExam, questions: List<QuestionEntity>): AttemptEntity {
        val byId = questions.associateBy { it.id }
        val records = exam.questionIds.mapNotNull { questionId ->
            val question = byId[questionId] ?: return@mapNotNull null
            val selected = exam.answers[questionId]
                ?.uppercase()
                ?.take(1)
                ?.takeIf { it in setOf("A", "B", "C", "D") }
            AnswerRecord(
                questionId = question.id,
                questionEn = question.questionEn,
                questionMl = question.questionMl,
                optionsEn = question.optionsEn(),
                optionsMl = question.optionsMl(),
                correctOption = question.correctOption,
                selectedOption = selected,
                explanationEn = question.explanationEn,
                explanationMl = question.explanationMl,
                topic = question.topic,
                difficulty = question.difficulty,
                timeSpentSeconds = (exam.timeByQuestion[questionId] ?: 0).coerceAtLeast(0),
                flagged = questionId in exam.flagged
            )
        }

        val total = records.size
        val correct = records.count { it.isCorrect }
        val skipped = records.count { it.isSkipped }
        val wrong = (total - correct - skipped).coerceAtLeast(0)
        val completedAt = System.currentTimeMillis()
        val elapsed = ((completedAt - exam.startedAtEpochMs) / 1000).toInt().coerceAtLeast(0)
        val duration = if (exam.mode == QuizMode.EXAM && exam.durationSeconds > 0) {
            elapsed.coerceAtMost(exam.durationSeconds)
        } else {
            elapsed
        }

        val attempt = AttemptEntity(
            id = exam.attemptId,
            setId = exam.setId,
            setTitle = exam.title,
            subject = exam.subject,
            mode = exam.mode,
            total = total,
            correct = correct,
            wrong = wrong,
            skipped = skipped,
            scorePercent = if (total == 0) 0 else Math.round(correct * 100f / total),
            durationSeconds = duration,
            startedAt = exam.startedAtEpochMs,
            completedAt = completedAt,
            answersJson = AppJson.encodeToString(ListSerializer(AnswerRecord.serializer()), records),
            synced = false
        )

        dao.insertAttempt(attempt)
        return attempt
    }

    fun decodeAnswers(attempt: AttemptEntity): List<AnswerRecord> =
        runCatching {
            AppJson.decodeFromString(
                ListSerializer(AnswerRecord.serializer()),
                attempt.answersJson
            )
        }.getOrDefault(emptyList())

    suspend fun previousAttempt(setId: String?, before: Long): AttemptEntity? {
        if (setId == null) return null
        return dao.attemptsForSet(setId)
            .filter { it.completedAt < before }
            .maxByOrNull { it.completedAt }
    }

    suspend fun bestAttempt(setId: String?): AttemptEntity? {
        if (setId == null) return null
        return dao.attemptsForSet(setId).maxByOrNull { it.scorePercent }
    }
}
