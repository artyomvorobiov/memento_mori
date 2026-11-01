@file:UseSerializers(InstantSerializer::class)

package app.mementomori.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import java.time.Instant
import java.time.ZoneId

// Модель данных. Всё хранится в одном JSON-файле (см. DataStore) в том же формате, что и на iPhone.
// Все поля со значениями по умолчанию: старые резервные копии и данные будущих версий открываются без ошибок.

// MARK: Категории

@Serializable(with = GoalCategorySerializer::class)
enum class GoalCategory(val raw: String, val title: String) {
    TRAVEL("travel", "Путешествия"),
    EXPERIENCE("experience", "Впечатления"),
    MOVIES("movies", "Фильмы"),
    BOOKS("books", "Книги"),
    SKILLS("skills", "Навыки"),
    BODY("body", "Тело"),
    PEOPLE("people", "Люди"),
    CREATE("create", "Создать"),
    OTHER("other", "Другое"),
}

object GoalCategorySerializer : LenientEnumSerializer<GoalCategory>("GoalCategory", GoalCategory.entries, { it.raw }, GoalCategory.OTHER)

// MARK: Цель

/** Шаг на пути к цели: «Сделать визу», «Купить билеты». */
@Serializable
data class GoalStep(
    val id: String = newId(),
    val title: String = "",
    val isDone: Boolean = false,
)

@Serializable
data class Goal(
    val id: String = newId(),
    val title: String = "",
    val category: GoalCategory = GoalCategory.OTHER,
    /** «Зачем мне это» — мотивация или детали. */
    val note: String = "",
    /** Срок в виде возраста («к 35 годам»). Взаимоисключающе с targetDate. */
    val targetAge: Int? = null,
    /** Срок в виде конкретной даты. */
    val targetDate: Instant? = null,
    val isFocused: Boolean = false,
    val createdAt: Instant = Instant.now(),
    val completedAt: Instant? = null,
    /** Заметка-воспоминание после выполнения. */
    val reflection: String = "",
    /** Имя файла фото в папке Photos. */
    val photoFileName: String? = null,
    /** ISO-код страны для целей-путешествий. */
    val countryCode: String? = null,
    val steps: List<GoalStep> = emptyList(),
) {
    val isCompleted: Boolean get() = completedAt != null
    val completedStepsCount: Int get() = steps.count { it.isDone }

    /** Первый невыполненный шаг — «что делать дальше». */
    val nextStep: GoalStep? get() = steps.firstOrNull { !it.isDone }
    val hasDeadline: Boolean get() = targetAge != null || targetDate != null

    /** Дата дедлайна с учётом даты рождения (для сроков «к N годам»). */
    fun deadline(birthDate: Instant, zone: ZoneId = ZoneId.systemDefault()): Instant? {
        targetDate?.let { return it }
        targetAge?.let { return birthDate.atZone(zone).plusYears(it.toLong()).toInstant() }
        return null
    }
}

// MARK: Профиль

/** Откуда берётся ожидаемая продолжительность жизни. */
@Serializable(with = ExpectancySourceSerializer::class)
enum class ExpectancySource(val raw: String, val title: String) {
    /** Средняя по стране (данные Всемирного банка). */
    COUNTRY("country", "Средняя по стране"),

    /** Своё число. */
    CUSTOM("custom", "Своё число"),
}

object ExpectancySourceSerializer : LenientEnumSerializer<ExpectancySource>("ExpectancySource", ExpectancySource.entries, { it.raw }, ExpectancySource.CUSTOM)

@Serializable
data class Profile(
    val birthDate: Instant,
    /** Ожидаемая продолжительность жизни в годах — по ней считается всё остальное. */
    val lifeExpectancy: Int = DEFAULT_LIFE_EXPECTANCY,
    val expectancySource: ExpectancySource = ExpectancySource.CUSTOM,
    /** Страна для режима «средняя по стране» (ISO-код или 001 — весь мир). */
    val expectancyCountry: String? = null,
    /** Когда человек начал пользоваться приложением (нужно для расчёта темпа). */
    val createdAt: Instant = Instant.now(),
) {
    /** В режиме «средняя по стране» подставляет актуальное значение из данных. */
    fun withCountryAverage(): Profile {
        if (expectancySource != ExpectancySource.COUNTRY) return this
        val years = expectancyCountry?.let { averageLifeExpectancy(it) } ?: return this
        return if (years == lifeExpectancy) this else copy(lifeExpectancy = years)
    }

    companion object {
        const val DEFAULT_LIFE_EXPECTANCY = 80
        val LIFE_EXPECTANCY_RANGE = 30..120

        /** Средняя продолжительность жизни в стране, округлённая до лет. */
        fun averageLifeExpectancy(code: String): Int? = LifeExpectancyData.value(code)?.let { Math.round(it).toInt() }
    }
}

