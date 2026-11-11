package com.magpie.pocket.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The page prints the remaining share unless the owner said otherwise: a low remaining number is
 * the danger signal, and a build that never stored a preference must already read it that way.
 */
class QuotaDisplayModeTest {

    @Test
    fun theDefaultPreferenceIsTheRemainingShare() {
        assertEquals(QuotaDisplayMode.REMAINING, DisplayPreferences().quotaDisplayMode)
    }

    @Test
    fun aStoredCodeReadsBackAsItsModeAndUnknownValuesFallBack() {
        assertEquals(QuotaDisplayMode.USED, QuotaDisplayMode.fromCode(QuotaDisplayMode.USED.code))
        assertEquals(
            QuotaDisplayMode.REMAINING,
            QuotaDisplayMode.fromCode(QuotaDisplayMode.REMAINING.code)
        )
        // A newer build's code, a blank value and a missing value all read as the default.
        assertEquals(QuotaDisplayMode.REMAINING, QuotaDisplayMode.fromCode("something-new"))
        assertEquals(QuotaDisplayMode.REMAINING, QuotaDisplayMode.fromCode(""))
        assertEquals(QuotaDisplayMode.REMAINING, QuotaDisplayMode.fromCode(null))
    }
}
