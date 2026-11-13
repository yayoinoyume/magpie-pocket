package com.magpie.pocket.widget

import android.content.Context
import android.content.res.Configuration
import com.magpie.pocket.core.model.ThemeMode
import com.magpie.pocket.core.security.PreferencesManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The widget config and the app theme mode needed to resolve one widget's dark mode. */
internal data class WidgetThemeState(
    val config: WidgetConfig = WidgetConfig(),
    val appThemeMode: ThemeMode = ThemeMode.SYSTEM
) {
    fun isDark(systemDark: Boolean): Boolean = config.isDark(systemDark, appThemeMode)
}

/**
 * The app ThemeMode is the only theme source. A widget's SYSTEM override follows it; DARK and
 * LIGHT stay explicit overrides. Reading DataStore never builds the ViewModel or the network.
 */
internal object WidgetThemeSource {

    /**
     * Pure resolution shared by the widgets, the config preview and the notification tints.
     * [ThemeMode.SYSTEM] is the only branch that reads the phone's night mode.
     */
    fun isDark(theme: WidgetTheme, appThemeMode: ThemeMode, systemDark: Boolean): Boolean =
        when (theme) {
            WidgetTheme.SYSTEM -> when (appThemeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            WidgetTheme.DARK -> true
            WidgetTheme.LIGHT -> false
        }

    fun systemDark(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    fun observeAppThemeMode(context: Context): Flow<ThemeMode> =
        PreferencesManager(context.applicationContext).displayPreferencesFlow
            .map { it.themeMode }
            .catch { emit(ThemeMode.SYSTEM) }

    suspend fun loadAppThemeMode(context: Context): ThemeMode =
        runCatching {
            PreferencesManager(context.applicationContext).displayPreferencesFlow.first().themeMode
        }.getOrElse { ThemeMode.SYSTEM }

    fun observe(context: Context): Flow<WidgetThemeState> =
        combine(WidgetConfigStore.observe(context), observeAppThemeMode(context)) { config, mode ->
            WidgetThemeState(config = config, appThemeMode = mode)
        }

    suspend fun load(context: Context): WidgetThemeState = WidgetThemeState(
        config = WidgetConfigStore.load(context),
        appThemeMode = loadAppThemeMode(context)
    )
}
