package app.mementomori.ui.world

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ZoomInMap
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.mementomori.core.CountryStatus
import app.mementomori.core.WorldMapData
import app.mementomori.data.MapResources
import app.mementomori.ui.theme.Palette
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/** Палитра карты. */
data class MapStyle(
    val land: Color = Color.White.copy(alpha = 0.16f),
    val other: Color = Color.White.copy(alpha = 0.1f),
    val visited: Color = Palette.accent,
    val wished: Color = Palette.accent.copy(alpha = 0.35f),
    val selection: Color = Color.White,
    /** Точки маленьких стран, где ещё не был: на интерактивной карте нужны, чтобы по ним можно было попасть. */
    val showsUnmarkedDots: Boolean = true,
    val unmarkedDot: Color = Color.White.copy(alpha = 0.3f),
)

/** Пути стран для рисования — строятся один раз. */
class MapPaths(val map: WorldMapData) {
    val countries: Map<String, Path> = map.countries.mapValues { (_, rings) -> path(rings) }
    val other: Path = path(map.other)

    private fun path(rings: List<FloatArray>) = Path().apply {
        for (ring in rings) {
            if (ring.size < 6) continue
            moveTo(ring[0], ring[1])
            var i = 2
            while (i + 1 < ring.size) {
                lineTo(ring[i], ring[i + 1])
                i += 2
            }
            close()
        }
    }
}

@Composable
fun rememberMapPaths(): MapPaths {
    val context = LocalContext.current
    return remember { MapPaths(MapResources.worldMap(context)) }
}

/** Масштаб и сдвиг карты в окне: fill — заполнить высоту (интерактивная карта), иначе вписать целиком. */
data class MapTransform(val scale: Float, val tx: Float, val ty: Float) {
    companion object {
        fun of(map: WorldMapData, size: Size, zoom: Float, offset: Offset, fill: Boolean): MapTransform {
            if (size.width <= 0 || size.height <= 0) return MapTransform(1f, 0f, 0f)
            val base = if (fill) max(size.height / map.height, size.width / map.width) else min(size.width / map.width, size.height / map.height)
            val scale = base * zoom
            return MapTransform(scale, (size.width - map.width * scale) / 2 + offset.x, (size.height - map.height * scale) / 2 + offset.y)
        }
    }
}

/** Рисует карту: посещённые страны — акцентом, желанные — полупрозрачно. */
fun DrawScope.drawWorldMap(
    paths: MapPaths,
    visited: Set<String>,
    wished: Set<String>,
    selected: String?,
    zoom: Float,
    offset: Offset,
    fill: Boolean,
    style: MapStyle,
) {
    val map = paths.map
    val t = MapTransform.of(map, size, zoom, offset, fill)
    fun status(code: String) = when (code) {
        in visited -> CountryStatus.VISITED
        in wished -> CountryStatus.WISHED
        else -> CountryStatus.NONE
    }
    fun color(code: String) = when (status(code)) {
        CountryStatus.VISITED -> style.visited
        CountryStatus.WISHED -> style.wished
        CountryStatus.NONE -> style.land
    }
    withTransform({
        translate(t.tx, t.ty)
        scale(t.scale, t.scale, pivot = Offset.Zero)
    }) {
        drawPath(paths.other, style.other)
        for ((code, path) in paths.countries) drawPath(path, color(code))
        val border = Stroke(width = 0.6f / t.scale)
        for (path in paths.countries.values) drawPath(path, Color.Black, style = border)
        val radius = max(2.2f, 1.6f * min(zoom, 3f)) * density / t.scale
        for ((code, center) in map.dots) {
            val unmarked = status(code) == CountryStatus.NONE
            if (unmarked && !style.showsUnmarkedDots) continue
            drawCircle(if (unmarked) style.unmarkedDot else color(code), radius, Offset(center.first, center.second))
        }
        if (selected != null) {
            paths.countries[selected]?.let { drawPath(it, style.selection, style = Stroke(width = 1.6f * density / t.scale)) }
            map.dots[selected]?.let { (x, y) ->
                drawCircle(style.selection, radius * 2, Offset(x, y), style = Stroke(width = 1.4f * density / t.scale))
            }
        }
    }
}

/** Статичная карта целиком — для карточек. */
@Composable
fun StaticWorldMap(visited: Set<String>, wished: Set<String>, modifier: Modifier = Modifier, style: MapStyle = MapStyle(showsUnmarkedDots = false)) {
    val paths = rememberMapPaths()
    Canvas(modifier) { drawWorldMap(paths, visited, wished, null, 1f, Offset.Zero, false, style) }
}

/** Запрос «покажи страну»: при изменении карта плавно перелетает к ней. */
data class FocusRequest(val code: String, val nonce: Long = System.nanoTime())

