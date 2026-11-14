package com.magpie.pocket.app

import android.content.Context
import android.content.res.Configuration
import com.magpie.pocket.core.design.DarkScheme
import com.magpie.pocket.core.design.LightScheme
import com.magpie.pocket.core.design.MagpieColorScheme
import com.magpie.pocket.core.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Resolves notification tints from the app ThemeMode and the phone's night mode. Notifications
 * do not depend on widget configuration.
 */
internal object NotificationTheme {

    /**
     * The phone's night mode as process state, so a running foreground service recolors its
     * notification when the system light/dark switch happens. The Application seeds it on
     * start and on every configuration change.
     */
    internal val systemDarkFlow = MutableStateFlow(false)

    fun isDark(themeMode: ThemeMode, systemDark: Boolean): Boolean =
        when (themeMode) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }

    fun systemDark(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    /** Writes the current uiMode into [systemDarkFlow]; called from Application callbacks. */
    fun refreshSystemDark(context: Context) {
        systemDarkFlow.value = systemDark(context)
    }

    fun scheme(themeMode: ThemeMode, systemDark: Boolean): MagpieColorScheme =
        if (isDark(themeMode, systemDark)) DarkScheme else LightScheme
}
