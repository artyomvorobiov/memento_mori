import SwiftUI
import WidgetKit

// MARK: - Таймлайн

struct GoalEntry: TimelineEntry {
    let date: Date
    let goal: Goal?
    let hasProfile: Bool
    let birthDate: Date?

    static var sample: GoalEntry {
        let snapshot = WidgetSnapshot.sample
        return GoalEntry(
            date: Date(),
            goal: snapshot.focusedGoals.first ?? snapshot.activeGoals.first,
            hasProfile: true,
            birthDate: snapshot.profile?.birthDate
        )
    }
}

struct GoalProvider: TimelineProvider {
    func placeholder(in context: Context) -> GoalEntry {
        .sample
    }

    func getSnapshot(in context: Context, completion: @escaping (GoalEntry) -> Void) {
        let entry = makeEntries(days: 0).first
        if context.isPreview && entry?.goal == nil {
            completion(.sample)
        } else {
            completion(entry ?? .sample)
        }
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<GoalEntry>) -> Void) {
        completion(Timeline(entries: makeEntries(days: 3), policy: .atEnd))
    }

    private func makeEntries(days: Int) -> [GoalEntry] {
        let snapshot = WidgetSnapshot.load()
        let shuffle = AppGroup.defaults.integer(forKey: ShuffleGoalIntent.shuffleKey)
        return WidgetSnapshot.refreshDates(from: Date(), days: days).map { date in
            GoalEntry(
                date: date,
                goal: snapshot.goalOfTheDay(date, shuffle: shuffle),
                hasProfile: snapshot.profile != nil,
                birthDate: snapshot.profile?.birthDate
            )
        }
    }
}

// MARK: - Виджет

struct GoalWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: WidgetKinds.goal, provider: GoalProvider()) { entry in
            GoalWidgetView(entry: entry)
                .containerBackground(for: .widget) {
                    Color.black
                }
        }
        .configurationDisplayName("Цель дня")
        .description("Одна цель из твоего списка. Отметь её выполненной прямо с экрана.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

struct GoalWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: GoalEntry

    var body: some View {
        if !entry.hasProfile {
            WidgetEmptyView(family: family)
        } else if let goal = entry.goal {
            if family == .systemMedium {
                MediumGoalView(goal: goal, birthDate: entry.birthDate, date: entry.date)
            } else {
                SmallGoalView(goal: goal)
            }
        } else {
            NoGoalsView()
        }
    }
}
