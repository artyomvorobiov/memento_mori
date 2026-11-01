package app.mementomori

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.mementomori.debug.DebugLaunch
import app.mementomori.ui.DeepLink
import app.mementomori.ui.MementoRoot
import app.mementomori.ui.theme.MementoTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val links = MutableStateFlow<DeepLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val model = MementoApp.model(this)
        val debug = DebugLaunch.from(intent)
        if (debug.demo) model.useDemo()
        links.value = DeepLink.parse(intent?.data)
        setContent {
            MementoTheme {
                MementoRoot(model, links, startScreen = debug.screen, startTab = debug.tab)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        DeepLink.parse(intent.data)?.let { links.value = it }
    }

    override fun onResume() {
        super.onResume()
        // Подхватываем изменения от виджетов, обновляем расписание напоминаний и виджеты.
        MementoApp.model(this).handleBecameActive()
    }
}
