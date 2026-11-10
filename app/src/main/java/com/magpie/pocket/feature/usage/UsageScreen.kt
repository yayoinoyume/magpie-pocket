package com.magpie.pocket.feature.usage

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.magpie.pocket.core.design.DataPanel
import com.magpie.pocket.core.design.GlassSurface
import com.magpie.pocket.core.design.MagpieDimensions
import com.magpie.pocket.core.design.MagpieTheme
import com.magpie.pocket.core.design.MagpieTypography
import com.magpie.pocket.core.design.springPress
import com.magpie.pocket.core.model.AccountQuota
import com.magpie.pocket.core.model.Period
import com.magpie.pocket.core.model.QuotaWindow
import com.magpie.pocket.core.model.UsageBreakdownItem
import com.magpie.pocket.core.model.UsageSnapshot
import com.magpie.pocket.core.util.MetricCalculators
import kotlin.math.roundToInt

/**
 * Usage page: period -> cost/token -> six-dimension breakdown -> quota health.
 * Catalog, privacy and display entries live on the More tab, so this page stays statistical.
 * The period only drives usage data; quotas are a snapshot of now.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun UsageScreen(
    usage: UsageSnapshot?,
    quotas: List<AccountQuota>,
    selectedPeriod: Period,
    onPeriodChange: (Period) -> Unit,
    isLoading: Boolean,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    isDemo: Boolean = false
) {
    val colors = MagpieTheme.colors
    val preferences = MagpieTheme.preferences
    var selectedQuotaDetail by remember { mutableStateOf<AccountQuota?>(null) }
    var dimensionKey by rememberSaveable { mutableStateOf(UsageDimension.MODELS.name) }
    var breakdownExpanded by rememberSaveable { mutableStateOf(false) }
    var accountsExpanded by rememberSaveable { mutableStateOf(false) }

    val dimension = UsageDimension.values().firstOrNull { it.name == dimensionKey } ?: UsageDimension.MODELS
    val totalTokens = MetricCalculators.calculateTotalTokens(usage?.tokens)
    val cacheHitRate = MetricCalculators.calculateCacheHitRate(usage?.tokens)
    val credits = usage?.credits
    val dimensionData = breakdownFor(usage, dimension, preferences.maskAccountNames)

    val allWindows = quotas.flatMap { it.windows }
    val tightest = quotas.mapNotNull { quota ->
        quota.windows.mapNotNull { window ->
            val ratio = MetricCalculators.calculateQuotaUsedRatio(window) ?: return@mapNotNull null
            TightestQuota(ratio = ratio, windowId = window.id, accountId = quota.accountId)
        }.maxByOrNull { it.ratio }
    }.maxByOrNull { it.ratio }
    val allUnlimited = allWindows.isNotEmpty() && allWindows.all { it.unlimited }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = MagpieDimensions.PagePadding),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "用量",
                        fontSize = MagpieTypography.PageTitle,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    if (isDemo) {
                        Spacer(modifier = Modifier.width(8.dp))
                        DemoBadge()
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    "费用、Token 与额度",
                    fontSize = 13.sp,
                    color = colors.textSecondary,
                    lineHeight = 18.sp
                )
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        item {
            GlassSurface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(MagpieDimensions.RadiusSelector)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    listOf(
                        Period.TODAY to "今日",
                        Period.SEVEN_DAYS to "7 天",
                        Period.THIRTY_DAYS to "30 天",
                        Period.ALL to "全部"
                    ).forEach { (period, label) ->
                        val selected = period == selectedPeriod
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize()
                                .springPress(scaleDown = 0.98f) { onPeriodChange(period) },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(MagpieDimensions.RadiusItem))
                                    .background(if (selected) colors.surface else Color.Transparent),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selected) colors.textPrimary else colors.textSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        item {
            DataPanel(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(MagpieDimensions.RadiusDataPanel),
                contentPadding = PaddingValues(18.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("本期费用", fontSize = 12.sp, color = colors.textSecondary)
                        Text("币种未标注", fontSize = 11.sp, color = colors.textSecondary)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (usage == null) "—" else MetricCalculators.formatCost(usage.cost),
                            fontSize = MagpieTypography.HeroMetric,
                            lineHeight = 40.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        if (isDemo) DemoBadge()
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = costHint(usage, isLoading),
                        fontSize = 11.sp,
                        color = colors.textSecondary,
                        lineHeight = 16.sp
                    )
                    if (credits != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "信用余额 " + MetricCalculators.formatCredits(credits),
                            fontSize = 11.sp,
                            color = colors.textSecondary,
                            lineHeight = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = colors.divider)
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Token", fontSize = 12.sp, color = colors.textSecondary)
                        Text("含缓存读取", fontSize = 11.sp, color = colors.textSecondary)
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = if (usage == null) "—" else MetricCalculators.formatCompactNumber(totalTokens),
                            fontSize = MagpieTypography.InlineMetric,
                            lineHeight = 28.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            "token",
                            fontSize = 13.sp,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    TokenCompositionBar(usage)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "缓存命中率 " + MetricCalculators.formatPercent(cacheHitRate),
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                        Text("按 Token 加权", fontSize = 11.sp, color = colors.textSecondary)
                    }
                }
            }
        }

        item { SectionLabel("明细", dimensionData.sortLabel) }

        item {
            DimensionChips(
                selected = dimension,
                onSelect = { next ->
                    if (next != dimension) {
                        dimensionKey = next.name
                        breakdownExpanded = false
                    }
                }
            )
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        item {
            if (dimensionData.rows.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (usage == null) "正在同步用量数据" else "此维度暂无数据",
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                    if (usage != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "本期没有可归因调用；此连接可能不返回该字段",
                            fontSize = 11.sp,
                            color = colors.textTertiary,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    val visible = if (breakdownExpanded) dimensionData.rows else dimensionData.rows.take(8)
                    visible.forEach { row -> RankRowView(row) }
                }
            }
        }

        if (dimensionData.rows.size > 8) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(MagpieDimensions.TouchTargetMinimum)
                        .springPress(scaleDown = 0.98f) { breakdownExpanded = !breakdownExpanded },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (breakdownExpanded) "收起" else "查看全部 " + dimensionData.rows.size + " 项",
                        fontSize = 12.sp,
                        color = colors.accent
                    )
                }
            }
        }

        item { SectionLabel("额度健康", "当前快照") }

        item {
            DataPanel(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(MagpieDimensions.RadiusDataPanel),
                contentPadding = PaddingValues(18.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = MagpieDimensions.TouchTargetMinimum)
                            .springPress(enabled = quotas.size > 4) {
                                accountsExpanded = !accountsExpanded
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (tightest != null) {
                            QuotaHealthRing(ratio = tightest.ratio, color = quotaRatioColor(tightest.ratio))
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.accentSurface),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.Key,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    tightest != null -> "额度健康"
                                    quotas.isEmpty() -> "暂无额度数据"
                                    allUnlimited -> "全部账号无限额"
                                    else -> "额度不可用"
                                },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.textPrimary,
                                lineHeight = 20.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = when {
                                    tightest != null -> "最紧：" + tightest.windowId
                                    quotas.isEmpty() -> "未配置或暂无可查询的额度窗口"
                                    allUnlimited -> "供应商未返回额度窗口"
                                    else -> "供应商未提供额度"
                                },
                                fontSize = 11.sp,
                                color = colors.textSecondary,
                                lineHeight = 16.sp
                            )
                            if (tightest != null) {
                                Text(
                                    text = MetricCalculators.maskAccountName(
                                        tightest.accountId,
                                        preferences.maskAccountNames
                                    ),
                                    fontSize = 11.sp,
                                    color = colors.textSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                        if (quotas.size > 4) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Filled.ExpandMore,
                                contentDescription = if (accountsExpanded) "收起账号额度" else "展开账号额度",
                                tint = colors.textTertiary,
                                modifier = Modifier
                                    .size(20.dp)
                                    .rotate(if (accountsExpanded) 180f else 0f)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "额度为当前快照，不随周期变化；85% 起提示，100% 为超限。",
                        fontSize = 11.sp,
                        color = colors.textTertiary,
                        lineHeight = 16.sp
                    )
                    if (quotas.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = colors.divider)
                        val shown = if (accountsExpanded) quotas else quotas.take(4)
                        shown.forEach { quota ->
                            QuotaRow(
                                quota = quota,
                                maskedAccount = MetricCalculators.maskAccountName(
                                    quota.accountId,
                                    preferences.maskAccountNames
                                ),
                                onClick = { selectedQuotaDetail = quota }
                            )
                        }
                        if (quotas.size > 4) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(MagpieDimensions.TouchTargetMinimum)
                                    .springPress(scaleDown = 0.98f) {
                                        accountsExpanded = !accountsExpanded
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (accountsExpanded) "收起" else "展开全部 " + quotas.size + " 个账号",
                                    fontSize = 12.sp,
                                    color = colors.accent
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }
    }

    if (selectedQuotaDetail != null) {
        val quota = selectedQuotaDetail!!
        ModalBottomSheet(
            onDismissRequest = { selectedQuotaDetail = null },
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
                    Column {
                        Text(
                            "额度窗口详情",
                            fontSize = 21.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            lineHeight = 26.sp
                        )
                        Text(
                            MetricCalculators.maskAccountName(
                                quota.accountId,
                                preferences.maskAccountNames
                            ),
                            fontSize = 12.sp,
                            color = colors.textSecondary
                        )
                    }
                    IconButton(
                        onClick = { selectedQuotaDetail = null },
                        modifier = Modifier.size(MagpieDimensions.TouchTargetMinimum)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "关闭", tint = colors.textSecondary)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (quota.windows.isEmpty()) {
                    Text(
                        "供应商未返回窗口明细",
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        lineHeight = 16.sp
                    )
                }
                quota.windows.forEach { window ->
                    val ratio = MetricCalculators.calculateQuotaUsedRatio(window)
                    Column(modifier = Modifier.padding(vertical = 9.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                window.id,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = colors.textPrimary,
                                lineHeight = 16.sp
                            )
                            Text(
                                quotaWindowText(window, ratio),
                                fontSize = 12.sp,
                                color = colors.textSecondary
                            )
                        }
                        if (window.resetsAt != null) {
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                "重置 " + window.resetsAt,
                                fontSize = 11.sp,
                                color = colors.textSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                    HorizontalDivider(color = colors.divider)
                }
            }
        }
    }
}

private enum class UsageDimension(val label: String) {
    MODELS("模型"),
    AGENTS("智能体"),
    SESSIONS("会话"),
    ACCOUNTS("账号"),
    CALLERS("调用方"),
    KEYS("密钥")
}

private data class TightestQuota(
    val ratio: Double,
    val windowId: String,
    val accountId: String
)

private data class RankRow(
    val name: String,
    val main: String,
    val secondary: String?,
    val ratio: Float
)

private data class DimensionData(
    val sortLabel: String,
    val rows: List<RankRow>
)

private fun costHint(usage: UsageSnapshot?, isLoading: Boolean): String {
    if (usage == null) return if (isLoading) "同步中" else "未提供计价"
    val unpriced = usage.unpriced ?: 0L
    return when {
        usage.cost == null && unpriced > 0L -> "未提供计价，" + unpriced + " 次未计价"
        usage.cost == null -> "未提供计价"
        unpriced > 0L -> unpriced.toString() + " 次未计价，未计入费用"
        usage.cost == 0.0 -> "本期无计费调用"
        else -> "已计价累计"
    }
}

@Composable
private fun quotaRatioColor(ratio: Double): Color {
    val colors = MagpieTheme.colors
    return when {
        ratio >= 1.0 -> colors.error
        ratio >= 0.85 -> colors.warning
        else -> colors.accent
    }
}

private fun tokensOf(item: UsageBreakdownItem): Long? {
    val total = (item.input ?: 0L) + (item.output ?: 0L) +
        (item.cacheRead ?: 0L) + (item.cacheWrite ?: 0L)
    return if (total > 0L) total else null
}

private fun breakdownFor(
    usage: UsageSnapshot?,
    dimension: UsageDimension,
    maskAccounts: Boolean
): DimensionData {
    if (usage == null) return DimensionData("同步中", emptyList())

    if (dimension == UsageDimension.MODELS) {
        val items = usage.models.sortedByDescending { it.totalTokens }
        val total = items.sumOf { it.totalTokens }
        val rows = items.map { item ->
            RankRow(
                name = item.modelId,
                main = MetricCalculators.formatCompactNumber(item.totalTokens) + " token",
                secondary = null,
                ratio = if (total > 0L) item.totalTokens.toFloat() / total.toFloat() else 0f
            )
        }
        return DimensionData("按 Token", rows)
    }

    val source = when (dimension) {
        UsageDimension.AGENTS -> usage.agents
        UsageDimension.SESSIONS -> usage.sessions
        UsageDimension.ACCOUNTS -> usage.accounts
        UsageDimension.CALLERS -> usage.callerKeys
        UsageDimension.KEYS -> usage.providerKeys
        UsageDimension.MODELS -> emptyList()
    }

    val anyCalls = source.any { it.calls != null }
    val anyCost = source.any { it.cost != null }
    val sorted = when {
        anyCalls -> source.sortedWith(
            compareByDescending<UsageBreakdownItem> { it.calls ?: -1L }
                .thenBy { it.label ?: it.id.orEmpty() }
        )
        anyCost -> source.sortedWith(
            compareByDescending<UsageBreakdownItem> { it.cost ?: Double.NEGATIVE_INFINITY }
                .thenBy { it.label ?: it.id.orEmpty() }
        )
        else -> source.sortedWith(
            compareByDescending<UsageBreakdownItem> { tokensOf(it) ?: -1L }
                .thenBy { it.label ?: it.id.orEmpty() }
        )
    }

    val totalCost = if (anyCost) sorted.sumOf { it.cost ?: 0.0 } else 0.0
    val totalTokens = sorted.sumOf { tokensOf(it) ?: 0L }
    val totalCalls = sorted.sumOf { it.calls ?: 0L }

    val rows = sorted.map { item ->
        val tokens = tokensOf(item)
        val name = displayBreakdownName(item, dimension, maskAccounts)
        val main = when {
            item.cost != null -> MetricCalculators.formatCost(item.cost)
            tokens != null -> MetricCalculators.formatCompactNumber(tokens) + " token"
            item.calls != null -> item.calls.toString() + " 次"
            else -> "—"
        }
        val secondary = when {
            item.cost != null && tokens != null ->
                MetricCalculators.formatCompactNumber(tokens) + " token"
            item.cost != null && item.calls != null -> item.calls.toString() + " 次请求"
            item.cost == null && tokens != null && item.calls != null -> item.calls.toString() + " 次请求"
            item.cost == null && tokens == null && item.calls != null -> "费用/Token 未提供"
            else -> null
        }
        val ratio = when {
            anyCost && totalCost > 0.0 -> ((item.cost ?: 0.0) / totalCost).toFloat()
            totalTokens > 0L -> (tokens ?: 0L).toFloat() / totalTokens.toFloat()
            totalCalls > 0L -> (item.calls ?: 0L).toFloat() / totalCalls.toFloat()
            else -> 0f
        }
        RankRow(name = name, main = main, secondary = secondary, ratio = ratio)
    }

    val sortLabel = when {
        anyCalls -> "按请求"
        anyCost -> "按费用"
        else -> "按 Token"
    }
    return DimensionData(sortLabel, rows)
}

private fun displayBreakdownName(
    item: UsageBreakdownItem,
    dimension: UsageDimension,
    maskAccounts: Boolean
): String {
    val raw = item.label?.takeIf { it.isNotBlank() }
        ?: item.id?.takeIf { it.isNotBlank() }
        ?: "未命名"
    return when (dimension) {
        UsageDimension.AGENTS -> when (raw) {
            "codex" -> "Codex"
            "claude-code", "claude_code" -> "Claude Code"
            else -> raw
        }
        UsageDimension.SESSIONS -> if (raw.length > 6) "…" + raw.takeLast(6) else raw
        UsageDimension.ACCOUNTS -> MetricCalculators.maskAccountName(raw, maskAccounts)
        UsageDimension.CALLERS -> if (raw.contains("@")) {
            MetricCalculators.maskAccountName(raw, maskAccounts)
        } else {
            raw
        }
        UsageDimension.KEYS -> "…" + raw.takeLast(4)
        UsageDimension.MODELS -> raw
    }
}

@Composable
private fun DemoBadge() {
    val colors = MagpieTheme.colors
    Text(
        text = "演示",
        fontSize = 11.sp,
        color = colors.accent,
        lineHeight = 14.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.accentSurface)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DimensionChips(selected: UsageDimension, onSelect: (UsageDimension) -> Unit) {
    val colors = MagpieTheme.colors
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        UsageDimension.values().forEach { dimension ->
            val isSelected = dimension == selected
            Box(
                modifier = Modifier
                    .height(MagpieDimensions.TouchTargetMinimum)
                    .springPress(scaleDown = 0.98f) { onSelect(dimension) },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .height(34.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(if (isSelected) colors.accentSurface else colors.surface)
                        .border(
                            width = 0.75.dp,
                            color = if (isSelected) colors.accent.copy(alpha = 0.26f) else colors.divider,
                            shape = RoundedCornerShape(17.dp)
                        )
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dimension.label,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected) colors.accent else colors.textSecondary
                    )
                }
            }
        }
    }
}

@Composable
private fun RankRowView(row: RankRow) {
    val colors = MagpieTheme.colors
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Text(
                text = row.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 20.sp,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = row.main,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = colors.textPrimary
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(colors.divider)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(row.ratio.coerceIn(0f, 1f))
                    .fillMaxSize()
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.accent)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = row.secondary.orEmpty(),
                fontSize = 11.sp,
                color = colors.textSecondary
            )
            Text(
                text = MetricCalculators.formatPercent(row.ratio.toDouble()),
                fontSize = 11.sp,
                color = colors.textSecondary
            )
        }
    }
}

@Composable
private fun QuotaHealthRing(ratio: Double, color: Color) {
    val colors = MagpieTheme.colors
    Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 6.dp.toPx()
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)
            drawArc(
                color = colors.divider,
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
                sweepAngle = (360f * ratio.coerceIn(0.0, 1.0)).toFloat(),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        Text(
            text = (ratio * 100.0).roundToInt().toString() + "%",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = colors.textPrimary
        )
    }
}

@Composable
private fun SectionLabel(label: String, trailing: String) {
    val colors = MagpieTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = colors.textPrimary,
            lineHeight = 16.sp
        )
        Text(trailing, fontSize = 11.sp, color = colors.textSecondary, lineHeight = 16.sp)
    }
}

@Composable
private fun TokenCompositionBar(usage: UsageSnapshot?) {
    val colors = MagpieTheme.colors
    val tokens = usage?.tokens
    val values = listOf(
        tokens?.cacheRead ?: 0L,
        tokens?.inputUncached ?: 0L,
        tokens?.output ?: 0L,
        tokens?.cacheWrite ?: 0L
    )
    val total = values.sum()
    val palette = listOf(
        colors.accent,
        colors.accent.copy(alpha = 0.62f),
        colors.accent.copy(alpha = 0.34f),
        colors.textTertiary.copy(alpha = 0.45f)
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
            if (total > 0) {
                values.forEachIndexed { index, value ->
                    if (value > 0) {
                        Box(
                            modifier = Modifier
                                .weight(value.toFloat() / total.toFloat())
                                .fillMaxSize()
                                .background(palette[index])
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(7.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("缓存", "未缓存", "输出", "写入").forEachIndexed { index, label ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(palette[index])
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(label, fontSize = 10.sp, color = colors.textSecondary, lineHeight = 14.sp)
                }
            }
        }
    }
}

private fun quotaWindowText(window: QuotaWindow, ratio: Double?): String {
    if (window.unlimited) return "无限额"
    if (!window.known) return "额度未知"
    val percent = ratio?.let { MetricCalculators.formatPercent(it) } ?: window.display ?: "—"
    val amount = window.amount ?: window.allowanceLimit
    return if (amount != null && window.allowanceLimit != null) {
        formatQuotaNumber(window.amount ?: 0.0) + " / " + formatQuotaNumber(window.allowanceLimit) +
            " " + window.unit + " · " + percent
    } else {
        percent + if (window.unit.isNotBlank()) " " + window.unit else ""
    }
}

private fun formatQuotaNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString()
    else String.format(java.util.Locale.US, "%.1f", value)

@Composable
private fun QuotaRow(quota: AccountQuota, maskedAccount: String, onClick: () -> Unit) {
    val colors = MagpieTheme.colors
    val warningWindow = quota.windows.firstOrNull { MetricCalculators.isQuotaWarning(it) }
    val primary = quota.windows.firstOrNull()
    val primaryRatio = primary?.let { MetricCalculators.calculateQuotaUsedRatio(it) }
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
                Text(
                    maskedAccount,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary,
                    lineHeight = 18.sp
                )
                Text(
                    text = when {
                        !quota.known -> "供应商未提供额度"
                        primary == null -> "暂无额度窗口"
                        else -> "已用 " + MetricCalculators.formatPercent(primaryRatio) +
                            (primary.resetsAt?.let { " · " + it + " 重置" } ?: "")
                    },
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    lineHeight = 16.sp
                )
            }
            if (!quota.known) {
                Text("未知", fontSize = 11.sp, color = colors.textSecondary)
            } else if (warningWindow != null) {
                Text(
                    text = "额度偏低",
                    fontSize = 10.sp,
                    color = colors.warning,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.warningSurface)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            } else if (primaryRatio != null) {
                Text(
                    text = MetricCalculators.formatPercent(primaryRatio),
                    fontSize = 13.sp,
                    color = colors.textPrimary
                )
            }
        }
        if (primaryRatio != null) {
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
                        .fillMaxWidth(primaryRatio.coerceIn(0.0, 1.0).toFloat())
                        .fillMaxSize()
                        .clip(RoundedCornerShape(4.dp))
                        .background(quotaRatioColor(primaryRatio))
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = colors.divider)
    }
}
