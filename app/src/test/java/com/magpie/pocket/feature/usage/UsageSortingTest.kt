package com.magpie.pocket.feature.usage

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageSortingTest {
    @Test
    fun everyCarriedKeyIsOffered() {
        val rows = listOf(
            row(name = "a", calls = 12, cost = 0.4, tokens = 900),
            row(name = "b", calls = 3, cost = 0.1, tokens = 200)
        )
        assertEquals(
            listOf(UsageSort.COST, UsageSort.TOKENS, UsageSort.CALLS),
            availableUsageSorts(rows, UsageSort.COST)
        )
    }

    @Test
    fun aKeyNoRowCarriesIsNotOffered() {
        // The models dimension reports tokens only; cost and calls chips stay disabled.
        val rows = listOf(row(name = "m1", tokens = 1200), row(name = "m2", tokens = 30))
        assertEquals(listOf(UsageSort.TOKENS), availableUsageSorts(rows, UsageSort.TOKENS))
    }

    @Test
    fun aDimensionWithoutAnyNumberKeepsTheDefault() {
        val rows = listOf(row(name = "a"), row(name = "b"))
        // The default is the dimension's own fallback, never an arbitrary first sort.
        assertEquals(listOf(UsageSort.CALLS), availableUsageSorts(rows, UsageSort.CALLS))
        assertEquals(listOf(UsageSort.COST), availableUsageSorts(rows, UsageSort.COST))
    }

    private fun row(
        name: String,
        calls: Long? = null,
        cost: Double? = null,
        tokens: Long? = null
    ) = UsageRow(name = name, calls = calls, cost = cost, tokens = tokens, errors = null)
}
