import Foundation

/// Письмо себе в будущее. Запечатано до дня открытия: к возрасту («к 40 годам») или к дате.
struct FutureLetter: Identifiable, Codable, Hashable {
    var id: UUID
    var text: String
    var createdAt: Date
    /// Открыть к возрасту. Взаимоисключающе с openDate.
    var openAge: Int?
    /// Открыть в конкретный день.
    var openDate: Date?
    /// Когда письмо прочитали. nil — ещё запечатано или ждёт.
    var openedAt: Date?

    init(
        id: UUID = UUID(),
        text: String,
        createdAt: Date = Date(),
        openAge: Int? = nil,
        openDate: Date? = nil,
        openedAt: Date? = nil
    ) {
        self.id = id
        self.text = text
        self.createdAt = createdAt
        self.openAge = openAge
        self.openDate = openDate
        self.openedAt = openedAt
    }

    var isOpened: Bool { openedAt != nil }

    /// С какого момента письмо можно открыть — с начала нужного дня.
    func unlockDate(birthDate: Date, calendar: Calendar = .memento) -> Date {
        if let openDate {
            return calendar.startOfDay(for: openDate)
        }
        if let openAge, let date = calendar.date(byAdding: .year, value: openAge, to: birthDate) {
            return calendar.startOfDay(for: date)
        }
        return calendar.startOfDay(for: calendar.date(byAdding: .year, value: 1, to: createdAt) ?? createdAt)
    }

    /// Срок настал (неважно, открыто ли уже).
    func isUnlocked(now: Date, birthDate: Date, calendar: Calendar = .memento) -> Bool {
        now >= unlockDate(birthDate: birthDate, calendar: calendar)
    }

    /// Срок настал, а письмо ещё не прочитано.
    func isWaiting(now: Date, birthDate: Date, calendar: Calendar = .memento) -> Bool {
        !isOpened && isUnlocked(now: now, birthDate: birthDate, calendar: calendar)
    }

    /// «к 40 годам» или «14 марта 2027».
    func openLabel(birthDate: Date, calendar: Calendar = .memento) -> String {
        if openDate == nil, let openAge {
            return Format.byAge(openAge)
        }
        return Format.date(unlockDate(birthDate: birthDate, calendar: calendar))
    }

    /// Сколько лет было, когда писали письмо.
    func ageWhenWritten(birthDate: Date, calendar: Calendar = .memento) -> Int {
        max(0, calendar.dateComponents([.year], from: birthDate, to: createdAt).year ?? 0)
    }

    /// Первая строка — чтобы узнать письмо в списке после того, как его прочитали.
    var preview: String {
        let line = text.split(whereSeparator: \.isNewline).first.map(String.init) ?? ""
        return line.count > 80 ? String(line.prefix(80)) + "…" : line
    }

    enum CodingKeys: String, CodingKey {
        case id, text, createdAt, openAge, openDate, openedAt
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decodeIfPresent(UUID.self, forKey: .id) ?? UUID()
        text = try c.decodeIfPresent(String.self, forKey: .text) ?? ""
        createdAt = try c.decodeIfPresent(Date.self, forKey: .createdAt) ?? Date()
        openAge = try c.decodeIfPresent(Int.self, forKey: .openAge)
        openDate = try c.decodeIfPresent(Date.self, forKey: .openDate)
        openedAt = try c.decodeIfPresent(Date.self, forKey: .openedAt)
    }
}

extension AppData {
    /// Письма, которые уже можно открыть, — старые первыми.
    func waitingLetters(now: Date, calendar: Calendar = .memento) -> [FutureLetter] {
        guard let birthDate = profile?.birthDate else { return [] }
        return letters
            .filter { $0.isWaiting(now: now, birthDate: birthDate, calendar: calendar) }
            .sorted { $0.unlockDate(birthDate: birthDate, calendar: calendar) < $1.unlockDate(birthDate: birthDate, calendar: calendar) }
    }

    /// Запечатанные письма — ближайшие первыми.
    func sealedLetters(now: Date, calendar: Calendar = .memento) -> [FutureLetter] {
        guard let birthDate = profile?.birthDate else { return [] }
        return letters
            .filter { !$0.isOpened && !$0.isUnlocked(now: now, birthDate: birthDate, calendar: calendar) }
            .sorted { $0.unlockDate(birthDate: birthDate, calendar: calendar) < $1.unlockDate(birthDate: birthDate, calendar: calendar) }
    }

    /// Прочитанные письма — последние прочитанные первыми.
    var openedLetters: [FutureLetter] {
        letters.filter(\.isOpened).sorted { ($0.openedAt ?? .distantPast) > ($1.openedAt ?? .distantPast) }
    }
}

/// Вопросы, с которых легко начать письмо.
enum LetterPrompts {
    static let all = [
        "Что тебя сейчас волнует больше всего?",
        "Чем ты гордишься в этом году?",
        "Чего ты боишься — и что будешь делать, если это случится?",
        "Какие цели из списка ты надеешься закрыть к этому дню?",
        "Кого ты любишь и что хочешь им сказать?",
        "Какой совет ты дашь себе будущему?",
        "Что ты делаешь каждый день и хочешь, чтобы это осталось?",
        "Что ты надеешься изменить к моменту, когда это прочитаешь?",
    ]
}
