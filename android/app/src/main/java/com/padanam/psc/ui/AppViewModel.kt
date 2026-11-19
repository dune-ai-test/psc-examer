package com.padanam.psc.ui

import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.padanam.psc.AppContainer
import com.padanam.psc.data.local.AttemptEntity
import com.padanam.psc.data.local.QuestionEntity
import com.padanam.psc.data.model.ActiveExam
import com.padanam.psc.data.model.AnswerRecord
import com.padanam.psc.data.model.QuizMode
import com.padanam.psc.data.model.SetSummary
import com.padanam.psc.data.model.SyncOutcome
import com.padanam.psc.data.prefs.AppSettings
import com.padanam.psc.data.remote.ServerApi
import com.padanam.psc.data.remote.ServerException
import com.padanam.psc.data.repo.WebBackup
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

    val session = container.authRepository.session.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), null
    )

    private val _authReady = MutableStateFlow(false)
    val authReady = _authReady.asStateFlow()

    private val _authBusy = MutableStateFlow(false)
    val authBusy = _authBusy.asStateFlow()

    private val _authError = MutableStateFlow("")
    val authError = _authError.asStateFlow()

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

    private val _syncOp = MutableStateFlow(OperationState())
    val syncOp = _syncOp.asStateFlow()

    private val _backupOp = MutableStateFlow(OperationState())
    val backupOp = _backupOp.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            restoreSession()
        }
    }

    fun questions(setId: String): Flow<List<QuestionEntity>> = dao.observeQuestions(setId)

    fun setSummary(setId: String): Flow<SetSummary?> = dao.observeSetSummary(setId)

    fun attempt(attemptId: String): Flow<AttemptEntity?> = dao.observeAttempt(attemptId)

    /** Decodes the self-contained answer snapshots stored on an attempt. */
    suspend fun answers(attemptId: String): List<AnswerRecord> = withContext(Dispatchers.IO) {
        val attempt = dao.getAttempt(attemptId) ?: return@withContext emptyList()
        container.attemptRepository.decodeAnswers(attempt)
    }

    // --------------------------------------------------------------- account

    private suspend fun restoreSession() {
        try {
            val stored = container.authRepository.current()
            if (stored == null) return
            settingsRepo.setAccountId(stored.user.id)
            if (stored.expired()) {
                container.authRepository.clear()
                return
            }
            val base = settingsRepo.current().serverUrl
            if (base.isBlank()) {
                container.authRepository.clear()
                return
            }
            try {
                val user = container.serverApi.session(base, stored.token)
                container.authRepository.save(stored.token, user, stored.expiresAt)
            } catch (error: ServerException) {
                if (error.expired || error.statusCode == 401) {
                    container.authRepository.clear()
                }
                /* A network failure keeps the cached session so the app still
                   works offline. The next sync will surface a 401. */
            } catch (_: Exception) {
                /* Same offline-first behaviour. */
            }
        } finally {
            _authReady.value = true
        }
    }

    fun signIn(serverUrl: String, username: String, password: String) {
        if (_authBusy.value) return
        val base = ServerApi.normalizeBase(serverUrl)
        if (base == null) {
            _authError.value = "Enter the https address of your Padanam site."
            return
        }
        if (username.isBlank() || password.isBlank()) {
            _authError.value = "Enter your username and password."
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _authBusy.value = true
            _authError.value = ""
            try {
                val result = container.serverApi.login(base, username, password)
                val previous = container.authRepository.current()
                val lastAccount = settingsRepo.current().lastAccountId
                val switched = (previous != null && previous.user.id != result.user.id) ||
                    (lastAccount.isNotBlank() && lastAccount != result.user.id)
                if (switched) {
                    /* Never let one account see another account's cached library. */
                    dao.resetLibrary()
                    container.examStore.clear()
                    container.scanSession.clear()
                    settingsRepo.clearSyncStatus()
                    settingsRepo.resetProfile()
                }
                settingsRepo.updateServerUrl(base)
                settingsRepo.setAccountId(result.user.id)
                settingsRepo.clearLegacySecrets()
                container.authRepository.save(result.token, result.user, result.expiresAt)
                _authError.value = ""
                emit(UiEvent("Signed in as ${result.user.label}."))
                syncInBackground()
            } catch (error: ServerException) {
                _authError.value = error.message ?: "Sign-in failed."
            } catch (error: Exception) {
                _authError.value = error.message ?: "Sign-in failed. Check your connection."
            } finally {
                _authBusy.value = false
            }
        }
    }

    fun signOut() {
        viewModelScope.launch(Dispatchers.IO) {
            val current = container.authRepository.current()
            val base = settingsRepo.current().serverUrl
            if (current != null && base.isNotBlank()) {
                container.serverApi.logout(base, current.token)
            }
            container.authRepository.clear()
            container.examStore.clear()
            container.scanSession.clear()
            dao.resetLibrary()
            settingsRepo.clearSyncStatus()
            _syncOp.value = OperationState()
            emit(UiEvent("Signed out. This device's library was cleared."))
        }
    }

    fun clearAuthError() {
        _authError.value = ""
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

    // ------------------------------------------------------------- library

    fun renameSet(setId: String, title: String, titleMl: String = "") {
        viewModelScope.launch(Dispatchers.IO) {
            if (!isAdmin()) {
                emit(UiEvent("Only the administrator can rename sets.", error = true))
                return@launch
            }
            libraryRepo.renameSet(setId, title, titleMl)
            val active = runCatching { container.examStore.activeExam.first() }.getOrNull()
            if (active?.setId == setId) {
                container.examStore.save(active.copy(title = title.trim().take(80)))
            }
            emit(UiEvent("Set details saved."))
            syncInBackground()
        }
    }

    fun seedDemo() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!isAdmin()) {
                emit(UiEvent("Only the administrator can add demo sets.", error = true))
                return@launch
            }
            runCatching { libraryRepo.seedDemo() }
                .onSuccess {
                    emit(UiEvent("Demo set added — 10 bilingual questions."))
                    syncInBackground()
                }
                .onFailure { emit(UiEvent(it.message ?: "Couldn't add the demo set.", error = true)) }
        }
    }

    fun exportLibrary() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!isAdmin()) {
                emit(UiEvent("Only the administrator can export the library.", error = true))
                return@launch
            }
            runCatching {
                val json = libraryRepo.exportJson(settingsRepo.current())
                val folder = File(container.appContext.cacheDir, "exports").apply { mkdirs() }
                val file = File(folder, "padanam-backup-${Format.exportStamp()}.json")
                file.writeText(json)
                val uri = FileProvider.getUriForFile(
                    container.appContext,
                    "${container.appContext.packageName}.files",
                    file
                )
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Padanam library backup")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(send, "Export Padanam backup")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                container.appContext.startActivity(chooser)
            }.onFailure {
                emit(UiEvent(it.message ?: "Couldn't build the export.", error = true))
            }
        }
    }

    /**
     * Restores a web backup or an older Android export. The file replaces the
     * offline library and profile, then syncs the restored copy to the account
     * workspace.
     */
    fun importLibrary(uri: Uri) {
        if (_backupOp.value.busy) return
        viewModelScope.launch(Dispatchers.IO) {
            if (!isAdmin()) {
                _backupOp.value = OperationState(
                    message = "Only the administrator can import a backup.",
                    success = false
                )
                emit(UiEvent("Only the administrator can import a backup.", error = true))
                return@launch
            }
            _backupOp.value = OperationState(busy = true, message = "Reading backup…")
            runCatching {
                val text = container.appContext.contentResolver.openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: error("Couldn't open the selected file.")
                if (text.isBlank()) error("That file is empty.")
                val backup = WebBackup.decode(text)
                    ?: error("That file is not a Padanam backup.")

                libraryRepo.replaceWith(backup)

                val current = settingsRepo.current()
                val profile = backup.profile
                if (profile != null) {
                    settingsRepo.saveProfile(
                        name = profile.name.ifBlank { current.name },
                        target = profile.target,
                        examDate = profile.examDate,
                        language = profile.language ?: current.language,
                        dailyGoal = profile.dailyGoal ?: current.dailyGoal,
                        reminders = profile.reminders ?: current.reminders
                    )
                    ReminderScheduler.schedule(
                        container.appContext,
                        profile.reminders ?: current.reminders
                    )
                }

                container.examStore.clear()
                container.scanSession.clear()
                backup
            }.onSuccess { backup ->
                val message = "Imported ${backup.sets.size} " +
                    (if (backup.sets.size == 1) "set" else "sets") +
                    " and ${backup.attempts.size} " +
                    (if (backup.attempts.size == 1) "attempt" else "attempts") + "."
                _backupOp.value = OperationState(message = message, success = true)
                emit(UiEvent(message))
                syncInBackground()
            }.onFailure {
                val message = it.message ?: "Couldn't import that backup."
                _backupOp.value = OperationState(message = message, success = false)
                emit(UiEvent(message, error = true))
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

    // ------------------------------------------------------------- settings

    fun saveProfile(
        name: String,
        target: String,
        examDate: String,
        language: String,
        dailyGoal: Int,
        reminders: Boolean
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.saveProfile(name, target, examDate, language, dailyGoal, reminders)
            ReminderScheduler.schedule(container.appContext, reminders)
            emit(UiEvent("Profile saved."))
            syncInBackground()
        }
    }

    fun setLanguage(language: String) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.updateLanguage(language)
            syncInBackground()
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
            syncInBackground()
        }
    }

    fun setAutoSync(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.updateAutoSync(enabled)
            emit(UiEvent(if (enabled) "Auto-sync on." else "Auto-sync off."))
            if (enabled) syncInBackground()
        }
    }

    /** Saves the deployed Padanam site used for /api/auth, /api/sync and /api/scan. */
    fun saveServer(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val base = ServerApi.normalizeBase(url)
            if (base == null) {
                _syncOp.value = OperationState(
                    message = "Enter the https address of your Padanam site.",
                    success = false
                )
                return@launch
            }
            settingsRepo.updateServerUrl(base)
            emit(UiEvent("Server address saved."))
            if (container.authRepository.current() != null) syncNow()
        }
    }

    // ---------------------------------------------------------------- sync

    fun syncNow(silent: Boolean = false) {
        if (_syncOp.value.busy) return
        viewModelScope.launch(Dispatchers.IO) {
            val current = container.authRepository.current()
            if (current == null) {
                if (!silent) {
                    _syncOp.value = OperationState(
                        message = "Sign in to sync your account.",
                        success = false
                    )
                }
                return@launch
            }
            val settings = settingsRepo.current()
            if (settings.serverUrl.isBlank()) {
                if (!silent) {
                    _syncOp.value = OperationState(
                        message = "Add the address of your Padanam site in Settings first.",
                        success = false
                    )
                }
                return@launch
            }
            _syncOp.value = OperationState(busy = true, message = "Syncing with your account…")
            val outcome = container.cloudSyncRepository.sync(
                settings.serverUrl,
                current,
                settings
            )
            reportSync(outcome, silent)
        }
    }

    /** Fire-and-forget sync used after a scan, an attempt or a profile edit. */
    fun syncInBackground() {
        viewModelScope.launch(Dispatchers.IO) {
            val settings = settingsRepo.current()
            if (!settings.autoSync) return@launch
            val current = container.authRepository.current() ?: return@launch
            if (settings.serverUrl.isBlank()) return@launch
            val outcome = container.cloudSyncRepository.sync(
                settings.serverUrl,
                current,
                settings
            )
            if (outcome.ok) {
                settingsRepo.markSynced(outcome.message)
            } else {
                settingsRepo.markSyncFailed(outcome.message)
            }
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

    private suspend fun isAdmin(): Boolean =
        container.authRepository.current()?.user?.isAdmin == true

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
