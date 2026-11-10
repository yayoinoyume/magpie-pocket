package com.magpie.pocket.feature.privacy

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.magpie.pocket.core.design.DataPanel
import com.magpie.pocket.core.design.InlineNotice
import com.magpie.pocket.core.design.MagpieDimensions
import com.magpie.pocket.core.design.MagpieTheme
import com.magpie.pocket.core.design.MagpieTypography
import com.magpie.pocket.core.design.springPress
import com.magpie.pocket.core.model.MaskingCount
import com.magpie.pocket.core.model.MaskingDayPoint
import com.magpie.pocket.core.model.MaskingRecord
import com.magpie.pocket.core.model.MaskingSummary
import com.magpie.pocket.core.model.PrivacyState
import com.magpie.pocket.core.util.RequestTimestamps
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Privacy masking sheet. The summary, day bars, kind ranking and discovery paths are
 * read-only; a placeholder can be revealed for ten seconds at a time, and the original
 * text never leaves this composition except through the host's in-memory cache.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PrivacySheet(
    state: PrivacyState,
    revealed: Map<String, String?>,
    isDemo: Boolean,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onDaysChange: (Int) -> Unit,
    onKindChange: (String?) -> Unit,
    onLoadMore: () -> Unit,
    onReveal: (String) -> Unit
) {
    val colors = MagpieTheme.colors
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val configuration = LocalConfiguration.current
    val compact = configuration.screenWidthDp <= 340
    val largeFont = configuration.fontScale >= 1.45f
    val pagePadding = if (compact) MagpieDimensions.CompactPagePadding else MagpieDimensions.PagePadding

    var activePlaceholder by remember { mutableStateOf<String?>(null) }
    var expandedRecord by remember { mutableStateOf<String?>(null) }

    // A new query invalidates the selected day, the expanded row and the reveal slot.
    LaunchedEffect(state.selectedDays, state.selectedKind) {
        activePlaceholder = null
        expandedRecord = null
    }

    // A loading/error/unsupported state has no chips left to keep a reveal alive.
    LaunchedEffect(state) {
        if (state !is PrivacyState.Ready) {
            activePlaceholder = null
            expandedRecord = null
        }
    }

    // Ten-second reveal window. Switching placeholders cancels the previous timer.
    LaunchedEffect(activePlaceholder) {
        val placeholder = activePlaceholder ?: return@LaunchedEffect
        delay(10_000)
        if (activePlaceholder == placeholder) activePlaceholder = null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        shape = RoundedCornerShape(
            topStart = MagpieDimensions.RadiusSheetTop,
            topEnd = MagpieDimensions.RadiusSheetTop
        ),
        modifier = Modifier.fillMaxHeight(0.94f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            PrivacyHeader(
                selectedDays = state.selectedDays ?: 7,
                isDemo = isDemo,
                largeFont = largeFont,
                pagePadding = pagePadding,
                onDismiss = onDismiss,
                onDaysChange = onDaysChange
            )

            when (state) {
                is PrivacyState.Ready -> ReadyContent(
                    state = state,
                    revealed = revealed,
                    activePlaceholder = activePlaceholder,
                    expandedRecord = expandedRecord,
                    largeFont = largeFont,
                    pagePadding = pagePadding,
                    onKindChange = onKindChange,
                    onLoadMore = onLoadMore,
                    onRevealToggle = { placeholder ->
                        activePlaceholder = if (activePlaceholder == placeholder) {
                            null
                        } else {
                            if (!revealed.containsKey(placeholder)) onReveal(placeholder)
                            placeholder
                        }
                    },
                    onToggleRecord = { key ->
                        expandedRecord = if (expandedRecord == key) null else key
                    },
                    modifier = Modifier.weight(1f)
                )
                is PrivacyState.Error -> ErrorContent(
                    message = state.message,
                    pagePadding = pagePadding,
                    onRetry = onRetry,
                    modifier = Modifier.weight(1f)
                )
                PrivacyState.Unsupported -> UnsupportedContent(
                    pagePadding = pagePadding,
                    modifier = Modifier.weight(1f)
                )
                else -> LoadingContent(
                    pagePadding = pagePadding,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun PrivacyHeader(
    selectedDays: Int,
    isDemo: Boolean,
    largeFont: Boolean,
    pagePadding: Dp,
    onDismiss: () -> Unit,
    onDaysChange: (Int) -> Unit
) {
    val colors = MagpieTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = pagePadding)
            .padding(top = 4.dp, bottom = 4.dp)
    ) {
        if (largeFont) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "隐私掩码",
                    style = MagpieTypography.SheetTitleStyle,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                CloseButton(onDismiss)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isDemo) {
                    DemoBadge()
                    Spacer(Modifier.width(8.dp))
                }
                DayPill(7, selectedDays == 7, onDaysChange)
                Spacer(Modifier.width(6.dp))
                DayPill(30, selectedDays == 30, onDaysChange)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "隐私掩码",
                    style = MagpieTypography.SheetTitleStyle,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (isDemo) {
                    DemoBadge()
                    Spacer(Modifier.width(8.dp))
                }
                DayPill(7, selectedDays == 7, onDaysChange)
                Spacer(Modifier.width(6.dp))
                DayPill(30, selectedDays == 30, onDaysChange)
                CloseButton(onDismiss)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "请求离开网关前换掉，回答里再放回去",
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = colors.textSecondary
        )
    }
}

@Composable
private fun CloseButton(onDismiss: () -> Unit) {
    val colors = MagpieTheme.colors
    IconButton(
        onClick = onDismiss,
        modifier = Modifier.size(MagpieDimensions.TouchTargetMinimum)
    ) {
        Icon(Icons.Filled.Close, contentDescription = "关闭", tint = colors.textSecondary)
    }
}

@Composable
private fun DemoBadge() {
    val colors = MagpieTheme.colors
    Text(
        text = "演示",
        fontSize = 11.sp,
        lineHeight = 14.sp,
        color = colors.accent,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.accentSurface)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun DayPill(days: Int, selected: Boolean, onDaysChange: (Int) -> Unit) {
    val colors = MagpieTheme.colors
    val shape = RoundedCornerShape(MagpieDimensions.RadiusPill)
    Box(
        modifier = Modifier
            .height(MagpieDimensions.TouchTargetMinimum)
            .widthIn(min = MagpieDimensions.TouchTargetMinimum)
            .springPress(scaleDown = 0.96f) { onDaysChange(days) },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .height(32.dp)
                .clip(shape)
                .background(if (selected) colors.accentSurface else colors.surfaceSubtle)
                .border(
                    width = 0.75.dp,
                    color = if (selected) colors.accent.copy(alpha = 0.26f) else colors.divider,
                    shape = shape
                )
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$days 天",
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) colors.accent else colors.textSecondary
            )
        }
    }
}

