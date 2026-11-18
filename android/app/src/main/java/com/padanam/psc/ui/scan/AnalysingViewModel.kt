package com.padanam.psc.ui.scan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.padanam.psc.AppContainer
import com.padanam.psc.data.remote.ScanException
import com.padanam.psc.data.remote.ScanImage
import com.padanam.psc.data.repo.LibraryRepository
import com.padanam.psc.util.ImageUtils
import com.padanam.psc.util.PreparedImage
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
    val questionCount: Int = 0
) {
    val stageLabel: String
        get() = when (stage) {
            "UPLOADING" -> "UPLOADING IMAGE"
            "READING" -> "READING TEXT"
            "MATCHING" -> "MATCHING OPTIONS"
            "SAVING" -> "SAVING TO DATABASE"
            "DONE" -> "READY"
            "ERROR" -> "SCAN PAUSED"
            else -> stage
        }

    val note: String
        get() = when (stage) {
            "UPLOADING" -> "Preparing a compact copy of your photograph"
            "READING" -> "The scanner is reading the question and all four options"
            "MATCHING" -> "Aligning English and Malayalam, then checking the answer"
            "SAVING" -> "Writing the verified questions into your library"
            "DONE" -> "Saved. Opening the scan result…"
            "ERROR" -> error ?: "Something interrupted the scan."
            else -> "Usually ready in a few seconds — keep this screen open"
        }
}

class AnalysingViewModel(private val container: AppContainer) : ViewModel() {

    private val _state = MutableStateFlow(
        AnalysingState(images = container.scanSession.images.value)
    )
    val state = _state.asStateFlow()

    private var started = false
    private var progressJob: Job? = null
    private var workJob: Job? = null

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
                if (snapshot.stage == "ERROR" || snapshot.stage == "DONE") continue
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

                val source = if (images.any { it.source == LibraryRepository.SOURCE_IMPORT }) {
                    LibraryRepository.SOURCE_IMPORT
                } else {
                    LibraryRepository.SOURCE_SCAN
                }
                val setId = withContext(Dispatchers.IO) {
                    container.libraryRepository.saveScan(
                        result = result,
                        source = source,
                        imagePaths = images.map { it.path }
                    )
                }
                withContext(Dispatchers.IO) {
                    container.settingsRepository.markSavedToLibrary(setId)
                }

                _state.update {
                    it.copy(
                        stage = "SAVING",
                        progress = it.progress.coerceAtLeast(94),
                        questionCount = result.questions.size
                    )
                }

                delay(450)
                _state.update {
                    it.copy(
                        stage = "DONE",
                        progress = 100,
                        running = false,
                        setId = setId
                    )
                }
                container.scanSession.clear()

                if (settings.autoSync) {
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

    fun retry() {
        workJob?.cancel()
        progressJob?.cancel()
        started = false
        _state.value = state.value.copy(
            stage = "UPLOADING",
            progress = 2,
            error = null,
            running = false
        )
        start()
    }

    fun cancel() {
        workJob?.cancel()
        progressJob?.cancel()
        container.scanSession.clear()
        _state.update { it.copy(running = false, stage = "ERROR", error = "Scan cancelled.") }
    }

    override fun onCleared() {
        super.onCleared()
        workJob?.cancel()
        progressJob?.cancel()
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
