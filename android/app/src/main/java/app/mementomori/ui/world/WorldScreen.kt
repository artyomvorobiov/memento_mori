package app.mementomori.ui.world

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mementomori.core.Continent
import app.mementomori.core.Country
import app.mementomori.core.CountryCatalog
import app.mementomori.core.CountryStatus
import app.mementomori.core.Format
import app.mementomori.core.Profile
import app.mementomori.core.RussianPlural
import app.mementomori.core.TravelStats
import app.mementomori.core.flagEmoji
import app.mementomori.ui.LocalModel
import app.mementomori.ui.LocalNavigator
import app.mementomori.ui.Screen
import app.mementomori.ui.Segmented
import app.mementomori.ui.ThinProgressBar
import app.mementomori.ui.goals.outlinedFieldColors
import app.mementomori.ui.theme.Palette
import java.util.Locale

private enum class CountryFilter(val title: String) { VISITED("Посещено"), WISHED("В планах"), ALL("Все") }

/** Вкладка «Мир»: интерактивная карта путешествий, части света и список стран. */
@Composable
fun WorldScreen() {
    val model = LocalModel.current
    val navigator = LocalNavigator.current
    val haptic = LocalHapticFeedback.current
    val data by model.data.collectAsStateWithLifecycle()
    val travel = TravelStats(data)
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    var focus by remember { mutableStateOf<FocusRequest?>(null) }
    var filter by rememberSaveable { mutableStateOf(CountryFilter.VISITED) }
    var query by rememberSaveable { mutableStateOf("") }

    fun toggleVisited(code: String) {
        if (!data.isVisited(code)) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        model.toggleVisited(code)
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Мир", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = { navigator.push(Screen.TravelShare) }) {
                    Icon(Icons.Outlined.Share, contentDescription = "Поделиться картой", tint = Palette.accent)
                }
            }
            InteractiveWorldMap(
                visited = travel.visited,
                wished = travel.wished,
                selected = selected,
                onSelect = { selected = it },
                focusRequest = focus,
                initialCenter = Locale.getDefault().country.ifBlank { "RU" },
                modifier = Modifier.fillMaxWidth().height(300.dp).background(Color(0xFF0A0A0A)),
            )
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = if (selected != null) 200.dp else 24.dp)) {
                item { Summary(travel) { navigator.push(Screen.PhotoImport()) } }
                item {
                    Text("Части света", color = Palette.secondaryText, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 8.dp))
                    Column(
                        Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(16.dp)).background(Palette.card).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Continent.entries.forEach { continent ->
                            val (visited, total) = travel.progress(continent)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row {
                                    Text(continent.title, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                    Text("$visited из $total", fontSize = 15.sp, color = if (visited > 0) Color.White else Palette.tertiaryText)
                                }
                                ThinProgressBar(if (total > 0) visited.toDouble() / total else 0.0, height = 3.dp, fill = Palette.accent)
                            }
                        }
                    }
                }
                item {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Segmented(CountryFilter.entries.map { it to it.title }, filter) { filter = it }
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text("Найти страну") },
                            leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = outlinedFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                val countries = CountryCatalog.all.filter { country ->
                    (query.isBlank() || country.name.contains(query.trim(), ignoreCase = true)) &&
                        when (filter) {
                            CountryFilter.VISITED -> country.code in travel.visited
                            CountryFilter.WISHED -> country.code in travel.wished
                            CountryFilter.ALL -> true
                        }
                }
                if (countries.isEmpty()) {
                    item {
                        Text(
                            when {
                                query.isNotBlank() -> "Ничего не найдено."
                                filter == CountryFilter.VISITED -> "Нажми на страну на карте и отметь «Посещено» — или найди страны в фото."
                                filter == CountryFilter.WISHED -> "Отметь страны, куда хочешь поехать, — они попадут в цели."
                                else -> "Ничего не найдено."
                            },
                            color = Palette.secondaryText,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                        )
                    }
                }
                items(countries, key = { it.code }) { country ->
                    CountryRow(
                        country = country,
                        isWished = country.code in travel.wished,
                        isVisited = country.code in travel.visited,
                        onClick = {
                            selected = country.code
                            focus = FocusRequest(country.code)
                        },
                        onToggleWish = { model.toggleWish(country.code) },
                        onToggleVisited = { toggleVisited(country.code) },
                    )
                }
            }
        }
        selected?.let { code ->
            SelectedCountryCard(
                code = code,
                status = travel.status(code),
                wishGoalId = data.wishGoal(code)?.id,
                onToggleVisited = { toggleVisited(code) },
                onToggleWish = { model.toggleWish(code) },
                onOpenGoal = { id -> navigator.push(Screen.GoalDetail(id)) },
                onClose = { selected = null },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

@Composable
private fun Summary(travel: TravelStats, onPhotoImport: () -> Unit) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("${travel.visitedCountries}", fontSize = 48.sp, fontWeight = FontWeight.Thin)
                Text("из ${Format.genitive(travel.totalCountries, "страны", "стран")}", color = Palette.secondaryText, fontSize = 12.sp)
            }
            Stat(Format.percent(travel.worldShare, 0), "мира")
            Spacer(Modifier.size(14.dp))
            Stat(
                "${travel.continentsVisited}",
                RussianPlural.form(travel.continentsVisited, "часть света", "части света", "частей света") + "\nиз 6",
            )
            Spacer(Modifier.size(14.dp))
            Stat("${travel.wishedCount}", "в планах")
        }
        ThinProgressBar(travel.worldShare, fill = Palette.accent)
        travel.nextMilestone?.let { (target, left) ->
            Text(
                if (target == 1) "Отметь первую страну — нажми на неё на карте." else "До ${Format.genitive(target, "страны", "стран")} — ещё $left.",
                color = Palette.secondaryText,
                fontSize = 13.sp,
            )
        }
        if (travel.visitedTerritories > 0) {
            Text(
                "и ещё ${travel.visitedTerritories} ${RussianPlural.form(travel.visitedTerritories, "территория", "территории", "территорий")}",
                color = Palette.tertiaryText,
                fontSize = 12.sp,
            )
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Palette.card).clickable(onClick = onPhotoImport).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.PhotoLibrary, contentDescription = null, tint = Palette.accent)
            Spacer(Modifier.size(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Найти страны в фото", color = Palette.accent, fontWeight = FontWeight.Medium, fontSize = 15.sp)
                if (travel.visitedCountries < 3) {
                    Text("Карта заполнится за минуту — по геометкам снимков.", color = Palette.secondaryText, fontSize = 12.sp)
                }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Palette.tertiaryText)
        }
    }
}

