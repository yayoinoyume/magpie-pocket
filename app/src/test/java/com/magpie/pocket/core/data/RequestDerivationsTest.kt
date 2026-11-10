package com.magpie.pocket.core.data

import com.magpie.pocket.core.model.Attempt
import com.magpie.pocket.core.util.MetricCalculators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RequestDerivationsTest {

    @Test
    fun rerouteCountComesFromAttemptsWhenTheDetailHasThem() {
        assertEquals(0, RequestDerivations.rerouteCount(listOf(Attempt(1))))
        assertEquals(1, RequestDerivations.rerouteCount(listOf(Attempt(1), Attempt(2))))
        assertEquals(2, RequestDerivations.rerouteCount(listOf(Attempt(1), Attempt(2), Attempt(3))))
        // The attempts are the stronger evidence, so a stale retried flag cannot lower it.
        assertEquals(1, RequestDerivations.rerouteCount(listOf(Attempt(1), Attempt(2)), retried = false))
    }

    @Test
    fun rerouteCountFallsBackToTheLedgerRetriedFlag() {
        assertEquals(1, RequestDerivations.rerouteCount(emptyList(), retried = true))
        assertEquals(0, RequestDerivations.rerouteCount(emptyList(), retried = false))
    }

    @Test
    fun tokenUsageWithoutAnyCategoryIsUnknownNotZero() {
        assertNull(RequestDerivations.tokenUsage(null, null, null, null))
    }

    @Test
    fun tokenUsageKeepsMissingCategoriesMissing() {
        val usage = RequestDerivations.tokenUsage(
            inputUncached = 100L,
            output = null,
            cacheRead = null,
            cacheWrite = 0L
        )!!
        assertEquals(100L, usage.inputUncached)
        assertNull(usage.output)
        assertNull(usage.cacheRead)
        assertEquals(0L, usage.cacheWrite)
    }

    @Test
    fun missingTokenEvidenceFormatsAsADash() {
        assertEquals("—", MetricCalculators.formatTotalTokens(null))
        assertNull(MetricCalculators.calculateTotalTokensOrNull(null))
        val known = RequestDerivations.tokenUsage(1L, 2L, null, null)!!
        assertEquals("3", MetricCalculators.formatTotalTokens(known))
    }
}
