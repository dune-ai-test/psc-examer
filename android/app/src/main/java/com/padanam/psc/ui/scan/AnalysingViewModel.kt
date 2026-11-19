package com.padanam.psc.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.padanam.psc.AppContainer
import com.padanam.psc.data.auth.AuthSession
import com.padanam.psc.data.model.AiScanResult
import com.padanam.psc.data.model.ScanDestination
import com.padanam.psc.data.prefs.AppSettings
import com.padanam.psc.data.remote.ScanException
import com.padanam.psc.data.remote.ScanImage
import com.padanam.psc.data.repo.LibraryRepository
import com.padanam.psc.util.ImageUtils
import com.padanam.psc.util.PreparedImage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AnalysingState(
    val images: List<PreparedImage> = emptyList(),
    val stage: String = "UPLOADING",
    val progress: Int = 0,
    val error: String? = null,
    val running: Boolean = false,
    val setId: String? = null,
    val questionCount: Int = 0,
    val draft: AiScanResult? = null,
    val destinations: List<ScanDestination> = emptyList(),
    val targetSetId: String? = null,
    val savedTitle: String = "",
    val appended: Boolean = false
) {
    val stageLabel: String
        get() = when (stage) {
            "UPLOADING" -> "UPLOADING IMAGE"
            "READING" -> "READING TEXT"
            "MATCHING" -> "MATCHING OPTIONS"
            "REVIEW" -> "READY TO SAVE"
            "SAVING" -> "SAVING TO DATABASE"
            "DONE" -> "READY"
            "ERROR" -> "SCAN PAUSED"
            else -> stage
        }

    val note: String
        get() = when (stage) {
            "UPLOADING" -> "Preparing a compact copy of your photograph"
            "READING" -> "The scanner is reading the question and every option"
            "MATCHING" -> "Aligning English and Malayalam, then checking the answer"
            "REVIEW" -> "Check the draft, then choose where the questions should go"
            "SAVING" -> "Writing the reviewed questions into your library"
            "DONE" -> "Saved. Opening the scan result…"
            "ERROR" -> error ?: "Something interrupted the scan."
            else -> "Usually ready in a few seconds — keep this screen open"
        }

    val targetTitle: String?
        get() = destinations.firstOrNull { it.id == targetSetId }?.title
}

class AnalysingViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(
        AnalysingState(images = container.scanSession.images.value)
    )
    val state = _state.asStateFlow()

    private var started = false
    private var progressJob: Job? = null
    private var workJob: Job? = null
    private var saveJob: Job? = null

    fun start() {
        if (started) return
        started = true

        val images = container.scanSession.images.value
        if (images.isEmpty()) {
            _state.update {
                it.copy(
                    images = images,
                    error = "No photo was received. Go back and scan again.",
                    stage = "ERROR",
                    running = false
                )
            }
            return
        }

        _state.update {
            it.copy(
                images = images,
                stage = "UPLOADING",
                progress = 2,
                error = null,
                running = true
            )
        }

        progressJob = viewModelScope.launch {
            while (isActive) {
                delay(220)
                val snapshot = _state.value
                if (snapshot.stage == "ERROR" ||
                    snapshot.stage == "REVIEW" ||
                    snapshot.stage == "DONE"
                ) {
                    continue
                }
                val cap = when (snapshot.stage) {
                    "UPLOADING" -> 36
                    "READING" -> 66
                    "MATCHING" -> 92
                    "SAVING" -> 98
                    else -> 98
                }
                if (snapshot.progress < cap) {
                    _state.update { it.copy(progress = (it.progress + 1).coerceAtMost(cap)) }
                }
            }
        }

        workJob = viewModelScope.launch {
            try {
                val settings = withContext(Dispatchers.IO) { container.settingsRepository.current() }
                if (settings.serverUrl.isBlank()) {
                    throw ScanException("Add the Padanam site address in Settings, then scan again.")
                }
                val session = withContext(Dispatchers.IO) { container.authRepository.current() }
                    ?: throw ScanException("Sign in again to scan questions.")
                if (!session.user.isAdmin) {
                    throw ScanException("Only the administrator can scan questions.")
                }

                val payload = withContext(Dispatchers.IO) {
                    images.map { prepared ->
                        ScanImage(
                            bytes = ImageUtils.compressForUpload(prepared.path),
                            mimeType = "image/jpeg"
                        )
                    }
                }

                _state.update {
                    it.copy(stage = "READING", progress = it.progress.coerceAtLeast(38))
                }

                val result = container.scanRepository.extractQuestions(
                    baseUrl = settings.serverUrl,
                    token = session.token,
                    images = payload
                )

                _state.update {
                    it.copy(stage = "MATCHING", progress = it.progress.coerceAtLeast(68))
                }

                if (result.questions.isEmpty()) {
                    throw ScanException(
                        "No questions were found in these pages. Use a sharper photo and try again."
                    )
                }

                val destinations = withContext(Dispatchers.IO) {
                    container.libraryRepository.scanDestinations()
                }

                _state.update {
                    it.copy(
                        stage = "REVIEW",
                        progress = 100,
                        running = false,
                        error = null,
                        questionCount = result.questions.size,
                        draft = result,
                        destinations = destinations,
                        targetSetId = null
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: ScanException) {
                progressJob?.cancel()
                _state.update {
                    it.copy(
                        stage = "ERROR",
                        running = false,
                        error = error.message ?: "The scan failed. Try again."
                    )
                }
            } catch (error: Exception) {
                progressJob?.cancel()
                _state.update {
                    it.copy(
                        stage = "ERROR",
                        running = false,
                        error = error.message
                            ?: "The scan was interrupted. Check your connection and try again."
                    )
                }
            }
        }
    }

    /** Chooses the destination for the draft. `null` creates a new set. */
    fun selectTarget(setId: String?) {
        if (setId != null && _state.value.destinations.none { it.id == setId }) return
        _state.update { it.copy(targetSetId = setId, error = null) }
    }

    /** Persists the reviewed draft as a new set or appends it to the target. */
    fun saveDraft() {
        val snapshot = _state.value
        val draft = snapshot.draft ?: return
        if (snapshot.running) return

        val images = snapshot.images
        val targetSetId = snapshot.targetSetId
        _state.update {
            it.copy(stage = "SAVING", progress = 94, running = true, error = null)
        }

        saveJob = viewModelScope.launch {
            try {
                val settings = withContext(Dispatchers.IO) { container.settingsRepository.current() }
                val session = withContext(Dispatchers.IO) { container.authRepository.current() }
                    ?: throw ScanException("Sign in again to save questions.")
                if (!session.user.isAdmin) {
                    throw ScanException("Only the administrator can save scanned questions.")
                }

                val source = if (images.any { it.source == LibraryRepository.SOURCE_IMPORT }) {
                    LibraryRepository.SOURCE_IMPORT
                } else {
                    LibraryRepository.SOURCE_SCAN
                }

                val saved = withContext(Dispatchers.IO) {
                    if (targetSetId == null) {
                        val setId = container.libraryRepository.saveScan(
                            result = draft,
                            source = source,
                            imagePaths = images.map { it.path }
                        )
                        SavedScan(
                            setId = setId,
                            title = container.libraryRepository.titleOf(setId)
                                .ifBlank { draft.title.ifBlank { "Scanned set" } },
                            added = draft.questions.size,
                            appended = false
                        )
                    } else {
                        val appended = container.libraryRepository.appendScan(targetSetId, draft)
                        if (appended.added == 0) {
                            throw ScanException(
                                "That set is no longer in your library. Pick another destination and try again."
                            )
                        }
                        SavedScan(
                            setId = targetSetId,
                            title = appended.title,
                            added = appended.added,
                            appended = true
                        )
                    }
                }

                // The set is already committed. A preferences hiccup must not
                // bounce the review screen back and invite a duplicate save.
                withContext(Dispatchers.IO) {
                    runCatching { container.settingsRepository.markSavedToLibrary(saved.setId) }
                }

                _state.update {
                    it.copy(
                        stage = "DONE",
                        progress = 100,
                        running = false,
                        error = null,
                        setId = saved.setId,
                        questionCount = saved.added,
                        savedTitle = saved.title,
                        appended = saved.appended
                    )
                }
                container.scanSession.clear()

                if (settings.autoSync) syncInBackground(settings, session)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        stage = "REVIEW",
                        progress = 100,
                        running = false,
                        error = error.message ?: "Couldn't save the scan. Try again."
                    )
                }
            }
        }
    }

    private fun syncInBackground(settings: AppSettings, session: AuthSession) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val outcome = container.cloudSyncRepository.sync(
                    settings.serverUrl,
                    session,
                    settings
                )
                if (outcome.ok) {
                    container.settingsRepository.markSynced(outcome.message)
                } else {
                    container.settingsRepository.markSyncFailed(outcome.message)
                }
            }
        }
    }

    fun retry() {
        workJob?.cancel()
        progressJob?.cancel()
        saveJob?.cancel()
        started = false
        _state.value = _state.value.copy(
            stage = "UPLOADING",
            progress = 2,
            error = null,
            running = false,
            setId = null,
            questionCount = 0,
            draft = null,
            destinations = emptyList(),
            targetSetId = null,
            savedTitle = "",
            appended = false
        )
        start()
    }

    fun cancel() {
        workJob?.cancel()
        progressJob?.cancel()
        saveJob?.cancel()
        container.scanSession.clear()
        _state.update { it.copy(running = false, stage = "ERROR", error = "Scan cancelled.") }
    }

    override fun onCleared() {
        super.onCleared()
        workJob?.cancel()
        progressJob?.cancel()
        saveJob?.cancel()
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AnalysingViewModel(container) as T
                }
            }
    }
}

private data class SavedScan(
    val setId: String,
    val title: String,
    val added: Int,
    val appended: Boolean
)
