package app.mementomori.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import app.mementomori.MementoApp
import app.mementomori.core.AppData
import app.mementomori.core.Continent
import app.mementomori.core.DeadlineText
import app.mementomori.core.Format
import app.mementomori.core.FutureLetter
import app.mementomori.core.Goal
import app.mementomori.core.LifeCounter
import app.mementomori.core.LifeStats
import app.mementomori.core.RussianPlural
import app.mementomori.core.TravelStats
import app.mementomori.core.capitalizedFirst
import app.mementomori.core.counterRemainingText
import app.mementomori.core.countersByUrgency
import app.mementomori.core.daysSinceLast
import app.mementomori.core.daysUntilLetter
import app.mementomori.core.flagEmoji
import app.mementomori.core.goalOfTheDay
import app.mementomori.core.isLogged
import app.mementomori.core.isOverdue
import app.mementomori.core.letterForWidget
import app.mementomori.data.MapBitmap
import java.time.Instant
import java.time.ZoneId

// MARK: «Цель дня»

private val goalIdKey = ActionParameters.Key<String>("goalId")
private val counterIdKey = ActionParameters.Key<String>("counterId")

/** Кнопка «Сделано» в виджете. */
class CompleteGoalAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[goalIdKey] ?: return
        MementoApp.model(context).update { it.completeGoal(id, Instant.now()) }
    }
}

/** Кнопка «Другая» — показать следующую цель. */
class ShuffleGoalAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val prefs = MementoApp.model(context).prefs
        prefs.goalShuffle = prefs.goalShuffle + 1
        GoalWidget().update(context, glanceId)
    }
}

/** Кнопка «Было сегодня» в виджете «Последний раз». */
class LogCounterAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[counterIdKey] ?: return
        MementoApp.model(context).logCounterToday(id)
    }
}

class GoalWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val model = MementoApp.model(context)
        provideContent {
            val data by model.data.collectAsState()
            GoalContent(data, model.prefs.goalShuffle)
        }
    }
}

class GoalWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = GoalWidget()
}

@Composable
private fun GoalContent(data: AppData, shuffle: Int) {
    val context = LocalContext.current
    if (data.profile == null) return WidgetEmpty(context, "ЦЕЛЬ ДНЯ")
    val goal = data.goalOfTheDay(Instant.now(), shuffle)
    val wide = LocalSize.current.width >= 250.dp
    WidgetFrame {
        if (goal == null) {
            Column(GlanceModifier.fillMaxSize().clickable(openApp(context))) {
                Caption("ЦЕЛЬ ДНЯ")
                Spacer(GlanceModifier.defaultWeight())
                Small("✦", WidgetColors.accent, 18.sp)
                Small("Список пуст. Добавь, что хочешь успеть, — время идёт.", WidgetColors.text, 12.sp, maxLines = 4)
            }
        } else Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth().clickable(openApp(context)), verticalAlignment = Alignment.CenterVertically) {
                Small(goal.countryCode?.let { flagEmoji(it) } ?: "✦", WidgetColors.accent, 14.sp)
                Spacer(GlanceModifier.width(6.dp))
                Caption(if (goal.isFocused) "В ФОКУСЕ" else "ЦЕЛЬ ДНЯ")
            }
            Spacer(GlanceModifier.height(6.dp))
            Column(GlanceModifier.defaultWeight().fillMaxWidth().clickable(openApp(context))) {
                SerifText(goal.title, if (wide) 17.sp else 15.sp, maxLines = if (wide) 2 else 4)
                detail(goal, data)?.let {
                    Spacer(GlanceModifier.height(4.dp))
                    Small(it, WidgetColors.secondary, 12.sp, maxLines = 2)
                }
            }
            Row(GlanceModifier.fillMaxWidth()) {
                WidgetButton(if (wide) "✓ Сделано" else "✓", true, actionRunCallback<CompleteGoalAction>(actionParametersOf(goalIdKey to goal.id)), GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(8.dp))
                WidgetButton(if (wide) "↻ Другая" else "↻", false, actionRunCallback<ShuffleGoalAction>(), GlanceModifier.defaultWeight())
            }
        }
    }
}

/** Следующий шаг, срок цели или её «зачем». */
private fun detail(goal: Goal, data: AppData): String? {
    goal.nextStep?.let { return "Дальше: ${it.title}" }
    DeadlineText.summary(goal, data.profile?.birthDate)?.let { return it.capitalizedFirst }
    return goal.note.ifEmpty { null }
}

