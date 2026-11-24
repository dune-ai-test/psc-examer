package com.padanam.psc.ui

import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.padanam.psc.AppContainer
import com.padanam.psc.data.auth.AuthSession
import com.padanam.psc.data.local.AttemptEntity
import com.padanam.psc.data.local.QuestionEntity
import com.padanam.psc.data.model.ActiveExam
import com.padanam.psc.data.model.AnswerRecord
import com.padanam.psc.data.model.DeckCard
import com.padanam.psc.data.model.DeckOverview
import com.padanam.psc.data.model.Decks
import com.padanam.psc.data.model.QuizMode
import com.padanam.psc.data.model.SetSummary
import com.padanam.psc.data.model.SyncOutcome
import com.padanam.psc.data.model.TopicSuggestion
import com.padanam.psc.data.model.Topics
import com.padanam.psc.data.model.ViewMode
import com.padanam.psc.data.prefs.AppSettings
import com.padanam.psc.data.remote.ProviderModel
import com.padanam.psc.data.remote.ScanProviderStatus
import com.padanam.psc.data.remote.ServerApi
import com.padanam.psc.data.remote.ServerException
import com.padanam.psc.data.repo.QuestionDraft
import com.padanam.psc.data.repo.WebBackup
import com.padanam.psc.util.Format
import com.padanam.psc.work.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class UiEvent(val message: String, val error: Boolean = false)

data class OperationState(
    val busy: Boolean = false,
    val message: String = "",
    val success: Boolean = false
)

