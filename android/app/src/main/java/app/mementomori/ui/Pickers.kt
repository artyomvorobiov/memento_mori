package app.mementomori.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.mementomori.core.CountryCatalog
import app.mementomori.ui.theme.Palette
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

fun LocalDate.toInstantAtStart(zone: ZoneId = ZoneId.systemDefault()): Instant = atStartOfDay(zone).toInstant()

/** Выбор даты. Календарь Material работает в UTC — переводим аккуратно, чтобы не съехать на день. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerSheet(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
    min: LocalDate? = null,
    max: LocalDate? = null,
    typing: Boolean = false,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        yearRange = (min?.year ?: 1900)..(max?.year ?: 2200),
        initialDisplayMode = if (typing) DisplayMode.Input else DisplayMode.Picker,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val date = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                return (min == null || !date.isBefore(min)) && (max == null || !date.isAfter(max))
            }

            override fun isSelectableYear(year: Int): Boolean = (min == null || year >= min.year) && (max == null || year <= max.year)
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("Готово") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    ) {
        DatePicker(state)
    }
}

/** Строка выбора числа: «−  к 40 годам  +». */
@Composable
fun StepperRow(title: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 16.sp, modifier = Modifier.weight(1f))
        StepButton(Icons.Filled.Remove, enabled = value > range.first) { onChange(value - 1) }
        Spacer(Modifier.size(8.dp))
        StepButton(Icons.Filled.Add, enabled = value < range.last) { onChange(value + 1) }
    }
}

@Composable
private fun StepButton(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(40.dp).clip(CircleShape).background(Palette.chip),
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) Color.White else Palette.tertiaryText)
    }
}

/** Строка списка для выбора страны. */
data class PickerItem(val code: String, val title: String, val flag: String, val trailing: String? = null)

val allCountryItems: List<PickerItem> by lazy { CountryCatalog.all.map { PickerItem(it.code, it.name, it.flag) } }

/** Полноэкранный список с поиском: страна цели или страна для средней продолжительности жизни. */
@Composable
fun CountryPickerDialog(
    title: String,
    items: List<PickerItem>,
    selected: String?,
    onDismiss: () -> Unit,
    footer: String? = null,
    noneTitle: String? = null,
    onPick: (String?) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Palette.background).safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss) { Text("Отмена", color = Palette.accent) }
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, modifier = Modifier.weight(1f).padding(end = 72.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Найти страну") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Transparent, unfocusedContainerColor = Palette.card, focusedContainerColor = Palette.card),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            val filtered = items.filter { query.isBlank() || it.title.contains(query.trim(), ignoreCase = true) }
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                if (noneTitle != null && selected != null) {
                    item {
                        Text(
                            noneTitle,
                            color = Color(0xFFFF6B5E),
                            fontSize = 16.sp,
                            modifier = Modifier.fillMaxWidth().clickable { onPick(null) }.padding(horizontal = 20.dp, vertical = 14.dp),
                        )
                    }
                }
                items(filtered, key = { it.code }) { item ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(item.code) }.padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(item.flag, fontSize = 22.sp)
                        Spacer(Modifier.size(12.dp))
                        Text(item.title, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        item.trailing?.let { Text(it, color = Palette.secondaryText) }
                        if (item.code == selected) {
                            Spacer(Modifier.size(8.dp))
                            Icon(Icons.Filled.Check, contentDescription = null, tint = Palette.accent)
                        }
                    }
                }
                if (footer != null) {
                    item { Text(footer, color = Palette.secondaryText, fontSize = 13.sp, modifier = Modifier.padding(20.dp)) }
                }
            }
        }
    }
}
