import SwiftUI
import WidgetKit

struct TravelEntry: TimelineEntry {
    let date: Date
    let snapshot: WidgetSnapshot
}

struct TravelProvider: TimelineProvider {
    func placeholder(in context: Context) -> TravelEntry {
        TravelEntry(date: Date(), snapshot: .sample)
    }

    func getSnapshot(in context: Context, completion: @escaping (TravelEntry) -> Void) {
        let snapshot = WidgetSnapshot.load()
        let useSample = context.isPreview && snapshot.profile == nil
        completion(TravelEntry(date: Date(), snapshot: useSample ? .sample : snapshot))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<TravelEntry>) -> Void) {
        // Карта меняется только из приложения — оно само перезагружает виджеты.
        completion(Timeline(entries: [TravelEntry(date: Date(), snapshot: WidgetSnapshot.load())], policy: .never))
    }
}

struct TravelWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: WidgetKinds.travel, provider: TravelProvider()) { entry in
            TravelWidgetView(entry: entry)
                .containerBackground(for: .widget) {
                    Color.black
                }
        }
        .configurationDisplayName("Мой мир")
        .description("Карта стран, где ты уже побывал(а) и куда собираешься.")
        .supportedFamilies([.systemMedium, .systemLarge])
    }
}

struct TravelWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: TravelEntry

    var body: some View {
        if entry.snapshot.profile == nil {
            WidgetEmptyView(family: family)
        } else if family == .systemLarge {
            TravelLargeView(travel: entry.snapshot.travel)
        } else {
            TravelMediumView(travel: entry.snapshot.travel)
        }
    }
}
