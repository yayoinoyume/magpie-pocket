package com.magpie.pocket.widget

import android.content.Context
import android.content.res.Configuration
import android.os.SystemClock
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
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
import com.magpie.pocket.R
import com.magpie.pocket.app.MainActivity
import com.magpie.pocket.core.design.AppTab

/**
 * The 4×2 live panel: three in-flight calls, one per row. The elapsed time is a real
 * [android.widget.Chronometer] embedded as RemoteViews, so the launcher keeps it ticking
 * without repainting the widget every second.
 */
class WidgetLive : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initialState = WidgetLiveStateStore.load(context)
        val stateFlow = WidgetLiveStateStore.observe(context)
        val isDark = (context.resources.configuration.uiMode and
            Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        provideContent {
            val state by stateFlow.collectAsState(initial = initialState)
            WidgetLiveContent(state = state, isDark = isDark)
        }
    }
}

class WidgetLiveReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WidgetLive()
}

@Composable
private fun WidgetLiveContent(state: WidgetLiveState, isDark: Boolean) {
    val colors = widgetPalette(isDark)
    val openAction = if (state.connected) openRoutingAction() else openConnectAction()
    Column(
        modifier = GlanceModifier.fillMaxSize()
            .background(ImageProvider(colors.background))
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clickable(openAction)
    ) {
        Text(
            text = "当前活动",
            style = TextStyle(
                color = ColorProvider(colors.text),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Serif
            ),
            maxLines = 1
        )
        Spacer(GlanceModifier.height(6.dp))
        when {
            !state.connected -> EmptyLivePanel(text = "未连接", color = colors.accent)
            state.requests.isEmpty() -> EmptyLivePanel(text = "当前空闲", color = colors.secondary)
            else -> Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                repeat(WidgetLiveStateStore.MAX_ROWS) { index ->
                    val request = state.requests.getOrNull(index)
                    if (request == null) {
                        // Missing calls leave their row blank; the panel never invents a line.
                        Spacer(GlanceModifier.fillMaxWidth().height(22.dp))
                    } else {
                        LiveRow(request = request, colors = colors)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.EmptyLivePanel(
    text: String,
    color: Color
) {
    Box(
        modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = ColorProvider(color),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun LiveRow(request: WidgetLiveRequest, colors: WidgetPalette) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(22.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = request.model + " · " + request.source,
            modifier = GlanceModifier.defaultWeight(),
            style = TextStyle(
                color = ColorProvider(colors.text),
                fontSize = 12.sp
            ),
            maxLines = 1
        )
        Text(
            text = "已运行",
            style = TextStyle(
                color = ColorProvider(colors.tertiary),
                fontSize = 9.sp
            ),
            maxLines = 1
        )
        Spacer(GlanceModifier.width(4.dp))
        val startedAt = request.startedAt
        if (startedAt == null) {
            Text(
                text = "—",
                style = TextStyle(
                    color = ColorProvider(colors.tertiary),
                    fontSize = 12.sp
                ),
                maxLines = 1
            )
        } else {
            AndroidRemoteViews(
                remoteViews = elapsedRemoteViews(context, startedAt, colors.text)
            )
        }
    }
}

/**
 * Chronometer takes an elapsedRealtime base, not a wall-clock timestamp. Rebuilding the base
 * from the current clock keeps a row that started before this composition honest.
 */
private fun elapsedRemoteViews(
    context: Context,
    startedAtWallMillis: Long,
    color: Color
): RemoteViews = RemoteViews(context.packageName, R.layout.widget_live_elapsed).apply {
    val elapsed = (System.currentTimeMillis() - startedAtWallMillis).coerceAtLeast(0L)
    val base = SystemClock.elapsedRealtime() - elapsed
    setChronometer(R.id.widget_live_elapsed, base, null, true)
    setTextColor(R.id.widget_live_elapsed, color.toArgb())
}

private fun openRoutingAction() = actionStartActivity<MainActivity>(
    actionParametersOf(
        ActionParameters.Key<String>(MainActivity.EXTRA_TAB) to AppTab.ROUTING.name
    )
)

private fun openConnectAction() = actionStartActivity<MainActivity>(
    actionParametersOf(
        ActionParameters.Key<String>(MainActivity.EXTRA_TAB) to AppTab.OVERVIEW.name,
        ActionParameters.Key<Boolean>(MainActivity.EXTRA_OPEN_CONNECT) to true
    )
)
