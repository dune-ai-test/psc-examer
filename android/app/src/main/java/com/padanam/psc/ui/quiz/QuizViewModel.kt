package com.padanam.psc.ui.quiz

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.padanam.psc.AppContainer
import com.padanam.psc.data.local.QuestionEntity
import com.padanam.psc.data.model.ActiveExam
import com.padanam.psc.data.model.Lang
import com.padanam.psc.data.model.QuizMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class QuizUiState(
    val loading: Boolean = true,
    val missing: Boolean = false,
    val exam: ActiveExam? = null,
    val questions: List<QuestionEntity> = emptyList(),
    val language: String = Lang.EN,
    val remainingSeconds: Int = 0,
    val elapsedSeconds: Int = 0,
    val paletteOpen: Boolean = false,
    val leaveDialog: Boolean = false,
    val submitDialog: Boolean = false,
    val submitting: Boolean = false,
    val submittedAttemptId: String? = null,
    val error: String? = null
) {
    val total: Int get() = questions.size
    val current: QuestionEntity? get() = questions.getOrNull(exam?.currentIndex ?: 0)
    val currentNumber: Int get() = ((exam?.currentIndex ?: 0) + 1).coerceAtLeast(1)
    val answeredCount: Int get() = exam?.answers?.size ?: 0
    val flaggedCount: Int get() = exam?.flagged?.size ?: 0
    val isExam: Boolean get() = exam?.mode == QuizMode.EXAM
    val timerText: String
        get() = if (isExam) {
            com.padanam.psc.util.Format.timer(remainingSeconds)
        } else {
            com.padanam.psc.util.Format.timer(elapsedSeconds)
        }
}

class QuizViewModel(private val container: AppContainer) : ViewModel() {

    private val _ui = MutableStateFlow(QuizUiState())
    val ui = _ui.asStateFlow()

    private var ticker: Job? = null
    private var questionEnteredAt = System.currentTimeMillis()
    private var autoSubmitStarted = false

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val exam = runCatching { container.examStore.activeExam.first() }.getOrNull()
            if (exam == null) {
                _ui.value = QuizUiState(loading = false, missing = true)
                return@launch
            }
            val loaded = withContext(Dispatchers.IO) {
                val questions = container.dao.questionsByIds(exam.questionIds)
                val byId = questions.associateBy { it.id }
                exam.questionIds.mapNotNull { byId[it] }
            }
            if (loaded.isEmpty()) {
                _ui.value = QuizUiState(
                    loading = false,
                    missing = true,
                    error = "The questions in this exam are no longer available."
                )
                return@launch
            }

