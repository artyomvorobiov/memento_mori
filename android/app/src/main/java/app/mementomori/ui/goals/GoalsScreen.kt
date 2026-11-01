package app.mementomori.ui.goals

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mementomori.core.AppData
import app.mementomori.core.DeadlineText
import app.mementomori.core.Format
import app.mementomori.core.Goal
import app.mementomori.core.GoalCategory
import app.mementomori.core.LifeStats
import app.mementomori.ui.GoalIcon
import app.mementomori.ui.LocalModel
import app.mementomori.ui.LocalNavigator
import app.mementomori.ui.Navigator
import app.mementomori.ui.Pill
import app.mementomori.ui.PillRow
import app.mementomori.ui.Screen
import app.mementomori.ui.Segmented
import app.mementomori.ui.icon
import app.mementomori.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant

@Composable
fun GoalsScreen() {
    val model = LocalModel.current
    val navigator = LocalNavigator.current
    val data by model.data.collectAsStateWithLifecycle()
    var showDone by rememberSaveable { mutableStateOf(false) }
    var category by rememberSaveable { mutableStateOf<GoalCategory?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var focusLimit by remember { mutableStateOf(false) }

    fun filtered(goals: List<Goal>): List<Goal> {
        val text = query.trim()
        return goals.filter { goal ->
            (category == null || goal.category == category) &&
                (text.isEmpty() || goal.title.contains(text, ignoreCase = true) || goal.note.contains(text, ignoreCase = true))
        }
    }

    fun toggleFocus(goal: Goal) {
        if (!model.toggleFocus(goal.id)) focusLimit = true
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Цели", fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.Add, contentDescription = "Добавить", tint = Palette.accent) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(
                        text = { Text("Новая цель") },
                        leadingIcon = { Icon(Icons.Outlined.EditNote, null) },
                        onClick = { menu = false; navigator.push(Screen.GoalEditor(category = category)) },
                    )
                    DropdownMenuItem(
                        text = { Text("Библиотека идей") },
                        leadingIcon = { Icon(Icons.Outlined.Lightbulb, null) },
                        onClick = { menu = false; navigator.push(Screen.Ideas) },
                    )
                    DropdownMenuItem(
                        text = { Text("Страны на карте") },
                        leadingIcon = { Icon(Icons.Outlined.Public, null) },
                        onClick = { menu = false; navigator.tab = Navigator.WORLD },
                    )
                }
            }
        }

        if (data.goals.isEmpty()) {
            EmptyGoals(onNew = { navigator.push(Screen.GoalEditor()) }, onIdeas = { navigator.push(Screen.Ideas) })
        } else LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Найти цель") },
                        leadingIcon = { Icon(Icons.Outlined.Search, null) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color.Transparent, unfocusedContainerColor = Palette.card, focusedContainerColor = Palette.card),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Segmented(
                        listOf(false to "Впереди · ${data.activeGoals.size}", true to "Сделано · ${data.completedGoals.size}"),
                        showDone,
                    ) { showDone = it }
                }
                val source = if (showDone) data.completedGoals else data.activeGoals
                val counts = source.groupingBy { it.category }.eachCount()
                PillRow(Modifier.padding(vertical = 12.dp), contentPadding = PaddingValues(horizontal = 20.dp)) {
                    Pill("Все", category == null) { category = null }
                    GoalCategory.entries.filter { (counts[it] ?: 0) > 0 || category == it }.forEach { item ->
                        Pill(item.title, category == item, icon = item.icon) { category = if (category == item) null else item }
                    }
                }
            }
            if (!showDone) {
                val goals = filtered(model.activeGoals(data))
                if (goals.isEmpty()) {
                    item { EmptyFilter(if (data.activeGoals.isEmpty()) "Всё выполнено. Самое время мечтать дальше." else "Ничего не найдено.") }
                }
                items(goals, key = { it.id }) { goal ->
                    SwipeableGoalRow(
                        goal = goal,
                        data = data,
                        onOpen = { navigator.push(Screen.GoalDetail(goal.id)) },
                        onComplete = { navigator.push(Screen.CompleteGoal(goal.id)) },
                        onDelete = { model.deleteGoal(goal.id) },
                        onToggleFocus = { toggleFocus(goal) },
                    )
                }
            } else {
                val goals = filtered(model.completedGoals(data))
                if (goals.isEmpty()) {
                    item {
                        EmptyFilter(
                            if (data.completedGoals.isEmpty()) "Здесь появятся выполненные цели — с датой, заметкой и фото." else "Ничего не найдено.",
                        )
                    }
                }
                val groups = goals.groupBy { it.completedAt?.atZone(java.time.ZoneId.systemDefault())?.year ?: 0 }
                for ((year, yearGoals) in groups.toSortedMap(compareByDescending { it })) {
                    item(key = "year-$year") {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
                            Text("$year", fontWeight = FontWeight.SemiBold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                            Text(Format.goals(yearGoals.size), color = Palette.tertiaryText, fontSize = 12.sp)
                        }
                    }
                    items(yearGoals, key = { it.id }) { goal ->
                        DoneGoalRow(goal, data) { navigator.push(Screen.GoalDetail(goal.id)) }
                    }
                }
            }
        }
    }

    if (focusLimit) {
        AlertDialog(
            onDismissRequest = { focusLimit = false },
            confirmButton = { TextButton(onClick = { focusLimit = false }) { Text("Понятно") } },
            title = { Text("В фокусе уже три цели") },
            text = { Text("Сначала убери звёздочку с одной из них: фокус работает, только когда целей мало.") },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SwipeableGoalRow(
    goal: Goal,
    data: AppData,
    onOpen: () -> Unit,
    onComplete: () -> Unit,
    onDelete: () -> Unit,
    onToggleFocus: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> { onComplete(); false }
                SwipeToDismissBoxValue.EndToStart -> { onDelete(); true }
                SwipeToDismissBoxValue.Settled -> false
            }
        },
    )
    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val toEnd = state.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Row(
                Modifier.fillMaxSize().background(if (toEnd) Palette.accent else Color(0xFFB3261E)).padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (toEnd) Arrangement.Start else Arrangement.End,
            ) {
                Icon(if (toEnd) Icons.Filled.Check else Icons.Outlined.Delete, contentDescription = null, tint = if (toEnd) Color.Black else Color.White)
            }
        },
    ) {
        Box(Modifier.background(Palette.background)) {
            GoalRow(goal, data.profile?.birthDate, Modifier.combinedClickable(onClick = onOpen, onLongClick = { menu = true }))
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Выполнено") }, onClick = { menu = false; onComplete() })
                DropdownMenuItem(text = { Text(if (goal.isFocused) "Убрать из фокуса" else "В фокус") }, onClick = { menu = false; onToggleFocus() })
                DropdownMenuItem(text = { Text("Удалить", color = Color(0xFFFF6B5E)) }, onClick = { menu = false; onDelete() })
            }
        }
    }
}

