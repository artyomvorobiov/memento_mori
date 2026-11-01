package app.mementomori.ui.life

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Drafts
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mementomori.core.AppData
import app.mementomori.core.Format
import app.mementomori.core.FutureLetter
import app.mementomori.core.Goal
import app.mementomori.core.LifeStats
import app.mementomori.data.YearsBitmap
import app.mementomori.debug.DebugLaunch
import app.mementomori.ui.LocalModel
import app.mementomori.ui.LocalNavigator
import app.mementomori.ui.Screen
import app.mementomori.ui.Segmented
import app.mementomori.ui.theme.Palette
import app.mementomori.ui.today.rememberNow
import kotlinx.coroutines.delay

/** Отметки в сетке: выполненные цели, сроки, письма в будущее. */
private class WeekMarkers(val completed: Set<Int>, val deadlines: Set<Int>, val letters: Set<Int>)

@Composable
fun LifeScreen() {
    val model = LocalModel.current
    val navigator = LocalNavigator.current
    val data by model.data.collectAsStateWithLifecycle()
    val profile = data.profile ?: return
    val now = rememberNow()
    val stats = LifeStats(profile, now)
    var mode by remember { mutableStateOf(DebugLaunch.lifeMode ?: model.prefs.lifeCalendarMode) }
    var selected by remember { mutableStateOf<Int?>(null) }
    val scroll = rememberScrollState()
    LaunchedEffect(Unit) {
        if (DebugLaunch.scrollToBottom) {
            delay(300)
            scroll.scrollTo(scroll.maxValue)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("Жизнь", fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Segmented(listOf("weeks" to "Недели", "years" to "Годы"), mode) {
                mode = it
                model.prefs.lifeCalendarMode = it
                selected = null
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (mode == "weeks") {
                    Text("Прожито ${Format.number(stats.livedWeeks)} из ${Format.genitive(stats.totalWeeks, "недели", "недель")}", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Text("Каждая клетка — неделя, строка — год жизни. Нажми на клетку, чтобы увидеть её даты, цели и письма.", color = Palette.secondaryText, fontSize = 13.sp)
                } else {
                    Text("Прожито ${Format.years(stats.ageYears)} из ${stats.lifeExpectancy}", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Text("Каждая точка — год. Текущий заполняется по мере того, как проходит.", color = Palette.secondaryText, fontSize = 13.sp)
                }
            }
            if (mode == "weeks") {
                WeeksGrid(stats, markers(data, stats), selected) { selected = if (selected == it) null else it }
            } else {
                YearsGrid(stats)
            }
            Legend(mode == "weeks", data.letters.isNotEmpty())
            LettersSection(data, now)
            Spacer(Modifier.height(if (selected != null) 180.dp else 0.dp))
        }
        AnimatedVisibility(
            visible = selected != null && mode == "weeks",
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val index = selected ?: 0
            WeekInfoCard(stats, index, goalsIn(data, stats, index), lettersIn(data, stats, index), onOpenLetter = { navigator.push(Screen.Letter(it)) }) {
                selected = null
            }
        }
    }
}

private fun markers(data: AppData, stats: LifeStats): WeekMarkers {
    val completed = mutableSetOf<Int>()
    val deadlines = mutableSetOf<Int>()
    for (goal in data.goals) {
        val done = goal.completedAt
        if (done != null) {
            stats.gridIndex(done)?.let { completed += it }
        } else {
            goal.deadline(stats.birthDate, stats.zone)?.let { deadline -> stats.gridIndex(deadline)?.let { deadlines += it } }
        }
    }
    val letters = data.letters.mapNotNull { stats.gridIndex(it.unlockDate(stats.birthDate, stats.zone)) }.toSet()
    return WeekMarkers(completed, deadlines, letters)
}

private fun goalsIn(data: AppData, stats: LifeStats, index: Int): List<Goal> {
    val (start, end) = stats.dateRange(index) ?: return emptyList()
    return data.goals.filter { goal ->
        val date = goal.completedAt ?: goal.deadline(stats.birthDate, stats.zone) ?: return@filter false
        !date.isBefore(start) && !date.isAfter(end)
    }
}

private fun lettersIn(data: AppData, stats: LifeStats, index: Int): List<FutureLetter> {
    val (start, end) = stats.dateRange(index) ?: return emptyList()
    return data.letters.filter { val date = it.unlockDate(stats.birthDate, stats.zone); !date.isBefore(start) && !date.isAfter(end) }
}

// MARK: Сетка недель

private const val COLUMNS = LifeStats.WEEKS_PER_ROW
private val LABEL_WIDTH = 22.dp
private const val GAP_RATIO = 0.3f
private val DECADE_GAP = 6.dp

@Composable
private fun WeeksGrid(stats: LifeStats, markers: WeekMarkers, selected: Int?, onSelect: (Int) -> Unit) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val pulse = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulse.animateFloat(1f, 3f, infiniteRepeatable(tween(1800), RepeatMode.Restart), label = "scale")
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = maxWidth
        val rows = stats.gridRows
        val labelPx = with(density) { LABEL_WIDTH.toPx() }
        val decadePx = with(density) { DECADE_GAP.toPx() }
        val widthPx = with(density) { width.toPx() }
        val cell = (widthPx - labelPx) / (COLUMNS + (COLUMNS - 1) * GAP_RATIO)
        val gap = cell * GAP_RATIO
        fun rowY(row: Int) = row * (cell + gap) + (row / 10) * decadePx
        fun columnX(column: Int) = labelPx + column * (cell + gap)
        val heightPx = rowY(rows - 1) + cell
        val height: Dp = with(density) { heightPx.toDp() }
        val lived = stats.livedGridCells
        val current = stats.currentGridIndex
        Box(Modifier.fillMaxWidth().height(height)) {
            Canvas(
                Modifier.matchParentSize().pointerInput(rows, cell) {
                    detectTapGestures { point ->
                        val column = ((point.x - labelPx + gap / 2) / (cell + gap)).toInt()
                        if (point.x < labelPx - gap || column !in 0 until COLUMNS) return@detectTapGestures
                        var row = (point.y / (cell + gap)).toInt().coerceIn(0, rows - 1)
                        while (row > 0 && rowY(row) > point.y) row--
                        while (row < rows - 1 && rowY(row + 1) <= point.y) row++
                        val index = row * COLUMNS + column
                        if (index < stats.gridCellCount) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(index)
                        }
                    }
                },
            ) {
                val radius = CornerRadius(cell * 0.28f)
                val ring = Stroke(1.dp.toPx())
                for (index in 0 until stats.gridCellCount) {
                    val x = columnX(index % COLUMNS)
                    val y = rowY(index / COLUMNS)
                    val color = when {
                        index in markers.completed || index == current -> Palette.accent
                        index < lived -> Palette.lived
                        else -> Palette.future
                    }
                    drawRoundRect(color, Offset(x, y), Size(cell, cell), radius)
                    if (index in markers.deadlines && index >= lived) {
                        drawRoundRect(Palette.accent, Offset(x - 0.8f, y - 0.8f), Size(cell + 1.6f, cell + 1.6f), CornerRadius(cell * 0.28f + 0.8f), style = ring)
                    }
                    if (index in markers.letters) {
                        drawRoundRect(Color.White, Offset(x - 0.8f, y - 0.8f), Size(cell + 1.6f, cell + 1.6f), CornerRadius(cell * 0.28f + 0.8f), style = ring)
                    }
                    if (index == selected) {
                        drawRoundRect(Color.White, Offset(x - 1.5f, y - 1.5f), Size(cell + 3f, cell + 3f), CornerRadius(cell * 0.28f + 1.5f), style = Stroke(1.5.dp.toPx()))
                    }
                }
                val labelPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.argb(97, 255, 255, 255)
                    textSize = 9.sp.toPx()
                    textAlign = android.graphics.Paint.Align.RIGHT
                }
                for (row in 0 until rows step 10) {
                    drawContext.canvas.nativeCanvas.drawText("$row", labelPx - 5.dp.toPx(), rowY(row) + cell / 2 + 3.dp.toPx(), labelPaint)
                }
            }
            // Мягко пульсирующая рамка текущей недели — отдельным слоем, чтобы не перерисовывать всю сетку.
            if (current != null) {
                Canvas(Modifier.matchParentSize()) {
                    val x = columnX(current % COLUMNS) + cell / 2
                    val y = rowY(current / COLUMNS) + cell / 2
                    val size = cell * pulseScale
                    drawRoundRect(
                        Palette.accent.copy(alpha = (0.9f * (3f - pulseScale) / 2f).coerceIn(0f, 1f)),
                        Offset(x - size / 2, y - size / 2),
                        Size(size, size),
                        CornerRadius(size * 0.3f),
                        style = Stroke(1.2.dp.toPx()),
                    )
                }
            }
        }
    }
}

