package app.mementomori.core

import java.time.Instant
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Страна по координатам — без интернета, по границам Natural Earth 1:10m из ресурса
 * country-lookup.json (собирается скриптом scripts/build-country-lookup.py, тот же файл, что на iPhone).
 */
class CountryLocator private constructor(private val shapes: List<Shape>, private val circles: List<Circle>) {
    /** Кольцо границы в градусах: x — долгота, y — широта. */
    private class Ring(val xs: DoubleArray, val ys: DoubleArray) {
        val minX = xs.min()
        val maxX = xs.max()
        val minY = ys.min()
        val maxY = ys.max()
        val area: Double

        init {
            var sum = 0.0
            var j = xs.size - 1
            for (i in xs.indices) {
                sum += xs[j] * ys[i] - xs[i] * ys[j]
                j = i
            }
            area = abs(sum) / 2
        }

        fun boxContains(x: Double, y: Double, mx: Double = 0.0, my: Double = 0.0) =
            x >= minX - mx && x <= maxX + mx && y >= minY - my && y <= maxY + my

        /** Луч вправо из точки: нечётное число пересечений — точка внутри. */
        fun contains(x: Double, y: Double): Boolean {
            var inside = false
            var j = xs.size - 1
            for (i in xs.indices) {
                val yi = ys[i]
                val yj = ys[j]
                if ((yi > y) != (yj > y)) {
                    val crossX = xs[i] + (y - yi) / (yj - yi) * (xs[j] - xs[i])
                    if (x < crossX) inside = !inside
                }
                j = i
            }
            return inside
        }

        /** Расстояние до границы в километрах (местная плоская проекция — для малых расстояний этого хватает). */
        fun distanceKm(x: Double, y: Double): Double {
            val kx = 111.32 * cos(Math.toRadians(y))
            val ky = 110.57
            var best = Double.MAX_VALUE
            var j = xs.size - 1
            for (i in xs.indices) {
                val ax = (xs[j] - x) * kx
                val ay = (ys[j] - y) * ky
                val bx = (xs[i] - x) * kx
                val by = (ys[i] - y) * ky
                val dx = bx - ax
                val dy = by - ay
                val length = dx * dx + dy * dy
                val t = if (length > 0) (-(ax * dx + ay * dy) / length).coerceIn(0.0, 1.0) else 0.0
                val px = ax + t * dx
                val py = ay + t * dy
                best = minOf(best, sqrt(px * px + py * py))
                j = i
            }
            return best
        }
    }

    private class Shape(val code: String, val rings: List<Ring>) {
        val minX = rings.minOf { it.minX }
        val maxX = rings.maxOf { it.maxX }
        val minY = rings.minOf { it.minY }
        val maxY = rings.maxOf { it.maxY }

        fun boxContains(x: Double, y: Double, mx: Double = 0.0, my: Double = 0.0) =
            x >= minX - mx && x <= maxX + mx && y >= minY - my && y <= maxY + my
    }

    /** Места, которых нет в данных отдельной страной (Ватикан, Реюньон): круг вокруг центра. */
    private class Circle(val code: String, val latitude: Double, val longitude: Double, val radiusKm: Double)

    val countryCount: Int get() = shapes.size + circles.size

    /**
     * Страна в точке. Если точка в море рядом с берегом (фото с пляжа или с лодки) —
     * ближайшая страна не дальше nearbyKm.
     */
    fun country(latitude: Double, longitude: Double, nearbyKm: Double = 12.0): String? {
        if (!latitude.isFinite() || !longitude.isFinite() || abs(latitude) > 90 || abs(longitude) > 180) return null
        val x = longitude
        val y = latitude
        circles.firstOrNull { distanceKm(latitude, longitude, it.latitude, it.longitude) <= it.radiusKm }?.let { return it.code }

        // Внутри страны: нечётное число её колец (дырки — анклавы вроде Лесото внутри ЮАР).
        // Если подходят несколько стран, побеждает меньшее кольцо: Монако, а не Франция вокруг.
        var best: String? = null
        var bestArea = Double.MAX_VALUE
        for (shape in shapes) {
            if (!shape.boxContains(x, y)) continue
            var inside = false
            var smallest = Double.MAX_VALUE
            for (ring in shape.rings) {
                if (ring.boxContains(x, y) && ring.contains(x, y)) {
                    inside = !inside
                    smallest = minOf(smallest, ring.area)
                }
            }
            if (inside && smallest < bestArea) {
                best = shape.code
                bestArea = smallest
            }
        }
        if (best != null) return best
        if (nearbyKm <= 0) return null

        val mx = nearbyKm / (111.32 * maxOf(0.05, cos(Math.toRadians(y))))
        val my = nearbyKm / 110.57
        var nearest: String? = null
        var nearestDistance = Double.MAX_VALUE
        for (shape in shapes) {
            if (!shape.boxContains(x, y, mx, my)) continue
            for (ring in shape.rings) {
                if (!ring.boxContains(x, y, mx, my)) continue
                val distance = ring.distanceKm(x, y)
                if (distance <= nearbyKm && distance < nearestDistance) {
                    nearest = shape.code
                    nearestDistance = distance
                }
            }
        }
        return nearest
    }

