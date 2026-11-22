package com.padanam.psc.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sets")
data class SetEntity(
    @PrimaryKey val id: String,
    val title: String,
    val titleMl: String = "",
    val subject: String,
    val mainTopic: String = "",
    val subTopic: String = "",
    val createdAt: Long,
    val updatedAt: Long = createdAt,
    val source: String = "scan",
    val imageUrisJson: String? = null,
    val synced: Boolean = false
)

@Entity(
    tableName = "questions",
    foreignKeys = [
        ForeignKey(
            entity = SetEntity::class,
            parentColumns = ["id"],
            childColumns = ["setId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("setId")]
)
data class QuestionEntity(
    @PrimaryKey val id: String,
    val setId: String,
    val order: Int,
    val questionEn: String,
    val questionMl: String,
    val optionAEn: String,
    val optionBEn: String,
    val optionCEn: String,
    val optionDEn: String,
    val optionEEn: String = "",
    val optionFEn: String = "",
    val optionAMl: String,
    val optionBMl: String,
    val optionCMl: String,
    val optionDMl: String,
    val optionEMl: String = "",
    val optionFMl: String = "",
    val optionCount: Int = 4,
    val correctOption: String,
    val explanationEn: String,
    val explanationMl: String,
    val topic: String,
    val difficulty: String,
    val confidence: Int,
    val createdAt: Long,
    val synced: Boolean = false
) {
    fun optionsEn(): List<String> =
        listOf(optionAEn, optionBEn, optionCEn, optionDEn, optionEEn, optionFEn)
            .take(optionCount.coerceIn(2, 6))

    fun optionsMl(): List<String> =
        listOf(optionAMl, optionBMl, optionCMl, optionDMl, optionEMl, optionFMl)
            .take(optionCount.coerceIn(2, 6))

    fun question(lang: String): String =
        if (lang == "ml" && questionMl.isNotBlank()) questionMl else questionEn

    fun options(lang: String): List<String> =
        if (lang == "ml" && optionsMl().any { it.isNotBlank() }) optionsMl() else optionsEn()

    fun explanation(lang: String): String =
        if (lang == "ml" && explanationMl.isNotBlank()) explanationMl else explanationEn
}

@Entity(tableName = "attempts")
data class AttemptEntity(
    @PrimaryKey val id: String,
    val setId: String?,
    val setTitle: String,
    val subject: String,
    val mode: String,
    val total: Int,
    val correct: Int,
    val wrong: Int,
    val skipped: Int,
    val scorePercent: Int,
    val durationSeconds: Int,
    val startedAt: Long,
    val completedAt: Long,
    val answersJson: String,
    val synced: Boolean = false
)