@Composable
private fun Stat(value: String, caption: String) {
    Column(horizontalAlignment = Alignment.End) {
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Light)
        Text(caption, color = Palette.secondaryText, fontSize = 11.sp, textAlign = TextAlign.End, lineHeight = 13.sp)
    }
}

/** Строка страны: звёздочка — «хочу», кружок — «посещено». */
@Composable
private fun CountryRow(
    country: Country,
    isWished: Boolean,
    isVisited: Boolean,
    onClick: () -> Unit,
    onToggleWish: () -> Unit,
    onToggleVisited: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 20.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(country.flag, fontSize = 26.sp)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(country.name, fontSize = 16.sp)
            if (isWished) Text("в списке целей", color = Palette.accent, fontSize = 12.sp)
        }
        IconButton(onClick = onToggleWish) {
            Icon(if (isWished) Icons.Filled.Star else Icons.Filled.StarBorder, contentDescription = if (isWished) "Убрать из целей" else "Хочу побывать", tint = if (isWished) Palette.accent else Palette.tertiaryText)
        }
        IconButton(onClick = onToggleVisited) {
            Icon(
                if (isVisited) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = if (isVisited) "Снять отметку о посещении" else "Отметить посещённой",
                tint = if (isVisited) Palette.accent else Palette.tertiaryText,
            )
        }
    }
}

/** Карточка выбранной на карте страны. */
@Composable
private fun SelectedCountryCard(
    code: String,
    status: CountryStatus,
    wishGoalId: String?,
    onToggleVisited: () -> Unit,
    onToggleWish: () -> Unit,
    onOpenGoal: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier.fillMaxWidth().padding(12.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xF21C1C1E)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(flagEmoji(code), fontSize = 40.sp)
            Spacer(Modifier.size(12.dp))
            Column(Modifier.weight(1f)) {
                Text(Country.name(code), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                val parts = listOfNotNull(
                    Continent.of(code)?.title,
                    Profile.averageLifeExpectancy(code)?.let { "живут в среднем ${Format.years(it)}" },
                )
                Text(parts.joinToString(" · "), color = Palette.secondaryText, fontSize = 12.sp)
            }
            IconButton(onClick = onClose) { Icon(Icons.Filled.Cancel, contentDescription = "Закрыть", tint = Palette.tertiaryText) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ToggleCapsule("Посещено", status == CountryStatus.VISITED, if (status == CountryStatus.VISITED) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked, Modifier.weight(1f), onToggleVisited)
            ToggleCapsule("В планах", wishGoalId != null, if (wishGoalId != null) Icons.Filled.Star else Icons.Filled.StarBorder, Modifier.weight(1f), onToggleWish)
        }
        if (wishGoalId != null) {
            Row(Modifier.clickable { onOpenGoal(wishGoalId) }, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = Palette.accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(6.dp))
                Text("Открыть цель", color = Palette.accent, fontSize = 15.sp)
            }
        }
    }
}

@Composable
private fun ToggleCapsule(title: String, isOn: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(CircleShape).background(if (isOn) Palette.accent else Color.White.copy(alpha = 0.1f)).clickable(onClick = onClick).padding(vertical = 11.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (isOn) Color.Black else Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(6.dp))
        Text(title, color = if (isOn) Color.Black else Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}
