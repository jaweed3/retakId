package com.unidagontor.retakid.data.elevation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.ByteArrayInputStream
import java.net.URL
import java.util.zip.ZipInputStream

object TileDownloader {

    private const val SRTM_URL = "https://srtm.kurviger.de/SRTM3/Eurasia"
    private const val CONNECT_TIMEOUT = 10_000
    private const val READ_TIMEOUT = 30_000

    suspend fun downloadTile(name: String, destDir: File): Boolean = withContext(Dispatchers.IO) {
        try {
            destDir.mkdirs()
            val destFile = File(destDir, "$name.hgt")
            if (destFile.exists()) return@withContext true

            val url = URL("$SRTM_URL/$name.hgt.zip")
            val connection = url.openConnection()
            connection.connectTimeout = CONNECT_TIMEOUT
            connection.readTimeout = READ_TIMEOUT

            val zipBytes = connection.getInputStream().use { it.readBytes() }

            val zis = ZipInputStream(ByteArrayInputStream(zipBytes))
            val entry = zis.nextEntry
            if (entry != null && entry.name.endsWith(".hgt")) {
                val hgtBytes = zis.readBytes()
                destFile.writeBytes(hgtBytes)
            }
            zis.closeEntry()
            zis.close()

            destFile.exists()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun downloadTiles(names: List<String>, destDir: File): DownloadResult {
        var success = 0
        val failed = mutableListOf<String>()
        for ((i, name) in names.withIndex()) {
            val ok = downloadTile(name, destDir)
            if (ok) success++ else failed.add(name)
        }
        return DownloadResult(success, failed, names.size)
    }

    data class DownloadResult(
        val successCount: Int,
        val failedNames: List<String>,
        val totalRequested: Int
    ) {
        val allSucceeded: Boolean get() = failedNames.isEmpty()
    }
}