@Composable
fun GoalRow(goal: Goal, birthDate: Instant?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        GoalIcon(goal)
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(goal.title, fontSize = 16.sp, maxLines = 3)
            DeadlineText.summary(goal, birthDate)?.let {
                Text(it, color = if (DeadlineText.isOverdue(goal, birthDate)) Palette.accent else Palette.secondaryText, fontSize = 12.sp)
            }
            if (goal.steps.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Checklist, contentDescription = null, tint = Palette.tertiaryText, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("${goal.completedStepsCount} из ${Format.genitive(goal.steps.size, "шага", "шагов")}", color = Palette.tertiaryText, fontSize = 12.sp)
                }
            }
        }
        if (goal.isFocused) Icon(Icons.Filled.Star, contentDescription = "В фокусе", tint = Palette.accent, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun DoneGoalRow(goal: Goal, data: AppData, onOpen: () -> Unit) {
    val model = LocalModel.current
    val thumbnail by produceState<Bitmap?>(null, goal.photoFileName) {
        value = withContext(Dispatchers.IO) { model.photo(goal, 160) }
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Palette.card), contentAlignment = Alignment.Center) {
            val bitmap = thumbnail
            if (bitmap != null) {
                Image(bitmap.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                GoalIcon(goal)
            }
        }
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(goal.title, fontSize = 16.sp, maxLines = 2)
            val date = goal.completedAt
            if (date != null) {
                val age = data.profile?.let { LifeStats(it, date).ageYears }
                Text(Format.date(date) + (age?.let { " · " + Format.atAge(it) } ?: ""), color = Palette.secondaryText, fontSize = 12.sp)
            }
            if (goal.reflection.isNotEmpty()) Text(goal.reflection, color = Palette.tertiaryText, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun EmptyFilter(text: String) {
    Text(text, color = Palette.secondaryText, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp))
}

@Composable
private fun EmptyGoals(onNew: () -> Unit, onIdeas: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.Checklist, contentDescription = null, tint = Palette.secondaryText, modifier = Modifier.size(44.dp))
        Text("Список пуст", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text("Что ты хочешь успеть? Начни с одной цели или загляни в библиотеку идей.", color = Palette.secondaryText, textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.clip(CircleShape).background(Palette.accent)) {
            TextButton(onClick = onNew) { Text("Новая цель", color = Color.Black) }
        }
        TextButton(onClick = onIdeas) { Text("Библиотека идей", color = Palette.accent) }
    }
}
