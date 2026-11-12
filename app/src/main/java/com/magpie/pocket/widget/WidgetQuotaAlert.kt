package com.magpie.pocket.widget

import android.content.Context
import android.content.res.Configuration
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
import com.magpie.pocket.app.Notifications
import com.magpie.pocket.app.formatQuotaPercent
import com.magpie.pocket.core.design.AppTab
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The 4×1 quota alert. One warning zone shows the worst window and counts the rest; a clean
 * snapshot shows the connection strip instead, so an empty alert never leaves a red bar.
 */
class WidgetQuotaAlert : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initialState = WidgetStateStore.load(context)
        val stateFlow = WidgetStateStore.observe(context)
        val isDark = (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        provideContent {
            val state by stateFlow.collectAsState(initial = initialState)
            WidgetQuotaAlertContent(state = state, isDark = isDark)
        }
    }
}

class WidgetQuotaAlertReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WidgetQuotaAlert()
}

@Composable
private fun WidgetQuotaAlertContent(state: WidgetState?, isDark: Boolean) {
    val colors = widgetPalette(isDark)
    val alert = state?.quotaAlert
    Row(
        modifier = GlanceModifier.fillMaxSize()
            .background(ImageProvider(colors.background))
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clickable(if (alert != null) openQuotaAction() else openOverviewAction()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (alert == null) {
            StatusRow(state = state, colors = colors)
        } else {
            AlertRow(alert = alert, colors = colors)
        }
    }
}

@Composable
private fun RowScope.AlertRow(
    alert: WidgetQuotaAlertSummary,
    colors: WidgetPalette
) {
    Box(
        modifier = GlanceModifier.width(3.dp)
            .height(28.dp)
            .background(ColorProvider(colors.error))
    ) {}
    Spacer(GlanceModifier.width(8.dp))
    Column(modifier = GlanceModifier.defaultWeight()) {
        Text(
            text = alert.providerLabel,
            style = TextStyle(
                color = ColorProvider(colors.text),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1
        )
        Text(
            text = if (alert.otherWindowCount > 0) {
                "另有 " + alert.otherWindowCount + " 个窗口接近上限"
            } else {
                "额度接近上限"
            },
            style = TextStyle(
                color = ColorProvider(colors.secondary),
                fontSize = 9.sp
            ),
            maxLines = 1
        )
    }
    Spacer(GlanceModifier.width(6.dp))
    Text(
        text = formatQuotaPercent(alert.ratio * 100.0),
        style = TextStyle(
            color = ColorProvider(colors.error),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        ),
        maxLines = 1
    )
}

@Composable
private fun RowScope.StatusRow(
    state: WidgetState?,
    colors: WidgetPalette
) {
    val connected = state?.connected == true
    Box(
        modifier = GlanceModifier.width(6.dp)
            .height(6.dp)
            .background(ColorProvider(if (connected) colors.accent else colors.tertiary))
    ) {}
    Spacer(GlanceModifier.width(8.dp))
    Text(
        text = if (connected) "已连接" else "未连接",
        style = TextStyle(
            color = ColorProvider(if (connected) colors.accent else colors.secondary),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        ),
        maxLines = 1
    )
    Spacer(GlanceModifier.defaultWeight())
    val updatedAt = state?.updatedAt ?: 0L
    if (updatedAt > 0L) {
        Text(
            text = updatedLabel(updatedAt),
            style = TextStyle(
                color = ColorProvider(colors.tertiary),
                fontSize = 10.sp
            ),
            maxLines = 1
        )
    }
}

/** "12:04 更新" in the device timezone; the snapshot clock, not the widget paint time. */
private fun updatedLabel(updatedAt: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(updatedAt)) + " 更新"

private fun openQuotaAction() = actionStartActivity<MainActivity>(
    actionParametersOf(
        ActionParameters.Key<String>(MainActivity.EXTRA_TAB) to AppTab.USAGE.name,
        ActionParameters.Key<String>(Notifications.EXTRA_USAGE_SECTION) to
            Notifications.USAGE_SECTION_QUOTA
    )
)

private fun openOverviewAction() = actionStartActivity<MainActivity>(
    actionParametersOf(
        ActionParameters.Key<String>(MainActivity.EXTRA_TAB) to AppTab.OVERVIEW.name
    )
)
