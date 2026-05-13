package com.unidagontor.retakid.data.photo

import androidx.exifinterface.media.ExifInterface

object ExifReader {

    fun read(filePath: String): ExifData? {
        return try {
            val exif = ExifInterface(filePath)

            val latLng = exif.latLong
            val altitude = exif.getAltitude(Double.NaN)

            ExifData(
                latitude = latLng?.getOrNull(0),
                longitude = latLng?.getOrNull(1),
                altitudeMeters = if (altitude.isNaN()) null else altitude,
                timestamp = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            )
        } catch (_: Exception) {
            null
        }
    }
}
