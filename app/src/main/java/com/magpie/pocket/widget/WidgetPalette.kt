package com.magpie.pocket.widget

import androidx.compose.ui.graphics.Color
import com.magpie.pocket.R

/**
 * One fixed palette for every home-screen size. Widgets do not read MaterialTheme; the
 * background is a shape drawable so API 24 gets the same 24dp corners as Android S+.
 */
internal data class WidgetPalette(
    val background: Int,
    val text: Color,
    val secondary: Color,
    val tertiary: Color,
    val accent: Color,
    val error: Color,
    val success: Color
)

internal fun widgetPalette(isDark: Boolean): WidgetPalette = if (isDark) {
    WidgetPalette(
        background = R.drawable.widget_mini_background_dark,
        text = Color(0xFFE9E8F0),
        secondary = Color(0xFFA5ACBC),
        tertiary = Color(0xFF777F91),
        accent = Color(0xFF9A92EE),
        error = Color(0xFFFF969F),
        success = Color(0xFF7AD7B5)
    )
} else {
    WidgetPalette(
        background = R.drawable.widget_mini_background_light,
        text = Color(0xFF1C1A17),
        secondary = Color(0xFF6D7283),
        tertiary = Color(0xFF9297A6),
        accent = Color(0xFF6C5CE7),
        error = Color(0xFFBD4852),
        success = Color(0xFF237A61)
    )
}
