package app.mementomori.core

import java.time.Instant
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/** «Темп»: успеваешь ли закрыть свой список при текущей скорости. */
class PaceInsight(goals: List<Goal>, stats: LifeStats, trackingSince: Instant) {
    val activeCount: Int = goals.count { !it.isCompleted }

    /** Период наблюдения в днях (от 30 до 365). */
    val observedDays: Int

    /** Сколько целей закрыто за период наблюдения. */
    val completedRecently: Int

    /** Фактический темп — целей в год (null, если пока ничего не закрыто). */
    val perYear: Double?

    /** Сколько лет уйдёт на весь список в текущем темпе. */
    val yearsNeeded: Double?

    /** Возраст, к которому список будет закрыт в текущем темпе. */
    val finishAge: Double?

    /** Успеваешь ли в текущем темпе. */
    val fitsInLife: Boolean?

    /** Сколько недель можно тратить на одну цель, чтобы успеть всё. */
    val weeksPerGoalNeeded: Double?

    init {
        val now = stats.now
        val trackedDays = maxOf(0.0, LifeStats.seconds(trackingSince, now) / LifeStats.SECONDS_PER_DAY)
        val window = trackedDays.coerceIn(30.0, 365.0)
        observedDays = window.toInt()
        val windowStart = now.minusMillis((window * LifeStats.SECONDS_PER_DAY * 1000).toLong())
        completedRecently = goals.count { goal ->
            val date = goal.completedAt ?: return@count false
            !date.isBefore(windowStart) && !date.isAfter(now)
        }
        if (completedRecently > 0) {
            val rate = completedRecently / window * 365.2425
            val needed = activeCount / rate
            perYear = rate
            yearsNeeded = needed
            finishAge = stats.ageExact + needed
            fitsInLife = needed <= stats.remainingYears
        } else {
            perYear = null
            yearsNeeded = null
            finishAge = null
            fitsInLife = null
        }
        weeksPerGoalNeeded = if (activeCount > 0 && stats.remainingWeeks > 0) stats.remainingWeeks.toDouble() / activeCount else null
    }

    /** Человекочитаемые выводы для экрана «Сегодня». */
    val lines: List<String>
        get() {
            if (activeCount <= 0) {
                return listOf("Список пуст. Добавь то, что хочешь успеть, — и здесь появится твой темп.")
            }
            val result = mutableListOf<String>()
            val weeks = weeksPerGoalNeeded
            if (weeks != null) {
                val goalsText = Format.goals(activeCount)
                when {
                    activeCount == 1 ->
                        result += "Впереди одна цель — и примерно ${Format.weeks(weeks.toInt())}, чтобы её выполнить. Не откладывай."
                    weeks >= 2 -> {
                        val interval = Format.count(floor(weeks).toInt(), "неделю", "недели", "недель")
                        result += "Впереди $goalsText. Чтобы успеть всё, закрывай по одной каждые $interval."
                    }
                    weeks >= 1 -> result += "Впереди $goalsText. Чтобы успеть всё, закрывай по одной каждую неделю."
                    else -> result += "Впереди $goalsText — это по ${Format.decimal(1 / weeks)} в неделю. Может, оставить только главное?"
                }
            } else {
                result += "Время по плану вышло, а впереди ещё ${Format.goals(activeCount)}. Каждый день — подарок: начни с главного."
            }

            val perYear = perYear
            val finishAge = finishAge
            val fitsInLife = fitsInLife
            val yearsNeeded = yearsNeeded
            if (perYear != null && finishAge != null && fitsInLife != null && yearsNeeded != null) {
                val tenths = (perYear * 10).roundToInt() / 10.0
                val pace = if (tenths >= 10 || tenths == Math.rint(tenths)) {
                    Format.goals(perYear.roundToInt())
                } else {
                    "${Format.decimal(tenths)} " + RussianPlural.form(tenths, "цель", "цели", "целей")
                }
                result += if (fitsInLife) {
                    "Твой темп — $pace в год. В этом темпе список будет закрыт ${Format.byAge(ceil(finishAge).toInt())}."
                } else {
                    "Твой темп — $pace в год. В этом темпе понадобится ${Format.years(ceil(yearsNeeded).toInt())} — больше, чем осталось. Ускорься или выбери главное."
                }
            } else {
                result += "Закрой первую цель — и здесь появится твой реальный темп."
            }
            return result
        }
}
