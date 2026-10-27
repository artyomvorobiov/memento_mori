import Foundation

/// «Темп»: успеваешь ли ты закрыть свой список при текущей скорости.
struct PaceInsight {
    let activeCount: Int
    /// Сколько целей закрыто за период наблюдения.
    let completedRecently: Int
    /// Период наблюдения в днях (от 30 до 365).
    let observedDays: Int
    /// Фактический темп — целей в год (nil, если пока ничего не закрыто).
    let perYear: Double?
    /// Сколько лет уйдёт на весь список в текущем темпе.
    let yearsNeeded: Double?
    /// Возраст, к которому список будет закрыт в текущем темпе.
    let finishAge: Double?
    /// Успеваешь ли в текущем темпе.
    let fitsInLife: Bool?
    /// Сколько недель можно тратить на одну цель, чтобы успеть всё.
    let weeksPerGoalNeeded: Double?

    init(goals: [Goal], stats: LifeStats, trackingSince: Date) {
        let now = stats.now
        activeCount = goals.filter { !$0.isCompleted }.count

        let trackedDays = max(0, now.timeIntervalSince(trackingSince) / LifeStats.secondsPerDay)
        let window = min(365, max(30, trackedDays))
        observedDays = Int(window)
        let windowStart = now.addingTimeInterval(-window * LifeStats.secondsPerDay)
        completedRecently = goals.filter { goal in
            guard let date = goal.completedAt else { return false }
            return date >= windowStart && date <= now
        }.count

        if completedRecently > 0 {
            let rate = Double(completedRecently) / window * 365.2425
            let needed = Double(activeCount) / rate
            perYear = rate
            yearsNeeded = needed
            finishAge = stats.ageExact + needed
            fitsInLife = needed <= stats.remainingYears
        } else {
            perYear = nil
            yearsNeeded = nil
            finishAge = nil
            fitsInLife = nil
        }

        if activeCount > 0 && stats.remainingWeeks > 0 {
            weeksPerGoalNeeded = Double(stats.remainingWeeks) / Double(activeCount)
        } else {
            weeksPerGoalNeeded = nil
        }
    }

    /// Человекочитаемые выводы для экрана «Сегодня».
    var lines: [String] {
        guard activeCount > 0 else {
            return ["Список пуст. Добавь то, что хочешь успеть, — и здесь появится твой темп."]
        }
        var result: [String] = []

        if let weeks = weeksPerGoalNeeded {
            let goalsText = Format.goals(activeCount)
            if activeCount == 1 {
                result.append("Впереди одна цель — и примерно \(Format.weeks(Int(weeks))), чтобы её выполнить. Не откладывай.")
            } else if weeks >= 2 {
                let rounded = Int(weeks.rounded(.down))
                let interval = Format.count(rounded, "неделю", "недели", "недель")
                result.append("Впереди \(goalsText). Чтобы успеть всё, закрывай по одной каждые \(interval).")
            } else if weeks >= 1 {
                result.append("Впереди \(goalsText). Чтобы успеть всё, закрывай по одной каждую неделю.")
            } else {
                result.append("Впереди \(goalsText) — это по \(Format.decimal(1 / weeks)) в неделю. Может, оставить только главное?")
            }
        } else {
            result.append("Время по плану вышло, а впереди ещё \(Format.goals(activeCount)). Каждый день — подарок: начни с главного.")
        }

        if let perYear, let finishAge, let fitsInLife, let yearsNeeded {
            let pace: String
            let tenths = (perYear * 10).rounded() / 10
            if tenths >= 10 || tenths.rounded() == tenths {
                pace = Format.goals(Int(perYear.rounded()))
            } else {
                pace = "\(Format.decimal(tenths)) " + RussianPlural.form(tenths, "цель", "цели", "целей")
            }
            if fitsInLife {
                let age = Int(finishAge.rounded(.up))
                result.append("Твой темп — \(pace) в год. В этом темпе список будет закрыт \(Format.byAge(age)).")
            } else {
                let years = Int(yearsNeeded.rounded(.up))
                result.append("Твой темп — \(pace) в год. В этом темпе понадобится \(Format.years(years)) — больше, чем осталось. Ускорься или выбери главное.")
            }
        } else {
            result.append("Закрой первую цель — и здесь появится твой реальный темп.")
        }
        return result
    }
}
