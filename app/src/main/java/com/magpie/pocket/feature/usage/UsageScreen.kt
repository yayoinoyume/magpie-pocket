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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
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
import androidx.compose.ui.unit.Dp
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
import com.magpie.pocket.core.design.UnderlineTabs
import com.magpie.pocket.core.design.springPress
import com.magpie.pocket.app.QuotaOverview
import com.magpie.pocket.app.QuotaProviderCard
import com.magpie.pocket.app.QuotaWindowRow
import com.magpie.pocket.app.defaultExpandedQuotaAccounts
import com.magpie.pocket.app.defaultQuotaAccountKey
import com.magpie.pocket.app.formatQuotaPercent
import com.magpie.pocket.app.quotaAccountKey
import com.magpie.pocket.app.quotaOverview
import com.magpie.pocket.app.tightestWindow
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

/** The two sub-pages of the usage page; the capsule under the title switches between them. */
enum class UsageTab { USAGE, QUOTA }

private val usageTabOptions = listOf("用量", "额度")

private val periodOptions = listOf(
    Period.TODAY to "今日",
    Period.SEVEN_DAYS to "近 7 天",
    Period.THIRTY_DAYS to "近 30 天",
    Period.ALL to "全部"
)

/**
 * The sub-page a deep link asks for: only "quota" names one, and every other value lands on
 * usage, which is the page's own default.
 */
internal fun usageTabFor(section: String?): UsageTab =
    if (section == "quota") UsageTab.QUOTA else UsageTab.USAGE

/**
 * What the header says the numbers below cover: the selected period and the requests behind it.
 * A period with no buckets yet names itself alone, so a missing answer never reads as "0 次请求".
 */
internal fun usageSubtitle(period: Period, usage: UsageSnapshot?): String {
    val label = periodOptions.firstOrNull { it.first == period }?.second ?: return ""
    val buckets = usage?.dailyTrends.orEmpty()
    if (buckets.isEmpty()) return label
    return label + " · " + buckets.sumOf { it.completedRequests } + " 次请求"
}

/** Quotas are a snapshot of now: only providers that answered with a window are counted. */
internal fun quotaSubtitle(accountQuotas: List<AccountQuotaView>): String {
    val reporting = providersWithWindows(accountQuotas)
    return if (reporting > 0) "实时快照 · " + reporting + " 个供应商" else "实时快照"
}

/** The card dimension counts a provider that only reported a balance, too. */
internal fun quotaCardSubtitle(cards: List<QuotaProviderCard>): String =
    if (cards.isNotEmpty()) "实时快照 · " + cards.size + " 个供应商" else "实时快照"

/** Providers that reported at least one window; a provider without one is counted nowhere. */
private fun providersWithWindows(rows: List<AccountQuotaView>): Int =
    rows.count { it.windows.isNotEmpty() }

/**
 * Usage page in two sub-pages, switched by the capsule under the title.
 *
 * 用量: one period at a time -> spend and tokens -> daily trend -> ranked detail. The period
 * tabs sit above the list, so they stay where they are while the numbers scroll.
 * 额度: a snapshot of now that never follows the selected period.
 *
 * Each sub-page owns its LazyColumn and its offset, so switching never moves the other list.
 * [initialSection] == "quota" opens the quota sub-page directly.
 */
