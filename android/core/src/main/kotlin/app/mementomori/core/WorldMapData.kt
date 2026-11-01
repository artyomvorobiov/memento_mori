package app.mementomori.core

import kotlin.math.hypot

/** Прямоугольник в координатах карты. */
data class MapBounds(val minX: Float, val minY: Float, val maxX: Float, val maxY: Float) {
    val width: Float get() = maxX - minX
    val height: Float get() = maxY - minY
    val centerX: Float get() = (minX + maxX) / 2
    val centerY: Float get() = (minY + maxY) / 2
}

/**
 * Контуры стран для карты (Natural Earth 1:50 000 000, проекция Natural Earth I).
 * Ресурс world-map.json — тот же, что на iPhone (собирается скриптом scripts/build-world-map.py).
 * Кольцо — плоский массив [x0, y0, x1, y1, …] в координатах карты.
 */
class WorldMapData(
    val width: Float,
    val height: Float,
    val countries: Map<String, List<FloatArray>>,
    /** Части суши без отдельной страны в каталоге (Косово, ледник Сиачен…). */
    val other: List<FloatArray>,
    /** Маленькие страны дополнительно рисуются точкой, чтобы их было видно и можно было нажать. */
    val dots: Map<String, Pair<Float, Float>>,
) {
    /** Прямоугольник основной территории: у России без Чукотки за 180-м меридианом, у США без Алеут. */
    private val mainBounds: Map<String, MapBounds> = countries.mapValues { (_, rings) ->
        rings.map(::ringBounds).maxBy { it.width * it.height }
    }

    /** Страна в точке карты. Сначала маленькие страны-точки, потом контуры. */
    fun country(x: Float, y: Float, dotRadius: Float): String? {
        dots.entries
            .map { (code, center) -> code to hypot(center.first - x, center.second - y) }
            .filter { it.second <= dotRadius }
            .minByOrNull { it.second }
            ?.let { return it.first }
        for ((code, rings) in countries) {
            if (rings.any { ringContains(it, x, y) }) return code
        }
        return null
    }

    /** Прямоугольник страны на карте (по основной территории). */
    fun bounds(code: String): MapBounds? {
        mainBounds[code]?.takeIf { it.width > 1 || it.height > 1 }?.let { return it }
        dots[code]?.let { (x, y) -> return MapBounds(x - 10, y - 10, x + 10, y + 10) }
        return null
    }

    companion object {
        fun parse(json: String): WorldMapData {
            var width = 2000f
            var height = 872f
            val countries = mutableMapOf<String, List<FloatArray>>()
            val other = mutableListOf<FloatArray>()
            val dots = mutableMapOf<String, Pair<Float, Float>>()
            val scanner = JsonScanner(json)
            scanner.readObject { key ->
                when (key) {
                    "width" -> width = scanner.readDouble().toFloat()
                    "height" -> height = scanner.readDouble().toFloat()
                    "countries" -> scanner.readObject { code ->
                        val rings = mutableListOf<FloatArray>()
                        scanner.readArray { scanner.readFloatArray().takeIf { it.size >= 6 }?.let { rings += it } }
                        countries[code] = rings
                    }
                    "other" -> scanner.readArray { scanner.readFloatArray().takeIf { it.size >= 6 }?.let { other += it } }
                    "dots" -> scanner.readObject { code ->
                        val point = scanner.readFloatArray()
                        if (point.size == 2) dots[code] = point[0] to point[1]
                    }
                    else -> scanner.skipValue()
                }
            }
            return WorldMapData(width, height, countries, other, dots)
        }

        fun ringBounds(ring: FloatArray): MapBounds {
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            var i = 0
            while (i + 1 < ring.size) {
                minX = minOf(minX, ring[i]); maxX = maxOf(maxX, ring[i])
                minY = minOf(minY, ring[i + 1]); maxY = maxOf(maxY, ring[i + 1])
                i += 2
            }
            return MapBounds(minX, minY, maxX, maxY)
        }

        fun ringContains(ring: FloatArray, x: Float, y: Float): Boolean {
            val count = ring.size / 2
            var inside = false
            var j = count - 1
            for (i in 0 until count) {
                val xi = ring[2 * i]
                val yi = ring[2 * i + 1]
                val xj = ring[2 * j]
                val yj = ring[2 * j + 1]
                if ((yi > y) != (yj > y) && x < xi + (y - yi) / (yj - yi) * (xj - xi)) inside = !inside
                j = i
            }
            return inside
        }
    }
}
