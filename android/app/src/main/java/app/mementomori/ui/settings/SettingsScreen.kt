package app.mementomori.ui.settings

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mementomori.BuildConfig
import app.mementomori.core.AppData
import app.mementomori.core.CountryCatalog
import app.mementomori.core.ExpectancyChoice
import app.mementomori.core.ExpectancySource
import app.mementomori.core.Format
import app.mementomori.core.LifeExpectancyData
import app.mementomori.core.LifeUnit
import app.mementomori.core.Profile
import app.mementomori.core.ReminderFrequency
import app.mementomori.core.localDate
import app.mementomori.reminders.ReminderScheduler
import app.mementomori.ui.CountryPickerDialog
import app.mementomori.ui.DatePickerSheet
import app.mementomori.ui.FormDivider
import app.mementomori.ui.FormRow
import app.mementomori.ui.FormSection
import app.mementomori.ui.LocalModel
import app.mementomori.ui.LocalNavigator
import app.mementomori.ui.Pill
import app.mementomori.ui.PillRow
import app.mementomori.ui.PickerItem
import app.mementomori.ui.Segmented
import app.mementomori.ui.SheetScaffold
import app.mementomori.ui.StepperRow
import app.mementomori.ui.theme.Palette
import app.mementomori.ui.toInstantAtStart
import app.mementomori.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Страна для средней продолжительности жизни: «🇷🇺 Россия» или «🌍 Весь мир». */
fun expectancyLabel(code: String): String =
    if (code == LifeExpectancyData.WORLD_CODE) "🌍 Весь мир" else "${app.mementomori.core.flagEmoji(code)} ${app.mementomori.core.Country.name(code)}"

