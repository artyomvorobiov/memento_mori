package app.mementomori.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import app.mementomori.core.AppData
import app.mementomori.core.Country
import app.mementomori.core.DataStore
import app.mementomori.core.DataStoreException
import app.mementomori.core.DemoData
import app.mementomori.core.ExpectancyChoice
import app.mementomori.core.FutureLetter
import app.mementomori.core.Goal
import app.mementomori.core.GoalCategory
import app.mementomori.core.Idea
import app.mementomori.core.LifeCounter
import app.mementomori.core.LifeStats
import app.mementomori.core.Profile
import app.mementomori.core.CounterPresets
import app.mementomori.core.ReminderSettings
import app.mementomori.core.openedLetters
import app.mementomori.core.sealedLetters
import app.mementomori.core.waitingLetters
import app.mementomori.reminders.ReminderScheduler
import app.mementomori.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Изменение фото-воспоминания при сохранении. */
sealed interface PhotoChange {
    data object Keep : PhotoChange
    data object Remove : PhotoChange
    data class Replace(val uri: Uri) : PhotoChange
}

/**
 * Состояние приложения — единственный источник правды для экранов и виджетов.
 * Любое изменение сохраняется на диск, обновляет виджеты и расписание напоминаний.
 */
class AppModel(val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private var store = DataStore(File(context.filesDir, "Memento"))
    private var lastSeenModification = 0L

    val prefs = Prefs(context)

    private val _data = MutableStateFlow(AppData())
    val data: StateFlow<AppData> = _data.asStateFlow()

    /** Файл данных есть, но прочитать его сейчас нельзя — ничего не записываем. */
    private val _storageUnavailable = MutableStateFlow(false)
    val storageUnavailable: StateFlow<Boolean> = _storageUnavailable.asStateFlow()

    /** Сообщение о проблеме с данными, которое нужно показать пользователю. */
    val problem = MutableStateFlow<String?>(null)

    /** Демо-режим для скриншотов: данные во временной папке, настоящие не трогаются. */
    var isDemo = false
        private set

    init {
        load()
    }

    fun useDemo() {
        if (isDemo) return
        isDemo = true
        store = DataStore(File(context.cacheDir, "demo"))
        store.save(DemoData.make())
        load()
        scope.launch { WidgetUpdater.updateAll(context) }
    }

    // MARK: Чтение

    val profile: Profile? get() = _data.value.profile
    val photosDirectory: File get() = store.photosDirectory

    fun stats(at: Instant = Instant.now()): LifeStats? = _data.value.profile?.let { LifeStats(it, at) }

    /** Активные цели: сначала в фокусе, потом по ближайшему сроку, потом новые. */
    fun activeGoals(data: AppData = _data.value): List<Goal> {
        val birthDate = data.profile?.birthDate ?: Instant.now()
        return data.activeGoals.sortedWith { lhs, rhs ->
            if (lhs.isFocused != rhs.isFocused) return@sortedWith if (lhs.isFocused) -1 else 1
            val left = lhs.deadline(birthDate)
            val right = rhs.deadline(birthDate)
            when {
                left != null && right != null && left != right -> left.compareTo(right)
                left != null && right == null -> -1
                left == null && right != null -> 1
                else -> rhs.createdAt.compareTo(lhs.createdAt)
            }
        }
    }

    fun completedGoals(data: AppData = _data.value): List<Goal> = data.completedGoals.sortedByDescending { it.completedAt }

    fun focusedGoals(data: AppData = _data.value): List<Goal> = data.focusedGoals.sortedBy { it.createdAt }

    fun hasGoal(title: String): Boolean = _data.value.goals.any { it.title.trim().equals(title.trim(), ignoreCase = true) }

    fun photo(goal: Goal, maxSize: Int = 1600): Bitmap? = goal.photoFileName?.let { PhotoStore.load(it, photosDirectory, maxSize) }

    // MARK: Знакомство и профиль

    fun completeOnboarding(birthDate: LocalDate, expectancy: ExpectancyChoice, ideas: List<Idea>, remindersEnabled: Boolean) {
        update { data ->
            var result = data.copy(
                profile = Profile(
                    birthDate = birthDate.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                    lifeExpectancy = expectancy.years,
                    expectancySource = expectancy.source,
                    expectancyCountry = expectancy.country,
                    createdAt = Instant.now(),
                ),
                reminders = data.reminders.copy(isEnabled = remindersEnabled),
            )
            for (idea in ideas) result = result.upsert(Goal(title = idea.title, category = idea.category))
            if (result.counters.isEmpty()) result = result.copy(counters = CounterPresets.defaults)
            result
        }
    }

    fun updateProfile(birthDate: LocalDate, expectancy: ExpectancyChoice) {
        update { data ->
            val profile = data.profile ?: return@update data
            data.copy(
                profile = profile.copy(
                    birthDate = birthDate.atStartOfDay(ZoneId.systemDefault()).toInstant(),
                    lifeExpectancy = expectancy.years,
                    expectancySource = expectancy.source,
                    expectancyCountry = expectancy.country,
                ),
            )
        }
    }

    // MARK: Цели

    fun saveGoal(goal: Goal) = update { it.upsert(goal) }

    fun addIdea(idea: Idea) {
        if (!hasGoal(idea.title)) update { it.upsert(Goal(title = idea.title, category = idea.category)) }
    }

    fun deleteGoal(id: String) {
        val removed = _data.value.goal(id)
        update { it.removeGoal(id) }
        removed?.photoFileName?.let { PhotoStore.delete(it, photosDirectory) }
    }

    /** Отметить выполненной (или отредактировать воспоминание уже выполненной цели). */
    fun saveCompletion(goalId: String, date: Instant, reflection: String, photo: PhotoChange) {
        val goal = _data.value.goal(goalId) ?: return
        var photoName = goal.photoFileName
        when (photo) {
            PhotoChange.Keep -> Unit
            PhotoChange.Remove -> {
                goal.photoFileName?.let { PhotoStore.delete(it, photosDirectory) }
                photoName = null
            }
            is PhotoChange.Replace -> PhotoStore.save(context, photo.uri, photosDirectory)?.let { saved ->
                goal.photoFileName?.let { PhotoStore.delete(it, photosDirectory) }
                photoName = saved
            }
        }
        update { data ->
            val done = data.completeGoal(goalId, date, reflection)
            done.goal(goalId)?.let { done.replaceGoal(it.copy(photoFileName = photoName)) } ?: done
        }
    }

    fun toggleStep(goalId: String, stepId: String) = update { it.toggleStep(goalId, stepId) }

    fun reopenGoal(id: String) = update { it.reopenGoal(id) }

    /** false — в фокусе уже максимум целей. */
    fun toggleFocus(id: String): Boolean {
        val next = _data.value.toggleFocus(id) ?: return false
        update { next }
        return true
    }

    // MARK: Страны

    fun toggleWish(code: String) {
        val wish = _data.value.wishGoal(code)
        if (wish != null) {
            deleteGoal(wish.id)
        } else {
            update { it.upsert(Goal(title = Country.name(code), category = GoalCategory.TRAVEL, countryCode = code)) }
        }
    }

    fun toggleVisited(code: String) = update { data ->
        val wish = data.wishGoal(code)
        when {
            data.isVisited(code) -> data.markVisited(code, false)
            wish != null -> data.completeGoal(wish.id, Instant.now())
            else -> data.markVisited(code, true)
        }
    }

    /** Отметить посещёнными сразу несколько стран (например, найденных в фото). */
    fun markVisited(codes: Collection<String>) = update { data -> codes.fold(data) { acc, code -> acc.markVisited(code, true) } }

    // MARK: Счётчики

    fun saveCounter(counter: LifeCounter) = update { it.upsertCounter(counter) }

    fun deleteCounter(id: String) = update { it.removeCounter(id) }

    /** «Было сегодня». false — сегодня уже отмечено. */
    fun logCounterToday(id: String): Boolean {
        val next = _data.value.logCounterToday(id) ?: return false
        update { next }
        return true
    }

    fun logCounter(id: String, at: Instant) = update { it.logCounter(id, at) }

    fun removeCounterLog(id: String, date: Instant) = update { it.removeCounterLog(id, date) }

    // MARK: Письма в будущее

    fun waitingLetters(at: Instant = Instant.now()) = _data.value.waitingLetters(at)
    fun sealedLetters(at: Instant = Instant.now()) = _data.value.sealedLetters(at)
    val openedLetters: List<FutureLetter> get() = _data.value.openedLetters

    fun saveLetter(letter: FutureLetter) = update { it.upsertLetter(letter) }
    fun openLetter(id: String) = update { it.openLetter(id) }
    fun deleteLetter(id: String) = update { it.removeLetter(id) }

    // MARK: Напоминания

    fun updateReminders(settings: ReminderSettings) = update { it.copy(reminders = settings) }

    // MARK: Резервные копии

    fun exportJson(): String = DataStore.encode(_data.value)

    /** Бросает ошибку, если файл не похож на резервную копию. */
    fun parseBackup(text: String): AppData = DataStore.decode(text)

    fun replaceAll(newData: AppData) = update { newData }

    fun resetAll() {
        photosDirectory.deleteRecursively()
        update { AppData() }
    }

    // MARK: Жизненный цикл

    /** Приложение снова на экране: подхватываем изменения от виджетов, обновляем расписание и виджеты. */
    fun handleBecameActive() {
        if (_storageUnavailable.value || store.lastModified() != lastSeenModification) load()
        afterChange()
    }

    private fun load() {
        synchronized(lock) {
            try {
                _data.value = store.load() ?: AppData()
                _storageUnavailable.value = false
                lastSeenModification = store.lastModified()
            } catch (error: DataStoreException.Unreadable) {
                _storageUnavailable.value = true
            } catch (error: DataStoreException.Corrupted) {
                val moved = store.quarantine()
                _data.value = AppData()
                _storageUnavailable.value = false
                lastSeenModification = 0
                problem.value = if (moved != null) {
                    "Файл с данными повреждён, поэтому приложение начало с чистого листа. Старый файл сохранён как «${moved.name}»."
                } else {
                    "Файл с данными повреждён, поэтому приложение начало с чистого листа."
                }
            }
        }
    }

    /** Все изменения проходят здесь: запись на диск, затем виджеты и напоминания. */
    fun update(change: (AppData) -> AppData) {
        synchronized(lock) {
            val old = _data.value
            val new = change(old)
            if (new == old) return
            if (_storageUnavailable.value) return
            try {
                store.save(new)
                lastSeenModification = store.lastModified()
            } catch (error: Exception) {
                problem.value = "Не удалось сохранить изменения: ${error.localizedMessage}"
            }
            _data.value = new
        }
        afterChange()
    }

    private fun afterChange() {
        val snapshot = _data.value
        scope.launch {
            WidgetUpdater.updateAll(context)
            if (!isDemo) ReminderScheduler.reschedule(context, snapshot)
        }
    }
}
