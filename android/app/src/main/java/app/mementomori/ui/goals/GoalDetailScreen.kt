package app.mementomori.ui.goals

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.Photo
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mementomori.core.Format
import app.mementomori.core.Goal
import app.mementomori.core.GoalCategory
import app.mementomori.core.IdeaLibrary
import app.mementomori.core.LifeStats
import app.mementomori.core.capitalizedFirst
import app.mementomori.core.flagEmoji
import app.mementomori.core.localDate
import app.mementomori.data.PhotoChange
import app.mementomori.ui.AccentButton
import app.mementomori.ui.DatePickerSheet
import app.mementomori.ui.FormRow
import app.mementomori.ui.FormSection
import app.mementomori.ui.LocalModel
import app.mementomori.ui.LocalNavigator
import app.mementomori.ui.Screen
import app.mementomori.ui.SectionHeader
import app.mementomori.ui.SheetScaffold
import app.mementomori.ui.ThinProgressBar
import app.mementomori.ui.icon
import app.mementomori.ui.mementoCard
import app.mementomori.ui.theme.Palette
import app.mementomori.ui.toInstantAtStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate

@Composable
fun GoalDetailScreen(goalId: String) {
    val model = LocalModel.current
    val navigator = LocalNavigator.current
    val haptic = LocalHapticFeedback.current
    val data by model.data.collectAsStateWithLifecycle()
    val goal = data.goal(goalId)
    var focusLimit by remember { mutableStateOf(false) }
    LaunchedEffect(goal == null) { if (goal == null) navigator.pop() }
    goal ?: return

    SheetScaffold(
        title = "",
        onBack = { navigator.pop() },
        actions = {
            if (!goal.isCompleted) {
                IconButton(onClick = { if (!model.toggleFocus(goal.id)) focusLimit = true }) {
                    Icon(
                        if (goal.isFocused) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = if (goal.isFocused) "Убрать из фокуса" else "В фокус",
                        tint = Palette.accent,
                    )
                }
            }
            TextButton(onClick = { navigator.push(Screen.GoalEditor(goal.id)) }) { Text("Изменить", color = Palette.accent) }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Заголовок
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(goal.category.icon, contentDescription = null, tint = Palette.tertiaryText, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(goal.category.title.uppercase(Format.locale), color = Palette.tertiaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp)
                }
                goal.countryCode?.let { Text(flagEmoji(it), fontSize = 56.sp) }
                Text(goal.title, fontFamily = FontFamily.Serif, fontSize = 30.sp, lineHeight = 36.sp)
                if (goal.isCompleted) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Verified, contentDescription = null, tint = Palette.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Выполнено", color = Palette.accent, fontWeight = FontWeight.Medium)
                    }
                }
            }

            if (goal.isCompleted) Memory(goal, data.profile?.let { LifeStats(it, goal.completedAt ?: Instant.now()) })

            if (goal.steps.isNotEmpty()) {
                Column(Modifier.mementoCard(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SectionHeader("Шаги", "${goal.completedStepsCount} из ${goal.steps.size}")
                    goal.steps.forEach { step ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                if (!step.isDone) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                model.toggleStep(goal.id, step.id)
                            },
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                if (step.isDone) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (step.isDone) Palette.accent else Palette.tertiaryText,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(Modifier.size(10.dp))
                            Text(
                                step.title,
                                color = if (step.isDone) Palette.secondaryText else Color.White,
                                textDecoration = if (step.isDone) TextDecoration.LineThrough else null,
                            )
                        }
                    }
                    ThinProgressBar(goal.completedStepsCount.toDouble() / maxOf(1, goal.steps.size), height = 3.dp, fill = Palette.accent)
                }
            }

            val birthDate = data.profile?.birthDate
            if (goal.hasDeadline) {
                val deadline = birthDate?.let { goal.deadline(it) }
                Column(Modifier.mementoCard(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SectionHeader("Срок")
                    Text(deadlineTitle(goal, deadline), fontSize = 20.sp)
                    val stats = model.stats()
                    if (!goal.isCompleted && deadline != null && stats != null) {
                        Text(deadlineInsight(deadline, stats), color = Palette.secondaryText, fontSize = 15.sp)
                    }
                }
            }

            if (goal.note.isNotEmpty()) {
                Column(Modifier.mementoCard(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader("Зачем")
                    Text(goal.note, color = Color.White.copy(alpha = 0.9f))
                }
            }

            if (goal.isCompleted) {
                OutlinedButton(onClick = { navigator.push(Screen.CompleteGoal(goal.id)) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Photo, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Изменить воспоминание")
                }
                OutlinedButton(onClick = { model.reopenGoal(goal.id) }, modifier = Modifier.fillMaxWidth()) {
                    Text("Вернуть в список", color = Color.White)
                }
            } else {
                AccentButton("Выполнено", icon = Icons.Filled.Check) { navigator.push(Screen.CompleteGoal(goal.id)) }
            }
            Text("Добавлена ${Format.date(goal.createdAt)}", color = Palette.tertiaryText, fontSize = 12.sp)
        }
    }

    if (focusLimit) {
        AlertDialog(
            onDismissRequest = { focusLimit = false },
            confirmButton = { TextButton(onClick = { focusLimit = false }) { Text("Понятно") } },
            title = { Text("В фокусе уже три цели") },
            text = { Text("Сначала убери звёздочку с одной из них.") },
        )
    }
}

