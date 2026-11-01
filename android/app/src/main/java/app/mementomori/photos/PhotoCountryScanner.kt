package app.mementomori.photos

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import app.mementomori.core.PhotoCountryHit
import app.mementomori.core.PhotoCountryTally
import app.mementomori.data.MapResources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

/**
 * Ищет страны по геометкам фото. Всё считается на телефоне: снимки никуда не отправляются,
 * читаются только место и дата съёмки.
 *
 * Чтобы не открывать каждый из тысяч снимков, фото одного часа считаются снятыми в одном месте:
 * геометка читается у первого снимка часа (или у следующих, если у первого её нет).
 */
object PhotoCountryScanner {
    enum class Access { FULL, LIMITED, DENIED }

    data class Result(val countries: List<PhotoCountryHit>, val scanned: Int, val geotagged: Int)

    /** Разрешения: фото и доступ к их геометкам. */
    fun permissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            Manifest.permission.ACCESS_MEDIA_LOCATION,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.ACCESS_MEDIA_LOCATION)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.ACCESS_MEDIA_LOCATION)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun access(context: Context): Access {
        fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        val full = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            granted(Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            granted(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        return when {
            full -> Access.FULL
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && granted(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) -> Access.LIMITED
            else -> Access.DENIED
        }
    }

    /** progress получает долю просмотренных снимков от 0 до 1. */
    suspend fun scan(context: Context, progress: (Double) -> Unit): Result = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val rows = mutableListOf<Pair<Long, Long>>()
        val projection = arrayOf(MediaStore.Images.Media._ID, MediaStore.Images.Media.DATE_TAKEN, MediaStore.Images.Media.DATE_ADDED)
        resolver.query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, projection, null, null, "${MediaStore.Images.Media.DATE_TAKEN} ASC")?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val takenColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val addedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            while (cursor.moveToNext()) {
                val taken = cursor.getLong(takenColumn)
                rows += cursor.getLong(idColumn) to (if (taken > 0) taken else cursor.getLong(addedColumn) * 1000)
            }
        }
        val tally = PhotoCountryTally()
        val hour = 3_600_000L
        var start = 0
        while (start < rows.size) {
            progress(start.toDouble() / rows.size)
            val bucket = rows[start].second / hour
            var end = start
            while (end < rows.size && rows[end].second / hour == bucket) end++
            // Геометка часа — у первого снимка, где она есть (пробуем не больше трёх).
            var location: DoubleArray? = null
            var index = start
            while (location == null && index < minOf(end, start + 3)) {
                location = latLong(context, rows[index].first)
                index++
            }
            location?.let { tally.add(it[0], it[1], Instant.ofEpochMilli(rows[start].second), count = end - start) }
            start = end
        }
        progress(1.0)
        val countries = tally.countries(MapResources.locator(context))
        Result(countries, rows.size, tally.geotagged)
    }

    private fun latLong(context: Context, id: Long): DoubleArray? {
        var uri: Uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Без этого Android отдаёт фото со стёртой геометкой.
            uri = runCatching { MediaStore.setRequireOriginal(uri) }.getOrDefault(uri)
        }
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).latLong }
        }.getOrNull()
    }
}
