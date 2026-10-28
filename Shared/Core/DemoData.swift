import Foundation

/// Демонстрационные данные: для скриншотов, превью виджетов и знакомства с приложением.
/// В отладочной сборке включаются аргументом запуска `-MementoDemo`.
enum DemoData {
    static let launchArgument = "-MementoDemo"

    static func make(now: Date = Date(), calendar: Calendar = .memento) -> AppData {
        func daysAgo(_ days: Int) -> Date {
            calendar.date(byAdding: .day, value: -days, to: now) ?? now
        }
        func daysAhead(_ days: Int) -> Date {
            calendar.date(byAdding: .day, value: days, to: now) ?? now
        }

        var data = AppData()
        let birthDate = calendar.date(byAdding: DateComponents(year: -34, month: -3, day: -11), to: calendar.startOfDay(for: now)) ?? now
        data.profile = Profile(birthDate: birthDate, lifeExpectancy: 82, createdAt: daysAgo(210))

        data.goals = [
            Goal(title: "Увидеть северное сияние", category: .travel, note: "Мечтаю об этом с детства — пора перестать мечтать и поехать.", targetAge: 36, isFocused: true, createdAt: daysAgo(200),
                 steps: [GoalStep(title: "Выбрать: Мурманск или Норвегия", isDone: true), GoalStep(title: "Купить билеты на февраль"), GoalStep(title: "Тёплые ботинки")]),
            Goal(title: "Выучить испанский до разговорного уровня", category: .skills, targetDate: daysAhead(320), isFocused: true, createdAt: daysAgo(190),
                 steps: [GoalStep(title: "Найти преподавателя", isDone: true), GoalStep(title: "Заниматься 3 раза в неделю", isDone: true), GoalStep(title: "Посмотреть сериал без субтитров")]),
            Goal(title: "Пробежать марафон", category: .body, note: "Доказать себе, что могу.", targetAge: 40, isFocused: true, createdAt: daysAgo(180)),
            Goal(title: "Япония", category: .travel, createdAt: daysAgo(170), countryCode: "JP"),
            Goal(title: "Исландия", category: .travel, createdAt: daysAgo(160), countryCode: "IS"),
            Goal(title: "«Сталкер» — Андрей Тарковский", category: .movies, createdAt: daysAgo(150)),
            Goal(title: "«Смерть Ивана Ильича» — Лев Толстой", category: .books, createdAt: daysAgo(140)),
            Goal(title: "Записать историю жизни бабушки", category: .people, note: "Пока есть кому рассказывать.", targetDate: daysAhead(60), createdAt: daysAgo(130)),
            Goal(title: "Написать книгу", category: .create, targetAge: 45, createdAt: daysAgo(120)),
            Goal(title: "Прыгнуть с парашютом", category: .experience, createdAt: daysAgo(110)),
            Goal(title: "Встретить рассвет в горах", category: .experience, createdAt: daysAgo(205), completedAt: daysAgo(38), reflection: "Эльбрус, 5:12 утра. Холодно и абсолютно счастливо."),
            Goal(title: "Грузия", category: .travel, createdAt: daysAgo(205), completedAt: daysAgo(115), reflection: "Хачапури, горы и самые добрые люди.", countryCode: "GE"),
            Goal(title: "«Мастер и Маргарита» — Михаил Булгаков", category: .books, createdAt: daysAgo(205), completedAt: daysAgo(170)),
            Goal(title: "Научиться плавать кролем", category: .skills, createdAt: daysAgo(500), completedAt: daysAgo(420)),
            Goal(title: "Посадить дерево", category: .create, createdAt: daysAgo(900), completedAt: daysAgo(760)),
        ]

        data.visitedCountries = ["AE", "AM", "DE", "ES", "FR", "GE", "IT", "KZ", "ME", "RU", "TH", "TR"]
        data.counters = [
            LifeCounter(title: "встреч с родителями", emoji: "❤️", perYear: 5, years: 20,
                        log: [400, 330, 250, 178, 104].map(daysAgo), createdAt: daysAgo(410)),
            LifeCounter(title: "прочитанных книг", emoji: "📚", perYear: 12,
                        log: [201, 178, 150, 120, 95, 66, 40, 12].map(daysAgo), createdAt: daysAgo(210)),
            LifeCounter(title: "отпусков", emoji: "🏖️", perYear: 2,
                        log: [300, 115, 38].map(daysAgo), createdAt: daysAgo(310)),
        ]
        data.letters = [
            FutureLetter(
                text: "Привет! Если ты это читаешь — прошёл ровно год. Надеюсь, горы уже случились, а испанский больше не откладывается на понедельник. И позвони родителям — прямо сейчас.",
                createdAt: daysAgo(365), openDate: now
            ),
            FutureLetter(
                text: "Сейчас мне 34. Хочется, чтобы к сорока книга была написана, а утро всё так же начиналось с радости.",
                createdAt: daysAgo(30), openAge: 40
            ),
            FutureLetter(
                text: "Только что вернулись с Эльбруса. Запомни это чувство: холод, рассвет и абсолютное счастье.",
                createdAt: daysAgo(5), openDate: daysAhead(214)
            ),
            FutureLetter(
                text: "Год назад было страшно менять работу. Как оно там?",
                createdAt: daysAgo(400), openDate: daysAgo(35), openedAt: daysAgo(35)
            ),
        ]
        return data
    }
}
