package app.mementomori.core

import java.time.Instant
import java.time.ZoneId

// Что показывают виджеты. Логика здесь, чтобы её можно было проверить без телефона.

/** Цель дня: по кругу из целей в фокусе (или из всех активных), «Другая» сдвигает очередь. */
fun AppData.goalOfTheDay(date: Instant, shuffle: Int, zone: ZoneId = ZoneId.systemDefault()): Goal? {
    val active = activeGoals.sortedBy { it.createdAt }
    val pool = active.filter { it.isFocused }.ifEmpty { active }
    if (pool.isEmpty()) return null
    return pool[positiveModulo(dayNumber(date, zone) + shuffle, pool.size)]
}

/** Счётчики для виджета «Последний раз»: сначала те, где «пора», потом остальные с отметками. */
fun AppData.countersByUrgency(now: Instant): List<LifeCounter> =
    counters.sortedWith(
        compareByDescending<LifeCounter> { if (it.log.isEmpty()) -1.0 else it.urgency(now) }.thenBy { it.createdAt },
    )

/** Письмо для виджета: ждущее открытия или ближайшее запечатанное. */
fun AppData.letterForWidget(now: Instant, zone: ZoneId = ZoneId.systemDefault()): Pair<FutureLetter, Boolean>? {
    waitingLetters(now, zone).firstOrNull()?.let { return it to true }
    return sealedLetters(now, zone).firstOrNull()?.let { it to false }
}

/** «≈ 98 встреч с родителями» — название счётчика читается после числа. */
fun counterRemainingText(counter: LifeCounter, stats: LifeStats?): String {
    stats ?: return counter.title
    return "≈ ${Format.number(counter.remaining(stats.remainingYears))} ${counter.title}"
}
