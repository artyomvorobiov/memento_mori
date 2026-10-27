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

    static func plan(data: AppData, now: Date = Date(), calendar: Calendar = .memento) -> [PlannedReminder] {
        guard let profile = data.profile, data.reminders.isEnabled else { return [] }
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

        if settings.deadlineAlerts {
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
        }

        result.sort { $0.date < $1.date }
        return Array(result.prefix(maxCount))
    }
}
