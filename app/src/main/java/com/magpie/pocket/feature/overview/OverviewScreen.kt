package com.magpie.pocket.feature.overview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.magpie.pocket.core.design.DataPanel
import com.magpie.pocket.core.design.MagpieDimensions
import com.magpie.pocket.core.design.MagpieTheme
import com.magpie.pocket.core.design.MagpieTypography
import com.magpie.pocket.core.model.AccountQuota
import com.magpie.pocket.core.model.ConnectionHealth
import com.magpie.pocket.core.model.ConnectionStatus
import com.magpie.pocket.core.model.OverviewSnapshot
import com.magpie.pocket.core.model.RequestSummary
import com.magpie.pocket.core.model.TraceSnapshot
import com.magpie.pocket.core.util.MetricCalculators
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Upstream day buckets may arrive as ISO dates; only a known ISO shape is shortened. */
private val IsoDayPattern = Regex("(\\d{4})-(\\d{2})-(\\d{2})")

private fun dayAxisLabel(label: String): String {
    val match = IsoDayPattern.find(label) ?: return label
    return match.groupValues[2] + "/" + match.groupValues[3]
}

/**
 * Overview always reports today. Usage owns its own period switch, so a period chosen on
 * the usage tab must never relabel 7-day numbers as "today".
 */
