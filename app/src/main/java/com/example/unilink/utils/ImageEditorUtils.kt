package com.example.unilink.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

enum class FilterType(val displayName: String) {
    ORIGINAL("Original"),
    CYBER_GLOW("Cyber Glow"),
    WARM_SUN("Warm Sun"),
    COOL_CYAN("Cool Cyan"),
    BW_MINIMAL("B&W Minimal"),
    VINTAGE("Vintage"),
    VIVID_HDR("Vivid HDR")
}

object ImageEditorUtils {

    fun applyFilterAndAdjustments(
        source: Bitmap,
        filterType: FilterType,
        brightness: Float = 0f, // -100 to 100
        contrast: Float = 1f,   // 0.5 to 2.0
        saturation: Float = 1f, // 0.0 to 2.0
        rotationAngle: Float = 0f, // 0, 90, 180, 270
        flipHorizontal: Boolean = false
    ): Bitmap {
        var processed = source

        // Handle Rotation and Flip
        if (rotationAngle != 0f || flipHorizontal) {
            val matrix = Matrix()
            if (rotationAngle != 0f) {
                matrix.postRotate(rotationAngle)
            }
            if (flipHorizontal) {
                matrix.postScale(-1f, 1f)
            }
            processed = Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        }

        val resultBitmap = Bitmap.createBitmap(processed.width, processed.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val cm = ColorMatrix()

        // Apply Base Preset Filter Matrix
        when (filterType) {
            FilterType.ORIGINAL -> {
                // Identity Matrix
            }
            FilterType.CYBER_GLOW -> {
                // Boost Cyans & Magentas
                val cyberMatrix = ColorMatrix(floatArrayOf(
                    1.2f, 0f, 0.2f, 0f, 10f,
                    0f, 1.3f, 0.4f, 0f, 15f,
                    0.2f, 0.3f, 1.5f, 0f, 20f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(cyberMatrix)
            }
            FilterType.WARM_SUN -> {
                val warmMatrix = ColorMatrix(floatArrayOf(
                    1.2f, 0.1f, 0f, 0f, 20f,
                    0.1f, 1.1f, 0f, 0f, 10f,
                    0f, 0f, 0.8f, 0f, -10f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(warmMatrix)
            }
            FilterType.COOL_CYAN -> {
                val cyanMatrix = ColorMatrix(floatArrayOf(
                    0.9f, 0f, 0.1f, 0f, -10f,
                    0f, 1.1f, 0.2f, 0f, 10f,
                    0.1f, 0.2f, 1.3f, 0f, 20f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(cyanMatrix)
            }
            FilterType.BW_MINIMAL -> {
                cm.setSaturation(0f)
                val bwMatrix = ColorMatrix(floatArrayOf(
                    1.2f, 0f, 0f, 0f, -10f,
                    0f, 1.2f, 0f, 0f, -10f,
                    0f, 0f, 1.2f, 0f, -10f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(bwMatrix)
            }
            FilterType.VINTAGE -> {
                val vintageMatrix = ColorMatrix(floatArrayOf(
                    0.9f, 0.1f, 0.1f, 0f, 15f,
                    0.1f, 0.8f, 0.1f, 0f, 10f,
                    0.1f, 0.1f, 0.7f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(vintageMatrix)
            }
            FilterType.VIVID_HDR -> {
                cm.setSaturation(1.4f)
                val vividMatrix = ColorMatrix(floatArrayOf(
                    1.15f, 0f, 0f, 0f, 5f,
                    0f, 1.15f, 0f, 0f, 5f,
                    0f, 0f, 1.15f, 0f, 5f,
                    0f, 0f, 0f, 1f, 0f
                ))
                cm.postConcat(vividMatrix)
            }
        }

        // Apply Saturation Adjustment
        if (saturation != 1f) {
            val satMatrix = ColorMatrix()
            satMatrix.setSaturation(saturation)
            cm.postConcat(satMatrix)
        }

        // Apply Contrast Adjustment
        if (contrast != 1f) {
            val scale = contrast
            val translate = (-0.5f * scale + 0.5f) * 255f
            val contrastMatrix = ColorMatrix(floatArrayOf(
                scale, 0f, 0f, 0f, translate,
                0f, scale, 0f, 0f, translate,
                0f, 0f, scale, 0f, translate,
                0f, 0f, 0f, 1f, 0f
            ))
            cm.postConcat(contrastMatrix)
        }

        // Apply Brightness Adjustment
        if (brightness != 0f) {
            val brightMatrix = ColorMatrix(floatArrayOf(
                1f, 0f, 0f, 0f, brightness,
                0f, 1f, 0f, 0f, brightness,
                0f, 0f, 1f, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            ))
            cm.postConcat(brightMatrix)
        }

        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(processed, 0f, 0f, paint)

        return resultBitmap
    }

    fun saveBitmapToTempUri(context: Context, bitmap: Bitmap): Uri? {
        return try {
            val cacheDir = File(context.cacheDir, "edited_photos")
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val tempFile = File(cacheDir, "edited_${System.currentTimeMillis()}.jpg")
            val outputStream = FileOutputStream(tempFile)
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            outputStream.flush()
            outputStream.close()
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
