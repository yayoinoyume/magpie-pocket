package com.magpie.pocket.core.design

import android.content.res.Configuration
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.magpie.pocket.core.model.DisplayPreferences
import com.magpie.pocket.core.model.ThemeMode

/**
 * Shared information-architecture patterns for the five rebuilt screens.
 * Every component is stateless, takes its colors and type only from [MagpieTheme], and
 * prints a required-but-empty value as an em dash instead of blank space.
 */

/** 11sp auxiliary text (section index, chart axis labels) assembled from the type tokens. */
private val MetadataStyle: TextStyle = MagpieTypography.CaptionStyle.copy(fontSize = MagpieTypography.Caption)

/** The spec fixes the error block at r16, which no spacing token covers. */
private val ErrorBlockShape = RoundedCornerShape(16.dp)

/** 28sp title, 12sp subtitle and one action slot inside an 80dp page header. */
@Composable
fun MagpiePageHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val colors = MagpieTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 80.dp)
            .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MagpieTypography.PageTitleStyle,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() }
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MagpieTypography.LabelStyle,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions
        )
    }
}

/** 17sp section title with an optional trailing note and an optional leading index badge. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    note: String? = null,
    index: Int? = null
) {
    val colors = MagpieTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 33.dp)
            .padding(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (index != null) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.accentSurface),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = index.toString(),
                    style = MetadataStyle.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.accent,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = title,
            style = MagpieTypography.SectionTitleStyle,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (!note.isNullOrBlank()) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = note,
                style = MagpieTypography.LabelStyle,
                color = colors.textSecondary,
                maxLines = 1
            )
        }
    }
}

/**
 * Connection capsule, optional demo badge and optional date.
 * The strip is 28dp tall visually and keeps a 48dp tap target for the capsule.
 */
@Composable
fun StatusStrip(
    connectionLabel: String,
    modifier: Modifier = Modifier,
    demo: Boolean = false,
    dateLabel: String? = null,
    onConnectionClick: (() -> Unit)? = null
) {
    val colors = MagpieTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MagpieDimensions.TouchTargetMinimum),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 10dp vertical padding above and below turns the 48dp target into a 28dp capsule.
        Box(
            modifier = Modifier
                .heightIn(min = MagpieDimensions.TouchTargetMinimum)
                .then(
                    if (onConnectionClick != null) {
                        Modifier.springPress(onClick = onConnectionClick)
                    } else {
                        Modifier
                    }
                )
                .padding(vertical = 10.dp)
                .clip(CircleShape)
                .background(colors.accentSurface)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displayOrDash(connectionLabel),
                style = MagpieTypography.SubheadStyle,
                color = colors.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (demo) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.surfaceSubtle)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "演示",
                    style = MagpieTypography.LabelStyle,
                    color = colors.textSecondary,
                    maxLines = 1
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        if (!dateLabel.isNullOrBlank()) {
            Text(
                text = dateLabel,
                style = MagpieTypography.LabelStyle,
                color = colors.textSecondary,
                maxLines = 1
            )
        }
    }
}

/** Two-column key/value grid: 12sp label above a 14sp value. */
@Composable
fun KvGrid(
    items: List<Pair<String, String?>>,
    modifier: Modifier = Modifier
) {
    val colors = MagpieTheme.colors
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                rowItems.forEach { (label, value) ->
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = label,
                            style = MagpieTypography.LabelStyle,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(1.dp))
                        Text(
                            text = displayOrDash(value),
                            style = MagpieTypography.BodyStyle,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

enum class AttemptState {
    FAILED,
    REROUTED,
    SUCCEEDED
}

data class AttemptEntry(
    val index: Int,
    val target: String,
    val detail: String,
    val meta: String,
    val state: AttemptState
)

/** 51dp rows, one node per attempt, three states. Deliberately independent of core/model. */
@Composable
fun AttemptTimeline(
    entries: List<AttemptEntry>,
    modifier: Modifier = Modifier
) {
    val colors = MagpieTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 30.dp)
    ) {
        entries.forEach { entry ->
            val tint = attemptTint(entry.state, colors)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(51.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(tint.copy(alpha = 0.14f))
                    )
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(tint)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "#${entry.index} ${entry.target}",
                        style = MagpieTypography.BodyStyle,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = displayOrDash(entry.detail),
                        style = MagpieTypography.SubheadStyle,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = displayOrDash(entry.meta),
                    style = MagpieTypography.LabelStyle,
                    color = colors.textTertiary,
                    maxLines = 1
                )
            }
        }
    }
}

