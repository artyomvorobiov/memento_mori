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
    var unit: LifeUnit = .weeks

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            WidgetCaption(text: "MEMENTO MORI")
            Spacer(minLength: 4)
            RemainingTimeView(stats: stats, unit: unit)
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
    var unit: LifeUnit = .weeks

    var body: some View {
        HStack(spacing: 16) {
            SmallLifeView(stats: stats, unit: unit)
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
    var unit: LifeUnit = .weeks

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: 2) {
                    WidgetCaption(text: "MEMENTO MORI")
                    RemainingTimeView(stats: stats, unit: unit, size: 34)
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
    var unit: LifeUnit = .weeks

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 4) {
                Image(systemName: "hourglass")
                Group {
                    if unit == .seconds && !stats.isBeyondExpectancy {
                        Text(timerInterval: stats.now...stats.deathDate, countsDown: true)
                    } else {
                        Text(unit.short(stats))
                    }
                }
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
    var unit: LifeUnit = .weeks

    var body: some View {
        // Строка на экране блокировки не тикает, поэтому секунды тут показываем минутами.
        Label((unit == .seconds ? LifeUnit.minutes : unit).short(stats) + " впереди", systemImage: "hourglass")
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
        let groups = AppGroup.ownProvisionedGroups
        let detail = groups.isEmpty ? "у виджета не включена ни одна группа" : "у виджета: " + groups.joined(separator: ", ")
        return "Нет доступа к данным: \(detail). В Xcode у MementoWidgetExtension включи ту же App Group, что у приложения."
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

// MARK: - «Последний раз»

/// Сколько дней назад: крупно число, мелко подпись. Если давно — акцентом.
private struct DaysAgoText: View {
    let counter: LifeCounter
    let date: Date
    var size: CGFloat = 36

    var body: some View {
        if let days = counter.daysSinceLast(now: date) {
            VStack(alignment: .leading, spacing: 0) {
                Text(days > 1 ? Format.number(days) : Format.daysAgo(days))
                    .font(.system(size: days > 1 ? size : size * 0.7, weight: .thin))
                    .monospacedDigit()
                    .lineLimit(1)
                    .minimumScaleFactor(0.6)
                    .foregroundStyle(counter.isOverdue(now: date) ? Theme.accent : Color.white)
                    .widgetAccentable()
                if days > 1 {
                    Text(RussianPlural.form(days, "день назад", "дня назад", "дней назад"))
                        .font(.caption2)
                        .foregroundStyle(Color.white.opacity(0.6))
                }
            }
        } else {
            Text("ещё не было")
                .font(.system(size: size * 0.55, weight: .light))
                .foregroundStyle(Color.white.opacity(0.8))
        }
    }
}

/// Кнопка «Было» или отметка, что сегодня уже было.
private struct LogCounterButton: View {
    let counter: LifeCounter
    let date: Date
    var compact = false

    var body: some View {
        if counter.isLogged(on: date) {
            WidgetButtonLabel(systemImage: "checkmark", title: compact ? nil : "Сегодня", isProminent: false)
                .opacity(0.7)
        } else {
            Button(intent: LogCounterIntent(counterID: counter.id)) {
                WidgetButtonLabel(systemImage: "plus", title: compact ? nil : "Было сегодня", isProminent: true)
            }
            .buttonStyle(.plain)
        }
    }
}

/// «≈ 98 встреч с родителями» — название счётчика читается после числа.
func counterRemainingText(_ counter: LifeCounter, stats: LifeStats?) -> String {
    guard let stats else { return counter.title }
    let left = counter.remaining(remainingYears: stats.remainingYears)
    return "≈ \(Format.number(left)) \(counter.title)"
}

struct CounterSmallView: View {
    let counter: LifeCounter
    let stats: LifeStats?
    let date: Date

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 6) {
                Text(counter.emoji)
                    .font(.system(size: 15))
                WidgetCaption(text: "ПОСЛЕДНИЙ РАЗ")
            }
            Spacer(minLength: 0)
            DaysAgoText(counter: counter, date: date)
            Text(counterRemainingText(counter, stats: stats))
                .font(.system(size: 11))
                .foregroundStyle(Color.white.opacity(0.6))
                .lineLimit(2)
                .minimumScaleFactor(0.8)
            Spacer(minLength: 4)
            LogCounterButton(counter: counter, date: date)
        }
        .foregroundStyle(Color.white)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}

struct CountersMediumView: View {
    let counters: [LifeCounter]
    let stats: LifeStats?
    let date: Date

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            WidgetCaption(text: "ПОСЛЕДНИЙ РАЗ")
            ForEach(counters.prefix(3)) { counter in
                HStack(spacing: 10) {
                    Text(counter.emoji)
                        .font(.system(size: 18))
                        .frame(width: 24)
                    Link(destination: DeepLink.url(for: .counter(counter.id))) {
                        VStack(alignment: .leading, spacing: 1) {
                            Text(counterRemainingText(counter, stats: stats))
                                .font(.system(size: 13, weight: .medium))
                                .lineLimit(1)
                                .minimumScaleFactor(0.8)
                            Text(lastTimeText(counter))
                                .font(.system(size: 11))
                                .foregroundStyle(counter.isOverdue(now: date) ? Theme.accent : Color.white.opacity(0.6))
                                .lineLimit(1)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    LogCounterButton(counter: counter, date: date, compact: true)
                        .frame(width: 44)
                }
            }
            Spacer(minLength: 0)
        }
        .foregroundStyle(Color.white)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private func lastTimeText(_ counter: LifeCounter) -> String {
        guard let days = counter.daysSinceLast(now: date) else { return "ещё не отмечено" }
        return "последний раз — " + Format.daysAgo(days)
    }
}

struct NoCountersView: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            WidgetCaption(text: "ПОСЛЕДНИЙ РАЗ")
            Spacer(minLength: 0)
            Text("❤️")
                .font(.title3)
            Text("Добавь счётчик на главном экране приложения — встречи с родителями, отпуска, книги — и отмечай, когда это было.")
                .font(.caption)
                .foregroundStyle(Color.white.opacity(0.8))
                .minimumScaleFactor(0.8)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
    }
}

