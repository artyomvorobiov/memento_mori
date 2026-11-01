package app.mementomori.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

// Календарь: григорианский, неделя с понедельника, часовой пояс устройства.

fun Instant.localDate(zone: ZoneId = ZoneId.systemDefault()): LocalDate = atZone(zone).toLocalDate()

fun startOfDay(date: Instant, zone: ZoneId = ZoneId.systemDefault()): Instant =
    date.localDate(zone).atStartOfDay(zone).toInstant()

fun isSameDay(a: Instant, b: Instant, zone: ZoneId = ZoneId.systemDefault()): Boolean = a.localDate(zone) == b.localDate(zone)

/** Число календарных дней между датами (по местному времени). */
fun daysBetween(from: Instant, to: Instant, zone: ZoneId = ZoneId.systemDefault()): Long =
    ChronoUnit.DAYS.between(from.localDate(zone), to.localDate(zone))

/** Порядковый номер дня — меняется в полночь по местному времени. */
fun dayNumber(date: Instant, zone: ZoneId = ZoneId.systemDefault()): Int = date.localDate(zone).toEpochDay().toInt()

fun positiveModulo(value: Int, modulus: Int): Int {
    if (modulus <= 0) return 0
    val result = value % modulus
    return if (result >= 0) result else result + modulus
}