            questionEnteredAt = System.currentTimeMillis()
            _ui.value = QuizUiState(
                loading = false,
                exam = exam.copy(currentIndex = exam.currentIndex.coerceIn(0, loaded.lastIndex)),
                questions = loaded,
                language = exam.language,
                remainingSeconds = remainingFor(exam),
                elapsedSeconds = elapsedFor(exam)
            )
            startTicker()
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (isActive) {
                delay(1_000)
                val exam = _ui.value.exam ?: continue
                val elapsed = elapsedFor(exam)
                val remaining = remainingFor(exam)
                _ui.update { it.copy(elapsedSeconds = elapsed, remainingSeconds = remaining) }
                if (exam.mode == QuizMode.EXAM && remaining <= 0 && !autoSubmitStarted) {
                    autoSubmitStarted = true
                    submit()
                }
            }
        }
    }

    private fun elapsedFor(exam: ActiveExam): Int =
        ((System.currentTimeMillis() - exam.startedAtEpochMs) / 1000).toInt().coerceAtLeast(0)

    private fun remainingFor(exam: ActiveExam): Int {
        if (exam.mode != QuizMode.EXAM || exam.durationSeconds <= 0) return 0
        return (exam.durationSeconds - elapsedFor(exam)).coerceAtLeast(0)
    }

    private fun mutate(transform: (ActiveExam) -> ActiveExam) {
        val exam = _ui.value.exam ?: return
        val now = System.currentTimeMillis()
        val delta = ((now - questionEnteredAt) / 1000).toInt().coerceIn(0, 3_600)
        val questionId = exam.currentQuestionId
        val times = if (questionId == null) {
            exam.timeByQuestion
        } else {
            exam.timeByQuestion + (questionId to (exam.timeByQuestion[questionId] ?: 0) + delta)
        }
        val updated = transform(exam.copy(timeByQuestion = times, lastSavedAtEpochMs = now))
        questionEnteredAt = now
        _ui.update { it.copy(exam = updated, language = updated.language) }
        viewModelScope.launch(Dispatchers.IO) { container.examStore.save(updated) }
    }

    fun selectOption(letter: String) {
        val clean = letter.uppercase().take(1)
        if (clean !in setOf("A", "B", "C", "D")) return
        mutate { exam ->
            val id = exam.currentQuestionId ?: return@mutate exam
            exam.copy(answers = exam.answers + (id to clean))
        }
    }

    fun toggleFlag() {
        mutate { exam ->
            val id = exam.currentQuestionId ?: return@mutate exam
            val flags = if (id in exam.flagged) exam.flagged - id else exam.flagged + id
            exam.copy(flagged = flags)
        }
    }

    fun next() {
        val state = _ui.value
        if (state.currentNumber >= state.total) {
            requestSubmit()
            return
        }
        mutate { it.copy(currentIndex = (it.currentIndex + 1).coerceIn(0, state.total - 1)) }
    }

    fun previous() {
        if ((_ui.value.exam?.currentIndex ?: 0) <= 0) return
        mutate { it.copy(currentIndex = (it.currentIndex - 1).coerceAtLeast(0)) }
    }

    fun goTo(index: Int) {
        val total = _ui.value.total
        if (total == 0) return
        mutate { it.copy(currentIndex = index.coerceIn(0, total - 1)) }
        _ui.update { it.copy(paletteOpen = false) }
    }

    fun setPalette(open: Boolean) {
        _ui.update { it.copy(paletteOpen = open) }
    }

    fun setLanguage(language: String) {
        if (_ui.value.language == language) return
        mutate { it.copy(language = Lang.normalize(language)) }
    }

    fun showLeaveDialog(show: Boolean) {
        _ui.update { it.copy(leaveDialog = show) }
    }

    fun requestSubmit() {
        if (_ui.value.submitting || _ui.value.submittedAttemptId != null) return
        _ui.update { it.copy(submitDialog = true, paletteOpen = false) }
    }

    fun cancelSubmit() {
        _ui.update { it.copy(submitDialog = false) }
    }

    fun submit() {
        val state = _ui.value
        if (state.submitting || state.submittedAttemptId != null) return
        val exam = state.exam ?: return
        val questions = state.questions
        _ui.update {
            it.copy(submitting = true, submitDialog = false, paletteOpen = false, error = null)
        }

        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val delta = ((now - questionEnteredAt) / 1000).toInt().coerceIn(0, 3_600)
                val questionId = exam.currentQuestionId
                val times = if (questionId == null) {
                    exam.timeByQuestion
                } else {
                    exam.timeByQuestion + (questionId to (exam.timeByQuestion[questionId] ?: 0) + delta)
                }
                val finalExam = exam.copy(timeByQuestion = times, lastSavedAtEpochMs = now)

                val attempt = withContext(Dispatchers.IO) {
                    container.attemptRepository.recordAttempt(finalExam, questions)
                }
                withContext(Dispatchers.IO) { container.examStore.clear() }
                ticker?.cancel()
                _ui.update {
                    it.copy(
                        submitting = false,
                        submittedAttemptId = attempt.id,
                        exam = finalExam
                    )
                }

                // Mirror the scan flow: when auto-sync is on, push the finished
                // attempt (and its score) to Neon without blocking navigation.
                val settings = runCatching {
                    withContext(Dispatchers.IO) { container.settingsRepository.current() }
                }.getOrNull()
                if (settings != null && settings.autoSync && settings.neonConfigured) {
                    container.appScope.launch(Dispatchers.IO) {
                        runCatching {
                            val outcome = container.syncRepository.sync(settings)
                            if (outcome.ok) {
                                container.settingsRepository.markSynced(outcome.message)
                            } else {
                                container.settingsRepository.markSyncFailed(outcome.message)
                            }
                        }
                    }
                }
            } catch (error: Exception) {
                _ui.update {
                    it.copy(
                        submitting = false,
                        error = error.message ?: "Couldn't submit the exam. Try again."
                    )
                }
            }
        }
    }

    fun abandon() {
        viewModelScope.launch(Dispatchers.IO) {
            container.examStore.clear()
            withContext(Dispatchers.Main) {
                _ui.update { it.copy(leaveDialog = false) }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        ticker?.cancel()
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return QuizViewModel(container) as T
                }
            }
    }
}
