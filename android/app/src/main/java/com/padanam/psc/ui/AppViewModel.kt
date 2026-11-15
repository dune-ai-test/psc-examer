package com.padanam.psc.ui

import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.padanam.psc.AppContainer
import com.padanam.psc.data.local.AttemptEntity
import com.padanam.psc.data.local.QuestionEntity
import com.padanam.psc.data.model.AnswerRecord
import com.padanam.psc.data.model.QuizMode
import com.padanam.psc.data.model.SetSummary
import com.padanam.psc.data.model.SyncOutcome
import com.padanam.psc.data.model.ActiveExam
import com.padanam.psc.data.prefs.AppSettings
import com.padanam.psc.data.remote.ScanException
import com.padanam.psc.util.Format
import com.padanam.psc.work.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class UiEvent(val message: String, val error: Boolean = false)

data class OperationState(
    val busy: Boolean = false,
    val message: String = "",
    val success: Boolean = false
)

class AppViewModel(private val container: AppContainer) : ViewModel() {

    private val dao = container.dao
    private val settingsRepo = container.settingsRepository
    private val libraryRepo = container.libraryRepository

    val settings: StateFlow<AppSettings> = settingsRepo.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSettings()
    )

    val sets = dao.observeSetSummaries().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    val attempts = dao.observeAttempts().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    val activeExam = container.examStore.activeExam.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), null
    )

    val questionCount = dao.observeQuestionCount().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), 0
    )

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 12)
    val events = _events.asSharedFlow()

    private val _aiOp = MutableStateFlow(OperationState())
    val aiOp = _aiOp.asStateFlow()

    private val _dbOp = MutableStateFlow(OperationState())
    val dbOp = _dbOp.asStateFlow()

    private val _syncOp = MutableStateFlow(OperationState())
    val syncOp = _syncOp.asStateFlow()

    fun questions(setId: String): Flow<List<QuestionEntity>> = dao.observeQuestions(setId)

    fun setSummary(setId: String): Flow<SetSummary?> = dao.observeSetSummary(setId)

    fun attempt(attemptId: String): Flow<AttemptEntity?> = dao.observeAttempt(attemptId)

    /** Decodes the self-contained answer snapshots stored on an attempt. */
    suspend fun answers(attemptId: String): List<AnswerRecord> = withContext(Dispatchers.IO) {
        val attempt = dao.getAttempt(attemptId) ?: return@withContext emptyList()
        container.attemptRepository.decodeAnswers(attempt)
    }

    // ------------------------------------------------------------- exam flow

    /**
     * Creates and persists a new exam. Returns the exam id, or null when the
     * set has no questions (the caller shows a friendly message).
     */
    suspend fun startExam(
        setId: String,
        mode: String,
        questionCount: Int,
        durationMinutes: Int,
        language: String
    ): String? = withContext(Dispatchers.IO) {
        val set = dao.getSet(setId) ?: return@withContext null
        val questions = dao.questionsOnce(setId)
        if (questions.isEmpty()) return@withContext null

        val wanted = if (questionCount <= 0) questions.size else questionCount.coerceAtMost(questions.size)
        val selected = if (mode == QuizMode.PRACTICE) {
            questions.shuffled().take(wanted)
        } else {
            questions.take(wanted)
        }

        val now = System.currentTimeMillis()
        val exam = ActiveExam(
            examId = "exam_${UUID.randomUUID()}",
            attemptId = "att_${UUID.randomUUID()}",
            setId = setId,
            title = set.title,
            subject = set.subject,
            mode = mode,
            questionIds = selected.map { it.id },
            durationSeconds = if (mode == QuizMode.EXAM) durationMinutes * 60 else 0,
            passPercent = 60,
            startedAtEpochMs = now,
            answers = emptyMap(),
            flagged = emptyList(),
            currentIndex = 0,
            language = language,
            timeByQuestion = emptyMap(),
            lastSavedAtEpochMs = now
        )
        container.examStore.save(exam)
        exam.examId
    }

    suspend fun abandonExam() = container.examStore.clear()

    // ------------------------------------------------------------ library

    fun deleteSet(setId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            libraryRepo.deleteSet(setId)
            settingsRepo.markDeletedSet(setId)
            val active = runCatching { container.examStore.activeExam.first() }.getOrNull()
            if (active?.setId == setId) container.examStore.clear()
            val current = runCatching { settingsRepo.current() }.getOrNull()
            emit(UiEvent("Set deleted."))
            if (current != null && current.autoSync && current.neonConfigured) {
                val outcome = container.syncRepository.sync(current)
                reportSync(outcome, silentSuccess = true)
            }
        }
    }

    fun renameSet(setId: String, title: String) {
        viewModelScope.launch(Dispatchers.IO) {
            libraryRepo.renameSet(setId, title)
            val active = runCatching { container.examStore.activeExam.first() }.getOrNull()
            if (active?.setId == setId) {
                container.examStore.save(active.copy(title = title.trim().take(80)))
            }
            emit(UiEvent("Set renamed."))
        }
    }

    fun seedDemo() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { libraryRepo.seedDemo() }
                .onSuccess { emit(UiEvent("Demo set added — 10 bilingual questions.")) }
                .onFailure { emit(UiEvent(it.message ?: "Couldn't add the demo set.", error = true)) }
        }
    }

    fun exportLibrary() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val json = libraryRepo.exportJson()
                val folder = File(container.appContext.cacheDir, "exports").apply { mkdirs() }
                val file = File(folder, "padanam-${Format.exportStamp()}.json")
                file.writeText(json)
                val uri = FileProvider.getUriForFile(
                    container.appContext,
                    "${container.appContext.packageName}.files",
                    file
                )
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Padanam library export")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(send, "Export Padanam library")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                container.appContext.startActivity(chooser)
            }.onFailure {
                emit(UiEvent(it.message ?: "Couldn't build the export.", error = true))
            }
        }
    }

    fun shareResult(title: String, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        container.appContext.startActivity(
            Intent.createChooser(send, "Share result").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun resetLibrary(clearNeon: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            container.examStore.clear()
            dao.resetLibrary()
            runCatching { settingsRepo.clearDeletedSets() }
            val current = runCatching { settingsRepo.current() }.getOrNull()
            if (clearNeon && current != null && current.neonConfigured) {
                val outcome = container.syncRepository.clearRemote(current)
                if (!outcome.ok) {
                    emit(UiEvent("Local library cleared. Neon: ${outcome.message}", error = true))
                    return@launch
                }
            }
            emit(UiEvent("Library cleared."))
        }
    }

    // ------------------------------------------------------------- settings

    fun saveProfile(name: String, language: String, dailyGoal: Int, reminders: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.saveProfile(name, language, dailyGoal, reminders)
            ReminderScheduler.schedule(container.appContext, reminders)
            emit(UiEvent("Profile saved."))
        }
    }

    fun setLanguage(language: String) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.updateLanguage(language)
        }
    }

    fun setReminders(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.updateReminders(enabled)
            ReminderScheduler.schedule(container.appContext, enabled)
            emit(UiEvent(if (enabled) "Daily reminders on." else "Daily reminders off."))
        }
    }

    fun setDailyGoal(goal: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.updateDailyGoal(goal)
        }
    }

    fun saveAi(geminiKey: String, geminiModel: String) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.saveAi(geminiKey, geminiModel)
            emit(UiEvent("Gemini settings saved."))
        }
    }

    fun saveDatabase(neonUrl: String, neonEndpoint: String, autoSync: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.saveDatabase(neonUrl, neonEndpoint, autoSync)
            emit(UiEvent("Neon settings saved."))
        }
    }

    fun testGemini(geminiKey: String, geminiModel: String) {
        if (_aiOp.value.busy) return
        viewModelScope.launch(Dispatchers.IO) {
            _aiOp.value = OperationState(busy = true, message = "Checking key…")
            _aiOp.value = try {
                val message = container.geminiRepository.testKey(geminiKey, geminiModel)
                OperationState(message = message, success = true)
            } catch (error: ScanException) {
                OperationState(message = error.message ?: "Gemini test failed.", success = false)
            } catch (error: Exception) {
                OperationState(message = error.message ?: "Gemini test failed.", success = false)
            }
        }
    }

    fun testNeon(url: String, endpoint: String) {
        if (_dbOp.value.busy) return
        viewModelScope.launch(Dispatchers.IO) {
            _dbOp.value = OperationState(busy = true, message = "Connecting to Neon…")
            _dbOp.value = try {
                val message = container.neonRepository.testConnection(url, endpoint)
                OperationState(message = message, success = true)
            } catch (error: Exception) {
                OperationState(message = error.message ?: "Neon test failed.", success = false)
            }
        }
    }

    fun syncNow(silent: Boolean = false) {
        if (_syncOp.value.busy) return
        viewModelScope.launch(Dispatchers.IO) {
            val current = runCatching { settingsRepo.current() }.getOrNull()
            if (current == null || !current.neonConfigured) {
                if (!silent) {
                    _syncOp.value = OperationState(
                        message = "Add your Neon connection string in Settings first.",
                        success = false
                    )
                }
                return@launch
            }
            _syncOp.value = OperationState(busy = true, message = "Syncing with Neon…")
            val outcome = container.syncRepository.sync(current)
            reportSync(outcome, silent)
        }
    }

    private suspend fun reportSync(outcome: SyncOutcome, silentSuccess: Boolean) {
        if (outcome.ok) {
            settingsRepo.markSynced(outcome.message)
            _syncOp.value = OperationState(message = outcome.message, success = true)
            if (!silentSuccess) emit(UiEvent(outcome.message))
        } else {
            settingsRepo.markSyncFailed(outcome.message)
            _syncOp.value = OperationState(message = outcome.message, success = false)
            emit(UiEvent(outcome.message, error = true))
        }
    }

    fun clearAiStatus() {
        _aiOp.value = OperationState()
    }

    fun clearDbStatus() {
        _dbOp.value = OperationState()
    }

    fun emit(event: UiEvent) {
        _events.tryEmit(event)
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AppViewModel(container) as T
                }
            }
    }
}
