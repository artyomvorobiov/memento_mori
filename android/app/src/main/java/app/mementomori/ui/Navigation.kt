package app.mementomori.ui

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.mementomori.core.GoalCategory
import app.mementomori.data.AppModel
import app.mementomori.ui.counters.CounterDetailScreen
import app.mementomori.ui.counters.CounterEditorScreen
import app.mementomori.ui.goals.CompleteGoalScreen
import app.mementomori.ui.goals.GoalDetailScreen
import app.mementomori.ui.goals.GoalEditorScreen
import app.mementomori.ui.goals.GoalsScreen
import app.mementomori.ui.goals.IdeasScreen
import app.mementomori.ui.life.LetterEditorScreen
import app.mementomori.ui.life.LetterScreen
import app.mementomori.ui.life.LifeScreen
import app.mementomori.ui.onboarding.OnboardingScreen
import app.mementomori.ui.settings.SettingsScreen
import app.mementomori.ui.share.LifeShareScreen
import app.mementomori.ui.share.TravelShareScreen
import app.mementomori.ui.theme.Palette
import app.mementomori.ui.today.TodayScreen
import app.mementomori.ui.world.PhotoImportScreen
import app.mementomori.ui.world.WorldScreen
import kotlinx.coroutines.flow.MutableStateFlow

/** Экраны поверх вкладок. */
sealed interface Screen {
    data class GoalDetail(val id: String) : Screen
    data class GoalEditor(val id: String? = null, val category: GoalCategory? = null, val title: String = "") : Screen
    data class CompleteGoal(val id: String) : Screen
    data object Ideas : Screen
    data object Settings : Screen
    data class CounterDetail(val id: String) : Screen
    data class CounterEditor(val id: String? = null) : Screen
    data class Letter(val id: String) : Screen
    data object NewLetter : Screen
    data class PhotoImport(val demo: Boolean = false) : Screen
    data object LifeShare : Screen
    data object TravelShare : Screen
}

/** Простая навигация: стопка экранов поверх вкладок и системная кнопка «Назад». */
class Navigator {
    val stack = mutableStateListOf<Screen>()
    var tab by mutableIntStateOf(0)

    fun push(screen: Screen) {
        stack.add(screen)
    }

    fun pop() {
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
    }

    /** Закрыть верхний экран и открыть другой (например, «Ответить» из письма). */
    fun replace(screen: Screen) {
        pop()
        push(screen)
    }

    companion object {
        const val TODAY = 0
        const val GOALS = 1
        const val WORLD = 2
        const val LIFE = 3
    }
}

val LocalModel = staticCompositionLocalOf<AppModel> { error("AppModel не передан") }
val LocalNavigator = staticCompositionLocalOf<Navigator> { error("Navigator не передан") }

/** Ссылки из виджетов и уведомлений: mementomori://letter/<id>, mementomori://letters/new, mementomori://counter/<id>. */
sealed interface DeepLink {
    data class Letter(val id: String) : DeepLink
    data object NewLetter : DeepLink
    data class Counter(val id: String) : DeepLink

    companion object {
        fun parse(uri: Uri?): DeepLink? {
            if (uri?.scheme != "mementomori") return null
            val id = uri.pathSegments.firstOrNull()
            return when (uri.host) {
                "letter" -> id?.let { Letter(it) }
                "letters" -> NewLetter
                "counter" -> id?.let { Counter(it) }
                else -> null
            }
        }
    }
}

@Composable
fun MementoRoot(model: AppModel, links: MutableStateFlow<DeepLink?>, startScreen: Screen? = null, startTab: Int? = null) {
    val navigator = remember {
        Navigator().apply {
            tab = startTab ?: model.prefs.selectedTab
            startScreen?.let { push(it) }
        }
    }
    val data by model.data.collectAsStateWithLifecycle()
    val storageUnavailable by model.storageUnavailable.collectAsStateWithLifecycle()
    val problem by model.problem.collectAsStateWithLifecycle()
    val link by links.collectAsStateWithLifecycle()

    LaunchedEffect(link, data.profile != null) {
        val current = link ?: return@LaunchedEffect
        if (data.profile == null) return@LaunchedEffect
        when (current) {
            is DeepLink.Letter -> {
                navigator.tab = Navigator.LIFE
                navigator.push(Screen.Letter(current.id))
            }
            DeepLink.NewLetter -> {
                navigator.tab = Navigator.LIFE
                navigator.push(Screen.NewLetter)
            }
            is DeepLink.Counter -> {
                navigator.tab = Navigator.TODAY
                navigator.push(Screen.CounterDetail(current.id))
            }
        }
        links.value = null
    }

    CompositionLocalProvider(LocalModel provides model, LocalNavigator provides navigator) {
        Box(Modifier.fillMaxSize()) {
            when {
                storageUnavailable -> StorageUnavailable(onRetry = model::handleBecameActive)
                data.profile == null -> OnboardingScreen()
                else -> MainContent(navigator)
            }
        }
        problem?.let { message ->
            AlertDialog(
                onDismissRequest = { model.problem.value = null },
                confirmButton = { TextButton(onClick = { model.problem.value = null }) { Text("Понятно") } },
                title = { Text("Проблема с данными") },
                text = { Text(message) },
            )
        }
    }
}

