package com.magpie.pocket.feature.usage

import com.magpie.pocket.core.model.AccountQuotaView
import com.magpie.pocket.core.model.DailyTrendPoint
import com.magpie.pocket.core.model.Period
import com.magpie.pocket.core.model.QuotaWindow
import com.magpie.pocket.core.model.UsageSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class UsageSubtitleTest {
    @Test
    fun theUsageSubtitleNamesThePeriodAndItsRequests() {
        val usage = UsageSnapshot(
            dailyTrends = listOf(
                DailyTrendPoint(dayLabel = "11-10", completedRequests = 1000),
                DailyTrendPoint(dayLabel = "11-11", completedRequests = 284)
            )
        )
        assertEquals("近 7 天 · 1284 次请求", usageSubtitle(Period.SEVEN_DAYS, usage))
    }

    @Test
    fun aPeriodWithoutBucketsNamesItselfAlone() {
        // A missing answer must not read as a true zero.
        assertEquals("今日", usageSubtitle(Period.TODAY, null))
        assertEquals("全部", usageSubtitle(Period.ALL, UsageSnapshot()))
    }

    @Test
    fun theQuotaSubtitleCountsTheProvidersThatReportedAWindow() {
        val quotas = listOf(provider(windows = 2), provider(windows = 1), provider(windows = 0))
        assertEquals("实时快照 · 2 个供应商", quotaSubtitle(quotas))
    }

    @Test
    fun aQuotaPageWithoutProvidersSaysSnapshotOnly() {
        assertEquals("实时快照", quotaSubtitle(emptyList()))
    }

    private fun provider(windows: Int) = AccountQuotaView(
        providerId = "p" + windows,
        label = "provider" + windows,
        known = true,
        tightestWindow = null,
        ratio = null,
        windows = List(windows) { index -> QuotaWindow(id = "w" + index, unit = "credits") }
    )
}
