package com.magpie.pocket.feature.usage

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.magpie.pocket.core.design.ChipGrid
import com.magpie.pocket.core.design.DataPanel
import com.magpie.pocket.core.design.DualLineChart
import com.magpie.pocket.core.design.MagpieDimensions
import com.magpie.pocket.core.design.MagpiePageHeader
import com.magpie.pocket.core.design.MagpieTheme
import com.magpie.pocket.core.design.MagpieTypography
import com.magpie.pocket.core.design.SectionHeader
import com.magpie.pocket.core.design.SegmentedControl
import com.magpie.pocket.core.design.springPress
import com.magpie.pocket.core.model.AccountQuotaView
import com.magpie.pocket.core.model.CostDisplay
import com.magpie.pocket.core.model.Period
import com.magpie.pocket.core.model.QuotaDisplayMode
import com.magpie.pocket.core.model.QuotaWindow
import com.magpie.pocket.core.model.TokenUsage
import com.magpie.pocket.core.model.UsageBreakdownItem
import com.magpie.pocket.core.model.UsageSnapshot
import com.magpie.pocket.core.util.MetricCalculators
import com.magpie.pocket.core.util.RequestTimestamps
import java.util.Locale

/** Fixed LazyColumn row of the "额度健康" header; the "quota" anchor scrolls to it. */
private const val QuotaSectionItemIndex = 11

private val periodOptions = listOf(
    Period.TODAY to "今日",
    Period.SEVEN_DAYS to "近 7 天",
    Period.THIRTY_DAYS to "近 30 天",
    Period.ALL to "全部"
)