// MARK: - «Письмо себе»

/// Сколько дней до дня, когда письмо можно будет открыть.
func daysUntilLetter(_ letter: FutureLetter, birthDate: Date, now: Date, calendar: Calendar = .memento) -> Int {
    let unlock = letter.unlockDate(birthDate: birthDate, calendar: calendar)
    return max(0, calendar.dateComponents([.day], from: calendar.startOfDay(for: now), to: unlock).day ?? 0)
}

struct LetterSmallView: View {
    let letter: FutureLetter?
    let isWaiting: Bool
    let birthDate: Date
    let date: Date

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            WidgetCaption(text: "ПИСЬМО СЕБЕ")
            Spacer(minLength: 0)
            if let letter, isWaiting {
                Image(systemName: "envelope.open.fill")
                    .font(.title2)
                    .foregroundStyle(Theme.accent)
                    .widgetAccentable()
                Text("Пришло письмо из \(String(Calendar.memento.component(.year, from: letter.createdAt))) года")
                    .font(.system(.subheadline, design: .serif).weight(.medium))
                    .lineLimit(3)
                    .minimumScaleFactor(0.8)
                Text("Открой — оно ждёт")
                    .font(.caption2)
                    .foregroundStyle(Color.white.opacity(0.6))
            } else if let letter {
                let days = daysUntilLetter(letter, birthDate: birthDate, now: date)
                Image(systemName: "envelope.fill")
                    .font(.system(size: 15))
                    .foregroundStyle(Theme.accent)
                    .widgetAccentable()
                Text(Format.number(days))
                    .font(.system(size: 36, weight: .thin))
                    .monospacedDigit()
                    .lineLimit(1)
                    .minimumScaleFactor(0.5)
                Text(RussianPlural.form(days, "день до письма", "дня до письма", "дней до письма"))
                    .font(.caption2)
                    .foregroundStyle(Color.white.opacity(0.6))
                Text(letter.openLabel(birthDate: birthDate).capitalizedFirst)
                    .font(.system(size: 10))
                    .foregroundStyle(Color.white.opacity(0.45))
                    .lineLimit(1)
            } else {
                Image(systemName: "envelope")
                    .font(.title3)
                    .foregroundStyle(Theme.accent)
                Text("Напиши письмо себе в будущее — оно откроется в выбранный день.")
                    .font(.caption)
                    .foregroundStyle(Color.white.opacity(0.8))
                    .minimumScaleFactor(0.8)
            }
        }
        .foregroundStyle(Color.white)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
    }
}

struct LetterRectangularView: View {
    let letter: FutureLetter?
    let isWaiting: Bool
    let birthDate: Date
    let date: Date

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            if let letter, isWaiting {
                Label("Письмо ждёт", systemImage: "envelope.open.fill")
                    .font(.headline)
                    .widgetAccentable()
                Text("из \(String(Calendar.memento.component(.year, from: letter.createdAt))) года — открой")
                    .font(.caption)
            } else if let letter {
                let days = daysUntilLetter(letter, birthDate: birthDate, now: date)
                Label(Format.days(days), systemImage: "envelope.fill")
                    .font(.headline)
                    .widgetAccentable()
                Text("до письма себе · \(letter.openLabel(birthDate: birthDate))")
                    .font(.caption)
                    .lineLimit(1)
            } else {
                Label("Письмо себе", systemImage: "envelope")
                    .font(.headline)
                Text("Напиши его в приложении")
                    .font(.caption)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}
