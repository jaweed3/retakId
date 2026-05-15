package com.unidagontor.retakid.data.ml

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * Downloads, applies, and manages delta-updated TFLite models.
 *
 * Flow:
 *  1. Download .rkd (Retak Delta) file from server
 *  2. Apply delta to existing model (bundled assets or cached)
 *  3. Save reconstructed model to internal storage
 *  4. MLAnalyzer loads from cache first, falls back to assets
 *
 * Delta format (.rkd):
 *  [4B magic "RKD1"] [4B num_regions (LE)]
 *  per region: [4B offset] [4B length] [lengthB data]
 *  Whole file is gzip-compressed.
 */
object DeltaModelLoader {

    private const val MODEL_FILE = "retak_mobilenetv2.tflite"
    private const val DELTA_DIR = "model_deltas"
    private const val DELTA_FILE = "delta.rkd"
    private const val CACHE_VERSION_FILE = "model_version.txt"

    /** Path to the cached/reconstructed model in internal storage. */
    fun getCachedModelPath(context: Context): File =
        File(context.filesDir, "$DELTA_DIR/$MODEL_FILE")

    private fun getDeltaFile(context: Context): File =
        File(context.cacheDir, DELTA_FILE)

    /** Version tracking file in internal storage. */
    fun getVersionFile(context: Context): File =
        File(context.filesDir, "$DELTA_DIR/$CACHE_VERSION_FILE")

    /** Returns true if a delta-updated model exists in internal storage. */
    fun hasCachedModel(context: Context): Boolean =
        getCachedModelPath(context).exists()

    /** Returns the currently cached model version string, or null. */
    fun getCachedVersion(context: Context): String? {
        val f = getVersionFile(context)
        return if (f.exists()) f.readText().trim() else null
    }

    /**
     * Download delta file from [deltaUrl] and apply it to the bundled model.
     * The reconstructed model is saved to internal storage.
     *
     * @param deltaUrl URL to the .rkd delta file
     * @param version  Version identifier (e.g. "v3b"), saved for tracking
     * @return true if patch succeeded, false otherwise
     */
    suspend fun applyDelta(
        context: Context,
        deltaUrl: String,
        version: String,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // 1. Download delta file
            val deltaFile = getDeltaFile(context)
            URL(deltaUrl).openStream().use { input ->
                deltaFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // 2. Decompress and parse delta
            val deltaBytes = deltaFile.readBytes()
            val decompressed = GZIPInputStream(deltaBytes.inputStream()).use { it.readBytes() }

            // 3. Verify magic
            val magic = decompressed.take(4).toByteArray()
            require(magic.contentEquals("RKD1".toByteArray())) { "Invalid delta magic" }

            // 4. Read number of regions
            var offset = 4
            val numRegions = littleEndianInt(decompressed, offset)
            offset += 4

            // 5. Read the bundled model (or cached model if exists)
            val oldModelBytes = if (hasCachedModel(context)) {
                getCachedModelPath(context).readBytes()
            } else {
                context.assets.open(MODEL_FILE).use { it.readBytes() }
            }

            // 6. Apply patches
            val newModelBytes = oldModelBytes.copyOf()
            for (i in 0 until numRegions) {
                val patchOffset = littleEndianInt(decompressed, offset)
                offset += 4
                val patchLength = littleEndianInt(decompressed, offset)
                offset += 4

                // Sanity check
                check(patchOffset + patchLength <= newModelBytes.size) {
                    "Patch out of bounds: offset=$patchOffset, length=$patchLength, file=${newModelBytes.size}"
                }

                // Overwrite bytes
                decompressed.copyInto(
                    destination = newModelBytes,
                    destinationOffset = patchOffset,
                    startIndex = offset,
                    endIndex = offset + patchLength,
                )
                offset += patchLength
            }

            // 7. Validate: try to create Interpreter with patched model
            try {
                val buffer = java.nio.ByteBuffer.wrap(newModelBytes)
                val interpreter = org.tensorflow.lite.Interpreter(buffer)
                interpreter.close()
            } catch (e: Exception) {
                // Patch produced invalid model — abort, don't save
                deltaFile.delete()
                throw IllegalStateException("Patched model failed validation: ${e.message}")
            }

            // 8. Save patched model to internal storage
            val modelPath = getCachedModelPath(context)
            modelPath.parentFile?.mkdirs()
            modelPath.writeBytes(newModelBytes)

            // 9. Save version
            getVersionFile(context).writeText(version)

            // 10. Cleanup delta file
            deltaFile.delete()

            true
        } catch (e: Exception) {
            android.util.Log.e("DeltaModelLoader", "applyDelta failed: ${e.message}", e)
            false
        }
    }

    /** Delete cached model and version info — reverts to bundled model. */
    fun revertToBundled(context: Context) {
        getCachedModelPath(context).delete()
        getVersionFile(context).delete()
    }

    private fun littleEndianInt(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or
        ((data[offset + 1].toInt() and 0xFF) shl 8) or
        ((data[offset + 2].toInt() and 0xFF) shl 16) or
        ((data[offset + 3].toInt() and 0xFF) shl 24)
}