/**
 * Usage page: one period at a time -> spend and tokens -> daily trend -> ranked detail ->
 * quota health. Quotas are a snapshot of now and never follow the selected period.
 *
 * The LazyColumn item order is fixed so [initialSection] == "quota" can scroll to
 * [QuotaSectionItemIndex]; keep both in sync when adding a section.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsageScreen(
    usage: UsageSnapshot?,
    accountQuotas: List<AccountQuotaView>,
    quotaDisplayMode: QuotaDisplayMode,
    onQuotaDisplayModeChange: (QuotaDisplayMode) -> Unit,
    usageCost: CostDisplay,
    usageDecodeSpeed: Double?,
    selectedPeriod: Period,
    onPeriodChange: (Period) -> Unit,
    isLoading: Boolean,
    contentPadding: PaddingValues,
    initialSection: String? = null,
    onSectionConsumed: () -> Unit = {},
    modifier: Modifier = Modifier,
    isDemo: Boolean = false
) {
    val colors = MagpieTheme.colors
    val maskAccountNames = MagpieTheme.preferences.maskAccountNames
    val listState = rememberLazyListState()

    // Only the key is held: the sheet renders the row from the latest list, so a masking change
    // or a refresh lands while it stays open.
    var selectedQuotaKey by remember { mutableStateOf<String?>(null) }
    var dimensionKey by rememberSaveable { mutableStateOf(UsageDimension.MODELS.name) }
    var sortKey by rememberSaveable { mutableStateOf("") }
    var breakdownExpanded by rememberSaveable { mutableStateOf(false) }

    val dimension = UsageDimension.values().firstOrNull { it.name == dimensionKey }
        ?: UsageDimension.MODELS
    val rows = usageRows(usage, dimension, maskAccountNames)
    // A sort key no row carries cannot order anything: its chip stays disabled, and a
    // remembered selection falls back to a default sort that has data.
    val fallbackSort = defaultSort(dimension, rows)
    val sortOptions = availableUsageSorts(rows, fallbackSort)
    val requestedSort = UsageSort.values().firstOrNull { it.name == sortKey }
    val activeSort = when {
        requestedSort != null && requestedSort in sortOptions -> requestedSort
        fallbackSort in sortOptions -> fallbackSort
        else -> sortOptions.firstOrNull() ?: fallbackSort
    }
    val sortedRows = sortRows(rows, activeSort)

    val allWindows = accountQuotas.flatMap { it.windows }
    val allUnlimited = allWindows.isNotEmpty() && allWindows.all { it.unlimited }
    val accountsExpandedByDefault =
        accountQuotas.size == 1 || allWindows.any { MetricCalculators.isQuotaWarning(it) }
    var accountsExpanded by rememberSaveable(accountsExpandedByDefault) {
        mutableStateOf(accountsExpandedByDefault)
    }

    val cacheHitRate = MetricCalculators.formatPercent(
        MetricCalculators.calculateCacheHitRate(usage?.tokens)
    )

    LaunchedEffect(initialSection) {
        if (initialSection == "quota") {
            listState.animateScrollToItem(QuotaSectionItemIndex)
            onSectionConsumed()
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            MagpiePageHeader(
                title = "用量",
                subtitle = "一段时间",
                actions = {
                    if (isDemo) DemoBadge()
                }
            )
        }

        item {
            SegmentedControl(
                options = periodOptions.map { it.second },
                selectedIndex = periodOptions.indexOfFirst { it.first == selectedPeriod },
                onSelect = { index ->
                    periodOptions.getOrNull(index)?.first?.let(onPeriodChange)
                },
                modifier = Modifier
                    .padding(horizontal = MagpieDimensions.PagePadding)
                    .padding(top = 16.dp)
            )
        }

        item {
            CostCard(
                usage = usage,
                usageCost = usageCost,
                period = selectedPeriod,
                modifier = Modifier
                    .padding(horizontal = MagpieDimensions.PagePadding)
                    .padding(top = 16.dp)
            )
        }

        item {
            TokenCard(
                usage = usage,
                cacheHitRate = cacheHitRate,
                usageDecodeSpeed = usageDecodeSpeed,
                modifier = Modifier
                    .padding(horizontal = MagpieDimensions.PagePadding)
                    .padding(top = 12.dp)
            )
        }

        item {
            SectionHeader(
                title = "趋势",
                note = "按日",
                modifier = Modifier
                    .padding(horizontal = MagpieDimensions.PagePadding)
                    .padding(top = 20.dp)
            )
        }

        item {
            TrendBlock(
                usage = usage,
                modifier = Modifier.padding(horizontal = MagpieDimensions.PagePadding)
            )
        }

        item {
            SectionHeader(
                title = "明细",
                modifier = Modifier
                    .padding(horizontal = MagpieDimensions.PagePadding)
                    .padding(top = 20.dp)
            )
        }

        item {
            ChipGrid(
                options = UsageDimension.values().map { it.label },
                selectedIndex = dimension.ordinal,
                onSelect = { index ->
                    val next = UsageDimension.values().getOrNull(index)
                    if (next != null && next != dimension) {
                        dimensionKey = next.name
                        sortKey = defaultSort(
                            next,
                            usageRows(usage, next, maskAccountNames)
                        ).name
                        breakdownExpanded = false
                    }
                },
                modifier = Modifier.padding(horizontal = MagpieDimensions.PagePadding)
            )
        }

        item {
            SortSelector(
                selected = activeSort,
                options = sortOptions,
                onSelect = { next ->
                    if (next != activeSort) {
                        sortKey = next.name
                        breakdownExpanded = false
                    }
                },
                modifier = Modifier
                    .padding(horizontal = MagpieDimensions.PagePadding)
                    .padding(top = 10.dp)
            )
        }

        item {
            DetailSection(
                usage = usage,
                sort = activeSort,
                rows = sortedRows,
                expanded = breakdownExpanded,
                isLoading = isLoading,
                onToggleExpanded = { breakdownExpanded = !breakdownExpanded },
                modifier = Modifier
                    .padding(horizontal = MagpieDimensions.PagePadding)
                    .padding(top = 10.dp)
            )
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        item {
            SectionHeader(
                title = "额度健康",
                note = "实时快照",
                modifier = Modifier
                    .padding(horizontal = MagpieDimensions.PagePadding)
                    .padding(top = 12.dp)
            )
        }

        item {
            QuotaPanel(
                rows = accountQuotas,
                allUnlimited = allUnlimited,
                displayMode = quotaDisplayMode,
                onDisplayModeChange = onQuotaDisplayModeChange,
                expanded = accountsExpanded,
                onToggleExpanded = { accountsExpanded = !accountsExpanded },
                onOpenDetail = { selectedQuotaKey = it.providerId },
                modifier = Modifier.padding(horizontal = MagpieDimensions.PagePadding)
            )
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }

    val detailProvider = selectedQuotaKey?.let { key ->
        accountQuotas.firstOrNull { it.providerId == key }
    }
    // A provider that left the list has no row left to show: close the sheet instead of
    // keeping a snapshot of a number the page no longer reports.
    LaunchedEffect(selectedQuotaKey, detailProvider) {
        if (selectedQuotaKey != null && detailProvider == null) selectedQuotaKey = null
    }
    if (detailProvider != null) {
        QuotaDetailSheet(
            view = detailProvider,
            displayMode = quotaDisplayMode,
            onDismiss = { selectedQuotaKey = null }
        )
    }
}

@Composable
private fun CostCard(
    usage: UsageSnapshot?,
    usageCost: CostDisplay,
    period: Period,
    modifier: Modifier = Modifier
) {
    val colors = MagpieTheme.colors
    DataPanel(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = periodCostLabel(period),
                    style = MagpieTypography.LabelStyle,
                    color = colors.textSecondary
                )
                Text(
                    text = "未计价 " +
                        (if (usage?.unpriced == null) "—" else usageCost.unpriced.toString()) + " 次",
                    style = MagpieTypography.LabelStyle,
                    color = colors.textSecondary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = MetricCalculators.formatCost(usageCost.value),
                style = MagpieTypography.HeroMetricStyle,
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "信用余额 " + creditText(usage?.credits),
                    style = MagpieTypography.LabelStyle,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "不随周期变化",
                    style = MagpieTypography.LabelStyle,
                    color = colors.textTertiary,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = colors.divider)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "仅统计已计价调用",
                style = MagpieTypography.LabelStyle,
                color = colors.textTertiary
            )
        }
    }
}

@Composable
private fun TokenCard(
    usage: UsageSnapshot?,
    cacheHitRate: String,
    usageDecodeSpeed: Double?,
    modifier: Modifier = Modifier
) {
    val colors = MagpieTheme.colors
    DataPanel(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Token",
                    style = MagpieTypography.LabelStyle,
                    color = colors.textSecondary
                )
                Text(
                    text = "含缓存读取",
                    style = MagpieTypography.LabelStyle,
                    color = colors.textSecondary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = MetricCalculators.formatTotalTokens(usage?.tokens),
                    style = MagpieTypography.InlineMetricStyle,
                    color = colors.textPrimary
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "token",
                    style = MagpieTypography.BodyStyle,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
            TokenCompositionBar(tokens = usage?.tokens)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "缓存命中率 " + cacheHitRate + " · 解码速度 " +
                    formatDecodeSpeed(usageDecodeSpeed),
                style = MagpieTypography.LabelStyle,
                color = colors.textSecondary
            )
        }
    }
}

@Composable
private fun TokenCompositionBar(tokens: TokenUsage?) {
    val colors = MagpieTheme.colors
    val values = listOf(
        tokens?.cacheRead,
        tokens?.inputUncached,
        tokens?.output,
        tokens?.cacheWrite
    )
    val total = values.sumOf { it ?: 0L }
    val palette = listOf(
        colors.accent,
        colors.accent.copy(alpha = 0.62f),
        colors.accent.copy(alpha = 0.34f),
        colors.textTertiary.copy(alpha = 0.45f)
    )
    val legend = listOf(
        "缓存" to values[0],
        "未缓存" to values[1],
        "输出" to values[2],
        "写入" to values[3]
    )
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.divider),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (total > 0L) {
                values.forEachIndexed { index, value ->
                    val part = value ?: 0L
                    if (part > 0L) {
                        Box(
                            modifier = Modifier
                                .weight(part.toFloat() / total.toFloat())
                                .fillMaxSize()
                                .background(palette[index])
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        legend.chunked(2).forEachIndexed { rowIndex, rowItems ->
            Row(modifier = Modifier.fillMaxWidth()) {
                rowItems.forEachIndexed { columnIndex, (label, value) ->
                    val index = rowIndex * 2 + columnIndex
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(palette[index])
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = label,
                            style = MagpieTypography.LabelStyle,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = value?.let { MetricCalculators.formatCompactNumber(it) } ?: "—",
                            style = MagpieTypography.LabelStyle,
                            color = colors.textPrimary
                        )
                    }
                }
            }
            if (rowIndex == 0) {
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun TrendBlock(
    usage: UsageSnapshot?,
    modifier: Modifier = Modifier
) {
    val colors = MagpieTheme.colors
    val days = usage?.dailyTrends.orEmpty()
    val hasData = days.any { it.completedRequests > 0L || it.cost != null }
    if (!hasData) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(101.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "暂无趋势数据",
                style = MagpieTypography.BodyStyle,
                color = colors.textTertiary
            )
        }
        return
    }
    DualLineChart(
        pointsA = days.map { it.completedRequests.toFloat() },
        pointsB = days.map { it.cost?.toFloat() },
        labels = trendLabels(days.map { it.dayLabel }),
        legendA = "日请求",
        legendB = "日费用",
        modifier = modifier
    )
}

@Composable
private fun SortSelector(
    selected: UsageSort,
    options: List<UsageSort>,
    onSelect: (UsageSort) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MagpieTheme.colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "排序",
            style = MagpieTypography.LabelStyle,
            color = colors.textTertiary
        )
        Spacer(modifier = Modifier.width(6.dp))
        UsageSort.values().forEach { sort ->
            val enabled = sort in options
            val isSelected = sort == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = MagpieDimensions.TouchTargetMinimum)
                    .selectable(
                        selected = isSelected,
                        enabled = enabled,
                        role = Role.RadioButton,
                        onClick = { onSelect(sort) }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = sort.label,
                    style = MagpieTypography.SubheadStyle,
                    color = when {
                        !enabled -> colors.textTertiary
                        isSelected -> colors.accent
                        else -> colors.textSecondary
                    },
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun DetailSection(
    usage: UsageSnapshot?,
    sort: UsageSort,
    rows: List<UsageRow>,
    expanded: Boolean,
    isLoading: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MagpieTheme.colors
    if (rows.isEmpty()) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = when {
                    isLoading && usage == null -> "正在同步用量数据"
                    usage == null -> "暂无用量数据"
                    else -> "该维度暂无数据"
                },
                style = MagpieTypography.BodyStyle,
                color = colors.textSecondary
            )
            if (usage != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "该周期没有可归因调用；此连接可能不返回该字段",
                    style = MagpieTypography.LabelStyle,
                    color = colors.textTertiary,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }
    val visibleRows = if (expanded) rows else rows.take(5)
    Column(modifier = modifier.fillMaxWidth()) {
        DataPanel(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(18.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "名称",
                        style = MagpieTypography.LabelStyle.copy(fontWeight = FontWeight.Medium),
                        color = colors.textTertiary
                    )
                    Text(
                        text = sort.label,
                        style = MagpieTypography.LabelStyle.copy(fontWeight = FontWeight.Medium),
                        color = colors.textTertiary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                HorizontalDivider(color = colors.divider)
                visibleRows.forEachIndexed { index, row ->
                    DetailRow(row = row, sort = sort)
                    if (index != visibleRows.lastIndex) {
                        HorizontalDivider(color = colors.divider.copy(alpha = 0.6f))
                    }
                }
            }
        }
        if (rows.size > 5) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(MagpieDimensions.TouchTargetMinimum)
                    .springPress(scaleDown = 0.98f, onClick = onToggleExpanded),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (expanded) "收起" else "查看全部 " + rows.size + " 项",
                    style = MagpieTypography.SubheadStyle,
                    color = colors.accent
                )
            }
        }
    }
}

@Composable
private fun DetailRow(row: UsageRow, sort: UsageSort) {
    val colors = MagpieTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = row.name,
                style = MagpieTypography.BodyStyle.copy(fontWeight = FontWeight.Medium),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = sortValue(row, sort),
                style = MagpieTypography.SubheadStyle.copy(fontWeight = FontWeight.Medium),
                color = colors.textPrimary,
                maxLines = 1
            )
        }
        val secondary = secondaryText(row, sort)
        if (secondary != null) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = secondary,
                style = MagpieTypography.LabelStyle,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun QuotaPanel(
    rows: List<AccountQuotaView>,
    allUnlimited: Boolean,
    displayMode: QuotaDisplayMode,
    onDisplayModeChange: (QuotaDisplayMode) -> Unit,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onOpenDetail: (AccountQuotaView) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MagpieTheme.colors
    // Rows arrive worst first, so the first row with a ratio is also the tightest provider.
    val tightestProvider = rows.firstOrNull { it.ratio != null }
    DataPanel(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 64.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val ringRatio = tightestProvider?.ratio
                if (ringRatio != null) {
                    QuotaHealthRing(
                        ratio = ringRatio,
                        displayMode = displayMode,
                        color = quotaRatioColor(ringRatio)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.accentSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Key,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    if (tightestProvider != null) {
                        Text(
                            text = "最紧供应商",
                            style = MagpieTypography.LabelStyle,
                            color = colors.textSecondary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = tightestProvider.label,
                            style = MagpieTypography.BodyStyle.copy(fontWeight = FontWeight.Medium),
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = quotaStateText(tightestProvider, displayMode),
                            style = MagpieTypography.LabelStyle,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = when {
                                rows.isEmpty() -> "网关未返回额度窗口"
                                allUnlimited -> "全部供应商无限额"
                                else -> "额度不可用"
                            },
                            style = MagpieTypography.BodyStyle.copy(fontWeight = FontWeight.Medium),
                            color = colors.textPrimary
                        )
                        if (rows.isNotEmpty() && !allUnlimited) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "无可查询的额度窗口",
                                style = MagpieTypography.LabelStyle,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (displayMode == QuotaDisplayMode.REMAINING) {
                    "数字是剩余比例，越少越危险"
                } else {
                    "数字是已用比例，越满越危险"
                },
                style = MagpieTypography.LabelStyle,
                color = colors.textTertiary
            )
            if (rows.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = colors.divider)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "全部供应商",
                        style = MagpieTypography.LabelStyle,
                        color = colors.textSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    QuotaDisplayModeSwitch(
                        mode = displayMode,
                        onChange = onDisplayModeChange
                    )
                }
                val shown = if (expanded) rows else rows.take(4)
                shown.forEach { row ->
                    QuotaRowView(
                        row = row,
                        displayMode = displayMode,
                        onClick = { onOpenDetail(row) }
                    )
                }
                if (rows.size > 4) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(MagpieDimensions.TouchTargetMinimum)
                            .springPress(scaleDown = 0.98f, onClick = onToggleExpanded),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (expanded) {
                                "收起"
                            } else {
                                "展开全部 " + rows.size + " 个供应商"
                            },
                            style = MagpieTypography.SubheadStyle,
                            color = colors.accent
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuotaRowView(
    row: AccountQuotaView,
    displayMode: QuotaDisplayMode,
    onClick: () -> Unit
) {
    val colors = MagpieTheme.colors
    // The printed share follows the preference; the colour and the sort key never do.
    val ratio = row.ratio
    val share = quotaFraction(row, displayMode)
    // One colour for both the number and its line, off the used ratio in either mode.
    val danger = if (ratio != null && ratio >= WarningRatio) quotaRatioColor(ratio) else null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = row.label,
                        style = MagpieTypography.SubheadStyle.copy(fontWeight = FontWeight.Medium),
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Text(
                    text = quotaStateText(row, displayMode),
                    style = MagpieTypography.LabelStyle,
                    color = danger ?: colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = MetricCalculators.formatPercent(share),
                style = MagpieTypography.SubheadStyle,
                color = danger ?: colors.textPrimary
            )
        }
        // The bar mirrors the printed share: a remaining row must not look full at 3.8% left.
        if (share != null && ratio != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(colors.divider)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(share.coerceIn(0.0, 1.0).toFloat())
                        .fillMaxSize()
                        .clip(RoundedCornerShape(4.dp))
                        .background(quotaRatioColor(ratio))
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = colors.divider)
    }
}

/**
 * Two small pills naming the two readings of the same share. Each pill is 28dp tall but keeps a
 * 40dp touch target, and the switch renders the stored preference, never a local guess.
 */
