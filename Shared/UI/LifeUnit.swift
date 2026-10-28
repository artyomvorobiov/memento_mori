import AppIntents
import SwiftUI
import WidgetKit

/// В чём считать оставшееся время в виджете «Время жизни».
enum LifeUnit: String, AppEnum {
    case years, months, weeks, days, hours, minutes, seconds

    static let typeDisplayRepresentation: TypeDisplayRepresentation = "Единица отсчёта"

    static let caseDisplayRepresentations: [LifeUnit: DisplayRepresentation] = [
        .years: "Годы",
        .months: "Месяцы",
        .weeks: "Недели",
        .days: "Дни",
        .hours: "Часы",
        .minutes: "Минуты",
        .seconds: "Секунды (живой отсчёт)",
    ]

    /// Число для показа. Для секунд не используется — там тикающий таймер.
    func value(_ stats: LifeStats) -> Int {
        switch self {
        case .years: Int(stats.remainingYears)
        case .months: stats.remainingMonths
        case .weeks: stats.remainingWeeks
        case .days: stats.remainingDays
        case .hours: stats.remainingHours
        case .minutes: Int(stats.remainingSeconds / 60)
        case .seconds: Int(stats.remainingSeconds)
        }
    }

    /// Подпись под числом: «недель впереди», «дня впереди»…
    func caption(_ value: Int) -> String {
        switch self {
        case .years: RussianPlural.form(value, "год впереди", "года впереди", "лет впереди")
        case .months: RussianPlural.form(value, "месяц впереди", "месяца впереди", "месяцев впереди")
        case .weeks: RussianPlural.form(value, "неделя впереди", "недели впереди", "недель впереди")
        case .days: RussianPlural.form(value, "день впереди", "дня впереди", "дней впереди")
        case .hours: RussianPlural.form(value, "час впереди", "часа впереди", "часов впереди")
        case .minutes: RussianPlural.form(value, "минута впереди", "минуты впереди", "минут впереди")
        case .seconds: "часов : минут : секунд"
        }
    }

    /// Короткая запись для экрана блокировки: «2 345 недель».
    func short(_ stats: LifeStats) -> String {
        let value = value(stats)
        let number = Format.number(value)
        switch self {
        case .years: return number + " " + RussianPlural.form(value, "год", "года", "лет")
        case .months: return number + " " + RussianPlural.form(value, "месяц", "месяца", "месяцев")
        case .weeks: return Format.weeks(value)
        case .days: return number + " " + RussianPlural.form(value, "день", "дня", "дней")
        case .hours: return number + " " + RussianPlural.form(value, "час", "часа", "часов")
        case .minutes: return number + " " + RussianPlural.form(value, "минута", "минуты", "минут")
        case .seconds: return number + " " + RussianPlural.form(value, "секунда", "секунды", "секунд")
        }
    }

    /// Когда виджету нужно пересчитать число: мелкие единицы — чаще.
    func refreshDates(from now: Date) -> [Date] {
        switch self {
        case .hours:
            return Self.steps(from: now, every: 3_600, count: 24)
        case .minutes:
            return Self.steps(from: now, every: 60, count: 90)
        default:
            return WidgetSnapshot.refreshDates(from: now, days: 7)
        }
    }

    /// now и следующие моменты, выровненные по началу минуты или часа.
    private static func steps(from now: Date, every interval: TimeInterval, count: Int) -> [Date] {
        let aligned = (now.timeIntervalSinceReferenceDate / interval).rounded(.down) * interval
        return [now] + (1...count).map { Date(timeIntervalSinceReferenceDate: aligned + Double($0) * interval) }
    }
}

/// Настройка виджета «Время жизни»: удерживай виджет → «Изменить виджет».
struct LifeWidgetIntent: WidgetConfigurationIntent {
    static let title: LocalizedStringResource = "Время жизни"
    static let description = IntentDescription("Выбери, в чём считать оставшееся время.")

    @Parameter(title: "Считать в", default: .weeks)
    var unit: LifeUnit

    init() {}

    init(unit: LifeUnit) {
        self.unit = unit
    }
}

/// Большое число оставшегося времени с подписью.
struct RemainingTimeView: View {
    let stats: LifeStats
    let unit: LifeUnit
    var size: CGFloat = 40

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            number
                .monospacedDigit()
                .lineLimit(1)
                .minimumScaleFactor(0.4)
                .widgetAccentable()
            Text(unit.caption(unit.value(stats)))
                .font(.caption2)
                .foregroundStyle(Color.white.opacity(0.6))
                .lineLimit(1)
                .minimumScaleFactor(0.8)
        }
    }

    @ViewBuilder
    private var number: some View {
        if unit == .seconds && !stats.isBeyondExpectancy {
            // Тикает сам, без обновления виджета: часы растут без ограничения.
            Text(timerInterval: stats.now...stats.deathDate, countsDown: true)
                .font(.system(size: size * 0.6, weight: .light))
        } else {
            Text(Format.number(unit.value(stats)))
                .font(.system(size: unit.value(stats) >= 1_000_000 ? size * 0.7 : size, weight: .thin))
        }
    }
}
