package com.padanam.psc.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

data class PreparedImage(
    val path: String,
    val displayName: String,
    val sizeBytes: Long,
    val source: String = "scan"
) {
    val file: File get() = File(path)
}

object ImageUtils {

    suspend fun importFromUris(context: Context, uris: List<Uri>): List<PreparedImage> =
        withContext(Dispatchers.IO) {
            uris.take(MAX_IMAGES).mapNotNull { uri ->
                runCatching { copyToCache(context, uri) }.getOrNull()
            }
        }

    private fun copyToCache(context: Context, uri: Uri): PreparedImage {
        val folder = File(context.cacheDir, "scan_inbox").apply { mkdirs() }
        val displayName = displayName(context, uri)
        val extension = displayName.substringAfterLast('.', "jpg")
            .lowercase(Locale.ENGLISH)
            .takeIf { it in setOf("jpg", "jpeg", "png", "webp", "heic", "heif") }
            ?: "jpg"
        val destination = File(folder, "IMG_${System.currentTimeMillis()}_${uniqueSuffix()}.$extension")

        context.contentResolver.openInputStream(uri).use { input ->
            if (input == null) error("Could not open that photo")
            destination.outputStream().use { output -> input.copyTo(output) }
        }

        return PreparedImage(
            path = destination.absolutePath,
            displayName = displayName,
            sizeBytes = destination.length(),
            source = "import"
        )
    }

    /**
     * Downsample, rotate using EXIF and re-encode as JPEG. Gemini receives a
     * much smaller payload and the upload progress feels immediate even on a
     * slow mobile connection.
     */
    suspend fun compressForUpload(path: String, maxDimension: Int = 1600, quality: Int = 86): ByteArray =
        withContext(Dispatchers.IO) {
            val file = File(path)
            require(file.exists()) { "Photo file is missing" }

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) error("That photo could not be read")

            var sample = 1
            while (bounds.outWidth / sample > maxDimension * 2 ||
                bounds.outHeight / sample > maxDimension * 2
            ) {
                sample *= 2
            }

            val decoded = BitmapFactory.decodeFile(
                path,
                BitmapFactory.Options().apply {
                    inSampleSize = sample
                    inPreferredConfig = Bitmap.Config.ARGB_8888
                }
            ) ?: error("That photo could not be decoded")

            val rotation = runCatching {
                when (ExifInterface(path).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }.getOrDefault(0f)

            val rotated = if (rotation != 0f) {
                val matrix = Matrix().apply { postRotate(rotation) }
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
                    .also { if (it !== decoded) decoded.recycle() }
            } else {
                decoded
            }

            val longest = maxOf(rotated.width, rotated.height)
            val scaled = if (longest > maxDimension) {
                val ratio = maxDimension.toFloat() / longest.toFloat()
                Bitmap.createScaledBitmap(
                    rotated,
                    (rotated.width * ratio).toInt().coerceAtLeast(1),
                    (rotated.height * ratio).toInt().coerceAtLeast(1),
                    true
                ).also { if (it !== rotated) rotated.recycle() }
            } else {
                rotated
            }

            val output = java.io.ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, output)
            scaled.recycle()
            output.toByteArray()
        }

    fun newCaptureFile(context: Context): File {
        val folder = File(context.cacheDir, "scan_inbox").apply { mkdirs() }
        val stamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH)
            .format(java.util.Date())
        return File(folder, "IMG_${stamp}_${uniqueSuffix()}.jpg")
    }

    fun sizeLabel(bytes: Long): String = when {
        bytes <= 0 -> "0 KB"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.ENGLISH, "%.0f KB", bytes / 1024f)
        else -> String.format(Locale.ENGLISH, "%.1f MB", bytes / (1024f * 1024f))
    }

    private fun displayName(context: Context, uri: Uri): String {
        val fromResolver = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) cursor.getString(index) else null
                    } else null
                }
        }.getOrNull()
        return fromResolver?.takeIf { it.isNotBlank() } ?: "IMG_${System.currentTimeMillis()}.jpg"
    }

    private fun uniqueSuffix(): String = (1000..9999).random().toString()

    private const val MAX_IMAGES = 8
}
