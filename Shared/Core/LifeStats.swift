import Foundation

/// Все расчёты «сколько прожито и сколько осталось».
/// Ожидаемый конец жизни — день рождения номер lifeExpectancy.
struct LifeStats {
    static let secondsPerDay: Double = 86_400
    static let secondsPerYear: Double = 365.2425 * 86_400
    static let daysPerLunarMonth: Double = 29.530588
    /// В «жизни в неделях» каждая строка — год жизни из 52 клеток.
    static let weeksPerRow = 52

    let birthDate: Date
    let lifeExpectancy: Int
    let now: Date
    let deathDate: Date
    let calendar: Calendar

    init(birthDate: Date, lifeExpectancy: Int, now: Date = Date(), calendar: Calendar = .memento) {
        let expectancy = max(1, lifeExpectancy)
        self.birthDate = birthDate
        self.lifeExpectancy = expectancy
        self.now = now
        self.calendar = calendar
        self.deathDate = calendar.date(byAdding: .year, value: expectancy, to: birthDate)
            ?? birthDate.addingTimeInterval(Double(expectancy) * Self.secondsPerYear)
    }

    init(profile: Profile, now: Date = Date(), calendar: Calendar = .memento) {
        self.init(birthDate: profile.birthDate, lifeExpectancy: profile.lifeExpectancy, now: now, calendar: calendar)
    }

    /// Те же параметры, но на другой момент времени.
    func at(_ date: Date) -> LifeStats {
        LifeStats(birthDate: birthDate, lifeExpectancy: lifeExpectancy, now: date, calendar: calendar)
    }

    // MARK: Прожито / осталось

    var totalSeconds: Double { deathDate.timeIntervalSince(birthDate) }
    var livedSeconds: Double { max(0, now.timeIntervalSince(birthDate)) }
    var remainingSeconds: Double { max(0, deathDate.timeIntervalSince(now)) }

    /// Доля прожитой жизни от 0 до 1.
    var progress: Double {
        guard totalSeconds > 0 else { return 1 }
        return min(1, max(0, livedSeconds / totalSeconds))
    }

    var isBeyondExpectancy: Bool { now >= deathDate }

    var ageYears: Int {
        max(0, calendar.dateComponents([.year], from: birthDate, to: now).year ?? 0)
    }

    var ageExact: Double { livedSeconds / Self.secondsPerYear }

    var remainingYears: Double { remainingSeconds / Self.secondsPerYear }
    var remainingMonths: Int {
        guard !isBeyondExpectancy else { return 0 }
        return max(0, calendar.dateComponents([.month], from: now, to: deathDate).month ?? 0)
    }
    var remainingDays: Int { Int(remainingSeconds / Self.secondsPerDay) }
    var remainingHours: Int { Int(remainingSeconds / 3_600) }

    // Недели — календарные, с понедельника по воскресенье: счётчик уменьшается в ночь на понедельник.

    /// Сколько недель впереди, считая текущую неделю прожитой.
    var remainingWeeks: Int {
        guard now < deathDate else { return 0 }
        return weeks(from: weekStart(max(now, birthDate)), to: weekStart(deathDate))
    }

    /// Сколько полных недель прожито с недели рождения.
    var livedWeeks: Int {
        guard now > birthDate else { return 0 }
        return weeks(from: weekStart(birthDate), to: weekStart(min(now, deathDate)))
    }

    var totalWeeks: Int {
        weeks(from: weekStart(birthDate), to: weekStart(deathDate))
    }

    /// Понедельник 00:00 недели, в которую попадает дата.
    func weekStart(_ date: Date) -> Date {
        calendar.dateInterval(of: .weekOfYear, for: date)?.start ?? calendar.startOfDay(for: date)
    }

    private func weeks(from start: Date, to end: Date) -> Int {
        max(0, (calendar.dateComponents([.day], from: start, to: end).day ?? 0) / 7)
    }

