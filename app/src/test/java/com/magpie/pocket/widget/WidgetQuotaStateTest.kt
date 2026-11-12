package com.magpie.pocket.widget

import com.magpie.pocket.core.model.AccountQuota
import com.magpie.pocket.core.model.QuotaDisplayMode
import com.magpie.pocket.core.model.QuotaWindow
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetQuotaStateTest {

    @Test
    fun theWidgetReadsTheSameAverageAndCountsAsTheQuotaPage() {
        val state = widgetQuotaState(
            quotas = listOf(
                AccountQuota(
                    providerId = "provider",
                    known = true,
                    windows = listOf(
                        QuotaWindow(id = "half", unit = "credits", usedPercent = 50.0),
                        QuotaWindow(id = "nearly-spent", unit = "credits", usedPercent = 90.0),
                        QuotaWindow(id = "almost-full", unit = "credits", usedPercent = 10.0),
                        QuotaWindow(
                            id = "display-only",
                            unit = "",
                            display = "—",
                            text = true
                        )
                    )
                )
            ),
            displayMode = QuotaDisplayMode.REMAINING
        )

        // Remaining shares are 50, 10 and 90; the text-only window has no reading.
        assertEquals(50.0, state.averageRemaining ?: -1.0, 0.001)
        assertEquals(4, state.windowCount)
        assertEquals(1, state.belowThirty)
    }

    @Test
    fun theWidgetPrintsWholePercentsAndAnEmDashWithoutData() {
        assertEquals("42%", WidgetQuotaText.average(WidgetQuotaState(averageRemaining = 41.5)))
        assertEquals("—", WidgetQuotaText.average(null))
        assertEquals(0.415f, WidgetQuotaText.fraction(
            WidgetQuotaState(averageRemaining = 41.5)
        ), 0.0001f)
    }
}
