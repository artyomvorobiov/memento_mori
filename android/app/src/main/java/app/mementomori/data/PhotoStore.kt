package app.mementomori.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.util.LruCache
import app.mementomori.core.newId
import java.io.File

/** Фото-воспоминания о выполненных целях. Хранятся только на этом устройстве, рядом с данными. */
object PhotoStore {
    private const val MAX_DIMENSION = 2048
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    /** Копирует выбранное фото, уменьшив его. Возвращает имя файла или null. */
    fun save(context: Context, uri: Uri, directory: File): String? = runCatching {
        val bitmap = decode(context, uri) ?: return null
        directory.mkdirs()
        val name = newId() + ".jpg"
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        name
    }.getOrNull()

    fun delete(name: String, directory: File) {
        File(directory, name).delete()
        cache.snapshot().keys.filter { it.startsWith("$name@") }.forEach { cache.remove(it) }
    }

    /** Фото, уменьшенное до maxSize по большей стороне. */
    fun load(name: String, directory: File, maxSize: Int): Bitmap? {
        val key = "$name@$maxSize"
        cache.get(key)?.let { return it }
        val file = File(directory, name)
        if (!file.exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSize) sample *= 2
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        cache.put(key, bitmap)
        return bitmap
    }

    private fun decode(context: Context, uri: Uri): Bitmap? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val size = info.size
                val largest = maxOf(size.width, size.height)
                if (largest > MAX_DIMENSION) {
                    val scale = MAX_DIMENSION.toFloat() / largest
                    decoder.setTargetSize((size.width * scale).toInt(), (size.height * scale).toInt())
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_DIMENSION) sample *= 2
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
            }
        }
}
