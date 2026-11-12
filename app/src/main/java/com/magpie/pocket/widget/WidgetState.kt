package com.magpie.pocket.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.magpie.pocket.app.AppViewModel
import com.magpie.pocket.app.MagpieApplication
import com.magpie.pocket.app.NotificationText
import com.magpie.pocket.app.QuotaAlertGate
import com.magpie.pocket.app.QuotaReading
import com.magpie.pocket.core.model.AccountQuota
import com.magpie.pocket.core.model.ConnectionHealth
import com.magpie.pocket.core.model.OverviewSnapshot
import com.magpie.pocket.core.util.MetricCalculators
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.widgetSnapshotDataStore by
    preferencesDataStore(name = "magpie_widget_snapshot")

/**
 * The numbers a home-screen widget shows. A null metric means "not measured or not loaded",
 * never a real zero; [connected] separates a stale snapshot from a connection that failed.
 */
data class WidgetState(
    val requests: Long? = null,
    val successRate: Double? = null,
    val ttftMs: Double? = null,
    val cost: Double? = null,
    val connected: Boolean = false,
    val updatedAt: Long = 0L,
    val quotaAlert: WidgetQuotaAlertSummary? = null
)

/**
 * The worst quota window at or above the alert threshold. [otherWindowCount] counts the further
 * windows in the same warning, so the 4×1 alert never duplicates a list it cannot fit.
 */
data class WidgetQuotaAlertSummary(
    val providerLabel: String,
    val ratio: Double,
    val otherWindowCount: Int
)

/**
 * Reads widget data without owning the network. A live app process answers from the shared
 * view model's current flows; a cold widget process falls back to the snapshot the app wrote
 * after its last successful overview refresh.
 */
object WidgetStateStore {

    /**
     * The app process's live snapshot, or null when only a widget broadcast woke this process.
     * The accessor must never construct [AppViewModel]; that would start the network stack.
     */
    fun live(context: Context): WidgetState? {
        val viewModel = liveViewModel(context) ?: return null
        return stateOf(
            viewModel.overview.value,
            viewModel.connectionStatus.value.health,
            viewModel.quotas.value
        )
    }

    /**
     * The flow a widget composition observes. A live app process follows the view model's own
     * flows; a cold process follows the persisted snapshot. Observing (rather than reading once)
     * keeps an already-running widget session fresh when Glance reuses it.
     */
    fun observe(context: Context): Flow<WidgetState?> {
        val viewModel = liveViewModel(context)
        if (viewModel != null) {
            return combine(
                viewModel.overview,
                viewModel.connectionStatus,
                viewModel.quotas
            ) { overview, status, quotas ->
                stateOf(overview, status.health, quotas)
            }
        }
        return context.widgetSnapshotDataStore.data
            .map { prefs -> prefs.toWidgetState() }
            .catch { emit(null) }
    }

    suspend fun load(context: Context): WidgetState? =
        live(context) ?: runCatching { readSnapshot(context) }.getOrNull()

    suspend fun save(context: Context, state: WidgetState) {
        context.widgetSnapshotDataStore.edit { prefs ->
            val requests = state.requests
            if (requests != null) {
                prefs[KEY_REQUESTS] = requests
            } else {
                prefs.remove(KEY_REQUESTS)
            }
            val successRate = state.successRate
            if (successRate != null) {
                prefs[KEY_SUCCESS_RATE] = successRate
            } else {
                prefs.remove(KEY_SUCCESS_RATE)
            }
            val ttftMs = state.ttftMs
            if (ttftMs != null) {
                prefs[KEY_TTFT_MS] = ttftMs
            } else {
                prefs.remove(KEY_TTFT_MS)
            }
            val cost = state.cost
            if (cost != null) {
                prefs[KEY_COST] = cost
            } else {
                prefs.remove(KEY_COST)
            }
            prefs[KEY_CONNECTED] = state.connected
            prefs[KEY_UPDATED_AT] = state.updatedAt
            val quotaAlert = state.quotaAlert
            if (quotaAlert != null) {
                prefs[KEY_QUOTA_ALERT_PROVIDER] = quotaAlert.providerLabel
                prefs[KEY_QUOTA_ALERT_RATIO] = quotaAlert.ratio
                prefs[KEY_QUOTA_ALERT_OTHERS] = quotaAlert.otherWindowCount
            } else {
                prefs.remove(KEY_QUOTA_ALERT_PROVIDER)
                prefs.remove(KEY_QUOTA_ALERT_RATIO)
                prefs.remove(KEY_QUOTA_ALERT_OTHERS)
            }
        }
    }