private fun attemptTint(state: AttemptState, colors: MagpieColorScheme): Color = when (state) {
    AttemptState.FAILED -> colors.error
    AttemptState.REROUTED -> colors.warning
    AttemptState.SUCCEEDED -> colors.success
}

/**
 * 38dp track split into equal segments with a 32dp selected thumb.
 * The rows measure 48dp so every segment still meets the touch minimum.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (options.isEmpty()) return
    val colors = MagpieTheme.colors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = MagpieDimensions.TouchTargetMinimum),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .clip(CircleShape)
                .background(colors.surfaceSubtle)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(MagpieDimensions.TouchTargetMinimum)
                .padding(horizontal = 3.dp)
        ) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(MagpieDimensions.TouchTargetMinimum)
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { onSelect(index) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .clip(CircleShape)
                                .background(colors.surface)
                        )
                    }
                    Text(
                        text = label,
                        style = if (selected) {
                            MagpieTypography.BodyStyle.copy(fontWeight = FontWeight.Medium)
                        } else {
                            MagpieTypography.SubheadStyle
                        },
                        color = if (selected) colors.textPrimary else colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}

/** Chips scrolling horizontally in one row; use [ChipGrid] when the set must wrap. */
@Composable
fun FilterChipRow(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (options.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEachIndexed { index, label ->
            FilterChip(
                label = label,
                selected = index == selectedIndex,
                onClick = { onSelect(index) }
            )
        }
    }
}

