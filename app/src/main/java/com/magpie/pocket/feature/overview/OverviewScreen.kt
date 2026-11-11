package com.magpie.pocket.feature.overview

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.magpie.pocket.core.design.MagpieDimensions
import com.magpie.pocket.core.design.MagpiePageHeader
import com.magpie.pocket.core.design.MagpieTheme
import com.magpie.pocket.core.design.MagpieTypography
import com.magpie.pocket.core.design.SectionHeader
import com.magpie.pocket.core.design.StatusStrip
import com.magpie.pocket.core.design.WarnPanel
import com.magpie.pocket.core.design.displayOrDash
import com.magpie.pocket.core.design.springPress
import com.magpie.pocket.core.model.ConnectionHealth
import com.magpie.pocket.core.model.ConnectionStatus
import com.magpie.pocket.core.model.CostDisplay
import com.magpie.pocket.core.model.Coverage
import com.magpie.pocket.core.model.DataCoverage
import com.magpie.pocket.core.model.OverviewSnapshot
import com.magpie.pocket.core.model.QuotaAlert
import com.magpie.pocket.core.model.RequestSummary
import com.magpie.pocket.core.util.MetricCalculators
import com.magpie.pocket.core.util.RequestTimestamps
import kotlinx.coroutines.delay

/** The page lists at most three in-flight routes; the section note says so. */
private const val ACTIVITY_LIMIT = 3

/**
 * Overview answers "what is happening now": today's counters, the in-flight routes, the
 * quota windows already in the warning zone and today's hourly buckets. It never shows
 * another period's numbers under a today label, and every missing value stays an em dash.
 */