/** Выбор ожидаемой продолжительности жизни на экранах знакомства и настроек. */
data class ExpectancyChoice(
    val source: ExpectancySource,
    /** Страна для режима «средняя по стране» (ISO-код или 001 — весь мир). */
    val country: String,
    /** Своё число для режима «своё число». */
    val customYears: Int,
) {
    /** Сколько лет в итоге закладываем на жизнь. */
    val years: Int
        get() = if (source == ExpectancySource.COUNTRY) Profile.averageLifeExpectancy(country) ?: customYears else customYears

    /** Выбрать страну: своё число тоже подстраиваем, чтобы при переключении начинать от средней. */
    fun selecting(country: String): ExpectancyChoice =
        copy(country = country, customYears = Profile.averageLifeExpectancy(country) ?: customYears)

    companion object {
        /** Средняя по стране, где живёт человек (по региону устройства), или по миру. */
        fun suggested(regionCode: String? = java.util.Locale.getDefault().country): ExpectancyChoice {
            val country = regionCode?.takeIf { it.isNotBlank() && LifeExpectancyData.hasData(it) } ?: LifeExpectancyData.WORLD_CODE
            val average = Profile.averageLifeExpectancy(country) ?: Profile.DEFAULT_LIFE_EXPECTANCY
            return ExpectancyChoice(ExpectancySource.COUNTRY, country, average)
        }

        fun of(profile: Profile): ExpectancyChoice {
            val fallback = suggested()
            val country = profile.expectancyCountry?.takeIf { LifeExpectancyData.hasData(it) } ?: fallback.country
            return ExpectancyChoice(profile.expectancySource, country, profile.lifeExpectancy)
        }
    }
}

// MARK: Счётчик «Осталось»

/**
 * Пользовательский счётчик в духе «The Tail End»: сколько раз ещё случится то,
 * что происходит N раз в год (встречи с родителями, отпуска, книги).
 * Каждый раз можно отметить «Было» — тогда видно, когда это было в последний раз.
 */
@Serializable
data class LifeCounter(
    val id: String = newId(),
    val title: String = "",
    val emoji: String = "✦",
    val perYear: Double = 1.0,
    /** Сколько лет это ещё продлится. null — до конца жизни. */
    val years: Int? = null,
    /** Отметки «Было» по возрастанию даты. */
    val log: List<Instant> = emptyList(),
    /** Мягко напомнить, если перерыв станет заметно дольше обычного. */
    val remindsWhenOverdue: Boolean = true,
    val createdAt: Instant = log.minOrNull() ?: Instant.now(),
) {
    /** Сколько раз осталось, если жить осталось remainingYears лет. */
    fun remaining(remainingYears: Double, perYear: Double = this.perYear): Int {
        val span = minOf(years?.toDouble() ?: Double.MAX_VALUE, maxOf(0.0, remainingYears))
        return Math.floor(maxOf(0.0, perYear) * span).toInt()
    }
}

// MARK: Напоминания

@Serializable(with = FrequencySerializer::class)
enum class ReminderFrequency(val raw: String, val title: String) {
    DAILY("daily", "Каждый день"),
    WEEKLY("weekly", "Раз в неделю"),
}

object FrequencySerializer : LenientEnumSerializer<ReminderFrequency>("Frequency", ReminderFrequency.entries, { it.raw }, ReminderFrequency.DAILY)

@Serializable
data class ReminderSettings(
    val isEnabled: Boolean = false,
    val hour: Int = 9,
    val minute: Int = 0,
    val frequency: ReminderFrequency = ReminderFrequency.DAILY,
    /** День недели для еженедельных напоминаний: 1 — воскресенье, 2 — понедельник… (как на iPhone). */
    val weekday: Int = 2,
    val deadlineAlerts: Boolean = true,
)

// MARK: Все данные

