package com.padanam.psc.data.model

import androidx.room.Embedded
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Language codes used by the UI and stored with active exams. */
object Lang {
    const val EN = "en"
    const val ML = "ml"

    fun normalize(value: String?): String = if (value == ML) ML else EN
}

object QuizMode {
    const val PRACTICE = "practice"
    const val EXAM = "exam"
}

/** Device-wide appearance choice; "system" follows the phone setting. */
object ThemeMode {
    const val LIGHT = "light"
    const val DARK = "dark"
    const val SYSTEM = "system"

    fun normalize(value: String?): String = when (value) {
        LIGHT, DARK -> value
        else -> SYSTEM
    }
}

/**
 * The view an administrator has chosen. A real admin account can switch to
 * the read-only learner view; learners are always learners regardless of the
 * stored value.
 */
object ViewMode {
    const val ADMIN = "admin"
    const val LEARNER = "learner"

    fun normalize(value: String?): String = if (value == LEARNER) LEARNER else ADMIN
}

/** Raw structured output from the scanning service. */
@Serializable
data class AiScanResult(
    val title: String = "",
    val subject: String = "General",
    val questions: List<AiQuestion> = emptyList()
)

@Serializable
data class AiQuestion(
    @SerialName("question_en") val questionEn: String = "",
    @SerialName("question_ml") val questionMl: String = "",
    val options: List<AiOption> = emptyList(),
    @SerialName("correct_option") val correctOption: String = "",
    @SerialName("explanation_en") val explanationEn: String = "",
    @SerialName("explanation_ml") val explanationMl: String = "",
    val topic: String = "General",
    val difficulty: String = "medium",
    val confidence: Int = 80
)

@Serializable
data class AiOption(
    val letter: String = "",
    @SerialName("text_en") val textEn: String = "",
    @SerialName("text_ml") val textMl: String = ""
)

/**
 * A self-contained snapshot of one answered question. Keeping the text and
 * explanation inside the attempt means old results remain reviewable even if
 * the source set is later deleted.
 */
@Serializable
data class AnswerRecord(
    val questionId: String,
    val questionEn: String = "",
    val questionMl: String = "",
    val optionsEn: List<String> = listOf("", "", "", ""),
    val optionsMl: List<String> = listOf("", "", "", ""),
    val correctOption: String = "A",
    val selectedOption: String? = null,
    val explanationEn: String = "",
    val explanationMl: String = "",
    val topic: String = "General",
    val difficulty: String = "medium",
    val timeSpentSeconds: Int = 0,
    val flagged: Boolean = false
) {
    val isCorrect: Boolean get() = selectedOption == correctOption
    val isSkipped: Boolean get() = selectedOption.isNullOrBlank()

    fun question(lang: String): String {
        return if (lang == Lang.ML && questionMl.isNotBlank()) questionMl else questionEn
    }

    fun options(lang: String): List<String> {
        return if (lang == Lang.ML && optionsMl.any { it.isNotBlank() }) optionsMl else optionsEn
    }

    fun explanation(lang: String): String {
        return if (lang == Lang.ML && explanationMl.isNotBlank()) explanationMl else explanationEn
    }

    companion object {
        private val letters = listOf("A", "B", "C", "D", "E", "F")
        fun letterAt(index: Int): String = letters.getOrElse(index) { "A" }
        fun isLetter(value: String): Boolean =
            value.trim().uppercase().take(1) in letters
    }
}

/**
 * An exam currently being taken. Persisted as JSON in DataStore so the timer
 * and every autosaved answer survive a process death. Started-at is absolute,
 * which means the countdown keeps running while the app is away, exactly as
 * the exam rules promise.
 */
@Serializable
data class ActiveExam(
    val examId: String,
    val attemptId: String,
    val setId: String,
    val title: String,
    val subject: String = "General",
    val mode: String = QuizMode.EXAM,
    val questionIds: List<String> = emptyList(),
    val durationSeconds: Int = 0,
    val passPercent: Int = 40,
    val startedAtEpochMs: Long = 0L,
    val answers: Map<String, String> = emptyMap(),
    val flagged: List<String> = emptyList(),
    val currentIndex: Int = 0,
    val language: String = Lang.EN,
    val timeByQuestion: Map<String, Int> = emptyMap(),
    val lastSavedAtEpochMs: Long = 0L
) {
    val total: Int get() = questionIds.size
    val answeredCount: Int get() = answers.size
    val currentQuestionId: String? get() = questionIds.getOrNull(currentIndex)
}

/** A set that a scan draft can be appended to. */
data class ScanDestination(
    val id: String,
    val title: String,
    val subject: String,
    val questionCount: Int
)

/** Lightweight joined row used by the library and home screens. */
data class SetSummary(
    @Embedded val set: com.padanam.psc.data.local.SetEntity,
    val questionCount: Int,
    val bestScore: Int?,
    val attemptCount: Int,
    val lastAttemptAt: Long? = null
) {
    val id: String get() = set.id
    val title: String get() = set.title
    val subject: String get() = set.subject
    val createdAt: Long get() = set.createdAt
}

data class SyncOutcome(
    val ok: Boolean,
    val message: String,
    val pushed: Int = 0,
    val pulled: Int = 0
)
