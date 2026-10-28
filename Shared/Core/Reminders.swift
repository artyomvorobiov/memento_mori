import Foundation

struct ReminderMessage: Hashable {
    let title: String
    let body: String
}

struct PlannedReminder: Hashable {
    let id: String
    let date: Date
    let title: String
    let body: String
}

/// Тексты напоминаний. Чередуются по дням, чтобы не приедаться.
enum ReminderComposer {
    static func daily(date: Date, profile: Profile, goals: [Goal], calendar: Calendar = .memento) -> ReminderMessage {
        let stats = LifeStats(profile: profile, now: date, calendar: calendar)
        if stats.isBeyondExpectancy {
            return ReminderMessage(
                title: "Memento mori",
                body: "Каждый день сверх ожидаемого — подарок. Проживи сегодняшний так, чтобы было что вспомнить."
            )
        }
        let day = calendar.dayNumber(for: date)
        let active = goals.filter { !$0.isCompleted }.sorted { $0.createdAt < $1.createdAt }
        let focused = active.filter(\.isFocused)

        switch positiveModulo(day, 4) {
        case 0:
            return quote(for: date, calendar: calendar)
        case 1:
            if let goal = pick(active, seed: day / 4) {
                return ReminderMessage(
                    title: "Осталось \(Format.weeks(stats.remainingWeeks))",
                    body: "«\(goal.title)» всё ещё в списке. Может, начать сегодня?"
                )
            }
            return quote(for: date, calendar: calendar)
        case 2:
            if let goal = pick(focused.isEmpty ? active : focused, seed: day / 4) {
                return ReminderMessage(
                    title: "Один шаг сегодня",
                    body: "Что можно сделать прямо сегодня для цели «\(goal.title)»?"
                )
            }
            return progress(stats: stats, goals: goals)
        default:
            return progress(stats: stats, goals: goals)
        }
    }

    static func weekly(date: Date, profile: Profile, goals: [Goal], calendar: Calendar = .memento) -> ReminderMessage {
        let stats = LifeStats(profile: profile, now: date, calendar: calendar)
        let active = goals.filter { !$0.isCompleted }.sorted { $0.createdAt < $1.createdAt }
        let focused = active.filter(\.isFocused)
        let week = calendar.dayNumber(for: date) / 7
        var body = "Осталось \(Format.weeks(stats.remainingWeeks)). "
        if let goal = pick(focused.isEmpty ? active : focused, seed: week) {
            body += "Может, эта неделя — для «\(goal.title)»?"
        } else {
            body += "Как ты проживёшь следующую?"
        }
        return ReminderMessage(title: "Прошла ещё одна неделя", body: body)
    }

    static func deadlineSoon(goal: Goal, deadline: Date) -> ReminderMessage {
        ReminderMessage(
            title: "До срока — месяц",
            body: "«\(goal.title)» — \(deadlineLabel(goal: goal, deadline: deadline)). Самое время начать."
        )
    }

    static func deadlineToday(goal: Goal) -> ReminderMessage {
        ReminderMessage(
            title: "Срок настал",
            body: "«\(goal.title)». Получилось? Если нет — не страшно: назначь новый срок и не откладывай."
        )
    }

    static func letterReady(_ letter: FutureLetter) -> ReminderMessage {
        ReminderMessage(
            title: "📬 Письмо из прошлого",
            body: "Письмо от \(Format.date(letter.createdAt)) можно открыть. Интересно, что изменилось с тех пор?"
        )
    }

    static func counterOverdue(_ counter: LifeCounter, at date: Date, profile: Profile, calendar: Calendar = .memento) -> ReminderMessage {
        var body = ""
        if let days = counter.daysSinceLast(now: date, calendar: calendar) {
            body = "Последний раз — \(Format.daysAgo(days))"
            if let usual = counter.usualIntervalDays {
                body += ", а обычно \(Format.everyInterval(days: usual))"
            }
            body += ". "
        }
        let stats = LifeStats(profile: profile, now: date, calendar: calendar)
        let left = counter.remaining(remainingYears: stats.remainingYears)
        body += left > 0 ? "Впереди ≈ \(Format.number(left)) \(counter.title). Может, пора?" : "Может, пора?"
        return ReminderMessage(title: "\(counter.emoji) Давно не было", body: body)
    }

    static func quote(for date: Date, calendar: Calendar = .memento) -> ReminderMessage {
        let quote = Quotes.ofTheDay(date, calendar: calendar)
        return ReminderMessage(title: "Memento mori", body: "«\(quote.text)» — \(quote.author)")
    }

    static func progress(stats: LifeStats, goals: [Goal]) -> ReminderMessage {
        let done = goals.filter(\.isCompleted).count
        let active = goals.count - done
        let body: String
        if done > 0 {
            body = "Выполнено: \(Format.goals(done)). Впереди: \(Format.goals(active)). Время идёт — и ты тоже."
        } else {
            body = "Время идёт. Что ты сделаешь с сегодняшним днём?"
        }
        return ReminderMessage(title: "Прожито \(Format.percent(stats.progress))", body: body)
    }

    private static func deadlineLabel(goal: Goal, deadline: Date) -> String {
        if let age = goal.targetAge { return Format.byAge(age) }
        return "до " + Format.date(deadline)
    }

    private static func pick(_ goals: [Goal], seed: Int) -> Goal? {
        guard !goals.isEmpty else { return nil }
        return goals[positiveModulo(seed, goals.count)]
    }
}

func positiveModulo(_ value: Int, _ modulus: Int) -> Int {
    guard modulus > 0 else { return 0 }
    let result = value % modulus
    return result >= 0 ? result : result + modulus
}