// MARK: «Мой мир»

class TravelWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val model = MementoApp.model(context)
        provideContent {
            val data by model.data.collectAsState()
            TravelContent(data)
        }
    }
}

class TravelWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TravelWidget()
}

@Composable
private fun TravelContent(data: AppData) {
    val context = LocalContext.current
    if (data.profile == null) return WidgetEmpty(context, "МОЙ МИР")
    val travel = TravelStats(data)
    val size = LocalSize.current
    val large = size.height >= 250.dp
    WidgetFrame(GlanceModifier.clickable(openApp(context))) {
        Column(GlanceModifier.fillMaxSize()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Box(GlanceModifier.defaultWeight()) { Caption("МОЙ МИР") }
                if (!large) {
                    BigNumber("${travel.visitedCountries}", 24.sp)
                    Spacer(GlanceModifier.width(4.dp))
                    Small(RussianPlural.form(travel.visitedCountries, "страна", "страны", "стран") + " · " + Format.percent(travel.worldShare, 0), WidgetColors.secondary, 11.sp)
                }
            }
            Spacer(GlanceModifier.height(6.dp))
            val mapHeight = if (large) 140f else (size.height.value - 60).coerceAtLeast(60f)
            val bitmap = MapBitmap.render(context, travel.visited, travel.wished, ((size.width.value - 28) * 2).toInt().coerceAtLeast(200), (mapHeight * 2).toInt())
            Image(ImageProvider(bitmap), contentDescription = "Карта путешествий", modifier = GlanceModifier.fillMaxWidth().height(mapHeight.dp))
            if (large) {
                Spacer(GlanceModifier.height(10.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    BigNumber("${travel.visitedCountries}", 36.sp)
                    Spacer(GlanceModifier.width(6.dp))
                    Small(RussianPlural.form(travel.visitedCountries, "страна", "страны", "стран"), WidgetColors.text, 15.sp)
                    Spacer(GlanceModifier.defaultWeight())
                    Small("${Format.percent(travel.worldShare, 0)} мира", WidgetColors.accent, 15.sp)
                }
                Spacer(GlanceModifier.height(6.dp))
                Continent.entries.forEach { continent ->
                    val (visited, total) = travel.progress(continent)
                    Row(GlanceModifier.fillMaxWidth().padding(vertical = 1.dp)) {
                        Box(GlanceModifier.defaultWeight()) { Small(continent.title, if (visited > 0) WidgetColors.text else WidgetColors.tertiary, 12.sp) }
                        Small("$visited/$total", if (visited > 0) WidgetColors.text else WidgetColors.tertiary, 12.sp)
                    }
                }
            }
        }
    }
}

// MARK: «Последний раз»

class CountersWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val model = MementoApp.model(context)
        provideContent {
            val data by model.data.collectAsState()
            CountersContent(data)
        }
    }
}

class CountersWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CountersWidget()
}

