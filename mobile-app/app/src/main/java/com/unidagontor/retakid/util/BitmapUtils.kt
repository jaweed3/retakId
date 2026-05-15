package com.unidagontor.retakid.util

import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder

object BitmapUtils {
    private const val INPUT_SIZE = 224

    /**
     * Convert Bitmap to TFLite-compatible ByteBuffer.
     *
     * Model expects: uint8 [1, 224, 224, 3] in RGB order, range [0, 255].
     * Preprocessing (scaling to [-1, 1]) is baked into the TFLite model.
     */
    fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        // ── Pastikan bitmap adalah software-backed ────────────────────────
        // ImageDecoder (API 28+) menghasilkan HARDWARE bitmap secara default.
        // HARDWARE bitmap tidak support getPixels(), jadi harus dicopy ke
        // ARGB_8888 (software) terlebih dahulu sebelum diproses.
        val softBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }

        // Resize ke 224x224
        val resized = Bitmap.createScaledBitmap(softBitmap, INPUT_SIZE, INPUT_SIZE, true)

        val buffer = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        for (pixel in pixels) {
            // Android Bitmap stores ARGB. Extract R, G, B.
            // Model expects RGB order, uint8 [0, 255]
            buffer.put(((pixel shr 16) and 0xFF).toByte())  // R
            buffer.put(((pixel shr 8) and 0xFF).toByte())   // G
            buffer.put((pixel and 0xFF).toByte())            // B
        }

        buffer.rewind()
        return buffer
    }
}
