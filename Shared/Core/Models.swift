import Foundation

// Модель данных приложения. Всё хранится в одном JSON-файле (см. DataStore),
// поэтому все декодеры терпимы к отсутствующим полям: старые резервные копии
// и данные будущих версий открываются без ошибок.

// MARK: - Категории

enum GoalCategory: String, Codable, CaseIterable, Identifiable, Hashable {
    case travel
    case experience
    case movies
    case books
    case skills
    case body
    case people
    case create
    case other

    var id: String { rawValue }

    var title: String {
        switch self {
        case .travel: "Путешествия"
        case .experience: "Впечатления"
        case .movies: "Фильмы"
        case .books: "Книги"
        case .skills: "Навыки"
        case .body: "Тело"
        case .people: "Люди"
        case .create: "Создать"
        case .other: "Другое"
        }
    }

    /// Имя SF Symbol для категории.
    var symbol: String {
        switch self {
        case .travel: "airplane"
        case .experience: "sparkles"
        case .movies: "film"
        case .books: "book"
        case .skills: "graduationcap"
        case .body: "figure.run"
        case .people: "heart"
        case .create: "hammer"
        case .other: "circle.dashed"
        }
    }

    init(from decoder: Decoder) throws {
        let raw = try decoder.singleValueContainer().decode(String.self)
        self = GoalCategory(rawValue: raw) ?? .other
    }
}

// MARK: - Цель

struct Goal: Identifiable, Codable, Hashable {
    var id: UUID
    var title: String
    var category: GoalCategory
    /// «Зачем мне это» — мотивация или детали.
    var note: String
    /// Срок в виде возраста («к 35 годам»). Взаимоисключающе с targetDate.
    var targetAge: Int?
    /// Срок в виде конкретной даты.
    var targetDate: Date?
    var isFocused: Bool
    var createdAt: Date
    var completedAt: Date?
    /// Заметка-воспоминание после выполнения.
    var reflection: String
    /// Имя файла фото в папке Photos (см. DataStore.photosDirectory).
    var photoFileName: String?
    /// ISO-код страны для целей-путешествий.
    var countryCode: String?

    init(
        id: UUID = UUID(),
        title: String,
        category: GoalCategory = .other,
        note: String = "",
        targetAge: Int? = nil,
        targetDate: Date? = nil,
        isFocused: Bool = false,
        createdAt: Date = Date(),
        completedAt: Date? = nil,
        reflection: String = "",
        photoFileName: String? = nil,
        countryCode: String? = nil
    ) {
        self.id = id
        self.title = title
        self.category = category
        self.note = note
        self.targetAge = targetAge
        self.targetDate = targetDate
        self.isFocused = isFocused
        self.createdAt = createdAt
        self.completedAt = completedAt
        self.reflection = reflection
        self.photoFileName = photoFileName
        self.countryCode = countryCode
    }

    var isCompleted: Bool { completedAt != nil }

    var hasDeadline: Bool { targetAge != nil || targetDate != nil }

    /// Дата дедлайна с учётом даты рождения (для сроков «к N годам»).
    func deadline(birthDate: Date, calendar: Calendar = .memento) -> Date? {
        if let targetDate { return targetDate }
        if let targetAge { return calendar.date(byAdding: .year, value: targetAge, to: birthDate) }
        return nil
    }

    enum CodingKeys: String, CodingKey {
        case id, title, category, note, targetAge, targetDate, isFocused
        case createdAt, completedAt, reflection, photoFileName, countryCode
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decodeIfPresent(UUID.self, forKey: .id) ?? UUID()
        title = try c.decodeIfPresent(String.self, forKey: .title) ?? ""
        category = try c.decodeIfPresent(GoalCategory.self, forKey: .category) ?? .other
        note = try c.decodeIfPresent(String.self, forKey: .note) ?? ""
        targetAge = try c.decodeIfPresent(Int.self, forKey: .targetAge)
        targetDate = try c.decodeIfPresent(Date.self, forKey: .targetDate)
        isFocused = try c.decodeIfPresent(Bool.self, forKey: .isFocused) ?? false
        createdAt = try c.decodeIfPresent(Date.self, forKey: .createdAt) ?? Date()
        completedAt = try c.decodeIfPresent(Date.self, forKey: .completedAt)
        reflection = try c.decodeIfPresent(String.self, forKey: .reflection) ?? ""
        photoFileName = try c.decodeIfPresent(String.self, forKey: .photoFileName)
        countryCode = try c.decodeIfPresent(String.self, forKey: .countryCode)
    }
}

