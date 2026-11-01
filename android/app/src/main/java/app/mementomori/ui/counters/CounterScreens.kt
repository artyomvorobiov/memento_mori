package app.mementomori.ui.counters

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mementomori.core.CounterPreset
import app.mementomori.core.CounterPresets
import app.mementomori.core.Format
import app.mementomori.core.LifeCounter
import app.mementomori.core.actualPerYear
import app.mementomori.core.countInYearOf
import app.mementomori.core.daysBetween
import app.mementomori.core.daysSinceLast
import app.mementomori.core.isLogged
import app.mementomori.core.isOverdue
import app.mementomori.core.lastDate
import app.mementomori.core.supportsOverdueReminders
import app.mementomori.core.usualIntervalDays
import app.mementomori.reminders.ReminderScheduler
import app.mementomori.ui.DatePickerSheet
import app.mementomori.ui.FormDivider
import app.mementomori.ui.FormRow
import app.mementomori.ui.FormSection
import app.mementomori.ui.LocalModel
import app.mementomori.ui.LocalNavigator
import app.mementomori.ui.Screen
import app.mementomori.ui.SheetScaffold
import app.mementomori.ui.StepperRow
import app.mementomori.ui.goals.plainFieldColors
import app.mementomori.ui.theme.Palette
import app.mementomori.ui.toInstantAtStart
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

