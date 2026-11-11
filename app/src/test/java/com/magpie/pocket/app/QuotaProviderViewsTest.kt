package com.magpie.pocket.app

import com.magpie.pocket.core.data.LiveMagpieRepository
import com.magpie.pocket.core.model.Account
import com.magpie.pocket.core.model.AccountQuota
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
    fun duplicateProviderRowsMergeWindowsByNameAndKeepTheReportedValue() {
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

        assertEquals(listOf("Credits", "Extra credits"), view.windows.map { it.id })
        assertEquals(100.0, view.windows.first { it.id == "Credits" }.usedPercent ?: 0.0, 0.0001)
        val extraCredits = view.windows.first { it.id == "Extra credits" }
        assertEquals(67.5, extraCredits.usedPercent ?: 0.0, 0.0001)
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

    private fun fixtureText(name: String): String =
        javaClass.classLoader!!.getResource("upstream/$name")!!.readText()
}
