package app.mementomori.ui.goals

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mementomori.core.Country
import app.mementomori.core.Format
import app.mementomori.core.Goal
import app.mementomori.core.GoalCategory
import app.mementomori.core.GoalStep
import app.mementomori.core.GoalSuggestions
import app.mementomori.core.Idea
import app.mementomori.core.LifeStats
import app.mementomori.core.capitalizedFirst
import app.mementomori.core.flagEmoji
import app.mementomori.core.localDate
import app.mementomori.ui.CountryPickerDialog
import app.mementomori.ui.DatePickerSheet
import app.mementomori.ui.FormDivider
import app.mementomori.ui.FormRow
import app.mementomori.ui.FormSection
import app.mementomori.ui.LocalModel
import app.mementomori.ui.LocalNavigator
import app.mementomori.ui.Pill
import app.mementomori.ui.PillRow
import app.mementomori.ui.Segmented
import app.mementomori.ui.SheetScaffold
import app.mementomori.ui.StepperRow
import app.mementomori.ui.allCountryItems
import app.mementomori.ui.icon
import app.mementomori.ui.theme.Palette
import app.mementomori.ui.toInstantAtStart
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private enum class DeadlineMode(val title: String) { NONE("Без срока"), AGE("К возрасту"), DATE("К дате") }

/** Быстрый выбор срока: «до конца года», «через год», «к 40 годам». */
private data class DeadlinePreset(val title: String, val mode: DeadlineMode, val date: LocalDate? = null, val age: Int? = null)

