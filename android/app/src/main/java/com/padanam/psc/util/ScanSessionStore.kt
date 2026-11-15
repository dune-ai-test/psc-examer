package com.padanam.psc.util

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hands the captured photos from the camera screen to the analysing screen.
 * Photographs live in the cache directory; only their paths are kept here.
 */
class ScanSessionStore {

    private val _images = MutableStateFlow<List<PreparedImage>>(emptyList())
    val images: StateFlow<List<PreparedImage>> = _images.asStateFlow()

    fun prepare(images: List<PreparedImage>) {
        _images.value = images.take(8)
    }

    fun clear() {
        _images.value = emptyList()
    }
}
