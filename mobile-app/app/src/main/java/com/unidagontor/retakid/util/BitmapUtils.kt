package com.unidagontor.retakid.util

import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder

object BitmapUtils {
    private const val INPUT_SIZE = 224

    data class ValidationResult(val valid: Boolean, val message: String? = null)

    /**
     * Validate image quality before inference.
     * Checks blur (Laplacian variance) and brightness levels.
     */
    fun validateImage(bitmap: Bitmap): ValidationResult {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        val gray = IntArray(width * height) { i ->
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            (0.299 * r + 0.587 * g + 0.114 * b).toInt()
        }

        val meanBrightness = gray.average()
        if (meanBrightness < 30) return ValidationResult(false, "Foto gelap — aktifkan flash")
        if (meanBrightness > 220) return ValidationResult(false, "Foto terlalu terang — hindari cahaya langsung")

        var lapSum = 0.0
        var count = 0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val c = gray[y * width + x]
                val l = gray[y * width + (x - 1)]
                val r = gray[y * width + (x + 1)]
                val t = gray[(y - 1) * width + x]
                val b = gray[(y + 1) * width + x]
                val laplacian = (4 * c - l - r - t - b)
                lapSum += laplacian * laplacian
                count++
            }
        }

        val variance = (lapSum / count).toFloat()
        if (variance < 100f) return ValidationResult(false, "Foto buram — dekatkan kamera ke retakan tanah")

        return ValidationResult(true)
    }

    /**
     * Convert Bitmap to TFLite-compatible ByteBuffer.
     *
     * Model expects: uint8 [1, 224, 224, 3] in RGB order, range [0, 255].
     * Preprocessing (scaling to [-1, 1]) is baked into the TFLite model.
     */
    fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)

        val buffer = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        for (pixel in pixels) {
            buffer.put(((pixel shr 16) and 0xFF).toByte())
            buffer.put(((pixel shr 8) and 0xFF).toByte())
            buffer.put((pixel and 0xFF).toByte())
        }

        buffer.rewind()
        return buffer
    }
}