// MARK: - Профиль

/// Откуда берётся ожидаемая продолжительность жизни.
enum ExpectancySource: String, Codable, CaseIterable, Identifiable {
    /// Средняя по стране (данные Всемирного банка).
    case country
    /// Своё число.
    case custom

    var id: String { rawValue }

    var title: String {
        switch self {
        case .country: "Средняя по стране"
        case .custom: "Своё число"
        }
    }

    init(from decoder: Decoder) throws {
        let raw = try decoder.singleValueContainer().decode(String.self)
        self = ExpectancySource(rawValue: raw) ?? .custom
    }
}

struct Profile: Codable, Hashable {
    var birthDate: Date
    /// Ожидаемая продолжительность жизни в годах — по ней считается всё остальное.
    var lifeExpectancy: Int
    var expectancySource: ExpectancySource
    /// Страна для режима «средняя по стране» (ISO-код или 001 — весь мир).
    var expectancyCountry: String?
    /// Когда человек начал пользоваться приложением (нужно для расчёта темпа).
    var createdAt: Date

    static let defaultLifeExpectancy = 80
    static let lifeExpectancyRange = 30...120

    init(
        birthDate: Date,
        lifeExpectancy: Int = Profile.defaultLifeExpectancy,
        expectancySource: ExpectancySource = .custom,
        expectancyCountry: String? = nil,
        createdAt: Date = Date()
    ) {
        self.birthDate = birthDate
        self.lifeExpectancy = lifeExpectancy
        self.expectancySource = expectancySource
        self.expectancyCountry = expectancyCountry
        self.createdAt = createdAt
        applyCountryAverage()
    }

    /// Средняя продолжительность жизни в стране, округлённая до лет.
    static func averageLifeExpectancy(forCountry code: String) -> Int? {
        LifeExpectancyData.value(for: code).map { Int($0.rounded()) }
    }

    /// В режиме «средняя по стране» подставляет актуальное значение из данных.
    mutating func applyCountryAverage() {
        guard expectancySource == .country,
              let code = expectancyCountry,
              let years = Profile.averageLifeExpectancy(forCountry: code)
        else { return }
        lifeExpectancy = years
    }

    enum CodingKeys: String, CodingKey {
        case birthDate, lifeExpectancy, expectancySource, expectancyCountry, createdAt
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        birthDate = try c.decode(Date.self, forKey: .birthDate)
        lifeExpectancy = try c.decodeIfPresent(Int.self, forKey: .lifeExpectancy) ?? Profile.defaultLifeExpectancy
        expectancySource = try c.decodeIfPresent(ExpectancySource.self, forKey: .expectancySource) ?? .custom
        expectancyCountry = try c.decodeIfPresent(String.self, forKey: .expectancyCountry)
        createdAt = try c.decodeIfPresent(Date.self, forKey: .createdAt) ?? Date()
        applyCountryAverage()
    }
}

/// Выбор ожидаемой продолжительности жизни на экранах знакомства и настроек.
struct ExpectancyChoice: Equatable {
    var source: ExpectancySource
    /// Страна для режима «средняя по стране» (ISO-код или 001 — весь мир).
    var country: String
    /// Своё число для режима «своё число».
    var customYears: Int

    /// Сколько лет в итоге закладываем на жизнь.
    var years: Int {
        if source == .country, let average = Profile.averageLifeExpectancy(forCountry: country) {
            return average
        }
        return customYears
    }

    /// Средняя по стране, где живёт человек (по региону устройства), или по миру.
    static func suggested(regionCode: String? = Locale.current.region?.identifier) -> ExpectancyChoice {
        let country = regionCode.flatMap { LifeExpectancyData.hasData(for: $0) ? $0 : nil } ?? LifeExpectancyData.worldCode
        let average = Profile.averageLifeExpectancy(forCountry: country) ?? Profile.defaultLifeExpectancy
        return ExpectancyChoice(source: .country, country: country, customYears: average)
    }