@Composable
private fun QuotaDisplayModeSwitch(
    mode: QuotaDisplayMode,
    onChange: (QuotaDisplayMode) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        QuotaDisplayModePill(
            label = "已用",
            option = QuotaDisplayMode.USED,
            mode = mode,
            onChange = onChange
        )
        QuotaDisplayModePill(
            label = "剩余",
            option = QuotaDisplayMode.REMAINING,
            mode = mode,
            onChange = onChange
        )
    }
}

@Composable
private fun QuotaDisplayModePill(
    label: String,
    option: QuotaDisplayMode,
    mode: QuotaDisplayMode,
    onChange: (QuotaDisplayMode) -> Unit
) {
    val colors = MagpieTheme.colors
    val selected = option == mode
    Box(
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(14.dp))
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = { if (!selected) onChange(option) }
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .height(28.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (selected) colors.accentSurface else Color.Transparent)
                .padding(horizontal = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MagpieTypography.LabelStyle.copy(fontSize = 12.sp),
                color = if (selected) colors.accent else colors.textSecondary
            )
        }
    }
}

/**
 * The ring reads the share the page prints, and its caption names that share. Colour stays on the
 * used ratio, so the provider closest to its limit is red in both modes.
 */
@Composable
private fun QuotaHealthRing(ratio: Double, displayMode: QuotaDisplayMode, color: Color) {
    val colors = MagpieTheme.colors
    val shown = if (displayMode == QuotaDisplayMode.REMAINING) 1.0 - ratio else ratio
    // A spent provider is an empty ring in remaining mode: the track carries the alarm instead.
    val track = if (ratio >= ErrorRatio) colors.error.copy(alpha = 0.18f) else colors.divider
    Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 6.dp.toPx()
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
            drawArc(
                color = track,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth)
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = (360f * shown.coerceIn(0.0, 1.0)).toFloat(),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = MetricCalculators.formatPercent(shown.coerceIn(0.0, 1.0)),
                style = MagpieTypography.LabelStyle.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary
            )
            Text(
                text = if (displayMode == QuotaDisplayMode.USED) "已用" else "剩余",
                style = MagpieTypography.LabelStyle.copy(fontSize = 10.sp),
                color = colors.textTertiary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuotaDetailSheet(
    view: AccountQuotaView,
    displayMode: QuotaDisplayMode,
    onDismiss: () -> Unit
) {
    val colors = MagpieTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        shape = RoundedCornerShape(
            topStart = MagpieDimensions.RadiusSheetTop,
            topEnd = MagpieDimensions.RadiusSheetTop
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MagpieDimensions.PagePadding)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "额度窗口详情",
                        style = MagpieTypography.SheetTitleStyle,
                        color = colors.textPrimary
                    )
                    Text(
                        text = view.label,
                        style = MagpieTypography.LabelStyle,
                        color = colors.textSecondary
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(MagpieDimensions.TouchTargetMinimum)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "关闭",
                        tint = colors.textSecondary
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            view.windows.forEach { window ->
                val ratio = MetricCalculators.calculateQuotaUsedRatio(window)
                Column(modifier = Modifier.padding(vertical = 9.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = window.id,
                            style = MagpieTypography.SubheadStyle.copy(fontWeight = FontWeight.Medium),
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = quotaWindowText(window, ratio, displayMode),
                            style = MagpieTypography.SubheadStyle,
                            color = colors.textSecondary,
                            maxLines = 1
                        )
                    }
                    val reset = RequestTimestamps.formatResetAt(window.resetsAt)
                    if (reset != null) {
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "重置 " + reset,
                            style = MagpieTypography.LabelStyle,
                            color = colors.textSecondary
                        )
                    }
                }
                HorizontalDivider(color = colors.divider)
            }
        }
    }
}

