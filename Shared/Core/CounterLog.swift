import Foundation

// «Последний раз»: что показывают отметки «Было» у счётчика.

extension LifeCounter {
    /// Напоминаем, когда перерыв в 1,25 раза дольше обычного.
    static let overdueFactor = 1.25
    /// Чаще раза в неделю — это уже привычка, а не редкая встреча: напоминания ни к чему.
    static let maxPerYearForReminders = 52.0

    var lastDate: Date? { log.last }

    /// Обычный промежуток между разами при заданной частоте.
    var usualInterval: TimeInterval? {
        perYear > 0 ? LifeStats.secondsPerYear / perYear : nil
    }

    var usualIntervalDays: Int? {
        usualInterval.map { max(1, Int(($0 / LifeStats.secondsPerDay).rounded())) }
    }

    /// Можно ли напоминать о долгом перерыве при такой частоте.
    var supportsOverdueReminders: Bool {
        perYear > 0 && perYear <= Self.maxPerYearForReminders
    }

    /// Сколько дней назад было в последний раз (0 — сегодня).
    func daysSinceLast(now: Date, calendar: Calendar = .memento) -> Int? {
        guard let last = lastDate else { return nil }
        let days = calendar.dateComponents([.day], from: calendar.startOfDay(for: last), to: calendar.startOfDay(for: now)).day ?? 0
        return max(0, days)
    }

    func isLogged(on day: Date, calendar: Calendar = .memento) -> Bool {
        log.contains { calendar.isDate($0, inSameDayAs: day) }
    }

    /// Перерыв заметно дольше обычного.
    func isOverdue(now: Date) -> Bool {
        guard let last = lastDate, let interval = usualInterval else { return false }
        return now.timeIntervalSince(last) > interval * Self.overdueFactor
    }

    /// Сколько обычных промежутков прошло с последнего раза: чем больше, тем «пора».
    func urgency(now: Date) -> Double {
        guard let last = lastDate, let interval = usualInterval, interval > 0 else { return 0 }
        return max(0, now.timeIntervalSince(last)) / interval
    }

    /// Сколько раз было в календарном году даты date.
    func count(inYearOf date: Date, calendar: Calendar = .memento) -> Int {
        let year = calendar.component(.year, from: date)
        return log.filter { calendar.component(.year, from: $0) == year }.count
    }

    /// Сколько раз в год выходит на самом деле — по отметкам за последний год.
    /// nil — отмечать начали недавно, судить рано.
    func actualPerYear(now: Date) -> Double? {
        guard log.count >= 2, let first = log.first else { return nil }
        let yearAgo = now.addingTimeInterval(-LifeStats.secondsPerYear)
        let start = max(min(createdAt, first), yearAgo)
        let span = now.timeIntervalSince(start)
        guard span >= 90 * LifeStats.secondsPerDay else { return nil }
        // Отметка в самом начале окна — точка отсчёта, а не результат периода.
        let count = log.filter { $0 > start && $0 <= now }.count
        return Double(count) / (span / LifeStats.secondsPerYear)
    }

    /// Когда напомнить о долгом перерыве: первый раз — когда он в 1,25 раза дольше обычного,
    /// дальше — через каждый обычный промежуток. Время дня — как у остальных напоминаний.
    func nextOverdueReminder(after now: Date, hour: Int, minute: Int, calendar: Calendar = .memento) -> Date? {
        guard remindsWhenOverdue, supportsOverdueReminders, let last = lastDate, let interval = usualInterval else { return nil }
        let elapsed = now.timeIntervalSince(last) / interval
        var step = max(0, Int((elapsed - Self.overdueFactor).rounded(.down)))
        for _ in 0..<3 {
            let due = last.addingTimeInterval(interval * (Self.overdueFactor + Double(step)))
            if let fire = calendar.date(bySettingHour: hour, minute: minute, second: 0, of: calendar.startOfDay(for: due)),
               fire > now, fire > last {
                return fire
            }
            step += 1
        }
        return nil
    }
}

extension Format {
    /// «раз в неделю», «раз в 10 дней», «раз в 2 месяца», «раз в полгода», «раз в год».
    static func everyInterval(days: Int) -> String {
        if days <= 1 { return "каждый день" }
        if days % 7 == 0 && days <= 21 {
            let weeks = days / 7
            return weeks == 1 ? "раз в неделю" : "раз в " + count(weeks, "неделю", "недели", "недель")
        }
        if (28...31).contains(days) { return "раз в месяц" }
        if days < 45 { return "раз в " + count(days, "день", "дня", "дней") }
        let months = Int((Double(days) / 30.44).rounded())
        if months < 12 {
            if months == 6 { return "раз в полгода" }
            return months <= 1 ? "раз в месяц" : "раз в " + count(months, "месяц", "месяца", "месяцев")
        }
        let years = Int((Double(days) / 365.2425).rounded())
        return years <= 1 ? "раз в год" : "раз в " + count(years, "год", "года", "лет")
    }

    /// «сегодня», «вчера», «12 дней назад», «больше года назад».
    static func daysAgo(_ days: Int) -> String {
        switch days {
        case ..<1: return "сегодня"
        case 1: return "вчера"
        case ..<365: return count(days, "день", "дня", "дней") + " назад"
        default:
            let years = days / 365
            return years == 1 ? "больше года назад" : "больше \(genitive(years, "года", "лет")) назад"
        }
    }

    /// Частота в год: «4 раза», «2,5 раза», «ни разу».
    static func perYear(_ value: Double) -> String {
        if value < 0.05 { return "ни разу" }
        if value.rounded() == value || value >= 10 {
            return times(Int(value.rounded()))
        }
        return decimal(value) + " раза"
    }
}
