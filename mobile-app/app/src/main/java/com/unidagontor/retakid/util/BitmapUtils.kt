package com.unidagontor.retakid.util

import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder

object BitmapUtils {
    private const val INPUT_SIZE = 224

    fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        // 1. Cek dan konversi HARDWARE bitmap menjadi Software Bitmap (ARGB_8888)
        val softwareBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }

        // 2. Resize ke 224x224 menggunakan software bitmap
        val resized = Bitmap.createScaledBitmap(softwareBitmap, INPUT_SIZE, INPUT_SIZE, true)

        val buffer = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)

        // Sekarang getPixels() aman dipanggil karena bitmap sudah berformat ARGB_8888
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        for (pixel in pixels) {
            // Android Bitmap menyimpan ARGB. Ekstrak R, G, B.
            // Model membutuhkan urutan RGB, uint8 [0, 255]
            buffer.put(((pixel shr 16) and 0xFF).toByte())   // R
            buffer.put(((pixel shr 8) and 0xFF).toByte())    // G
            buffer.put((pixel and 0xFF).toByte())            // B
        }

        buffer.rewind()

        // 3. (Opsional tapi direkomendasikan) Bersihkan memori dari copy bitmap
        if (softwareBitmap != bitmap) {
            softwareBitmap.recycle()
        }
        if (resized != softwareBitmap && resized != bitmap) {
            resized.recycle()
        }

        return buffer
    }
}