@Composable
private fun Memory(goal: Goal, stats: LifeStats?) {
    val model = LocalModel.current
    val photo by produceState<Bitmap?>(null, goal.photoFileName) { value = withContext(Dispatchers.IO) { model.photo(goal) } }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        photo?.let {
            Image(
                it.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(280.dp).clip(RoundedCornerShape(20.dp)),
            )
        }
        goal.completedAt?.let { date ->
            Text(
                "Выполнено ${Format.date(date)}" + (stats?.let { " — " + Format.atAge(it.ageYears) } ?: ""),
                color = Palette.secondaryText,
                fontSize = 15.sp,
            )
        }
        if (goal.reflection.isNotEmpty()) Text(goal.reflection, fontFamily = FontFamily.Serif, fontSize = 17.sp)
    }
}

private fun deadlineTitle(goal: Goal, deadline: Instant?): String {
    goal.targetAge?.let { age ->
        return Format.byAge(age).capitalizedFirst + (deadline?.let { " — " + Format.monthYear(it) } ?: "")
    }
    goal.targetDate?.let { return "До " + Format.date(it) }
    return ""
}

private fun deadlineInsight(deadline: Instant, stats: LifeStats): String {
    val seconds = LifeStats.seconds(stats.now, deadline)
    if (seconds <= 0) return "Срок прошёл. Назначь новый — или сделай прямо сейчас."
    val weeks = (seconds / LifeStats.SECONDS_PER_DAY).toInt() / 7
    if (stats.remainingWeeks <= 0) return "До срока ${Format.weeks(weeks)}."
    val share = weeks.toDouble() / stats.remainingWeeks
    return "До срока ${Format.weeks(weeks)} — это ${if (share < 0.01) "меньше 1%" else Format.percent(share, 0)} оставшейся жизни."
}

// MARK: Выполнено

