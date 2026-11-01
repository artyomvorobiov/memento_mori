package app.mementomori.ui.today

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Drafts
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mementomori.core.AppData
import app.mementomori.core.DeadlineText
import app.mementomori.core.Format
import app.mementomori.core.FutureLetter
import app.mementomori.core.LifeCounter
import app.mementomori.core.LifeStats
import app.mementomori.core.PaceInsight
import app.mementomori.core.Quotes
import app.mementomori.core.RussianPlural
import app.mementomori.core.daysSinceLast
import app.mementomori.core.isLogged
import app.mementomori.core.isOverdue
import app.mementomori.core.waitingLetters
import app.mementomori.debug.DebugLaunch
import app.mementomori.ui.LocalModel
import app.mementomori.ui.LocalNavigator
import app.mementomori.ui.Screen
import app.mementomori.ui.SectionHeader
import app.mementomori.ui.ThinProgressBar
import app.mementomori.ui.mementoCard
import app.mementomori.ui.theme.Palette
import kotlinx.coroutines.delay
import java.time.Instant

/** Текущее время, обновляется раз в минуту (или чаще). */
@Composable
fun rememberNow(periodMillis: Long = 60_000): Instant {
    val now by produceState(Instant.now(), periodMillis) {
        while (true) {
            delay(periodMillis - System.currentTimeMillis() % periodMillis)
            value = Instant.now()
        }
    }
    return now
}

@Composable
fun TodayScreen() {
    val model = LocalModel.current
    val navigator = LocalNavigator.current
    val data by model.data.collectAsStateWithLifecycle()
    val profile = data.profile ?: return
    val now = rememberNow()
    val stats = LifeStats(profile, now)
    val scroll = rememberScrollState()
    LaunchedEffect(Unit) {
        if (DebugLaunch.scrollToBottom) {
            delay(300)
            scroll.scrollTo(scroll.maxValue)
        }
        if (DebugLaunch.openFirstCounter) data.counters.firstOrNull()?.let { navigator.push(Screen.CounterDetail(it.id)) }
        if (DebugLaunch.openWaitingLetter) data.waitingLetters(now).firstOrNull()?.let { navigator.push(Screen.Letter(it.id)) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { navigator.push(Screen.LifeShare) }) {
                Icon(Icons.Outlined.Share, contentDescription = "Поделиться", tint = Palette.accent)
            }
            Text(
                "MEMENTO MORI",
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = Palette.secondaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.sp,
            )
            IconButton(onClick = { navigator.push(Screen.Settings) }) {
                Icon(Icons.Outlined.Settings, contentDescription = "Настройки", tint = Palette.accent)
            }
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 20.dp).padding(top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                HeroCountdown(stats)
                LifeProgressSummary(stats)
            }
            data.waitingLetters(now).firstOrNull()?.let { letter ->
                LetterArrivedCard(letter) { navigator.push(Screen.Letter(letter.id)) }
            }
            QuoteCard(now)
            FocusSection(data)
            RemainingSection(data, stats)
            PaceSection(data, stats)
        }
    }
}

// MARK: Главный счётчик

private enum class HeroUnit { WEEKS, DAYS, HOURS, SECONDS, YEARS, PERCENT }

/** Сколько осталось. Нажатие переключает единицы: недели → дни → часы → секунды → годы → проценты. */
@Composable
private fun HeroCountdown(stats: LifeStats) {
    val model = LocalModel.current
    val haptic = LocalHapticFeedback.current
    var unit by remember { mutableStateOf(runCatching { HeroUnit.valueOf(model.prefs.heroUnit.uppercase()) }.getOrDefault(HeroUnit.WEEKS)) }
    val tick = if (unit == HeroUnit.SECONDS) rememberNow(1000) else stats.now
    val live = stats.at(maxOf(tick, stats.now))
    val (number, caption) = display(unit, live)
    Column(
        Modifier.fillMaxWidth().clickable {
            unit = HeroUnit.entries[(unit.ordinal + 1) % HeroUnit.entries.size]
            model.prefs.heroUnit = unit.name.lowercase()
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        },
    ) {
        AnimatedContent(targetState = number, label = "hero") { value ->
            Text(value, fontSize = 64.sp, fontWeight = FontWeight.Thin, maxLines = 1, softWrap = false)
        }
        Text(caption, color = Palette.secondaryText, fontSize = 15.sp)
    }
}

