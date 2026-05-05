package com.unidagontor.retakid.util

import android.graphics.Bitmap

object BitmapUtils {
    /**
     * Memastikan bitmap dalam format ARGB_8888 yang dibutuhkan oleh TFLite.
     */
    fun ensureARGB8888(bitmap: Bitmap): Bitmap {
        return if (bitmap.config != Bitmap.Config.ARGB_8888) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
        } else {
            bitmap
        }
    }
}