/** Отметить цель выполненной — или отредактировать воспоминание о ней. */
@Composable
fun CompleteGoalScreen(goalId: String) {
    val model = LocalModel.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val goal = model.data.value.goal(goalId) ?: run {
        LaunchedEffect(Unit) { navigator.pop() }
        return
    }
    var date by remember { mutableStateOf(goal.completedAt?.localDate() ?: LocalDate.now()) }
    var reflection by remember { mutableStateOf(goal.reflection) }
    var newPhoto by remember { mutableStateOf<android.net.Uri?>(null) }
    var removeExisting by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            newPhoto = uri
            removeExisting = false
        }
    }
    val existing by produceState<Bitmap?>(null, goal.photoFileName) { value = withContext(Dispatchers.IO) { model.photo(goal, 900) } }
    val preview by produceState<Bitmap?>(null, newPhoto) {
        value = newPhoto?.let { uri ->
            withContext(Dispatchers.IO) {
                runCatching {
                    model.context.contentResolver.openInputStream(uri)?.use { stream ->
                        android.graphics.BitmapFactory.decodeStream(stream, null, android.graphics.BitmapFactory.Options().apply { inSampleSize = 4 })
                    }
                }.getOrNull()
            }
        }
    }
    val shown = preview ?: existing.takeIf { !removeExisting }

    SheetScaffold(
        title = if (goal.isCompleted) "Воспоминание" else "Выполнено",
        onBack = { navigator.pop() },
        backLabel = "Отмена",
        actions = {
            TextButton(enabled = !saving, onClick = {
                saving = true
                val change = newPhoto?.let { PhotoChange.Replace(it) } ?: if (removeExisting) PhotoChange.Remove else PhotoChange.Keep
                scope.launch {
                    withContext(Dispatchers.IO) { model.saveCompletion(goal.id, date.toInstantAtStart().plusSeconds(12 * 3600), reflection.trim(), change) }
                    navigator.pop()
                }
            }) { Text("Сохранить", color = Palette.accent, fontSize = 16.sp) }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    if (goal.isCompleted) "Воспоминание" else "Одной мечтой меньше — одной историей больше",
                    color = Palette.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(goal.title, fontFamily = FontFamily.Serif, fontSize = 22.sp)
            }
            FormSection {
                FormRow("Когда", Format.date(date.toInstantAtStart()), onClick = { pickDate = true })
            }
            FormSection(header = "На память") {
                TextField(
                    value = reflection,
                    onValueChange = { reflection = it },
                    placeholder = { Text("Каким было это мгновение?") },
                    colors = plainFieldColors(),
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            FormSection(footer = "Фото хранится только на этом устройстве.") {
                if (saving) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Palette.accent) }
                } else if (shown != null) {
                    Image(
                        shown.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).padding(12.dp).clip(RoundedCornerShape(12.dp)),
                    )
                }
                FormRow(
                    if (shown != null) "Выбрать другое фото" else "Добавить фото",
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                )
                if (shown != null) {
                    Text(
                        "Убрать фото",
                        color = Color(0xFFFF6B5E),
                        modifier = Modifier.fillMaxWidth().clickable {
                            newPhoto = null
                            removeExisting = true
                        }.padding(16.dp),
                    )
                }
            }
        }
    }
    if (pickDate) DatePickerSheet(date, onDismiss = { pickDate = false }, onPick = { date = it }, max = LocalDate.now())
}

// MARK: Библиотека идей

@Composable
fun IdeasScreen() {
    val model = LocalModel.current
    val navigator = LocalNavigator.current
    val data by model.data.collectAsStateWithLifecycle()
    SheetScaffold(title = "Библиотека идей", onBack = { navigator.pop() }, backLabel = "Готово") { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            for (category in GoalCategory.entries) {
                val ideas = IdeaLibrary.ideas(category)
                if (ideas.isEmpty()) continue
                item(key = "header-${category.raw}") {
                    Row(Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(category.icon, contentDescription = null, tint = Palette.secondaryText, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(category.title, color = Palette.secondaryText, fontWeight = FontWeight.SemiBold)
                    }
                }
                items(ideas, key = { it.title }) { idea ->
                    val added = data.goals.any { it.title.equals(idea.title, ignoreCase = true) }
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(idea.title, modifier = Modifier.weight(1f), color = if (added) Palette.secondaryText else Color.White)
                        Box(
                            Modifier.size(32.dp).clip(CircleShape).background(if (added) Palette.future else Palette.accent).clickable(enabled = !added) { model.addIdea(idea) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (added) Icons.Filled.Check else Icons.Filled.Add,
                                contentDescription = if (added) "Уже в списке" else "Добавить",
                                tint = if (added) Color.White else Color.Black,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}
