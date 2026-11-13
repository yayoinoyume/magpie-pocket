package com.magpie.pocket.widget

import android.content.Context
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
import com.magpie.pocket.R
import com.magpie.pocket.app.MainActivity
import com.magpie.pocket.app.Notifications
import com.magpie.pocket.core.design.AppTab
import kotlin.math.roundToInt

/**
 * The 2×2 quota tile: average remaining, window count and how many windows sit below 30%.
 * Glance has no arc canvas, so the page's ring becomes a horizontal bar plus the same big
 * number; the palette and 24dp corners match the mini bar.
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
    val colors = if (isDark) QuotaDarkColors else QuotaLightColors
    val backgroundRes = if (isDark) {
        R.drawable.widget_quota_background_dark
    } else {
        R.drawable.widget_quota_background_light
    }
    val hasData = state != null && state.windowCount > 0
    Column(
        modifier = GlanceModifier.fillMaxSize()
            .background(ImageProvider(backgroundRes))
            .padding(horizontal = 12.dp, vertical = 8.dp)
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
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = "平均剩余",
                style = TextStyle(color = ColorProvider(colors.secondary), fontSize = 10.sp),
                maxLines = 1
            )
        }
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = WidgetQuotaText.average(state),
            style = TextStyle(
                color = ColorProvider(colors.text),
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
        Spacer(GlanceModifier.height(6.dp))
        LinearProgressIndicator(
            progress = WidgetQuotaText.fraction(state),
            modifier = GlanceModifier.fillMaxWidth().height(6.dp),
            color = ColorProvider(colors.accent),
            backgroundColor = ColorProvider(colors.track)
        )
        Spacer(GlanceModifier.height(6.dp))
        Spacer(GlanceModifier.defaultWeight())
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (hasData) {
                    (state?.windowCount ?: 0).toString() + " 个窗口"
                } else {
                    "暂无额度数据"
                },
                style = TextStyle(color = ColorProvider(colors.secondary), fontSize = 10.sp),
                maxLines = 1
            )
            Spacer(GlanceModifier.defaultWeight())
            Text(
                text = if (hasData) {
                    (state?.belowThirty ?: 0).toString() + " 项 <30%"
                } else {
                    "打开应用"
                },
                style = TextStyle(
                    color = ColorProvider(
                        if (hasData && (state?.belowThirty ?: 0) > 0) {
                            colors.accent
                        } else {
                            colors.secondary
                        }
                    ),
                    fontSize = 10.sp
                ),
                maxLines = 1
            )
        }
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

private data class QuotaColors(
    val text: Color,
    val secondary: Color,
    val accent: Color,
    val track: Color
)

// Widgets do not read MaterialTheme; each palette is fixed to the approved widget colors.
private val QuotaDarkColors = QuotaColors(
    text = Color(0xFFE9E8F0),
    secondary = Color(0xFFE9E8F0).copy(alpha = 0.70f),
    accent = Color(0xFF9A92EE),
    track = Color(0xFFE9E8F0).copy(alpha = 0.12f)
)

private val QuotaLightColors = QuotaColors(
    text = Color(0xFF1C1A17),
    secondary = Color(0xFF1C1A17).copy(alpha = 0.70f),
    accent = Color(0xFF6C5CE7),
    track = Color(0xFF1C1A17).copy(alpha = 0.12f)
)

private fun openQuotaAction() = actionStartActivity<MainActivity>(
    actionParametersOf(
        ActionParameters.Key<String>(MainActivity.EXTRA_TAB) to AppTab.USAGE.name,
        ActionParameters.Key<String>(Notifications.EXTRA_USAGE_SECTION) to
            Notifications.USAGE_SECTION_QUOTA
    )
)
