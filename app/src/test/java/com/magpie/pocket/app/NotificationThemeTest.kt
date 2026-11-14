package com.magpie.pocket.app

import com.magpie.pocket.core.design.DarkScheme
import com.magpie.pocket.core.design.LightScheme
import com.magpie.pocket.core.model.ThemeMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationThemeTest {

    @Test
    fun systemThemeFollowsSystemDark() {
        assertTrue(NotificationTheme.isDark(ThemeMode.SYSTEM, systemDark = true))
        assertFalse(NotificationTheme.isDark(ThemeMode.SYSTEM, systemDark = false))
    }

    @Test
    fun lightThemeStaysLightRegardlessOfSystemDark() {
        assertFalse(NotificationTheme.isDark(ThemeMode.LIGHT, systemDark = true))
        assertFalse(NotificationTheme.isDark(ThemeMode.LIGHT, systemDark = false))
    }

    @Test
    fun darkThemeStaysDarkRegardlessOfSystemDark() {
        assertTrue(NotificationTheme.isDark(ThemeMode.DARK, systemDark = true))
        assertTrue(NotificationTheme.isDark(ThemeMode.DARK, systemDark = false))
    }

    @Test
    fun schemeMatchesTheResolvedTheme() {
        assertSame(DarkScheme, NotificationTheme.scheme(ThemeMode.SYSTEM, systemDark = true))
        assertSame(LightScheme, NotificationTheme.scheme(ThemeMode.SYSTEM, systemDark = false))
        assertSame(LightScheme, NotificationTheme.scheme(ThemeMode.LIGHT, systemDark = true))
        assertSame(DarkScheme, NotificationTheme.scheme(ThemeMode.DARK, systemDark = false))
    }
}
