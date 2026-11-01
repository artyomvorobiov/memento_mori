package app.mementomori.data

import android.content.Context
import android.content.SharedPreferences
import app.mementomori.core.LifeUnit

/** Мелкие настройки интерфейса: последняя единица главного счётчика, вид сетки, единица виджета. */
class Prefs(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("memento", Context.MODE_PRIVATE)

    var heroUnit: String
        get() = prefs.getString("heroUnit", "weeks") ?: "weeks"
        set(value) = prefs.edit().putString("heroUnit", value).apply()

    var lifeCalendarMode: String
        get() = prefs.getString("lifeCalendarMode", "weeks") ?: "weeks"
        set(value) = prefs.edit().putString("lifeCalendarMode", value).apply()

    /** В чём считать время в виджете «Время жизни». */
    var widgetUnit: LifeUnit
        get() = LifeUnit.fromRaw(prefs.getString("widgetUnit", null))
        set(value) = prefs.edit().putString("widgetUnit", value.raw).apply()

    /** Сдвиг очереди «Цели дня» — кнопка «Другая» в виджете. */
    var goalShuffle: Int
        get() = prefs.getInt("goalShuffle", 0)
        set(value) = prefs.edit().putInt("goalShuffle", value).apply()

    /** Сколько будильников-напоминаний запланировано — чтобы снять старые. */
    var scheduledReminders: Int
        get() = prefs.getInt("scheduledReminders", 0)
        set(value) = prefs.edit().putInt("scheduledReminders", value).apply()

    var selectedTab: Int
        get() = prefs.getInt("selectedTab", 0)
        set(value) = prefs.edit().putInt("selectedTab", value).apply()
}
