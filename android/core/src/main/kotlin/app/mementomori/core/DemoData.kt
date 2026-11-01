package app.mementomori.core

import java.time.Instant
import java.time.ZoneId

/** Демонстрационные данные: для скриншотов и знакомства с приложением (как на iPhone). */
object DemoData {
    fun make(now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): AppData {
        val today = now.atZone(zone)
        fun daysAgo(days: Long): Instant = today.minusDays(days).toInstant()
        fun daysAhead(days: Long): Instant = today.plusDays(days).toInstant()

        val birthDate = today.toLocalDate().minusYears(34).minusMonths(3).minusDays(11).atStartOfDay(zone).toInstant()
        val profile = Profile(birthDate = birthDate, lifeExpectancy = 82, createdAt = daysAgo(210))

        val goals = listOf(
            Goal(
                title = "Увидеть северное сияние", category = GoalCategory.TRAVEL,
                note = "Мечтаю об этом с детства — пора перестать мечтать и поехать.", targetAge = 36, isFocused = true,
                createdAt = daysAgo(200),
                steps = listOf(GoalStep(title = "Выбрать: Мурманск или Норвегия", isDone = true), GoalStep(title = "Купить билеты на февраль"), GoalStep(title = "Тёплые ботинки")),
            ),
            Goal(
                title = "Выучить испанский до разговорного уровня", category = GoalCategory.SKILLS,
                targetDate = daysAhead(320), isFocused = true, createdAt = daysAgo(190),
                steps = listOf(GoalStep(title = "Найти преподавателя", isDone = true), GoalStep(title = "Заниматься 3 раза в неделю", isDone = true), GoalStep(title = "Посмотреть сериал без субтитров")),
            ),
            Goal(title = "Пробежать марафон", category = GoalCategory.BODY, note = "Доказать себе, что могу.", targetAge = 40, isFocused = true, createdAt = daysAgo(180)),
            Goal(title = "Япония", category = GoalCategory.TRAVEL, createdAt = daysAgo(170), countryCode = "JP"),
            Goal(title = "Исландия", category = GoalCategory.TRAVEL, createdAt = daysAgo(160), countryCode = "IS"),
            Goal(title = "«Сталкер» — Андрей Тарковский", category = GoalCategory.MOVIES, createdAt = daysAgo(150)),
            Goal(title = "«Смерть Ивана Ильича» — Лев Толстой", category = GoalCategory.BOOKS, createdAt = daysAgo(140)),
            Goal(title = "Записать историю жизни бабушки", category = GoalCategory.PEOPLE, note = "Пока есть кому рассказывать.", targetDate = daysAhead(60), createdAt = daysAgo(130)),
            Goal(title = "Написать книгу", category = GoalCategory.CREATE, targetAge = 45, createdAt = daysAgo(120)),
            Goal(title = "Прыгнуть с парашютом", category = GoalCategory.EXPERIENCE, createdAt = daysAgo(110)),
            Goal(title = "Встретить рассвет в горах", category = GoalCategory.EXPERIENCE, createdAt = daysAgo(205), completedAt = daysAgo(38), reflection = "Эльбрус, 5:12 утра. Холодно и абсолютно счастливо."),
            Goal(title = "Грузия", category = GoalCategory.TRAVEL, createdAt = daysAgo(205), completedAt = daysAgo(115), reflection = "Хачапури, горы и самые добрые люди.", countryCode = "GE"),
            Goal(title = "«Мастер и Маргарита» — Михаил Булгаков", category = GoalCategory.BOOKS, createdAt = daysAgo(205), completedAt = daysAgo(170)),
            Goal(title = "Научиться плавать кролем", category = GoalCategory.SKILLS, createdAt = daysAgo(500), completedAt = daysAgo(420)),
            Goal(title = "Посадить дерево", category = GoalCategory.CREATE, createdAt = daysAgo(900), completedAt = daysAgo(760)),
        )

        val counters = listOf(
            LifeCounter(title = "встреч с родителями", emoji = "❤️", perYear = 5.0, years = 20, log = listOf(400L, 330L, 250L, 178L, 104L).map(::daysAgo).sorted(), createdAt = daysAgo(410)),
            LifeCounter(title = "прочитанных книг", emoji = "📚", perYear = 12.0, log = listOf(201L, 178L, 150L, 120L, 95L, 66L, 40L, 12L).map(::daysAgo).sorted(), createdAt = daysAgo(210)),
            LifeCounter(title = "отпусков", emoji = "🏖️", perYear = 2.0, log = listOf(300L, 115L, 38L).map(::daysAgo).sorted(), createdAt = daysAgo(310)),
        )

        val letters = listOf(
            FutureLetter(
                text = "Привет! Если ты это читаешь — прошёл ровно год. Надеюсь, горы уже случились, а испанский больше не откладывается на понедельник. И позвони родителям — прямо сейчас.",
                createdAt = daysAgo(365), openDate = now,
            ),
            FutureLetter(text = "Сейчас мне 34. Хочется, чтобы к сорока книга была написана, а утро всё так же начиналось с радости.", createdAt = daysAgo(30), openAge = 40),
            FutureLetter(text = "Только что вернулись с Эльбруса. Запомни это чувство: холод, рассвет и абсолютное счастье.", createdAt = daysAgo(5), openDate = daysAhead(214)),
            FutureLetter(text = "Год назад было страшно менять работу. Как оно там?", createdAt = daysAgo(400), openDate = daysAgo(35), openedAt = daysAgo(35)),
        )

        return AppData(
            profile = profile,
            goals = goals,
            counters = counters,
            visitedCountries = listOf("AE", "AM", "DE", "ES", "FR", "GE", "IT", "KZ", "ME", "RU", "TH", "TR"),
            letters = letters,
        )
    }
}