@Composable
private fun YearsGrid(stats: LifeStats) {
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widthPx = with(density) { maxWidth.roundToPx() }
        val rows = (stats.lifeExpectancy + 9) / 10
        val cell = widthPx / (10 + 9 * 0.45f)
        val heightPx = (rows * cell + (rows - 1) * cell * 0.45f).toInt().coerceAtLeast(1)
        val bitmap = remember(stats.ageYears, stats.lifeExpectancy, widthPx, stats.now.epochSecond / 3600) { YearsBitmap.render(stats, widthPx, heightPx) }
        Image(bitmap.asImageBitmap(), contentDescription = "Жизнь в годах", modifier = Modifier.fillMaxWidth().aspectRatio(widthPx.toFloat() / heightPx))
    }
}

@Composable
private fun Legend(weeks: Boolean, hasLetters: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        LegendItem(Palette.lived, "прожито")
        LegendItem(Palette.future, "впереди")
        LegendItem(Palette.accent, if (weeks) "выполнено" else "сейчас")
        if (weeks) {
            LegendItem(Palette.accent, "срок", outlined = true)
            if (hasLetters) LegendItem(Color.White, "письмо", outlined = true)
        }
    }
}

@Composable
private fun LegendItem(color: Color, title: String, outlined: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).let {
                if (outlined) it.border(1.dp, color, RoundedCornerShape(2.dp)) else it.background(color)
            },
        )
        Spacer(Modifier.size(5.dp))
        Text(title, color = Palette.secondaryText, fontSize = 11.sp)
    }
}

