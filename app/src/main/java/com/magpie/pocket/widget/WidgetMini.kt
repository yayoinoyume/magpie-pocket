package com.magpie.pocket.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.magpie.pocket.R
import com.magpie.pocket.app.MainActivity
import com.magpie.pocket.core.design.AppTab
import com.magpie.pocket.core.util.MetricCalculators

/**
 * The 4×1 mini bar: four metrics in one row, the shared widget palette, and one tap target
 * for the whole surface. Later sizes add their own GlanceAppWidget without touching this one.
 */
class WidgetMini : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initialState = WidgetStateStore.load(context)
        val stateFlow = WidgetStateStore.observe(context)
        val initialConfig = WidgetConfigStore.load(context)
        val configFlow = WidgetConfigStore.observe(context)
        val systemDark = (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        provideContent {
            val state by stateFlow.collectAsState(initial = initialState)
            val config by configFlow.collectAsState(initial = initialConfig)
            WidgetMiniContent(state = state, isDark = config.isDark(systemDark))
        }
    }
}

class WidgetMiniReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WidgetMini()
}

@Composable
private fun WidgetMiniContent(state: WidgetState?, isDark: Boolean) {
    val colors = if (isDark) MiniDarkColors else MiniLightColors
    val backgroundRes = if (isDark) {
        R.drawable.widget_mini_background_dark
    } else {
        R.drawable.widget_mini_background_light
    }
    if (state != null && !state.connected) {
        Box(
            modifier = GlanceModifier.fillMaxSize()
                .background(ImageProvider(backgroundRes))
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
            .background(ImageProvider(backgroundRes))
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable(openOverviewAction()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MiniMetric(label = "请求", value = WidgetMiniText.requests(state), colors = colors)
        MiniMetric(label = "成功率", value = WidgetMiniText.successRate(state), colors = colors)
        MiniMetric(label = "首响", value = WidgetMiniText.ttft(state), colors = colors)
        MiniMetric(label = "费用", value = WidgetMiniText.cost(state), colors = colors)
    }
}

@Composable
private fun RowScope.MiniMetric(label: String, value: String, colors: MiniColors) {
    Column(
        modifier = GlanceModifier.defaultWeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = value,
            style = TextStyle(
                color = ColorProvider(colors.text),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
        Spacer(GlanceModifier.height(2.dp))
        Text(
            text = label,
            style = TextStyle(
                color = ColorProvider(colors.accent),
                fontSize = 10.sp
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

private data class MiniColors(
    val text: Color,
    val accent: Color
)

// Widgets do not read MaterialTheme; each palette is fixed so the bar matches the brand.
// The rounded background lives in a shape drawable so API 24 gets the same 24dp corners.
private val MiniDarkColors = MiniColors(
    text = Color(0xFFE9E8F0),
    accent = Color(0xFF9A92EE)
)

private val MiniLightColors = MiniColors(
    text = Color(0xFF1C1A17),
    accent = Color(0xFF6C5CE7)
)

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
