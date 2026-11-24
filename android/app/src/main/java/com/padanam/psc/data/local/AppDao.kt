package com.padanam.psc.data.local

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.padanam.psc.data.model.SetSummary
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {

    // ---------------------------------------------------------------- sets
    @Query("SELECT * FROM sets ORDER BY createdAt DESC")
    fun observeSets(): Flow<List<SetEntity>>

    @Query(
        """
        SELECT s.*,
            (SELECT COUNT(*) FROM questions q WHERE q.setId = s.id) AS questionCount,
            (SELECT MAX(a.scorePercent) FROM attempts a WHERE a.setId = s.id) AS bestScore,
            (SELECT COUNT(*) FROM attempts a WHERE a.setId = s.id) AS attemptCount,
            (SELECT MAX(a.completedAt) FROM attempts a WHERE a.setId = s.id) AS lastAttemptAt
        FROM sets s
        ORDER BY s.createdAt DESC
        """
    )
    fun observeSetSummaries(): Flow<List<SetSummary>>

    @Query(
        """
        SELECT s.*,
            (SELECT COUNT(*) FROM questions q WHERE q.setId = s.id) AS questionCount,
            (SELECT MAX(a.scorePercent) FROM attempts a WHERE a.setId = s.id) AS bestScore,
            (SELECT COUNT(*) FROM attempts a WHERE a.setId = s.id) AS attemptCount,
            (SELECT MAX(a.completedAt) FROM attempts a WHERE a.setId = s.id) AS lastAttemptAt
        FROM sets s
        WHERE s.id = :setId
        """
    )
    fun observeSetSummary(setId: String): Flow<SetSummary?>

    @Query("SELECT * FROM sets WHERE id = :setId")
    suspend fun getSet(setId: String): SetEntity?

    @Query("SELECT COUNT(*) FROM sets")
    fun observeSetCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM questions")
    fun observeQuestionCount(): Flow<Int>

    @Query("SELECT * FROM sets ORDER BY createdAt DESC")
    suspend fun setsOnce(): List<SetEntity>

    @Query(
        """
        SELECT s.*,
            (SELECT COUNT(*) FROM questions q WHERE q.setId = s.id) AS questionCount,
            (SELECT MAX(a.scorePercent) FROM attempts a WHERE a.setId = s.id) AS bestScore,
            (SELECT COUNT(*) FROM attempts a WHERE a.setId = s.id) AS attemptCount,
            (SELECT MAX(a.completedAt) FROM attempts a WHERE a.setId = s.id) AS lastAttemptAt
        FROM sets s
        ORDER BY s.updatedAt DESC
        """
    )
    suspend fun setSummariesOnce(): List<SetSummary>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSet(set: SetEntity)

    /**
     * Updates the row in place. `INSERT OR REPLACE` on a set would delete and
     * re-insert the parent row, which fires the questions foreign-key cascade
     * and silently empties the set.
     */
    @Update
    suspend fun updateSet(set: SetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSets(sets: List<SetEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRemoteSets(sets: List<SetEntity>): List<Long>

    @Query("UPDATE sets SET synced = 1 WHERE id = :id")
    suspend fun markSetSynced(id: String)

    @Query("DELETE FROM sets WHERE id = :setId")
    suspend fun deleteSetById(setId: String)

    @Query("SELECT * FROM sets WHERE synced = 0")
    suspend fun pendingSets(): List<SetEntity>

    @Query("SELECT COUNT(*) FROM sets WHERE synced = 0")
    fun observePendingSetCount(): Flow<Int>

    @Query("UPDATE sets SET synced = 1")
    suspend fun markAllSetsSynced()

    // ----------------------------------------------------------- questions
    @Query("SELECT * FROM questions WHERE setId = :setId ORDER BY `order` ASC")
    fun observeQuestions(setId: String): Flow<List<QuestionEntity>>

    @Query("SELECT * FROM questions WHERE setId = :setId ORDER BY `order` ASC")
    suspend fun questionsOnce(setId: String): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE id IN (:ids)")
    suspend fun questionsByIds(ids: List<String>): List<QuestionEntity>

    @Query("SELECT * FROM questions WHERE id = :id")
    suspend fun getQuestion(id: String): QuestionEntity?

    @Query("SELECT * FROM questions ORDER BY createdAt ASC")
    suspend fun allQuestionsOnce(): List<QuestionEntity>

    @Query("SELECT * FROM questions ORDER BY createdAt ASC")
    fun observeAllQuestions(): Flow<List<QuestionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestions(questions: List<QuestionEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuestion(question: QuestionEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRemoteQuestions(questions: List<QuestionEntity>): List<Long>

    @Query("UPDATE questions SET synced = 1 WHERE id = :id")
    suspend fun markQuestionSynced(id: String)

    @Query("SELECT * FROM questions WHERE synced = 0")
    suspend fun pendingQuestions(): List<QuestionEntity>

    @Query("SELECT COUNT(*) FROM questions WHERE synced = 0")
    fun observePendingQuestionCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM questions")
    suspend fun questionCountOnce(): Int

    @Query("UPDATE questions SET synced = 1")
    suspend fun markAllQuestionsSynced()

    @Query("DELETE FROM questions WHERE id = :questionId")
    suspend fun deleteQuestionById(questionId: String)

    @Transaction
    suspend fun saveQuestionAndTouchSet(set: SetEntity, question: QuestionEntity) {
        updateSet(set)
        insertQuestion(question)
    }

    @Transaction
    suspend fun deleteQuestionAndTouchSet(set: SetEntity, questionId: String) {
        updateSet(set)
        deleteQuestionById(questionId)
    }

    @Query("DELETE FROM questions WHERE setId = :setId")
    suspend fun deleteQuestionsForSet(setId: String)

    @Transaction
    suspend fun insertSetWithQuestions(set: SetEntity, questions: List<QuestionEntity>) {
        insertSet(set)
        insertQuestions(questions)
    }

    @Transaction
    suspend fun replaceSetWithQuestions(set: SetEntity, questions: List<QuestionEntity>) {
        deleteQuestionsForSet(set.id)
        insertSet(set)
        insertQuestions(questions)
    }

    /** Appends questions without touching the existing rows in the set. */
    @Transaction
    suspend fun appendQuestionsToSet(set: SetEntity, questions: List<QuestionEntity>) {
        updateSet(set)
        insertQuestions(questions)
    }

    @Transaction
    suspend fun deleteSetWithQuestions(setId: String) {
        deleteQuestionsForSet(setId)
        deleteSetById(setId)
    }

    // ------------------------------------------------------------- attempts
    @Query("SELECT * FROM attempts ORDER BY completedAt DESC")
    fun observeAttempts(): Flow<List<AttemptEntity>>

    @Query("SELECT * FROM attempts ORDER BY completedAt DESC")
    suspend fun allAttemptsOnce(): List<AttemptEntity>

    @Query("SELECT * FROM attempts WHERE id = :attemptId")
    fun observeAttempt(attemptId: String): Flow<AttemptEntity?>

    @Query("SELECT * FROM attempts WHERE id = :attemptId")
    suspend fun getAttempt(attemptId: String): AttemptEntity?

    @Query("SELECT * FROM attempts WHERE setId = :setId ORDER BY completedAt DESC")
    suspend fun attemptsForSet(setId: String): List<AttemptEntity>

    @Query("SELECT * FROM attempts WHERE synced = 0")
    suspend fun pendingAttempts(): List<AttemptEntity>

    @Query("SELECT COUNT(*) FROM attempts WHERE synced = 0")
    fun observePendingAttemptCount(): Flow<Int>

    @Query("UPDATE attempts SET synced = 1")
    suspend fun markAllAttemptsSynced()

    /** One-shot total used before swapping the active account. */
    @Query(
        """
        SELECT
            (SELECT COUNT(*) FROM sets WHERE synced = 0) +
            (SELECT COUNT(*) FROM questions WHERE synced = 0) +
            (SELECT COUNT(*) FROM attempts WHERE synced = 0) +
            (SELECT COUNT(*) FROM deck_progress WHERE synced = 0)
        """
    )
    suspend fun pendingChangeCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttempt(attempt: AttemptEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAttempts(attempts: List<AttemptEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRemoteAttempts(attempts: List<AttemptEntity>): List<Long>

    @Query("UPDATE attempts SET synced = 1 WHERE id = :id")
    suspend fun markAttemptSynced(id: String)

    // --------------------------------------------------------- deck progress
    @Query("SELECT * FROM deck_progress")
    fun observeDeckProgress(): Flow<List<DeckProgressEntity>>

    @Query("SELECT * FROM deck_progress")
    suspend fun deckProgressOnce(): List<DeckProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDeckProgress(entry: DeckProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDeckProgress(entries: List<DeckProgressEntity>)

    @Query("SELECT COUNT(*) FROM deck_progress WHERE synced = 0")
    fun observePendingDeckCount(): Flow<Int>

    @Query("UPDATE deck_progress SET synced = 1")
    suspend fun markAllDeckProgressSynced()

    @Query("DELETE FROM deck_progress")
    suspend fun deleteAllDeckProgress()

    // --------------------------------------------------------------- reset
    @Query("DELETE FROM attempts")
    suspend fun deleteAllAttempts()

    @Query("DELETE FROM questions")
    suspend fun deleteAllQuestions()

    @Query("DELETE FROM sets")
    suspend fun deleteAllSets()

    @Transaction
    suspend fun resetLibrary() {
        deleteAllAttempts()
        deleteAllQuestions()
        deleteAllSets()
        deleteAllDeckProgress()
    }

    /** Replaces the whole offline library with an imported backup. */
    @Transaction
    suspend fun replaceLibrary(
        sets: List<SetEntity>,
        questions: List<QuestionEntity>,
        attempts: List<AttemptEntity>
    ) {
        resetLibrary()
        insertSets(sets)
        insertQuestions(questions)
        insertAttempts(attempts)
    }
}
