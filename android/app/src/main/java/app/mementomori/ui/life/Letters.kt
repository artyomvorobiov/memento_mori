package app.mementomori.ui.life

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Drafts
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mementomori.core.AppData
import app.mementomori.core.Format
import app.mementomori.core.FutureLetter
import app.mementomori.core.LetterPrompts
import app.mementomori.core.LifeStats
import app.mementomori.core.capitalizedFirst
import app.mementomori.core.openedLetters
import app.mementomori.core.sealedLetters
import app.mementomori.core.waitingLetters
import app.mementomori.ui.AccentButton
import app.mementomori.ui.DatePickerSheet
import app.mementomori.ui.FormDivider
import app.mementomori.ui.FormRow
import app.mementomori.ui.FormSection
import app.mementomori.ui.LocalModel
import app.mementomori.ui.LocalNavigator
import app.mementomori.ui.Pill
import app.mementomori.ui.PillRow
import app.mementomori.ui.Screen
import app.mementomori.ui.SectionHeader
import app.mementomori.ui.SheetScaffold
import app.mementomori.ui.StepperRow
import app.mementomori.ui.goals.plainFieldColors
import app.mementomori.ui.mementoCard
import app.mementomori.ui.theme.Palette
import app.mementomori.ui.toInstantAtStart
import java.time.Instant
import java.time.LocalDate

// MARK: Список писем (вкладка «Жизнь»)

@Composable
fun LettersSection(data: AppData, now: Instant) {
    val navigator = LocalNavigator.current
    val waiting = data.waitingLetters(now)
    val sealed = data.sealedLetters(now)
    val opened = data.openedLetters
    val rows = waiting + sealed + opened
    val birthDate = data.profile?.birthDate ?: now
    Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("Письма в будущее", if (rows.isEmpty()) null else "${rows.size}")
        if (rows.isEmpty()) {
            Column(Modifier.mementoCard(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Напиши письмо себе — через год, к 40 годам или к любой дате. До этого дня его не прочитать, а в нужный день придёт уведомление. Письмо появится рамкой на сетке недель.",
                    color = Palette.secondaryText,
                    fontSize = 15.sp,
                )
                AccentButton("Написать письмо", icon = Icons.Outlined.Mail) { navigator.push(Screen.NewLetter) }
            }
        } else {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Palette.card)) {
                rows.forEachIndexed { index, letter ->
                    if (index > 0) FormDivider()
                    LetterRow(letter, now, birthDate) { navigator.push(Screen.Letter(letter.id)) }
                }
                FormDivider()
                Row(
                    Modifier.fillMaxWidth().clickable { navigator.push(Screen.NewLetter) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.EditNote, contentDescription = null, tint = Palette.accent)
                    Spacer(Modifier.size(10.dp))
                    Text("Написать ещё одно", color = Palette.accent, fontSize = 15.sp)
                }
            }
        }
    }
}

@Composable
private fun LetterRow(letter: FutureLetter, now: Instant, birthDate: Instant, onClick: () -> Unit) {
    val unlock = letter.unlockDate(birthDate)
    val waiting = letter.isWaiting(now, birthDate)
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            when {
                letter.isOpened -> Icons.Outlined.Drafts
                waiting -> Icons.Outlined.MarkEmailUnread
                else -> Icons.Outlined.Mail
            },
            contentDescription = null,
            tint = if (letter.isOpened) Palette.secondaryText else Palette.accent,
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                when {
                    letter.isOpened -> letter.preview.ifEmpty { "Письмо" }.let { "«$it»" }
                    waiting -> "Можно открыть"
                    else -> "Откроется ${letter.openLabel(birthDate)}"
                },
                color = if (waiting) Palette.accent else Color.White,
                maxLines = 2,
            )
            val openedAt = letter.openedAt
            Text(
                when {
                    openedAt != null -> "написано ${Format.shortDate(letter.createdAt)} · прочитано ${Format.shortDate(openedAt)}"
                    letter.isUnlocked(now, birthDate) -> "написано ${Format.shortDate(letter.createdAt)}"
                    else -> "написано ${Format.shortDate(letter.createdAt)} · ${Format.relative(now, unlock)}"
                },
                color = Palette.secondaryText,
                fontSize = 12.sp,
                maxLines = 2,
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Palette.tertiaryText)
    }
}

// MARK: Новое письмо

