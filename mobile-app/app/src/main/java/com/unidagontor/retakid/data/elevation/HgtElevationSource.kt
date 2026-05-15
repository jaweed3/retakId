package com.unidagontor.retakid.data.elevation

import android.content.res.AssetManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.floor

data class HgtTile(
    val rows: Int,
    val cols: Int,
    val data: ShortArray
)

object HgtElevationSource {

    private const val SRTM3_SIZE = 1201
    private const val SRTM1_SIZE = 3601
    val NO_DATA = -32768.toShort()

    private val tileMap = mutableMapOf<Pair<Int, Int>, HgtTile>()

    val loadedTileCount: Int get() = tileMap.size
    val loadedTileNames: List<String> get() = tileMap.keys.map {
        formatTileName(it.first, it.second)
    }

    fun loadFromAssets(assetManager: AssetManager, fileName: String) {
        val inputStream = assetManager.open("dem/$fileName")
        val name = fileName.substringBeforeLast(".")
        parseAndRegister(inputStream, name)
    }

    fun loadFromDirectory(dir: File): Int {
        if (!dir.exists() || !dir.isDirectory) return 0
        var count = 0
        dir.listFiles { f -> f.extension.equals("hgt", ignoreCase = true) }?.forEach { file ->
            val name = file.nameWithoutExtension
            if (tileMap.containsKey(tileKeyFromName(name))) return@forEach
            try {
                val stream = file.inputStream()
                parseAndRegister(stream, name)
                count++
            } catch (_: Exception) { }
        }
        return count
    }

    suspend fun initFromFile(filePath: String) {
        withContext(Dispatchers.IO) {
            val file = File(filePath)
            if (!file.exists()) return@withContext
            val name = file.nameWithoutExtension
            val stream = file.inputStream()
            parseAndRegister(stream, name)
        }
    }

    private fun parseAndRegister(inputStream: InputStream, name: String) {
        val bytes = inputStream.readBytes()
        inputStream.close()

        val size = when (bytes.size) {
            SRTM3_SIZE * SRTM3_SIZE * 2 -> SRTM3_SIZE
            SRTM1_SIZE * SRTM1_SIZE * 2 -> SRTM1_SIZE
            else -> {
                kotlin.math.sqrt((bytes.size / 2).toDouble()).toInt()
            }
        }

        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val data = ShortArray(size * size)
        for (i in data.indices) {
            data[i] = buffer.getShort()
        }

        val latSouth = parseLat(name)
        val lonWest = parseLon(name)
        tileMap[Pair(latSouth, lonWest)] = HgtTile(
            rows = size,
            cols = size,
            data = data
        )
    }

    fun getElevation(latitude: Double, longitude: Double): Double? {
        val key = tileKeyFromCoord(latitude, longitude)
        val tile = tileMap[key] ?: return null

        val latSouth = key.first
        val lonWest = key.second

        val latOffset = latitude - latSouth
        val lonOffset = longitude - lonWest

        if (latOffset < 0.0 || latOffset > 1.0 || lonOffset < 0.0 || lonOffset > 1.0) {
            return null
        }

        val rowExact = (1.0 - latOffset) * (tile.rows - 1)
        val colExact = lonOffset * (tile.cols - 1)

        val row0 = rowExact.toInt().coerceIn(0, tile.rows - 2)
        val col0 = colExact.toInt().coerceIn(0, tile.cols - 2)
        val row1 = row0 + 1
        val col1 = col0 + 1

        val fracRow = rowExact - row0
        val fracCol = colExact - col0

        val v00 = tile.data[row0 * tile.cols + col0].toDouble()
        val v10 = tile.data[row0 * tile.cols + col1].toDouble()
        val v01 = tile.data[row1 * tile.cols + col0].toDouble()
        val v11 = tile.data[row1 * tile.cols + col1].toDouble()

        if (v00.toShort() == NO_DATA || v10.toShort() == NO_DATA ||
            v01.toShort() == NO_DATA || v11.toShort() == NO_DATA
        ) return null

        val top = v00 + (v10 - v00) * fracCol
        val bottom = v01 + (v11 - v01) * fracCol
        return top + (bottom - top) * fracRow
    }

    fun isLoaded(): Boolean = tileMap.isNotEmpty()

    fun clear() {
        tileMap.clear()
    }

    private fun tileKeyFromCoord(lat: Double, lon: Double): Pair<Int, Int> {
        return Pair(floor(lat).toInt(), floor(lon).toInt())
    }

    private fun tileKeyFromName(filename: String): Pair<Int, Int> {
        return Pair(parseLat(filename), parseLon(filename))
    }

    private fun parseLat(filename: String): Int {
        val regex = Regex("[NS](\\d{1,2})")
        val match = regex.find(filename) ?: return -99
        val value = match.groupValues[1].toInt()
        return if (filename.contains("S")) -value else value
    }

    private fun parseLon(filename: String): Int {
        val regex = Regex("[EW](\\d{1,3})")
        val match = regex.find(filename) ?: return -999
        val value = match.groupValues[1].toInt()
        return if (filename.contains("W")) -value else value
    }

    fun formatTileName(lat: Int, lon: Int): String {
        val ns = if (lat < 0) "S${(-lat).toString().padStart(2, '0')}" else "N${lat.toString().padStart(2, '0')}"
        val ew = if (lon < 0) "W${(-lon).toString().padStart(3, '0')}" else "E${lon.toString().padStart(3, '0')}"
        return "$ns$ew"
    }
}