@Composable
fun UsageScreen(
    usage: UsageSnapshot?,
    quotaCards: List<QuotaProviderCard>,
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
    val maskAccountNames = MagpieTheme.preferences.maskAccountNames
    // Both offsets are declared here and always composed: a sub-page that leaves the screen has
    // to keep its own for the trip back.
    val usageListState = rememberLazyListState()
    val quotaListState = rememberLazyListState()
    var tab by rememberSaveable { mutableStateOf(usageTabFor(initialSection)) }

    // Expanded state lives here, not in the card, so a refresh or a reorder cannot close a card.
    var expandedQuotaProviders by rememberSaveable(stateSaver = QuotaStringSetSaver) {
        mutableStateOf(emptySet())
    }
    var expandedQuotaAccounts by rememberSaveable(stateSaver = QuotaStringSetSaver) {
        mutableStateOf(defaultExpandedQuotaAccounts(quotaCards))
    }
    val quotaSummary = remember(quotaCards, quotaDisplayMode) {
        quotaOverview(quotaCards, quotaDisplayMode)
    }
    var quotaDefaultsSeeded by rememberSaveable {
        mutableStateOf(quotaCards.any { it.accounts.isNotEmpty() })
    }
    LaunchedEffect(quotaCards) {
        if (!quotaDefaultsSeeded && quotaCards.any { it.accounts.isNotEmpty() }) {
            expandedQuotaAccounts = defaultExpandedQuotaAccounts(quotaCards)
            quotaDefaultsSeeded = true
        }
    }
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

    val cacheHitRate = MetricCalculators.formatPercent(
        MetricCalculators.calculateCacheHitRate(usage?.tokens)
    )

    // The anchor opens the quota sub-page once, then reports itself consumed.
    LaunchedEffect(initialSection) {
        if (usageTabFor(initialSection) == UsageTab.QUOTA) {
            tab = UsageTab.QUOTA
            onSectionConsumed()
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = contentPadding.calculateTopPadding())
        ) {
            MagpiePageHeader(
                title = "用量",
                subtitle = when (tab) {
                    UsageTab.USAGE -> usageSubtitle(selectedPeriod, usage)
                    UsageTab.QUOTA -> quotaCardSubtitle(quotaCards)
                },
                actions = {
                    if (isDemo) DemoBadge()
                }
            )
            SegmentedControl(
                options = usageTabOptions,
                selectedIndex = tab.ordinal,
                onSelect = { index ->
                    UsageTab.values().getOrNull(index)?.let { tab = it }
                },
                modifier = Modifier.padding(horizontal = MagpieDimensions.PagePadding)
            )
            if (tab == UsageTab.USAGE) {
                UnderlineTabs(
                    options = periodOptions.map { it.second },
                    selectedIndex = periodOptions.indexOfFirst { it.first == selectedPeriod },
                    onSelect = { index ->
                        periodOptions.getOrNull(index)?.first?.let(onPeriodChange)
                    },
                    modifier = Modifier
                        .padding(horizontal = MagpieDimensions.PagePadding)
                        .padding(top = 14.dp)
                )
            }
        }

        // Whatever stays fixed over a list ends 14dp above it, and the list keeps the page's
        // bottom inset so the floating navigation never covers a last row.
        val listPadding = PaddingValues(
            top = 14.dp,
            bottom = contentPadding.calculateBottomPadding()
        )

        when (tab) {
            UsageTab.USAGE -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = usageListState,
                contentPadding = listPadding
            ) {
                item {
                    CostCard(
                        usage = usage,
                        usageCost = usageCost,
                        period = selectedPeriod,
                        modifier = Modifier.padding(horizontal = MagpieDimensions.PagePadding)
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
            }

            UsageTab.QUOTA -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = quotaListState,
                contentPadding = listPadding
            ) {
                item {
                    QuotaOverviewCard(
                        overview = quotaSummary,
                        providerCount = quotaCards.size,
                        modifier = Modifier.padding(horizontal = MagpieDimensions.PagePadding)
                    )
                }

                // No provider card means no switch to explain and nothing to count.
                if (quotaCards.isNotEmpty()) {
                    item {
                        QuotaSupplierHeader(
                            displayMode = quotaDisplayMode,
                            onDisplayModeChange = onQuotaDisplayModeChange,
                            modifier = Modifier
                                .padding(horizontal = MagpieDimensions.PagePadding)
                                .padding(top = 16.dp)
                        )
                    }

                    items(quotaCards.size, key = { quotaCards[it].providerId }) { index ->
                        val card = quotaCards[index]
                        val accountKeys = card.accounts.mapIndexed { accountIndex, account ->
                            quotaAccountKey(card.providerId, account, accountIndex)
                        }.toSet()
                        QuotaProviderCardView(
                            card = card,
                            displayMode = quotaDisplayMode,
                            expanded = card.providerId in expandedQuotaProviders,
                            onToggleExpanded = {
                                expandedQuotaProviders =
                                    expandedQuotaProviders.toggle(card.providerId)
                            },
                            expandedAccounts = expandedQuotaAccounts,
                            onToggleAccount = { key ->
                                expandedQuotaAccounts = expandedQuotaAccounts.toggle(key)
                            },
                            onToggleAllAccounts = {
                                val allExpanded = accountKeys.isNotEmpty() &&
                                    accountKeys.all { it in expandedQuotaAccounts }
                                expandedQuotaAccounts = if (allExpanded) {
                                    setOfNotNull(defaultQuotaAccountKey(card))
                                } else {
                                    expandedQuotaAccounts + accountKeys
                                }
                            },
                            modifier = Modifier
                                .padding(horizontal = MagpieDimensions.PagePadding)
                                .padding(top = 10.dp)
                        )
                    }
                }

                item {
                    QuotaModeNote(
                        displayMode = quotaDisplayMode,
                        modifier = Modifier
                            .padding(horizontal = MagpieDimensions.PagePadding)
                            .padding(top = 14.dp)
                    )
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }
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

private val QuotaStringSetSaver = listSaver<Set<String>, String>(
    save = { it.toList() },
    restore = { it.toSet() }
)

private fun Set<String>.toggle(key: String): Set<String> =
    if (key in this) this - key else this + key

/**
 * The sub-page overview: the average remaining share, how many providers/windows it covers, and
 * the two providers at the ends. A window with no reading never tilts the average.
 */
@Composable
private fun QuotaOverviewCard(
    overview: QuotaOverview,
    providerCount: Int,
    modifier: Modifier = Modifier
) {
    val colors = MagpieTheme.colors
    val average = overview.averageRemaining
    DataPanel(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(18.dp)) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 92.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                QuotaOverviewRing(remaining = average, color = quotaRemainingColor(average))
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "额度总览",
                        style = MagpieTypography.BodyStyle.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = if (overview.windowCount == 0) {
                            "暂无额度窗口"
                        } else {
                            providerCount.toString() + " 个供应商 · " +
                                overview.windowCount + " 个窗口"
                        },
                        style = MagpieTypography.LabelStyle,
                        color = colors.textSecondary
                    )
                    if (overview.windowCount > 0) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (overview.lowCount > 0) {
                                overview.lowCount.toString() + " 项低于 30%"
                            } else {
                                "整体充足"
                            },
                            style = MagpieTypography.LabelStyle,
                            color = colors.textTertiary
                        )
                    }
                }
            }
            val lowest = overview.lowest
            val highest = overview.highest
            if (lowest != null || highest != null) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = colors.divider)
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = lowest?.let {
                            "最低 " + it.first + " " + formatQuotaPercent(it.second)
                        } ?: "",
                        style = MagpieTypography.LabelStyle,
                        color = colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = highest?.let {
                            "最高 " + it.first + " " + formatQuotaPercent(it.second)
                        } ?: "",
                        style = MagpieTypography.LabelStyle,
                        color = colors.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun QuotaOverviewRing(remaining: Double?, color: Color) {
    val colors = MagpieTheme.colors
    val shown = ((remaining ?: 0.0) / 100.0).coerceIn(0.0, 1.0)
    val track = if (remaining != null && remaining < 30.0) {
        colors.error.copy(alpha = 0.18f)
    } else {
        colors.divider
    }
    Box(modifier = Modifier.size(92.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 8.dp.toPx()
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
            if (remaining != null) {
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = (360f * shown).toFloat(),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = formatQuotaPercent(remaining),
                style = MagpieTypography.SubheadStyle.copy(
                    fontSize = 22.sp,
                    lineHeight = 26.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = if (remaining != null) color else colors.textTertiary
            )
            Text(
                text = "平均剩余",
                style = MagpieTypography.LabelStyle.copy(fontSize = 10.sp, lineHeight = 12.sp),
                color = colors.textTertiary
            )
        }
    }
}

/** "全部供应商" and the switch that names how every card under it reads its number. */
@Composable
private fun QuotaSupplierHeader(
    displayMode: QuotaDisplayMode,
    onDisplayModeChange: (QuotaDisplayMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MagpieTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "全部供应商",
            style = MagpieTypography.SectionTitleStyle,
            color = colors.textPrimary,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        QuotaDisplayModeSwitch(mode = displayMode, onChange = onDisplayModeChange)
    }
}

/** One line naming what the numbers mean, under the switch that changes the reading. */
@Composable
private fun QuotaModeNote(displayMode: QuotaDisplayMode, modifier: Modifier = Modifier) {
    val colors = MagpieTheme.colors
    Text(
        text = if (displayMode == QuotaDisplayMode.REMAINING) {
            "数字是剩余比例，越少越危险"
        } else {
            "数字是已用比例，越满越危险"
        },
        style = MagpieTypography.LabelStyle,
        color = colors.textTertiary,
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * One provider card. A single-account provider expands its window list in place; a multi-account
 * provider always shows its merged total and its accounts, with one account open by default.
 */
@Composable
private fun QuotaProviderCardView(
    card: QuotaProviderCard,
    displayMode: QuotaDisplayMode,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    expandedAccounts: Set<String>,
    onToggleAccount: (String) -> Unit,
    onToggleAllAccounts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MagpieTheme.colors
    val allWindows = card.accounts.flatMap { it.windows }
    val tightest = card.tightestWindow(displayMode)
    // An upstream-rendered balance wins the headline even when the provider also answered a
    // window (StepFun does exactly that); the window stays behind the card's own button.
    val balanceHeadline = !card.balance.isNullOrBlank()
    val unlimitedOnly = card.windowCount > 0 && allWindows.all { it.unlimited }
    val textOnly = if (tightest == null) allWindows.firstOrNull { it.text != null } else null
    val multiAccount = card.accounts.size > 1

    val headlineText = when {
        balanceHeadline -> card.balance.orEmpty()
        unlimitedOnly -> "无限额"
        textOnly != null -> textOnly.text ?: "—"
        tightest?.percent != null -> formatQuotaPercent(tightest?.percent)
        else -> "—"
    }
    val hasPercentHeadline = tightest?.percent != null &&
        !balanceHeadline && !unlimitedOnly && textOnly == null
    val headlineCaption = if (hasPercentHeadline) {
        if (displayMode == QuotaDisplayMode.USED) "已用" else "剩余"
    } else {
        null
    }
    val tightestColor = if (tightest != null) {
        quotaIndexColor(tightest.color)
    } else {
        colors.textPrimary
    }
    val headlineColor = when {
        unlimitedOnly -> colors.success
        textOnly != null -> colors.warning
        balanceHeadline || tightest?.percent == null -> colors.textPrimary
        else -> tightestColor
    }

    DataPanel(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = card.displayName,
                        style = MagpieTypography.SubheadStyle.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = colors.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val subtitle = listOfNotNull(
                        card.plan,
                        if (multiAccount) card.accountCount.toString() + " 个账号" else null
                    ).joinToString(" · ")
                    if (subtitle.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MagpieTypography.LabelStyle.copy(fontSize = 11.sp),
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = headlineText,
                        style = MagpieTypography.SubheadStyle.copy(
                            fontSize = 17.sp,
                            lineHeight = 22.sp,
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = headlineColor,
                        maxLines = 1
                    )
                    if (headlineCaption != null) {
                        Text(
                            text = headlineCaption,
                            style = MagpieTypography.LabelStyle.copy(fontSize = 11.sp),
                            color = colors.textTertiary,
                            modifier = Modifier.padding(start = 3.dp, bottom = 2.dp)
                        )
                    }
                }
            }

            val showBar = tightest != null && tightest.hasBar &&
                !balanceHeadline && !unlimitedOnly && textOnly == null
            if (showBar && tightest != null) {
                QuotaBar(
                    percent = tightest.percent,
                    color = quotaIndexColor(tightest.color),
                    height = 4.dp,
                    modifier = Modifier.padding(top = 9.dp)
                )
            }

            val lead = when {
                balanceHeadline -> "余额 · 不按窗口计"
                tightest != null -> listOfNotNull(
                    tightest.name,
                    tightest.countText ?: tightest.shareText ?: tightest.text
                ).joinToString(" · ")
                unlimitedOnly -> "订阅未设上限"
                textOnly != null -> "上游只给文本，没有数字"
                else -> "暂无额度窗口"
            }
            Text(
                text = lead,
                style = MagpieTypography.LabelStyle,
                color = colors.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp)
            )
            val reset = tightest?.resetText
            if (reset != null && !balanceHeadline) {
                Text(
                    text = reset,
                    style = MagpieTypography.LabelStyle.copy(fontSize = 11.sp, lineHeight = 15.sp),
                    color = colors.textTertiary,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }

            if (multiAccount) {
                if (card.mergedWindows.isNotEmpty()) {
                    QuotaMergedBlock(card = card, modifier = Modifier.padding(top = 11.dp))
                }
                card.accounts.forEachIndexed { index, account ->
                    val accountKey = quotaAccountKey(card.providerId, account, index)
                    val accountExpanded = accountKey in expandedAccounts
                    if (index > 0 || card.mergedWindows.isNotEmpty()) {
                        HorizontalDivider(
                            color = colors.divider,
                            modifier = Modifier.padding(top = if (index == 0) 11.dp else 0.dp)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 40.dp)
                            .clickable { onToggleAccount(accountKey) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = account.label,
                            style = MagpieTypography.SubheadStyle,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        account.plan?.takeIf { it != card.plan }?.let { plan ->
                            Text(
                                text = plan,
                                style = MagpieTypography.LabelStyle.copy(fontSize = 10.sp),
                                color = colors.accent,
                                maxLines = 1,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = if (accountExpanded) {
                                Icons.Filled.ExpandLess
                            } else {
                                Icons.Filled.ExpandMore
                            },
                            contentDescription = if (accountExpanded) "收起账号" else "展开账号",
                            tint = colors.textTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    if (accountExpanded) {
                        QuotaWindowList(account.windows)
                    } else {
                        QuotaWindowGrid(
                            windows = account.windows,
                            modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
                        )
                    }
                }
                if (card.accounts.size > 1) {
                    val accountKeys = card.accounts.mapIndexed { index, account ->
                        quotaAccountKey(card.providerId, account, index)
                    }
                    val allExpanded = accountKeys.isNotEmpty() &&
                        accountKeys.all { it in expandedAccounts }
                    HorizontalDivider(
                        color = colors.divider,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    QuotaMoreRow(
                        text = if (allExpanded) {
                            "其他账号收成简略"
                        } else {
                            "展开全部 " + card.accounts.size + " 个账号"
                        },
                        expanded = allExpanded,
                        onClick = onToggleAllAccounts
                    )
                }
            } else {
                if (expanded && card.windowCount > 0) {
                    HorizontalDivider(
                        color = colors.divider,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    QuotaWindowList(card.accounts.firstOrNull()?.windows.orEmpty())
                }
                if (card.windowCount > 0 && !unlimitedOnly && textOnly == null) {
                    HorizontalDivider(
                        color = colors.divider,
                        modifier = Modifier.padding(top = 11.dp)
                    )
                    QuotaMoreRow(
                        text = if (card.windowCount == 1) {
                            "查看 1 个窗口"
                        } else {
                            "另外 " + (card.windowCount - 1) + " 个窗口"
                        },
                        expanded = expanded,
                        onClick = onToggleExpanded
                    )
                }
            }
        }
    }
}

@Composable
private fun QuotaMergedBlock(card: QuotaProviderCard, modifier: Modifier = Modifier) {
    val colors = MagpieTheme.colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.divider.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = card.accountCount.toString() + " 个账号合计",
            style = MagpieTypography.LabelStyle.copy(
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = colors.textTertiary
        )
        Spacer(modifier = Modifier.height(9.dp))
        QuotaWindowGrid(windows = card.mergedWindows)
    }
}

@Composable
private fun QuotaWindowGrid(windows: List<QuotaWindowRow>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        windows.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                pair.forEach { row ->
                    QuotaWindowCell(row = row, modifier = Modifier.weight(1f))
                }
                if (pair.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun QuotaWindowCell(row: QuotaWindowRow, modifier: Modifier = Modifier) {
    val colors = MagpieTheme.colors
    val value = row.countText ?: row.shareText ?: row.text
        ?: if (row.unlimited) "无限额" else "—"
    val valueColor = quotaIndexColor(row.color)
    Column(modifier = modifier) {
        Text(
            text = row.name,
            style = MagpieTypography.LabelStyle.copy(fontSize = 11.sp, lineHeight = 14.sp),
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MagpieTypography.LabelStyle.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            ),
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 1.dp)
        )
        if (row.hasBar) {
            QuotaBar(
                percent = row.percent,
                color = valueColor,
                height = 3.dp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun QuotaWindowList(windows: List<QuotaWindowRow>) {
    val colors = MagpieTheme.colors
    Column(modifier = Modifier.fillMaxWidth()) {
        windows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(color = colors.divider)
            QuotaWindowLine(row)
        }
    }
}

@Composable
private fun QuotaWindowLine(row: QuotaWindowRow) {
    val colors = MagpieTheme.colors
    val value = row.countText ?: row.shareText ?: row.text
        ?: if (row.unlimited) "无限额" else "—"
    val valueColor = quotaIndexColor(row.color)
    Column(modifier = Modifier.padding(vertical = 9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = row.name,
                style = MagpieTypography.SubheadStyle,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = value,
                style = MagpieTypography.SubheadStyle.copy(fontWeight = FontWeight.Medium),
                color = valueColor,
                maxLines = 1
            )
        }
        if (row.hasBar) {
            QuotaBar(
                percent = row.percent,
                color = valueColor,
                height = 4.dp,
                modifier = Modifier.padding(top = 5.dp)
            )
        }
        val detail = listOfNotNull(row.resetText, row.paceText).joinToString(" · ")
        if (detail.isNotEmpty()) {
            Text(
                text = detail,
                style = MagpieTypography.LabelStyle.copy(fontSize = 11.sp, lineHeight = 15.sp),
                color = colors.textTertiary,
                modifier = Modifier.padding(top = 5.dp)
            )
        }
    }
}

@Composable
private fun QuotaMoreRow(
    text: String,
    expanded: Boolean,
    onClick: () -> Unit
) {
    val colors = MagpieTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clickable(onClick = onClick)
            .padding(top = 8.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MagpieTypography.SubheadStyle,
            color = colors.textSecondary,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = text,
            tint = colors.textTertiary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun QuotaBar(
    percent: Double?,
    color: Color,
    height: Dp,
    modifier: Modifier = Modifier
) {
    val colors = MagpieTheme.colors
    val shape = RoundedCornerShape(height)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(colors.divider)
    ) {
        if (percent != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((percent / 100.0).coerceIn(0.0, 1.0).toFloat())
                    .fillMaxSize()
                    .clip(shape)
                    .background(color)
            )
        }
    }
}

@Composable
private fun quotaIndexColor(index: Int): Color {
    val colors = MagpieTheme.colors
    return when (index) {
        0 -> colors.error
        1 -> colors.warning
        else -> colors.accent
    }
}

@Composable
private fun quotaRemainingColor(remaining: Double?): Color {
    val colors = MagpieTheme.colors
    return when {
        remaining == null -> colors.textTertiary
        remaining >= 60.0 -> colors.success
        remaining >= 30.0 -> colors.warning
        else -> colors.error
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
