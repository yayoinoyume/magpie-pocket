package com.magpie.pocket.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.magpie.pocket.app.MainActivity
import com.magpie.pocket.app.Notifications
import com.magpie.pocket.core.design.AppTab
import com.magpie.pocket.core.util.MetricCalculators
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The 4×2 overview: a hero request count, three compact ledger lines and the 24-hour strip.
 * It reads the same snapshot as the mini bar, so both sizes agree on the numbers.
 */
class WidgetOverview : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initialState = WidgetStateStore.load(context)
        val stateFlow = WidgetStateStore.observe(context)
        val initialTheme = WidgetThemeSource.load(context)
        val themeFlow = WidgetThemeSource.observe(context)
        val systemDark = WidgetThemeSource.systemDark(context)
        provideContent {
            val state by stateFlow.collectAsState(initial = initialState)
            val theme by themeFlow.collectAsState(initial = initialTheme)
            WidgetOverviewContent(state = state, isDark = theme.isDark(systemDark))
        }
    }
}

class WidgetOverviewReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WidgetOverview()
}

@Composable
private fun WidgetOverviewContent(state: WidgetState?, isDark: Boolean) {
    val colors = widgetPalette(isDark)
    // A failed connection must not present the previous snapshot as today's numbers.
    if (state == null || !state.connected) {
        OverviewPlaceholder(state = state, colors = colors)
        return
    }

    Column(
        modifier = GlanceModifier.fillMaxSize()
            .background(ImageProvider(colors.background))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "今日",
                style = TextStyle(
                    color = ColorProvider(colors.text),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    // Android resolves the serif family to Noto Serif CJK for these glyphs.
                    fontFamily = FontFamily.Serif
                ),
                maxLines = 1
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = updatedLabel(state.updatedAt),
                style = TextStyle(
                    color = ColorProvider(colors.tertiary),
                    fontSize = 9.sp
                ),
                maxLines = 1
            )
        }
        Spacer(GlanceModifier.height(3.dp))
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = GlanceModifier.defaultWeight()
                    .clickable(openRequestsAction())
            ) {
                Text(
                    text = "请求",
                    style = TextStyle(
                        color = ColorProvider(colors.accent),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 1
                )
                Spacer(GlanceModifier.height(1.dp))
                Text(
                    text = WidgetMiniText.requests(state),
                    style = TextStyle(
                        color = ColorProvider(colors.text),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1
                )
            }
            Spacer(GlanceModifier.width(12.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                OverviewLedgerLine(
                    label = "成功率",
                    value = WidgetOverviewText.successRate(state),
                    colors = colors,
                    action = openRequestsAction()
                )
                OverviewLedgerLine(
                    label = "首响",
                    value = WidgetMiniText.ttft(state),
                    colors = colors,
                    action = openUsageAction()
                )
                OverviewLedgerLine(
                    label = "费用",
                    value = WidgetMiniText.cost(state),
                    colors = colors,
                    action = openUsageAction()
                )
            }
        }
        Spacer(GlanceModifier.height(3.dp))
        HourlyBars(values = state.hourlyRequests, colors = colors)
    }
}

