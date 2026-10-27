import Foundation

struct CounterPreset: Identifiable, Hashable {
    let title: String
    let emoji: String
    let perYear: Double
    let years: Int?

    var id: String { title }

    var hint: String {
        let perYearText = perYear.rounded() == perYear ? "\(Int(perYear))" : Format.decimal(perYear)
        var text = "\(perYearText) в год"
        if let years { text += ", \(Format.years(years))" }
        return text
    }

    func makeCounter() -> LifeCounter {
        LifeCounter(title: title, emoji: emoji, perYear: perYear, years: years)
    }
}

/// Готовые счётчики «Осталось» — в духе эссе Тима Урбана «The Tail End».
enum CounterPresets {
    static let all: [CounterPreset] = [
        CounterPreset(title: "встреч с родителями", emoji: "❤️", perYear: 5, years: 20),
        CounterPreset(title: "прочитанных книг", emoji: "📚", perYear: 12, years: nil),
        CounterPreset(title: "отпусков", emoji: "🏖️", perYear: 2, years: nil),
        CounterPreset(title: "встреч с друзьями детства", emoji: "🫂", perYear: 3, years: nil),
        CounterPreset(title: "походов в театр", emoji: "🎭", perYear: 4, years: nil),
        CounterPreset(title: "горнолыжных сезонов", emoji: "⛷️", perYear: 1, years: 30),
        CounterPreset(title: "летних отпусков с детьми", emoji: "🧒", perYear: 1, years: 15),
    ]

    /// Счётчики, которые появляются после онбординга.
    static var defaults: [LifeCounter] {
        all.filter { $0.title == "прочитанных книг" || $0.title == "отпусков" }.map { $0.makeCounter() }
    }
}