    /// Выбрать страну: своё число тоже подстраиваем, чтобы при переключении начинать от средней.
    mutating func select(country code: String) {
        country = code
        if let average = Profile.averageLifeExpectancy(forCountry: code) {
            customYears = average
        }
    }

    init(source: ExpectancySource, country: String, customYears: Int) {
        self.source = source
        self.country = country
        self.customYears = customYears
    }

    init(profile: Profile) {
        let fallback = ExpectancyChoice.suggested()
        source = profile.expectancySource
        country = profile.expectancyCountry.flatMap { LifeExpectancyData.hasData(for: $0) ? $0 : nil } ?? fallback.country
        customYears = profile.lifeExpectancy
    }
}

// MARK: - Счётчик «Осталось»

/// Пользовательский счётчик в духе «The Tail End»: сколько раз ещё случится
/// что-то, что происходит N раз в год (встречи с родителями, отпуска, книги).
struct LifeCounter: Identifiable, Codable, Hashable {
    var id: UUID
    var title: String
    var emoji: String
    var perYear: Double
    /// Сколько лет это ещё продлится. nil — до конца жизни.
    var years: Int?

    init(id: UUID = UUID(), title: String, emoji: String = "✦", perYear: Double, years: Int? = nil) {
        self.id = id
        self.title = title
        self.emoji = emoji
        self.perYear = perYear
        self.years = years
    }

    /// Сколько раз осталось, если жить осталось remainingYears лет.
    func remaining(remainingYears: Double) -> Int {
        let span = min(Double(years ?? Int.max), max(0, remainingYears))
        return Int((perYear * span).rounded(.down))
    }

    enum CodingKeys: String, CodingKey {
        case id, title, emoji, perYear, years
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decodeIfPresent(UUID.self, forKey: .id) ?? UUID()
        title = try c.decodeIfPresent(String.self, forKey: .title) ?? ""
        emoji = try c.decodeIfPresent(String.self, forKey: .emoji) ?? "✦"
        perYear = try c.decodeIfPresent(Double.self, forKey: .perYear) ?? 1
        years = try c.decodeIfPresent(Int.self, forKey: .years)
    }
}

// MARK: - Напоминания

struct ReminderSettings: Codable, Hashable {
    enum Frequency: String, Codable, CaseIterable, Identifiable {
        case daily
        case weekly

        var id: String { rawValue }

        var title: String {
            switch self {
            case .daily: "Каждый день"
            case .weekly: "Раз в неделю"
            }
        }

        init(from decoder: Decoder) throws {
            let raw = try decoder.singleValueContainer().decode(String.self)
            self = Frequency(rawValue: raw) ?? .daily
        }
    }

    var isEnabled: Bool = false
    var hour: Int = 9
    var minute: Int = 0
    var frequency: Frequency = .daily
    /// День недели для еженедельных напоминаний: 1 — воскресенье, 2 — понедельник…
    var weekday: Int = 2
    var deadlineAlerts: Bool = true

    init() {}

    enum CodingKeys: String, CodingKey {
        case isEnabled, hour, minute, frequency, weekday, deadlineAlerts
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        isEnabled = try c.decodeIfPresent(Bool.self, forKey: .isEnabled) ?? false
        hour = try c.decodeIfPresent(Int.self, forKey: .hour) ?? 9
        minute = try c.decodeIfPresent(Int.self, forKey: .minute) ?? 0
        frequency = try c.decodeIfPresent(Frequency.self, forKey: .frequency) ?? .daily
        weekday = try c.decodeIfPresent(Int.self, forKey: .weekday) ?? 2
        deadlineAlerts = try c.decodeIfPresent(Bool.self, forKey: .deadlineAlerts) ?? true
    }
}

// MARK: - Все данные

struct AppData: Codable {
    static let currentVersion = 1
    static let maxFocusedGoals = 3

    var version: Int = AppData.currentVersion
    var profile: Profile?
    var goals: [Goal] = []
    var counters: [LifeCounter] = []
    var visitedCountries: [String] = []
    var reminders = ReminderSettings()

    init() {}

