package com.magpie.pocket.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale
import java.util.TimeZone

class RequestTimestampsTest {
    private val shanghai = TimeZone.getTimeZone("Asia/Shanghai")
    private val newYork = TimeZone.getTimeZone("America/New_York")

    @Test
    fun zuluAndNineFractionDigitsAreLocalized() {
        val raw = "2026-10-22T12:36:54.619611001Z"
        assertEquals("20:36:54", RequestTimestamps.formatTime(raw, shanghai, Locale.US))
        assertEquals(
            "2026-10-22 20:36:54 · UTC+08:00 · Asia/Shanghai",
            RequestTimestamps.formatFullLocal(raw, shanghai, Locale.US)
        )
    }

    @Test
    fun explicitPositiveOffsetIsNotDoubleShifted() {
        val raw = "2026-10-22T20:36:54.619+08:00"
        assertEquals("20:36:54", RequestTimestamps.formatTime(raw, shanghai, Locale.US))
        assertEquals(
            "2026-10-22 20:36:54 · UTC+08:00 · Asia/Shanghai",
            RequestTimestamps.formatFullLocal(raw, shanghai, Locale.US)
        )
    }

    @Test
    fun negativeOffsetConvertsToDeviceZone() {
        val raw = "2026-10-22T08:36:54-04:00"
        assertEquals("20:36:54", RequestTimestamps.formatTime(raw, shanghai, Locale.US))
    }

    @Test
    fun crossMidnightRollsDateForward() {
        val raw = "2026-10-22T17:30:00Z"
        assertEquals(
            "2026-10-23 01:30:00 · UTC+08:00 · Asia/Shanghai",
            RequestTimestamps.formatFullLocal(raw, shanghai, Locale.US)
        )
    }

    @Test
    fun sameInstantFromDifferentOffsetsFormatsIdentically() {
        val zulu = "2026-10-22T12:36:54Z"
        val offset = "2026-10-22T20:36:54+08:00"
        assertEquals(
            RequestTimestamps.formatFullLocal(zulu, shanghai, Locale.US),
            RequestTimestamps.formatFullLocal(offset, shanghai, Locale.US)
        )
    }

    @Test
    fun timezoneLessLegacyDateUsesDeviceZone() {
        assertEquals(
            "2026-10-22 12:36:54 · UTC+08:00 · Asia/Shanghai",
            RequestTimestamps.formatFullLocal("2026-10-22 12:36:54", shanghai, Locale.US)
        )
        assertEquals("2026-10-22 12:36:54", RequestTimestamps.formatFullLocal("2026-10-22 12:36:54", newYork, Locale.US).substringBefore(" ·"))
    }

    @Test
    fun timeOnlyKeepsClockAndInvalidIsEmDash() {
        assertEquals("12:36:54", RequestTimestamps.formatTime("12:36:54", shanghai, Locale.US))
        assertEquals("—", RequestTimestamps.formatTime("not-a-date", shanghai, Locale.US))
        assertEquals("—", RequestTimestamps.formatTime("2026-13-40T25:61:61Z", shanghai, Locale.US))
        assertNull(RequestTimestamps.parseMillis("2026-02-30T00:00:00Z", shanghai))
    }

    @Test
    fun quotaResetTimesReadAsDistanceThenDate() {
        val now = RequestTimestamps.parseMillis("2026-11-11T00:00:00Z", shanghai)!!
        fun reset(raw: String) = RequestTimestamps.formatResetAt(raw, now, shanghai, Locale.US)

        assertEquals("3 小时后", reset("2026-11-11T03:00:00Z"))
        assertEquals("1 小时后", reset("2026-11-11T00:30:00Z"))
        assertEquals("1 天后", reset("2026-11-12T00:00:00Z"))
        assertEquals("3 天后", reset("2026-11-14T00:00:00Z"))
        assertEquals("11月16日", reset("2026-11-16T00:00:00Z"))
        // A deadline that already passed shows its date rather than counting backwards.
        assertEquals("11月10日", reset("2026-11-10T00:00:00Z"))
        assertEquals("not-a-date", reset("not-a-date"))
        assertNull(RequestTimestamps.formatResetAt(null, now, shanghai, Locale.US))
        assertNull(RequestTimestamps.formatResetAt("   ", now, shanghai, Locale.US))
    }

    @Test
    fun utcNowIsTrulyUtc() {
        val value = RequestTimestamps.nowUtcIso(0L)
        assertEquals("1970-01-01T00:00:00.000Z", value)
        assertEquals(0L, RequestTimestamps.parseMillis(value, shanghai))
    }
}
