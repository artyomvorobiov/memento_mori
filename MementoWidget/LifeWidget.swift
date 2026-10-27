import SwiftUI
import WidgetKit

// MARK: - Таймлайн

struct LifeEntry: TimelineEntry {
    let date: Date
    let snapshot: WidgetSnapshot

    var stats: LifeStats? {
        snapshot.profile.map { LifeStats(profile: $0, now: date) }
    }
}

struct LifeProvider: TimelineProvider {
    func placeholder(in context: Context) -> LifeEntry {
        LifeEntry(date: Date(), snapshot: .sample)
    }

    func getSnapshot(in context: Context, completion: @escaping (LifeEntry) -> Void) {
        let snapshot = WidgetSnapshot.load()
        let useSample = context.isPreview && snapshot.profile == nil
        completion(LifeEntry(date: Date(), snapshot: useSample ? .sample : snapshot))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<LifeEntry>) -> Void) {
        let snapshot = WidgetSnapshot.load()
        let entries = WidgetSnapshot.refreshDates(from: Date(), days: 7).map { date in
            LifeEntry(date: date, snapshot: snapshot)
        }
        completion(Timeline(entries: entries, policy: .atEnd))
    }
}

// MARK: - Виджет

struct LifeWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: WidgetKinds.life, provider: LifeProvider()) { entry in
            LifeWidgetView(entry: entry)
                .containerBackground(for: .widget) {
                    Color.black
                }
        }
        .configurationDisplayName("Время жизни")
        .description("Сколько недель впереди и какая часть жизни уже прожита.")
        .supportedFamilies([
            .systemSmall,
            .systemMedium,
            .systemLarge,
            .accessoryCircular,
            .accessoryRectangular,
            .accessoryInline,
        ])
    }
}

struct LifeWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: LifeEntry

    var body: some View {
        if let stats = entry.stats {
            switch family {
            case .systemMedium:
                MediumLifeView(stats: stats, quote: Quotes.ofTheDay(entry.date))
            case .systemLarge:
                LargeLifeView(stats: stats, snapshot: entry.snapshot)
            case .accessoryCircular:
                CircularLifeView(stats: stats)
            case .accessoryRectangular:
                RectangularLifeView(stats: stats)
            case .accessoryInline:
                Label(Format.weeks(stats.remainingWeeks) + " впереди", systemImage: "hourglass")
            default:
                SmallLifeView(stats: stats)
            }
        } else {
            WidgetEmptyView(family: family)
        }
    }
}

// MARK: - Главный экран

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
                Image(systemName: "hourglass")
                    .font(.title2)
                    .foregroundStyle(Theme.accent)
                Text("Открой приложение, чтобы начать отсчёт")
                    .font(.caption)
                    .foregroundStyle(Color.white.opacity(0.8))
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        }
    }
}
