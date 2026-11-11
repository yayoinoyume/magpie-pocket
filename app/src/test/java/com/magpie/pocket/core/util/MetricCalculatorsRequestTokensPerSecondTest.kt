package com.magpie.pocket.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MetricCalculatorsRequestTokensPerSecondTest {

    @Test
    fun requestTokensPerSecondMatchesRealSample() {
        // 365 output tokens over 5296 - 2052 = 3244 ms of decode time.
        assertEquals(
            112.5154,
            MetricCalculators.requestTokensPerSecond(365L, 5296L, 2052L) ?: 0.0,
            0.0001
        )
    }

    @Test
    fun requestTokensPerSecondRejectsMissingOrNonPositiveOutput() {
        assertNull(MetricCalculators.requestTokensPerSecond(null, 5296L, 2052L))
        assertNull(MetricCalculators.requestTokensPerSecond(0L, 5296L, 2052L))
        assertNull(MetricCalculators.requestTokensPerSecond(-1L, 5296L, 2052L))
    }

    @Test
    fun requestTokensPerSecondRejectsMissingOrNonPositiveDuration() {
        assertNull(MetricCalculators.requestTokensPerSecond(365L, null, 2052L))
        assertNull(MetricCalculators.requestTokensPerSecond(365L, 0L, 2052L))
        assertNull(MetricCalculators.requestTokensPerSecond(365L, -1L, 2052L))
    }

    @Test
    fun requestTokensPerSecondRejectsMissingTtft() {
        // 244 tokens in 68 ms would read as 3588 tok/s if missing TTFT were treated as zero.
        assertNull(MetricCalculators.requestTokensPerSecond(244L, 68L, null))
        assertNull(MetricCalculators.requestTokensPerSecond(365L, 5296L, null))
    }

    @Test
    fun requestTokensPerSecondRejectsTtftAtOrBeyondDuration() {
        assertNull(MetricCalculators.requestTokensPerSecond(365L, 5296L, 5296L))
        assertNull(MetricCalculators.requestTokensPerSecond(365L, 5296L, 6000L))
    }

    @Test
    fun formatTokensPerSecondRoundsAndHidesUnknown() {
        assertEquals("113 tok/s", MetricCalculators.formatTokensPerSecond(112.6))
        assertEquals("112 tok/s", MetricCalculators.formatTokensPerSecond(112.4))
        assertNull(MetricCalculators.formatTokensPerSecond(null))
        assertNull(MetricCalculators.formatTokensPerSecond(0.0))
        assertNull(MetricCalculators.formatTokensPerSecond(-1.0))
    }
}
