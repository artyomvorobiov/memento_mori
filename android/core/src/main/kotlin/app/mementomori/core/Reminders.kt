package app.mementomori.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

data class ReminderMessage(val title: String, val body: String)

data class PlannedReminder(val id: String, val date: Instant, val title: String, val body: String)

/** Тексты напоминаний. Чередуются по дням, чтобы не приедаться. */
object ReminderComposer {
    fun daily(date: Instant, profile: Profile, goals: List<Goal>, zone: ZoneId = ZoneId.systemDefault()): ReminderMessage {
        val stats = LifeStats(profile, date, zone)
        if (stats.isBeyondExpectancy) {
            return ReminderMessage("Memento mori", "Каждый день сверх ожидаемого — подарок. Проживи сегодняшний так, чтобы было что вспомнить.")
        }
        val day = dayNumber(date, zone)
        val active = goals.filter { !it.isCompleted }.sortedBy { it.createdAt }
        val focused = active.filter { it.isFocused }
        return when (positiveModulo(day, 4)) {
            0 -> quote(date, zone)
            1 -> pick(active, day / 4)?.let {
                ReminderMessage("Осталось ${Format.weeks(stats.remainingWeeks)}", "«${it.title}» всё ещё в списке. Может, начать сегодня?")
            } ?: quote(date, zone)
            2 -> pick(focused.ifEmpty { active }, day / 4)?.let {
                ReminderMessage("Один шаг сегодня", "Что можно сделать прямо сегодня для цели «${it.title}»?")
            } ?: progress(stats, goals)
            else -> progress(stats, goals)
        }
    }

    fun weekly(date: Instant, profile: Profile, goals: List<Goal>, zone: ZoneId = ZoneId.systemDefault()): ReminderMessage {
        val stats = LifeStats(profile, date, zone)
        val active = goals.filter { !it.isCompleted }.sortedBy { it.createdAt }
        val focused = active.filter { it.isFocused }
        var body = "Осталось ${Format.weeks(stats.remainingWeeks)}. "
        val goal = pick(focused.ifEmpty { active }, dayNumber(date, zone) / 7)
        body += if (goal != null) "Может, эта неделя — для «${goal.title}»?" else "Как ты проживёшь следующую?"
        return ReminderMessage("Прошла ещё одна неделя", body)
    }

    fun deadlineSoon(goal: Goal, deadline: Instant, zone: ZoneId = ZoneId.systemDefault()): ReminderMessage {
        val label = goal.targetAge?.let { Format.byAge(it) } ?: ("до " + Format.date(deadline, zone))
        return ReminderMessage("До срока — месяц", "«${goal.title}» — $label. Самое время начать.")
    }

    fun deadlineToday(goal: Goal) = ReminderMessage(
        "Срок настал",
        "«${goal.title}». Получилось? Если нет — не страшно: назначь новый срок и не откладывай.",
    )

    fun letterReady(letter: FutureLetter, zone: ZoneId = ZoneId.systemDefault()) = ReminderMessage(
        "📬 Письмо из прошлого",
        "Письмо от ${Format.date(letter.createdAt, zone)} можно открыть. Интересно, что изменилось с тех пор?",
    )

    fun counterOverdue(counter: LifeCounter, at: Instant, profile: Profile, zone: ZoneId = ZoneId.systemDefault()): ReminderMessage {
        var body = ""
        counter.daysSinceLast(at, zone)?.let { days ->
            body = "Последний раз — ${Format.daysAgo(days)}"
            counter.usualIntervalDays?.let { body += ", а обычно ${Format.everyInterval(it)}" }
            body += ". "
        }
        val left = counter.remaining(LifeStats(profile, at, zone).remainingYears)
        body += if (left > 0) "Впереди ≈ ${Format.number(left)} ${counter.title}. Может, пора?" else "Может, пора?"
        return ReminderMessage("${counter.emoji} Давно не было", body)
    }

    fun quote(date: Instant, zone: ZoneId = ZoneId.systemDefault()): ReminderMessage {
        val quote = Quotes.ofTheDay(date, zone = zone)
        return ReminderMessage("Memento mori", "«${quote.text}» — ${quote.author}")
    }

    fun progress(stats: LifeStats, goals: List<Goal>): ReminderMessage {
        val done = goals.count { it.isCompleted }
        val active = goals.size - done
        val body = if (done > 0) {
            "Выполнено: ${Format.goals(done)}. Впереди: ${Format.goals(active)}. Время идёт — и ты тоже."
        } else {
            "Время идёт. Что ты сделаешь с сегодняшним днём?"
        }
        return ReminderMessage("Прожито ${Format.percent(stats.progress)}", body)
    }

    private fun pick(goals: List<Goal>, seed: Int): Goal? = if (goals.isEmpty()) null else goals[positiveModulo(seed, goals.size)]
}

/**
 * Расписание напоминаний на месяц вперёд. Обновляется при каждом открытии приложения и после
 * перезагрузки телефона. Письма и «давно не было» приходят, даже если ежедневные напоминания выключены.
 */
