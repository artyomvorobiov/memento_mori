package app.mementomori.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.glance.layout.padding
import androidx.glance.text.FontFamily
import androidx.glance.text.FontStyle
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.mementomori.MainActivity

/** Цвета виджетов — те же, что в приложении. */
object WidgetColors {
    val accent = Color(0xFFF2A541)
    val text = Color.White
    val secondary = Color.White.copy(alpha = 0.6f)
    val tertiary = Color.White.copy(alpha = 0.45f)
    val future = Color.White.copy(alpha = 0.14f)
}

fun provider(color: Color) = ColorProvider(color)

/** Чёрный фон и стандартные отступы. */
@Composable
fun WidgetFrame(modifier: GlanceModifier = GlanceModifier, content: @Composable () -> Unit) {
    Box(
        GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .background(Color.Black)
            .cornerRadius(22.dp)
            .padding(14.dp)
            .then(modifier),
        contentAlignment = Alignment.TopStart,
    ) {
        content()
    }
}

@Composable
fun Caption(text: String) {
    Text(text, style = TextStyle(color = provider(WidgetColors.tertiary), fontSize = 10.sp, fontWeight = FontWeight.Medium), maxLines = 1)
}

@Composable
fun BigNumber(text: String, size: TextUnit = 38.sp, color: Color = WidgetColors.text) {
    Text(text, style = TextStyle(color = provider(color), fontSize = size, fontFamily = FontFamily("sans-serif-light")), maxLines = 1)
}

@Composable
fun Small(text: String, color: Color = WidgetColors.secondary, size: TextUnit = 11.sp, maxLines: Int = 1) {
    Text(text, style = TextStyle(color = provider(color), fontSize = size), maxLines = maxLines)
}

@Composable
fun SerifText(text: String, size: TextUnit = 14.sp, maxLines: Int = 4, italic: Boolean = false, color: Color = WidgetColors.text) {
    Text(
        text,
        style = TextStyle(color = provider(color), fontSize = size, fontFamily = FontFamily.Serif, fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal),
        maxLines = maxLines,
    )
}

/** Капсула-кнопка внутри виджета. */
@Composable
fun WidgetButton(title: String, prominent: Boolean, action: Action, modifier: GlanceModifier = GlanceModifier) {
    Box(
        modifier
            .background(if (prominent) WidgetColors.accent else Color.White.copy(alpha = 0.12f))
            .cornerRadius(18.dp)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            style = TextStyle(color = provider(if (prominent) Color.Black else Color.White), fontSize = 12.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
    }
}

/** Открыть приложение, при необходимости — по ссылке (письмо, счётчик). */
fun openApp(context: Context, link: String? = null): Action =
    actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            link?.let { data = Uri.parse(it) }
        },
    )

/** Пусто: знакомство ещё не пройдено. */
@Composable
fun WidgetEmpty(context: Context, caption: String = "MEMENTO MORI") {
    WidgetFrame(GlanceModifier.clickable(openApp(context))) {
        Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.Bottom) {
            Caption(caption)
            Box(GlanceModifier.defaultWeight()) {}
            Small("Открой приложение, чтобы начать отсчёт", WidgetColors.text, 12.sp, maxLines = 3)
        }
    }
}

/** Горизонтальная полоска прогресса из двух прямоугольников. */
@Composable
fun ProgressLine(progress: Float, width: Dp, height: Dp = 3.dp, fill: Color = Color.White.copy(alpha = 0.85f)) {
    androidx.glance.layout.Row(GlanceModifier.fillMaxWidth()) {
        val filled = (width.value * progress.coerceIn(0f, 1f)).coerceAtLeast(height.value)
        Box(GlanceModifier.width(filled.dp).height(height).background(fill).cornerRadius(2.dp)) {}
        Box(GlanceModifier.defaultWeight().height(height).background(WidgetColors.future).cornerRadius(2.dp)) {}
    }
}

/** Обновить все виджеты (после любого изменения данных). */
object WidgetUpdater {
    suspend fun updateAll(context: Context) {
        runCatching {
            LifeWidget().updateAll(context)
            GoalWidget().updateAll(context)
            TravelWidget().updateAll(context)
            CountersWidget().updateAll(context)
            LetterWidget().updateAll(context)
        }
    }
}
