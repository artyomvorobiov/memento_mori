package app.mementomori.core

/** В чём считать оставшееся время в виджете «Время жизни». */
enum class LifeUnit(val raw: String, val title: String) {
    YEARS("years", "Годы"),
    MONTHS("months", "Месяцы"),
    WEEKS("weeks", "Недели"),
    DAYS("days", "Дни"),
    HOURS("hours", "Часы"),
    SECONDS("seconds", "Секунды (живой отсчёт)");

    /** Число для показа. Для секунд в виджете — тикающий таймер. */
    fun value(stats: LifeStats): Long = when (this) {
        YEARS -> stats.remainingYears.toLong()
        MONTHS -> stats.remainingMonths.toLong()
        WEEKS -> stats.remainingWeeks.toLong()
        DAYS -> stats.remainingDays.toLong()
        HOURS -> stats.remainingHours.toLong()
        SECONDS -> stats.remainingSeconds.toLong()
    }

    /** Подпись под числом: «недель впереди», «дня впереди»… */
    fun caption(value: Long): String {
        val n = (value % 1000).toInt()
        return when (this) {
            YEARS -> RussianPlural.form(n, "год впереди", "года впереди", "лет впереди")
            MONTHS -> RussianPlural.form(n, "месяц впереди", "месяца впереди", "месяцев впереди")
            WEEKS -> RussianPlural.form(n, "неделя впереди", "недели впереди", "недель впереди")
            DAYS -> RussianPlural.form(n, "день впереди", "дня впереди", "дней впереди")
            HOURS -> RussianPlural.form(n, "час впереди", "часа впереди", "часов впереди")
            SECONDS -> "часов : минут : секунд"
        }
    }

    /** Короткая запись для строк: «2 345 недель». */
    fun short(stats: LifeStats): String {
        val value = value(stats)
        val n = (value % 1000).toInt()
        val number = Format.number(value)
        return number + " " + when (this) {
            YEARS -> RussianPlural.form(n, "год", "года", "лет")
            MONTHS -> RussianPlural.form(n, "месяц", "месяца", "месяцев")
            WEEKS -> RussianPlural.form(n, "неделя", "недели", "недель")
            DAYS -> RussianPlural.form(n, "день", "дня", "дней")
            HOURS -> RussianPlural.form(n, "час", "часа", "часов")
            SECONDS -> RussianPlural.form(n, "секунда", "секунды", "секунд")
        }
    }

    companion object {
        fun fromRaw(raw: String?): LifeUnit = entries.firstOrNull { it.raw == raw } ?: WEEKS
    }
}
