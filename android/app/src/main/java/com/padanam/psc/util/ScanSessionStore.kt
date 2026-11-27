package com.padanam.psc.util

import com.padanam.psc.data.model.ScanMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hands the captured photos and the chosen scan type from the camera screen to
 * the analysing screen. Photographs live in the cache directory; only their
 * paths are kept here.
 */
class ScanSessionStore {

    private val _images = MutableStateFlow<List<PreparedImage>>(emptyList())
    val images: StateFlow<List<PreparedImage>> = _images.asStateFlow()

    private val _mode = MutableStateFlow(ScanMode.SET)
    val mode: StateFlow<String> = _mode.asStateFlow()

    fun prepare(images: List<PreparedImage>, mode: String = ScanMode.SET) {
        _images.value = images.take(8)
        _mode.value = ScanMode.normalize(mode)
    }

    fun clear() {
        _images.value = emptyList()
        _mode.value = ScanMode.SET
    }
}