@Serializable
data class AppData(
    val version: Int = CURRENT_VERSION,
    val profile: Profile? = null,
    val goals: List<Goal> = emptyList(),
    val counters: List<LifeCounter> = emptyList(),
    val visitedCountries: List<String> = emptyList(),
    val letters: List<FutureLetter> = emptyList(),
    val reminders: ReminderSettings = ReminderSettings(),
) {
    val activeGoals: List<Goal> get() = goals.filter { !it.isCompleted }
    val completedGoals: List<Goal> get() = goals.filter { it.isCompleted }
    val focusedGoals: List<Goal> get() = activeGoals.filter { it.isFocused }

    fun goal(id: String): Goal? = goals.firstOrNull { it.id == id }
    fun counter(id: String): LifeCounter? = counters.firstOrNull { it.id == id }
    fun letter(id: String): FutureLetter? = letters.firstOrNull { it.id == id }
    fun isVisited(code: String): Boolean = code in visitedCountries
    fun wishGoal(countryCode: String): Goal? = activeGoals.firstOrNull { it.countryCode == countryCode }

    /** Можно ли поставить в фокус ещё одну цель (не считая goalId). */
    fun canFocus(excluding: String? = null): Boolean = focusedGoals.count { it.id != excluding } < MAX_FOCUSED_GOALS

    // Операции возвращают новую копию данных: их одинаково используют приложение и виджеты.

    fun upsert(goal: Goal): AppData {
        var cleaned = goal.copy(
            title = goal.title.trim(),
            steps = goal.steps.map { it.copy(title = it.title.trim()) }.filter { it.title.isNotEmpty() },
        )
        if (cleaned.isFocused && (cleaned.isCompleted || !canFocus(excluding = cleaned.id))) {
            cleaned = cleaned.copy(isFocused = false)
        }
        val index = goals.indexOfFirst { it.id == cleaned.id }
        return copy(goals = if (index >= 0) goals.toMutableList().also { it[index] = cleaned } else goals + cleaned)
    }

    fun removeGoal(id: String): AppData = copy(goals = goals.filterNot { it.id == id })

    fun completeGoal(id: String, at: Instant = Instant.now(), reflection: String? = null, photoFileName: String? = null): AppData {
        val goal = goal(id) ?: return this
        val done = goal.copy(
            completedAt = at,
            isFocused = false,
            reflection = reflection ?: goal.reflection,
            photoFileName = photoFileName ?: goal.photoFileName,
        )
        var result = replaceGoal(done)
        done.countryCode?.let { result = result.markVisited(it, true) }
        return result
    }

    fun reopenGoal(id: String): AppData = goal(id)?.let { replaceGoal(it.copy(completedAt = null)) } ?: this

    fun toggleStep(goalId: String, stepId: String): AppData {
        val goal = goal(goalId) ?: return this
        return replaceGoal(goal.copy(steps = goal.steps.map { if (it.id == stepId) it.copy(isDone = !it.isDone) else it }))
    }

    /** Возвращает null, если поставить в фокус нельзя: в фокусе уже максимум целей. */
    fun toggleFocus(id: String): AppData? {
        val goal = goal(id) ?: return null
        if (goal.isFocused) return replaceGoal(goal.copy(isFocused = false))
        if (goal.isCompleted || !canFocus(excluding = id)) return null
        return replaceGoal(goal.copy(isFocused = true))
    }

    fun replaceGoal(goal: Goal): AppData = copy(goals = goals.map { if (it.id == goal.id) goal else it })

    // Страны

    fun markVisited(code: String, visited: Boolean): AppData = copy(
        visitedCountries = if (visited) (visitedCountries + code).distinct().sorted() else visitedCountries - code,
    )

    // Счётчики

    fun upsertCounter(counter: LifeCounter): AppData {
        val index = counters.indexOfFirst { it.id == counter.id }
        return copy(counters = if (index >= 0) counters.toMutableList().also { it[index] = counter } else counters + counter)
    }

    fun removeCounter(id: String): AppData = copy(counters = counters.filterNot { it.id == id })

    /** Отметить «Было» в указанный момент. */
    fun logCounter(id: String, at: Instant): AppData {
        val counter = counter(id) ?: return this
        return upsertCounter(counter.copy(log = (counter.log + at).sorted()))
    }

    /** Отметить «Было сегодня», если сегодня ещё не отмечено. null — сегодня уже было. */
    fun logCounterToday(id: String, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()): AppData? {
        val counter = counter(id) ?: return null
        if (counter.isLogged(now, zone)) return null
        return logCounter(id, now)
    }

    fun removeCounterLog(id: String, date: Instant): AppData {
        val counter = counter(id) ?: return this
        val index = counter.log.indexOf(date)
        if (index < 0) return this
        return upsertCounter(counter.copy(log = counter.log.toMutableList().also { it.removeAt(index) }))
    }

    // Письма

    fun upsertLetter(letter: FutureLetter): AppData {
        val cleaned = letter.copy(text = letter.text.trim())
        val index = letters.indexOfFirst { it.id == cleaned.id }
        return copy(letters = if (index >= 0) letters.toMutableList().also { it[index] = cleaned } else letters + cleaned)
    }

    fun openLetter(id: String, at: Instant = Instant.now()): AppData {
        val letter = letter(id) ?: return this
        if (letter.openedAt != null) return this
        return upsertLetter(letter.copy(openedAt = at))
    }

    fun removeLetter(id: String): AppData = copy(letters = letters.filterNot { it.id == id })

    /** После чтения: средняя по стране — из актуальных данных. */
    fun normalized(): AppData = copy(
        profile = profile?.withCountryAverage(),
        counters = counters.map { counter -> if (counter.log == counter.log.sorted()) counter else counter.copy(log = counter.log.sorted()) },
    )

    companion object {
        const val CURRENT_VERSION = 1
        const val MAX_FOCUSED_GOALS = 3
    }
}