private sealed interface OpenWhen {
    data class InYears(val years: Int) : OpenWhen
    data object AtAge : OpenWhen
    data object OnDate : OpenWhen
}

/** Письмо себе в будущее: текст и день, когда его можно будет открыть. */
@Composable
fun LetterEditorScreen() {
    val model = LocalModel.current
    val navigator = LocalNavigator.current
    val stats = model.stats()
    val currentAge = stats?.ageYears ?: 30
    val birthDate = model.profile?.birthDate ?: Instant.now()
    var text by remember { mutableStateOf("") }
    var openWhen by remember { mutableStateOf<OpenWhen>(OpenWhen.InYears(1)) }
    var age by remember { mutableIntStateOf(maxOf(currentAge + 1, (currentAge / 10 + 1) * 10)) }
    var date by remember { mutableStateOf(LocalDate.now().plusYears(1)) }
    var pickDate by remember { mutableStateOf(false) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { navigator.pop() }

    fun draft(): FutureLetter = when (val chosen = openWhen) {
        is OpenWhen.InYears -> FutureLetter(text = text, openDate = LocalDate.now().plusYears(chosen.years.toLong()).toInstantAtStart())
        OpenWhen.AtAge -> FutureLetter(text = text, openAge = age)
        OpenWhen.OnDate -> FutureLetter(text = text, openDate = date.toInstantAtStart())
    }

    fun insight(): String {
        val unlock = draft().unlockDate(birthDate)
        var line = "Откроется ${Format.date(unlock)} — ${Format.relative(Instant.now(), unlock)}."
        stats?.let { line += " Тебе будет ${Format.years(it.age(unlock))}." }
        if (stats != null && stats.remainingSeconds > 0) {
            val share = LifeStats.seconds(Instant.now(), unlock) / stats.remainingSeconds
            if (share in 0.005..1.0) line += " Это ${Format.percent(share, 0)} оставшейся жизни."
            else if (share > 1) line += " Это дальше, чем ожидаемая продолжительность жизни, — но вдруг."
        }
        return line
    }

    fun save() {
        model.saveLetter(draft())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !app.mementomori.reminders.ReminderScheduler.canNotify(model.context)) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            navigator.pop()
        }
    }

    SheetScaffold(
        title = "Письмо себе",
        onBack = { navigator.pop() },
        backLabel = "Отмена",
        actions = {
            TextButton(onClick = ::save, enabled = text.isNotBlank()) {
                Text("Запечатать", color = if (text.isNotBlank()) Palette.accent else Palette.tertiaryText, fontSize = 16.sp)
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            FormSection(header = "Когда открыть", footer = insight()) {
                PillRow(Modifier.padding(vertical = 12.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                    Pill("Через год", openWhen == OpenWhen.InYears(1)) { openWhen = OpenWhen.InYears(1) }
                    Pill("Через 3 года", openWhen == OpenWhen.InYears(3)) { openWhen = OpenWhen.InYears(3) }
                    Pill("Через 5 лет", openWhen == OpenWhen.InYears(5)) { openWhen = OpenWhen.InYears(5) }
                    Pill(Format.byAge(age).capitalizedFirst, openWhen == OpenWhen.AtAge) { openWhen = OpenWhen.AtAge }
                    Pill("Своя дата", openWhen == OpenWhen.OnDate) { openWhen = OpenWhen.OnDate }
                }
                when (openWhen) {
                    OpenWhen.AtAge -> {
                        FormDivider()
                        StepperRow("Возраст: ${Format.byAge(age)}", age, (currentAge + 1)..maxOf(currentAge + 1, 120)) { age = it }
                    }
                    OpenWhen.OnDate -> {
                        FormDivider()
                        FormRow("Дата", Format.date(date.toInstantAtStart()), onClick = { pickDate = true })
                    }
                    is OpenWhen.InYears -> Unit
                }
            }
            FormSection(
                header = "Письмо",
                footer = "Нажми на вопрос, чтобы начать с него. После сохранения письмо запечатается: до этого дня его не прочитать и не изменить. В день открытия придёт уведомление.",
            ) {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Привет из прошлого! Сейчас…") },
                    colors = plainFieldColors(),
                    minLines = 8,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 240.dp),
                )
                FormDivider()
                PillRow(Modifier.padding(vertical = 12.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                    LetterPrompts.all.forEach { prompt ->
                        Pill(prompt, false) {
                            val trimmed = text.trim()
                            text = if (trimmed.isEmpty()) "$prompt\n" else "$trimmed\n\n$prompt\n"
                        }
                    }
                }
            }
        }
    }
    if (pickDate) DatePickerSheet(date, onDismiss = { pickDate = false }, onPick = { date = it }, min = LocalDate.now().plusDays(1))
}