@Composable
fun OverviewScreen(
    overview: OverviewSnapshot?,
    runningRequests: List<RequestSummary>,
    quotaAlerts: List<QuotaAlert>,
    statisticsDay: String?,
    cost: CostDisplay,
    dataCoverage: DataCoverage,
    isLoading: Boolean,
    isOnline: Boolean,
    connectionStatus: ConnectionStatus,
    isDemo: Boolean,
    onOpenMore: () -> Unit,
    onOpenQuota: () -> Unit,
    onOpenRequest: (String) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val colors = MagpieTheme.colors
    val configuration = LocalConfiguration.current
    // A 320dp phone keeps 16dp of margin; from 360dp up the page uses the 20dp gutter.
    val pagePadding = if (configuration.screenWidthDp < 360) {
        MagpieDimensions.CompactPagePadding
    } else {
        MagpieDimensions.PagePadding
    }
    val pageInset = Modifier.padding(horizontal = pagePadding)

    val successRate = overview?.let {
        MetricCalculators.calculateSuccessRate(it.completedRequests, it.finalErrors)
    }
    val ttftMeanMs = overview?.let {
        MetricCalculators.calculateTtftMean(it.ttftSumMs, it.ttftSampleCount)
    }
    // While the first snapshot is still loading, the data cells say so instead of
    // pretending the gateway measured zero or that every value is missing.
    val dataPending = isLoading && overview == null
    val requestsValue = if (dataPending) {
        "同步中"
    } else {
        overview?.completedRequests?.toString() ?: "—"
    }
    val requestsNote = overview?.rangeEnd?.let(::cutoffLabel) ?: "—"
    val successNote = overview?.let { "${it.finalErrors} 次最终失败" } ?: "—"
    val ttftNote = overview?.ttftSampleCount?.let { "$it 个流式样本" } ?: "—"
    // A priced zero and "the upstream priced nothing" are different answers.
    val costNote = when {
        overview == null -> "—"
        cost.value == null -> "未提供计价"
        else -> "${cost.unpriced} 次未计价"
    }
    // Errors outrank a healthy session; a connected session outranks the data stage, so
    // the strip never says "同步中" for a connection that has already answered.
    val connectionLabel = when {
        !isOnline -> "离线"
        connectionStatus.health != ConnectionHealth.CONNECTED &&
            connectionStatus.health != ConnectionHealth.CONNECTING ->
            connectionHealthLabel(connectionStatus.health)
        connectionStatus.health == ConnectionHealth.CONNECTED -> "已连接"
        isLoading -> "同步中"
        else -> connectionHealthLabel(connectionStatus.health)
    }
    val hourlyPoints = overview?.hourlyTrends.orEmpty()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            // The header owns its own 20dp gutter, so it is not inset with the rest.
            // The brand bar above already carries the one theme switch; a second one here
            // stacked two identical buttons in the corner.
            MagpiePageHeader(
                title = "概览",
                subtitle = "现在"
            )
        }

        item {
            StatusStrip(
                connectionLabel = connectionLabel,
                demo = isDemo,
                dateLabel = statisticsDay?.let { "统计日 $it" },
                onConnectionClick = onOpenMore,
                modifier = pageInset
            )
        }

        item { SectionHeader(title = "今日", modifier = pageInset.padding(top = 14.dp)) }

        item {
            Column(
                modifier = pageInset.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    StatCell(
                        label = "今日请求",
                        value = requestsValue,
                        note = requestsNote,
                        modifier = Modifier.weight(1f)
                    )
                    StatCell(
                        label = "成功率",
                        value = if (dataPending) "同步中" else MetricCalculators.formatPercent(successRate),
                        note = successNote,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    StatCell(
                        label = "首响均值",
                        value = if (dataPending) {
                            "同步中"
                        } else {
                            MetricCalculators.formatDurationMs(ttftMeanMs?.toLong())
                        },
                        note = ttftNote,
                        modifier = Modifier.weight(1f)
                    )
                    StatCell(
                        label = "今日费用",
                        value = if (dataPending) "同步中" else MetricCalculators.formatCost(cost.value),
                        note = costNote,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            SectionHeader(
                title = "当前活动",
                note = "最多 $ACTIVITY_LIMIT 条",
                modifier = pageInset.padding(top = 14.dp)
            )
        }

        item {
            if (runningRequests.isEmpty()) {
                Text(
                    text = "当前没有处理中的请求",
                    style = MagpieTypography.SubheadStyle,
                    color = colors.textSecondary,
                    modifier = pageInset.padding(vertical = 8.dp)
                )
            } else {
                // One clock for the row set: every elapsed label advances on the same tick.
                val nowMs = rememberElapsedNow()
                Column(modifier = pageInset) {
                    runningRequests.take(ACTIVITY_LIMIT).forEach { request ->
                        ActivityRow(
                            request = request,
                            nowMs = nowMs,
                            onClick = { onOpenRequest(request.key.routeId) }
                        )
                    }
                }
            }
        }

        if (quotaAlerts.isNotEmpty()) {
            item { SectionHeader(title = "需要留意", modifier = pageInset.padding(top = 14.dp)) }
            item {
                WarnPanel(
                    message = quotaAlertMessage(quotaAlerts),
                    actionLabel = "查看",
                    onAction = onOpenQuota,
                    modifier = pageInset
                )
            }
        }

        item { SectionHeader(title = "今日 24 小时", modifier = pageInset.padding(top = 14.dp)) }

        item {
            if (hourlyPoints.isEmpty()) {
                Box(
                    modifier = pageInset
                        .fillMaxWidth()
                        .height(88.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (dataPending) "同步中" else "暂无趋势数据",
                        style = MagpieTypography.BodyStyle,
                        color = colors.textTertiary
                    )
                }
            } else {
                val values = hourlyPoints.map { it.completedRequests }
                val peak = values.maxOrNull() ?: 0L
                Column(modifier = pageInset) {
                    HourlyBars(
                        values = values,
                        description = "今日每小时完成请求柱图，峰值 $peak",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(88.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf(
                            hourlyPoints.first(),
                            hourlyPoints[hourlyPoints.size / 2],
                            hourlyPoints.last()
                        ).forEach { point ->
                            Text(
                                text = displayOrDash(point.hourLabel),
                                style = MagpieTypography.CaptionStyle,
                                color = colors.textTertiary,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        item {
            Text(
                text = footerText(dataCoverage),
                style = MagpieTypography.LabelStyle,
                color = colors.textSecondary,
                modifier = pageInset.padding(top = 14.dp, bottom = 8.dp)
            )
        }
    }
}

/** "截至 HH:mm" from the server's range end; a value without a time keeps its own shape. */
private fun cutoffLabel(rangeEnd: String): String {
    val time = rangeEnd.substringAfter('T', "").take(5)
    return if (time.length == 5) "截至 $time" else "截至 $rangeEnd"
}

private fun quotaAlertMessage(alerts: List<QuotaAlert>): String {
    val worst = alerts.first()
    val remaining = alerts.size - 1
    val tail = if (remaining > 0) {
        "，另有 $remaining 个额度窗口接近上限"
    } else {
        "，建议查看额度窗口"
    }
    return "${worst.providerLabel} 额度已用 ${MetricCalculators.formatPercent(worst.ratio)}$tail"
}

private fun footerText(coverage: DataCoverage): String {
    val caveat = when {
        coverage.coverage != Coverage.COMPLETE && coverage.stale -> "数据不完整，且可能过期。"
        coverage.coverage != Coverage.COMPLETE -> "数据不完整。"
        coverage.stale -> "数据可能过期。"
        else -> ""
    }
    return "成功含改道；费用只统计已计价调用。" + caveat
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

/** Model, source and provider, the accent-toned processing state and elapsed time. */
@Composable
private fun ActivityRow(
    request: RequestSummary,
    nowMs: Long,
    onClick: () -> Unit
) {
    val colors = MagpieTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = MagpieDimensions.TouchTargetMinimum)
            .springPress(onClick = onClick)
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = request.servedModel ?: request.requestedModel ?: "—",
                style = MagpieTypography.BodyStyle,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = displayOrDash(
                    listOfNotNull(request.agentId, request.providerId)
                        .filter { it.isNotBlank() }
                        .joinToString(" · ")
                ),
                style = MagpieTypography.LabelStyle,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "处理中",
            style = MagpieTypography.LabelStyle,
            color = colors.accent,
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = elapsedLabel(request.startedAt, nowMs),
            style = MagpieTypography.LabelStyle,
            color = colors.textTertiary,
            maxLines = 1
        )
    }
}

/** Elapsed milliseconds since the route started; an unparseable start time stays a dash. */
private fun elapsedLabel(startedAt: String?, nowMs: Long): String {
    val start = RequestTimestamps.parseMillis(startedAt) ?: return "—"
    return MetricCalculators.formatDurationMs((nowMs - start).coerceAtLeast(0L))
}

/**
 * A 1-second tick keeps in-flight elapsed labels honest without polling the gateway.
 * It runs only while the activity rows are composed.
 */
@Composable
private fun rememberElapsedNow(): Long {
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            nowMs = System.currentTimeMillis()
        }
    }
    return nowMs
}

/** One cell of the 2×2: 12sp label, 24sp value and a 12sp caveat that never wraps. */
@Composable
private fun StatCell(
    label: String,
    value: String,
    note: String,
    modifier: Modifier = Modifier
) {
    val colors = MagpieTheme.colors
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        Text(
            text = label,
            style = MagpieTypography.LabelStyle,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = MagpieTypography.InlineMetricStyle,
            color = colors.textPrimary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = note,
            style = MagpieTypography.LabelStyle,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** One bar per upstream bucket, on a shared baseline; zero buckets keep an empty slot. */
@Composable
private fun HourlyBars(
    values: List<Long>,
    description: String,
    modifier: Modifier = Modifier
) {
    if (values.isEmpty()) return
    val colors = MagpieTheme.colors
    val peak = values.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    Canvas(modifier = modifier.semantics { contentDescription = description }) {
        val baseline = size.height - 1.dp.toPx()
        val top = 4.dp.toPx()
        drawLine(
            color = colors.divider,
            start = Offset(0f, baseline),
            end = Offset(size.width, baseline),
            strokeWidth = 1.dp.toPx()
        )
        val slot = size.width / values.size
        val barWidth = (slot * 0.55f).coerceAtLeast(1.5.dp.toPx())
        values.forEachIndexed { index, value ->
            val height = (baseline - top) * (value.toFloat() / peak.toFloat())
            if (height <= 0f) return@forEachIndexed
            drawRoundRect(
                color = colors.accent.copy(alpha = 0.82f),
                topLeft = Offset(index * slot + (slot - barWidth) / 2f, baseline - height),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