@Composable
private fun CountersContent(data: AppData) {
    val context = LocalContext.current
    val profile = data.profile ?: return WidgetEmpty(context, "ПОСЛЕДНИЙ РАЗ")
    val now = Instant.now()
    val stats = LifeStats(profile, now)
    val counters = data.countersByUrgency(now)
    val wide = LocalSize.current.width >= 250.dp
    WidgetFrame {
        if (counters.isEmpty()) {
            Column(GlanceModifier.fillMaxSize().clickable(openApp(context))) {
                Caption("ПОСЛЕДНИЙ РАЗ")
                Spacer(GlanceModifier.defaultWeight())
                Small("❤️", WidgetColors.text, 18.sp)
                Small("Добавь счётчик на главном экране приложения — встречи с родителями, отпуска, книги — и отмечай, когда это было.", WidgetColors.text, 12.sp, maxLines = 5)
            }
        } else if (wide) {
            Column(GlanceModifier.fillMaxSize()) {
                Caption("ПОСЛЕДНИЙ РАЗ")
                Spacer(GlanceModifier.height(6.dp))
                counters.take(3).forEach { counter -> CounterRow(counter, stats, now) }
            }
        } else {
            val counter = counters.first()
            Column(GlanceModifier.fillMaxSize()) {
                Row(GlanceModifier.fillMaxWidth().clickable(openApp(context, "mementomori://counter/${counter.id}")), verticalAlignment = Alignment.CenterVertically) {
                    Small(counter.emoji, WidgetColors.text, 14.sp)
                    Spacer(GlanceModifier.width(6.dp))
                    Caption("ПОСЛЕДНИЙ РАЗ")
                }
                Spacer(GlanceModifier.defaultWeight())
                Column(GlanceModifier.clickable(openApp(context, "mementomori://counter/${counter.id}"))) {
                    DaysAgo(counter, now)
                    Small(counterRemainingText(counter, stats), WidgetColors.secondary, 11.sp, maxLines = 2)
                }
                Spacer(GlanceModifier.height(6.dp))
                LogButton(counter, now, compact = false, GlanceModifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun DaysAgo(counter: LifeCounter, now: Instant) {
    val days = counter.daysSinceLast(now)
    val color = if (counter.isOverdue(now)) WidgetColors.accent else WidgetColors.text
    when {
        days == null -> Small("ещё не было", WidgetColors.text, 18.sp)
        days > 1 -> {
            BigNumber(Format.number(days), 34.sp, color)
            Small(RussianPlural.form(days, "день назад", "дня назад", "дней назад"), WidgetColors.secondary, 11.sp)
        }
        else -> BigNumber(Format.daysAgo(days), 26.sp, color)
    }
}

@Composable
private fun CounterRow(counter: LifeCounter, stats: LifeStats, now: Instant) {
    val context = LocalContext.current
    Row(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Small(counter.emoji, WidgetColors.text, 17.sp)
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight().clickable(openApp(context, "mementomori://counter/${counter.id}"))) {
            Small(counterRemainingText(counter, stats), WidgetColors.text, 13.sp)
            val days = counter.daysSinceLast(now)
            Small(
                if (days == null) "ещё не отмечено" else "последний раз — " + Format.daysAgo(days),
                if (counter.isOverdue(now)) WidgetColors.accent else WidgetColors.secondary,
                11.sp,
            )
        }
        LogButton(counter, now, compact = true, GlanceModifier.width(44.dp))
    }
}

/** Кнопка «Было» или отметка, что сегодня уже было. */
@Composable
private fun LogButton(counter: LifeCounter, now: Instant, compact: Boolean, modifier: GlanceModifier) {
    if (counter.isLogged(now)) {
        Box(modifier.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
            Small(if (compact) "✓" else "✓ Сегодня", WidgetColors.secondary, 12.sp)
        }
    } else {
        WidgetButton(if (compact) "+" else "+ Было сегодня", true, actionRunCallback<LogCounterAction>(actionParametersOf(counterIdKey to counter.id)), modifier)
    }
}

// MARK: «Письмо себе»

class LetterWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val model = MementoApp.model(context)
        provideContent {
            val data by model.data.collectAsState()
            LetterContent(data)
        }
    }
}

class LetterWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LetterWidget()
}

@Composable
private fun LetterContent(data: AppData) {
    val context = LocalContext.current
    val profile = data.profile ?: return WidgetEmpty(context, "ПИСЬМО СЕБЕ")
    val now = Instant.now()
    val found: Pair<FutureLetter, Boolean>? = data.letterForWidget(now)
    val link = found?.let { "mementomori://letter/${it.first.id}" } ?: "mementomori://letters/new"
    WidgetFrame(GlanceModifier.clickable(openApp(context, link))) {
        Column(GlanceModifier.fillMaxSize()) {
            Caption("ПИСЬМО СЕБЕ")
            Spacer(GlanceModifier.defaultWeight())
            when {
                found == null -> {
                    Small("✉", WidgetColors.accent, 18.sp)
                    Small("Напиши письмо себе в будущее — оно откроется в выбранный день.", WidgetColors.text, 12.sp, maxLines = 4)
                }
                found.second -> {
                    Small("📬", WidgetColors.accent, 22.sp)
                    SerifText("Пришло письмо из ${found.first.createdAt.atZone(ZoneId.systemDefault()).year} года", 15.sp, maxLines = 3)
                    Small("Открой — оно ждёт", WidgetColors.secondary, 11.sp)
                }
                else -> {
                    val letter = found.first
                    val days = daysUntilLetter(letter, profile.birthDate, now)
                    Small("✉", WidgetColors.accent, 15.sp)
                    BigNumber(Format.number(days), 34.sp)
                    Small(RussianPlural.form(days, "день до письма", "дня до письма", "дней до письма"), WidgetColors.secondary, 11.sp)
                    Small(letter.openLabel(profile.birthDate).capitalizedFirst, WidgetColors.tertiary, 10.sp)
                }
            }
        }
    }
}
