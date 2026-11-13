package com.magpie.pocket.widget

import com.magpie.pocket.core.model.ThemeMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetThemeTest {

    @Test
    fun theSystemOverrideFollowsTheAppThemeMode() {
        val follow = WidgetConfig(theme = WidgetTheme.SYSTEM)

        assertFalse(follow.isDark(systemDark = false, appThemeMode = ThemeMode.SYSTEM))
        assertTrue(follow.isDark(systemDark = true, appThemeMode = ThemeMode.SYSTEM))
        assertFalse(follow.isDark(systemDark = true, appThemeMode = ThemeMode.LIGHT))
        assertTrue(follow.isDark(systemDark = false, appThemeMode = ThemeMode.DARK))
    }

    @Test
    fun explicitWidgetOverridesWinOverTheAppThemeMode() {
        val dark = WidgetConfig(theme = WidgetTheme.DARK)
        val light = WidgetConfig(theme = WidgetTheme.LIGHT)

        assertTrue(dark.isDark(systemDark = false, appThemeMode = ThemeMode.LIGHT))
        assertFalse(light.isDark(systemDark = true, appThemeMode = ThemeMode.DARK))
    }

    @Test
    fun themeStateReadsTheCollectedSystemDarkValue() {
        val systemMode = WidgetThemeState(
            config = WidgetConfig(theme = WidgetTheme.SYSTEM),
            appThemeMode = ThemeMode.SYSTEM,
            systemDark = true
        )

        assertTrue(systemMode.isDark())
        assertFalse(systemMode.copy(systemDark = false).isDark())
        assertFalse(systemMode.copy(appThemeMode = ThemeMode.LIGHT).isDark())
        assertTrue(systemMode.copy(appThemeMode = ThemeMode.DARK, systemDark = false).isDark())
    }

    @Test
    fun systemIsTheDefaultAndReadsAsFollowApp() {
        assertEquals(WidgetTheme.SYSTEM, WidgetConfig().theme)
        assertEquals("跟随 App", WidgetTheme.SYSTEM.label)
    }
}