@Composable
private fun LoadingContent(pagePadding: Dp, modifier: Modifier) {
    val colors = MagpieTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = pagePadding)
            .padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(28.dp),
            color = colors.accent,
            strokeWidth = 2.dp
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = "正在加载隐私掩码数据",
            fontSize = 13.sp,
            color = colors.textSecondary
        )
    }
}

@Composable
private fun ErrorContent(message: String, pagePadding: Dp, onRetry: () -> Unit, modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = pagePadding)
            .padding(top = 16.dp)
    ) {
        InlineNotice(
            message = message.ifBlank { "隐私掩码加载失败" },
            isError = true
        )
        Spacer(Modifier.height(12.dp))
        ActionButton("重试", onRetry)
    }
}

@Composable
private fun UnsupportedContent(pagePadding: Dp, modifier: Modifier) {
    val colors = MagpieTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = pagePadding)
            .padding(top = 16.dp)
    ) {
        InlineNotice(message = "当前网关版本不支持隐私统计")
        Spacer(Modifier.height(12.dp))
        Text(
            text = "请升级网关到支持掩码统计的版本后再试。",
            fontSize = 12.sp,
            lineHeight = 18.sp,
            color = colors.textSecondary
        )
    }
}

@Composable
private fun ReadyContent(
    state: PrivacyState.Ready,
    revealed: Map<String, String?>,
    activePlaceholder: String?,
    expandedRecord: String?,
    largeFont: Boolean,
    pagePadding: Dp,
    onKindChange: (String?) -> Unit,
    onLoadMore: () -> Unit,
    onRevealToggle: (String) -> Unit,
    onToggleRecord: (String) -> Unit,
    modifier: Modifier
) {
    val colors = MagpieTheme.colors
    val summary = state.summary
    val records = state.records
    val empty = records.isEmpty() &&
        (summary.total ?: 0L) == 0L &&
        summary.days.all { (it.n ?: 0L) == 0L }

    val valueBuckets = summary.values
        .filter { it.id != null || it.name != null }
        .take(8)
    val kindRows = buildKindRows(summary.kinds)
    val byEntries = discoveryEntries(summary.by)
    val clearKind: (() -> Unit)? = if (state.selectedKind != null) {
        { onKindChange(null) }
    } else {
        null
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            start = pagePadding,
            end = pagePadding,
            top = 12.dp,
            bottom = 32.dp
        )
    ) {
        item {
            SummaryHero(summary = summary, largeFont = largeFont)
        }

        if (empty) {
            item { EmptyState(days = state.selectedDays) }
        } else {
            if (valueBuckets.isNotEmpty()) {
                item { SectionLabel("已掩码的值", "共 ${summary.values.size} 个") }
                item {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        valueBuckets.forEach { bucket ->
                            val placeholder = bucket.id ?: bucket.name
                            MaskValueChip(
                                kind = bucket.kind?.takeIf { it.isNotBlank() }
                                    ?: kindFromPlaceholder(placeholder),
                                count = bucket.n,
                                placeholder = placeholder,
                                head = bucket.head,
                                len = bucket.len,
                                showShape = true,
                                active = placeholder != null && placeholder == activePlaceholder,
                                hasRevealed = placeholder != null && revealed.containsKey(placeholder),
                                revealedValue = placeholder?.let { revealed[it] },
                                onToggle = { if (placeholder != null) onRevealToggle(placeholder) }
                            )
                        }
                    }
                }
            }

            if (summary.days.isNotEmpty()) {
                item { SectionLabel("近 ${state.selectedDays} 天", "${summary.days.size} 天") }
                item {
                    DayBars(
                        days = summary.days,
                        selectedDays = state.selectedDays,
                        largeFont = largeFont
                    )
                }
            }

            if (kindRows.isNotEmpty()) {
                item { SectionLabel("敏感类型", "${summary.kinds.size} 类") }
                item {
                    KindRanking(
                        rows = kindRows,
                        selectedKind = state.selectedKind,
                        onKindChange = onKindChange
                    )
                }
            }

            if (byEntries.isNotEmpty()) {
                item { SectionLabel("发现方式", "共 ${byEntries.sumOf { it.second }} 个") }
                item { DiscoveryPanel(entries = byEntries) }
            }

            item {
                SectionLabel(
                    label = "命中记录",
                    trailing = if (state.selectedKind != null) "清除筛选" else "${records.size} 条",
                    onTrailingClick = clearKind
                )
            }

            if (records.isEmpty()) {
                item {
                    Text(
                        text = if (state.selectedKind != null) "该类型暂无记录" else "暂无命中记录",
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                itemsIndexed(records) { index, record ->
                    val key = record.key ?: "row-$index"
                    RecordRow(
                        record = record,
                        expanded = expandedRecord == key,
                        activePlaceholder = activePlaceholder,
                        revealed = revealed,
                        onToggleExpand = { onToggleRecord(key) },
                        onRevealToggle = onRevealToggle
                    )
                    if (index != records.lastIndex) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(0.75.dp)
                                .background(colors.divider)
                        )
                    }
                }
                if (state.more) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        ActionButton("再看 40 条", onLoadMore)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryHero(summary: MaskingSummary, largeFont: Boolean) {
    val colors = MagpieTheme.colors
    DataPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MagpieDimensions.RadiusDataPanel),
        contentPadding = PaddingValues(18.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = summary.total?.toString() ?: "—",
                    fontSize = 36.sp,
                    lineHeight = 40.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "个值被掩码",
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            if (largeFont) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        MetaItem(summary.distinct, "个不同值")
                        MetaItem(summary.requests, "个请求")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        MetaItem(summary.blocked, "被拦截", tint = colors.error)
                        MetaItem(summary.warned, "原文放行", tint = colors.warning)
                    }
                }
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    MetaItem(summary.distinct, "个不同值")
                    MetaItem(summary.requests, "个请求")
                    MetaItem(summary.blocked, "被拦截", tint = colors.error)
                    MetaItem(summary.warned, "原文放行", tint = colors.warning)
                }
            }
        }
    }
}