val expectancyItems: List<PickerItem> by lazy {
    listOf(PickerItem(LifeExpectancyData.WORLD_CODE, "Весь мир", "🌍", Format.decimal(LifeExpectancyData.WORLD))) +
        CountryCatalog.all.mapNotNull { country ->
            LifeExpectancyData.value(country.code)?.let { PickerItem(country.code, country.name, country.flag, Format.decimal(it)) }
        }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val model = LocalModel.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val data by model.data.collectAsStateWithLifecycle()
    val profile = data.profile ?: return
    var expectancy by remember { mutableStateOf(ExpectancyChoice.of(profile)) }
    var birthDate by remember { mutableStateOf(profile.birthDate.localDate()) }
    var pickBirth by remember { mutableStateOf(false) }
    var pickCountry by remember { mutableStateOf(false) }
    var pickTime by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<AppData?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    var widgetUnit by remember { mutableStateOf(model.prefs.widgetUnit) }
    var canNotify by remember { mutableStateOf(ReminderScheduler.canNotify(context)) }
    val reminders = data.reminders

    fun saveProfile(date: LocalDate = birthDate, choice: ExpectancyChoice = expectancy) {
        birthDate = date
        expectancy = choice
        model.updateProfile(date, choice)
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        canNotify = granted
        model.updateReminders(reminders.copy(isEnabled = true))
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(model.exportJson().toByteArray()) } }
            .onSuccess { Toast.makeText(context, "Резервная копия сохранена", Toast.LENGTH_SHORT).show() }
            .onFailure { Toast.makeText(context, "Не удалось сохранить файл", Toast.LENGTH_LONG).show() }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.openInputStream(uri)?.use { model.parseBackup(it.readBytes().decodeToString()) } }
            .onSuccess { pendingImport = it }
            .onFailure { Toast.makeText(context, "Это не похоже на резервную копию Memento Mori", Toast.LENGTH_LONG).show() }
    }

    SheetScaffold(title = "Настройки", onBack = { navigator.pop() }, backLabel = "Готово") { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            FormSection(
                header = "Ты",
                footer = if (expectancy.source == ExpectancySource.COUNTRY) {
                    "Средняя продолжительность жизни при рождении. ${LifeExpectancyData.ATTRIBUTION}"
                } else {
                    "В среднем по миру люди живут около 73 лет. Это не прогноз, а точка отсчёта — выбери число, которое тебя мотивирует."
                },
            ) {
                FormRow("Дата рождения", Format.date(birthDate.toInstantAtStart()), onClick = { pickBirth = true })
                FormDivider()
                Segmented(ExpectancySource.entries.map { it to it.title }, expectancy.source, Modifier.padding(16.dp)) {
                    saveProfile(choice = expectancy.copy(source = it))
                }
                if (expectancy.source == ExpectancySource.COUNTRY) {
                    FormRow("Страна", expectancyLabel(expectancy.country), onClick = { pickCountry = true }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Palette.tertiaryText)
                    }
                    FormDivider()
                    FormRow("Средняя продолжительность", Format.years(expectancy.years))
                } else {
                    StepperRow("Ожидаемая жизнь: ${Format.years(expectancy.customYears)}", expectancy.customYears, Profile.LIFE_EXPECTANCY_RANGE) {
                        saveProfile(choice = expectancy.copy(customYears = it))
                    }
                }
            }

            FormSection(
                header = "Напоминания",
                footer = if (reminders.isEnabled && !canNotify) {
                    "Уведомления запрещены. Включи их: Настройки телефона → Приложения → Memento Mori → Уведомления."
                } else {
                    "Мысль дня, цель из твоего списка или просто «помни». О сроках — за месяц и в сам день. Письма в будущее и «давно не было» приходят в это же время."
                },
            ) {
                FormRow("Напоминания") {
                    Switch(
                        checked = reminders.isEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !ReminderScheduler.canNotify(context)) {
                                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                model.updateReminders(reminders.copy(isEnabled = enabled))
                            }
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = Palette.accent, checkedThumbColor = Color.Black),
                    )
                }
                if (reminders.isEnabled) {
                    FormDivider()
                    Segmented(ReminderFrequency.entries.map { it to it.title }, reminders.frequency, Modifier.padding(16.dp)) {
                        model.updateReminders(reminders.copy(frequency = it))
                    }
                    if (reminders.frequency == ReminderFrequency.WEEKLY) {
                        // 1 — воскресенье, 2 — понедельник… (как на iPhone).
                        val days = listOf(2 to "Пн", 3 to "Вт", 4 to "Ср", 5 to "Чт", 6 to "Пт", 7 to "Сб", 1 to "Вс")
                        PillRow(Modifier.padding(bottom = 12.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                            days.forEach { (number, title) ->
                                Pill(title, reminders.weekday == number) { model.updateReminders(reminders.copy(weekday = number)) }
                            }
                        }
                    }
                    FormDivider()
                    FormRow("Время", Format.time(reminders.hour, reminders.minute), onClick = { pickTime = true })
                    FormDivider()
                    FormRow("Напоминать о сроках целей") {
                        Switch(
                            checked = reminders.deadlineAlerts,
                            onCheckedChange = { model.updateReminders(reminders.copy(deadlineAlerts = it)) },
                            colors = SwitchDefaults.colors(checkedTrackColor = Palette.accent, checkedThumbColor = Color.Black),
                        )
                    }
                    FormDivider()
                    Text(
                        "Прислать пробное уведомление",
                        color = Palette.accent,
                        modifier = Modifier.fillMaxWidth().clickable { ReminderScheduler.sendTest(context, data) }.padding(16.dp),
                    )
                }
            }

            FormSection(
                header = "Виджеты",
                footer = "Удерживай палец на свободном месте главного экрана → «Виджеты» → Memento Mori. Виджет «Время жизни» считает в выбранных единицах; секунды тикают прямо на экране.",
            ) {
                Text("«Время жизни» считает в", color = Palette.secondaryText, fontSize = 14.sp, modifier = Modifier.padding(start = 16.dp, top = 14.dp))
                PillRow(Modifier.padding(vertical = 12.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                    LifeUnit.entries.forEach { unit ->
                        Pill(unit.title.substringBefore(" ("), widgetUnit == unit) {
                            widgetUnit = unit
                            model.prefs.widgetUnit = unit
                            CoroutineScope(Dispatchers.Default).launch { WidgetUpdater.updateAll(context) }
                        }
                    }
                }
            }

            FormSection(header = "Данные", footer = "Данные хранятся только на этом телефоне. Резервная копия — обычный JSON-файл; его можно открыть и в версии для iPhone.") {
                Text("Сохранить резервную копию", color = Palette.accent, modifier = Modifier.fillMaxWidth().clickable {
                    exporter.launch("memento-mori-${LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE)}.json")
                }.padding(16.dp))
                FormDivider()
                Text("Восстановить из файла", color = Palette.accent, modifier = Modifier.fillMaxWidth().clickable {
                    importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
                }.padding(16.dp))
                FormDivider()
                Text("Удалить все данные", color = Color(0xFFFF6B5E), modifier = Modifier.fillMaxWidth().clickable { confirmReset = true }.padding(16.dp))
            }

            FormSection(header = "О приложении") {
                FormRow("Версия", BuildConfig.VERSION_NAME)
                FormDivider()
                Text(
                    "Memento Mori — помни о смерти, чтобы жить. Карта: Natural Earth (общественное достояние). ${LifeExpectancyData.ATTRIBUTION}",
                    color = Palette.secondaryText,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    if (pickBirth) {
        DatePickerSheet(birthDate, onDismiss = { pickBirth = false }, onPick = { saveProfile(date = it) }, min = LocalDate.now().minusYears(110), max = LocalDate.now(), typing = true)
    }
    if (pickCountry) {
        CountryPickerDialog(
            title = "Страна",
            items = expectancyItems,
            selected = expectancy.country,
            footer = "Средняя продолжительность жизни при рождении, лет. ${LifeExpectancyData.ATTRIBUTION}",
            onDismiss = { pickCountry = false },
        ) { code ->
            code?.let { saveProfile(choice = expectancy.selecting(it)) }
            pickCountry = false
        }
    }
    if (pickTime) {
        val state = rememberTimePickerState(reminders.hour, reminders.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = {
                    model.updateReminders(reminders.copy(hour = state.hour, minute = state.minute))
                    pickTime = false
                }) { Text("Готово") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Отмена") } },
            text = { TimePicker(state) },
        )
    }
    pendingImport?.let { imported ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Заменить текущие данные?") },
            text = { Text("В файле ${Format.goals(imported.goals.size)}. Всё, что сейчас в приложении, будет заменено.") },
            confirmButton = {
                TextButton(onClick = {
                    model.replaceAll(imported)
                    pendingImport = null
                    Toast.makeText(context, "Данные восстановлены", Toast.LENGTH_SHORT).show()
                }) { Text("Заменить", color = Color(0xFFFF6B5E)) }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Отмена") } },
        )
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Удалить все данные?") },
            text = { Text("Цели, воспоминания, фото и настройки будут удалены без возможности восстановления.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    model.resetAll()
                    navigator.pop()
                }) { Text("Удалить всё", color = Color(0xFFFF6B5E)) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Отмена") } },
        )
    }
}
