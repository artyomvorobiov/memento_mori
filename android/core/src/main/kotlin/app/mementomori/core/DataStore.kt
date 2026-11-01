package app.mementomori.core

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

sealed class DataStoreException(message: String, cause: Throwable?) : Exception(message, cause) {
    /** Файл есть, но прочитать его сейчас нельзя. */
    class Unreadable(cause: Throwable) : DataStoreException("Файл данных недоступен", cause)

    /** Файл прочитан, но содержимое повреждено. */
    class Corrupted(cause: Throwable) : DataStoreException("Файл данных повреждён", cause)
}

/**
 * Хранилище данных: один JSON-файл и папка с фото. Приложение и виджеты работают с одним файлом,
 * запись атомарная — читатель всегда видит целый файл.
 */
class DataStore(val directory: File) {
    val file: File get() = File(directory, FILE_NAME)
    val photosDirectory: File get() = File(directory, "Photos")
    val exists: Boolean get() = file.exists()

    /**
     * Читает данные. null — файла ещё нет. Если файл есть, но прочитать его не удалось,
     * бросает ошибку: в этом случае ни в коем случае нельзя перезаписывать файл пустыми данными.
     */
    fun load(): AppData? {
        if (!exists) return null
        val text = try {
            file.readText()
        } catch (error: IOException) {
            throw DataStoreException.Unreadable(error)
        }
        return try {
            decode(text)
        } catch (error: Exception) {
            throw DataStoreException.Corrupted(error)
        }
    }

    fun save(data: AppData) {
        directory.mkdirs()
        val temp = File(directory, "$FILE_NAME.tmp")
        temp.writeText(encode(data))
        Files.move(temp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    fun lastModified(): Long = if (exists) file.lastModified() else 0L

    /** Откладывает повреждённый файл в сторону, чтобы его можно было восстановить вручную. */
    fun quarantine(nowSeconds: Long = System.currentTimeMillis() / 1000): File? {
        if (!exists) return null
        val target = File(directory, "memento-data-unreadable-$nowSeconds.json")
        return if (file.renameTo(target)) target else null
    }

    companion object {
        const val FILE_NAME = "memento-data.json"

        fun encode(data: AppData): String = MementoJson.encodeToString(AppData.serializer(), data)

        fun decode(text: String): AppData = MementoJson.decodeFromString(AppData.serializer(), text).normalized()
    }
}
