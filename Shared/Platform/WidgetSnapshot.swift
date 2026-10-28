import Foundation

/// Данные для виджетов: читаются из общего контейнера App Group.
struct WidgetSnapshot {
    let profile: Profile?
    let goals: [Goal]
    var visitedCountries: [String] = []

    static func load() -> WidgetSnapshot {
        let data = try? DataStore.shared.load()
        return WidgetSnapshot(
            profile: data?.profile,
            goals: data?.goals ?? [],
            visitedCountries: data?.visitedCountries ?? []
        )
    }

    var travel: TravelStats {
        TravelStats(visited: visitedCountries, wished: activeGoals.compactMap(\.countryCode))
    }

    var activeGoals: [Goal] {
        goals.filter { !$0.isCompleted }.sorted { $0.createdAt < $1.createdAt }
    }

    var focusedGoals: [Goal] {
        activeGoals.filter(\.isFocused)
    }

    var completedCount: Int {
        goals.filter(\.isCompleted).count
    }

    /// Цель дня: по кругу из целей в фокусе (или из всех активных), «Другая» сдвигает очередь.
    func goalOfTheDay(_ date: Date, shuffle: Int) -> Goal? {
        let pool = focusedGoals.isEmpty ? activeGoals : focusedGoals
        guard !pool.isEmpty else { return nil }
        let index = positiveModulo(Calendar.memento.dayNumber(for: date) + shuffle, pool.count)
        return pool[index]
    }

    /// Моменты обновления виджета: сейчас и каждая следующая полночь.
    static func refreshDates(from now: Date, days: Int) -> [Date] {
        let calendar = Calendar.memento
        let today = calendar.startOfDay(for: now)
        var dates = [now]
        var offset = 1
        while offset <= days {
            if let date = calendar.date(byAdding: .day, value: offset, to: today) {
                dates.append(date)
            }
            offset += 1
        }
        return dates
    }

    /// Пример для галереи виджетов и отладочных превью.
    static var sample: WidgetSnapshot {
        let data = DemoData.make()
        return WidgetSnapshot(profile: data.profile, goals: data.goals, visitedCountries: data.visitedCountries)
    }
}