/// Расписание локальных уведомлений. iOS хранит не больше 64 запланированных,
/// поэтому планируем на месяц вперёд и обновляем при каждом открытии приложения.
enum ReminderPlanner {
    static let maxCount = 60
    static let dailyHorizonDays = 30
    static let weeklyHorizonWeeks = 10
    /// Письма могут ждать годами — им место в расписании оставляем всегда.
    static let maxLetterReminders = 10

    static func plan(data: AppData, now: Date = Date(), calendar: Calendar = .memento) -> [PlannedReminder] {
        guard let profile = data.profile else { return [] }
        // Письма и «давно не было» приходят, даже если ежедневные напоминания выключены.
        let letters = Array(letterReminders(data: data, profile: profile, now: now, calendar: calendar).prefix(maxLetterReminders))
        var regular = counterReminders(data: data, profile: profile, now: now, calendar: calendar)
        if data.reminders.isEnabled {
            regular += periodicReminders(data: data, profile: profile, now: now, calendar: calendar)
            if data.reminders.deadlineAlerts {
                regular += deadlineReminders(data: data, profile: profile, now: now, calendar: calendar)
            }
        }
        regular.sort { $0.date < $1.date }
        let result = letters + regular.prefix(max(0, maxCount - letters.count))
        return result.sorted { $0.date < $1.date }
    }

    private static func periodicReminders(data: AppData, profile: Profile, now: Date, calendar: Calendar) -> [PlannedReminder] {
        let settings = data.reminders
        var result: [PlannedReminder] = []
        switch settings.frequency {
        case .daily:
            let today = calendar.startOfDay(for: now)
            for offset in 0..<dailyHorizonDays {
                guard let day = calendar.date(byAdding: .day, value: offset, to: today),
                      let fire = calendar.date(bySettingHour: settings.hour, minute: settings.minute, second: 0, of: day),
                      fire > now
                else { continue }
                let message = ReminderComposer.daily(date: fire, profile: profile, goals: data.goals, calendar: calendar)
                result.append(PlannedReminder(
                    id: "memento.daily.\(calendar.dayNumber(for: fire))",
                    date: fire,
                    title: message.title,
                    body: message.body
                ))
            }
        case .weekly:
            var components = DateComponents()
            components.weekday = settings.weekday
            components.hour = settings.hour
            components.minute = settings.minute
            var cursor = now
            for _ in 0..<weeklyHorizonWeeks {
                guard let fire = calendar.nextDate(after: cursor, matching: components, matchingPolicy: .nextTime) else { break }
                let message = ReminderComposer.weekly(date: fire, profile: profile, goals: data.goals, calendar: calendar)
                result.append(PlannedReminder(
                    id: "memento.weekly.\(calendar.dayNumber(for: fire))",
                    date: fire,
                    title: message.title,
                    body: message.body
                ))
                cursor = fire.addingTimeInterval(60)
            }
        }
        return result
    }

    private static func deadlineReminders(data: AppData, profile: Profile, now: Date, calendar: Calendar) -> [PlannedReminder] {
        let settings = data.reminders
        var result: [PlannedReminder] = []
        for goal in data.activeGoals {
            guard let deadline = goal.deadline(birthDate: profile.birthDate, calendar: calendar) else { continue }
            let deadlineDay = calendar.startOfDay(for: deadline)
            if let monthBefore = calendar.date(byAdding: .day, value: -30, to: deadlineDay),
               let fire = calendar.date(bySettingHour: settings.hour, minute: settings.minute, second: 0, of: monthBefore),
               fire > now {
                let message = ReminderComposer.deadlineSoon(goal: goal, deadline: deadline)
                result.append(PlannedReminder(
                    id: "memento.deadline.soon.\(goal.id.uuidString)",
                    date: fire,
                    title: message.title,
                    body: message.body
                ))
            }
            if let fire = calendar.date(bySettingHour: settings.hour, minute: settings.minute, second: 0, of: deadlineDay),
               fire > now {
                let message = ReminderComposer.deadlineToday(goal: goal)
                result.append(PlannedReminder(
                    id: "memento.deadline.today.\(goal.id.uuidString)",
                    date: fire,
                    title: message.title,
                    body: message.body
                ))
            }
        }
        return result
    }

    /// День открытия письма — в обычное время напоминаний.
    private static func letterReminders(data: AppData, profile: Profile, now: Date, calendar: Calendar) -> [PlannedReminder] {
        let settings = data.reminders
        return data.letters.compactMap { letter -> PlannedReminder? in
            guard !letter.isOpened else { return nil }
            let day = letter.unlockDate(birthDate: profile.birthDate, calendar: calendar)
            guard let fire = calendar.date(bySettingHour: settings.hour, minute: settings.minute, second: 0, of: day),
                  fire > now
            else { return nil }
            let message = ReminderComposer.letterReady(letter)
            return PlannedReminder(id: letterReminderID(letter.id), date: fire, title: message.title, body: message.body)
        }
        .sorted { $0.date < $1.date }
    }

    /// По одному ближайшему напоминанию «давно не было» на счётчик.
    private static func counterReminders(data: AppData, profile: Profile, now: Date, calendar: Calendar) -> [PlannedReminder] {
        let settings = data.reminders
        return data.counters.compactMap { counter -> PlannedReminder? in
            guard let fire = counter.nextOverdueReminder(after: now, hour: settings.hour, minute: settings.minute, calendar: calendar) else {
                return nil
            }
            let message = ReminderComposer.counterOverdue(counter, at: fire, profile: profile, calendar: calendar)
            return PlannedReminder(id: counterReminderID(counter.id), date: fire, title: message.title, body: message.body)
        }
    }

    static func letterReminderID(_ id: UUID) -> String { "memento.letter.\(id.uuidString)" }
    static func counterReminderID(_ id: UUID) -> String { "memento.counter.\(id.uuidString)" }
}