@Composable
private fun MetaItem(value: Long?, label: String, tint: Color? = null) {
    val colors = MagpieTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = value?.toString() ?: "—",
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            color = tint ?: colors.textPrimary
        )
        Spacer(Modifier.width(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = colors.textSecondary
        )
    }
}

@Composable
private fun DayBars(days: List<MaskingDayPoint>, selectedDays: Int, largeFont: Boolean) {
    val colors = MagpieTheme.colors
    var selectedDay by remember(selectedDays) { mutableStateOf<String?>(null) }
    val maxN = days.maxOfOrNull { it.n ?: 0L } ?: 0L
    val selected = days.firstOrNull { it.dayLabel == selectedDay }

    DataPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MagpieDimensions.RadiusDataPanel),
        contentPadding = PaddingValues(18.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                horizontalArrangement = Arrangement.spacedBy(if (days.size > 7) 2.dp else 6.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                days.forEachIndexed { index, day ->
                    val isSelected = day.dayLabel == selectedDay
                    val fraction = if (maxN > 0L) {
                        ((day.n ?: 0L).toFloat() / maxN.toFloat()).coerceIn(0.06f, 1f)
                    } else {
                        0.06f
                    }
                    val barColor = if (isSelected || index == days.lastIndex) {
                        colors.accent
                    } else {
                        colors.accent.copy(alpha = 0.35f)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .semantics {
                                contentDescription = dayDescription(day, isSelected)
                            }
                            .springPress(scaleDown = 0.98f) {
                                selectedDay = if (isSelected) null else day.dayLabel
                            },
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(fraction)
                                .clip(RoundedCornerShape(6.dp))
                                .background(barColor)
                        )
                    }
                }
            }

            if (days.size <= 7) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (days.size > 7) 2.dp else 6.dp)
                ) {
                    days.forEach { day ->
                        Text(
                            text = dayAxisLabel(day.dayLabel, compact = largeFont),
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = if (day.dayLabel == selectedDay) colors.accent else colors.textTertiary,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = selected?.let {
                    "${dayLabel(it.dayLabel)} · ${it.n ?: 0} 个值 · ${it.requests ?: 0} 个请求"
                } ?: "点柱子查看当天",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = if (selected != null) colors.textPrimary else colors.textTertiary
            )
        }
    }
}