@Composable
fun GoalEditorScreen(goalId: String?, presetCategory: GoalCategory?, initialTitle: String) {
    val model = LocalModel.current
    val navigator = LocalNavigator.current
    val existing = goalId?.let { model.data.value.goal(it) }
    val isNew = existing == null
    val stats = model.stats()
    val age = stats?.ageYears ?: 30

    var draft by remember { mutableStateOf(existing ?: Goal(title = initialTitle, category = presetCategory ?: GoalCategory.EXPERIENCE)) }
    var mode by remember {
        mutableStateOf(if (draft.targetAge != null) DeadlineMode.AGE else if (draft.targetDate != null) DeadlineMode.DATE else DeadlineMode.NONE)
    }
    val roundAges = remember(age) {
        val byFive = (age / 5 + 1) * 5
        var byTen = (age / 10 + 1) * 10
        if (byTen == byFive) byTen += 10
        listOf(byFive, byTen).filter { it <= 120 }
    }
    var targetAge by remember { mutableIntStateOf(draft.targetAge ?: roundAges.firstOrNull() ?: 40) }
    var targetDate by remember { mutableStateOf(draft.targetDate?.localDate() ?: LocalDate.now().plusYears(1)) }
    /** Категорию выбрали руками — больше не угадываем её по тексту. */
    var categoryTouched by remember { mutableStateOf(!isNew || presetCategory != null) }
    var dismissedCountry by remember { mutableStateOf<String?>(null) }
    var newStep by remember { mutableStateOf("") }
    var pickCountry by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val titleFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (isNew && !categoryTouched) GoalSuggestions.category(draft.title)?.let { draft = draft.copy(category = it) }
        if (isNew && initialTitle.isEmpty()) titleFocus.requestFocus()
    }

    fun setTitle(title: String) {
        draft = draft.copy(title = title)
        if (!categoryTouched) GoalSuggestions.category(title)?.let { draft = draft.copy(category = it) }
    }

    fun addStep() {
        val title = newStep.trim()
        if (title.isEmpty()) return
        draft = draft.copy(steps = draft.steps + GoalStep(title = title))
        newStep = ""
    }

    fun save() {
        addStep()
        var goal = draft.copy(title = draft.title.trim())
        goal = when (mode) {
            DeadlineMode.NONE -> goal.copy(targetAge = null, targetDate = null)
            DeadlineMode.AGE -> goal.copy(targetAge = targetAge, targetDate = null)
            DeadlineMode.DATE -> goal.copy(targetAge = null, targetDate = targetDate.toInstantAtStart())
        }
        if (goal.category != GoalCategory.TRAVEL) goal = goal.copy(countryCode = null)
        model.saveGoal(goal)
        navigator.pop()
    }

    val data = model.data.value
    val suggestions = if (isNew) GoalSuggestions.ideas(draft.title, data.goals.map { it.title }.toSet()) else emptyList()
    val detectedCountry = GoalSuggestions.country(draft.title)?.takeIf { draft.countryCode == null && it != dismissedCountry }

    SheetScaffold(
        title = if (isNew) "Новая цель" else "Цель",
        onBack = { navigator.pop() },
        backLabel = "Отмена",
        actions = {
            TextButton(onClick = ::save, enabled = draft.title.isNotBlank()) {
                Text("Сохранить", color = if (draft.title.isNotBlank()) Palette.accent else Palette.tertiaryText, fontSize = 16.sp)
            }
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Название и подсказки
            FormSection(footer = if (suggestions.isNotEmpty()) "Похожие идеи из библиотеки — нажми, чтобы подставить." else null) {
                TextField(
                    value = draft.title,
                    onValueChange = ::setTitle,
                    placeholder = { Text("Что ты хочешь успеть?") },
                    colors = plainFieldColors(),
                    textStyle = TextStyle(fontSize = 17.sp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth().focusRequester(titleFocus),
                )
                suggestions.forEach { idea ->
                    FormDivider()
                    SuggestionRow(idea) {
                        draft = draft.copy(title = idea.title, category = idea.category)
                        categoryTouched = true
                    }
                }
                if (detectedCountry != null) {
                    FormDivider()
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            Modifier.weight(1f).clickable {
                                draft = draft.copy(countryCode = detectedCountry, category = GoalCategory.TRAVEL)
                                categoryTouched = true
                            }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Outlined.Place, contentDescription = null, tint = Palette.accent, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.size(8.dp))
                            Text("Привязать страну: ${flagEmoji(detectedCountry)} ${Country.name(detectedCountry)}", color = Palette.accent, fontSize = 15.sp)
                        }
                        IconButton(onClick = { dismissedCountry = detectedCountry }) {
                            Icon(Icons.Filled.Close, contentDescription = "Не привязывать", tint = Palette.tertiaryText, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            // Категория
            FormSection(header = "Категория") {
                PillRow(Modifier.padding(vertical = 12.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                    GoalCategory.entries.forEach { category ->
                        Pill(category.title, draft.category == category, icon = category.icon) {
                            draft = draft.copy(category = category)
                            categoryTouched = true
                        }
                    }
                }
                if (draft.category == GoalCategory.TRAVEL) {
                    FormDivider()
                    FormRow(
                        "Страна",
                        draft.countryCode?.let { "${flagEmoji(it)} ${Country.name(it)}" } ?: "Не выбрана",
                        onClick = { pickCountry = true },
                    ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Palette.tertiaryText) }
                }
            }

            // Срок
            val presets = remember(roundAges) {
                val today = LocalDate.now()
                buildList {
                    add(DeadlinePreset("До конца года", DeadlineMode.DATE, date = LocalDate.of(today.year, 12, 31)))
                    add(DeadlinePreset("Через год", DeadlineMode.DATE, date = today.plusYears(1)))
                    add(DeadlinePreset("Через 3 года", DeadlineMode.DATE, date = today.plusYears(3)))
                    add(DeadlinePreset("Через 5 лет", DeadlineMode.DATE, date = today.plusYears(5)))
                    roundAges.forEach { add(DeadlinePreset(Format.byAge(it).capitalizedFirst, DeadlineMode.AGE, age = it)) }
                }
            }
            FormSection(header = "Срок", footer = deadlineFooter(mode, targetAge, targetDate, stats)) {
                PillRow(Modifier.padding(vertical = 12.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                    presets.forEach { preset ->
                        val active = preset.mode == mode && (preset.age?.let { it == targetAge } ?: (preset.date == targetDate))
                        Pill(preset.title, active) {
                            mode = preset.mode
                            preset.date?.let { targetDate = it }
                            preset.age?.let { targetAge = it }
                        }
                    }
                }
                Segmented(DeadlineMode.entries.map { it to it.title }, mode, Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) { mode = it }
                when (mode) {
                    DeadlineMode.AGE -> {
                        FormDivider()
                        StepperRow(Format.byAge(targetAge).capitalizedFirst, targetAge, 1..120) { targetAge = it }
                    }
                    DeadlineMode.DATE -> {
                        FormDivider()
                        FormRow("Дата", Format.date(targetDate.toInstantAtStart()), onClick = { pickDate = true })
                    }
                    DeadlineMode.NONE -> Unit
                }
            }

            // Шаги
            FormSection(header = "Шаги", footer = "Большую цель проще начать с маленького шага. Ближайший шаг будет виден на главном экране и в виджете.") {
                draft.steps.forEach { step ->
                    Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            draft = draft.copy(steps = draft.steps.map { if (it.id == step.id) it.copy(isDone = !it.isDone) else it })
                        }) {
                            Icon(
                                if (step.isDone) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (step.isDone) Palette.accent else Palette.tertiaryText,
                            )
                        }
                        TextField(
                            value = step.title,
                            onValueChange = { text -> draft = draft.copy(steps = draft.steps.map { if (it.id == step.id) it.copy(title = text) else it }) },
                            colors = plainFieldColors(),
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { draft = draft.copy(steps = draft.steps.filterNot { it.id == step.id }) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Удалить шаг", tint = Palette.tertiaryText, modifier = Modifier.size(18.dp))
                        }
                    }
                    FormDivider()
                }
                Row(Modifier.fillMaxWidth().padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = ::addStep) { Icon(Icons.Filled.AddCircle, contentDescription = "Добавить шаг", tint = Palette.accent) }
                    TextField(
                        value = newStep,
                        onValueChange = { newStep = it },
                        placeholder = { Text("Добавить шаг") },
                        colors = plainFieldColors(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next, capitalization = KeyboardCapitalization.Sentences),
                        keyboardActions = KeyboardActions(onNext = { addStep() }),
                        modifier = Modifier.weight(1f),
                    )
                }
                val templates = stepTemplates(draft.category)
                if (draft.steps.isEmpty() && templates.isNotEmpty()) {
                    FormDivider()
                    Row(
                        Modifier.fillMaxWidth().clickable { draft = draft.copy(steps = templates.map { GoalStep(title = it) }) }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.AutoFixHigh, contentDescription = null, tint = Palette.accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Предложить шаги", color = Palette.accent, fontSize = 15.sp)
                    }
                }
            }

            FormSection(header = "Зачем?") {
                TextField(
                    value = draft.note,
                    onValueChange = { draft = draft.copy(note = it) },
                    placeholder = { Text("Почему это важно для тебя") },
                    colors = plainFieldColors(),
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (!draft.isCompleted) {
                val canFocus = draft.isFocused || data.canFocus(excluding = draft.id)
                FormSection(
                    footer = if (!canFocus) "В фокусе уже три цели. Сначала убери одну из них." else "Цели в фокусе видны на главном экране и в виджете. Не больше трёх.",
                ) {
                    FormRow("В фокусе") {
                        Switch(
                            checked = draft.isFocused,
                            onCheckedChange = { draft = draft.copy(isFocused = it) },
                            enabled = canFocus,
                            colors = SwitchDefaults.colors(checkedTrackColor = Palette.accent, checkedThumbColor = Color.Black),
                        )
                    }
                }
            }

            if (!isNew) {
                FormSection {
                    Text(
                        "Удалить цель",
                        color = Color(0xFFFF6B5E),
                        fontSize = 16.sp,
                        modifier = Modifier.fillMaxWidth().clickable { confirmDelete = true }.padding(16.dp),
                    )
                }
            }
        }
    }

    if (pickCountry) {
        CountryPickerDialog(
            title = "Страна",
            items = allCountryItems,
            selected = draft.countryCode,
            noneTitle = "Без страны",
            onDismiss = { pickCountry = false },
        ) { code ->
            val previousName = draft.countryCode?.let { Country.name(it) }
            draft = draft.copy(countryCode = code)
            if (code != null && (draft.title.isBlank() || draft.title.trim() == previousName)) draft = draft.copy(title = Country.name(code))
            pickCountry = false
        }
    }
    if (pickDate) {
        DatePickerSheet(targetDate, onDismiss = { pickDate = false }, onPick = { targetDate = it })
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить цель?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    model.deleteGoal(draft.id)
                    navigator.pop()
                    if (navigator.stack.lastOrNull() is app.mementomori.ui.Screen.GoalDetail) navigator.pop()
                }) { Text("Удалить", color = Color(0xFFFF6B5E)) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun SuggestionRow(idea: Idea, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.Lightbulb, contentDescription = null, tint = Palette.accent, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(10.dp))
        Text(idea.title, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Icon(idea.category.icon, contentDescription = null, tint = Palette.tertiaryText, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun plainFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    cursorColor = Palette.accent,
)

@Composable
fun outlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = Color.Transparent,
    unfocusedContainerColor = Palette.card,
    focusedContainerColor = Palette.card,
)

private fun stepTemplates(category: GoalCategory): List<String> = when (category) {
    GoalCategory.TRAVEL -> listOf("Выбрать даты", "Купить билеты", "Забронировать жильё", "Составить маршрут")
    GoalCategory.SKILLS -> listOf("Найти курс или учителя", "Заниматься 3 раза в неделю", "Первый маленький результат")
    GoalCategory.BODY -> listOf("Составить план тренировок", "Первая тренировка", "Промежуточная цель")
    GoalCategory.CREATE -> listOf("Набросать план", "Сделать первый черновик", "Показать кому-нибудь")
    GoalCategory.PEOPLE -> listOf("Выбрать день", "Договориться о встрече")
    GoalCategory.EXPERIENCE -> listOf("Найти, где это можно сделать", "Выбрать дату", "Записаться")
    GoalCategory.MOVIES, GoalCategory.BOOKS, GoalCategory.OTHER -> emptyList()
}

/** «Это март 2027, через 5 месяцев — 1% оставшейся жизни. Тебе будет 35 лет.» */
private fun deadlineFooter(mode: DeadlineMode, targetAge: Int, targetDate: LocalDate, stats: LifeStats?): String {
    stats ?: return ""
    val deadline = when (mode) {
        DeadlineMode.NONE -> return "Цель со сроком сбывается чаще. Выбери вариант выше — или назначь свой."
        DeadlineMode.AGE -> stats.birthDate.atZone(stats.zone).plusYears(targetAge.toLong()).toInstant()
        DeadlineMode.DATE -> targetDate.toInstantAtStart(stats.zone)
    }
    if (!deadline.isAfter(stats.now)) return "Этот срок уже прошёл — выбери другой."
    val weeksLeft = maxOf(0L, ChronoUnit.DAYS.between(stats.weekStart(stats.now).localDate(stats.zone), stats.weekStart(deadline).localDate(stats.zone)) / 7)
    var text = "Это ${Format.monthYear(deadline)}, ${Format.relative(stats.now, deadline)}"
    if (stats.remainingWeeks > 0) {
        val share = weeksLeft.toDouble() / stats.remainingWeeks
        text += " — ${if (share < 0.01) "меньше 1%" else Format.percent(share, 0)} оставшейся жизни"
    }
    if (mode == DeadlineMode.DATE) text += ". Тебе будет ${Format.years(stats.age(deadline))}"
    return "$text."
}
