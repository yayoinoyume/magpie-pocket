package com.magpie.pocket.widget

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.magpie.pocket.core.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** What a configurable widget shows. A fixed size such as the mini bar ignores this. */
enum class WidgetContent(val label: String) {
    TODAY_OVERVIEW("今日概览"),
    CURRENT_ACTIVITY("当前活动"),
    QUOTA("额度"),
    TREND("趋势")
}

/**
 * The widget palette override. [SYSTEM] follows the app's ThemeMode (including the app's own
 * SYSTEM setting); [DARK] and [LIGHT] are explicit overrides.
 */
enum class WidgetTheme(val label: String) {
    SYSTEM("跟随 App"),
    DARK("深色"),
    LIGHT("浅色")
}

/** How widgets are repainted. Event-driven is the recommended default. */
enum class WidgetRefreshMode(val label: String) {
    EVENT_DRIVEN("事件驱动"),
    EVERY_15_MINUTES("每 15 分钟")
}

data class WidgetConfig(
    val content: WidgetContent = WidgetContent.TODAY_OVERVIEW,
    val theme: WidgetTheme = WidgetTheme.SYSTEM,
    val refresh: WidgetRefreshMode = WidgetRefreshMode.EVENT_DRIVEN
) {
    fun isDark(systemDark: Boolean, appThemeMode: ThemeMode): Boolean =
        WidgetThemeSource.isDark(theme, appThemeMode, systemDark)
}

private val Context.widgetConfigDataStore by preferencesDataStore(name = "widget_config")

/**
 * One global config file for every home-screen size. A missing or unknown stored value reads
 * as the default, so a downgrade never leaves a widget unrenderable.
 */
object WidgetConfigStore {

    fun observe(context: Context): Flow<WidgetConfig> =
        context.widgetConfigDataStore.data
            .map { it.toWidgetConfig() }
            .catch { emit(WidgetConfig()) }

    suspend fun load(context: Context): WidgetConfig =
        runCatching { context.widgetConfigDataStore.data.first().toWidgetConfig() }
            .getOrElse { WidgetConfig() }

    suspend fun save(context: Context, config: WidgetConfig) {
        context.widgetConfigDataStore.edit { prefs ->
            prefs[KEY_CONTENT] = config.content.name
            prefs[KEY_THEME] = config.theme.name
            prefs[KEY_REFRESH] = config.refresh.name
        }
    }

    private fun Preferences.toWidgetConfig(): WidgetConfig = WidgetConfig(
        content = WidgetContent.values().firstOrNull { it.name == this[KEY_CONTENT] }
            ?: WidgetContent.TODAY_OVERVIEW,
        theme = WidgetTheme.values().firstOrNull { it.name == this[KEY_THEME] }
            ?: WidgetTheme.SYSTEM,
        refresh = WidgetRefreshMode.values().firstOrNull { it.name == this[KEY_REFRESH] }
            ?: WidgetRefreshMode.EVENT_DRIVEN
    )

    private val KEY_CONTENT = stringPreferencesKey("widget_content")
    private val KEY_THEME = stringPreferencesKey("widget_theme")
    private val KEY_REFRESH = stringPreferencesKey("widget_refresh")
}
