package com.example.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

object PhotoStorageHelper {
    private const val PHOTOS_DIR = "photos"
    private const val CAMERA_CACHE_DIR = "camera"
    private const val MAX_DIM_PX = 1280

    fun createCameraCaptureUri(context: Context): Pair<Uri, File> {
        val dir = File(context.cacheDir, CAMERA_CACHE_DIR).apply { mkdirs() }
        val file = File(dir, "capture_${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return uri to file
    }

    fun persistUriToInternalPhoto(context: Context, sourceUri: Uri): String? {
        return runCatching {
            val bitmap = decodeScaledBitmapFromUri(context, sourceUri) ?: return null
            saveBitmapToInternalPhoto(context, bitmap)
        }.getOrNull()
    }

    fun persistFileToInternalPhoto(context: Context, sourceFile: File): String? {
        return runCatching {
            val bitmap = decodeScaledBitmapFromFile(sourceFile) ?: return null
            saveBitmapToInternalPhoto(context, bitmap)
        }.getOrNull()
    }

    fun saveBitmapToInternalPhoto(context: Context, bitmap: Bitmap): String {
        val dir = File(context.filesDir, PHOTOS_DIR).apply { mkdirs() }
        val fileName = "marea_${UUID.randomUUID()}.jpg"
        val destFile = File(dir, fileName)
        FileOutputStream(destFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        return fileName
    }

    fun resolvePhotoFile(context: Context, fileName: String): File? {
        if (fileName.isBlank()) return null
        val file = File(File(context.filesDir, PHOTOS_DIR), fileName)
        return file.takeIf { it.exists() }
    }

    fun loadBitmapForAnalysis(context: Context, fileName: String): Bitmap? {
        val file = resolvePhotoFile(context, fileName) ?: return null
        return decodeScaledBitmapFromFile(file, maxDim = 1024)
    }

    private fun decodeScaledBitmapFromUri(context: Context, uri: Uri, maxDim: Int = MAX_DIM_PX): Bitmap? {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, boundsOpts)
        }
        val sampleSize = computeSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, maxDim)
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, decodeOpts)
        }
    }

    private fun decodeScaledBitmapFromFile(file: File, maxDim: Int = MAX_DIM_PX): Bitmap? {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
        val sampleSize = computeSampleSize(boundsOpts.outWidth, boundsOpts.outHeight, maxDim)
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
    }

    private fun computeSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var inSampleSize = 1
        val largest = max(width, height)
        if (largest > maxDim) {
            while ((largest / inSampleSize) > maxDim * 2) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
