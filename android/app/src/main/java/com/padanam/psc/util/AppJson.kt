package com.padanam.psc.util

import kotlinx.serialization.json.Json

/**
 * Single JSON configuration used for Room snapshots, export files and the
 * Padanam API payloads. Unknown keys are ignored so a schema addition in a
 * future server response never crashes an older install.
 */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
    explicitNulls = false
    coerceInputValues = true
}