@Composable
private fun KindRanking(
    rows: List<KindRow>,
    selectedKind: String?,
    onKindChange: (String?) -> Unit
) {
    val colors = MagpieTheme.colors
    val maxN = rows.maxOfOrNull { it.n }?.coerceAtLeast(1L) ?: 1L
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        rows.forEach { row ->
            val selected = row.kind != null && row.kind == selectedKind
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = MagpieDimensions.TouchTargetMinimum)
                    .clip(RoundedCornerShape(MagpieDimensions.RadiusItem))
                    .background(if (selected) colors.accentSurface else Color.Transparent)
                    .then(
                        if (row.selectable) {
                            Modifier.springPress(scaleDown = 0.98f) {
                                onKindChange(if (selected) null else row.kind)
                            }
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = row.label,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = row.n.toString(),
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = colors.textSecondary
                    )
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.divider)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(row.n.toFloat() / maxN.toFloat())
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.accent)
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoveryPanel(entries: List<Pair<String, Int>>) {
    val colors = MagpieTheme.colors
    val palette = listOf(
        colors.accent,
        colors.accent.copy(alpha = 0.72f),
        colors.accent.copy(alpha = 0.52f),
        colors.accent.copy(alpha = 0.34f),
        colors.textTertiary
    )
    DataPanel(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MagpieDimensions.RadiusDataPanel),
        contentPadding = PaddingValues(18.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.divider),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                entries.forEachIndexed { index, entry ->
                    Box(
                        modifier = Modifier
                            .weight(entry.second.toFloat())
                            .fillMaxHeight()
                            .background(palette[index % palette.size])
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                entries.forEachIndexed { index, entry ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(palette[index % palette.size])
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = discoveryLabel(entry.first) ?: entry.first,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = colors.textSecondary
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = entry.second.toString(),
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = colors.textPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(
    label: String,
    trailing: String,
    onTrailingClick: (() -> Unit)? = null
) {
    val colors = MagpieTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f)
        )
        if (onTrailingClick != null) {
            Box(
                modifier = Modifier
                    .heightIn(min = MagpieDimensions.TouchTargetMinimum)
                    .springPress(scaleDown = 0.96f, onClick = onTrailingClick)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = trailing,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = colors.accent
                )
            }
        } else {
            Text(
                text = trailing,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = colors.textSecondary
            )
        }
    }
}

