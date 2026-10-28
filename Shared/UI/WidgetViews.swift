import AppIntents
import SwiftUI
import WidgetKit

// Содержимое виджетов. Лежит в Shared, чтобы приложение могло показать его
// в отладочной галерее (аргумент запуска -MementoWidgetGallery).

struct WidgetCaption: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.system(size: 10, weight: .semibold))
            .tracking(1.6)
            .foregroundStyle(Color.white.opacity(0.5))
            .lineLimit(1)
    }
}

// MARK: - «Время жизни»

struct SmallLifeView: View {
    let stats: LifeStats

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            WidgetCaption(text: "MEMENTO MORI")
            Spacer(minLength: 4)
            Text(Format.number(stats.remainingWeeks))
                .font(.system(size: 40, weight: .thin))
                .monospacedDigit()
                .lineLimit(1)
                .minimumScaleFactor(0.5)
                .widgetAccentable()
            Text(RussianPlural.form(stats.remainingWeeks, "неделя впереди", "недели впереди", "недель впереди"))
                .font(.caption2)
                .foregroundStyle(Color.white.opacity(0.6))
            Spacer(minLength: 6)
            ThinProgressBar(progress: stats.progress, height: 3)
            Text("\(Format.percent(stats.progress)) прожито")
                .font(.system(size: 10))
                .foregroundStyle(Color.white.opacity(0.5))
                .padding(.top, 4)
        }
        .foregroundStyle(Color.white)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
    }
}

struct MediumLifeView: View {
    let stats: LifeStats
    let quote: Quote

    var body: some View {
        HStack(spacing: 16) {
            SmallLifeView(stats: stats)
                .frame(width: 128)
            Rectangle()
                .fill(Color.white.opacity(0.12))
                .frame(width: 0.5)
            VStack(alignment: .leading, spacing: 6) {
                Text("«\(quote.text)»")
                    .font(.system(size: 13, design: .serif))
                    .italic()
                    .foregroundStyle(Color.white.opacity(0.9))
                    .lineLimit(5)
                    .minimumScaleFactor(0.8)
                Text(quote.author)
                    .font(.system(size: 10))
                    .foregroundStyle(Color.white.opacity(0.5))
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        }
    }
}

struct LargeLifeView: View {
    let stats: LifeStats
    let snapshot: WidgetSnapshot

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: 2) {
                    WidgetCaption(text: "MEMENTO MORI")
                    Text(Format.number(stats.remainingWeeks))
                        .font(.system(size: 34, weight: .thin))
                        .monospacedDigit()
                        .widgetAccentable()
                    Text(RussianPlural.form(stats.remainingWeeks, "неделя впереди", "недели впереди", "недель впереди"))
                        .font(.caption2)
                        .foregroundStyle(Color.white.opacity(0.6))
                }
                Spacer()
                VStack(alignment: .trailing, spacing: 2) {
                    Text(Format.percent(stats.progress))
                        .font(.system(size: 22, weight: .light))
                        .monospacedDigit()
                    Text("прожито")
                        .font(.caption2)
                        .foregroundStyle(Color.white.opacity(0.6))
                }
            }

            LifeYearsGrid(stats: stats)
                .frame(maxWidth: .infinity, maxHeight: .infinity)

            focusList
        }
        .foregroundStyle(Color.white)
    }

    @ViewBuilder
    private var focusList: some View {
        let focus = Array(snapshot.focusedGoals.prefix(3))
        if focus.isEmpty {
            Text("Выполнено целей: \(snapshot.completedCount) · впереди: \(snapshot.activeGoals.count)")
                .font(.caption)
                .foregroundStyle(Color.white.opacity(0.6))
        } else {
            VStack(alignment: .leading, spacing: 4) {
                ForEach(focus) { goal in
                    HStack(spacing: 6) {
                        Image(systemName: "star.fill")
                            .font(.system(size: 8))
                            .foregroundStyle(Theme.accent)
                            .widgetAccentable()
                        Text(goal.title)
                            .font(.caption)
                            .lineLimit(1)
                    }
                }
            }
        }
    }
}

// MARK: - Экран блокировки

struct CircularLifeView: View {
    let stats: LifeStats

    var body: some View {
        Gauge(value: stats.progress) {
            Image(systemName: "hourglass")
        } currentValueLabel: {
            Text("\(Int((stats.progress * 100).rounded()))")
        }
        .gaugeStyle(.accessoryCircular)
    }
}

