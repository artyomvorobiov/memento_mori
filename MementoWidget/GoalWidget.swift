import AppIntents
import SwiftUI
import WidgetKit

// MARK: - Таймлайн

struct GoalEntry: TimelineEntry {
    let date: Date
    let goal: Goal?
    let hasProfile: Bool
    let birthDate: Date?
    let activeCount: Int

    static var sample: GoalEntry {
        let snapshot = WidgetSnapshot.sample
        return GoalEntry(
            date: Date(),
            goal: snapshot.activeGoals.first,
            hasProfile: true,
            birthDate: snapshot.profile?.birthDate,
            activeCount: snapshot.activeGoals.count
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
                birthDate: snapshot.profile?.birthDate,
                activeCount: snapshot.activeGoals.count
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
                MediumGoalView(goal: goal, entry: entry)
            } else {
                SmallGoalView(goal: goal)
            }
        } else {
            NoGoalsView()
        }
    }
}

struct SmallGoalView: View {
    let goal: Goal

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                GoalIcon(goal: goal, size: 14)
                Spacer()
                if goal.isFocused {
                    Image(systemName: "star.fill")
                        .font(.system(size: 10))
                        .foregroundStyle(Theme.accent)
                        .widgetAccentable()
                }
            }
            Text(goal.title)
                .font(.system(.subheadline, design: .serif).weight(.medium))
                .foregroundStyle(Color.white)
                .lineLimit(4)
                .minimumScaleFactor(0.75)
            Spacer(minLength: 0)
            HStack(spacing: 6) {
                Button(intent: CompleteGoalIntent(goalID: goal.id)) {
                    WidgetButtonLabel(systemImage: "checkmark", isProminent: true)
                }
                Button(intent: ShuffleGoalIntent()) {
                    WidgetButtonLabel(systemImage: "arrow.triangle.2.circlepath", isProminent: false)
                }
            }
            .buttonStyle(.plain)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

struct MediumGoalView: View {
    let goal: Goal
    let entry: GoalEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                GoalIcon(goal: goal, size: 14)
                WidgetCaption(text: caption)
                Spacer()
            }
            Text(goal.title)
                .font(.system(.headline, design: .serif))
                .foregroundStyle(Color.white)
                .lineLimit(2)
                .minimumScaleFactor(0.8)
            if let detail {
                Text(detail)
                    .font(.caption)
                    .foregroundStyle(Color.white.opacity(0.6))
                    .lineLimit(2)
            }
            Spacer(minLength: 0)
            HStack(spacing: 8) {
                Button(intent: CompleteGoalIntent(goalID: goal.id)) {
                    WidgetButtonLabel(systemImage: "checkmark", title: "Сделано", isProminent: true)
                }
                Button(intent: ShuffleGoalIntent()) {
                    WidgetButtonLabel(systemImage: "arrow.triangle.2.circlepath", title: "Другая", isProminent: false)
                }
            }
            .buttonStyle(.plain)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private var caption: String {
        goal.isFocused ? "В ФОКУСЕ" : "ЦЕЛЬ ДНЯ"
    }

    /// Срок цели или её «зачем».
    private var detail: String? {
        if let summary = DeadlineText.summary(for: goal, birthDate: entry.birthDate, now: entry.date) {
            return summary.capitalizedFirst
        }
        return goal.note.isEmpty ? nil : goal.note
    }
}

struct WidgetButtonLabel: View {
    let systemImage: String
    var title: String? = nil
    let isProminent: Bool

    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: systemImage)
            if let title {
                Text(title)
            }
        }
        .font(.system(size: 12, weight: .semibold))
        .frame(maxWidth: .infinity)
        .padding(.vertical, 7)
        .background(isProminent ? Theme.accent : Color.white.opacity(0.12), in: Capsule())
        .foregroundStyle(isProminent ? Color.black : Color.white)
    }
}

struct NoGoalsView: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            WidgetCaption(text: "ЦЕЛЬ ДНЯ")
            Spacer(minLength: 0)
            Image(systemName: "sparkles")
                .font(.title3)
                .foregroundStyle(Theme.accent)
            Text("Список пуст. Добавь, что хочешь успеть, — время идёт.")
                .font(.caption)
                .foregroundStyle(Color.white.opacity(0.8))
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
    }
}
