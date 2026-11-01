package app.mementomori.ui.share

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import app.mementomori.core.Continent
import app.mementomori.core.Format
import app.mementomori.core.Quotes
import app.mementomori.core.RussianPlural
import app.mementomori.core.TravelStats
import app.mementomori.data.YearsBitmap
import app.mementomori.ui.AccentButton
import app.mementomori.ui.LocalModel
import app.mementomori.ui.LocalNavigator
import app.mementomori.ui.SheetScaffold
import app.mementomori.ui.ThinProgressBar
import app.mementomori.ui.theme.Palette
import app.mementomori.ui.world.StaticWorldMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Карточка для соцсетей: жизнь в годах и главные цифры. */
@Composable
fun LifeShareScreen() {
    val model = LocalModel.current
    val stats = model.stats() ?: return
    val completed = model.data.value.completedGoals.size
    val quote = Quotes.ofTheDay()
    val density = LocalDensity.current
    val gridBitmap = remember {
        with(density) { YearsBitmap.render(stats, 304.dp.roundToPx(), 170.dp.roundToPx()) }
    }
    RenderedShare("Карточка", "memento-mori.png") {
        Text("MEMENTO MORI", color = Palette.secondaryText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 4.sp)
        Image(gridBitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxWidth().height(170.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Metric(Format.percent(stats.progress, 0), "жизни\nпрожито")
            Metric(Format.number(stats.remainingWeeks), RussianPlural.form(stats.remainingWeeks, "неделя\nвпереди", "недели\nвпереди", "недель\nвпереди"))
            Metric("$completed", RussianPlural.form(completed, "цель\nвыполнена", "цели\nвыполнено", "целей\nвыполнено"))
        }
        Text("«${quote.text}»", fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 13.sp, color = Color.White.copy(alpha = 0.8f), maxLines = 4)
    }
}

/** Карточка «моя карта путешествий» для соцсетей. */
@Composable
fun TravelShareScreen() {
    val model = LocalModel.current
    val travel = TravelStats(model.data.value)
    RenderedShare("Карта путешествий", "memento-mori-world.png") {
        Text("MEMENTO MORI · МОЙ МИР", color = Palette.secondaryText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 3.sp)
        StaticWorldMap(travel.visited, travel.wished, Modifier.fillMaxWidth().height(140.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${travel.visitedCountries}", fontSize = 44.sp, fontWeight = FontWeight.Thin)
            Spacer(Modifier.width(8.dp))
            Text(RussianPlural.form(travel.visitedCountries, "страна", "страны", "стран"), fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
            Spacer(Modifier.weight(1f))
            Text("${Format.percent(travel.worldShare, 0)} мира", color = Palette.accent, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Continent.entries.forEach { continent ->
                val (visited, total) = travel.progress(continent)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(continent.title, fontSize = 12.sp, color = if (visited > 0) Color.White else Palette.tertiaryText, modifier = Modifier.width(130.dp))
                    ThinProgressBar(if (total > 0) visited.toDouble() / total else 0.0, Modifier.weight(1f), height = 3.dp, fill = Palette.accent)
                    Text("$visited/$total", fontSize = 12.sp, color = if (visited > 0) Color.White else Palette.tertiaryText, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun Metric(value: String, caption: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, fontSize = 30.sp, fontWeight = FontWeight.Light, maxLines = 1)
        Text(caption, color = Palette.secondaryText, fontSize = 11.sp, lineHeight = 13.sp)
    }
}

/** Лист «Поделиться»: рисует карточку в картинку и отдаёт её системному меню. */
@Composable
private fun RenderedShare(title: String, fileName: String, card: @Composable ColumnScope.() -> Unit) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val layer = rememberGraphicsLayer()
    SheetScaffold(title = title, onBack = { navigator.pop() }, backLabel = "Готово") { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    },
            ) {
                Column(
                    Modifier.width(360.dp).background(Color.Black).padding(28.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    content = card,
                )
            }
            AccentButton("Поделиться", icon = Icons.Outlined.Share) {
                scope.launch {
                    val bitmap = layer.toImageBitmap().asAndroidBitmap()
                    share(context, bitmap, fileName)
                }
            }
        }
    }
}

private suspend fun share(context: Context, bitmap: Bitmap, fileName: String) {
    val file = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "shared").apply { mkdirs() }
        File(directory, fileName).also { file -> file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val intent = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, null))
}

@Composable
private fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    fontStyle: FontStyle? = null,
    letterSpacing: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    lineHeight: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
) = androidx.compose.material3.Text(
    text,
    modifier = modifier,
    color = color,
    fontSize = fontSize,
    fontWeight = fontWeight,
    fontFamily = fontFamily,
    fontStyle = fontStyle,
    letterSpacing = letterSpacing,
    lineHeight = lineHeight,
    maxLines = maxLines,
)
