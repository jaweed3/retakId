package com.unidagontor.retakid.data.ml

import android.content.Context
import com.unidagontor.retakid.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Periodically checks for model updates via Supabase edge function.
 *
 * On app launch (or periodically), calls check-model-update edge function.
 * If a newer version exists:
 *   1. Download the .rkd delta file
 *   2. Apply it via DeltaModelLoader
 *   3. Next inference uses the updated model
 *
 * Usage:
 *   ModelUpdateChecker.checkForUpdate(context, "v3a")
 *     .onSuccess { /* update applied or not needed */ }
 *     .onFailure { /* log, retry later */ }
 */
object ModelUpdateChecker {

    private const val TAG = "ModelUpdateChecker"
    private const val EDGE_FUNCTION_PATH = "/functions/v1/check-model-update"
    private const val BUNDLED_VERSION = "v3a"

    /**
     * Check for model update and apply if available.
     *
     * @param context Application context
     * @param currentVersion Version string currently installed (defaults to bundled version)
     * @return Result with applied version or null if up-to-date
     */
    suspend fun checkForUpdate(
        context: Context,
        currentVersion: String? = null,
    ): Result<String?> = withContext(Dispatchers.IO) {
        try {
            val version = currentVersion
                ?: DeltaModelLoader.getCachedVersion(context)
                ?: BUNDLED_VERSION

            // 1. Call edge function
            val edgeUrl = "${BuildConfig.SUPABASE_URL}$EDGE_FUNCTION_PATH"
            val json = JSONObject().apply { put("current_version", version) }
            val response = httpPost(edgeUrl, json.toString())

            val result = JSONObject(response)
            if (!result.optBoolean("update_available", false)) {
                android.util.Log.i(TAG, "Model is up-to-date ($version)")
                return@withContext Result.success(null)
            }

            val latestVersion = result.getString("latest_version")
            val deltaUrl = result.optString("delta_url", "")
            val fullUrl = result.optString("full_url", "")
            val changelog = result.optString("changelog", "")

            android.util.Log.i(TAG, "Update available: $version → $latestVersion")
            android.util.Log.i(TAG, "Changelog: $changelog")

            // 2. Try delta first
            var success = false
            if (deltaUrl.isNotEmpty()) {
                android.util.Log.i(TAG, "Downloading delta…")
                success = DeltaModelLoader.applyDelta(context, deltaUrl, latestVersion)
            }

            // 3. Fall back to full model if delta failed
            if (!success && fullUrl.isNotEmpty()) {
                android.util.Log.w(TAG, "Delta failed, downloading full model…")
                success = downloadFullModel(context, fullUrl, latestVersion)
            }

            if (success) {
                android.util.Log.i(TAG, "Model updated to $latestVersion")
                Result.success(latestVersion)
            } else {
                Result.failure(Exception("Failed to update model from all sources"))
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "checkForUpdate failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /** Download full TFLite model as fallback when delta fails. */
    private suspend fun downloadFullModel(
        context: Context,
        url: String,
        version: String,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val modelFile = DeltaModelLoader.getCachedModelPath(context)
            modelFile.parentFile?.mkdirs()
            URL(url).openStream().use { input ->
                modelFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            val buffer = java.nio.ByteBuffer.wrap(modelFile.readBytes())
            org.tensorflow.lite.Interpreter(buffer).close()
            val versionFile = File(
                context.filesDir,
                "model_deltas/model_version.txt"
            )
            versionFile.writeText(version)
            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Full model download failed: ${e.message}", e)
            false
        }
    }

    /** Simple HTTP POST returning response body as string. */
    private fun httpPost(urlString: String, body: String): String {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000

        conn.outputStream.use { it.write(body.toByteArray()) }

        val responseCode = conn.responseCode
        if (responseCode !in 200..299) {
            val error = conn.errorStream?.bufferedReader()?.readText() ?: "Unknown"
            throw RuntimeException("HTTP $responseCode: $error")
        }

        return conn.inputStream.bufferedReader().readText()
    }
}
