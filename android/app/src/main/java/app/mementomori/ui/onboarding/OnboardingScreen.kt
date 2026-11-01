package app.mementomori.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mementomori.core.ExpectancyChoice
import app.mementomori.core.ExpectancySource
import app.mementomori.core.Format
import app.mementomori.core.IdeaLibrary
import app.mementomori.core.LifeStats
import app.mementomori.core.Profile
import app.mementomori.core.RussianPlural
import app.mementomori.reminders.ReminderScheduler
import app.mementomori.ui.CountryPickerDialog
import app.mementomori.ui.DatePickerSheet
import app.mementomori.ui.LocalModel
import app.mementomori.ui.Segmented
import app.mementomori.ui.StepperRow
import app.mementomori.ui.icon
import app.mementomori.ui.settings.expectancyItems
import app.mementomori.ui.settings.expectancyLabel
import app.mementomori.ui.theme.Palette
import app.mementomori.ui.toInstantAtStart
import java.time.LocalDate

@Composable
fun OnboardingScreen() {
    val model = LocalModel.current
    val context = androidx.compose.ui.platform.LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    var birthDate by remember { mutableStateOf(LocalDate.now().minusYears(30)) }
    var expectancy by remember { mutableStateOf(ExpectancyChoice.suggested()) }
    var selectedIdeas by remember { mutableStateOf(setOf<String>()) }
    var reminders by remember { mutableStateOf(true) }
    var pickDate by remember { mutableStateOf(false) }
    var pickCountry by remember { mutableStateOf(false) }
    val lastStep = 2

    fun finish(remindersAllowed: Boolean) {
        model.completeOnboarding(birthDate, expectancy, IdeaLibrary.starter.filter { it.title in selectedIdeas }, remindersAllowed)
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> finish(granted) }

    fun advance() {
        if (step < lastStep) {
            step += 1
            return
        }
        if (reminders && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !ReminderScheduler.canNotify(context)) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            finish(reminders)
        }
    }

    BackHandler(enabled = step > 0) { step -= 1 }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        AnimatedContent(targetState = step, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "step", modifier = Modifier.weight(1f)) { current ->
            when (current) {
                0 -> Welcome()
                1 -> LifeStep(birthDate, expectancy, onPickDate = { pickDate = true }, onPickCountry = { pickCountry = true }) { expectancy = it }
                else -> GoalsStep(selectedIdeas, reminders, onToggle = { title ->
                    selectedIdeas = if (title in selectedIdeas) selectedIdeas - title else selectedIdeas + title
                }) { reminders = it }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(80.dp)) {
                if (step > 0) TextButton(onClick = { step -= 1 }) { Text("Назад", color = Palette.secondaryText) }
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                (0..lastStep).forEach { index ->
                    Box(Modifier.padding(3.dp).size(6.dp).clip(CircleShape).background(if (index == step) Palette.accent else Palette.future))
                }
            }
            Box(
                Modifier.clip(CircleShape).background(Palette.accent).clickable(onClick = ::advance).padding(horizontal = 28.dp, vertical = 14.dp),
            ) {
                Text(if (step == lastStep) "Начать" else "Далее", color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            }
        }
    }

    if (pickDate) {
        DatePickerSheet(birthDate, onDismiss = { pickDate = false }, onPick = { birthDate = it }, min = LocalDate.now().minusYears(110), max = LocalDate.now(), typing = true)
    }
    if (pickCountry) {
        CountryPickerDialog(
            title = "Страна",
            items = expectancyItems,
            selected = expectancy.country,
            footer = "Средняя продолжительность жизни при рождении, лет. ${app.mementomori.core.LifeExpectancyData.ATTRIBUTION}",
            onDismiss = { pickCountry = false },
        ) { code ->
            code?.let { expectancy = expectancy.selecting(it) }
            pickCountry = false
        }
    }
}

