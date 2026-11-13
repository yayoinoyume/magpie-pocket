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
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.magpie.pocket.app.MainActivity
import com.magpie.pocket.app.Notifications
import com.magpie.pocket.core.design.AppTab
import kotlin.math.roundToInt

/**
 * The 2×2 quota tile: a hero average, its bar, and two footnote lines for the window count
 * and the windows below 30%. Glance has no arc canvas, so the page's ring becomes this bar.
 */
class WidgetQuota : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initialState = WidgetQuotaStateStore.load(context)
        val stateFlow = WidgetQuotaStateStore.observe(context)
        val initialTheme = WidgetThemeSource.load(context)
        val themeFlow = WidgetThemeSource.observe(context)
        val systemDark = WidgetThemeSource.systemDark(context)
        provideContent {
            val state by stateFlow.collectAsState(initial = initialState)
            val theme by themeFlow.collectAsState(initial = initialTheme)
            WidgetQuotaContent(state = state, isDark = theme.isDark(systemDark))
        }
    }
}

class WidgetQuotaReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WidgetQuota()
}

@Composable
private fun WidgetQuotaContent(state: WidgetQuotaState?, isDark: Boolean) {
    val colors = widgetPalette(isDark)
    val hasData = state != null && state.windowCount > 0
    val belowThirty = state?.belowThirty ?: 0
    Column(
        modifier = GlanceModifier.fillMaxSize()
            .background(ImageProvider(colors.quotaBackground))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clickable(openQuotaAction())
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "额度",
                style = TextStyle(
                    color = ColorProvider(colors.accent),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = "平均剩余",
                style = TextStyle(color = ColorProvider(colors.secondary), fontSize = 9.sp),
                maxLines = 1
            )
        }
        Spacer(GlanceModifier.height(2.dp))
        Text(
            text = WidgetQuotaText.average(state),
            style = TextStyle(
                color = ColorProvider(colors.text),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
        Spacer(GlanceModifier.height(4.dp))
        LinearProgressIndicator(
            progress = WidgetQuotaText.fraction(state),
            modifier = GlanceModifier.fillMaxWidth().height(6.dp),
            color = ColorProvider(colors.accent),
            backgroundColor = ColorProvider(colors.divider)
        )
        Spacer(GlanceModifier.defaultWeight())
        Text(
            text = if (hasData) {
                (state?.windowCount ?: 0).toString() + " 个窗口"
            } else {
                "暂无额度数据"
            },
            style = TextStyle(color = ColorProvider(colors.secondary), fontSize = 9.sp),
            maxLines = 1
        )
        Spacer(GlanceModifier.height(1.dp))
        Text(
            text = if (hasData) {
                belowThirty.toString() + " 项 <30%"
            } else {
                "打开应用"
            },
            style = TextStyle(
                color = ColorProvider(
                    if (hasData && belowThirty > 0) colors.warning else colors.secondary
                ),
                fontSize = 9.sp
            ),
            maxLines = 1
        )
    }
}

/** Formatting stays pure so a test can pin the exact widget copy. */
internal object WidgetQuotaText {

    fun average(state: WidgetQuotaState?): String {
        val average = state?.averageRemaining ?: return "—"
        return average.roundToInt().toString() + "%"
    }

    fun fraction(state: WidgetQuotaState?): Float =
        ((state?.averageRemaining ?: 0.0) / 100.0).coerceIn(0.0, 1.0).toFloat()
}

private fun openQuotaAction() = actionStartActivity<MainActivity>(
    actionParametersOf(
        ActionParameters.Key<String>(MainActivity.EXTRA_TAB) to AppTab.USAGE.name,
        ActionParameters.Key<String>(Notifications.EXTRA_USAGE_SECTION) to
            Notifications.USAGE_SECTION_QUOTA
    )
)
