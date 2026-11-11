package com.magpie.pocket.core.data

import com.magpie.pocket.core.model.Account
import com.magpie.pocket.core.model.AccountQuota
import com.magpie.pocket.core.model.UsageBreakdownItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountSourcesTest {

    @Test
    fun groupRowsWinEveryIdTheUsageAndQuotaRowsAlsoName() {
        val groups = listOf(Account("account-a", "vendor", "Group A", "AVAILABLE"))
        val usage = listOf(
            UsageBreakdownItem(id = "account-a", label = "Usage A", provider = "vendor", calls = 3L),
            UsageBreakdownItem(id = "account-b", label = "Account B", provider = "other")
        )
        val quotas = listOf(
            AccountQuota(accountId = "account-a", providerId = "vendor", known = true),
            AccountQuota(accountId = "account-c", providerId = "other", known = true)
        )

        val merged = mergeAccountSources(groups, usage, quotas)

        assertEquals(3, merged.size)
        val fromGroups = merged.first { it.id == "account-a" }
        assertEquals("vendor", fromGroups.providerId)
        assertEquals("Group A", fromGroups.alias)
        assertEquals("AVAILABLE", fromGroups.availability)
        assertFalse(fromGroups.fromUsage)

        val fromUsage = merged.first { it.id == "account-b" }
        assertEquals("other", fromUsage.providerId)
        assertEquals("Account B", fromUsage.alias)
        assertEquals("UNKNOWN", fromUsage.availability)
        assertTrue(fromUsage.fromUsage)

        val fromQuota = merged.first { it.id == "account-c" }
        assertEquals("other", fromQuota.providerId)
        assertEquals("account-c", fromQuota.alias)
        assertEquals("UNKNOWN", fromQuota.availability)
        assertFalse(fromQuota.fromUsage)
    }

    @Test
    fun usageRowsKeepTheirProviderAndFallBackToTheirLabelForAnId() {
        val usage = listOf(
            UsageBreakdownItem(id = null, label = "account-d", provider = "vendor", calls = 2L),
            UsageBreakdownItem(id = "account-e", label = "Account E", provider = null)
        )

        val merged = mergeAccountSources(emptyList(), usage, emptyList())

        assertEquals(2, merged.size)
        val byLabel = merged.first { it.id == "account-d" }
        assertEquals("vendor", byLabel.providerId)
        assertTrue(byLabel.fromUsage)
        val providerless = merged.first { it.id == "account-e" }
        assertEquals("unknown", providerless.providerId)
        assertEquals("Account E", providerless.alias)
    }

    @Test
    fun noSourceMeansNoAccounts() {
        assertTrue(mergeAccountSources(emptyList(), emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun aQuotaAnswerThatNamesNoAccountAddsNoRow() {
        // A quota row that names no account adds no account, whatever its provider is.
        val quotas = listOf(
            AccountQuota(providerId = "opencode-go", displayName = "OpenCode Go", known = true),
            AccountQuota(accountId = "account-f", providerId = "vendor", known = true)
        )

        val merged = mergeAccountSources(emptyList(), emptyList(), quotas)

        assertEquals(1, merged.size)
        assertEquals("account-f", merged.single().id)
        assertEquals("vendor", merged.single().providerId)
    }
}