@Composable
private fun Welcome() {
    Column(Modifier.fillMaxSize().padding(horizontal = 28.dp), verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically)) {
        Text("MEMENTO\nMORI", fontSize = 52.sp, fontWeight = FontWeight.ExtraLight, letterSpacing = 6.sp, lineHeight = 58.sp)
        Text("Помни о смерти.", fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 24.sp)
        Text(
            "Не чтобы бояться, а чтобы жить. Собери всё, что хочешь успеть, — и смотри, сколько у тебя осталось времени.",
            color = Palette.secondaryText,
            fontSize = 17.sp,
        )
    }
}

@Composable
private fun LifeStep(
    birthDate: LocalDate,
    expectancy: ExpectancyChoice,
    onPickDate: () -> Unit,
    onPickCountry: () -> Unit,
    onChange: (ExpectancyChoice) -> Unit,
) {
    val stats = LifeStats(birthDate.toInstantAtStart(), expectancy.years)
    val age = stats.ageYears
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Дата твоего рождения", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.card).clickable(onClick = onPickDate).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(Format.date(birthDate.toInstantAtStart()), fontSize = 20.sp, modifier = Modifier.weight(1f))
            Text("Изменить", color = Palette.accent)
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Сколько лет закладываем на жизнь?", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Segmented(ExpectancySource.entries.map { it to it.title }, expectancy.source) { onChange(expectancy.copy(source = it)) }
            if (expectancy.source == ExpectancySource.COUNTRY) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.card).clickable(onClick = onPickCountry).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(expectancyLabel(expectancy.country), modifier = Modifier.weight(1f))
                    Text(Format.years(expectancy.years), fontSize = 20.sp)
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Palette.tertiaryText)
                }
                val late = age >= expectancy.years - 2
                Text(
                    if (late) {
                        "Средняя по стране уже почти позади — и это хорошо: кто прожил дольше, обычно и живёт дольше среднего. Лучше укажи своё число."
                    } else {
                        "Средняя продолжительность жизни в стране по данным Всемирного банка за 2023 год — без расчётов и гаданий. Поменять можно в любой момент."
                    },
                    color = if (late) Palette.accent else Palette.secondaryText,
                    fontSize = 13.sp,
                )
            } else {
                Box(Modifier.clip(RoundedCornerShape(14.dp)).background(Palette.card)) {
                    StepperRow(Format.years(expectancy.customYears), expectancy.customYears, Profile.LIFE_EXPECTANCY_RANGE) { onChange(expectancy.copy(customYears = it)) }
                }
                Text("В среднем по миру люди живут около 73 лет. Можно взять с запасом — это легко поменять позже.", color = Palette.secondaryText, fontSize = 13.sp)
            }
        }
        Column {
            Text(Format.number(stats.remainingWeeks), fontSize = 56.sp, fontWeight = FontWeight.Thin)
            Text(RussianPlural.form(stats.remainingWeeks, "неделя впереди", "недели впереди", "недель впереди"), color = Palette.secondaryText)
        }
    }
}

@Composable
private fun GoalsStep(selected: Set<String>, reminders: Boolean, onToggle: (String) -> Unit, onReminders: (Boolean) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp, vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Что ты хочешь успеть?", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text("Выбери несколько идей для начала. Свои цели добавишь потом — список можно менять когда угодно.", color = Palette.secondaryText)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            IdeaLibrary.starter.forEach { idea ->
                val isSelected = idea.title in selected
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.card).clickable { onToggle(idea.title) }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(idea.category.icon, contentDescription = null, tint = Palette.secondaryText, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.size(12.dp))
                    Text(idea.title, modifier = Modifier.weight(1f))
                    Icon(
                        if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (isSelected) Palette.accent else Palette.tertiaryText,
                    )
                }
            }
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Ежедневное напоминание")
                Text("В 9:00 — мысль дня, цель из списка или просто «помни».", color = Palette.secondaryText, fontSize = 13.sp)
            }
            Switch(reminders, onReminders, colors = SwitchDefaults.colors(checkedTrackColor = Palette.accent, checkedThumbColor = Color.Black))
        }
    }
}