@Composable
private fun MainContent(navigator: Navigator) {
    val model = LocalModel.current
    BackHandler(enabled = navigator.stack.isNotEmpty()) { navigator.pop() }
    val top = navigator.stack.lastOrNull()
    AnimatedContent(
        targetState = top,
        transitionSpec = {
            if (targetState != null && (initialState == null || navigator.stack.size > 1)) {
                (slideInHorizontally { it / 3 } + fadeIn()) togetherWith fadeOut()
            } else {
                fadeIn() togetherWith (slideOutHorizontally { it / 3 } + fadeOut())
            }
        },
        label = "screens",
    ) { screen ->
        if (screen == null) {
            Tabs(navigator)
        } else {
            ScreenContent(screen)
        }
    }
    LaunchedEffect(navigator.tab) { model.prefs.selectedTab = navigator.tab }
}

@Composable
private fun ScreenContent(screen: Screen) {
    when (screen) {
        is Screen.GoalDetail -> GoalDetailScreen(screen.id)
        is Screen.GoalEditor -> GoalEditorScreen(screen.id, screen.category, screen.title)
        is Screen.CompleteGoal -> CompleteGoalScreen(screen.id)
        Screen.Ideas -> IdeasScreen()
        Screen.Settings -> SettingsScreen()
        is Screen.CounterDetail -> CounterDetailScreen(screen.id)
        is Screen.CounterEditor -> CounterEditorScreen(screen.id)
        is Screen.Letter -> LetterScreen(screen.id)
        Screen.NewLetter -> LetterEditorScreen()
        is Screen.PhotoImport -> PhotoImportScreen(screen.demo)
        Screen.LifeShare -> LifeShareScreen()
        Screen.TravelShare -> TravelShareScreen()
    }
}

@Composable
private fun Tabs(navigator: Navigator) {
    val items = listOf(
        Triple("Сегодня", Icons.Outlined.HourglassEmpty, Navigator.TODAY),
        Triple("Цели", Icons.Outlined.Checklist, Navigator.GOALS),
        Triple("Мир", Icons.Outlined.Public, Navigator.WORLD),
        Triple("Жизнь", Icons.Outlined.GridView, Navigator.LIFE),
    )
    androidx.compose.material3.Scaffold(
        containerColor = Palette.background,
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF0C0C0D), tonalElevation = 0.dp) {
                items.forEach { (title, icon, index) ->
                    NavigationBarItem(
                        selected = navigator.tab == index,
                        onClick = { navigator.tab = index },
                        icon = { Icon(icon, contentDescription = null) },
                        label = { Text(title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Palette.accent,
                            selectedTextColor = Palette.accent,
                            indicatorColor = Color.White.copy(alpha = 0.08f),
                            unselectedIconColor = Palette.secondaryText,
                            unselectedTextColor = Palette.secondaryText,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (navigator.tab) {
                Navigator.GOALS -> GoalsScreen()
                Navigator.WORLD -> WorldScreen()
                Navigator.LIFE -> LifeScreen()
                else -> TodayScreen()
            }
        }
    }
}

/** Показывается, если файл данных существует, но система пока не даёт его прочитать. */
@Composable
private fun StorageUnavailable(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.Lock, contentDescription = null, tint = Palette.secondaryText, modifier = Modifier.size(40.dp))
        Text("Данные пока недоступны")
        Text("Разблокируй телефон и попробуй ещё раз.", color = Palette.secondaryText)
        OutlinedButton(onClick = onRetry) { Text("Попробовать снова") }
    }
}