@Composable
private fun DemoBadge() {
    val colors = MagpieTheme.colors
    Text(
        text = "演示",
        style = MagpieTypography.LabelStyle,
        color = colors.accent,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.accentSurface)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

private enum class UsageDimension(val label: String) {
    MODELS("模型"),
    AGENTS("智能体"),
    SESSIONS("会话"),
    ACCOUNTS("账号"),
    CALLER_KEYS("调用方密钥"),
    PROVIDER_KEYS("供应商密钥")
}

private fun periodCostLabel(period: Period): String =
    if (period == Period.TODAY) "今日费用" else "该周期费用"

private fun creditText(credits: Double?): String =
    credits?.let { "¥" + MetricCalculators.formatCredits(it) } ?: "—"

private fun formatDecodeSpeed(speed: Double?): String =
    speed?.let { String.format(Locale.US, "%.1f tok/s", it) } ?: "—"

private fun trendLabels(labels: List<String>): List<String> {
    if (labels.size <= 5) return labels
    val indexes = listOf(0, labels.size / 3, labels.size * 2 / 3, labels.lastIndex).distinct()
    return indexes.map { labels[it] }
}

private fun usageRows(
    usage: UsageSnapshot?,
    dimension: UsageDimension,
    maskAccountNames: Boolean
): List<UsageRow> {
    if (usage == null) return emptyList()
    if (dimension == UsageDimension.MODELS) {
        return usage.models
            .sortedByDescending { it.totalTokens }
            .map { item ->
                UsageRow(
                    name = item.modelId,
                    calls = null,
                    cost = null,
                    tokens = item.totalTokens,
                    errors = null
                )
            }
    }
    val source = when (dimension) {
        UsageDimension.AGENTS -> usage.agents
        UsageDimension.SESSIONS -> usage.sessions
        UsageDimension.ACCOUNTS -> usage.accounts
        UsageDimension.CALLER_KEYS -> usage.callerKeys
        UsageDimension.PROVIDER_KEYS -> usage.providerKeys
        UsageDimension.MODELS -> emptyList()
    }
    return source.map { item ->
        UsageRow(
            name = displayBreakdownName(item, dimension, maskAccountNames),
            calls = item.calls,
            cost = item.cost,
            tokens = tokensOf(item),
            errors = item.errors
        )
    }
}

private fun tokensOf(item: UsageBreakdownItem): Long? {
    val categories = listOf(item.input, item.output, item.cacheRead, item.cacheWrite)
    if (categories.all { it == null }) return null
    return categories.sumOf { it ?: 0L }
}

private fun displayBreakdownName(
    item: UsageBreakdownItem,
    dimension: UsageDimension,
    maskAccountNames: Boolean
): String {
    val raw = item.label?.takeIf { it.isNotBlank() }
        ?: item.id?.takeIf { it.isNotBlank() }
        ?: "—"
    return when (dimension) {
        UsageDimension.AGENTS -> when (raw) {
            "codex" -> "Codex"
            "claude-code", "claude_code" -> "Claude Code"
            else -> raw
        }
        UsageDimension.SESSIONS -> if (raw != "—" && raw.length > 6) "…" + raw.takeLast(6) else raw
        UsageDimension.ACCOUNTS -> MetricCalculators.maskAccountName(raw, maskAccountNames)
        UsageDimension.CALLER_KEYS -> if (raw.contains("@")) {
            MetricCalculators.maskAccountName(raw, maskAccountNames)
        } else {
            raw
        }
        UsageDimension.PROVIDER_KEYS -> if (raw == "—") raw else "…" + raw.takeLast(4)
        UsageDimension.MODELS -> raw
    }
}

private fun defaultSort(dimension: UsageDimension, rows: List<UsageRow>): UsageSort {
    if (dimension == UsageDimension.MODELS) return UsageSort.TOKENS
    return when {
        rows.any { it.cost != null } -> UsageSort.COST
        rows.any { (it.tokens ?: 0L) > 0L } -> UsageSort.TOKENS
        else -> UsageSort.CALLS
    }
}

private fun sortRows(rows: List<UsageRow>, sort: UsageSort): List<UsageRow> =
    rows.sortedWith(
        compareByDescending<UsageRow> { sortMetric(it, sort) ?: Double.NEGATIVE_INFINITY }
            .thenBy { it.name }
    )

private fun sortValue(row: UsageRow, sort: UsageSort): String = when (sort) {
    UsageSort.COST -> MetricCalculators.formatCost(row.cost)
    UsageSort.TOKENS -> row.tokens?.let {
        MetricCalculators.formatCompactNumber(it) + " token"
    } ?: "—"
    UsageSort.CALLS -> row.calls?.let { it.toString() + " 次" } ?: "—"
}

private fun secondaryText(row: UsageRow, sort: UsageSort): String? {
    val parts = buildList {
        if (sort != UsageSort.CALLS) {
            row.calls?.let { add(it.toString() + " 次调用") }
        }
        if (sort != UsageSort.TOKENS) {
            row.tokens?.takeIf { it > 0L }?.let {
                add(MetricCalculators.formatCompactNumber(it) + " token")
            }
        }
        if (sort != UsageSort.COST) {
            row.cost?.let { add("¥" + MetricCalculators.formatCost(it)) }
        }
        row.errors?.takeIf { it > 0L }?.let {
            add(it.toString() + " 次最终失败")
        }
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/**
 * A provider row reads its windows, not the answer's trust flag: unlimited is a known answer, so
 * "无限额" must win before anything says the figures are missing.
 */
private fun quotaStateText(view: AccountQuotaView, displayMode: QuotaDisplayMode): String {
    val window = view.tightestWindow
    if (window != null) {
        val reset = RequestTimestamps.formatResetAt(window.resetsAt)
        val parts = listOfNotNull(
            window.id,
            quotaRowFigure(view, displayMode),
            reset?.let { "重置 " + it }
        )
        return parts.joinToString(" · ")
    }
    return when {
        view.windows.isEmpty() -> "暂无额度窗口"
        view.windows.all { it.unlimited } -> "无限额"
        view.known -> "额度未知"
        else -> "无可查询的额度窗口"
    }
}

/**
 * Danger colour off the used ratio, never off the printed share: in remaining mode the smallest
 * number is the most dangerous, so the colour cannot follow the number.
 */
@Composable
private fun quotaRatioColor(ratio: Double): Color {
    val colors = MagpieTheme.colors
    return when {
        ratio >= ErrorRatio -> colors.error
        ratio >= WarningRatio -> colors.warning
        else -> colors.accent
    }
}

/** A quota at or past this used share is in the warning zone; at or past the next one, red. */
private const val WarningRatio = 0.7
private const val ErrorRatio = 0.9

/** The share one provider prints in the chosen mode, 0..1; a row without a number has none. */
private fun quotaFraction(view: AccountQuotaView, displayMode: QuotaDisplayMode): Double? {
    val percent = when (displayMode) {
        QuotaDisplayMode.USED -> view.usedPercent
        QuotaDisplayMode.REMAINING -> view.remainingPercent
    } ?: return null
    return (percent / 100.0).coerceIn(0.0, 1.0)
}

/**
 * The row's own figure line: the absolute pair when the answer reported one, the share
 * otherwise, the upstream's text for a display-only window, and nothing for an unknown one.
 */
private fun quotaRowFigure(view: AccountQuotaView, displayMode: QuotaDisplayMode): String? {
    val window = view.tightestWindow ?: return null
    if (window.text) return view.displayText
    if (!window.known) return null
    val used = displayMode == QuotaDisplayMode.USED
    val label = if (used) "已用 " else "剩余 "
    val amount = if (used) view.usedAmount else view.remainingAmount
    val allowance = window.allowanceLimit
    if (amount != null && allowance != null) {
        val unit = view.unitLabel?.let { " " + it }.orEmpty()
        return label + formatQuotaFigure(amount) + " / " + formatQuotaFigure(allowance) + unit
    }
    val share = if (used) view.usedPercent else view.remainingPercent
    return share?.let { label + MetricCalculators.formatPercent(it / 100.0) }
}

/**
 * One window in the sheet, in the same words the provider row uses. The upstream's own rendering
 * wins when it sent one; a display-only window has nothing else to show.
 */
private fun quotaWindowText(
    window: QuotaWindow,
    ratio: Double?,
    displayMode: QuotaDisplayMode
): String {
    if (window.unlimited) return "无限额"
    val reported = window.display?.takeIf { it.isNotBlank() }
    if (reported != null) return reported
    if (window.text || !window.known) return "—"
    val used = displayMode == QuotaDisplayMode.USED
    val label = if (used) "已用 " else "剩余 "
    val share = ratio?.let { if (used) it else (1.0 - it).coerceAtLeast(0.0) }
    val percent = MetricCalculators.formatPercent(share)
    val amount = window.amount
    val allowance = window.allowanceLimit
    return if (amount != null && allowance != null) {
        val value = if (used) amount else (allowance - amount).coerceAtLeast(0.0)
        label + formatQuotaFigure(value) + " / " + formatQuotaFigure(allowance) +
            " " + window.unit + " · " + percent
    } else {
        label + percent + if (window.unit.isNotBlank()) " " + window.unit else ""
    }
}

/**
 * A quota figure keeps a decimal while it is small and turns compact once it is a token count:
 * a row can say "已用 200.0M / 200.0M" but never "已用 200029738 / 200000000".
 */
private fun formatQuotaFigure(value: Double): String =
    if (value >= CompactFigureThreshold) {
        MetricCalculators.formatCompactNumber(value.toLong())
    } else {
        formatQuotaNumber(value)
    }

/** Past this a raw figure stops fitting a single row line. */
private const val CompactFigureThreshold = 10_000.0

private fun formatQuotaNumber(value: Double): String =
    if (value == value.toLong().toDouble()) {
        value.toLong().toString()
    } else {
        String.format(Locale.US, "%.1f", value)
    }
