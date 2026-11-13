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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.magpie.pocket.app.MainActivity
import com.magpie.pocket.core.design.AppTab
import com.magpie.pocket.core.util.MetricCalculators

/**
 * The 4×1 mini bar: one hero request count and three compact readings, in the shared widget
 * palette. One tap target covers the whole surface.
 */
class WidgetMini : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initialState = WidgetStateStore.load(context)
        val stateFlow = WidgetStateStore.observe(context)
        val initialTheme = WidgetThemeSource.load(context)
        val themeFlow = WidgetThemeSource.observe(context)
        val systemDark = WidgetThemeSource.systemDark(context)
        provideContent {
            val state by stateFlow.collectAsState(initial = initialState)
            val theme by themeFlow.collectAsState(initial = initialTheme)
            WidgetMiniContent(state = state, isDark = theme.isDark(systemDark))
        }
    }
}

class WidgetMiniReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WidgetMini()
}

@Composable
private fun WidgetMiniContent(state: WidgetState?, isDark: Boolean) {
    val colors = widgetPalette(isDark)
    if (state != null && !state.connected) {
        Box(
            modifier = GlanceModifier.fillMaxSize()
                .background(ImageProvider(colors.background))
                .clickable(openConnectAction()),
            contentAlignment = Alignment.Center
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
        }
        return
    }

    Row(
        modifier = GlanceModifier.fillMaxSize()
            .background(ImageProvider(colors.background))
            .padding(horizontal = 16.dp, vertical = 7.dp)
            .clickable(openOverviewAction()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MiniHero(
            value = WidgetMiniText.requests(state),
            colors = colors
        )
        Spacer(GlanceModifier.width(12.dp))
        MiniMetric(label = "成功率", value = WidgetMiniText.successRate(state), colors = colors)
        MiniMetric(label = "首响", value = WidgetMiniText.ttft(state), colors = colors)
        MiniMetric(label = "费用", value = WidgetMiniText.cost(state), colors = colors)
    }
}

@Composable
private fun RowScope.MiniHero(value: String, colors: WidgetPalette) {
    Column(modifier = GlanceModifier.defaultWeight()) {
        Text(
            text = "请求",
            style = TextStyle(
                color = ColorProvider(colors.accent),
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1
        )
        Spacer(GlanceModifier.height(2.dp))
        Text(
            text = value,
            style = TextStyle(
                color = ColorProvider(colors.text),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun RowScope.MiniMetric(label: String, value: String, colors: WidgetPalette) {
    Column(
        modifier = GlanceModifier.defaultWeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = value,
            style = TextStyle(
                color = ColorProvider(colors.text),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
        Spacer(GlanceModifier.height(2.dp))
        Text(
            text = label,
            style = TextStyle(
                color = ColorProvider(colors.tertiary),
                fontSize = 9.sp
            ),
            maxLines = 1
        )
    }
}

/**
 * Formatting stays pure so the mini bar and later sizes print the same numbers as the app.
 * A missing metric is an em dash; requests stay the full count that the overview shows.
 */
internal object WidgetMiniText {

    fun requests(state: WidgetState?): String = state?.requests?.toString() ?: "—"

    fun successRate(state: WidgetState?): String =
        MetricCalculators.formatPercent(state?.successRate)

    fun ttft(state: WidgetState?): String =
        MetricCalculators.formatDurationMs(state?.ttftMs?.toLong())

    fun cost(state: WidgetState?): String {
        val cost = state?.cost ?: return "—"
        return "¥" + MetricCalculators.formatCost(cost)
    }
}

private fun openOverviewAction() = actionStartActivity<MainActivity>(
    actionParametersOf(
        ActionParameters.Key<String>(MainActivity.EXTRA_TAB) to AppTab.OVERVIEW.name
    )
)

private fun openConnectAction() = actionStartActivity<MainActivity>(
    actionParametersOf(
        ActionParameters.Key<String>(MainActivity.EXTRA_TAB) to AppTab.OVERVIEW.name,
        ActionParameters.Key<Boolean>(MainActivity.EXTRA_OPEN_CONNECT) to true
    )
)
