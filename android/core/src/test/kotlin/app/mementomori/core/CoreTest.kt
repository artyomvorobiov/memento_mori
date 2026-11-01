package app.mementomori.core

import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CoreTest {
    private val zone: ZoneId = ZoneId.of("Europe/Moscow")

    private fun date(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0): Instant =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant()

    private val base = date(2026, 10, 27)
    private fun daysBefore(days: Long): Instant = base.atZone(zone).minusDays(days).toInstant()

    private fun resource(name: String): String =
        requireNotNull(javaClass.classLoader.getResource(name)) { "нет ресурса $name" }.readText()

    @Test
    fun plurals() {
        assertEquals("неделя", RussianPlural.form(1, "неделя", "недели", "недель"))
        assertEquals("недели", RussianPlural.form(2, "неделя", "недели", "недель"))
        assertEquals("недель", RussianPlural.form(11, "неделя", "недели", "недель"))
        assertEquals("неделя", RussianPlural.form(21, "неделя", "недели", "недель"))
        assertEquals("недель", RussianPlural.form(112, "неделя", "недели", "недель"))
        assertEquals("года", RussianPlural.form(1.5, "год", "года", "лет"))
        assertEquals("из 82 лет", "из " + Format.genitive(82, "года", "лет"))
        assertEquals("из 81 года", "из " + Format.genitive(81, "года", "лет"))
        assertEquals("к 21 году", Format.byAge(21))
        assertEquals("к 35 годам", Format.byAge(35))
        assertEquals("в 25 лет", Format.atAge(25))
    }

    @Test
    fun numbers() {
        assertEquals("2 490", Format.number(2490).replace(' ', ' '))
        assertEquals("38,2%", Format.percent(0.382))
        assertEquals("1,8 млрд", Format.compact(1_800_000_000.0))
        assertEquals("раз в 2 месяца", Format.everyInterval(73))
        assertEquals("раз в неделю", Format.everyInterval(7))
        assertEquals("раз в месяц", Format.everyInterval(30))
        assertEquals("раз в полгода", Format.everyInterval(183))
        assertEquals("раз в 2 года", Format.everyInterval(730))
        assertEquals("104 дня назад", Format.daysAgo(104))
        assertEquals("больше 2 лет назад", Format.daysAgo(800))
        assertEquals("2,5 раза", Format.perYear(2.5))
        assertEquals("ни разу", Format.perYear(0.0))
        assertEquals("К 40 годам", Format.byAge(40).capitalizedFirst)
    }

    @Test
    fun relativeDates() {
        assertEquals("через 3 дня", Format.relative(base, base.plusSeconds(3 * 86_400 + 60), zone))
        assertEquals("через 2 недели", Format.relative(base, base.plusSeconds(20 * 86_400), zone))
        assertEquals("через 3 месяца", Format.relative(base, date(2027, 2, 10), zone))
        assertEquals("через 3 года", Format.relative(base, date(2029, 11, 1), zone))
        assertEquals("срок прошёл", Format.relative(base, base.minusSeconds(10), zone))
        assertEquals("сегодня", Format.relative(base, base.plusSeconds(3600), zone))
        assertEquals("завтра", Format.relative(date(2026, 10, 28, 23, 30), date(2026, 10, 29, 0, 30), zone))
        assertEquals("через год", Format.relative(date(2026, 10, 28, 9, 41), date(2027, 10, 28, 0), zone))
        assertEquals("через 16 месяцев", Format.relative(base, date(2028, 3, 1), zone))
    }

    @Test
    fun lifeStatsWeeksStartOnMonday() {
        val birth = LocalDateTime.of(1992, 7, 16, 0, 0).atZone(zone).toInstant()
        val stats = LifeStats(birth, 82, base, zone)
        assertEquals(34, stats.ageYears)
        // Неделя сменяется в ночь на понедельник: воскресенье и понедельник — разные недели.
        val sunday = LifeStats(birth, 82, date(2026, 11, 1, 23, 59), zone)
        val monday = LifeStats(birth, 82, date(2026, 11, 2, 0, 1), zone)
        assertEquals(sunday.remainingWeeks - 1, monday.remainingWeeks)
        val tuesday = LifeStats(birth, 82, date(2026, 11, 3), zone)
        assertEquals(monday.remainingWeeks, tuesday.remainingWeeks)
        // Клетки сетки: недели с понедельника по воскресенье.
        val index = stats.gridIndex(base)!!
        val (start, end) = stats.dateRange(index)!!
        assertEquals(java.time.DayOfWeek.MONDAY, start.atZone(zone).dayOfWeek)
        assertEquals(java.time.DayOfWeek.SUNDAY, end.atZone(zone).dayOfWeek)
        assertTrue(!base.isBefore(start) && !base.isAfter(end))
        assertEquals(index, stats.currentGridIndex)
        assertNull(stats.gridIndex(birth.minusSeconds(60)))
        assertEquals(0, stats.gridIndex(birth))
        assertTrue(stats.remainingWeeks in 2480..2500, "weeks ${stats.remainingWeeks}")
        assertEquals("пн, 26 окт. — вс, 1 нояб. 2026 г.", Format.weekRange(start, end, zone))
    }

    @Test
    fun iosBackupOpensOnAndroid() {
        val data = DataStore.decode(resource("ios-backup.json"))
        assertEquals(15, data.goals.size)
        assertEquals(12, data.visitedCountries.size)
        assertEquals(4, data.letters.size)
        assertEquals(3, data.counters.size)
        assertEquals(5, data.counters[0].log.size)
        assertEquals(ReminderFrequency.WEEKLY, data.reminders.frequency)
        assertEquals(6, data.reminders.weekday)
        assertEquals(ExpectancySource.COUNTRY, data.profile?.expectancySource)
        assertEquals(Profile.averageLifeExpectancy("RU"), data.profile?.lifeExpectancy)
        val northernLights = data.goals.first { it.title == "Увидеть северное сияние" }
        assertEquals(3, northernLights.steps.size)
        assertEquals("Купить билеты на февраль", northernLights.nextStep?.title)
        assertEquals(GoalCategory.TRAVEL, northernLights.category)
        assertTrue(northernLights.isFocused)
        // Обратно — тот же формат: даты без долей секунды, те же ключи.
        val encoded = DataStore.encode(data)
        assertTrue(encoded.contains("\"isFocused\""))
        assertFalse(Regex("\\d{2}:\\d{2}:\\d{2}\\.\\d+Z").containsMatchIn(encoded), "даты без долей секунды")
        assertEquals(data, DataStore.decode(encoded))
    }

    @Test
    fun tolerantDecoding() {
        val data = DataStore.decode("""{"goals":[{"title":"Новая","category":"future-category","unknown":1}],"reminders":{"frequency":"hourly"}}""")
        assertEquals(GoalCategory.OTHER, data.goals[0].category)
        assertEquals(ReminderFrequency.DAILY, data.reminders.frequency)
        assertEquals(0, data.letters.size)
        assertEquals(1, data.version)
    }

    @Test
    fun goalsAndFocus() {
        var data = AppData()
        repeat(4) { data = data.upsert(Goal(title = "  Цель $it  ", isFocused = true, steps = listOf(GoalStep(title = " "), GoalStep(title = "Шаг")))) }
        assertEquals(3, data.focusedGoals.size, "не больше трёх в фокусе")
        assertEquals("Цель 0", data.goals[0].title)
        assertEquals(1, data.goals[0].steps.size, "пустые шаги отбрасываются")
        val unfocused = data.goals.first { !it.isFocused }
        assertNull(data.toggleFocus(unfocused.id))
        val japan = Goal(title = "Япония", category = GoalCategory.TRAVEL, countryCode = "JP")
        data = data.upsert(japan).completeGoal(japan.id, base)
        assertTrue(data.isVisited("JP"))
        assertEquals(base, data.goal(japan.id)?.completedAt)
        data = data.reopenGoal(japan.id)
        assertFalse(data.goal(japan.id)!!.isCompleted)
    }

    @Test
    fun livingCounters() {
        val parents = LifeCounter(
            title = "встреч с родителями", emoji = "❤️", perYear = 5.0, years = 20,
            log = listOf(400L, 330L, 250L, 178L, 104L).map(::daysBefore).sorted(), createdAt = daysBefore(410),
        )
        assertEquals(104, parents.daysSinceLast(base, zone))
        assertEquals(73, parents.usualIntervalDays)
        assertTrue(parents.isOverdue(base))
        val pace = parents.actualPerYear(base)!!
        assertTrue(kotlin.math.abs(pace - 4.0) < 0.6, "pace $pace")
        assertEquals(3, parents.countInYearOf(base, zone))
        val nudge = parents.nextOverdueReminder(base, 9, 0, zone)!!
        val nudgeDays = daysBetween(base, nudge, zone)
        assertTrue(nudgeDays in 58..62, "nudge in $nudgeDays days")
        assertEquals(9, nudge.atZone(zone).hour)
        assertNull(parents.copy(remindsWhenOverdue = false).nextOverdueReminder(base, 9, 0, zone))
        assertNull(LifeCounter(title = "прогулок", perYear = 300.0, log = listOf(daysBefore(30))).nextOverdueReminder(base, 9, 0, zone))

        var data = AppData(profile = Profile(birthDate = date(1992, 7, 16, 0), lifeExpectancy = 82), counters = listOf(parents))
        data = data.logCounterToday(parents.id, base, zone)!!
        assertNull(data.logCounterToday(parents.id, base.plusSeconds(3600), zone), "один раз в день")
        assertEquals(0, data.counters[0].daysSinceLast(base, zone))
        data = data.logCounter(parents.id, daysBefore(500))
        assertEquals(daysBefore(500), data.counters[0].log.first())
        data = data.removeCounterLog(parents.id, daysBefore(500))
        assertEquals(6, data.counters[0].log.size)
        assertEquals("≈ 100 встреч с родителями", counterRemainingText(parents, LifeStats(data.profile!!, base, zone)))
    }

    @Test
    fun letters() {
        val birth = date(1992, 7, 16, 0)
        val byAge = FutureLetter(text = "Привет", createdAt = base, openAge = 40)
        assertEquals(startOfDay(date(2032, 7, 16), zone), byAge.unlockDate(birth, zone))
        assertEquals("к 40 годам", byAge.openLabel(birth, zone))
        assertEquals(34, byAge.ageWhenWritten(birth, zone))
        val byDate = FutureLetter(text = "Первая строка\nвторая", createdAt = base, openDate = date(2027, 3, 14, 18))
        assertEquals("Первая строка", byDate.preview)
        assertFalse(byDate.isUnlocked(date(2027, 3, 13, 23), birth, zone))
        assertTrue(byDate.isWaiting(date(2027, 3, 14, 0, 1), birth, zone))

        var data = AppData(profile = Profile(birthDate = birth, lifeExpectancy = 82))
        val far = FutureLetter(text = "  Через десять лет  ", createdAt = base, openDate = date(2036, 10, 27))
        data = data.upsertLetter(far).upsertLetter(byDate)
        assertEquals("Через десять лет", data.letters[0].text)
        assertEquals(listOf(byDate.id, far.id), data.sealedLetters(base, zone).map { it.id })
        assertEquals(listOf(byDate.id), data.waitingLetters(date(2027, 3, 15), zone).map { it.id })
        data = data.openLetter(byDate.id, date(2027, 3, 15)).openLetter(byDate.id, date(2027, 4, 1))
        assertEquals(date(2027, 3, 15), data.letter(byDate.id)?.openedAt)
        assertEquals(listOf(byDate.id), data.openedLetters.map { it.id })
    }

    @Test
    fun planner() {
        val birth = date(1992, 7, 16, 0)
        val parents = LifeCounter(title = "встреч с родителями", emoji = "❤️", perYear = 5.0, years = 20, log = listOf(daysBefore(104)), createdAt = daysBefore(410))
        val far = FutureLetter(text = "далеко", createdAt = base, openDate = date(2036, 10, 27))
        var data = AppData(
            profile = Profile(birthDate = birth, lifeExpectancy = 82),
            reminders = ReminderSettings(isEnabled = false, hour = 10),
            letters = listOf(far, FutureLetter(text = "прочитано", createdAt = daysBefore(400), openDate = daysBefore(35), openedAt = daysBefore(35))),
            counters = listOf(parents),
        )
        val quiet = ReminderPlanner.plan(data, base, zone)
        assertEquals(
            setOf(ReminderPlanner.counterReminderId(parents.id), ReminderPlanner.letterReminderId(far.id)),
            quiet.map { it.id }.toSet(),
        )
        val letter = quiet.first { it.id.startsWith("memento.letter.") }
        assertEquals(2036, letter.date.atZone(zone).year)
        assertEquals(10, letter.date.atZone(zone).hour)
        assertTrue(quiet.first { it.id.startsWith("memento.counter.") }.body.contains("встреч с родителями"))

        data = data.copy(reminders = data.reminders.copy(isEnabled = true))
        val busy = ReminderPlanner.plan(data, base, zone)
        assertTrue(busy.size <= ReminderPlanner.MAX_COUNT)
        assertTrue(busy.any { it.id == letter.id }, "далёкое письмо остаётся в расписании")
        assertEquals(busy.sortedBy { it.date }, busy)
        assertTrue(busy.count { it.id.startsWith("memento.daily.") } >= 29)

        data = data.copy(reminders = data.reminders.copy(frequency = ReminderFrequency.WEEKLY, weekday = 2))
        val weekly = ReminderPlanner.plan(data, base, zone).filter { it.id.startsWith("memento.weekly.") }
        assertEquals(ReminderPlanner.WEEKLY_HORIZON_WEEKS, weekly.size)
        assertTrue(weekly.all { it.date.atZone(zone).dayOfWeek == java.time.DayOfWeek.MONDAY })
        assertEquals(java.time.DayOfWeek.SUNDAY, ReminderPlanner.appleWeekdayToDayOfWeek(1))
        assertEquals(0, ReminderPlanner.plan(AppData(), base, zone).size)
    }

    @Test
    fun suggestions() {
        assertEquals(GoalCategory.BOOKS, GoalSuggestions.category("Прочитать «Идиот»"))
        assertEquals(GoalCategory.CREATE, GoalSuggestions.category("Снять фильм о бабушке"))
        assertEquals(GoalCategory.TRAVEL, GoalSuggestions.category("Япония"))
        assertEquals("JP", GoalSuggestions.country("Поехать в Японию"))
        assertEquals("FR", GoalSuggestions.country("Побывать во Франции весной"))
        assertEquals("US", GoalSuggestions.country("Слетать в США"))
        assertEquals("NZ", GoalSuggestions.country("Увидеть Новую Зеландию"))
        assertNull(GoalSuggestions.country("Испечь хлеб"))
        assertEquals("Увидеть цветение сакуры в Японии", GoalSuggestions.ideas("Поехать в Японию").firstOrNull()?.title)
        assertEquals(listOf("Пробежать марафон", "Пробежать полумарафон"), GoalSuggestions.ideas("марафон").map { it.title })
        assertTrue(GoalSuggestions.ideas("хочу увидеть").isEmpty())
    }

    @Test
    fun travel() {
        val travel = TravelStats(listOf("JP", "DE", "IT", "HK"), listOf("IS", "JP"))
        assertEquals(3, travel.visitedCountries)
        assertEquals(1, travel.visitedTerritories)
        assertEquals(195, travel.totalCountries)
        assertEquals(1, travel.wishedCount)
        assertEquals(5 to 2, travel.nextMilestone)
        assertEquals(CountryStatus.WISHED, travel.status("IS"))
        assertEquals(195, Continent.entries.sumOf { travel.progress(it).second })
        assertTrue(CountryCatalog.all.all { Continent.of(it.code) != null })
        assertEquals("Япония", Country.name("JP"))
        assertEquals("🇯🇵", flagEmoji("JP"))
        assertEquals(207, LifeExpectancyData.byCountry.size)
    }

    @Test
    fun countryLocator() {
        val locator = CountryLocator.parse(resource("country-lookup.json"))
        assertEquals(210, locator.countryCount)
        val places = listOf(
            Triple(41.8902, 12.4922, "IT"), Triple(41.9022, 12.4539, "VA"), Triple(46.2044, 6.1432, "CH"),
            Triple(46.1934, 6.2342, "FR"), Triple(43.7384, 7.4246, "MC"), Triple(-29.3151, 27.4869, "LS"),
            Triple(35.6762, 139.6503, "JP"), Triple(64.7337, 177.4968, "RU"), Triple(-16.85, -179.95, "FJ"),
            Triple(-21.1151, 55.5364, "RE"), Triple(41.3780, 2.2000, "ES"), Triple(60.0973, 19.9348, "FI"),
        )
        for ((lat, lon, code) in places) assertEquals(code, locator.country(lat, lon), "$lat, $lon")
        assertNull(locator.country(30.0, -40.0))

        val tally = PhotoCountryTally()
        tally.add(41.8902, 12.4922, date(2019, 5, 1))
        tally.add(41.8903, 12.4921, date(2024, 8, 3), count = 2)
        tally.add(48.8584, 2.2945, date(2019, 5, 1))
        tally.add(0.0, 0.0, base)
        assertEquals(4, tally.geotagged)
        val hits = tally.countries(locator)
        assertEquals(listOf("IT", "FR"), hits.map { it.code })
        assertEquals(3, hits[0].photos)
        assertEquals("2019–2024", hits[0].yearsText(zone))
    }

    @Test
    fun worldMap() {
        val map = WorldMapData.parse(resource("world-map.json"))
        assertEquals(2000f, map.width)
        assertTrue(map.countries.size > 190)
        val russia = map.bounds("RU")!!
        val (x, y) = russia.centerX to russia.centerY
        assertEquals("RU", map.country(x, y, 1f))
        assertNotNull(map.dots["MC"])
        assertEquals("MC", map.country(map.dots["MC"]!!.first, map.dots["MC"]!!.second, 2f))
    }

    @Test
    fun widgetData() {
        val demo = DemoData.make(base, zone)
        assertEquals(1, demo.waitingLetters(base, zone).size)
        assertEquals(2, demo.sealedLetters(base, zone).size)
        assertEquals("встреч с родителями", demo.countersByUrgency(base).first().title)
        assertEquals(true, demo.letterForWidget(base, zone)?.second)
        val goal = demo.goalOfTheDay(base, 0, zone)
        assertTrue(goal?.isFocused == true)
        assertTrue(demo.goalOfTheDay(base, 1, zone) != goal)
        val stats = LifeStats(demo.profile!!, base, zone)
        assertEquals(34, stats.ageYears)
        assertTrue(PaceInsight(demo.goals, stats, demo.profile!!.createdAt).lines.size == 2)
        assertEquals("недель впереди", LifeUnit.WEEKS.caption(2490))
        assertEquals(LifeUnit.WEEKS, LifeUnit.fromRaw("nonsense"))
        assertTrue(Quotes.all.size == 25 && IdeaLibrary.all.size == 87 && IdeaLibrary.starter.size == 12)
    }
}
