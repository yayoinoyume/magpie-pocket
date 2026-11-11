package com.magpie.pocket.app

import com.magpie.pocket.core.data.LiveMagpieRepository
import com.magpie.pocket.core.data.mergeAccountSources
import com.magpie.pocket.core.model.Account
import com.magpie.pocket.core.model.AccountQuota
import com.magpie.pocket.core.model.AccountQuotaView
import com.magpie.pocket.core.model.QuotaWindow
import com.magpie.pocket.core.network.MagpieApiClient
import com.magpie.pocket.core.util.MetricCalculators
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Quotas are a provider dimension. quotas-real.json is the deployed answer captured from the
 * owner's gateway: 19 rows, a few of which also name their account (a phone, an email, a key),
 * and windows that only carry a used percentage.
 */
class QuotaProviderViewsTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/api/usage/quotas" -> MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(fixtureText("quotas-real.json"))
                else -> MockResponse().setResponseCode(404)
            }
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun realGatewayAnswerMergesEveryDuplicateProviderAndDropsEmptyOnes() = runBlocking {
        val rows = realGatewayQuotas()
        assertEquals(19, rows.size)
        // Some providers name an account, some do not; the provider is the row identity either way.
        assertEquals(13, rows.count { it.accountId != null })
        assertNull(rows.first { it.providerId == "opencode-go" }.accountId)
        assertEquals("18009276201", rows.first { it.providerId == "workbuddy" }.accountId)

        val views = providerQuotaViews(rows, accounts = emptyList(), maskAccountNames = true)

        // Ten providers in the sample; yayoiapi answered no window and has nothing to show.
        assertEquals(10, rows.map { it.providerId }.distinct().size)
        assertEquals(9, views.size)
        assertEquals(1, views.count { it.providerId == "stepfun-cn" })
        assertEquals(1, views.count { it.providerId == "workbuddy" })
        assertEquals(1, views.count { it.providerId == "mistral" })
        assertNull(views.firstOrNull { it.providerId == "yayoiapi" })

        // Worst first, so the ring and the first row name the provider closest to its limit.
        assertEquals("workbuddy", views.first().providerId)
        assertTrue(
            views.zipWithNext().all { (worse, better) ->
                (worse.ratio ?: -1.0) >= (better.ratio ?: -1.0)
            }
        )
        // workbuddy is four accounts with four windows each: nothing is dropped, names repeat.
        val workbuddy = views.first { it.providerId == "workbuddy" }
        assertEquals(16, workbuddy.windows.size)
        assertEquals(listOf("Credits", "Credits #2", "Credits #3", "Credits #4"), workbuddy.windows.take(4).map { it.id })
    }

    @Test
    fun opencodeGoKeepsItsPercentagesAndItsProviderName() = runBlocking {
        val view = providerQuotaViews(realGatewayQuotas(), emptyList(), maskAccountNames = true)
            .first { it.providerId == "opencode-go" }

        assertEquals("OpenCode Go", view.label)
        assertFalse(view.label.contains("***"))
        assertEquals(listOf("5 hours", "7 days", "Month"), view.windows.map { it.id })

        val week = view.windows.first { it.id == "7 days" }
        assertEquals(0.13, MetricCalculators.calculateQuotaUsedRatio(week) ?: 0.0, 0.0001)
        assertEquals("2026-11-16T00:00:00Z", week.resetsAt)
        // Month (23%) is the window that decides the provider, not the first window (0%).
        assertEquals("Month", view.tightestWindow?.id)
        assertEquals(0.23, view.ratio ?: 0.0, 0.0001)
    }

    @Test
    fun maskingHidesTheNamedAccountAndNeverTheProviderName() = runBlocking {
        val rows = realGatewayQuotas()
        val masked = providerQuotaViews(rows, emptyList(), maskAccountNames = true)
            .first { it.providerId == "workbuddy" }
        val unmasked = providerQuotaViews(rows, emptyList(), maskAccountNames = false)
            .first { it.providerId == "workbuddy" }

        // The provider name stays readable; only the phone number it named is masked.
        assertTrue(masked.label.startsWith("WorkBuddy · "))
        assertFalse(masked.label.contains("18009276201"))
        assertTrue(unmasked.label.contains("18009276201"))

        val providerOnly = providerQuotaViews(rows, emptyList(), maskAccountNames = true)
            .first { it.providerId == "opencode-go" }
        assertEquals("OpenCode Go", providerOnly.label)
        assertFalse(providerOnly.label.contains("***"))
    }

    @Test
    fun theRealGatewayAnswerNeverTurnsAProviderNameIntoAnAccount() = runBlocking {
        val merged = mergeAccountSources(
            groups = emptyList(),
            usage = emptyList(),
            quotas = realGatewayQuotas()
        )

        // Only the 13 rows that name a user become accounts; the six provider-only rows add none.
        assertEquals(13, merged.size)
        assertTrue(merged.none { it.alias == "OpenCode Go" })
        assertTrue(merged.none { it.id == "opencode-go" })
    }

    @Test
    fun aNamedAccountIsMaskedThroughItsAliasWhenTheGroupListKnowsIt() {
        val rows = listOf(
            AccountQuota(
                providerId = "workbuddy-ai",
                displayName = "WorkBuddy AI",
                known = true,
                windows = listOf(percentWindow("Credits", 96.99))
            )
        )
        val named = rows.map { it.copy(accountId = "workbuddy-primary") }
        val accounts = listOf(
            Account(
                id = "workbuddy-primary",
                providerId = "workbuddy-ai",
                alias = "workbuddy-primary"
            )
        )
        assertEquals(
            "WorkBuddy AI · workbuddy-primary",
            providerQuotaViews(named, accounts, maskAccountNames = false).single().label
        )
        assertEquals(
            "WorkBuddy AI · wor***ary",
            providerQuotaViews(named, accounts, maskAccountNames = true).single().label
        )
    }

    @Test
    fun duplicateProviderWindowsKeepEveryValueAndGetNumberedTitles() {
        val rows = listOf(
            AccountQuota(
                providerId = "workbuddy",
                displayName = "WorkBuddy",
                known = true,
                windows = listOf(percentWindow("Credits", 100.0), blankWindow("Extra credits"))
            ),
            AccountQuota(
                providerId = "workbuddy",
                displayName = "WorkBuddy",
                known = true,
                windows = listOf(percentWindow("Credits", 0.0), percentWindow("Extra credits", 67.5))
            )
        )

        val view = providerQuotaViews(rows, emptyList(), maskAccountNames = false).single()

        // Same name, same provider: both windows stay, the repeat is numbered instead of merged.
        assertEquals(
            listOf("Credits", "Credits #2", "Extra credits", "Extra credits #2"),
            view.windows.map { it.id }
        )
        assertEquals(100.0, view.windows.first { it.id == "Credits" }.usedPercent ?: 0.0, 0.0001)
        assertEquals(0.0, view.windows.first { it.id == "Credits #2" }.usedPercent ?: -1.0, 0.0001)
        assertEquals(67.5, view.windows.first { it.id == "Extra credits #2" }.usedPercent ?: 0.0, 0.0001)
        assertEquals(1.0, view.ratio ?: 0.0, 0.0001)
    }

    @Test
    fun aProviderThatReportedNoWindowNeverReachesThePage() {
        val rows = listOf(
            AccountQuota(providerId = "yayoiapi", displayName = "YAYOIAPI", known = false)
        )

        assertTrue(providerQuotaViews(rows, emptyList(), maskAccountNames = true).isEmpty())
    }

    @Test
    fun theLiveAnswerReadsBothWaysOffTheOneUsedShare() = runBlocking {
        val views = providerQuotaViews(realGatewayQuotas(), emptyList(), maskAccountNames = true)

        // WorkBuddy's deciding window is the token window that went over its allowance:
        // 200,029,738 / 200,000,000, read as a full 100% and nothing left.
        val workbuddy = views.first { it.providerId == "workbuddy" }
        assertEquals(100.0, workbuddy.usedPercent ?: 0.0, 0.0001)
        assertEquals(0.0, workbuddy.remainingPercent ?: -1.0, 0.0001)
        assertEquals("100.0%", percentText(workbuddy.usedPercent))
        assertEquals("0.0%", percentText(workbuddy.remainingPercent))
        assertEquals(200029738.0, workbuddy.usedAmount ?: 0.0, 0.001)
        assertEquals(0.0, workbuddy.remainingAmount ?: -1.0, 0.001)
        // The over-spent window reports no unit, so the row prints no unit instead of guessing.
        assertNull(workbuddy.unitLabel)

        // Qoder spent 2500 of 2600 credits: 96.2% used, 3.8% left.
        val qoder = views.first { it.providerId == "qoder-cn" }
        assertEquals(96.1538, qoder.usedPercent ?: 0.0, 0.001)
        assertEquals(3.8461, qoder.remainingPercent ?: 0.0, 0.001)
        assertEquals("96.2%", percentText(qoder.usedPercent))
        assertEquals("3.8%", percentText(qoder.remainingPercent))
        assertEquals(2500.0, qoder.usedAmount ?: 0.0, 0.0001)
        assertEquals(100.0, qoder.remainingAmount ?: 0.0, 0.0001)
        assertEquals("credits", qoder.unitLabel)

        // OpenCode Go reports shares only: 23% used, 77% left, and no absolute pair either way.
        val opencode = views.first { it.providerId == "opencode-go" }
        assertEquals(23.0, opencode.usedPercent ?: 0.0, 0.0001)
        assertEquals(77.0, opencode.remainingPercent ?: 0.0, 0.0001)
        assertEquals("23.0%", percentText(opencode.usedPercent))
        assertEquals("77.0%", percentText(opencode.remainingPercent))
        assertNull(opencode.usedAmount)
        assertNull(opencode.remainingAmount)
        assertNull(opencode.unitLabel)

        // Every row is one number read two ways, never two independent readings.
        views.forEach { view ->
            val used = view.usedPercent
            val remaining = view.remainingPercent
            if (used != null && remaining != null) {
                assertEquals(100.0, used + remaining, 0.0001)
            }
        }
    }

    @Test
    fun bothReadingsKeepTheSameWorstProviderFirst() = runBlocking {
        val views = providerQuotaViews(realGatewayQuotas(), emptyList(), maskAccountNames = true)
        val order = views.map { it.providerId }

        // The list is ordered by the used share, so the spent provider is first in both modes.
        assertEquals("workbuddy", order.first())
        assertTrue(views.zipWithNext().all { (worse, better) ->
            (worse.usedPercent ?: -1.0) >= (better.usedPercent ?: -1.0)
        })
        // Ordering by what is left would bury the same provider last; that is the bug this keeps.
        assertNotEquals(
            views.sortedWith(
                compareByDescending<AccountQuotaView> { it.remainingPercent ?: -1.0 }
                    .thenBy { it.providerId }
            ).map { it.providerId },
            order
        )
    }

    @Test
    fun aDisplayOnlyWindowPrintsItsTextAndNeverAPercentage() {
        val rows = listOf(
            AccountQuota(
                providerId = "balance-vendor",
                displayName = "Balance vendor",
                known = true,
                windows = listOf(
                    QuotaWindow(
                        id = "Balance",
                        unit = "CNY",
                        display = "¥3.57",
                        text = true
                    )
                )
            )
        )

        val view = providerQuotaViews(rows, emptyList(), maskAccountNames = false).single()

        // The answer is readable, it just has no reading: no share, no count, only its own text.
        assertTrue(view.known)
        assertNull(view.ratio)
        assertNull(view.usedPercent)
        assertNull(view.remainingPercent)
        assertNull(view.usedAmount)
        assertNull(view.remainingAmount)
        assertEquals("Balance", view.tightestWindow?.id)
        assertEquals("¥3.57", view.displayText)
    }

    @Test
    fun anUnlimitedOnlyProviderHasNoRatioRatherThanAFullBar() {
        val rows = listOf(
            AccountQuota(
                providerId = "vendor",
                displayName = "Vendor",
                known = false,
                windows = listOf(
                    QuotaWindow(id = "unlimited", unit = "", unlimited = true, known = false)
                )
            )
        )

        val view = providerQuotaViews(rows, emptyList(), maskAccountNames = false).single()

        assertNull(view.ratio)
        assertNull(view.tightestWindow)
        assertEquals("Vendor", view.label)
        // Neither reading invents a number for an allowance that has no limit.
        assertNull(view.usedPercent)
        assertNull(view.remainingPercent)
        assertNull(view.usedAmount)
        assertNull(view.remainingAmount)
        assertNull(view.displayText)
    }

    private suspend fun realGatewayQuotas(): List<AccountQuota> {
        val repo = LiveMagpieRepository(
            server.url("/").toString(),
            "quota-provider",
            MagpieApiClient()
        )
        return repo.quotas()
    }

    private fun percentWindow(id: String, used: Double) =
        QuotaWindow(id = id, unit = "", usedPercent = used)

    private fun blankWindow(id: String) = QuotaWindow(id = id, unit = "")

    /** What the row prints for a 0..100 share: the same one-decimal formatter the page uses. */
    private fun percentText(percent: Double?): String =
        MetricCalculators.formatPercent(percent?.let { it / 100.0 })

    private fun fixtureText(name: String): String =
        javaClass.classLoader!!.getResource("upstream/$name")!!.readText()
}
