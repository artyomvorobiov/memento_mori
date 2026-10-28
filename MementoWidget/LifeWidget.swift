import SwiftUI
import WidgetKit

// MARK: - Таймлайн

struct LifeEntry: TimelineEntry {
    let date: Date
    let snapshot: WidgetSnapshot
    var unit: LifeUnit = .weeks

    var stats: LifeStats? {
        snapshot.profile.map { LifeStats(profile: $0, now: date) }
    }
}

struct LifeProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> LifeEntry {
        LifeEntry(date: Date(), snapshot: .sample)
    }

    func snapshot(for configuration: LifeWidgetIntent, in context: Context) async -> LifeEntry {
        let snapshot = WidgetSnapshot.load()
        let useSample = context.isPreview && snapshot.profile == nil
        return LifeEntry(date: Date(), snapshot: useSample ? .sample : snapshot, unit: configuration.unit)
    }

    func timeline(for configuration: LifeWidgetIntent, in context: Context) async -> Timeline<LifeEntry> {
        let snapshot = WidgetSnapshot.load()
        let now = Date()
        guard snapshot.profile != nil else {
            // Данных пока нет — заглянем снова скоро, а не через неделю.
            return Timeline(entries: [LifeEntry(date: now, snapshot: snapshot, unit: configuration.unit)],
                            policy: .after(now.addingTimeInterval(15 * 60)))
        }
        let entries = configuration.unit.refreshDates(from: now).map { date in
            LifeEntry(date: date, snapshot: snapshot, unit: configuration.unit)
        }
        return Timeline(entries: entries, policy: .atEnd)
    }
}

// MARK: - Виджет

struct LifeWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: WidgetKinds.life, intent: LifeWidgetIntent.self, provider: LifeProvider()) { entry in
            LifeWidgetView(entry: entry)
                .containerBackground(for: .widget) {
                    Color.black
                }
        }
        .configurationDisplayName("Время жизни")
        .description("Сколько времени впереди — в годах, неделях, днях, часах или живыми секундами. Единицу можно сменить: удерживай виджет → «Изменить виджет».")
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
                MediumLifeView(stats: stats, quote: Quotes.ofTheDay(entry.date), unit: entry.unit)
            case .systemLarge:
                LargeLifeView(stats: stats, snapshot: entry.snapshot, unit: entry.unit)
            case .accessoryCircular:
                CircularLifeView(stats: stats)
            case .accessoryRectangular:
                RectangularLifeView(stats: stats, unit: entry.unit)
            case .accessoryInline:
                InlineLifeView(stats: stats, unit: entry.unit)
            default:
                SmallLifeView(stats: stats, unit: entry.unit)
            }
        } else {
            WidgetEmptyView(family: family)
        }
    }
}
