import Foundation

/// Русские формы множественного числа: 1 неделя, 2 недели, 5 недель.
enum RussianPlural {
    static func form(_ number: Int, _ one: String, _ few: String, _ many: String) -> String {
        let n = abs(number)
        let mod10 = n % 10
        let mod100 = n % 100
        if mod10 == 1 && mod100 != 11 { return one }
        if (2...4).contains(mod10) && !(12...14).contains(mod100) { return few }
        return many
    }

    /// Для дробных чисел в русском используется родительный падеж единственного числа:
    /// «1,5 года», «2,5 недели».
    static func form(_ number: Double, _ one: String, _ few: String, _ many: String) -> String {
        if number.rounded() == number, abs(number) < Double(Int.max) {
            return form(Int(number), one, few, many)
        }
        return few
    }
}

/// Форматирование чисел и дат по-русски, независимо от языка устройства.
enum Format {
    static let locale = Locale(identifier: "ru_RU")

    static func number(_ value: Int) -> String {
        value.formatted(.number.locale(locale))
    }

    static func decimal(_ value: Double, digits: Int = 1) -> String {
        value.formatted(.number.precision(.fractionLength(digits)).locale(locale))
    }

    /// 0.382 → «38,2%»
    static func percent(_ fraction: Double, digits: Int = 1) -> String {
        decimal(fraction * 100, digits: digits) + "%"
    }

    static func count(_ value: Int, _ one: String, _ few: String, _ many: String) -> String {
        "\(number(value)) \(RussianPlural.form(value, one, few, many))"
    }

    static func weeks(_ value: Int) -> String { count(value, "неделя", "недели", "недель") }
    static func days(_ value: Int) -> String { count(value, "день", "дня", "дней") }
    static func years(_ value: Int) -> String { count(value, "год", "года", "лет") }
    static func months(_ value: Int) -> String { count(value, "месяц", "месяца", "месяцев") }
    static func goals(_ value: Int) -> String { count(value, "цель", "цели", "целей") }
    static func times(_ value: Int) -> String { count(value, "раз", "раза", "раз") }

    /// Родительный падеж после «из»: «из 82 лет», «из 81 года», «из 4 278 недель».
    static func genitive(_ value: Int, _ singular: String, _ plural: String) -> String {
        let n = abs(value)
        let word = n % 10 == 1 && n % 100 != 11 ? singular : plural
        return "\(number(value)) \(word)"
    }

    /// Крупные числа покороче: «1,6 млрд», «418,5 млн».
    static func compact(_ value: Double) -> String {
        if value >= 1_000_000_000 {
            return decimal(value / 1_000_000_000) + " млрд"
        }
        if value >= 1_000_000 {
            return decimal(value / 1_000_000) + " млн"
        }
        return number(Int(value))
    }

    /// «к 35 годам», «к 21 году».
    static func byAge(_ age: Int) -> String {
        "к \(age) " + RussianPlural.form(age, "году", "годам", "годам")
    }

    /// «в 34 года», «в 21 год», «в 25 лет».
    static func atAge(_ age: Int) -> String {
        "в \(age) " + RussianPlural.form(age, "год", "года", "лет")
    }

    /// «12 марта 2026»
    static func date(_ date: Date) -> String {
        date.formatted(.dateTime.day().month(.wide).year().locale(locale))
    }

    /// «12 мар. 2026»
    static func shortDate(_ date: Date) -> String {
        date.formatted(.dateTime.day().month(.abbreviated).year().locale(locale))
    }

    /// «март 2026»
    static func monthYear(_ date: Date) -> String {
        date.formatted(.dateTime.month(.wide).year().locale(locale))
    }

    /// Время до события: «через 3 дня», «через 2 недели», «через 5 месяцев», «через 3 года».
    static func relative(from now: Date, to date: Date, calendar: Calendar = .memento) -> String {
        let seconds = date.timeIntervalSince(now)
        if seconds < 0 { return "срок прошёл" }
        let days = Int(seconds / LifeStats.secondsPerDay)
        if days == 0 { return "сегодня" }
        if days < 14 {
            return "через " + count(days, "день", "дня", "дней")
        }
        if days < 70 {
            return "через " + count(days / 7, "неделю", "недели", "недель")
        }
        let months = calendar.dateComponents([.month], from: now, to: date).month ?? days / 30
        if months < 24 {
            return "через " + count(max(1, months), "месяц", "месяца", "месяцев")
        }
        return "через " + years(Int(seconds / LifeStats.secondsPerYear))
    }
}

// MARK: - Подписи к срокам целей

enum DeadlineText {
    /// Короткое описание срока: «к 35 годам · через 3 года».
    static func summary(for goal: Goal, birthDate: Date?, now: Date = Date(), calendar: Calendar = .memento) -> String? {
        guard goal.hasDeadline else { return nil }
        var parts: [String] = []
        if let age = goal.targetAge {
            parts.append(Format.byAge(age))
        } else if let date = goal.targetDate {
            parts.append("до " + Format.shortDate(date))
        }
        if !goal.isCompleted, let birthDate, let deadline = goal.deadline(birthDate: birthDate, calendar: calendar) {
            parts.append(Format.relative(from: now, to: deadline, calendar: calendar))
        }
        return parts.joined(separator: " · ")
    }

    static func isOverdue(_ goal: Goal, birthDate: Date?, now: Date = Date(), calendar: Calendar = .memento) -> Bool {
        guard !goal.isCompleted, let birthDate, let deadline = goal.deadline(birthDate: birthDate, calendar: calendar) else {
            return false
        }
        return deadline < now
    }
}

extension String {
    /// «к 35 годам» → «К 35 годам».
    var capitalizedFirst: String {
        guard let first else { return self }
        return first.uppercased() + dropFirst()
    }
}
