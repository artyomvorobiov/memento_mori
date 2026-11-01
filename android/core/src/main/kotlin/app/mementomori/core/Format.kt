package app.mementomori.core

import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Русские формы множественного числа: 1 неделя, 2 недели, 5 недель. */
object RussianPlural {
    fun form(number: Int, one: String, few: String, many: String): String {
        val n = abs(number)
        val mod10 = n % 10
        val mod100 = n % 100
        if (mod10 == 1 && mod100 != 11) return one
        if (mod10 in 2..4 && mod100 !in 12..14) return few
        return many
    }

    /** Для дробных чисел в русском используется родительный падеж единственного числа: «1,5 года». */
    fun form(number: Double, one: String, few: String, many: String): String =
        if (number == Math.rint(number) && abs(number) < Int.MAX_VALUE) form(number.toInt(), one, few, many) else few
}

/** Форматирование чисел и дат по-русски, независимо от языка устройства. */
object Format {
    val locale: Locale = Locale("ru", "RU")

    fun number(value: Int): String = NumberFormat.getIntegerInstance(locale).format(value)
    fun number(value: Long): String = NumberFormat.getIntegerInstance(locale).format(value)

    fun decimal(value: Double, digits: Int = 1): String = NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = digits
        maximumFractionDigits = digits
    }.format(value)

    /** 0.382 → «38,2%» */
    fun percent(fraction: Double, digits: Int = 1): String = decimal(fraction * 100, digits) + "%"

    fun count(value: Int, one: String, few: String, many: String): String = "${number(value)} ${RussianPlural.form(value, one, few, many)}"

    fun weeks(value: Int) = count(value, "неделя", "недели", "недель")
    fun days(value: Int) = count(value, "день", "дня", "дней")
    fun years(value: Int) = count(value, "год", "года", "лет")
    fun months(value: Int) = count(value, "месяц", "месяца", "месяцев")
    fun goals(value: Int) = count(value, "цель", "цели", "целей")
    fun times(value: Int) = count(value, "раз", "раза", "раз")

    /** Родительный падеж после «из»: «из 82 лет», «из 81 года», «из 4 278 недель». */
    fun genitive(value: Int, singular: String, plural: String): String {
        val n = abs(value)
        val word = if (n % 10 == 1 && n % 100 != 11) singular else plural
        return "${number(value)} $word"
    }

    /** Крупные числа покороче: «1,6 млрд», «418,5 млн». */
    fun compact(value: Double): String = when {
        value >= 1_000_000_000 -> decimal(value / 1_000_000_000) + " млрд"
        value >= 1_000_000 -> decimal(value / 1_000_000) + " млн"
        else -> number(value.toInt())
    }

    /** «к 35 годам», «к 21 году». */
    fun byAge(age: Int) = "к $age " + RussianPlural.form(age, "году", "годам", "годам")

    /** «в 34 года», «в 21 год», «в 25 лет». */
    fun atAge(age: Int) = "в $age " + RussianPlural.form(age, "год", "года", "лет")

    private fun formatter(pattern: String): DateTimeFormatter = DateTimeFormatter.ofPattern(pattern, locale)

    /** «12 марта 2026 г.» */
    fun date(date: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale).format(date.atZone(zone))

    /** «12 мар. 2026 г.» */
    fun shortDate(date: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        formatter("d MMM yyyy 'г.'").format(date.atZone(zone))

    /** «пн, 26 окт. — вс, 1 нояб. 2026 г.» */
    fun weekRange(start: Instant, end: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
        val first = formatter("EE, d MMM").format(start.atZone(zone))
        val last = formatter("EE, d MMM yyyy 'г.'").format(end.atZone(zone))
        return "$first — $last".lowercase(locale)
    }

    /** «март 2026 г.» */
    fun monthYear(date: Instant, zone: ZoneId = ZoneId.systemDefault()): String =
        formatter("LLLL yyyy 'г.'").format(date.atZone(zone))

    /** «9:05» */
    fun time(hour: Int, minute: Int): String = "%d:%02d".format(hour, minute)

    /** Время до события: «через 3 дня», «через 2 недели», «через 5 месяцев», «через 3 года». */
    fun relative(now: Instant, date: Instant, zone: ZoneId = ZoneId.systemDefault()): String {
        if (date.isBefore(now)) return "срок прошёл"
        // Считаем по календарным дням: «через год» от сегодняшнего утра — это ровно год, а не 11 месяцев.
        val start = now.localDate(zone)
        val end = date.localDate(zone)
        val days = ChronoUnit.DAYS.between(start, end).toInt()
        if (days <= 0) return "сегодня"
        if (days == 1) return "завтра"
        if (days < 14) return "через " + count(days, "день", "дня", "дней")
        if (days < 70) return "через " + count(days / 7, "неделю", "недели", "недель")
        val months = ChronoUnit.MONTHS.between(start, end).toInt()
        if (months == 12) return "через год"
        if (months < 24) return "через " + count(maxOf(1, months), "месяц", "месяца", "месяцев")
        return "через " + years(ChronoUnit.YEARS.between(start, end).toInt())
    }

    /** «раз в неделю», «раз в 10 дней», «раз в 2 месяца», «раз в полгода», «раз в год». */
    fun everyInterval(days: Int): String {
        if (days <= 1) return "каждый день"
        if (days % 7 == 0 && days <= 21) {
            val weeks = days / 7
            return if (weeks == 1) "раз в неделю" else "раз в " + count(weeks, "неделю", "недели", "недель")
        }
        if (days in 28..31) return "раз в месяц"
        if (days < 45) return "раз в " + count(days, "день", "дня", "дней")
        val months = (days / 30.44).roundToInt()
        if (months < 12) {
            if (months == 6) return "раз в полгода"
            return if (months <= 1) "раз в месяц" else "раз в " + count(months, "месяц", "месяца", "месяцев")
        }
        val years = (days / 365.2425).roundToInt()
        return if (years <= 1) "раз в год" else "раз в " + count(years, "год", "года", "лет")
    }

    /** «сегодня», «вчера», «12 дней назад», «больше года назад». */
    fun daysAgo(days: Int): String = when {
        days < 1 -> "сегодня"
        days == 1 -> "вчера"
        days < 365 -> count(days, "день", "дня", "дней") + " назад"
        else -> {
            val years = days / 365
            if (years == 1) "больше года назад" else "больше ${genitive(years, "года", "лет")} назад"
        }
    }

    /** Частота в год: «4 раза», «2,5 раза», «ни разу». */
    fun perYear(value: Double): String = when {
        value < 0.05 -> "ни разу"
        value == Math.rint(value) || value >= 10 -> times(value.roundToInt())
        else -> decimal(value) + " раза"
    }
}

/** «к 35 годам» → «К 35 годам». */
val String.capitalizedFirst: String
    get() = if (isEmpty()) this else substring(0, 1).uppercase(Format.locale) + substring(1)

// Подписи к срокам целей

object DeadlineText {
    /** Короткое описание срока: «к 35 годам · через 3 года». */
    fun summary(goal: Goal, birthDate: Instant?, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): String? {
        if (!goal.hasDeadline) return null
        val parts = mutableListOf<String>()
        val age = goal.targetAge
        val date = goal.targetDate
        if (age != null) parts += Format.byAge(age) else if (date != null) parts += "до " + Format.shortDate(date, zone)
        if (!goal.isCompleted && birthDate != null) {
            goal.deadline(birthDate, zone)?.let { parts += Format.relative(now, it, zone) }
        }
        return parts.joinToString(" · ")
    }

    fun isOverdue(goal: Goal, birthDate: Instant?, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (goal.isCompleted || birthDate == null) return false
        val deadline = goal.deadline(birthDate, zone) ?: return false
        return deadline.isBefore(now)
    }
}
