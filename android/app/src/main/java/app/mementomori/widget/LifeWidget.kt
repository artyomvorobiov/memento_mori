package app.mementomori.widget

import android.content.Context
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import app.mementomori.MementoApp
import app.mementomori.R
import app.mementomori.core.AppData
import app.mementomori.core.Format
import app.mementomori.core.LifeStats
import app.mementomori.core.LifeUnit
import app.mementomori.core.Quotes
import app.mementomori.data.YearsBitmap
import java.time.Instant

/** «Время жизни»: сколько времени впереди — в выбранных единицах, вплоть до живых секунд. */
class LifeWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val model = MementoApp.model(context)
        provideContent {
            val data by model.data.collectAsState()
            LifeContent(data, model.prefs.widgetUnit)
        }
    }
}

class LifeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LifeWidget()
}

@Composable
private fun LifeContent(data: AppData, unit: LifeUnit) {
    val context = LocalContext.current
    val profile = data.profile ?: return WidgetEmpty(context)
    val stats = LifeStats(profile, Instant.now())
    val size = LocalSize.current
    WidgetFrame(GlanceModifier.clickable(openApp(context))) {
        when {
            size.height >= 250.dp && size.width >= 250.dp -> LargeLife(stats, data, unit)
            size.width >= 250.dp -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Box(GlanceModifier.width(130.dp).fillMaxHeight()) { SmallLife(stats, unit) }
                Spacer(GlanceModifier.width(14.dp))
                Box(GlanceModifier.width(0.5.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.12f))) {}
                Spacer(GlanceModifier.width(14.dp))
                val quote = Quotes.ofTheDay()
                Column(GlanceModifier.defaultWeight()) {
                    SerifText("«${quote.text}»", 13.sp, maxLines = 5, italic = true)
                    Spacer(GlanceModifier.height(6.dp))
                    Small(quote.author, WidgetColors.tertiary, 10.sp)
                }
            }
            else -> SmallLife(stats, unit)
        }
    }
}

@Composable
private fun SmallLife(stats: LifeStats, unit: LifeUnit) {
    Column(GlanceModifier.fillMaxSize()) {
        Caption("MEMENTO MORI")
        Spacer(GlanceModifier.defaultWeight())
        Remaining(stats, unit, 38)
        Spacer(GlanceModifier.height(8.dp))
        ProgressLine(stats.progress.toFloat(), 120.dp)
        Spacer(GlanceModifier.height(4.dp))
        Small("${Format.percent(stats.progress)} прожито", WidgetColors.tertiary, 10.sp)
    }
}

/** Число с подписью; секунды — живым таймером, который тикает без обновления виджета. */
@Composable
private fun Remaining(stats: LifeStats, unit: LifeUnit, size: Int) {
    val context = LocalContext.current
    if (unit == LifeUnit.SECONDS && !stats.isBeyondExpectancy) {
        val base = SystemClock.elapsedRealtime() + (stats.deathDate.toEpochMilli() - System.currentTimeMillis())
        val views = RemoteViews(context.packageName, R.layout.widget_chronometer).apply {
            setChronometer(R.id.countdown, base, null, true)
            setChronometerCountDown(R.id.countdown, true)
        }
        AndroidRemoteViews(views)
    } else {
        val value = if (stats.isBeyondExpectancy) "∞" else Format.number(unit.value(stats))
        BigNumber(value, (if (value.length > 9) size * 0.7 else size.toDouble()).sp)
    }
    Small(unit.caption(unit.value(stats)), WidgetColors.secondary, 11.sp)
}

@Composable
private fun LargeLife(stats: LifeStats, data: AppData, unit: LifeUnit) {
    val size = LocalSize.current
    val focus = data.focusedGoals.sortedBy { it.createdAt }.take(3)
    Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(GlanceModifier.defaultWeight()) {
                Caption("MEMENTO MORI")
                Remaining(stats, unit, 32)
            }
            Column(horizontalAlignment = Alignment.End) {
                BigNumber(Format.percent(stats.progress), 20.sp)
                Small("прожито", WidgetColors.secondary, 11.sp)
            }
        }
        Spacer(GlanceModifier.height(10.dp))
        val gridHeight = (size.height.value - 215).coerceAtLeast(80f)
        val bitmap = YearsBitmap.render(stats, ((size.width.value - 28) * 2).toInt().coerceAtLeast(100), (gridHeight * 2).toInt())
        Image(ImageProvider(bitmap), contentDescription = "Жизнь в годах", modifier = GlanceModifier.fillMaxWidth().height(gridHeight.dp))
        Spacer(GlanceModifier.height(10.dp))
        if (focus.isEmpty()) {
            Small("Выполнено целей: ${data.completedGoals.size} · впереди: ${data.activeGoals.size}", WidgetColors.secondary, 12.sp)
        } else {
            focus.forEach { goal ->
                Row(GlanceModifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Small("★", WidgetColors.accent, 10.sp)
                    Spacer(GlanceModifier.width(6.dp))
                    Small(goal.title, WidgetColors.text, 12.sp)
                }
            }
        }
    }
}
