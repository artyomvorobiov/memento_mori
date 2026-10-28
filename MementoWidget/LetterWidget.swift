import SwiftUI
import WidgetKit

// MARK: - Таймлайн

struct LetterEntry: TimelineEntry {
    let date: Date
    let snapshot: WidgetSnapshot
}

struct LetterProvider: TimelineProvider {
    func placeholder(in context: Context) -> LetterEntry {
        LetterEntry(date: Date(), snapshot: .sample)
    }

    func getSnapshot(in context: Context, completion: @escaping (LetterEntry) -> Void) {
        let snapshot = WidgetSnapshot.load()
        let useSample = context.isPreview && snapshot.letters.isEmpty
        completion(LetterEntry(date: Date(), snapshot: useSample ? .sample : snapshot))
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<LetterEntry>) -> Void) {
        let snapshot = WidgetSnapshot.load()
        // Счёт дней меняется в полночь — в ту же полночь письмо и «приходит».
        let entries = WidgetSnapshot.refreshDates(from: Date(), days: 7).map { date in
            LetterEntry(date: date, snapshot: snapshot)
        }
        completion(Timeline(entries: entries, policy: .atEnd))
    }
}

// MARK: - Виджет

struct LetterWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: WidgetKinds.letter, provider: LetterProvider()) { entry in
            LetterWidgetView(entry: entry)
                .containerBackground(for: .widget) {
                    Color.black
                }
        }
        .configurationDisplayName("Письмо себе")
        .description("Сколько дней осталось до письма, которое ты написал(а) себе в будущее.")
        .supportedFamilies([.systemSmall, .accessoryRectangular])
    }
}

struct LetterWidgetView: View {
    @Environment(\.widgetFamily) private var family
    let entry: LetterEntry

    var body: some View {
        if let profile = entry.snapshot.profile {
            let found = entry.snapshot.letterForWidget(now: entry.date)
            Group {
                if family == .accessoryRectangular {
                    LetterRectangularView(letter: found?.letter, isWaiting: found?.isWaiting ?? false, birthDate: profile.birthDate, date: entry.date)
                } else {
                    LetterSmallView(letter: found?.letter, isWaiting: found?.isWaiting ?? false, birthDate: profile.birthDate, date: entry.date)
                }
            }
            .widgetURL(DeepLink.url(for: found.map { DeepLink.letter($0.letter.id) } ?? DeepLink.newLetter))
        } else {
            WidgetEmptyView(family: family)
        }
    }
}