    private suspend fun readSnapshot(context: Context): WidgetState? {
        val prefs = context.widgetSnapshotDataStore.data.first()
        return prefs.toWidgetState()
    }

    private fun Preferences.toWidgetState(): WidgetState? {
        // updatedAt marks "a snapshot exists"; every metric may still be null while loading.
        val updatedAt = this[KEY_UPDATED_AT] ?: return null
        return WidgetState(
            requests = this[KEY_REQUESTS],
            successRate = this[KEY_SUCCESS_RATE],
            ttftMs = this[KEY_TTFT_MS],
            cost = this[KEY_COST],
            connected = this[KEY_CONNECTED] ?: false,
            updatedAt = updatedAt,
            quotaAlert = readQuotaAlert()
        )
    }

    private fun Preferences.readQuotaAlert(): WidgetQuotaAlertSummary? {
        val provider = this[KEY_QUOTA_ALERT_PROVIDER]?.takeIf { it.isNotBlank() } ?: return null
        val ratio = this[KEY_QUOTA_ALERT_RATIO] ?: return null
        return WidgetQuotaAlertSummary(
            providerLabel = provider,
            ratio = ratio,
            otherWindowCount = this[KEY_QUOTA_ALERT_OTHERS] ?: 0
        )
    }

    private fun liveViewModel(context: Context): AppViewModel? =
        (context.applicationContext as? MagpieApplication)?.existingAppViewModel

    private fun stateOf(
        overview: OverviewSnapshot?,
        health: ConnectionHealth,
        quotas: List<AccountQuota>
    ): WidgetState =
        WidgetState(
            requests = overview?.completedRequests,
            successRate = overview?.let {
                MetricCalculators.calculateSuccessRate(it.completedRequests, it.finalErrors)
            },
            ttftMs = overview?.let {
                MetricCalculators.calculateTtftMean(it.ttftSumMs, it.ttftSampleCount)
            },
            cost = overview?.cost,
            // CONNECTING still renders placeholders; only a real failure reads "未连接".
            connected = health == ConnectionHealth.CONNECTED ||
                health == ConnectionHealth.CONNECTING,
            updatedAt = System.currentTimeMillis(),
            quotaAlert = quotaAlertOf(quotas)
        )

    /**
     * One reading per window, already normalized by the same rule the quota page and the
     * notification gate use. The worst window titles the widget; the rest are only counted.
     */
    private fun quotaAlertOf(quotas: List<AccountQuota>): WidgetQuotaAlertSummary? {
        val warnings = NotificationText.quotaReadings(quotas)
            .filter { it.ratio >= QuotaAlertGate.THRESHOLD }
            .sortedWith(compareByDescending<QuotaReading> { it.ratio }.thenBy { it.providerId })
        val worst = warnings.firstOrNull() ?: return null
        return WidgetQuotaAlertSummary(
            providerLabel = worst.providerLabel,
            ratio = worst.ratio,
            otherWindowCount = warnings.size - 1
        )
    }

    private val KEY_REQUESTS = longPreferencesKey("widget_requests")
    private val KEY_SUCCESS_RATE = doublePreferencesKey("widget_success_rate")
    private val KEY_TTFT_MS = doublePreferencesKey("widget_ttft_ms")
    private val KEY_COST = doublePreferencesKey("widget_cost")
    private val KEY_CONNECTED = booleanPreferencesKey("widget_connected")
    private val KEY_UPDATED_AT = longPreferencesKey("widget_updated_at")
    private val KEY_QUOTA_ALERT_PROVIDER = stringPreferencesKey("widget_quota_alert_provider")
    private val KEY_QUOTA_ALERT_RATIO = doublePreferencesKey("widget_quota_alert_ratio")
    private val KEY_QUOTA_ALERT_OTHERS = intPreferencesKey("widget_quota_alert_others")
}