    companion object {
        fun parse(json: String): CountryLocator {
            val shapes = mutableListOf<Shape>()
            val circles = mutableListOf<Circle>()
            var scale = 1000.0
            val scanner = JsonScanner(json)
            scanner.readObject { key ->
                when (key) {
                    "scale" -> scale = scanner.readDouble()
                    "countries" -> scanner.readObject { code ->
                        val rings = mutableListOf<Ring>()
                        scanner.readArray {
                            val values = scanner.readIntArray()
                            if (values.size >= 6) rings += decodeRing(values, scale)
                        }
                        if (rings.isNotEmpty()) shapes += Shape(code, rings)
                    }
                    "circles" -> scanner.readObject { code ->
                        val values = scanner.readFloatArray()
                        if (values.size == 3) circles += Circle(code, values[0].toDouble(), values[1].toDouble(), values[2].toDouble())
                    }
                    else -> scanner.skipValue()
                }
            }
            return CountryLocator(shapes.sortedBy { it.code }, circles.sortedBy { it.code })
        }

        /** Первая точка целиком, дальше — разницы с предыдущей. */
        private fun decodeRing(values: IntArray, scale: Double): Ring {
            val count = values.size / 2
            val xs = DoubleArray(count)
            val ys = DoubleArray(count)
            var x = 0
            var y = 0
            for (i in 0 until count) {
                x += values[2 * i]
                y += values[2 * i + 1]
                xs[i] = x / scale
                ys[i] = y / scale
            }
            return Ring(xs, ys)
        }

        /** Расстояние по большому кругу в километрах. */
        fun distanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
            val dLat = Math.toRadians(lat2 - lat1)
            val dLon = Math.toRadians(lon2 - lon1)
            val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
            return 6371 * 2 * atan2(sqrt(a), sqrt(1 - a))
        }
    }
}

/** Найденная в фото страна: сколько снимков и в какие годы. */
data class PhotoCountryHit(val code: String, val photos: Int, val first: Instant?, val last: Instant?) {
    /** «2019–2024» или «2023». */
    fun yearsText(zone: ZoneId = ZoneId.systemDefault()): String? {
        if (first == null || last == null) return null
        val start = first.atZone(zone).year
        val end = last.atZone(zone).year
        return if (start == end) "$start" else "$start–$end"
    }
}

/** Сводит геометки фото в страны. Отделено от галереи телефона, чтобы проверять на компьютере. */
class PhotoCountryTally {
    private class Cell(val latitude: Double, val longitude: Double, var photos: Int, var first: Instant?, var last: Instant?)

    /** Фото группируются по клеткам ≈ 1 км, чтобы не искать страну для каждого снимка. */
    private val cells = HashMap<Long, Cell>()
    var geotagged = 0
        private set

    val cellCount: Int get() = cells.size

    /** count — сколько снимков представляет эта геометка (снимки одного часа читаются по одному). */
    fun add(latitude: Double, longitude: Double, date: Instant?, count: Int = 1) {
        if (!latitude.isFinite() || !longitude.isFinite() || abs(latitude) > 90 || abs(longitude) > 180) return
        // (0, 0) — «пустая» геометка у некоторых камер и приложений.
        if (latitude == 0.0 && longitude == 0.0) return
        geotagged += count
        val key = Math.round(latitude * 100) * 1_000_000L + Math.round(longitude * 100)
        val cell = cells[key]
        if (cell == null) {
            cells[key] = Cell(latitude, longitude, count, date, date)
        } else {
            cell.photos += count
            if (date != null) {
                cell.first = cell.first?.let { minOf(it, date) } ?: date
                cell.last = cell.last?.let { maxOf(it, date) } ?: date
            }
        }
    }

    /** Страны по убыванию числа фото. */
    fun countries(locator: CountryLocator): List<PhotoCountryHit> {
        val hits = HashMap<String, PhotoCountryHit>()
        for (cell in cells.values) {
            val code = locator.country(cell.latitude, cell.longitude) ?: continue
            val hit = hits[code] ?: PhotoCountryHit(code, 0, null, null)
            hits[code] = hit.copy(
                photos = hit.photos + cell.photos,
                first = listOfNotNull(hit.first, cell.first).minOrNull(),
                last = listOfNotNull(hit.last, cell.last).maxOrNull(),
            )
        }
        return hits.values.sortedWith(compareByDescending<PhotoCountryHit> { it.photos }.thenBy { it.code })
    }
}
