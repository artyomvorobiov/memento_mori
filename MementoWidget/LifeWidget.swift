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
                InlineLifeView(stats: stats)
            default:
                SmallLifeView(stats: stats)
            }
        } else {
            WidgetEmptyView(family: family)
        }
    }
}
