package app.mementomori.debug

import android.content.Intent
import app.mementomori.BuildConfig
import app.mementomori.ui.Screen

/**
 * Параметры запуска для отладки и скриншотов (работают только в отладочной сборке):
 *
 *     adb shell am start -n <пакет>/app.mementomori.MainActivity \
 *         --ez demo true --ei tab 2 --es screen photoImport
 *
 * demo — демонстрационные данные во временной папке (настоящие не трогаются);
 * tab — вкладка: 0 «Сегодня», 1 «Цели», 2 «Мир», 3 «Жизнь»;
 * screen — сразу открыть экран: counter, letter, newLetter, photoImport, newGoal, settings;
 * title — текст для screen=newGoal; scrollToBottom — открыть «Сегодня» и «Жизнь» прокрученными вниз;
 * lifeMode — weeks или years.
 */
data class DebugLaunch(
    val demo: Boolean = false,
    val tab: Int? = null,
    val screen: Screen? = null,
) {
    companion object {
        var scrollToBottom = false
            private set
        var lifeMode: String? = null
            private set
        var openWaitingLetter = false
            private set
        var openFirstCounter = false
            private set

        fun from(intent: Intent?): DebugLaunch {
            if (!BuildConfig.DEBUG || intent == null) return DebugLaunch()
            scrollToBottom = intent.getBooleanExtra("scrollToBottom", false)
            lifeMode = intent.getStringExtra("lifeMode")
            val screenName = intent.getStringExtra("screen")
            openWaitingLetter = screenName == "letter"
            openFirstCounter = screenName == "counter"
            val screen = when (screenName) {
                "newLetter" -> Screen.NewLetter
                "photoImport" -> Screen.PhotoImport(demo = true)
                "newGoal" -> Screen.GoalEditor(title = intent.getStringExtra("title") ?: "")
                "settings" -> Screen.Settings
                else -> null
            }
            return DebugLaunch(
                demo = intent.getBooleanExtra("demo", false),
                tab = if (intent.hasExtra("tab")) intent.getIntExtra("tab", 0) else null,
                screen = screen,
            )
        }
    }
}