/** Счётчик «Последний раз»: когда было, как часто выходит на самом деле, история отметок. */
@Composable
fun CounterDetailScreen(counterId: String) {
    val model = LocalModel.current
    val navigator = LocalNavigator.current
    val haptic = LocalHapticFeedback.current
    val data by model.data.collectAsStateWithLifecycle()
    val counter = data.counter(counterId)
    var pickDate by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf<Instant?>(null) }
    LaunchedEffect(counter == null) { if (counter == null) navigator.pop() }
    counter ?: return
    val now = Instant.now()
    val stats = model.stats(now)
    val remainingYears = stats?.remainingYears ?: 0.0

    SheetScaffold(
        title = "",
        onBack = { navigator.pop() },
        backLabel = "Готово",
        actions = { TextButton(onClick = { navigator.push(Screen.CounterEditor(counter.id)) }) { Text("Изменить", color = Palette.accent) } },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(counter.emoji, fontSize = 44.sp)
                Text("≈ ${Format.number(counter.remaining(remainingYears))}", fontSize = 44.sp, fontWeight = FontWeight.Thin)
                Text(counter.title, color = Palette.secondaryText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                val days = counter.daysSinceLast(now)
                val last = counter.lastDate
                if (days != null && last != null) {
                    Text(
                        "Последний раз — ${Format.daysAgo(days)}, ${Format.date(last)}",
                        color = if (counter.isOverdue(now)) Palette.accent else Color.White,
                        fontSize = 15.sp,
                    )
                }
                counter.usualIntervalDays?.let { usual ->
                    Text(
                        "Обычно — ${Format.everyInterval(usual)}" + if (counter.isOverdue(now)) ". Перерыв уже дольше обычного." else ".",
                        color = Palette.secondaryText,
                        fontSize = 13.sp,
                    )
                }
            }

            FormSection(
                footer = if (counter.log.isEmpty()) "Отмечай каждый раз, когда это случается. Приложение покажет, сколько прошло с последнего раза, и напомнит, если перерыв затянется." else null,
            ) {
                if (counter.isLogged(now)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Palette.accent)
                        Spacer(Modifier.size(10.dp))
                        Text("Сегодня уже отмечено", color = Palette.accent, fontSize = 16.sp)
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            if (model.logCounterToday(counter.id)) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.AddCircle, contentDescription = null, tint = Palette.accent)
                        Spacer(Modifier.size(10.dp))
                        Text("Было сегодня", color = Palette.accent, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                FormDivider()
                FormRow("Было в другой день…", onClick = { pickDate = true })
            }

            if (counter.log.isNotEmpty()) {
                val lines = buildList {
                    val year = now.atZone(ZoneId.systemDefault()).year
                    add("В $year году — ${counter.countInYearOf(now)} из ${maxOf(1, Math.round(counter.perYear).toInt())} запланированных.")
                    counter.actualPerYear(now)?.let { actual ->
                        val atPace = counter.remaining(remainingYears, actual)
                        val planned = counter.remaining(remainingYears)
                        add(
                            when {
                                actual < 0.05 -> "За последний год — ни разу. В таком темпе этого больше не случится."
                                abs(atPace - planned) >= maxOf(1, planned / 10) ->
                                    "По факту — ${Format.perYear(actual)} в год. В таком темпе впереди ≈ ${Format.number(atPace)}, а не ${Format.number(planned)}."
                                else -> "По факту — ${Format.perYear(actual)} в год: всё идёт по плану."
                            },
                        )
                    }
                }
                FormSection(header = "Как выходит на самом деле") {
                    lines.forEachIndexed { index, line ->
                        if (index > 0) FormDivider()
                        Text(line, fontSize = 15.sp, modifier = Modifier.padding(16.dp))
                    }
                }
                val history = counter.log.reversed()
                FormSection(header = "Когда было", footer = "Нажми на крестик, чтобы удалить отметку.") {
                    (if (showAll) history else history.take(8)).forEachIndexed { index, date ->
                        if (index > 0) FormDivider()
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(Format.date(date), modifier = Modifier.weight(1f))
                            Text(Format.daysAgo(maxOf(0L, daysBetween(date, now)).toInt()), color = Palette.tertiaryText, fontSize = 12.sp)
                            IconButton(onClick = { removing = date }) {
                                Icon(Icons.Outlined.Close, contentDescription = "Удалить отметку", tint = Palette.tertiaryText, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    if (history.size > 8 && !showAll) {
                        FormDivider()
                        Text("Показать все — ${history.size}", color = Palette.accent, modifier = Modifier.fillMaxWidth().clickable { showAll = true }.padding(16.dp))
                    }
                }
            }
        }
    }
    if (pickDate) {
        DatePickerSheet(LocalDate.now(), onDismiss = { pickDate = false }, onPick = { date ->
            // Отметки задним числом ставим на полдень, чтобы не съезжали на соседний день.
            model.logCounter(counter.id, date.toInstantAtStart().plusSeconds(12 * 3600))
        }, max = LocalDate.now())
    }
    removing?.let { date ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Удалить отметку?") },
            text = { Text(Format.date(date)) },
            confirmButton = { TextButton(onClick = { model.removeCounterLog(counter.id, date); removing = null }) { Text("Удалить", color = Color(0xFFFF6B5E)) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Отмена") } },
        )
    }
}

/** Свой счётчик «Осталось»: что-то, что случается N раз в год. */
@Composable
fun CounterEditorScreen(counterId: String?) {
    val model = LocalModel.current
    val navigator = LocalNavigator.current
    val existing = counterId?.let { model.data.value.counter(it) }
    val isNew = existing == null
    var draft by remember { mutableStateOf(existing ?: LifeCounter(title = "", emoji = "❤️", perYear = 4.0)) }
    var perYearText by remember { mutableStateOf(formatPerYear(draft.perYear)) }
    var untilEnd by remember { mutableStateOf(draft.years == null) }
    var years by remember { mutableStateOf(draft.years ?: 20) }
    var confirmDelete by remember { mutableStateOf(false) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { navigator.pop() }

    val perYear = perYearText.replace(',', '.').toDoubleOrNull() ?: 0.0
    fun result() = draft.copy(
        title = draft.title.trim(),
        emoji = draft.emoji.trim().ifEmpty { "✦" },
        perYear = perYear,
        years = if (untilEnd) null else years,
    )
    val canSave = draft.title.isNotBlank() && perYear > 0

    fun apply(preset: CounterPreset) {
        draft = draft.copy(title = preset.title, emoji = preset.emoji)
        perYearText = formatPerYear(preset.perYear)
        untilEnd = preset.years == null
        preset.years?.let { years = it }
    }

    fun save() {
        val counter = result()
        model.saveCounter(counter)
        val wantsReminders = counter.remindsWhenOverdue && counter.supportsOverdueReminders
        if (wantsReminders && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !ReminderScheduler.canNotify(model.context)) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            navigator.pop()
        }
    }

    SheetScaffold(
        title = if (isNew) "Новый счётчик" else "Счётчик",
        onBack = { navigator.pop() },
        backLabel = "Отмена",
        actions = {
            TextButton(onClick = ::save, enabled = canSave) {
                Text("Сохранить", color = if (canSave) Palette.accent else Palette.tertiaryText, fontSize = 16.sp)
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            if (isNew) {
                FormSection(header = "Идеи") {
                    CounterPresets.all.forEachIndexed { index, preset ->
                        if (index > 0) FormDivider()
                        Row(Modifier.fillMaxWidth().clickable { apply(preset) }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(preset.emoji)
                            Spacer(Modifier.size(10.dp))
                            Text(preset.title, modifier = Modifier.weight(1f))
                            Text(preset.hint, color = Palette.secondaryText, fontSize = 12.sp)
                        }
                    }
                }
            }
            FormSection(header = "Что считаем", footer = "Название читается после числа: «≈ 100 встреч с родителями».") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = draft.emoji,
                        onValueChange = { draft = draft.copy(emoji = it.take(4)) },
                        colors = plainFieldColors(),
                        textStyle = TextStyle(textAlign = TextAlign.Center, fontSize = 20.sp),
                        singleLine = true,
                        modifier = Modifier.width(64.dp),
                    )
                    TextField(
                        value = draft.title,
                        onValueChange = { draft = draft.copy(title = it) },
                        placeholder = { Text("например, встреч с родителями") },
                        colors = plainFieldColors(),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            FormSection(
                header = "Как часто",
                footer = "Если видишь родителей 5 раз в год и надеешься, что так будет ещё лет 20, — впереди около 100 встреч. Не так уж много.",
            ) {
                Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Раз в год", modifier = Modifier.weight(1f), fontSize = 16.sp)
                    TextField(
                        value = perYearText,
                        onValueChange = { perYearText = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(6) },
                        colors = plainFieldColors(),
                        textStyle = TextStyle(textAlign = TextAlign.End, fontSize = 16.sp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.width(110.dp),
                    )
                }
                FormDivider()
                FormRow("До конца жизни") {
                    Switch(
                        checked = untilEnd,
                        onCheckedChange = { untilEnd = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = Palette.accent, checkedThumbColor = Color.Black),
                    )
                }
                if (!untilEnd) {
                    FormDivider()
                    StepperRow("Ещё ${Format.years(years)}", years, 1..100) { years = it }
                }
            }
            val preview = result()
            if (preview.supportsOverdueReminders) {
                FormSection(
                    footer = "Отмечай «Было» на главном экране. Если перерыв станет заметно дольше обычного (${Format.everyInterval(preview.usualIntervalDays ?: 1)}), придёт мягкое напоминание.",
                ) {
                    FormRow("Напоминать, если давно не было") {
                        Switch(
                            checked = draft.remindsWhenOverdue,
                            onCheckedChange = { draft = draft.copy(remindsWhenOverdue = it) },
                            colors = SwitchDefaults.colors(checkedTrackColor = Palette.accent, checkedThumbColor = Color.Black),
                        )
                    }
                }
            }
            FormSection {
                FormRow("Осталось примерно") {
                    Text(Format.number(preview.remaining(model.stats()?.remainingYears ?: 0.0)), color = Palette.accent, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (!isNew) {
                FormSection {
                    Text("Удалить счётчик", color = Color(0xFFFF6B5E), modifier = Modifier.fillMaxWidth().clickable { confirmDelete = true }.padding(16.dp))
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить счётчик?") },
            text = { Text("Вместе с историей отметок.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    model.deleteCounter(draft.id)
                    navigator.pop()
                    if (navigator.stack.lastOrNull() is Screen.CounterDetail) navigator.pop()
                }) { Text("Удалить", color = Color(0xFFFF6B5E)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

private fun formatPerYear(value: Double): String =
    if (value == Math.rint(value)) value.toInt().toString() else Format.decimal(value)
