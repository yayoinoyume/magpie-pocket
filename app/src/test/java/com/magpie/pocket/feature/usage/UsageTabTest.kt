package com.magpie.pocket.feature.usage

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageTabTest {
    @Test
    fun withoutAnAnchorTheUsageSubPageOpens() {
        assertEquals(UsageTab.USAGE, usageTabFor(null))
    }

    @Test
    fun theQuotaAnchorOpensTheQuotaSubPage() {
        assertEquals(UsageTab.QUOTA, usageTabFor("quota"))
    }

    @Test
    fun anAnchorNobodyOwnsFallsBackToUsage() {
        assertEquals(UsageTab.USAGE, usageTabFor("xxx"))
    }
}
