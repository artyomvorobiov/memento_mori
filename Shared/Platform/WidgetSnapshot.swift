import Foundation

/// Данные для виджетов: читаются из общего контейнера App Group.
struct WidgetSnapshot {
    let profile: Profile?
    let goals: [Goal]
    var visitedCountries: [String] = []
    var counters: [LifeCounter] = []
    var letters: [FutureLetter] = []

    init(data: AppData?) {
        profile = data?.profile
        goals = data?.goals ?? []
        visitedCountries = data?.visitedCountries ?? []
        counters = data?.counters ?? []
        letters = data?.letters ?? []
    }

    static func load() -> WidgetSnapshot {
        WidgetSnapshot(data: try? DataStore.shared.load())
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

    /// Счётчики для виджета «Последний раз»: сначала те, где «пора», потом остальные с отметками.
    func countersByUrgency(now: Date) -> [LifeCounter] {
        counters.sorted { lhs, rhs in
            let left = lhs.log.isEmpty ? -1 : lhs.urgency(now: now)
            let right = rhs.log.isEmpty ? -1 : rhs.urgency(now: now)
            return left != right ? left > right : lhs.createdAt < rhs.createdAt
        }
    }

    /// Письмо для виджета: ждущее открытия или ближайшее запечатанное.
    func letterForWidget(now: Date) -> (letter: FutureLetter, isWaiting: Bool)? {
        var data = AppData()
        data.profile = profile
        data.letters = letters
        if let waiting = data.waitingLetters(now: now).first {
            return (waiting, true)
        }
        return data.sealedLetters(now: now).first.map { ($0, false) }
    }

    /// Пример для галереи виджетов и отладочных превью.
    static var sample: WidgetSnapshot {
        WidgetSnapshot(data: DemoData.make())
    }
}
