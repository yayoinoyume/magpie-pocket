package com.magpie.pocket.widget

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetMiniTextTest {

    @Test
    fun theMiniBarPrintsWholePercentsAndOneDecimalOtherwise() {
        // WidgetOverview prints the same function, so both sizes must agree on these strings.
        assertEquals("100%", WidgetMiniText.successRate(WidgetState(successRate = 1.0)))
        assertEquals("99.5%", WidgetMiniText.successRate(WidgetState(successRate = 0.995)))
        assertEquals("0%", WidgetMiniText.successRate(WidgetState(successRate = 0.0)))
    }

    @Test
    fun anUnmeasuredSuccessRateStaysAnEmDash() {
        assertEquals("—", WidgetMiniText.successRate(null))
        assertEquals("—", WidgetMiniText.successRate(WidgetState()))
    }

    @Test
    fun requestsStayTheFullCountWithoutKOrMAbbreviations() {
        assertEquals("123", WidgetMiniText.requests(WidgetState(requests = 123)))
        assertEquals("12345", WidgetMiniText.requests(WidgetState(requests = 12_345)))
    }

    @Test
    fun anUnmeasuredRequestCountStaysAnEmDash() {
        assertEquals("—", WidgetMiniText.requests(null))
        assertEquals("—", WidgetMiniText.requests(WidgetState()))
    }
}
