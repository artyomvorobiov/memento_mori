package app.mementomori.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Палитра: чёрный фон, белый текст и один тёплый акцент — цвет пламени свечи. */
object Palette {
    val accent = Color(0xFFF2A541)
    val background = Color.Black
    val card = Color.White.copy(alpha = 0.06f)
    val cardStroke = Color.White.copy(alpha = 0.08f)
    val secondaryText = Color.White.copy(alpha = 0.6f)
    val tertiaryText = Color.White.copy(alpha = 0.38f)
    val lived = Color.White.copy(alpha = 0.85f)
    val future = Color.White.copy(alpha = 0.14f)
    val chip = Color.White.copy(alpha = 0.08f)
}

private val colors = darkColorScheme(
    primary = Palette.accent,
    onPrimary = Color.Black,
    primaryContainer = Palette.accent,
    onPrimaryContainer = Color.Black,
    secondary = Palette.accent,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF3A2C17),
    onSecondaryContainer = Palette.accent,
    tertiary = Palette.accent,
    background = Color.Black,
    onBackground = Color.White,
    surface = Color.Black,
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFF9A9A9E),
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF0E0E0F),
    surfaceContainer = Color(0xFF141415),
    surfaceContainerHigh = Color(0xFF1C1C1E),
    surfaceContainerHighest = Color(0xFF262628),
    outline = Color(0xFF48484A),
    outlineVariant = Color(0xFF2C2C2E),
    error = Color(0xFFFF6B5E),
    onError = Color.Black,
)

@Composable
fun MementoTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, content = content)
}