/** Все расчёты «сколько прожито и сколько осталось». Ожидаемый конец жизни — день рождения номер lifeExpectancy. */
class LifeStats(
    val birthDate: Instant,
    lifeExpectancy: Int,
    val now: Instant = Instant.now(),
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    constructor(profile: Profile, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()) :
        this(profile.birthDate, profile.lifeExpectancy, now, zone)

    val lifeExpectancy: Int = maxOf(1, lifeExpectancy)
    private val birth: ZonedDateTime = birthDate.atZone(zone)
    val deathDate: Instant = birth.plusYears(this.lifeExpectancy.toLong()).toInstant()

    /** Те же параметры, но на другой момент времени. */
    fun at(date: Instant): LifeStats = LifeStats(birthDate, lifeExpectancy, date, zone)

    // Прожито / осталось

    val totalSeconds: Double get() = seconds(birthDate, deathDate)
    val livedSeconds: Double get() = maxOf(0.0, seconds(birthDate, now))
    val remainingSeconds: Double get() = maxOf(0.0, seconds(now, deathDate))

    /** Доля прожитой жизни от 0 до 1. */
    val progress: Double get() = if (totalSeconds <= 0) 1.0 else (livedSeconds / totalSeconds).coerceIn(0.0, 1.0)

    val isBeyondExpectancy: Boolean get() = !now.isBefore(deathDate)

    val ageYears: Int get() = age(now)
    val ageExact: Double get() = livedSeconds / SECONDS_PER_YEAR

    val remainingYears: Double get() = remainingSeconds / SECONDS_PER_YEAR
    val remainingMonths: Int
        get() = if (isBeyondExpectancy) 0 else maxOf(0L, ChronoUnit.MONTHS.between(now.atZone(zone), deathDate.atZone(zone))).toInt()
    val remainingDays: Int get() = (remainingSeconds / SECONDS_PER_DAY).toInt()
    val remainingHours: Int get() = (remainingSeconds / 3600).toInt()

    // Недели — календарные, с понедельника по воскресенье: счётчик уменьшается в ночь на понедельник.

    /** Сколько недель впереди, считая текущую неделю прожитой. */
    val remainingWeeks: Int
        get() = if (!now.isBefore(deathDate)) 0 else weeks(weekStart(maxOf(now, birthDate)), weekStart(deathDate))

    /** Сколько полных недель прожито с недели рождения. */
    val livedWeeks: Int
        get() = if (!now.isAfter(birthDate)) 0 else weeks(weekStart(birthDate), weekStart(minOf(now, deathDate)))

    val totalWeeks: Int get() = weeks(weekStart(birthDate), weekStart(deathDate))

    /** Понедельник 00:00 недели, в которую попадает дата. */
    fun weekStart(date: Instant): Instant =
        date.localDate(zone).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).atStartOfDay(zone).toInstant()

    private fun weeks(start: Instant, end: Instant): Int = maxOf(0L, daysBetween(start, end, zone) / 7).toInt()

    // «Осталось примерно»

    val summersLeft: Int get() = occurrencesLeft(7, 1)
    val newYearsLeft: Int get() = occurrencesLeft(1, 1)
    val fullMoonsLeft: Int get() = (remainingDays / DAYS_PER_LUNAR_MONTH).toInt()

    /** Ударов сердца при среднем пульсе 70 в минуту. */
    val heartbeatsLeft: Double get() = remainingSeconds / 60 * 70

    /** Сколько раз дата «день.месяц» встретится в промежутке (сейчас; конец]. */
    fun occurrencesLeft(month: Int, day: Int): Int {
        if (!now.isBefore(deathDate)) return 0
        val firstYear = now.atZone(zone).year
        val lastYear = deathDate.atZone(zone).year
        var count = 0
        for (year in firstYear..lastYear) {
            val date = LocalDate.of(year, month, day).atStartOfDay(zone).toInstant()
            if (date.isAfter(now) && !date.isAfter(deathDate)) count++
        }
        return count
    }

    // Сетка «жизнь в неделях»

    val gridRows: Int get() = lifeExpectancy
    val gridCellCount: Int get() = gridRows * WEEKS_PER_ROW

    /** Начало строки возраста row: понедельник недели, на которую выпал день рождения. */
    fun rowStart(row: Int): Instant = weekStart(birth.plusYears(row.toLong()).toInstant())

    /** Клетка сетки, на которую приходится дата (null — до рождения или за пределами сетки). */
    fun gridIndex(date: Instant): Int? {
        if (date.isBefore(birthDate)) return null
        var row = age(date)
        // Неделя следующего дня рождения уже относится к следующей строке.
        if (!date.isBefore(rowStart(row + 1))) row += 1
        if (row >= gridRows) return null
        val days = daysBetween(rowStart(row), date, zone).toInt()
        val week = (days / 7).coerceIn(0, WEEKS_PER_ROW - 1)
        return row * WEEKS_PER_ROW + week
    }

    val currentGridIndex: Int? get() = gridIndex(now)

    /** Сколько клеток уже прожито (текущая неделя не считается прожитой). */
    val livedGridCells: Int get() = currentGridIndex ?: if (now.isBefore(birthDate)) 0 else gridCellCount

    /** Даты клетки: с понедельника по воскресенье. Если в году жизни 53 недели, последняя клетка охватывает две. */
    fun dateRange(index: Int): Pair<Instant, Instant>? {
        if (index < 0 || index >= gridCellCount) return null
        val row = index / WEEKS_PER_ROW
        val column = index % WEEKS_PER_ROW
        val start = rowStart(row).atZone(zone).plusDays(column * 7L).toInstant()
        val end = if (column == WEEKS_PER_ROW - 1) {
            rowStart(row + 1).minusSeconds(1)
        } else {
            start.atZone(zone).plusDays(7).toInstant().minusSeconds(1)
        }
        return if (end.isBefore(start)) null else start to end
    }

    /** Возраст (полных лет) в начале клетки. */
    fun ageForGridIndex(index: Int): Int = index / WEEKS_PER_ROW

    /** Возраст в момент date (полных лет). */
    fun age(date: Instant): Int = maxOf(0L, ChronoUnit.YEARS.between(birth, date.atZone(zone))).toInt()

    companion object {
        const val SECONDS_PER_DAY = 86_400.0
        const val SECONDS_PER_YEAR = 365.2425 * 86_400
        const val DAYS_PER_LUNAR_MONTH = 29.530588
        /** В «жизни в неделях» каждая строка — год жизни из 52 клеток. */
        const val WEEKS_PER_ROW = 52

        fun seconds(from: Instant, to: Instant): Double = (to.toEpochMilli() - from.toEpochMilli()) / 1000.0
    }
}
