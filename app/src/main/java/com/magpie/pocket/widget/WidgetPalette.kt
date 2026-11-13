package com.magpie.pocket.widget

import androidx.compose.ui.graphics.Color
import com.magpie.pocket.R
import com.magpie.pocket.core.design.DarkScheme
import com.magpie.pocket.core.design.LightScheme
import com.magpie.pocket.core.design.MagpieColorScheme

/**
 * One palette for every home-screen size, taken from the app's brand tokens. Widgets do not
 * read MaterialTheme; the background stays a shape drawable so API 24 gets the same 26dp
 * corners as Android S+.
 */
internal data class WidgetPalette(
    val background: Int,
    val quotaBackground: Int,
    val text: Color,
    val secondary: Color,
    val tertiary: Color,
    val accent: Color,
    val error: Color,
    val success: Color,
    val warning: Color,
    val divider: Color
)

internal fun widgetPalette(isDark: Boolean): WidgetPalette {
    val scheme: MagpieColorScheme = if (isDark) DarkScheme else LightScheme
    return WidgetPalette(
        background = if (isDark) {
            R.drawable.widget_mini_background_dark
        } else {
            R.drawable.widget_mini_background_light
        },
        quotaBackground = if (isDark) {
            R.drawable.widget_quota_background_dark
        } else {
            R.drawable.widget_quota_background_light
        },
        text = scheme.textPrimary,
        secondary = scheme.textSecondary,
        tertiary = scheme.textTertiary,
        accent = scheme.accent,
        error = scheme.error,
        success = scheme.success,
        warning = scheme.warning,
        divider = scheme.divider
    )
}
