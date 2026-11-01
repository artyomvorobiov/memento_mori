package app.mementomori.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import app.mementomori.core.CountryLocator
import app.mementomori.core.CountryStatus
import app.mementomori.core.LifeStats
import app.mementomori.core.WorldMapData
import kotlin.math.max
import kotlin.math.min

/** Карта и границы стран из assets — те же файлы, что у версии для iPhone. */
object MapResources {
    @Volatile
    private var map: WorldMapData? = null

    fun worldMap(context: Context): WorldMapData = map ?: synchronized(this) {
        map ?: WorldMapData.parse(context.assets.open("world-map.json").bufferedReader().use { it.readText() }).also { map = it }
    }

    /** Подробные границы для поиска стран по фото (~2 МБ) — загружаются только когда нужны. */
    fun locator(context: Context): CountryLocator =
        CountryLocator.parse(context.assets.open("country-lookup.json").bufferedReader().use { it.readText() })

    /** Кольца в android.graphics.Path — для картинок в виджетах и карточках. */
    fun androidPath(rings: List<FloatArray>): Path = Path().apply {
        for (ring in rings) {
            if (ring.size < 6) continue
            moveTo(ring[0], ring[1])
            var i = 2
            while (i + 1 < ring.size) {
                lineTo(ring[i], ring[i + 1])
                i += 2
            }
            close()
        }
    }
}

/** Картинка карты для виджетов и карточки «Мой мир»: посещённые — акцентом, планы — полупрозрачно. */
object MapBitmap {
    private const val ACCENT = 0xFFF2A541.toInt()

    fun render(context: Context, visited: Set<String>, wished: Set<String>, width: Int, height: Int): Bitmap {
        val map = MapResources.worldMap(context)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val scale = min(width / map.width, height / map.height)
        canvas.translate((width - map.width * scale) / 2, (height - map.height * scale) / 2)
        canvas.scale(scale, scale)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.6f / scale
            color = 0xFF000000.toInt()
        }
        fill.color = 0x1AFFFFFF
        canvas.drawPath(MapResources.androidPath(map.other), fill)
        for ((code, rings) in map.countries) {
            fill.color = colorFor(statusOf(code, visited, wished))
            val path = MapResources.androidPath(rings)
            canvas.drawPath(path, fill)
            canvas.drawPath(path, border)
        }
        // Маленькие страны — точкой, но только отмеченные: так карта чище.
        val radius = max(2.2f, 3f) / scale
        for ((code, center) in map.dots) {
            val status = statusOf(code, visited, wished)
            if (status == CountryStatus.NONE) continue
            fill.color = colorFor(status)
            canvas.drawCircle(center.first, center.second, radius, fill)
        }
        return bitmap
    }

    private fun statusOf(code: String, visited: Set<String>, wished: Set<String>) = when (code) {
        in visited -> CountryStatus.VISITED
        in wished -> CountryStatus.WISHED
        else -> CountryStatus.NONE
    }

    private fun colorFor(status: CountryStatus): Int = when (status) {
        CountryStatus.VISITED -> ACCENT
        CountryStatus.WISHED -> (0x59 shl 24) or (ACCENT and 0x00FFFFFF)
        CountryStatus.NONE -> 0x29FFFFFF
    }
}

/** Жизнь в годах: каждая точка — год; прожитые белые, текущий заполняется акцентом, будущие едва видны. */
object YearsBitmap {
    fun render(stats: LifeStats, width: Int, height: Int, columns: Int = 10): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val total = stats.lifeExpectancy
        val rows = (total + columns - 1) / columns
        val ratio = 0.45f
        val cell = max(1f, min(width / (columns + (columns - 1) * ratio), height / (rows + max(0, rows - 1) * ratio)))
        val gap = cell * ratio
        val gridWidth = columns * cell + (columns - 1) * gap
        val gridHeight = rows * cell + max(0, rows - 1) * gap
        val originX = (width - gridWidth) / 2
        val originY = (height - gridHeight) / 2
        val current = if (stats.isBeyondExpectancy) total else stats.ageYears
        val fraction = (stats.ageExact - stats.ageYears).toFloat().coerceIn(0f, 1f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (year in 0 until total) {
            val left = originX + (year % columns) * (cell + gap)
            val top = originY + (year / columns) * (cell + gap)
            val cx = left + cell / 2
            val cy = top + cell / 2
            paint.style = Paint.Style.FILL
            when {
                year < current -> {
                    paint.color = 0xD9FFFFFF.toInt()
                    canvas.drawCircle(cx, cy, cell / 2, paint)
                }
                year == current -> {
                    paint.color = 0x24FFFFFF
                    canvas.drawCircle(cx, cy, cell / 2, paint)
                    paint.color = 0xFFF2A541.toInt()
                    canvas.drawArc(left, top, left + cell, top + cell, -90f, 360f * fraction, true, paint)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = max(0.75f, cell * 0.08f)
                    canvas.drawCircle(cx, cy, cell / 2 - paint.strokeWidth / 2, paint)
                }
                else -> {
                    paint.color = 0x24FFFFFF
                    canvas.drawCircle(cx, cy, cell / 2, paint)
                }
            }
        }
        return bitmap
    }
}