struct RectangularLifeView: View {
    let stats: LifeStats

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 4) {
                Image(systemName: "hourglass")
                Text(Format.weeks(stats.remainingWeeks))
                    .font(.headline)
                    .lineLimit(1)
                    .minimumScaleFactor(0.7)
            }
            .widgetAccentable()
            Text("впереди · \(Format.percent(stats.progress, digits: 0)) прожито")
                .font(.caption)
                .lineLimit(1)
            ProgressView(value: stats.progress)
                .progressViewStyle(.linear)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct InlineLifeView: View {
    let stats: LifeStats

    var body: some View {
        Label(Format.weeks(stats.remainingWeeks) + " впереди", systemImage: "hourglass")
    }
}

// MARK: - «Цель дня»

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
    let birthDate: Date?
    let date: Date

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 6) {
                GoalIcon(goal: goal, size: 14)
                WidgetCaption(text: goal.isFocused ? "В ФОКУСЕ" : "ЦЕЛЬ ДНЯ")
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

    /// Следующий шаг, срок цели или её «зачем».
    private var detail: String? {
        if let step = goal.nextStep {
            return "Дальше: \(step.title)"
        }
        if let summary = DeadlineText.summary(for: goal, birthDate: birthDate, now: date) {
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

// MARK: - Пусто

struct WidgetEmptyView: View {
    let family: WidgetFamily

    var body: some View {
        switch family {
        case .accessoryInline:
            Text("Memento mori")
        case .accessoryCircular:
            Image(systemName: "hourglass")
                .font(.title2)
        case .accessoryRectangular:
            VStack(alignment: .leading) {
                Text("Memento mori")
                    .font(.headline)
                Text("Открой приложение")
                    .font(.caption)
            }
        default:
            VStack(alignment: .leading, spacing: 8) {
                WidgetCaption(text: "MEMENTO MORI")
                Spacer(minLength: 0)
                Image(systemName: AppGroup.isAvailable ? "hourglass" : "exclamationmark.triangle")
                    .font(.title2)
                    .foregroundStyle(Theme.accent)
                Text(message)
                    .font(.caption)
                    .foregroundStyle(Color.white.opacity(0.8))
                    .minimumScaleFactor(0.8)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        }
    }

    private var message: String {
        if AppGroup.isAvailable {
            return "Открой приложение, чтобы начать отсчёт"
        }
        return "Нет доступа к данным приложения. Включи App Groups у приложения и виджета — см. README."
    }
}

// MARK: - «Мой мир»

extension WorldMapStyle {
    /// Для виджетов и картинок: только отмеченные маленькие страны.
    static var compact: WorldMapStyle {
        var style = WorldMapStyle()
        style.showsUnmarkedDots = false
        return style
    }
}

struct TravelMediumView: View {
    let travel: TravelStats

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                WidgetCaption(text: "МОЙ МИР")
                Spacer()
                Text("\(travel.visitedCountries)")
                    .font(.system(size: 20, weight: .light))
                    .monospacedDigit()
                    .widgetAccentable()
                Text(RussianPlural.form(travel.visitedCountries, "страна", "страны", "стран") + " · " + Format.percent(travel.worldShare, digits: 0))
                    .font(.caption2)
                    .foregroundStyle(Color.white.opacity(0.6))
            }
            WorldMapView(visited: travel.visited, wished: travel.wished, style: .compact)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .foregroundStyle(Color.white)
    }
}

struct TravelLargeView: View {
    let travel: TravelStats

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            WidgetCaption(text: "МОЙ МИР")
            WorldMapView(visited: travel.visited, wished: travel.wished, style: .compact)
                .frame(maxWidth: .infinity)
                .frame(height: 140)
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text("\(travel.visitedCountries)")
                    .font(.system(size: 34, weight: .thin))
                    .monospacedDigit()
                    .widgetAccentable()
                Text(RussianPlural.form(travel.visitedCountries, "страна", "страны", "стран"))
                    .font(.subheadline)
                Spacer()
                Text(Format.percent(travel.worldShare, digits: 0) + " мира")
                    .font(.subheadline.weight(.light))
                    .foregroundStyle(Theme.accent)
            }
            VStack(alignment: .leading, spacing: 5) {
                ForEach(Continent.allCases) { continent in
                    let progress = travel.progress(for: continent)
                    HStack(spacing: 8) {
                        Text(continent.title)
                            .font(.system(size: 11))
                            .frame(width: 112, alignment: .leading)
                        ThinProgressBar(
                            progress: progress.total > 0 ? Double(progress.visited) / Double(progress.total) : 0,
                            height: 3,
                            fill: Theme.accent
                        )
                        Text("\(progress.visited)/\(progress.total)")
                            .font(.system(size: 11))
                            .monospacedDigit()
                            .frame(width: 38, alignment: .trailing)
                    }
                    .foregroundStyle(progress.visited > 0 ? Color.white : Color.white.opacity(0.45))
                }
            }
            Spacer(minLength: 0)
        }
        .foregroundStyle(Color.white)
    }
}
