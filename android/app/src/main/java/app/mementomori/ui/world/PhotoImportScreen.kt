package app.mementomori.ui.world

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.NoPhotography
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mementomori.core.Country
import app.mementomori.core.Format
import app.mementomori.core.PhotoCountryHit
import app.mementomori.core.flagEmoji
import app.mementomori.photos.PhotoCountryScanner
import app.mementomori.ui.AccentButton
import app.mementomori.ui.LocalModel
import app.mementomori.ui.LocalNavigator
import app.mementomori.ui.SheetScaffold
import app.mementomori.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

private sealed interface ImportPhase {
    data object Intro : ImportPhase
    data object Scanning : ImportPhase
    data object Results : ImportPhase
    data object Denied : ImportPhase
}

/** «Найти страны в фото»: по геометкам снимков предлагает отметить страны посещёнными. */
@Composable
fun PhotoImportScreen(demo: Boolean) {
    val model = LocalModel.current
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phase by remember { mutableStateOf<ImportPhase>(if (demo) ImportPhase.Results else ImportPhase.Intro) }
    var progress by remember { mutableDoubleStateOf(0.0) }
    var result by remember { mutableStateOf(if (demo) demoResult() else null) }
    var limited by remember { mutableStateOf(false) }
    var selected by remember {
        mutableStateOf(result?.countries?.map { it.code }?.filterNot { model.data.value.isVisited(it) }?.toSet() ?: emptySet())
    }

    fun scan() {
        phase = ImportPhase.Scanning
        scope.launch {
            val scanned = PhotoCountryScanner.scan(context) { value -> scope.launch(Dispatchers.Main) { progress = value } }
            result = scanned
            selected = scanned.countries.map { it.code }.filterNot { model.data.value.isVisited(it) }.toSet()
            phase = ImportPhase.Results
        }
    }

    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        when (PhotoCountryScanner.access(context)) {
            PhotoCountryScanner.Access.DENIED -> phase = ImportPhase.Denied
            PhotoCountryScanner.Access.LIMITED -> { limited = true; scan() }
            PhotoCountryScanner.Access.FULL -> scan()
        }
    }

    SheetScaffold(title = "Страны из фото", onBack = { navigator.pop() }, backLabel = "Закрыть") { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (phase) {
                ImportPhase.Intro -> Message(
                    icon = Icons.Outlined.PhotoLibrary,
                    title = "Заполни карту за минуту",
                    text = "Приложение посмотрит геометки в фото и найдёт страны, где они сняты. Отметишь посещёнными только те, что выберешь.",
                    note = "Всё считается на телефоне: фото никуда не отправляются, читаются только место и дата съёмки.",
                    button = "Найти страны",
                ) { permissions.launch(PhotoCountryScanner.permissions()) }
                ImportPhase.Scanning -> Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    LinearProgressIndicator(progress = { progress.toFloat() }, color = Palette.accent, trackColor = Palette.future, modifier = Modifier.width(240.dp))
                    Text(if (progress < 1) "Смотрю фото… ${Format.percent(progress, 0)}" else "Определяю страны…", color = Palette.secondaryText)
                }
                ImportPhase.Denied -> Message(
                    icon = Icons.Outlined.NoPhotography,
                    title = "Нет доступа к фото",
                    text = "Разреши доступ в настройках телефона: Приложения → Memento Mori → Разрешения → Фото и видео.",
                    note = null,
                    button = "Открыть настройки",
                ) {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                }
                ImportPhase.Results -> Results(result, selected, limited, onToggle = { code ->
                    selected = if (code in selected) selected - code else selected + code
                }) {
                    model.markVisited(selected.sorted())
                    navigator.pop()
                }
            }
        }
    }
}

@Composable
private fun Message(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String, note: String?, button: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.weight(1f))
        Icon(icon, contentDescription = null, tint = Palette.accent, modifier = Modifier.size(56.dp))
        Spacer(Modifier.size(18.dp))
        Text(title, fontFamily = FontFamily.Serif, fontSize = 24.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.size(12.dp))
        Text(text, color = Palette.secondaryText, textAlign = TextAlign.Center)
        if (note != null) {
            Spacer(Modifier.size(16.dp))
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = Palette.tertiaryText, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(6.dp))
                Text(note, color = Palette.tertiaryText, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.weight(1f))
        AccentButton(button, icon = Icons.Outlined.Search, onClick = onClick)
    }
}