    // MARK: «Осталось примерно»

    var summersLeft: Int { occurrencesLeft(month: 7, day: 1) }
    var newYearsLeft: Int { occurrencesLeft(month: 1, day: 1) }
    var birthdaysLeft: Int { max(0, lifeExpectancy - ageYears) }
    var fullMoonsLeft: Int { Int(Double(remainingDays) / Self.daysPerLunarMonth) }
    /// Ударов сердца при среднем пульсе 70 в минуту.
    var heartbeatsLeft: Double { remainingSeconds / 60 * 70 }

    /// Сколько раз дата «день.месяц» встретится в промежутке (сейчас; конец].
    func occurrencesLeft(month: Int, day: Int) -> Int {
        guard now < deathDate else { return 0 }
        let firstYear = calendar.component(.year, from: now)
        let lastYear = calendar.component(.year, from: deathDate)
        guard firstYear <= lastYear else { return 0 }
        var count = 0
        for year in firstYear...lastYear {
            guard let date = calendar.date(from: DateComponents(year: year, month: month, day: day)) else { continue }
            if date > now && date <= deathDate {
                count += 1
            }
        }
        return count
    }

    // MARK: Сетка «жизнь в неделях»

    var gridRows: Int { lifeExpectancy }
    var gridCellCount: Int { gridRows * Self.weeksPerRow }

    /// Начало строки возраста row: понедельник недели, на которую выпал день рождения.
    func rowStart(_ row: Int) -> Date? {
        calendar.date(byAdding: .year, value: row, to: birthDate).map(weekStart)
    }

    /// Клетка сетки, на которую приходится дата (nil — до рождения или за пределами сетки).
    /// Клетка — календарная неделя с понедельника; строка — год жизни.
    func gridIndex(for date: Date) -> Int? {
        guard date >= birthDate else { return nil }
        var row = max(0, calendar.dateComponents([.year], from: birthDate, to: date).year ?? 0)
        // Неделя следующего дня рождения уже относится к следующей строке.
        if let next = rowStart(row + 1), date >= next {
            row += 1
        }
        guard row < gridRows, let start = rowStart(row) else { return nil }
        let days = calendar.dateComponents([.day], from: start, to: date).day ?? 0
        let week = min(Self.weeksPerRow - 1, max(0, days / 7))
        return row * Self.weeksPerRow + week
    }

    var currentGridIndex: Int? { gridIndex(for: now) }

    /// Сколько клеток уже прожито (текущая неделя не считается прожитой).
    var livedGridCells: Int {
        if let current = currentGridIndex { return current }
        return now < birthDate ? 0 : gridCellCount
    }

    /// Диапазон дат клетки: с понедельника по воскресенье.
    /// Если в году жизни выпало 53 недели, последняя клетка строки охватывает две.
    func dateRange(forGridIndex index: Int) -> ClosedRange<Date>? {
        guard index >= 0, index < gridCellCount else { return nil }
        let row = index / Self.weeksPerRow
        let column = index % Self.weeksPerRow
        guard let rowBegin = rowStart(row),
              let start = calendar.date(byAdding: .day, value: column * 7, to: rowBegin)
        else { return nil }
        let end: Date?
        if column == Self.weeksPerRow - 1 {
            end = rowStart(row + 1).flatMap { calendar.date(byAdding: .second, value: -1, to: $0) }
        } else {
            end = calendar.date(byAdding: .day, value: 7, to: start)
                .flatMap { calendar.date(byAdding: .second, value: -1, to: $0) }
        }
        guard let end, end >= start else { return nil }
        return start...end
    }

    /// Возраст (полных лет) в начале клетки.
    func age(forGridIndex index: Int) -> Int {
        index / Self.weeksPerRow
    }

    /// Возраст в момент date (полных лет).
    func age(at date: Date) -> Int {
        max(0, calendar.dateComponents([.year], from: birthDate, to: date).year ?? 0)
    }
}