@Composable
private fun WeekInfoCard(
    stats: LifeStats,
    index: Int,
    goals: List<Goal>,
    letters: List<FutureLetter>,
    onOpenLetter: (String) -> Unit,
    onClose: () -> Unit,
) {
    val lived = stats.livedGridCells
    val status = when {
        index == stats.currentGridIndex -> "сейчас"
        index < lived -> "прожита"
        else -> "впереди"
    }
    Column(
        Modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xF21C1C1E)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Неделя ${Format.number(index + 1)}", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Spacer(Modifier.size(8.dp))
            Text(status, color = if (status == "сейчас") Palette.accent else Palette.tertiaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Filled.Cancel, contentDescription = "Закрыть", tint = Palette.tertiaryText) }
        }
        stats.dateRange(index)?.let { (start, end) ->
            Text("${Format.weekRange(start, end)} · ${Format.years(stats.ageForGridIndex(index))}", color = Palette.secondaryText, fontSize = 14.sp)
        }
        goals.forEach { goal ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (goal.isCompleted) Icons.Filled.CheckCircle else Icons.Outlined.Flag, contentDescription = null, tint = if (goal.isCompleted) Palette.accent else Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(goal.title, color = if (goal.isCompleted) Palette.accent else Color.White, fontSize = 15.sp, maxLines = 2)
            }
        }
        letters.forEach { letter ->
            Row(Modifier.clickable { onOpenLetter(letter.id) }, verticalAlignment = Alignment.CenterVertically) {
                Icon(if (letter.isOpened) Icons.Outlined.Drafts else Icons.Outlined.Mail, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(
                    if (letter.isOpened) "Письмо от ${Format.shortDate(letter.createdAt)} — прочитано" else "Письмо от ${Format.shortDate(letter.createdAt)} откроется в эту неделю",
                    fontSize = 15.sp,
                    maxLines = 2,
                )
            }
        }
    }
}