private fun display(unit: HeroUnit, stats: LifeStats): Pair<String, String> {
    if (stats.isBeyondExpectancy) return "∞" to "каждый день сверх плана — подарок"
    return when (unit) {
        HeroUnit.WEEKS -> stats.remainingWeeks.let { Format.number(it) to RussianPlural.form(it, "неделя осталась", "недели осталось", "недель осталось") }
        HeroUnit.DAYS -> stats.remainingDays.let { Format.number(it) to RussianPlural.form(it, "день остался", "дня осталось", "дней осталось") }
        HeroUnit.HOURS -> stats.remainingHours.let { Format.number(it) to RussianPlural.form(it, "час остался", "часа осталось", "часов осталось") }
        HeroUnit.SECONDS -> stats.remainingSeconds.toLong().let {
            Format.number(it) to RussianPlural.form((it % 1000).toInt(), "секунда осталась", "секунды осталось", "секунд осталось")
        }
        HeroUnit.YEARS -> Format.decimal(stats.remainingYears) to "года осталось"
        HeroUnit.PERCENT -> Format.percent(stats.progress) to "жизни прожито"
    }
}

@Composable
private fun LifeProgressSummary(stats: LifeStats) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ThinProgressBar(stats.progress)
        Row(Modifier.fillMaxWidth()) {
            Text("${Format.percent(stats.progress)} прожито", color = Palette.secondaryText, fontSize = 12.sp, modifier = Modifier.weight(1f))
            Text("${stats.ageYears} из ${Format.genitive(stats.lifeExpectancy, "года", "лет")}", color = Palette.secondaryText, fontSize = 12.sp)
        }
    }
}

// MARK: Мысль дня