object ReminderPlanner {
    const val MAX_COUNT = 60
    const val DAILY_HORIZON_DAYS = 30
    const val WEEKLY_HORIZON_WEEKS = 10

    /** Письма могут ждать годами — им место в расписании оставляем всегда. */
    const val MAX_LETTER_REMINDERS = 10

    fun plan(data: AppData, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): List<PlannedReminder> {
        val profile = data.profile ?: return emptyList()
        val letters = letterReminders(data, profile, now, zone).take(MAX_LETTER_REMINDERS)
        val regular = counterReminders(data, profile, now, zone).toMutableList()
        if (data.reminders.isEnabled) {
            regular += periodicReminders(data, profile, now, zone)
            if (data.reminders.deadlineAlerts) regular += deadlineReminders(data, profile, now, zone)
        }
        regular.sortBy { it.date }
        return (letters + regular.take(maxOf(0, MAX_COUNT - letters.size))).sortedBy { it.date }
    }

    private fun at(day: Instant, settings: ReminderSettings, zone: ZoneId): Instant =
        day.localDate(zone).atTime(LocalTime.of(settings.hour, settings.minute)).atZone(zone).toInstant()

    private fun periodicReminders(data: AppData, profile: Profile, now: Instant, zone: ZoneId): List<PlannedReminder> {
        val settings = data.reminders
        val result = mutableListOf<PlannedReminder>()
        val today = now.localDate(zone)
        when (settings.frequency) {
            ReminderFrequency.DAILY -> for (offset in 0 until DAILY_HORIZON_DAYS) {
                val fire = today.plusDays(offset.toLong()).atTime(LocalTime.of(settings.hour, settings.minute)).atZone(zone).toInstant()
                if (!fire.isAfter(now)) continue
                val message = ReminderComposer.daily(fire, profile, data.goals, zone)
                result += PlannedReminder("memento.daily.${dayNumber(fire, zone)}", fire, message.title, message.body)
            }
            ReminderFrequency.WEEKLY -> {
                val dayOfWeek = appleWeekdayToDayOfWeek(settings.weekday)
                var date = today.with(TemporalAdjusters.nextOrSame(dayOfWeek))
                var added = 0
                while (added < WEEKLY_HORIZON_WEEKS) {
                    val fire = date.atTime(LocalTime.of(settings.hour, settings.minute)).atZone(zone).toInstant()
                    if (fire.isAfter(now)) {
                        val message = ReminderComposer.weekly(fire, profile, data.goals, zone)
                        result += PlannedReminder("memento.weekly.${dayNumber(fire, zone)}", fire, message.title, message.body)
                        added++
                    }
                    date = date.plusWeeks(1)
                }
            }
        }
        return result
    }

    private fun deadlineReminders(data: AppData, profile: Profile, now: Instant, zone: ZoneId): List<PlannedReminder> {
        val settings = data.reminders
        val result = mutableListOf<PlannedReminder>()
        for (goal in data.activeGoals) {
            val deadline = goal.deadline(profile.birthDate, zone) ?: continue
            val monthBefore = at(deadline.atZone(zone).minusDays(30).toInstant(), settings, zone)
            if (monthBefore.isAfter(now)) {
                val message = ReminderComposer.deadlineSoon(goal, deadline, zone)
                result += PlannedReminder("memento.deadline.soon.${goal.id}", monthBefore, message.title, message.body)
            }
            val sameDay = at(deadline, settings, zone)
            if (sameDay.isAfter(now)) {
                val message = ReminderComposer.deadlineToday(goal)
                result += PlannedReminder("memento.deadline.today.${goal.id}", sameDay, message.title, message.body)
            }
        }
        return result
    }

    /** День открытия письма — в обычное время напоминаний. */
    private fun letterReminders(data: AppData, profile: Profile, now: Instant, zone: ZoneId): List<PlannedReminder> =
        data.letters.mapNotNull { letter ->
            if (letter.isOpened) return@mapNotNull null
            val fire = at(letter.unlockDate(profile.birthDate, zone), data.reminders, zone)
            if (!fire.isAfter(now)) return@mapNotNull null
            val message = ReminderComposer.letterReady(letter, zone)
            PlannedReminder(letterReminderId(letter.id), fire, message.title, message.body)
        }.sortedBy { it.date }

    /** По одному ближайшему напоминанию «давно не было» на счётчик. */
    private fun counterReminders(data: AppData, profile: Profile, now: Instant, zone: ZoneId): List<PlannedReminder> =
        data.counters.mapNotNull { counter ->
            val fire = counter.nextOverdueReminder(now, data.reminders.hour, data.reminders.minute, zone) ?: return@mapNotNull null
            val message = ReminderComposer.counterOverdue(counter, fire, profile, zone)
            PlannedReminder(counterReminderId(counter.id), fire, message.title, message.body)
        }

    fun letterReminderId(id: String) = "memento.letter.$id"
    fun counterReminderId(id: String) = "memento.counter.$id"

    /** 1 — воскресенье, 2 — понедельник… (так хранится на iPhone). */
    fun appleWeekdayToDayOfWeek(weekday: Int): DayOfWeek = DayOfWeek.of(positiveModulo(weekday - 2, 7) + 1)
}