/** Admin-only scanner and sync diagnostics shown in Settings. */
data class DiagnosticsState(
    val busy: Boolean = false,
    val status: ScanProviderStatus? = null,
    val testResult: String = "",
    val models: List<ProviderModel> = emptyList(),
    val modelsProvider: String = "",
    val message: String = "",
    val error: String = ""
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

    /** Every login saved on this phone, in the order it was added. */
    val savedSessions = container.authRepository.sessions.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    private val _authReady = MutableStateFlow(false)
    val authReady = _authReady.asStateFlow()

    private val _authBusy = MutableStateFlow(false)
    val authBusy = _authBusy.asStateFlow()

    private val _authError = MutableStateFlow("")
    val authError = _authError.asStateFlow()

    private val _accountBusy = MutableStateFlow(false)
    val accountBusy = _accountBusy.asStateFlow()

    /**
     * Serialises Room writes with session changes. Without it a background
     * sync started for one account could finish after a switch and write that
     * account's sets under the next account.
     */
    private val syncMutex = Mutex()

    val sets = dao.observeSetSummaries().safeCache(emptyList()).stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    val attempts = dao.observeAttempts().safeCache(emptyList()).stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    val activeExam = container.examStore.activeExam.safeCache(null).stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), null
    )

    val questionCount = dao.observeQuestionCount().safeCache(0).stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), 0
    )

    val deckProgress = dao.observeDeckProgress().safeCache(emptyList()).stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )

    val deckSession = container.deckSessionStore.session.safeCache(null).stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), null
    )

    private val deckCards = combine(
        dao.observeSets(),
        dao.observeAllQuestions()
    ) { sets, questions -> Decks.buildCards(sets, questions) }
        .safeCache(emptyList())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val deckOverview = combine(deckCards, deckProgress) { cards, progress ->
        Decks.overview(cards, progress.associateBy { it.cardKey })
    }.safeCache(DeckOverview()).stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), DeckOverview()
    )

    val deckSummary = combine(deckCards, deckProgress, deckSession) { cards, progress, session ->
        Decks.summary(cards, progress.associateBy { it.cardKey }, session)
    }.safeCache(null).stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), null
    )

    val deckContinue = combine(deckCards, deckProgress, deckSession) { cards, progress, session ->
        Decks.continueInfo(cards, progress.associateBy { it.cardKey }, session)
    }.safeCache(null).stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), null
    )

    val online = container.networkMonitor.online.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), true
    )

    val pendingChanges = combine(
        dao.observePendingSetCount(),
        dao.observePendingQuestionCount(),
        dao.observePendingAttemptCount(),
        dao.observePendingDeckCount()
    ) { sets, questions, attempts, decks -> sets + questions + attempts + decks }
        .safeCache(0).stateIn(
            viewModelScope, SharingStarted.WhileSubscribed(5_000), 0
        )

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 12)
    val events = _events.asSharedFlow()

    private val _syncOp = MutableStateFlow(OperationState())
    val syncOp = _syncOp.asStateFlow()

    private val _backupOp = MutableStateFlow(OperationState())
    val backupOp = _backupOp.asStateFlow()

    private val _diagnostics = MutableStateFlow(DiagnosticsState())
    val diagnostics = _diagnostics.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            restoreSession()
        }
        viewModelScope.launch(Dispatchers.IO) {
            var wasOffline = false
            container.networkMonitor.online.collect { isOnline ->
                if (!isOnline) {
                    wasOffline = true
                } else if (wasOffline) {
                    wasOffline = false
                    val settings = settingsRepo.current()
                    if (settings.autoSync && settings.serverUrl.isNotBlank()) {
                        syncInBackground()
                    }
                }
            }
        }
    }

    fun questions(setId: String): Flow<List<QuestionEntity>> =
        dao.observeQuestions(setId).safeCache(emptyList())

    fun setSummary(setId: String): Flow<SetSummary?> =
        dao.observeSetSummary(setId).safeCache(null)

    fun attempt(attemptId: String): Flow<AttemptEntity?> =
        dao.observeAttempt(attemptId).safeCache(null)

    /** Decodes the self-contained answer snapshots stored on an attempt. */
    suspend fun answers(attemptId: String): List<AnswerRecord> = withContext(Dispatchers.IO) {
        val attempt = dao.getAttempt(attemptId) ?: return@withContext emptyList()
        container.attemptRepository.decodeAnswers(attempt)
    }

    // --------------------------------------------------------------- account

    private suspend fun restoreSession() {
        try {
            container.authRepository.pruneExpired()
            val stored = container.authRepository.current() ?: return
            val settings = settingsRepo.current()
            val base = stored.serverUrl.ifBlank { settings.serverUrl }

            /* Room belongs to lastAccountId. If the active account changed while
               the app was not running, start from an empty library instead of
               showing another account's sets. */
            if (settings.lastAccountId.isNotBlank() && settings.lastAccountId != stored.user.id) {
                resetLocalWorkspace()
            }
            settingsRepo.setAccountId(stored.user.id)
            settingsRepo.clearLegacySecrets()

            if (base.isBlank()) return
            try {
                val user = container.serverApi.session(base, stored.token)
                container.authRepository.save(stored.token, user, stored.expiresAt, base)
            } catch (error: ServerException) {
                if (error.expired || error.statusCode == 401) {
                    /* Drop only this login. Another saved account becomes
                       active automatically, if there is one. */
                    resetLocalWorkspace()
                    val next = container.authRepository.remove(stored.key)
                    if (next == null) return
                    settingsRepo.setAccountId(next.user.id)
                    val nextBase = next.serverUrl.ifBlank { base }
                    if (nextBase.isNotBlank()) settingsRepo.updateServerUrl(nextBase)
                    if (nextBase.isNotBlank() &&
                        runCatching { container.networkMonitor.online.first() }.getOrDefault(false)
                    ) {
                        val outcome = syncMutex.withLock {
                            container.cloudSyncRepository.sync(
                                nextBase,
                                next,
                                settingsRepo.current()
                            )
                        }
                        if (outcome.ok) settingsRepo.markSynced(outcome.message)
                    }
                }
                /* A network failure keeps the cached session so the app still
                   works offline. The next sync will surface a 401. */
            } catch (_: Exception) {
                /* Same offline-first behaviour. */
            }
        } catch (_: Exception) {
            /* A damaged preference file should show the sign-in screen, not
               kill the process before the first frame. */
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
                syncMutex.withLock {
                    val previous = container.authRepository.current()
                    val lastAccount = settingsRepo.current().lastAccountId
                    if (previous != null && previous.user.id != result.user.id) {
                        /* Adding an account must not throw away the work of the
                           account that is still open. Login is online-only, so the
                           sync below normally succeeds. */
                        val pending = dao.pendingChangeCount()
                        val currentSettings = settingsRepo.current()
                        val unsynced = pending > 0 ||
                            (currentSettings.profileUpdatedAt > 0L &&
                                currentSettings.profileUpdatedAt > currentSettings.lastSyncAt)
                        val previousBase = currentSettings.serverUrl.ifBlank { previous.serverUrl }
                        if (previousBase.isNotBlank()) {
                            val outcome = container.cloudSyncRepository.sync(
                                previousBase,
                                previous,
                                currentSettings
                            )
                            if (outcome.ok) {
                                settingsRepo.markSynced(outcome.message)
                            } else if (unsynced) {
                                _authError.value = "Couldn't save the current account's changes. " +
                                    outcome.message
                                return@launch
                            }
                        }
                        resetLocalWorkspace()
                    } else if (lastAccount.isNotBlank() && lastAccount != result.user.id) {
                        /* Never let one account see another account's cached library. */
                        resetLocalWorkspace()
                    }
                    settingsRepo.updateServerUrl(base)
                    settingsRepo.setAccountId(result.user.id)
                    settingsRepo.clearLegacySecrets()
                    container.authRepository.save(result.token, result.user, result.expiresAt, base)
                }
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

    /**
     * Switches to an account that is already saved on this phone. No password
     * is asked again; the current account is synced first so local work is not
     * lost, then Room is refilled from the target account's workspace.
     */
    fun switchAccount(key: String) {
        if (_accountBusy.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _accountBusy.value = true
            try {
                val target = container.authRepository.list().firstOrNull { it.key == key }
                if (target == null) {
                    emit(UiEvent("That saved account is no longer on this phone.", error = true))
                    return@launch
                }
                val current = container.authRepository.current()
                if (current?.key == target.key) return@launch
                val settings = settingsRepo.current()
                val base = target.serverUrl.ifBlank { settings.serverUrl }
                if (base.isBlank()) {
                    emit(
                        UiEvent(
                            "Add the Padanam site address before switching accounts.",
                            error = true
                        )
                    )
                    return@launch
                }
                if (target.expired()) {
                    container.authRepository.remove(target.key)
                    emit(
                        UiEvent(
                            "${target.user.label}'s login expired. Sign in again.",
                            error = true
                        )
                    )
                    return@launch
                }

                val onlineNow = runCatching {
                    container.networkMonitor.online.first()
                }.getOrDefault(true)
                val awayProblem = if (current != null) {
                    prepareSwitchAway(current, onlineNow)
                } else {
                    null
                }
                if (awayProblem != null) {
                    emit(UiEvent(awayProblem, error = true))
                    return@launch
                }

                /* Confirm the saved token while online so an expired learner
                   login is removed instead of opening an empty library. */
                var chosen = target.copy(serverUrl = base)
                if (onlineNow) {
                    try {
                        val user = container.serverApi.session(base, target.token)
                        chosen = chosen.copy(user = user)
                    } catch (error: ServerException) {
                        if (error.expired || error.statusCode == 401) {
                            container.authRepository.remove(target.key)
                            emit(
                                UiEvent(
                                    "${target.user.label}'s login expired. Sign in again.",
                                    error = true
                                )
                            )
                            return@launch
                        }
                        /* A network hiccup keeps the cached session. */
                    } catch (_: Exception) {
                        /* Same. */
                    }
                }

                var outcome: SyncOutcome? = null
                syncMutex.withLock {
                    resetLocalWorkspace()
                    settingsRepo.updateServerUrl(base)
                    container.authRepository.save(
                        chosen.token,
                        chosen.user,
                        chosen.expiresAt,
                        base
                    )
                    settingsRepo.setAccountId(chosen.user.id)
                    settingsRepo.clearLegacySecrets()
                    if (onlineNow) {
                        _syncOp.value = OperationState(
                            busy = true,
                            message = "Loading ${chosen.user.label}'s library…"
                        )
                        outcome = container.cloudSyncRepository.sync(
                            base,
                            chosen,
                            settingsRepo.current()
                        )
                    }
                }
                emit(
                    UiEvent(
                        if (onlineNow) {
                            "Switched to ${chosen.user.label}."
                        } else {
                            "Switched to ${chosen.user.label}. The library downloads when you're online."
                        }
                    )
                )
                outcome?.let { reportSync(it, silentSuccess = true) }
            } catch (error: Exception) {
                emit(UiEvent(error.message ?: "Couldn't switch accounts.", error = true))
            } finally {
                _accountBusy.value = false
            }
        }
    }

    /**
     * Pushes the current account before its cache is cleared. Returns a message
     * when the switch must be blocked, or null when it is safe to continue.
     */
    private suspend fun prepareSwitchAway(current: AuthSession, onlineNow: Boolean): String? {
        val settings = settingsRepo.current()
        val currentBase = current.serverUrl.ifBlank { settings.serverUrl }
        val pending = dao.pendingChangeCount()
        val profilePending = settings.profileUpdatedAt > 0L &&
            settings.profileUpdatedAt > settings.lastSyncAt
        val unsynced = pending > 0 || profilePending
        if (currentBase.isBlank()) {
            return if (unsynced) {
                "Add the Padanam site address so your latest changes can sync before switching."
            } else {
                null
            }
        }
        if (!onlineNow) {
            return if (unsynced) {
                if (pending > 0) {
                    "You have $pending unsaved ${if (pending == 1) "change" else "changes"}. " +
                        "Connect to the internet before switching so they aren't lost."
                } else {
                    "Your profile has unsaved changes. Connect to the internet before " +
                        "switching so they aren't lost."
                }
            } else {
                null
            }
        }
        val outcome = syncMutex.withLock {
            container.cloudSyncRepository.sync(
                currentBase,
                current,
                settings
            )
        }
        if (outcome.ok) {
            settingsRepo.markSynced(outcome.message)
            return null
        }
        return if (unsynced) {
            "Couldn't save your latest changes. ${outcome.message}"
        } else {
            emit(UiEvent("Couldn't sync before switching. ${outcome.message}"))
            null
        }
    }

    /** Removes one non-active saved login without touching the open library. */
    fun removeSavedAccount(key: String) {
        if (_accountBusy.value) return
        viewModelScope.launch(Dispatchers.IO) {
            _accountBusy.value = true
            try {
                val target = container.authRepository.list().firstOrNull { it.key == key }
                    ?: return@launch
                val current = container.authRepository.current()
                if (current?.key == target.key) return@launch
                val base = target.serverUrl.ifBlank { settingsRepo.current().serverUrl }
                if (base.isNotBlank()) {
                    runCatching { container.serverApi.logout(base, target.token) }
                }
                container.authRepository.remove(target.key)
                emit(UiEvent("Removed ${target.user.label} from this phone."))
            } finally {
                _accountBusy.value = false
            }
        }
    }

    fun signOut() {
        viewModelScope.launch(Dispatchers.IO) {
            val current = container.authRepository.current()
            val base = settingsRepo.current().serverUrl
            if (current != null && base.isNotBlank()) {
                runCatching { container.serverApi.logout(base, current.token) }
            }
            /* Clear Room before the active account changes so the next account
               never appears over the previous account's library. */
            val next = syncMutex.withLock {
                resetLocalWorkspace()
                if (current != null) {
                    container.authRepository.remove(current.key)
                } else {
                    container.authRepository.current()
                }
            }
            if (next != null) {
                settingsRepo.setAccountId(next.user.id)
                emit(
                    UiEvent(
                        "Signed out of ${current?.user?.label ?: "the account"}. " +
                            "Switched to ${next.user.label}."
                    )
                )
                val nextBase = next.serverUrl.ifBlank { base }
                if (nextBase.isNotBlank()) settingsRepo.updateServerUrl(nextBase)
                if (nextBase.isNotBlank() &&
                    runCatching { container.networkMonitor.online.first() }.getOrDefault(false)
                ) {
                    syncNow(silent = true)
                }
            } else {
                emit(UiEvent("Signed out. This device's library was cleared."))
            }
        }
    }

    private suspend fun resetLocalWorkspace() {
        dao.resetLibrary()
        container.examStore.clear()
        container.deckSessionStore.clear()
        container.scanSession.clear()
        settingsRepo.clearSyncStatus()
        settingsRepo.resetProfile()
        _syncOp.value = OperationState()
    }

    fun clearAuthError() {
        _authError.value = ""
    }

    // ------------------------------------------------------------- deck flow

    /**
     * Opens a fresh round. [keys] narrows the deck (the missed pile) and
     * [round] forces the round number shown on the completion screen.
     */
    suspend fun startDeckRound(
        mainTopic: String,
        subTopic: String,
        keys: List<String>? = null,
        shuffle: Boolean = false,
        round: Int = 0
    ): Boolean = withContext(Dispatchers.IO) {
        container.deckRepository.startRound(
            mainTopic = mainTopic,
            subTopic = subTopic,
            keys = keys,
            shuffle = shuffle,
            round = round
        ) != null
    }

    /**
     * Resumes the saved round for this scope, or creates the first round when
     * there is none. Returns false when the deck has no cards.
     */
    suspend fun ensureDeckRound(mainTopic: String, subTopic: String): Boolean =
        withContext(Dispatchers.IO) {
            container.deckRepository.ensureRound(mainTopic, subTopic) != null
        }

    suspend fun sortDeckCard(result: String): DeckCard? {
        val sorted = withContext(Dispatchers.IO) { container.deckRepository.sort(result) }
        if (sorted != null) scheduleDeckSync()
        return sorted
    }

    suspend fun undoDeckCard(): Boolean {
        val undone = withContext(Dispatchers.IO) { container.deckRepository.undo() }
        if (undone) scheduleDeckSync()
        return undone
    }

    /**
     * Rounds can move quickly, so deck progress syncs after a short pause
     * instead of pushing a full workspace snapshot on every swipe. The web app
     * debounces its auto-sync the same way.
     */
    private var deckSyncJob: Job? = null

    private fun scheduleDeckSync() {
        deckSyncJob?.cancel()
        deckSyncJob = viewModelScope.launch {
            delay(1_500)
            syncInBackground()
        }
    }

    suspend fun clearDeckRound() = withContext(Dispatchers.IO) {
        container.deckRepository.clearRound()
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
        durationSeconds: Int,
        shuffle: Boolean,
        language: String
    ): String? = withContext(Dispatchers.IO) {
        val set = dao.getSet(setId) ?: return@withContext null
        val questions = dao.questionsOnce(setId)
        if (questions.isEmpty()) return@withContext null

        val wanted = if (questionCount <= 0) questions.size else questionCount.coerceAtMost(questions.size)
        val ordered = if (shuffle) questions.shuffled() else questions
        val selected = ordered.take(wanted)

        val now = System.currentTimeMillis()
        val exam = ActiveExam(
            examId = "exam_${UUID.randomUUID()}",
            attemptId = "att_${UUID.randomUUID()}",
            setId = setId,
            title = set.title,
            subject = set.subject,
            mode = mode,
            questionIds = selected.map { it.id },
            durationSeconds = if (mode == QuizMode.EXAM) durationSeconds.coerceAtLeast(0) else 0,
            passPercent = Format.PASS_PERCENT,
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

    fun createSet(
        title: String,
        titleMl: String,
        subject: String,
        mainTopic: String = "",
        subTopic: String = ""
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!isAdmin()) {
                emit(UiEvent("Switch to the admin view to create sets.", error = true))
                return@launch
            }
            if (title.isBlank()) {
                emit(UiEvent("Give the set a name first.", error = true))
                return@launch
            }
            runCatching { libraryRepo.createSet(title, titleMl, subject, mainTopic, subTopic) }
                .onSuccess {
                    emit(UiEvent("Set created. Add its first question."))
                    syncInBackground()
                }
                .onFailure { emit(UiEvent(it.message ?: "Couldn't create the set.", error = true)) }
        }
    }

    fun updateSetDetails(
        setId: String,
        title: String,
        titleMl: String,
        subject: String,
        mainTopic: String? = null,
        subTopic: String? = null
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!isAdmin()) {
                emit(UiEvent("Switch to the admin view to edit sets.", error = true))
                return@launch
            }
            if (title.isBlank()) {
                emit(UiEvent("The set name cannot be empty.", error = true))
                return@launch
            }
            libraryRepo.updateSetDetails(setId, title, titleMl, subject, mainTopic, subTopic)
            val active = runCatching { container.examStore.activeExam.first() }.getOrNull()
            if (active?.setId == setId) {
                container.examStore.save(
                    active.copy(
                        title = title.trim().take(80),
                        subject = subject.trim().take(60).ifBlank { "General" }
                    )
                )
            }
            emit(UiEvent("Set details saved."))
            syncInBackground()
        }
    }

    /**
     * Asks the server-side provider for a topic pair, then falls back to the
     * same title/subject heuristic used by the web app. Provider keys stay in
     * Cloudflare; the phone only sends text.
     */
    suspend fun suggestTopics(
        title: String,
        subject: String,
        subTopic: String,
        samples: List<String>
    ): TopicSuggestion = withContext(Dispatchers.IO) {
        val local = TopicSuggestion(
            mainTopic = Topics.normalize("", title, subject),
            subTopic = Topics.subTopic(subTopic, title),
            source = "local"
        )
        val settings = settingsRepo.current()
        val active = container.authRepository.current()
        if (settings.serverUrl.isBlank() || active == null) {
            emit(UiEvent("AI is unavailable. Filled a local topic guess."))
            return@withContext local
        }

        val existing = dao.setsOnce()
            .map { Topics.subTopic(it.subTopic, it.title) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(30)

        runCatching {
            container.serverApi.classifyTopic(
                baseUrl = settings.serverUrl,
                token = active.token,
                title = title,
                subject = subject,
                subTopic = subTopic,
                subTopics = existing,
                samples = samples
            )
        }.getOrElse { error ->
            val reason = error.message?.takeIf { it.isNotBlank() }
                ?: "The AI could not classify this set."
            emit(UiEvent("$reason Filled a local topic guess instead."))
            local
        }.also {
            if (it.source == "ai") {
                emit(UiEvent("Topic suggested: ${it.mainTopic} · ${it.subTopic}"))
            }
        }
    }

    fun saveQuestion(draft: QuestionDraft) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!isAdmin()) {
                emit(UiEvent("Switch to the admin view to edit questions.", error = true))
                return@launch
            }
            runCatching { libraryRepo.saveQuestion(draft) }
                .onSuccess {
                    emit(UiEvent(if (draft.questionId == null) "Question added." else "Question saved."))
                    syncInBackground()
                }
                .onFailure { emit(UiEvent(it.message ?: "Couldn't save the question.", error = true)) }
        }
    }

    fun deleteQuestion(setId: String, questionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!isAdmin()) {
                emit(UiEvent("Switch to the admin view to delete questions.", error = true))
                return@launch
            }
            libraryRepo.deleteQuestion(setId, questionId)
            emit(UiEvent("Question deleted."))
            syncInBackground()
        }
    }

    fun restoreDemos() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!isAdmin()) {
                emit(UiEvent("Switch to the admin view to restore demos.", error = true))
                return@launch
            }
            runCatching { libraryRepo.restoreDemos() }
                .onSuccess {
                    emit(UiEvent("Starter demo set restored."))
                    syncInBackground()
                }
                .onFailure { emit(UiEvent(it.message ?: "Couldn't restore the demo set.", error = true)) }
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

    fun setThemeMode(mode: String) {
        viewModelScope.launch(Dispatchers.IO) {
            settingsRepo.updateThemeMode(mode)
        }
    }

    /**
     * A real administrator can drop into the read-only learner view. Learners
     * cannot elevate their view; the account role is always checked too.
     */
    fun setViewMode(mode: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val account = container.authRepository.current()?.user
            if (account?.isAdmin != true) {
                emit(UiEvent("Only an administrator account can change the view.", error = true))
                return@launch
            }
            settingsRepo.updateViewMode(mode)
            emit(
                UiEvent(
                    if (mode == ViewMode.LEARNER) {
                        "Learner view on. Library editing is hidden."
                    } else {
                        "Admin view on."
                    }
                )
            )
        }
    }

    // ------------------------------------------------------- offline & ops

    /**
     * Pulls the account workspace and pushes local changes, then marks the
     * complete Room copy as offline-ready. This is the explicit download the
     * learner can run before losing connectivity.
     */
    fun downloadForOffline() {
        if (_syncOp.value.busy) return
        viewModelScope.launch(Dispatchers.IO) {
            val current = container.authRepository.current()
            if (current == null) {
                _syncOp.value = OperationState(
                    message = "Sign in to download your questions.",
                    success = false
                )
                return@launch
            }
            val settings = settingsRepo.current()
            if (settings.serverUrl.isBlank()) {
                _syncOp.value = OperationState(
                    message = "Add the address of your Padanam site in Settings first.",
                    success = false
                )
                return@launch
            }
            _syncOp.value = OperationState(
                busy = true,
                message = "Downloading every set and question…"
            )
            val outcome = syncMutex.withLock {
                val liveSession = container.authRepository.current() ?: return@launch
                val liveSettings = settingsRepo.current()
                container.cloudSyncRepository.sync(
                    liveSettings.serverUrl,
                    liveSession,
                    liveSettings
                )
            }
            if (!outcome.ok) {
                reportSync(outcome, silentSuccess = false)
                return@launch
            }
            settingsRepo.markOfflineReady()
            val sets = dao.setsOnce().size
            val questions = dao.questionCountOnce()
            val message = "Offline copy ready · $sets ${if (sets == 1) "set" else "sets"} · " +
                "$questions ${if (questions == 1) "question" else "questions"}."
            settingsRepo.markSynced(message)
            _syncOp.value = OperationState(message = message, success = true)
            emit(UiEvent(message))
        }
    }

    fun loadScannerStatus() {
        if (_diagnostics.value.busy) return
        viewModelScope.launch(Dispatchers.IO) {
            if (!isAdmin()) {
                _diagnostics.value = DiagnosticsState(
                    error = "Switch to the admin view to run scanner checks."
                )
                return@launch
            }
            val base = settingsRepo.current().serverUrl
            if (base.isBlank()) {
                _diagnostics.value = DiagnosticsState(
                    error = "Add the Padanam site address first."
                )
                return@launch
            }
            _diagnostics.value = _diagnostics.value.copy(
                busy = true,
                error = "",
                message = ""
            )
            runCatching { container.serverApi.scanStatus(base) }
                .onSuccess { status ->
                    _diagnostics.value = _diagnostics.value.copy(
                        busy = false,
                        status = status,
                        message = if (status.ready) {
                            "Provider is ready."
                        } else {
                            status.error.ifBlank { "The provider is not configured." }
                        }
                    )
                }
                .onFailure { error ->
                    _diagnostics.value = _diagnostics.value.copy(
                        busy = false,
                        error = error.message ?: "Couldn't read the scanner status."
                    )
                }
        }
    }

    fun testScanner() {
        if (_diagnostics.value.busy) return
        viewModelScope.launch(Dispatchers.IO) {
            if (!isAdmin()) {
                _diagnostics.value = DiagnosticsState(
                    error = "Switch to the admin view to test the scanner."
                )
                return@launch
            }
            val base = settingsRepo.current().serverUrl
            if (base.isBlank()) {
                _diagnostics.value = _diagnostics.value.copy(
                    error = "Add the Padanam site address first."
                )
                return@launch
            }
            _diagnostics.value = _diagnostics.value.copy(
                busy = true,
                error = "",
                testResult = "",
                message = ""
            )
            val token = container.authRepository.current()?.token
            runCatching { container.serverApi.testScanProvider(base, token) }
                .onSuccess { text ->
                    _diagnostics.value = _diagnostics.value.copy(
                        busy = false,
                        testResult = text,
                        message = "Provider test passed."
                    )
                }
                .onFailure { error ->
                    _diagnostics.value = _diagnostics.value.copy(
                        busy = false,
                        error = error.message ?: "The provider test failed."
                    )
                }
        }
    }

    fun loadModels() {
        if (_diagnostics.value.busy) return
        viewModelScope.launch(Dispatchers.IO) {
            if (!isAdmin()) {
                _diagnostics.value = DiagnosticsState(
                    error = "Switch to the admin view to list provider models."
                )
                return@launch
            }
            val base = settingsRepo.current().serverUrl
            if (base.isBlank()) {
                _diagnostics.value = _diagnostics.value.copy(
                    error = "Add the Padanam site address first."
                )
                return@launch
            }
            _diagnostics.value = _diagnostics.value.copy(
                busy = true,
                error = "",
                message = ""
            )
            runCatching { container.serverApi.providerModels(base) }
                .onSuccess { result ->
                    _diagnostics.value = _diagnostics.value.copy(
                        busy = false,
                        models = result.models,
                        modelsProvider = result.provider.ifBlank { result.host },
                        message = "${result.models.size} models available."
                    )
                }
                .onFailure { error ->
                    _diagnostics.value = _diagnostics.value.copy(
                        busy = false,
                        error = error.message ?: "Couldn't load the model list."
                    )
                }
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
            val outcome = syncMutex.withLock {
                val liveSession = container.authRepository.current()
                val liveSettings = settingsRepo.current()
                if (liveSession == null) {
                    SyncOutcome(false, "Sign in to sync your account.")
                } else if (liveSettings.serverUrl.isBlank()) {
                    SyncOutcome(false, "Add the address of your Padanam site in Settings first.")
                } else {
                    container.cloudSyncRepository.sync(
                        liveSettings.serverUrl,
                        liveSession,
                        liveSettings
                    )
                }
            }
            reportSync(outcome, silent)
        }
    }

    /** Fire-and-forget sync used after a scan, an attempt or a profile edit. */
    fun syncInBackground() {
        viewModelScope.launch(Dispatchers.IO) {
            val outcome = syncMutex.withLock {
                val settings = settingsRepo.current()
                if (!settings.autoSync) return@withLock null
                val current = container.authRepository.current() ?: return@withLock null
                if (settings.serverUrl.isBlank()) return@withLock null
                container.cloudSyncRepository.sync(
                    settings.serverUrl,
                    current,
                    settings
                )
            } ?: return@launch
            if (outcome.ok) {
                settingsRepo.markSynced(outcome.message)
                if (settingsRepo.current().offlineReady) settingsRepo.markOfflineReady()
            } else {
                settingsRepo.markSyncFailed(outcome.message)
            }
        }
    }

    private suspend fun reportSync(outcome: SyncOutcome, silentSuccess: Boolean) {
        if (outcome.ok) {
            settingsRepo.markSynced(outcome.message)
            if (settingsRepo.current().offlineReady) settingsRepo.markOfflineReady()
            _syncOp.value = OperationState(message = outcome.message, success = true)
            if (!silentSuccess) emit(UiEvent(outcome.message))
        } else {
            settingsRepo.markSyncFailed(outcome.message)
            _syncOp.value = OperationState(message = outcome.message, success = false)
            emit(UiEvent(outcome.message, error = true))
        }
    }

    private suspend fun isAdmin(): Boolean {
        val user = container.authRepository.current()?.user ?: return false
        if (!user.isAdmin) return false
        return settingsRepo.current().viewMode != ViewMode.LEARNER
    }

    /**
     * A damaged local cache must never be able to throw from a flow that the
     * UI collects on the main thread. Screens fall back to their empty state
     * and the next sync can refill the library.
     */
    private fun <T> Flow<T>.safeCache(fallback: T): Flow<T> = catch { emit(fallback) }

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
