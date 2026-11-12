package com.magpie.pocket.widget

import android.content.Context
import com.magpie.pocket.app.AppViewModel
import com.magpie.pocket.app.MagpieApplication
import com.magpie.pocket.core.model.ConnectionHealth
import com.magpie.pocket.core.model.RequestSummary
import com.magpie.pocket.core.util.RequestTimestamps
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/** One in-flight call, already reduced to the three fields the 4×2 row prints. */
data class WidgetLiveRequest(
    val model: String,
    val source: String,
    /** Wall-clock start, or null when the upstream timestamp cannot be parsed. */
    val startedAt: Long?
)

/**
 * The live rows a 4×2 widget shows. A widget-only process has no view model to read, so it
 * reports [connected] false and no rows rather than inventing an empty-looking session.
 */
data class WidgetLiveState(
    val requests: List<WidgetLiveRequest> = emptyList(),
    val connected: Boolean = false
)

object WidgetLiveStateStore {

    const val MAX_ROWS = 3

    fun load(context: Context): WidgetLiveState = live(context) ?: WidgetLiveState()

    /**
     * A live app process follows the view model's own flows. A cold process gets one fixed
     * empty state: in-flight calls are ephemeral and must not be restored from disk.
     */
    fun observe(context: Context): Flow<WidgetLiveState> {
        val viewModel = liveViewModel(context) ?: return flowOf(WidgetLiveState())
        return combine(viewModel.runningRequests, viewModel.connectionStatus) { running, status ->
            stateOf(running, status.health)
        }
    }

    private fun live(context: Context): WidgetLiveState? {
        val viewModel = liveViewModel(context) ?: return null
        return stateOf(viewModel.runningRequests.value, viewModel.connectionStatus.value.health)
    }

    private fun liveViewModel(context: Context): AppViewModel? =
        (context.applicationContext as? MagpieApplication)?.existingAppViewModel

    private fun stateOf(
        running: List<RequestSummary>,
        health: ConnectionHealth
    ): WidgetLiveState = WidgetLiveState(
        requests = running.take(MAX_ROWS).map(::toRow),
        // CONNECTING still renders placeholders; only a real failure reads "未连接".
        connected = health == ConnectionHealth.CONNECTED ||
            health == ConnectionHealth.CONNECTING
    )

    private fun toRow(request: RequestSummary): WidgetLiveRequest = WidgetLiveRequest(
        model = request.servedModel?.takeIf { it.isNotBlank() }
            ?: request.requestedModel?.takeIf { it.isNotBlank() }
            ?: "—",
        source = request.agentId?.takeIf { it.isNotBlank() }
            ?: request.providerId?.takeIf { it.isNotBlank() }
            ?: "—",
        startedAt = RequestTimestamps.parseMillis(request.startedAt)
    )
}
