package com.magpie.pocket.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetricCalculatorsCostTest {

    @Test
    fun decodeTokensPerSecondConvertsMilliseconds() {
        // 600 tokens produced over 4000 ms is 150 tokens per second.
        assertEquals(150.0, MetricCalculators.decodeTokensPerSecond(600L, 4000L) ?: 0.0, 0.0001)
        assertEquals(1000.0, MetricCalculators.decodeTokensPerSecond(1000L, 1000L) ?: 0.0, 0.0001)
    }

    @Test
    fun decodeTokensPerSecondRejectsMissingOrNonPositiveSamples() {
        assertNull(MetricCalculators.decodeTokensPerSecond(null, 4000L))
        assertNull(MetricCalculators.decodeTokensPerSecond(600L, null))
        assertNull(MetricCalculators.decodeTokensPerSecond(600L, 0L))
        assertNull(MetricCalculators.decodeTokensPerSecond(600L, -5L))
    }

    @Test
    fun formatCostKeepsUnknownAndSubCentAmountsHonest() {
        assertEquals("—", MetricCalculators.formatCost(null))
        assertEquals("0.00", MetricCalculators.formatCost(0.0))
        assertEquals("12.35", MetricCalculators.formatCost(12.3456))
        assertEquals("<0.01", MetricCalculators.formatCost(0.0034))
        assertEquals("1235", MetricCalculators.formatCost(1234.56))
        assertEquals("12.3K", MetricCalculators.formatCost(12345.0))
    }

    @Test
    fun formatCreditsShowsUnknownAsDash() {
        assertEquals("—", MetricCalculators.formatCredits(null))
        assertEquals("0.00", MetricCalculators.formatCredits(0.0))
        assertEquals("48.50", MetricCalculators.formatCredits(48.5))
    }
}
