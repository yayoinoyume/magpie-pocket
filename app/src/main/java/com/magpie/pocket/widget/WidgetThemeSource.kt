package com.magpie.pocket.widget

import android.content.Context
import android.content.res.Configuration
import com.magpie.pocket.core.model.ThemeMode
import com.magpie.pocket.core.security.PreferencesManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The widget config, app ThemeMode and current system night mode for one paint. */
internal data class WidgetThemeState(
    val config: WidgetConfig = WidgetConfig(),
    val appThemeMode: ThemeMode = ThemeMode.SYSTEM,
    val systemDark: Boolean = false
) {
    fun isDark(): Boolean = config.isDark(systemDark, appThemeMode)
}

/**
 * The app ThemeMode is the only theme source. A widget's SYSTEM override follows it; DARK and
 * LIGHT stay explicit overrides. Reading DataStore never builds the ViewModel or the network.
 */
internal object WidgetThemeSource {

    /**
     * The phone's night mode as process state, so an already-composed widget repaints when the
     * launcher process survives a system light/dark switch. The app seeds it on start and on
     * every configuration change; [observe] also seeds it before the first emission.
     */
    internal val systemDarkFlow = MutableStateFlow(false)

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

    /** Writes the current uiMode into [systemDarkFlow]; called from Application callbacks. */
    fun refreshSystemDark(context: Context) {
        systemDarkFlow.value = systemDark(context)
    }

    fun observeAppThemeMode(context: Context): Flow<ThemeMode> =
        PreferencesManager(context.applicationContext).displayPreferencesFlow
            .map { it.themeMode }
            .catch { emit(ThemeMode.SYSTEM) }

    suspend fun loadAppThemeMode(context: Context): ThemeMode =
        runCatching {
            PreferencesManager(context.applicationContext).displayPreferencesFlow.first().themeMode
        }.getOrElse { ThemeMode.SYSTEM }

    fun observe(context: Context): Flow<WidgetThemeState> {
        refreshSystemDark(context)
        return combine(
            WidgetConfigStore.observe(context),
            observeAppThemeMode(context),
            systemDarkFlow
        ) { config, mode, dark ->
            WidgetThemeState(config = config, appThemeMode = mode, systemDark = dark)
        }
    }

    suspend fun load(context: Context): WidgetThemeState = WidgetThemeState(
        config = WidgetConfigStore.load(context),
        appThemeMode = loadAppThemeMode(context),
        systemDark = systemDark(context)
    )
}