/** Three chips per row; the last row keeps its empty slots so the columns stay aligned. */
@Composable
fun ChipGrid(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (options.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.chunked(3).forEachIndexed { rowIndex, rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowItems.forEachIndexed { columnIndex, label ->
                    val index = rowIndex * 3 + columnIndex
                    FilterChip(
                        label = label,
                        selected = index == selectedIndex,
                        onClick = { onSelect(index) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(3 - rowItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** 32dp pill inside a 48dp tap target: 8dp padding above and below does the shrinking. */
@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MagpieTheme.colors
    Box(
        modifier = modifier
            .heightIn(min = MagpieDimensions.TouchTargetMinimum)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick
            )
            .padding(vertical = 8.dp)
            .clip(CircleShape)
            .background(if (selected) colors.accentSurface else colors.surfaceSubtle)
            .padding(horizontal = 13.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MagpieTypography.SubheadStyle,
            color = if (selected) colors.accent else colors.textSecondary,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Warning band on the soft warning surface with a 24% outline and bolded figures. */
@Composable
fun WarnPanel(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    icon: ImageVector = Icons.Filled.Warning
) {
    val colors = MagpieTheme.colors
    val shape = RoundedCornerShape(MagpieDimensions.RadiusCard)
    val annotatedMessage = remember(message) { emphasizeNumbers(message) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.warningSurface)
            .border(1.dp, colors.warning.copy(alpha = 0.24f), shape)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(colors.warning.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.warning,
                modifier = Modifier.size(16.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = annotatedMessage,
            style = MagpieTypography.BodyStyle,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        if (!actionLabel.isNullOrBlank() && onAction != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .sizeIn(
                        minWidth = MagpieDimensions.TouchTargetMinimum,
                        minHeight = MagpieDimensions.TouchTargetMinimum
                    )
                    .springPress(onClick = onAction),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = actionLabel,
                    style = MagpieTypography.SubheadStyle,
                    color = colors.accent,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

/** Error block on the soft error surface, r16. */
@Composable
fun ErrorBlock(
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    val colors = MagpieTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ErrorBlockShape)
            .background(colors.errorSurface)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(
            text = displayOrDash(title),
            style = MagpieTypography.LabelStyle.copy(fontWeight = FontWeight.Medium),
            color = colors.error
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = displayOrDash(message),
            style = MagpieTypography.SubheadStyle,
            color = colors.textSecondary
        )
    }
}

/**
 * Two independent series: a solid line and a dashed line, each with its own scale,
 * endpoint dots, a baseline axis and a legend. Nulls stay gaps.
 */
@Composable
fun DualLineChart(
    pointsA: List<Float?>,
    pointsB: List<Float?>,
    labels: List<String>,
    modifier: Modifier = Modifier,
    legendA: String = "请求",
    legendB: String = "费用"
) {
    val colors = MagpieTheme.colors
    val hasData = pointsA.any { it != null } || pointsB.any { it != null }
    val description = "${legendA}与${legendB}趋势图"
    Column(modifier = modifier.fillMaxWidth()) {
        if (hasData) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(101.dp)
                    .semantics { contentDescription = description }
            ) {
                val seriesA = normalizedSeries(pointsA)
                val seriesB = normalizedSeries(pointsB)
                val count = maxOf(seriesA.size, seriesB.size)
                val topInset = 4.dp.toPx()
                val axisY = size.height - 4.dp.toPx()
                val stepX = if (count > 1) size.width / (count - 1) else 0f

                fun xOf(index: Int): Float = if (count > 1) index * stepX else size.width / 2f

                fun yOf(value: Float): Float =
                    topInset + (1f - value.coerceIn(0f, 1f)) * (axisY - topInset)

                // Each series is scaled on its own, so a shared value axis would lie.
                // The chart carries a baseline; the date labels underneath carry the domain.
                drawLine(
                    color = colors.divider,
                    start = Offset(0f, axisY),
                    end = Offset(size.width, axisY),
                    strokeWidth = 1.dp.toPx()
                )

                fun drawSeries(values: List<Float?>, color: Color, dashed: Boolean) {
                    val effect = if (dashed) {
                        PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx()))
                    } else {
                        null
                    }
                    contiguousRuns(values).forEach { run ->
                        val points = run.mapNotNull { index ->
                            values[index]?.let { value -> Offset(xOf(index), yOf(value)) }
                        }
                        if (points.isNotEmpty()) {
                            val path = Path().apply {
                                moveTo(points.first().x, points.first().y)
                                points.drop(1).forEach { point -> lineTo(point.x, point.y) }
                            }
                            drawPath(
                                path = path,
                                color = color,
                                style = Stroke(
                                    width = 2.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round,
                                    pathEffect = effect
                                )
                            )
                            drawCircle(color = color, radius = 3.dp.toPx(), center = points.first())
                            drawCircle(color = color, radius = 3.dp.toPx(), center = points.last())
                        }
                    }
                }

                drawSeries(seriesA, colors.accent, dashed = false)
                drawSeries(seriesB, colors.success, dashed = true)
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(101.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无趋势",
                    style = MagpieTypography.BodyStyle,
                    color = colors.textTertiary
                )
            }
        }
        if (labels.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                labels.forEach { label ->
                    Text(
                        text = label,
                        style = MetadataStyle,
                        color = colors.textTertiary,
                        maxLines = 1
                    )
                }
            }
        }
        Row(
            modifier = Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendItem(color = colors.accent, label = legendA)
            LegendItem(color = colors.success, label = legendB)
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    val colors = MagpieTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MagpieTypography.LabelStyle,
            color = colors.textSecondary,
            maxLines = 1
        )
    }
}

/**
 * Each line gets its own scale: requests and money must not share one axis.
 * Null values stay null so the caller can render a gap instead of a fake zero.
 */
internal fun normalizedSeries(points: List<Float?>): List<Float?> {
    val max = points.filterNotNull().maxOrNull()
    if (max == null || max <= 0f) {
        return points.map { if (it == null) null else 0f }
    }
    return points.map { value -> value?.div(max)?.coerceIn(0f, 1f) }
}

/** Index runs of consecutive non-null samples; every null breaks the line. */
internal fun contiguousRuns(values: List<Float?>): List<IntRange> {
    val runs = mutableListOf<IntRange>()
    var start = -1
    values.forEachIndexed { index, value ->
        if (value == null) {
            if (start >= 0) {
                runs += start until index
                start = -1
            }
        } else if (start < 0) {
            start = index
        }
    }
    if (start >= 0) {
        runs += start until values.size
    }
    return runs
}

/** Missing values print as an em dash so an empty field never looks like a rendering bug. */
internal fun displayOrDash(value: String?): String =
    value?.takeIf { it.isNotBlank() } ?: "—"

private val NumberPattern = Regex("""\d+(?:[.,]\d+)*%?""")

/** Character ranges for numbers and percentages, used to bold figures in a sentence. */
internal fun numericSpanRanges(text: String): List<IntRange> =
    NumberPattern.findAll(text).map { it.range }.toList()

private fun emphasizeNumbers(text: String) = buildAnnotatedString {
    append(text)
    numericSpanRanges(text).forEach { range ->
        addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), range.first, range.last + 1)
    }
}

@Composable
private fun PatternPreview(dark: Boolean, content: @Composable ColumnScope.() -> Unit) {
    MagpiePocketTheme(
        displayPreferences = DisplayPreferences(
            themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MagpieTheme.colors.background)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 12.dp),
            content = content
        )
    }
}

@Preview(name = "PageHeader light", showBackground = true, widthDp = 390)
@Composable
private fun MagpiePageHeaderLightPreview() {
    PatternPreview(dark = false) {
        MagpiePageHeader(
            title = "概览",
            subtitle = "现在",
            actions = {
                Text(
                    text = "动作",
                    style = MagpieTypography.SubheadStyle,
                    color = MagpieTheme.colors.accent
                )
            }
        )
    }
}

@Preview(
    name = "PageHeader dark",
    showBackground = true,
    widthDp = 390,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun MagpiePageHeaderDarkPreview() {
    PatternPreview(dark = true) {
        MagpiePageHeader(
            title = "概览",
            subtitle = "现在",
            actions = {
                Text(
                    text = "动作",
                    style = MagpieTypography.SubheadStyle,
                    color = MagpieTheme.colors.accent
                )
            }
        )
    }
}

@Preview(name = "SectionHeader light", showBackground = true, widthDp = 390)
@Composable
private fun SectionHeaderLightPreview() {
    PatternPreview(dark = false) {
        SectionHeader(
            title = "今日",
            note = "最多 3 条",
            index = 1,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Preview(
    name = "SectionHeader dark",
    showBackground = true,
    widthDp = 390,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun SectionHeaderDarkPreview() {
    PatternPreview(dark = true) {
        SectionHeader(
            title = "今日",
            note = "最多 3 条",
            index = 1,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Preview(name = "StatusStrip light", showBackground = true, widthDp = 390)
@Composable
private fun StatusStripLightPreview() {
    PatternPreview(dark = false) {
        StatusStrip(
            connectionLabel = "已连接",
            demo = true,
            dateLabel = "统计日 11-10",
            onConnectionClick = {},
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Preview(
    name = "StatusStrip dark",
    showBackground = true,
    widthDp = 390,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun StatusStripDarkPreview() {
    PatternPreview(dark = true) {
        StatusStrip(
            connectionLabel = "已连接",
            demo = true,
            dateLabel = "统计日 11-10",
            onConnectionClick = {},
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Preview(name = "KvGrid light", showBackground = true, widthDp = 390)
@Composable
private fun KvGridLightPreview() {
    PatternPreview(dark = false) {
        KvGrid(
            items = listOf(
                "模型" to "claude-sonnet-4-5",
                "状态" to "成功",
                "耗时" to "3.7 s",
                "费用" to null
            ),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Preview(
    name = "KvGrid dark",
    showBackground = true,
    widthDp = 390,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun KvGridDarkPreview() {
    PatternPreview(dark = true) {
        KvGrid(
            items = listOf(
                "模型" to "claude-sonnet-4-5",
                "状态" to "成功",
                "耗时" to "3.7 s",
                "费用" to null
            ),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Preview(name = "AttemptTimeline light", showBackground = true, widthDp = 390)
@Composable
private fun AttemptTimelineLightPreview() {
    PatternPreview(dark = false) {
        AttemptTimeline(
            entries = previewAttempts(),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Preview(
    name = "AttemptTimeline dark",
    showBackground = true,
    widthDp = 390,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun AttemptTimelineDarkPreview() {
    PatternPreview(dark = true) {
        AttemptTimeline(
            entries = previewAttempts(),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

private fun previewAttempts() = listOf(
    AttemptEntry(1, "OpenRouter", "429 rate_limit，412 ms", "412 ms", AttemptState.FAILED),
    AttemptEntry(2, "OpenRouter 备用", "改道到 claude-haiku", "820 ms", AttemptState.REROUTED),
    AttemptEntry(3, "直连 Anthropic", "200 成功，3.7 s", "3.7 s", AttemptState.SUCCEEDED)
)

@Preview(name = "SegmentedControl light", showBackground = true, widthDp = 390)
@Composable
private fun SegmentedControlLightPreview() {
    PatternPreview(dark = false) {
        SegmentedControl(
            options = listOf("7 天", "30 天", "90 天", "全部"),
            selectedIndex = 1,
            onSelect = {},
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Preview(
    name = "SegmentedControl dark",
    showBackground = true,
    widthDp = 390,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun SegmentedControlDarkPreview() {
    PatternPreview(dark = true) {
        SegmentedControl(
            options = listOf("7 天", "30 天", "90 天", "全部"),
            selectedIndex = 1,
            onSelect = {},
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Preview(name = "FilterChipRow light", showBackground = true, widthDp = 390)
@Composable
private fun FilterChipRowLightPreview() {
    PatternPreview(dark = false) {
        FilterChipRow(
            options = listOf("全部", "改道", "错误", "处理中", "成功"),
            selectedIndex = 0,
            onSelect = {},
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Preview(
    name = "FilterChipRow dark",
    showBackground = true,
    widthDp = 390,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun FilterChipRowDarkPreview() {
    PatternPreview(dark = true) {
        FilterChipRow(
            options = listOf("全部", "改道", "错误", "处理中", "成功"),
            selectedIndex = 0,
            onSelect = {},
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Preview(name = "ChipGrid light", showBackground = true, widthDp = 390)
@Composable
private fun ChipGridLightPreview() {
    PatternPreview(dark = false) {
        ChipGrid(
            options = listOf("模型", "智能体", "会话", "账号", "调用方", "密钥"),
            selectedIndex = 0,
            onSelect = {},
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Preview(
    name = "ChipGrid dark",
    showBackground = true,
    widthDp = 390,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun ChipGridDarkPreview() {
    PatternPreview(dark = true) {
        ChipGrid(
            options = listOf("模型", "智能体", "会话", "账号", "调用方", "密钥"),
            selectedIndex = 0,
            onSelect = {},
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Preview(name = "WarnPanel light", showBackground = true, widthDp = 390)
@Composable
private fun WarnPanelLightPreview() {
    PatternPreview(dark = false) {
        WarnPanel(
            message = "Anthropic 主账号额度已用 87%，按当前用量约 6 小时触顶",
            actionLabel = "查看",
            onAction = {},
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Preview(
    name = "WarnPanel dark",
    showBackground = true,
    widthDp = 390,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun WarnPanelDarkPreview() {
    PatternPreview(dark = true) {
        WarnPanel(
            message = "Anthropic 主账号额度已用 87%，按当前用量约 6 小时触顶",
            actionLabel = "查看",
            onAction = {},
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Preview(name = "ErrorBlock light", showBackground = true, widthDp = 390)
@Composable
private fun ErrorBlockLightPreview() {
    PatternPreview(dark = false) {
        ErrorBlock(
            title = "错误 429 rate_limit（已回退成功）",
            message = "上游并发已达上限，建议提高 OpenRouter 并发配额。",
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Preview(
    name = "ErrorBlock dark",
    showBackground = true,
    widthDp = 390,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun ErrorBlockDarkPreview() {
    PatternPreview(dark = true) {
        ErrorBlock(
            title = "错误 429 rate_limit（已回退成功）",
            message = "上游并发已达上限，建议提高 OpenRouter 并发配额。",
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Preview(name = "DualLineChart light", showBackground = true, widthDp = 390)
@Composable
private fun DualLineChartLightPreview() {
    PatternPreview(dark = false) {
        DualLineChart(
            pointsA = listOf(12f, 18f, 15f, 24f, 21f, 31f),
            pointsB = listOf(1.2f, 1.8f, 1.5f, 2.4f, null, 3.1f),
            labels = listOf("11-05", "11-07", "11-09", "11-10"),
            legendA = "日请求",
            legendB = "日费用",
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Preview(
    name = "DualLineChart dark",
    showBackground = true,
    widthDp = 390,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun DualLineChartDarkPreview() {
    PatternPreview(dark = true) {
        DualLineChart(
            pointsA = listOf(12f, 18f, 15f, 24f, 21f, 31f),
            pointsB = listOf(1.2f, 1.8f, 1.5f, 2.4f, null, 3.1f),
            labels = listOf("11-05", "11-07", "11-09", "11-10"),
            legendA = "日请求",
            legendB = "日费用",
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}
