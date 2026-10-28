import AppIntents
import UserNotifications
import WidgetKit

/// Кнопка «Было» в виджете «Последний раз»: отметить сегодня.
struct LogCounterIntent: AppIntent {
    static let title: LocalizedStringResource = "Отметить «Было»"
    static let isDiscoverable = false

    @Parameter(title: "Счётчик")
    var counterID: String

    init() {}

    init(counterID: UUID) {
        self.counterID = counterID.uuidString
    }

    func perform() async throws -> some IntentResult {
        guard let id = UUID(uuidString: counterID) else { return .result() }
        let store = DataStore.shared
        // Если файл прочитать не удалось — ничего не записываем, чтобы не потерять данные.
        guard var data = try store.load(), data.logCounterToday(id: id) else { return .result() }
        try store.save(data)
        // Напоминание «давно не было» больше не актуально; новое запланирует приложение.
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [ReminderPlanner.counterReminderID(id)])
        WidgetCenter.shared.reloadAllTimelines()
        return .result()
    }
}

/// Счётчик для настройки виджета.
struct CounterEntity: AppEntity {
    static let typeDisplayRepresentation: TypeDisplayRepresentation = "Счётчик"
    static let defaultQuery = CounterQuery()

    let id: UUID
    let title: String
    let emoji: String

    var displayRepresentation: DisplayRepresentation {
        DisplayRepresentation(title: "\(emoji) \(title)")
    }

    init(counter: LifeCounter) {
        id = counter.id
        title = counter.title
        emoji = counter.emoji
    }
}

struct CounterQuery: EntityQuery {
    func entities(for identifiers: [UUID]) async throws -> [CounterEntity] {
        allCounters().filter { identifiers.contains($0.id) }
    }

    func suggestedEntities() async throws -> [CounterEntity] {
        allCounters()
    }

    private func allCounters() -> [CounterEntity] {
        let data = try? DataStore.shared.load()
        return (data?.counters ?? []).map(CounterEntity.init)
    }
}

/// Настройка виджета «Последний раз»: какой счётчик показывать.
struct CounterWidgetIntent: WidgetConfigurationIntent {
    static let title: LocalizedStringResource = "Последний раз"
    static let description = IntentDescription("Выбери счётчик. Если не выбрать — виджет покажет то, что давно не случалось.")

    @Parameter(title: "Счётчик")
    var counter: CounterEntity?

    init() {}
}
