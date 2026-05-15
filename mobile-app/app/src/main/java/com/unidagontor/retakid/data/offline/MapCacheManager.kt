package com.unidagontor.retakid.data.offline

import android.content.Context
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import java.io.File

/**
 * Mengatur tile cache OSMDroid untuk offline map.
 *
 * OSMDroid secara otomatis meng-cache tile yang sudah pernah dilihat.
 * Class ini:
 * 1. Mengatur ukuran cache (disk) menjadi 300 MB
 * 2. Mengekspos fungsi untuk menghitung estimasi jumlah tile
 *
 * Tile akan di-cache otomatis saat user membuka peta (online).
 * Saat offline, OSMDroid akan menggunakan cache tersebut secara transparan.
 */
object MapCacheManager {

    // Area default: Jenangan, Ponorogo dan sekitarnya
    val DEFAULT_CENTER = GeoPoint(-7.876, 111.470)
    val DEFAULT_BBOX   = BoundingBox(
        -7.70,  // north
        111.60, // east
        -8.00,  // south
        111.30  // west
    )

    fun configure(context: Context) {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName

            // Cache tile di internal storage
            val cacheDir = File(context.cacheDir, "osmdroid_tiles")
            cacheDir.mkdirs()
            osmdroidTileCache = cacheDir

            // Batas cache disk: 300 MB
            tileFileSystemCacheMaxBytes  = 300L * 1024 * 1024
            // Trim ke 250 MB saat mendekati limit
            tileFileSystemCacheTrimBytes = 250L * 1024 * 1024
        }
    }

    /** Apakah tile untuk region ini sudah cukup ter-cache (estimasi kasar) */
    fun isCacheReady(context: Context): Boolean {
        val cacheDir = File(context.cacheDir, "osmdroid_tiles")
        val sizeBytes = cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        return sizeBytes > 5L * 1024 * 1024  // setidaknya 5 MB tile tersedia
    }
}