@Composable
private fun Results(
    result: PhotoCountryScanner.Result?,
    selected: Set<String>,
    limited: Boolean,
    onToggle: (String) -> Unit,
    onApply: () -> Unit,
) {
    val model = LocalModel.current
    val found = result?.countries ?: emptyList()
    val fresh = found.filterNot { model.data.value.isVisited(it.code) }
    val known = found.filter { model.data.value.isVisited(it.code) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 100.dp)) {
            item {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        if (found.isEmpty()) "Стран не нашлось" else "Нашлось ${Format.count(found.size, "страна", "страны", "стран")}",
                        fontFamily = FontFamily.Serif,
                        fontSize = 26.sp,
                    )
                    Text(summary(result, found), color = Palette.secondaryText, fontSize = 15.sp)
                }
            }
            if (fresh.isNotEmpty()) {
                item { Header("Новые — отметить посещёнными") }
                items(fresh, key = { "new-" + it.code }) { hit -> HitRow(hit, hit.code in selected, false) { onToggle(hit.code) } }
                item { Footer("Сними галочку, если фото попало туда случайно — например, прислали чужое.") }
            }
            if (known.isNotEmpty()) {
                item { Header("Уже отмечены") }
                items(known, key = { "known-" + it.code }) { hit -> HitRow(hit, true, true) {} }
            }
            if (limited) {
                item { Footer("Есть доступ только к выбранным фото, поэтому страны найдены только по ним. Открыть все фото можно в настройках телефона.") }
            }
        }
        if (fresh.isNotEmpty()) {
            AccentButton(
                if (selected.isEmpty()) "Ничего не выбрано" else "Отметить ${Format.count(selected.size, "страну", "страны", "стран")}",
                icon = Icons.Filled.Check,
                enabled = selected.isNotEmpty(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(20.dp),
                onClick = onApply,
            )
        }
    }
}

private fun summary(result: PhotoCountryScanner.Result?, found: List<PhotoCountryHit>): String {
    result ?: return ""
    return when {
        result.scanned == 0 -> "В галерее пока нет фото."
        result.geotagged == 0 -> "У фото нет геометок. Чтобы камера их сохраняла, включи в настройках камеры «Сохранять местоположение»."
        found.isEmpty() -> "Геометки есть у ${Format.number(result.geotagged)} фото, но ни одна не попала в страну из списка."
        else -> "По ${Format.number(result.geotagged)} фото с геометками из ${Format.number(result.scanned)}."
    }
}

@Composable
private fun Header(text: String) {
    Text(text, color = Palette.secondaryText, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 6.dp))
}

@Composable
private fun Footer(text: String) {
    Text(text, color = Palette.secondaryText, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
}

@Composable
private fun HitRow(hit: PhotoCountryHit, isSelected: Boolean, isKnown: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !isKnown, onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(flagEmoji(hit.code), fontSize = 26.sp)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(Country.name(hit.code), color = if (isKnown) Palette.secondaryText else androidx.compose.ui.graphics.Color.White, fontSize = 16.sp)
            Text(listOfNotNull(Format.number(hit.photos) + " фото", hit.yearsText()).joinToString(" · "), color = Palette.tertiaryText, fontSize = 12.sp)
        }
        Icon(
            when {
                isKnown -> Icons.Outlined.Verified
                isSelected -> Icons.Filled.CheckCircle
                else -> Icons.Outlined.RadioButtonUnchecked
            },
            contentDescription = null,
            tint = if (isSelected && !isKnown) Palette.accent else Palette.tertiaryText,
        )
    }
}

/** Пример результата для скриншотов. */
private fun demoResult(): PhotoCountryScanner.Result {
    fun year(value: Int) = LocalDate.of(value, 6, 1).atStartOfDay(ZoneId.systemDefault()).toInstant()
    val hits = listOf(
        PhotoCountryHit("RU", 2431, year(2014), year(2026)),
        PhotoCountryHit("IT", 132, year(2019), year(2024)),
        PhotoCountryHit("TH", 120, year(2023), year(2023)),
        PhotoCountryHit("FR", 87, year(2018), year(2025)),
        PhotoCountryHit("ES", 64, year(2022), year(2022)),
        PhotoCountryHit("GR", 45, year(2021), year(2021)),
        PhotoCountryHit("GE", 41, year(2025), year(2025)),
        PhotoCountryHit("PT", 28, year(2017), year(2017)),
        PhotoCountryHit("TR", 33, year(2016), year(2024)),
        PhotoCountryHit("CZ", 19, year(2015), year(2015)),
        PhotoCountryHit("AT", 12, year(2015), year(2015)),
        PhotoCountryHit("NL", 9, year(2019), year(2019)),
        PhotoCountryHit("HU", 4, year(2015), year(2015)),
    )
    return PhotoCountryScanner.Result(hits, 18_204, 3_412)
}