    enum CodingKeys: String, CodingKey {
        case version, profile, goals, counters, visitedCountries, reminders
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        version = try c.decodeIfPresent(Int.self, forKey: .version) ?? AppData.currentVersion
        profile = try c.decodeIfPresent(Profile.self, forKey: .profile)
        goals = try c.decodeIfPresent([Goal].self, forKey: .goals) ?? []
        counters = try c.decodeIfPresent([LifeCounter].self, forKey: .counters) ?? []
        visitedCountries = try c.decodeIfPresent([String].self, forKey: .visitedCountries) ?? []
        reminders = try c.decodeIfPresent(ReminderSettings.self, forKey: .reminders) ?? ReminderSettings()
    }
}

// MARK: - Операции над данными
// Логика живёт здесь, а не во вьюхах, чтобы её одинаково использовали
// приложение и интерактивный виджет.

extension AppData {
    var activeGoals: [Goal] { goals.filter { !$0.isCompleted } }
    var completedGoals: [Goal] { goals.filter(\.isCompleted) }
    var focusedGoals: [Goal] { activeGoals.filter(\.isFocused) }

    func goal(id: UUID) -> Goal? {
        goals.first { $0.id == id }
    }

    /// Можно ли поставить в фокус ещё одну цель (не считая goalID).
    func canFocus(excluding goalID: UUID? = nil) -> Bool {
        focusedGoals.filter { $0.id != goalID }.count < AppData.maxFocusedGoals
    }

    mutating func upsert(_ goal: Goal) {
        var goal = goal
        goal.title = goal.title.trimmingCharacters(in: .whitespacesAndNewlines)
        if goal.isFocused && (goal.isCompleted || !canFocus(excluding: goal.id)) {
            goal.isFocused = false
        }
        if let index = goals.firstIndex(where: { $0.id == goal.id }) {
            goals[index] = goal
        } else {
            goals.append(goal)
        }
    }

    @discardableResult
    mutating func removeGoal(id: UUID) -> Goal? {
        guard let index = goals.firstIndex(where: { $0.id == id }) else { return nil }
        return goals.remove(at: index)
    }

    mutating func completeGoal(id: UUID, at date: Date = Date(), reflection: String? = nil, photoFileName: String? = nil) {
        guard let index = goals.firstIndex(where: { $0.id == id }) else { return }
        goals[index].completedAt = date
        goals[index].isFocused = false
        if let reflection { goals[index].reflection = reflection }
        if let photoFileName { goals[index].photoFileName = photoFileName }
        if let code = goals[index].countryCode {
            markVisited(code, true)
        }
    }

    mutating func reopenGoal(id: UUID) {
        guard let index = goals.firstIndex(where: { $0.id == id }) else { return }
        goals[index].completedAt = nil
    }

    @discardableResult
    mutating func toggleFocus(id: UUID) -> Bool {
        guard let index = goals.firstIndex(where: { $0.id == id }) else { return false }
        if goals[index].isFocused {
            goals[index].isFocused = false
            return true
        }
        guard !goals[index].isCompleted, canFocus(excluding: id) else { return false }
        goals[index].isFocused = true
        return true
    }

    // MARK: Страны

    func isVisited(_ code: String) -> Bool {
        visitedCountries.contains(code)
    }

    func wishGoal(forCountry code: String) -> Goal? {
        activeGoals.first { $0.countryCode == code }
    }

    mutating func markVisited(_ code: String, _ visited: Bool) {
        if visited {
            if !visitedCountries.contains(code) {
                visitedCountries.append(code)
                visitedCountries.sort()
            }
        } else {
            visitedCountries.removeAll { $0 == code }
        }
    }
}

// MARK: - Календарь

extension Calendar {
    /// Григорианский календарь с неделей, начинающейся с понедельника.
    static var memento: Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.locale = Locale(identifier: "ru_RU")
        calendar.firstWeekday = 2
        calendar.timeZone = .current
        return calendar
    }

    /// Порядковый номер дня (стабилен в пределах одних суток по местному времени).
    func dayNumber(for date: Date) -> Int {
        let parts = dateComponents([.year, .month, .day], from: date)
        var utc = Calendar(identifier: .gregorian)
        utc.timeZone = TimeZone(identifier: "UTC") ?? .current
        let normalized = utc.date(from: parts) ?? date
        return Int((normalized.timeIntervalSince1970 / 86_400).rounded(.down))
    }
}
