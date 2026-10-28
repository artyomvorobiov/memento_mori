import SwiftUI
import WidgetKit

// MARK: - Таймлайн

struct CountersEntry: TimelineEntry {
    let date: Date
    let snapshot: WidgetSnapshot
    /// Выбранный в настройках счётчик. nil — сначала те, что давно не случались.
    var pinnedID: UUID?

    var stats: LifeStats? {
        snapshot.profile.map { LifeStats(profile: $0, now: date) }
    }

    var counters: [LifeCounter] {
        var list = snapshot.countersByUrgency(now: date)
        if let pinnedID, let index = list.firstIndex(where: { $0.id == pinnedID }) {
            list.insert(list.remove(at: index), at: 0)
        }
        return list
    }
}

struct CountersProvider: AppIntentTimelineProvider {
    func placeholder(in context: Context) -> CountersEntry {
        CountersEntry(date: Date(), snapshot: .sample)
    }

    func snapshot(for configuration: CounterWidgetIntent, in context: Context) async -> CountersEntry {
        let snapshot = WidgetSnapshot.load()
        let useSample = context.isPreview && snapshot.counters.isEmpty
        return CountersEntry(date: Date(), snapshot: useSample ? .sample : snapshot, pinnedID: configuration.counter?.id)
    }

    func timeline(for configuration: CounterWidgetIntent, in context: Context) async -> Timeline<CountersEntry> {
        let snapshot = WidgetSnapshot.load()
        // «N дней назад» меняется в полночь.
        let entries = WidgetSnapshot.refreshDates(from: Date(), days: 7).map { date in
            CountersEntry(date: date, snapshot: snapshot, pinnedID: configuration.counter?.id)
        }
        return Timeline(entries: entries, policy: .atEnd)
    }
}

// MARK: - Виджет

struct CountersWidget: Widget {
    var body: some WidgetConfiguration {
        AppIntentConfiguration(kind: WidgetKinds.counters, intent: CounterWidgetIntent.self, provider: CountersProvider()) { entry in
            CountersWidgetView(entry: entry)
                .containerBackground(for: .widget) {
                    Color.black
                }
        }
        .configurationDisplayName("Последний раз")
        .description("Сколько дней прошло с последней встречи, отпуска или книги — и кнопка «Было» прямо на экране.")
        .supportedFamilies([.systemSmall, .systemMedium])
    }
}

struct CountersWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: CountersEntry

    var body: some View {
        let counters = entry.counters
        if entry.snapshot.profile == nil {
            WidgetEmptyView(family: family)
        } else if counters.isEmpty {
            NoCountersView()
        } else if family == .systemMedium {
            CountersMediumView(counters: counters, stats: entry.stats, date: entry.date)
        } else if let counter = counters.first {
            CounterSmallView(counter: counter, stats: entry.stats, date: entry.date)
                .widgetURL(DeepLink.url(for: .counter(counter.id)))
        }
    }
}
