package com.magpie.pocket.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.magpie.pocket.app.MagpieApplication
import com.magpie.pocket.app.quotaOverview
import com.magpie.pocket.app.quotaProviderCards
import com.magpie.pocket.core.model.AccountQuota
import com.magpie.pocket.core.model.QuotaDisplayMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The quota figures a home-screen widget shows; a null average means no readable window. */
data class WidgetQuotaState(
    val averageRemaining: Double? = null,
    val windowCount: Int = 0,
    val belowThirty: Int = 0
)

/**
 * Reads the same overview the quota page reads, so the widget's average, window count and
 * below-30 count cannot drift from the app. Remaining is always the widget's direction.
 */
internal fun widgetQuotaState(
    quotas: List<AccountQuota>,
    displayMode: QuotaDisplayMode
): WidgetQuotaState {
    val overview = quotaOverview(
        quotaProviderCards(
            quotas = quotas,
            accounts = emptyList(),
            maskAccountNames = false,
            displayMode = displayMode
        ),
        displayMode
    )
    return WidgetQuotaState(
        averageRemaining = overview.averageRemaining,
        windowCount = overview.windowCount,
        belowThirty = overview.lowCount
    )
}

private val Context.widgetQuotaDataStore by
    preferencesDataStore(name = "magpie_widget_quota")

/**
 * Live app process or the last snapshot, mirroring [WidgetStateStore]'s two paths. A widget
 * broadcast must never construct [MagpieApplication.appViewModel]; that starts the network.
 */
object WidgetQuotaStateStore {

    fun live(context: Context): WidgetQuotaState? {
        val viewModel = (context.applicationContext as? MagpieApplication)?.existingAppViewModel
            ?: return null
        return widgetQuotaState(
            quotas = viewModel.quotas.value,
            displayMode = viewModel.displayPreferences.value.quotaDisplayMode
        )
    }

    fun observe(context: Context): Flow<WidgetQuotaState?> {
        val viewModel = (context.applicationContext as? MagpieApplication)?.existingAppViewModel
        if (viewModel != null) {
            return combine(viewModel.quotas, viewModel.displayPreferences) { quotas, preferences ->
                widgetQuotaState(quotas, preferences.quotaDisplayMode)
            }
        }
        return context.widgetQuotaDataStore.data
            .map { prefs -> prefs.toWidgetQuotaState() }
            .catch { emit(null) }
    }

    suspend fun load(context: Context): WidgetQuotaState? =
        live(context) ?: runCatching { readSnapshot(context) }.getOrNull()

    suspend fun save(context: Context, state: WidgetQuotaState) {
        context.widgetQuotaDataStore.edit { prefs ->
            prefs[KEY_HAS_SNAPSHOT] = true
            val average = state.averageRemaining
            if (average != null) {
                prefs[KEY_AVERAGE_REMAINING] = average
            } else {
                prefs.remove(KEY_AVERAGE_REMAINING)
            }
            prefs[KEY_WINDOW_COUNT] = state.windowCount
            prefs[KEY_BELOW_THIRTY] = state.belowThirty
        }
    }

    private suspend fun readSnapshot(context: Context): WidgetQuotaState? {
        val prefs = context.widgetQuotaDataStore.data.first()
        return prefs.toWidgetQuotaState()
    }

    private fun Preferences.toWidgetQuotaState(): WidgetQuotaState? {
        if (this[KEY_HAS_SNAPSHOT] != true) return null
        return WidgetQuotaState(
            averageRemaining = this[KEY_AVERAGE_REMAINING],
            windowCount = this[KEY_WINDOW_COUNT] ?: 0,
            belowThirty = this[KEY_BELOW_THIRTY] ?: 0
        )
    }

    private val KEY_HAS_SNAPSHOT = booleanPreferencesKey("widget_quota_has_snapshot")
    private val KEY_AVERAGE_REMAINING = doublePreferencesKey("widget_quota_average_remaining")
    private val KEY_WINDOW_COUNT = intPreferencesKey("widget_quota_window_count")
    private val KEY_BELOW_THIRTY = intPreferencesKey("widget_quota_below_thirty")
}
