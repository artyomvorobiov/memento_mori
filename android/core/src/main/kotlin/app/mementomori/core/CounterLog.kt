package app.mementomori.core

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.floor
import kotlin.math.roundToInt

// «Последний раз»: что показывают отметки «Было» у счётчика.

/** Напоминаем, когда перерыв в 1,25 раза дольше обычного. */
const val OVERDUE_FACTOR = 1.25

/** Чаще раза в неделю — это уже привычка, а не редкая встреча: напоминания ни к чему. */
const val MAX_PER_YEAR_FOR_REMINDERS = 52.0

val LifeCounter.lastDate: Instant? get() = log.lastOrNull()

/** Обычный промежуток между разами (в секундах) при заданной частоте. */
val LifeCounter.usualIntervalSeconds: Double? get() = if (perYear > 0) LifeStats.SECONDS_PER_YEAR / perYear else null

val LifeCounter.usualIntervalDays: Int?
    get() = usualIntervalSeconds?.let { maxOf(1, (it / LifeStats.SECONDS_PER_DAY).roundToInt()) }

/** Можно ли напоминать о долгом перерыве при такой частоте. */
val LifeCounter.supportsOverdueReminders: Boolean get() = perYear > 0 && perYear <= MAX_PER_YEAR_FOR_REMINDERS

/** Сколько дней назад было в последний раз (0 — сегодня). */
fun LifeCounter.daysSinceLast(now: Instant, zone: ZoneId = ZoneId.systemDefault()): Int? =
    lastDate?.let { maxOf(0L, daysBetween(it, now, zone)).toInt() }

fun LifeCounter.isLogged(day: Instant, zone: ZoneId = ZoneId.systemDefault()): Boolean = log.any { isSameDay(it, day, zone) }

/** Перерыв заметно дольше обычного. */
fun LifeCounter.isOverdue(now: Instant): Boolean {
    val last = lastDate ?: return false
    val interval = usualIntervalSeconds ?: return false
    return LifeStats.seconds(last, now) > interval * OVERDUE_FACTOR
}

/** Сколько обычных промежутков прошло с последнего раза: чем больше, тем «пора». */
fun LifeCounter.urgency(now: Instant): Double {
    val last = lastDate ?: return 0.0
    val interval = usualIntervalSeconds ?: return 0.0
    return maxOf(0.0, LifeStats.seconds(last, now)) / interval
}

/** Сколько раз было в календарном году даты date. */
fun LifeCounter.countInYearOf(date: Instant, zone: ZoneId = ZoneId.systemDefault()): Int {
    val year = date.atZone(zone).year
    return log.count { it.atZone(zone).year == year }
}

/** Сколько раз в год выходит на самом деле — по отметкам за последний год. null — судить рано. */
fun LifeCounter.actualPerYear(now: Instant): Double? {
    if (log.size < 2) return null
    val first = log.first()
    val yearAgo = now.minusMillis((LifeStats.SECONDS_PER_YEAR * 1000).toLong())
    val start = maxOf(minOf(createdAt, first), yearAgo)
    val span = LifeStats.seconds(start, now)
    if (span < 90 * LifeStats.SECONDS_PER_DAY) return null
    // Отметка в самом начале окна — точка отсчёта, а не результат периода.
    val count = log.count { it.isAfter(start) && !it.isAfter(now) }
    return count / (span / LifeStats.SECONDS_PER_YEAR)
}

/**
 * Когда напомнить о долгом перерыве: первый раз — когда он в 1,25 раза дольше обычного,
 * дальше — через каждый обычный промежуток. Время дня — как у остальных напоминаний.
 */
fun LifeCounter.nextOverdueReminder(after: Instant, hour: Int, minute: Int, zone: ZoneId = ZoneId.systemDefault()): Instant? {
    if (!remindsWhenOverdue || !supportsOverdueReminders) return null
    val last = lastDate ?: return null
    val interval = usualIntervalSeconds ?: return null
    val elapsed = LifeStats.seconds(last, after) / interval
    var step = maxOf(0, floor(elapsed - OVERDUE_FACTOR).toInt())
    repeat(3) {
        val due = last.plusMillis((interval * (OVERDUE_FACTOR + step) * 1000).toLong())
        val fire = due.localDate(zone).atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant()
        if (fire.isAfter(after) && fire.isAfter(last)) return fire
        step += 1
    }
    return null
}