@Composable
private fun OverviewLedgerLine(
    label: String,
    value: String,
    colors: WidgetPalette,
    action: Action
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().clickable(action),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = ColorProvider(colors.tertiary),
                fontSize = 9.sp
            ),
            maxLines = 1
        )
        Spacer(GlanceModifier.defaultWeight())
        Spacer(GlanceModifier.width(4.dp))
        Text(
            text = value,
            style = TextStyle(
                color = ColorProvider(colors.text),
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun OverviewPlaceholder(state: WidgetState?, colors: WidgetPalette) {
    Column(
        modifier = GlanceModifier.fillMaxSize()
            .background(ImageProvider(colors.background))
            .clickable(openConnectAction())
            .padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "未连接",
            style = TextStyle(
                color = ColorProvider(colors.accent),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1
        )
        Spacer(GlanceModifier.height(3.dp))
        Text(
            text = staleLabel(state),
            style = TextStyle(
                color = ColorProvider(colors.tertiary),
                fontSize = 9.sp
            ),
            maxLines = 1
        )
    }
}

/**
 * 24 bars do not fit one Glance Row (ten children max), so the series splits into at most three
 * equal-width rows of eight. Short rows are padded with spacers so every bar keeps its width.
 */
@Composable
private fun HourlyBars(values: List<Long>, colors: WidgetPalette) {
    val points = values.takeLast(MAX_BARS)
    val max = points.maxOrNull() ?: 0L
    if (points.isEmpty() || max <= 0L) {
        Text(
            text = "暂无趋势",
            style = TextStyle(
                color = ColorProvider(colors.tertiary),
                fontSize = 9.sp
            ),
            maxLines = 1
        )
        return
    }

    val rowCount = (points.size + MAX_ROW_CHILDREN - 1) / MAX_ROW_CHILDREN
    val perRow = (points.size + rowCount - 1) / rowCount
    Row(
        modifier = GlanceModifier.fillMaxWidth()
            .height(CHART_HEIGHT)
            .clickable(openUsageAction()),
        verticalAlignment = Alignment.Bottom
    ) {
        points.chunked(perRow).forEach { chunk ->
            Row(
                modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                verticalAlignment = Alignment.Bottom
            ) {
                chunk.forEach { value ->
                    HourlyBar(value = value, max = max, colors = colors)
                }
                repeat(perRow - chunk.size) {
                    Spacer(GlanceModifier.defaultWeight())
                }
            }
        }
    }
}

@Composable
private fun RowScope.HourlyBar(value: Long, max: Long, colors: WidgetPalette) {
    val barHeight = (CHART_HEIGHT.value * value.toFloat() / max.toFloat())
        .coerceAtLeast(MIN_BAR_HEIGHT)
        .dp
    Box(
        modifier = GlanceModifier.defaultWeight()
            .fillMaxHeight()
            .padding(horizontal = 1.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            modifier = GlanceModifier.fillMaxWidth()
                .height(barHeight)
                .background(ColorProvider(colors.accent.copy(alpha = 0.72f)))
        ) {}
    }
}

/** Matches the mini bar's numbers, but drops ".0" so a full day reads "100%". */
internal object WidgetOverviewText {

    fun successRate(state: WidgetState?): String =
        MetricCalculators.formatPercent(state?.successRate).replace(".0%", "%")
}

private fun updatedLabel(updatedAt: Long): String =
    if (updatedAt > 0L) "更新于 ${timeFormat().format(Date(updatedAt))}" else "未同步"

private fun staleLabel(state: WidgetState?): String {
    val updatedAt = state?.updatedAt ?: 0L
    return if (updatedAt > 0L) {
        "上次更新 " + dateTimeFormat().format(Date(updatedAt))
    } else {
        "打开应用同步"
    }
}

private fun timeFormat(): SimpleDateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

private fun dateTimeFormat(): SimpleDateFormat =
    SimpleDateFormat("M月d日 HH:mm", Locale.getDefault())

private fun openRequestsAction(): Action = actionStartActivity<MainActivity>(
    actionParametersOf(
        ActionParameters.Key<String>(MainActivity.EXTRA_TAB) to AppTab.REQUESTS.name
    )
)

private fun openUsageAction(): Action = actionStartActivity<MainActivity>(
    actionParametersOf(
        ActionParameters.Key<String>(MainActivity.EXTRA_TAB) to AppTab.USAGE.name,
        // Any non-"quota" section lands on the usage sub-page rather than the quota sheet.
        ActionParameters.Key<String>(Notifications.EXTRA_USAGE_SECTION) to USAGE_SECTION
    )
)

private fun openConnectAction(): Action = actionStartActivity<MainActivity>(
    actionParametersOf(
        ActionParameters.Key<String>(MainActivity.EXTRA_TAB) to AppTab.OVERVIEW.name,
        ActionParameters.Key<Boolean>(MainActivity.EXTRA_OPEN_CONNECT) to true
    )
)

private const val USAGE_SECTION = "usage"
private const val MAX_ROW_CHILDREN = 10
private const val MAX_BARS = 24
private val CHART_HEIGHT = 14.dp
private const val MIN_BAR_HEIGHT = 1.5f