/** Карта, которую можно приближать двумя пальцами, двигать и нажимать на страны. */
@Composable
fun InteractiveWorldMap(
    visited: Set<String>,
    wished: Set<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    focusRequest: FocusRequest?,
    initialCenter: String?,
    modifier: Modifier = Modifier,
) {
    val paths = rememberMapPaths()
    val map = paths.map
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    var size by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var centered by remember { mutableStateOf(false) }
    val maxZoom = 10f
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)

    fun viewSize() = Size(size.width.toFloat(), size.height.toFloat())
    fun baseScale() = MapTransform.of(map, viewSize(), 1f, Offset.Zero, true).scale

    fun clamp(offset: Offset, z: Float): Offset {
        val scale = baseScale() * z
        val maxX = max(0f, (map.width * scale - size.width) / 2)
        val maxY = max(0f, (map.height * scale - size.height) / 2)
        return Offset(offset.x.coerceIn(-maxX, maxX), offset.y.coerceIn(-maxY, maxY))
    }

    /** Сдвиг, при котором точка карты оказывается в центре. */
    fun centering(x: Float, y: Float, z: Float): Offset {
        val scale = baseScale() * z
        return Offset(-scale * (x - map.width / 2), -scale * (y - map.height / 2))
    }

    fun flyTo(targetZoom: Float, target: Offset) {
        val startZoom = zoom
        val startX = offsetX
        val startY = offsetY
        scope.launch {
            animate(0f, 1f, animationSpec = tween(600)) { f, _ ->
                zoom = startZoom + (targetZoom - startZoom) * f
                offsetX = startX + (target.x - startX) * f
                offsetY = startY + (target.y - startY) * f
            }
        }
    }

    LaunchedEffect(size) {
        if (size.width == 0 || centered) return@LaunchedEffect
        centered = true
        val bounds = initialCenter?.let { map.bounds(it) } ?: return@LaunchedEffect
        val target = clamp(centering(bounds.centerX, bounds.centerY, 1f), 1f)
        offsetX = target.x
        offsetY = target.y
    }

    LaunchedEffect(focusRequest) {
        val request = focusRequest ?: return@LaunchedEffect
        val bounds = map.bounds(request.code) ?: return@LaunchedEffect
        if (size.width == 0) return@LaunchedEffect
        val base = baseScale()
        // Страна занимает примерно половину карты, но без сильного приближения.
        val desired = min(size.width / max(bounds.width * base, 1f), size.height / max(bounds.height * base, 1f)) * 0.5f
        val newZoom = max(zoom, desired).coerceIn(1f, 5f)
        flyTo(newZoom, clamp(centering(bounds.centerX, bounds.centerY, newZoom), newZoom))
    }

    Box(modifier.clipToBounds().onSizeChanged { size = it }) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(map) {
                    detectTransformGestures { centroid, pan, gestureZoom, _ ->
                        val newZoom = (zoom * gestureZoom).coerceIn(1f, maxZoom)
                        val ratio = newZoom / zoom
                        // Точка под пальцами остаётся на месте.
                        val anchorX = centroid.x - size.width / 2f
                        val anchorY = centroid.y - size.height / 2f
                        val moved = Offset(
                            anchorX - (anchorX - offsetX) * ratio + pan.x,
                            anchorY - (anchorY - offsetY) * ratio + pan.y,
                        )
                        zoom = newZoom
                        val clamped = clamp(moved, newZoom)
                        offsetX = clamped.x
                        offsetY = clamped.y
                    }
                }
                .pointerInput(map) {
                    detectTapGestures { position ->
                        val t = MapTransform.of(map, viewSize(), zoom, Offset(offsetX, offsetY), true)
                        if (t.scale <= 0f) return@detectTapGestures
                        val x = (position.x - t.tx) / t.scale
                        val y = (position.y - t.ty) / t.scale
                        val code = map.country(x, y, 16 * density / t.scale)
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        currentOnSelect(if (code == currentSelected) null else code)
                    }
                },
        ) {
            drawWorldMap(paths, visited, wished, selected, zoom, Offset(offsetX, offsetY), true, MapStyle())
        }
        if (zoom > 1.05f) {
            IconButton(
                onClick = {
                    centered = false
                    val bounds = initialCenter?.let { map.bounds(it) }
                    val target = bounds?.let { clamp(centering(it.centerX, it.centerY, 1f), 1f) } ?: Offset.Zero
                    flyTo(1f, target)
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp).size(40.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.6f)),
            ) {
                Icon(Icons.Filled.ZoomInMap, contentDescription = "Показать всю карту", tint = Color.White)
            }
        }
    }
}