@Composable
fun OverviewScreen(
    overview: OverviewSnapshot?,
    trace: TraceSnapshot?,
    runningRequest: RequestSummary?,
    quotas: List<AccountQuota>,
    isLoading: Boolean,
    isOnline: Boolean,
    connectionStatus: ConnectionStatus,
    isDemo: Boolean,
    onNavigateToRouting: (String?) -> Unit,
    onNavigateToErrors: () -> Unit,
    onNavigateToQuotas: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val colors = MagpieTheme.colors
    val configuration = LocalConfiguration.current
    // Compact layout starts below 340dp, while the page gutter only narrows below 360dp:
    // a 320dp phone keeps 16dp of margin and 340-359dp still affords 20dp.
    val narrowWidth = configuration.screenWidthDp < 340
    val compact = narrowWidth || configuration.fontScale > 1.15f
    val stackedMetrics = configuration.fontScale >= 1.45f
    val pagePadding = if (configuration.screenWidthDp < 360) {
        MagpieDimensions.CompactPagePadding
    } else {
        MagpieDimensions.PagePadding
    }
    val cellGap = if (compact) 12.dp else 16.dp
    val date = SimpleDateFormat("M 月 d 日 · EEEE", Locale.CHINESE).format(Date())

    val successRate = overview?.let { MetricCalculators.calculateSuccessRate(it.completedRequests, it.finalErrors) }
    val ttftMeanMs = overview?.let { MetricCalculators.calculateTtftMean(it.ttftSumMs, it.ttftSampleCount) }
    val decodeRate = overview?.let { MetricCalculators.decodeTokensPerSecond(it.decodeOut, it.decodeMs) }
    val pricedCost = overview?.cost
    val alertQuota = quotas.firstOrNull { quota ->
        quota.windows.any { MetricCalculators.isQuotaWarning(it) }
    }
    val yesterday = overview?.sameTimeYesterdayCompletedRequests
    val delta = if (yesterday != null && yesterday > 0L && overview != null) {
        (overview.completedRequests - yesterday).toDouble() / yesterday.toDouble()
    } else null

    val hourlyPoints = overview?.hourlyTrends.orEmpty()
    val dailyPoints = overview?.dailyTrends.orEmpty()
    val hasHourlyTrend = hourlyPoints.isNotEmpty()
    // One point is not a trend, and a chip that cannot change the chart must not appear.
    val hasDailyTrend = dailyPoints.size >= 2
    var dailyRequested by rememberSaveable { mutableStateOf(false) }
    val showDailyTrend = hasDailyTrend && (dailyRequested || !hasHourlyTrend)
    val trendValues = if (showDailyTrend) {
        dailyPoints.map { it.completedRequests }
    } else {
        hourlyPoints.map { it.completedRequests }
    }
    val trendPeak = trendValues.maxOrNull() ?: 0L

    val completedCount = overview?.completedRequests
    val finalErrorsCount = overview?.finalErrors ?: 0L
    val ttftSamples = overview?.ttftSampleCount
    val unpricedCount = overview?.unpriced ?: 0L

    val successDetail = when {
        completedCount == null -> if (isLoading) "同步中" else "暂不可用"
        completedCount > 0L -> "${finalErrorsCount} 次最终错误"
        else -> "暂无完成请求"
    }
    val ttftDetail = when {
        ttftSamples == null -> if (isLoading) "同步中" else "暂不可用"
        ttftSamples > 0L -> "${ttftSamples} 个流式样本"
        else -> "暂无流式样本"
    }
    val decodeValue = if (decodeRate == null) "—" else String.format(Locale.US, "%.1f", decodeRate)
    val decodeDetail = when {
        overview == null -> if (isLoading) "同步中" else "暂不可用"
        decodeRate == null -> "样本不足"
        else -> "总输出 ÷ 生成时长"
    }
    val costValue = MetricCalculators.formatCost(pricedCost)
    val costDetail = when {
        overview == null -> if (isLoading) "同步中" else "暂不可用"
        pricedCost == null -> "未提供计价"
        pricedCost == 0.0 -> "本期无计费调用"
        unpricedCount > 0L -> "${unpricedCount} 次未计价"
        else -> "已计价调用"
    }
    // The pill reports the real connection instead of a fixed green: demo data, a dropped
    // link and a socket still reconnecting each get their own tone and label.
    val connectionPill = when {
        isDemo -> Triple("演示", colors.accent, colors.accentSurface)
        isLoading && overview == null -> Triple("同步中", colors.textSecondary, colors.surfaceSubtle)
        !isOnline -> Triple("离线", colors.warning, colors.warningSurface)
        connectionStatus.health == ConnectionHealth.CONNECTED ->
            Triple("已连接", colors.success, colors.successSurface)
        else -> Triple(
            connectionHealthLabel(connectionStatus.health),
            colors.warning,
            colors.warningSurface
        )
    }

    val metricValueStyle: TextStyle = if (compact) {
        MagpieTypography.SectionTitleStyle
    } else {
        MagpieTypography.InlineMetricStyle
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = pagePadding),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .semantics { heading() }
                ) {
                    Text(
                        text = "一切，尽在掌握",
                        style = MagpieTypography.PageTitleStyle,
                        color = colors.textPrimary
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(text = date, fontSize = 13.sp, color = colors.textSecondary,
                    lineHeight = 18.sp
                    )
                }
                StatusPill(
                    label = connectionPill.first,
                    color = connectionPill.second,
                    surface = connectionPill.third
                )
            }
        }

        item { Spacer(modifier = Modifier.height(18.dp)) }

        item {
            DataPanel(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(MagpieDimensions.RadiusCard),
                contentPadding = PaddingValues(18.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("今日请求", fontSize = 12.sp, color = colors.textSecondary,
                        lineHeight = 16.sp
                        )
                        Text(
                            text = overview?.rangeEnd?.take(16)?.replace('T', ' ')?.takeLast(5)?.let { "截至 $it" } ?: "今日",
                            fontSize = 11.sp,
                            color = colors.textSecondary
                        )
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = overview?.completedRequests?.toString() ?: "—",
                            fontSize = MagpieTypography.HeroMetric,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "次",
                            fontSize = 13.sp,
                            color = colors.textSecondary,
                            modifier = Modifier.padding(bottom = 5.dp)
                        )
                        if (isDemo) {
                            Spacer(modifier = Modifier.width(8.dp))
                            DemoBadge(modifier = Modifier.padding(bottom = 5.dp))
                        }
                    }
                    if (delta != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        StatusPill(
                            label = "${if (delta >= 0) "↑" else "↓"} ${MetricCalculators.formatPercent(kotlin.math.abs(delta))} 较昨日同期",
                            color = colors.success,
                            surface = colors.successSurface
                        )
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("昨日同期数据不可用", fontSize = 11.sp, color = colors.textSecondary,
                        lineHeight = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (showDailyTrend) "近 14 天" else "今日 24 小时",
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = colors.textSecondary,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (hasHourlyTrend) {
                                TrendModeChip(
                                    label = "小时",
                                    selected = !showDailyTrend,
                                    onClick = { dailyRequested = false }
                                )
                            }
                            if (hasDailyTrend) {
                                TrendModeChip(
                                    label = "日",
                                    selected = showDailyTrend,
                                    onClick = { dailyRequested = true }
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    if (trendValues.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(96.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("暂无趋势数据", fontSize = 11.sp, lineHeight = 16.sp, color = colors.textSecondary)
                        }
                    } else {
                        OverviewTrendChart(
                            values = trendValues,
                            description = if (showDailyTrend) {
                                "近 14 天每日完成请求趋势，峰值 $trendPeak"
                            } else {
                                "今日每小时完成请求趋势，峰值 $trendPeak"
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(96.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val axisLabels = if (showDailyTrend) {
                                listOf(
                                    dayAxisLabel(dailyPoints.first().dayLabel),
                                    dayAxisLabel(dailyPoints[dailyPoints.size / 2].dayLabel),
                                    dayAxisLabel(dailyPoints.last().dayLabel)
                                )
                            } else {
                                listOf("00:00", "12:00", "23:00")
                            }
                            axisLabels.forEach { label ->
                                Text(label, fontSize = 11.sp, lineHeight = 16.sp, color = colors.textTertiary)
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
                shape = RoundedCornerShape(MagpieDimensions.RadiusCard),
                contentPadding = PaddingValues(18.dp)
            ) {
                Column {
                    if (stackedMetrics) {
                        // Large font: one column, dividers stay horizontal so nothing is squeezed.
                        PerformanceCell(
                            label = "请求成功率",
                            value = MetricCalculators.formatPercent(successRate),
                            detail = successDetail,
                            valueStyle = metricValueStyle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp)
                        )
                        HorizontalDivider(thickness = 0.75.dp, color = colors.divider)
                        PerformanceCell(
                            label = "首响均值",
                            value = MetricCalculators.formatDurationMs(ttftMeanMs?.toLong()),
                            detail = ttftDetail,
                            valueStyle = metricValueStyle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp)
                        )
                        HorizontalDivider(thickness = 0.75.dp, color = colors.divider)
                        PerformanceCell(
                            label = "解码速度",
                            value = decodeValue,
                            unit = if (decodeRate == null) null else "tok/s",
                            detail = decodeDetail,
                            valueStyle = metricValueStyle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp)
                        )
                        HorizontalDivider(thickness = 0.75.dp, color = colors.divider)
                        PerformanceCell(
                            label = "今日费用",
                            value = costValue,
                            detail = costDetail,
                            valueStyle = metricValueStyle,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp)
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                        ) {
                            PerformanceCell(
                                label = "请求成功率",
                                value = MetricCalculators.formatPercent(successRate),
                                detail = successDetail,
                                valueStyle = metricValueStyle,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(end = cellGap / 2, top = 2.dp, bottom = 10.dp)
                            )
                            CellDividerVertical()
                            PerformanceCell(
                                label = "首响均值",
                                value = MetricCalculators.formatDurationMs(ttftMeanMs?.toLong()),
                                detail = ttftDetail,
                                valueStyle = metricValueStyle,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(start = cellGap / 2, top = 2.dp, bottom = 10.dp)
                            )
                        }
                        HorizontalDivider(thickness = 0.75.dp, color = colors.divider)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                        ) {
                            PerformanceCell(
                                label = "解码速度",
                                value = decodeValue,
                                unit = if (decodeRate == null) null else "tok/s",
                                detail = decodeDetail,
                                valueStyle = metricValueStyle,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(end = cellGap / 2, top = 10.dp, bottom = 4.dp)
                            )
                            CellDividerVertical()
                            PerformanceCell(
                                label = "今日费用",
                                value = costValue,
                                detail = costDetail,
                                valueStyle = metricValueStyle,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(start = cellGap / 2, top = 10.dp, bottom = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(thickness = 0.75.dp, color = colors.divider)
                    Text(
                        text = "解码速度 = 总输出 token ÷ 总生成时长；费用只统计已计价调用。",
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        if (alertQuota != null) {
            item { SectionLabel("需要留意", "额度") }
            item {
                val window = alertQuota.windows.first { MetricCalculators.isQuotaWarning(it) }
                PlainRow(
                    icon = Icons.Filled.HourglassBottom,
                    title = MetricCalculators.maskAccountName(alertQuota.accountId, true),
                    subtitle = "额度已用 ${MetricCalculators.formatPercent(MetricCalculators.calculateQuotaUsedRatio(window))}，建议关注可用状态",
                    onClick = onNavigateToQuotas
                )
            }
        }

        item { SectionLabel("当前活动", if (trace?.runningRouteIds?.isNotEmpty() == true) "${trace.runningRouteIds.size} 个请求" else "") }
        item {
            if (runningRequest != null) {
                PlainRow(
                    icon = Icons.Filled.Terminal,
                    title = runningRequest.servedModel ?: runningRequest.requestedModel ?: "请求处理中",
                    subtitle = listOfNotNull(
                        runningRequest.agentId,
                        runningRequest.groupId,
                        runningRequest.providerId
                    ).joinToString("、").ifBlank { "等待更多路由信息" },
                    onClick = { onNavigateToRouting(runningRequest.key.routeId) },
                    trailing = {
                        StatusPill("回答中", colors.accent, colors.accentSurface)
                    }
                )
            } else {
                PlainRow(
                    icon = Icons.Filled.Terminal,
                    title = "当前没有处理中的请求",
                    subtitle = "新的调用会出现在这里",
                    onClick = null
                )
            }
        }

        item {
            HorizontalDivider(
                modifier = Modifier.padding(top = 8.dp),
                color = colors.divider
            )
            Text(
                text = "成功含改道后成功；改道与最终错误分开计数。",
                fontSize = 11.sp,
                color = colors.textSecondary,
                modifier = Modifier.padding(vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun CellDividerVertical() {
    val colors = MagpieTheme.colors
    Box(
        modifier = Modifier
            .width(0.75.dp)
            .fillMaxHeight()
            .background(colors.divider)
    )
}

/** One of the four performance cells: label, value (with optional unit), and a plain caveat. */
@Composable
private fun PerformanceCell(
    label: String,
    value: String,
    detail: String,
    valueStyle: TextStyle,
    modifier: Modifier = Modifier,
    unit: String? = null
) {
    val colors = MagpieTheme.colors
    Column(
        modifier = modifier
            .heightIn(min = 72.dp)
            .semantics(mergeDescendants = true) { heading() },
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(label, fontSize = 12.sp, lineHeight = 16.sp, color = colors.textSecondary)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                style = valueStyle,
                color = colors.textPrimary
            )
            if (unit != null) {
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = unit,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
        }
        Text(detail, fontSize = 11.sp, lineHeight = 16.sp, color = colors.textSecondary)
    }
}

@Composable
private fun TrendModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MagpieTheme.colors
    Box(
        modifier = Modifier
            .height(MagpieDimensions.TouchTargetMinimum)
            .widthIn(min = MagpieDimensions.TouchTargetMinimum)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .height(34.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(if (selected) colors.accentSurface else colors.surface)
                .padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = if (selected) colors.accent else colors.textSecondary
            )
        }
    }
}

@Composable
private fun SectionLabel(label: String, trailing: String) {
    val colors = MagpieTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary,
        lineHeight = 16.sp
        )
        if (trailing.isNotBlank()) Text(trailing, fontSize = 11.sp, color = colors.textSecondary,
        lineHeight = 16.sp
        )
    }
}

@Composable
private fun PlainRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
    trailing: (@Composable () -> Unit)? = null
) {
    val colors = MagpieTheme.colors
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.accentSurface),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (trailing != null) {
                trailing()
            } else if (onClick != null) {
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(15.dp))
            }
        }
        HorizontalDivider(color = colors.divider)
    }
}

@Composable
private fun StatusPill(label: String, color: Color, surface: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(surface)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(5.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color, maxLines = 1,
        lineHeight = 14.sp
        )
    }
}

/** Provenance label for demo data: the numbers look plausible but are not real. */
@Composable
private fun DemoBadge(modifier: Modifier = Modifier) {
    val colors = MagpieTheme.colors
    Text(
        text = "演示",
        fontSize = 11.sp,
        lineHeight = 14.sp,
        color = colors.accent,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.accentSurface)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

private fun connectionHealthLabel(health: ConnectionHealth): String = when (health) {
    ConnectionHealth.CONNECTED -> "已连接"
    ConnectionHealth.CONNECTING -> "重连中"
    ConnectionHealth.AUTH_REQUIRED -> "需要登录"
    ConnectionHealth.ACCESS_DENIED -> "访问被拒绝"
    ConnectionHealth.NETWORK_UNREACHABLE -> "网络不可达"
    ConnectionHealth.INCOMPATIBLE -> "响应不兼容"
    ConnectionHealth.OFFLINE -> "离线"
}

/**
 * One shared canvas for both trend modes. The caller owns which series is shown, so the
 * line, fill and axis treatment never fork into two chart implementations.
 */
@Composable
private fun OverviewTrendChart(
    values: List<Long>,
    description: String,
    modifier: Modifier = Modifier
) {
    val colors = MagpieTheme.colors
    if (values.isEmpty()) return
    val maxValue = values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    Canvas(modifier = modifier.semantics { contentDescription = description }) {
        val left = 24.dp.toPx()
        val right = size.width
        val top = 4.dp.toPx()
        val bottom = size.height - 4.dp.toPx()
        val axisColor = colors.divider
        drawLine(axisColor, Offset(left, top), Offset(left, bottom), 1.dp.toPx())
        drawLine(axisColor, Offset(left, bottom), Offset(right, bottom), 1.dp.toPx())
        for (i in 0..2) {
            val y = top + (bottom - top) * i / 2f
            drawLine(axisColor.copy(alpha = 0.45f), Offset(left, y), Offset(right, y), 0.5.dp.toPx())
        }
        val stepX = if (values.size > 1) (right - left) / (values.size - 1) else 0f
        val path = Path()
        val fill = Path()
        values.forEachIndexed { index, value ->
            val x = left + stepX * index
            val y = bottom - (bottom - top) * (value.toFloat() / maxValue.toFloat())
            if (index == 0) {
                path.moveTo(x, y)
                fill.moveTo(x, bottom)
                fill.lineTo(x, y)
            } else {
                path.lineTo(x, y)
                fill.lineTo(x, y)
            }
            if (index == values.lastIndex) {
                fill.lineTo(x, bottom)
                fill.close()
            }
        }
        drawPath(
            path = fill,
            brush = Brush.verticalGradient(
                listOf(colors.accent.copy(alpha = 0.16f), colors.accent.copy(alpha = 0.02f)),
                startY = top,
                endY = bottom
            )
        )
        drawPath(path, color = colors.accent.copy(alpha = 0.82f), style = Stroke(1.7.dp.toPx(), cap = StrokeCap.Round))
    }
}