@Composable
private fun EmptyState(days: Int) {
    val colors = MagpieTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "网关还没有掩码记录",
            fontSize = 17.sp,
            lineHeight = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "最近 $days 天没有检测到敏感内容。",
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = colors.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ActionButton(label: String, onClick: () -> Unit) {
    val colors = MagpieTheme.colors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(MagpieDimensions.TouchTargetMinimum)
            .clip(RoundedCornerShape(MagpieDimensions.RadiusItem))
            .background(colors.accentSurface)
            .springPress(scaleDown = 0.98f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = colors.accent
        )
    }
}

@Composable
private fun MaskValueChip(
    kind: String?,
    count: Long?,
    placeholder: String?,
    head: String?,
    len: Long?,
    showShape: Boolean,
    active: Boolean,
    hasRevealed: Boolean,
    revealedValue: String?,
    onToggle: () -> Unit
) {
    val colors = MagpieTheme.colors
    val canReveal = !placeholder.isNullOrBlank()
    val label = kindLabel(kind)
    val shape = maskedShape(head, len) ?: "••••••"
    val original = revealedValue
    val chipShape = RoundedCornerShape(6.dp)

    Column(
        modifier = Modifier
            .then(
                if (canReveal) Modifier.heightIn(min = MagpieDimensions.TouchTargetMinimum) else Modifier
            )
            .clip(chipShape)
            .background(if (active) colors.accentSurface else colors.surfaceSubtle)
            .border(
                width = 0.75.dp,
                color = if (active) colors.accent.copy(alpha = 0.30f) else colors.divider,
                shape = chipShape
            )
            .then(
                if (canReveal) {
                    Modifier.springPress(scaleDown = 0.98f, onClick = onToggle)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = if (count != null) "$label ×$count" else label,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            color = if (active) colors.accent else colors.textSecondary
        )
        if (active || showShape) {
            Spacer(Modifier.height(2.dp))
            when {
                active && hasRevealed && original != null && original.isNotEmpty() -> Text(
                    text = original,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = colors.textPrimary,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = "原文已显示，10 秒后自动隐藏"
                    }
                )
                active && hasRevealed && original == null -> Text(
                    text = "原文已不可用",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = colors.warning
                )
                active && hasRevealed -> Text(
                    text = "无可显示的原文",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = colors.warning
                )
                active -> Text(
                    text = "正在取回原文…",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = colors.textSecondary
                )
                else -> Text(
                    text = shape,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun RecordRow(
    record: MaskingRecord,
    expanded: Boolean,
    activePlaceholder: String?,
    revealed: Map<String, String?>,
    onToggleExpand: () -> Unit,
    onRevealToggle: (String) -> Unit
) {
    val colors = MagpieTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MagpieDimensions.TouchTargetMinimum)
            .springPress(scaleDown = 0.995f, onClick = onToggleExpand)
            .padding(vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = "${timeLabel(record.observedAt)} · ${whatLabel(record.what)}",
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            if (record.blocked == true) {
                Text(
                    text = "已拦截",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = colors.error,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.errorSurface)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "收起记录" else "展开记录",
                tint = colors.textTertiary,
                modifier = Modifier.size(18.dp)
            )
        }

        val route = routeLabel(record)
        if (route.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = route,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = colors.textTertiary
            )
        }

        Spacer(Modifier.height(8.dp))
        if (!expanded) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                record.values.forEach { value ->
                    MaskValueChip(
                        kind = value.kind,
                        count = value.n,
                        placeholder = value.placeholder,
                        head = value.head,
                        len = value.len,
                        showShape = false,
                        active = value.placeholder != null && value.placeholder == activePlaceholder,
                        hasRevealed = value.placeholder != null && revealed.containsKey(value.placeholder),
                        revealedValue = value.placeholder?.let { revealed[it] },
                        onToggle = { value.placeholder?.let { onRevealToggle(it) } }
                    )
                }
            }
        } else {
            if (record.values.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    record.values.forEach { value ->
                        ValueDetailRow(
                            value = value,
                            active = value.placeholder != null && value.placeholder == activePlaceholder,
                            hasRevealed = value.placeholder != null && revealed.containsKey(value.placeholder),
                            revealedValue = value.placeholder?.let { revealed[it] },
                            onToggle = { value.placeholder?.let { onRevealToggle(it) } }
                        )
                    }
                }
            }
            if (record.snippets.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "供应商看到的原文",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = colors.textSecondary
                )
                Spacer(Modifier.height(6.dp))
                record.snippets.forEach { (_, snippet) ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(MagpieDimensions.RadiusItem))
                            .background(colors.surfaceSubtle)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = snippet,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = colors.textPrimary,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun ValueDetailRow(
    value: MaskingCount,
    active: Boolean,
    hasRevealed: Boolean,
    revealedValue: String?,
    onToggle: () -> Unit
) {
    val colors = MagpieTheme.colors
    val canReveal = !value.placeholder.isNullOrBlank()
    val shape = maskedShape(value.head, value.len) ?: "掩码形状未提供"
    val original = revealedValue

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MagpieDimensions.TouchTargetMinimum)
            .then(
                if (canReveal) {
                    Modifier.springPress(scaleDown = 0.99f, onClick = onToggle)
                } else {
                    Modifier
                }
            )
            .padding(vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = kindLabel(value.kind),
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary
            )
            Text(
                text = " · " + (discoveryLabel(value.by) ?: "发现方式未提供"),
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = colors.textSecondary
            )
            Spacer(Modifier.weight(1f))
            if (value.n != null) {
                Text(
                    text = "×${value.n}",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = colors.textSecondary
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        when {
            active && hasRevealed && original != null && original.isNotEmpty() -> Text(
                text = original,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = colors.textPrimary,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = "原文已显示，10 秒后自动隐藏"
                }
            )
            active && hasRevealed && original == null -> Text(
                text = "原文已不可用",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = colors.warning
            )
            active && hasRevealed -> Text(
                text = "无可显示的原文",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = colors.warning
            )
            active -> Text(
                text = "正在取回原文…",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = colors.textSecondary
            )
            else -> Text(
                text = shape,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = colors.textPrimary
            )
        }
    }
}

private data class KindRow(
    val kind: String?,
    val label: String,
    val n: Long,
    val selectable: Boolean
)

private fun buildKindRows(kinds: List<MaskingCount>): List<KindRow> {
    val cleaned = kinds.mapNotNull { item ->
        val kind = item.kind?.trim().orEmpty()
        if (kind.isEmpty()) null else KindRow(kind, kindLabel(kind), item.n ?: 0L, true)
    }.sortedByDescending { it.n }
    if (cleaned.isEmpty()) return emptyList()
    val top = cleaned.take(6)
    val rest = cleaned.drop(6)
    if (rest.isEmpty()) return top
    return top + KindRow(
        kind = null,
        label = "其他",
        n = rest.sumOf { it.n },
        selectable = false
    )
}

private val DISCOVERY_KEYS = listOf("rule", "yours", "word", "model", "known")

private fun discoveryEntries(counts: Map<String, Int>): List<Pair<String, Int>> {
    val entries = mutableListOf<Pair<String, Int>>()
    DISCOVERY_KEYS.forEach { key ->
        val n = counts[key] ?: 0
        if (n > 0) entries.add(key to n)
    }
    counts.forEach { (key, n) ->
        if (key !in DISCOVERY_KEYS && n > 0) entries.add(key to n)
    }
    return entries
}

private fun kindLabel(raw: String?): String {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return "敏感值"
    return when (value.uppercase(Locale.ROOT)) {
        "EMAIL" -> "邮箱"
        "API_KEY", "APIKEY", "SECRET", "KEY" -> "密钥"
        "TOKEN" -> "令牌"
        "PHONE", "PHONE_NUMBER" -> "电话号码"
        "ADDRESS" -> "地址"
        "CUSTOM" -> "自定义"
        "ID_CARD", "IDCARD" -> "身份证"
        "BANK_CARD", "BANKCARD" -> "银行卡"
        else -> value
    }
}

private fun discoveryLabel(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    return when (value.lowercase(Locale.ROOT)) {
        "rule" -> "内置规则"
        "yours" -> "自定义规则"
        "word" -> "自定义词"
        "model" -> "本地模型"
        "known" -> "见过的值"
        else -> value
    }
}

private fun whatLabel(raw: String?): String {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return "请求"
    return when (value.lowercase(Locale.ROOT)) {
        "chat" -> "对话"
        "search" -> "搜索"
        "image" -> "图像"
        "video" -> "视频"
        "decide" -> "决策"
        "embeddings" -> "向量"
        "audio" -> "音频"
        else -> value
    }
}

private fun kindFromPlaceholder(placeholder: String?): String? {
    val token = placeholder
        ?.trim()
        ?.trim('{', '}', '<', '>')
        ?.trim()
        .orEmpty()
    if (token.isEmpty()) return null
    return token.substringBeforeLast('_', token).takeIf { it.isNotBlank() }
}

private fun maskedShape(head: String?, len: Long?): String? {
    val total = len ?: return null
    if (total <= 0L) return null
    val visible = head.orEmpty()
    val rest = (total - visible.length).coerceIn(1L, 8L).toInt()
    return visible + "•".repeat(rest)
}

private fun timeLabel(raw: String?): String {
    val formatted = RequestTimestamps.formatTime(raw)
    return if (formatted == RequestTimestamps.MISSING) formatted else formatted.take(5)
}

private fun routeLabel(record: MaskingRecord): String {
    val agent = record.agent?.trim().orEmpty()
    val provider = record.provider?.trim().orEmpty()
    val model = record.model?.trim().orEmpty()
    return buildString {
        when {
            agent.isNotEmpty() && provider.isNotEmpty() -> append("$agent → $provider")
            agent.isNotEmpty() -> append(agent)
            provider.isNotEmpty() -> append(provider)
        }
        if (model.isNotEmpty()) {
            if (isNotEmpty()) append(" · ")
            append(model)
        }
    }
}

private fun dayAxisLabel(raw: String, compact: Boolean): String {
    val parts = raw.split("-")
    if (parts.size < 3) return raw
    val month = parts[1].toIntOrNull() ?: return raw
    val day = parts[2].take(2).toIntOrNull() ?: return raw
    return if (compact) "${month}/${day}" else String.format(Locale.ROOT, "%02d/%02d", month, day)
}

private fun dayLabel(raw: String): String {
    val parts = raw.split("-")
    if (parts.size < 3) return raw
    val month = parts[1].toIntOrNull() ?: return raw
    val day = parts[2].take(2).toIntOrNull() ?: return raw
    return String.format(Locale.ROOT, "%02d/%02d", month, day)
}

private fun dayDescription(day: MaskingDayPoint, selected: Boolean): String =
    "${dayLabel(day.dayLabel)}，${day.n ?: 0} 个值，${day.requests ?: 0} 个请求" +
        if (selected) "，已选中" else ""
