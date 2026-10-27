import AppIntents
import WidgetKit

/// Кнопка «Сделано» в интерактивном виджете.
struct CompleteGoalIntent: AppIntent {
    static let title: LocalizedStringResource = "Отметить цель выполненной"
    static let isDiscoverable = false

    @Parameter(title: "Цель")
    var goalID: String

    init() {}

    init(goalID: UUID) {
        self.goalID = goalID.uuidString
    }

    func perform() async throws -> some IntentResult {
        guard let id = UUID(uuidString: goalID) else { return .result() }
        let store = DataStore.shared
        // Если файл прочитать не удалось — ничего не записываем, чтобы не потерять данные.
        guard var data = try store.load(), data.goal(id: id) != nil else { return .result() }
        data.completeGoal(id: id, at: Date())
        try store.save(data)
        WidgetCenter.shared.reloadAllTimelines()
        return .result()
    }
}

/// Кнопка «Другая» — показать следующую цель.
struct ShuffleGoalIntent: AppIntent {
    static let title: LocalizedStringResource = "Показать другую цель"
    static let isDiscoverable = false

    static let shuffleKey = "goalWidgetShuffle"

    init() {}

    func perform() async throws -> some IntentResult {
        let defaults = AppGroup.defaults
        defaults.set(defaults.integer(forKey: Self.shuffleKey) + 1, forKey: Self.shuffleKey)
        WidgetCenter.shared.reloadAllTimelines()
        return .result()
    }
}

/// Ключи виджетов — нужны, чтобы перезагружать их из приложения.
enum WidgetKinds {
    static let life = "LifeWidget"
    static let goal = "GoalWidget"
}
