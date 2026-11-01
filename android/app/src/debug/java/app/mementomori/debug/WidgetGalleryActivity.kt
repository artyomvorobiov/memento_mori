package app.mementomori.debug

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import app.mementomori.MementoApp
import app.mementomori.widget.CountersWidgetReceiver
import app.mementomori.widget.GoalWidgetReceiver
import app.mementomori.widget.LetterWidgetReceiver
import app.mementomori.widget.LifeWidgetReceiver
import app.mementomori.widget.TravelWidgetReceiver
import app.mementomori.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Галерея настоящих виджетов на демо-данных (только отладочная сборка).
 * Нужно разрешение показывать виджеты: adb shell appwidget grantbind --package <пакет>
 *
 *     adb shell am start -n <пакет>/app.mementomori.debug.WidgetGalleryActivity --ei page 1
 */
class WidgetGalleryActivity : Activity() {
    private lateinit var host: AppWidgetHost

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MementoApp.model(this).useDemo()
        host = AppWidgetHost(this, 4242)
        host.deleteHost()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(56), dp(16), dp(16))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0xFF2E3350.toInt(), 0xFF51384D.toInt()))
        }
        setContentView(column)

        val page = intent.getIntExtra("page", 1)
        val rows: List<List<Triple<Class<*>, Int, Int>>> = if (page == 1) {
            listOf(
                listOf(Triple(LifeWidgetReceiver::class.java, 170, 170), Triple(GoalWidgetReceiver::class.java, 170, 170)),
                listOf(Triple(LifeWidgetReceiver::class.java, 356, 170)),
                listOf(Triple(GoalWidgetReceiver::class.java, 356, 170)),
                listOf(Triple(TravelWidgetReceiver::class.java, 356, 170)),
            )
        } else {
            listOf(
                listOf(Triple(LifeWidgetReceiver::class.java, 356, 370)),
                listOf(Triple(CountersWidgetReceiver::class.java, 170, 170), Triple(LetterWidgetReceiver::class.java, 170, 170)),
                listOf(Triple(CountersWidgetReceiver::class.java, 356, 170)),
            )
        }
        val manager = AppWidgetManager.getInstance(this)
        for (row in rows) {
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            for ((receiver, width, height) in row) {
                val provider = ComponentName(this, receiver)
                val id = host.allocateAppWidgetId()
                val info = manager.installedProviders.firstOrNull { it.provider == provider }
                if (info == null || !manager.bindAppWidgetIdIfAllowed(id, provider)) {
                    line.addView(TextView(this).apply { text = "Нет разрешения: adb shell appwidget grantbind" })
                    continue
                }
                val view = host.createView(applicationContext, id, info)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    view.updateAppWidgetSize(Bundle(), listOf(SizeF(width.toFloat(), height.toFloat())))
                }
                line.addView(view, LinearLayout.LayoutParams(dp(width), dp(height)).apply { setMargins(dp(8), dp(8), dp(8), dp(8)) })
            }
            column.addView(line)
        }
        host.startListening()
        CoroutineScope(Dispatchers.Default).launch { WidgetUpdater.updateAll(applicationContext) }
    }

    override fun onDestroy() {
        host.stopListening()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()
}