// MARK: Письмо

/** Запечатанное, ждущее или прочитанное письмо. */
@Composable
fun LetterScreen(letterId: String) {
    val model = LocalModel.current
    val navigator = LocalNavigator.current
    val haptic = LocalHapticFeedback.current
    val data by model.data.collectAsStateWithLifecycle()
    val letter = data.letter(letterId)
    val birthDate = data.profile?.birthDate
    var confirmEarly by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(letter == null) { if (letter == null) navigator.pop() }
    if (letter == null || birthDate == null) return
    val now = Instant.now()

    fun open() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        model.openLetter(letter.id)
    }

    SheetScaffold(
        title = "",
        onBack = { navigator.pop() },
        backLabel = "Закрыть",
        actions = {
            IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, contentDescription = "Удалить письмо", tint = Palette.secondaryText) }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                letter.isOpened -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("ПИСЬМО ИЗ ПРОШЛОГО", color = Palette.tertiaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
                        Text("${Format.date(letter.createdAt)} · тебе было ${Format.years(letter.ageWhenWritten(birthDate))}", color = Palette.secondaryText, fontSize = 15.sp)
                    }
                    SelectionContainer {
                        Text(letter.text, fontFamily = FontFamily.Serif, fontSize = 21.sp, lineHeight = 31.sp)
                    }
                    letter.openedAt?.let { Text("Прочитано ${Format.date(it)}", color = Palette.tertiaryText, fontSize = 12.sp) }
                    Row(Modifier.clickable { navigator.replace(Screen.NewLetter) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.EditNote, contentDescription = null, tint = Palette.accent)
                        Spacer(Modifier.size(8.dp))
                        Text("Написать ответ в будущее", color = Palette.accent)
                    }
                }
                letter.isUnlocked(now, birthDate) -> Envelope(
                    icon = Icons.Outlined.MarkEmailUnread,
                    title = "Письмо из прошлого",
                    subtitle = "Написано ${Format.date(letter.createdAt)}, тебе было ${Format.years(letter.ageWhenWritten(birthDate))}.",
                    detail = null,
                ) {
                    AccentButton("Открыть письмо", icon = Icons.Outlined.Drafts, onClick = ::open)
                }
                else -> {
                    val unlock = letter.unlockDate(birthDate)
                    Envelope(
                        icon = Icons.Outlined.Mail,
                        title = "Запечатано до ${Format.date(unlock)}",
                        subtitle = "Написано ${Format.date(letter.createdAt)}, откроется ${Format.relative(now, unlock)}.",
                        detail = "До этого дня его не прочитать — в этом и смысл.",
                    ) {
                        TextButton(onClick = { confirmEarly = true }) { Text("Открыть досрочно", color = Palette.secondaryText) }
                    }
                }
            }
        }
    }

    if (confirmEarly) {
        AlertDialog(
            onDismissRequest = { confirmEarly = false },
            title = { Text("Открыть раньше срока?") },
            text = { Text("Письмо задумано на ${Format.date(letter.unlockDate(birthDate))} — иногда ждать тоже часть удовольствия.") },
            confirmButton = { TextButton(onClick = { confirmEarly = false; open() }) { Text("Открыть сейчас") } },
            dismissButton = { TextButton(onClick = { confirmEarly = false }) { Text("Подождать") } },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить письмо?") },
            text = { Text("Его нельзя будет восстановить.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    model.deleteLetter(letter.id)
                }) { Text("Удалить", color = Color(0xFFFF6B5E)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun Envelope(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    detail: String?,
    actions: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(110.dp).clip(CircleShape).background(Palette.accent.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Palette.accent, modifier = Modifier.size(60.dp))
        }
        Spacer(Modifier.size(20.dp))
        Text(title, fontFamily = FontFamily.Serif, fontSize = 24.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.size(8.dp))
        Text(subtitle, color = Palette.secondaryText, textAlign = TextAlign.Center)
        if (detail != null) {
            Spacer(Modifier.size(8.dp))
            Text(detail, color = Palette.tertiaryText, fontSize = 13.sp, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.weight(1f))
        actions()
    }
}
