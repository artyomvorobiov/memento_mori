package app.mementomori.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.mementomori.core.Format
import app.mementomori.core.Goal
import app.mementomori.core.GoalCategory
import app.mementomori.core.flagEmoji
import app.mementomori.ui.theme.Palette

/** Значок категории. */
val GoalCategory.icon: ImageVector
    get() = when (this) {
        GoalCategory.TRAVEL -> Icons.Outlined.Flight
        GoalCategory.EXPERIENCE -> Icons.Outlined.AutoAwesome
        GoalCategory.MOVIES -> Icons.Outlined.Movie
        GoalCategory.BOOKS -> Icons.Outlined.Book
        GoalCategory.SKILLS -> Icons.Outlined.School
        GoalCategory.BODY -> Icons.Outlined.FitnessCenter
        GoalCategory.PEOPLE -> Icons.Outlined.FavoriteBorder
        GoalCategory.CREATE -> Icons.Outlined.Build
        GoalCategory.OTHER -> Icons.Outlined.RadioButtonUnchecked
    }

/** Карточка в стиле приложения. */
fun Modifier.mementoCard(padding: Dp = 18.dp): Modifier = this
    .fillMaxWidth()
    .clip(RoundedCornerShape(20.dp))
    .background(Palette.card)
    .border(BorderStroke(0.5.dp, Palette.cardStroke), RoundedCornerShape(20.dp))
    .padding(padding)

/** Заголовок секции капителью. */
@Composable
fun SectionHeader(title: String, trailing: String? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            title.uppercase(Format.locale),
            color = Palette.tertiaryText,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) Text(trailing, color = Palette.tertiaryText, fontSize = 12.sp)
    }
}

/** Тонкая полоска прогресса. */
@Composable
fun ThinProgressBar(progress: Double, modifier: Modifier = Modifier, height: Dp = 4.dp, fill: Color = Palette.lived, track: Color = Palette.future) {
    BoxWithConstraints(modifier.fillMaxWidth().height(height).clip(CircleShape).background(track)) {
        val fraction = progress.toFloat().coerceIn(0f, 1f)
        Box(Modifier.fillMaxHeight().width(maxOf(height, maxWidth * fraction)).clip(CircleShape).background(fill))
    }
}

/** Большая кнопка-капсула с акцентным фоном. */
@Composable
fun AccentButton(title: String, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(CircleShape)
            .background(if (enabled) Palette.accent else Palette.accent.copy(alpha = 0.4f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(title, color = Color.Black, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
    }
}

/** Капсула-переключатель: категории, сроки, фильтры. */
@Composable
fun Pill(text: String, selected: Boolean, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: () -> Unit) {
    Row(
        modifier
            .clip(CircleShape)
            .background(if (selected) Palette.accent else Palette.chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (selected) Color.Black else Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = if (selected) Color.Black else Color.White, fontSize = 15.sp)
    }
}

/** Горизонтальная лента капсул. */
@Composable
fun PillRow(modifier: Modifier = Modifier, contentPadding: PaddingValues = PaddingValues(horizontal = 0.dp), content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** Значок цели: флаг для стран или символ категории. */
@Composable
fun GoalIcon(goal: Goal, size: Dp = 18.dp) {
    Box(Modifier.size(size * 1.6f), contentAlignment = Alignment.Center) {
        val code = goal.countryCode
        if (!code.isNullOrEmpty()) {
            Text(flagEmoji(code), fontSize = (size.value + 2).sp)
        } else {
            Icon(
                goal.category.icon,
                contentDescription = goal.category.title,
                tint = if (goal.isCompleted) Palette.accent else Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(size),
            )
        }
    }
}

/** Сегментированный переключатель. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, modifier: Modifier = Modifier, onSelect: (T) -> Unit) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, title) ->
            SegmentedButton(
                selected = value == selected,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = Color.White.copy(alpha = 0.18f),
                    activeContentColor = Color.White,
                    inactiveContainerColor = Color.Transparent,
                    inactiveContentColor = Palette.secondaryText,
                    activeBorderColor = Palette.cardStroke,
                    inactiveBorderColor = Palette.cardStroke,
                ),
                icon = {},
            ) { Text(title, maxLines = 1, fontSize = 14.sp) }
        }
    }
}

/** Экран-«лист» поверх вкладок: заголовок, «Назад» и действия справа. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetScaffold(
    title: String,
    onBack: () -> Unit,
    backLabel: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = Palette.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) },
                navigationIcon = {
                    if (backLabel != null) {
                        TextButton(onClick = onBack) { Text(backLabel, color = Palette.accent, fontSize = 16.sp) }
                    } else {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Palette.background),
            )
        },
        bottomBar = bottomBar,
        content = content,
    )
}

/** Строка формы: слева подпись, справа значение. */
@Composable
fun FormRow(title: String, value: String? = null, onClick: (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), fontSize = 16.sp)
        if (value != null) Text(value, color = Palette.secondaryText, fontSize = 16.sp)
        trailing?.invoke()
    }
}

/** Группа строк формы на сером фоне — как секция настроек. */
@Composable
fun FormSection(header: String? = null, footer: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth()) {
        if (header != null) {
            Text(header, color = Palette.secondaryText, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(start = 16.dp, bottom = 6.dp))
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh),
            content = content,
        )
        if (footer != null) {
            Text(footer, color = Palette.secondaryText, fontSize = 13.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp))
        }
    }
}

/** Тонкий разделитель внутри секции. */
@Composable
fun FormDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 16.dp).height(0.5.dp).background(Color.White.copy(alpha = 0.1f)))
}