@Composable
private fun QuoteCard(date: Instant) {
    var offset by remember { mutableIntStateOf(0) }
    val quote = Quotes.ofTheDay(date, offset)
    Column(
        Modifier.clip(RoundedCornerShape(20.dp)).clickable { offset += 1 }.mementoCard(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("«${quote.text}»", fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 20.sp, lineHeight = 27.sp)
        Text("— ${quote.author}", color = Palette.secondaryText, fontSize = 13.sp)
    }
}

// MARK: Письмо

@Composable
fun LetterArrivedCard(letter: FutureLetter, onOpen: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .border(BorderStroke(1.dp, Palette.accent.copy(alpha = 0.5f)), RoundedCornerShape(20.dp))
            .clickable(onClick = onOpen)
            .mementoCard(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(Icons.Outlined.Drafts, contentDescription = null, tint = Palette.accent, modifier = Modifier.size(30.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Пришло письмо из прошлого", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Medium, fontSize = 17.sp)
            Text("Написано ${Format.date(letter.createdAt)} — открой, когда будет минутка.", color = Palette.secondaryText, fontSize = 12.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Palette.tertiaryText)
    }
}

// MARK: Фокус

@Composable
private fun FocusSection(data: AppData) {
    val model = LocalModel.current
    val navigator = LocalNavigator.current
    val goals = model.focusedGoals(data)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("В фокусе", if (goals.isEmpty()) null else "${goals.size} из 3")
        if (goals.isEmpty()) {
            Text(
                if (data.activeGoals.isEmpty()) "Добавь цели во вкладке «Цели» и отметь звёздочкой до трёх главных."
                else "Отметь звёздочкой до трёх главных целей — они будут здесь и на виджете.",
                color = Palette.secondaryText,
                fontSize = 15.sp,
                modifier = Modifier.mementoCard(),
            )
        } else {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Palette.card).padding(horizontal = 16.dp)) {
                goals.forEachIndexed { index, goal ->
                    if (index > 0) Box(Modifier.fillMaxWidth().padding(vertical = 0.dp).heightIn(min = 0.5.dp, max = 0.5.dp).background(Palette.cardStroke))
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { navigator.push(Screen.CompleteGoal(goal.id)) }) {
                            Icon(Icons.Outlined.RadioButtonUnchecked, contentDescription = "Отметить выполненной", tint = Palette.accent)
                        }
                        Column(Modifier.weight(1f).clickable { navigator.push(Screen.GoalDetail(goal.id)) }) {
                            Text(goal.title, fontSize = 16.sp)
                            goal.nextStep?.let { Text("Дальше: ${it.title}", color = Palette.accent, fontSize = 12.sp, maxLines = 1) }
                            DeadlineText.summary(goal, data.profile?.birthDate)?.let {
                                Text(it, color = Palette.secondaryText, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// MARK: Осталось примерно

@Composable
private fun RemainingSection(data: AppData, stats: LifeStats) {
    val model = LocalModel.current
    val navigator = LocalNavigator.current
    val haptic = LocalHapticFeedback.current
    val tiles = mutableListOf<@Composable (Modifier) -> Unit>()
    tiles += { m -> CounterTile("🌅", Format.number(stats.remainingDays), RussianPlural.form(stats.remainingDays, "рассвет", "рассвета", "рассветов"), m) }
    tiles += { m -> CounterTile("🌕", Format.number(stats.fullMoonsLeft), RussianPlural.form(stats.fullMoonsLeft, "полнолуние", "полнолуния", "полнолуний"), m) }
    tiles += { m -> CounterTile("🎄", Format.number(stats.newYearsLeft), RussianPlural.form(stats.newYearsLeft, "Новый год", "Новых года", "Новых годов"), m) }
    tiles += { m -> CounterTile("🫀", Format.compact(stats.heartbeatsLeft), "ударов сердца", m) }
    for (counter in data.counters) {
        tiles += { m ->
            LivingCounterTile(
                counter = counter,
                remaining = counter.remaining(stats.remainingYears),
                now = stats.now,
                modifier = m,
                onOpen = { navigator.push(Screen.CounterDetail(counter.id)) },
                onLog = { if (model.logCounterToday(counter.id)) haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
            )
        }
    }
    tiles += { m -> AddCounterTile(m) { navigator.push(Screen.CounterEditor()) } }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("Осталось примерно")
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { tile -> tile(Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CounterTile(emoji: String, value: String, caption: String, modifier: Modifier) {
    Column(
        modifier.heightIn(min = 112.dp).clip(RoundedCornerShape(18.dp)).background(Palette.card).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(emoji, fontSize = 20.sp)
        Text(value, fontSize = 28.sp, fontWeight = FontWeight.Light, maxLines = 1)
        Text(caption, color = Palette.secondaryText, fontSize = 12.sp, maxLines = 2)
    }
}

/** Свой счётчик: сколько раз осталось, когда было в последний раз и кнопка «Было». */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LivingCounterTile(
    counter: LifeCounter,
    remaining: Int,
    now: Instant,
    modifier: Modifier,
    onOpen: () -> Unit,
    onLog: () -> Unit,
) {
    val loggedToday = counter.isLogged(now)
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 112.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Palette.card)
                .combinedClickable(onClick = onOpen, onLongClick = { menu = true })
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(counter.emoji, fontSize = 20.sp)
            Text(Format.number(remaining), fontSize = 28.sp, fontWeight = FontWeight.Light, maxLines = 1)
            Text(counter.title, color = Palette.secondaryText, fontSize = 12.sp, maxLines = 2)
            val days = counter.daysSinceLast(now)
            Text(
                if (days == null) "нажми +, когда случится" else "последний раз — " + Format.daysAgo(days),
                color = if (counter.isOverdue(now)) Palette.accent else Palette.tertiaryText,
                fontSize = 11.sp,
                maxLines = 2,
            )
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(if (loggedToday) Palette.future else Palette.accent)
                .clickable(enabled = !loggedToday, onClick = onLog),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (loggedToday) Icons.Filled.Check else Icons.Filled.Add,
                contentDescription = if (loggedToday) "Сегодня уже отмечено" else "Было сегодня",
                tint = if (loggedToday) Color.White.copy(alpha = 0.7f) else Color.Black,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(text = { Text("Было сегодня") }, enabled = !loggedToday, onClick = { menu = false; onLog() })
            DropdownMenuItem(text = { Text("Подробнее") }, onClick = { menu = false; onOpen() })
        }
    }
}

@Composable
private fun AddCounterTile(modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .heightIn(min = 112.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.matchParentSize()) {
            drawRoundRect(
                color = Palette.future,
                cornerRadius = CornerRadius(18.dp.toPx()),
                style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = Palette.secondaryText)
            Text("Свой счётчик", color = Palette.secondaryText, fontSize = 12.sp)
        }
    }
}

// MARK: Темп

@Composable
private fun PaceSection(data: AppData, stats: LifeStats) {
    val insight = PaceInsight(data.goals, stats, data.profile?.createdAt ?: stats.now)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("Темп", "выполнено ${data.completedGoals.size} из ${data.goals.size}")
        Column(Modifier.mementoCard(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            insight.lines.forEach { Text(it, color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp) }
        }
    }
}
