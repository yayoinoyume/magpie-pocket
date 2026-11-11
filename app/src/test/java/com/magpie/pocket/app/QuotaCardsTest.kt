package com.magpie.pocket.app

import com.magpie.pocket.core.data.LiveMagpieRepository
import com.magpie.pocket.core.model.AccountQuota
import com.magpie.pocket.core.model.QuotaDisplayMode
import com.magpie.pocket.core.model.QuotaWindow
import com.magpie.pocket.core.network.MagpieApiClient
import com.magpie.pocket.core.util.RequestTimestamps
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * quotas-real.json is the deployed gateway answer: 19 rows, 37 windows, four WorkBuddy accounts
 * and a balance-only StepFun row. The cards read that answer, not the design mock's invented
 * totals.
 */
class QuotaCardsTest {

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
    fun theCapturedAnswerKeepsPlanBalanceStartsAtAndLastServedAt() = runBlocking {
        val rows = realGatewayQuotas()

        val stepfunPlan = rows.first { it.providerId == "stepfun-cn" && it.windows.isNotEmpty() }
        assertEquals("Plus", stepfunPlan.plan)
        assertEquals("2026-11-19T09:00:40Z", stepfunPlan.until)
        val stepfunBalance = rows.first { it.providerId == "stepfun-cn" && it.balance != null }
        assertEquals("¥3.57", stepfunBalance.balance)

        val opencode = rows.first { it.providerId == "opencode-go" }
        assertEquals("2026-11-09T00:00:00Z", opencode.windows.first { it.id == "7 days" }.startsAt)
        assertNotNull(opencode.lastServedAt)
        assertNull(rows.first { it.providerId == "workbuddy" }.lastServedAt)

        assertEquals("2026-11-26T11:26:39Z", rows.first { it.providerId == "commandcode-plan" }.until)
    }

    @Test
    fun theCapturedAnswerBuildsProviderCardsAndKeepsTheBalanceOnlyProvider() = runBlocking {
        val cards = realCards(QuotaDisplayMode.REMAINING)

        assertEquals(10, cards.size)
        val workbuddy = cards.first { it.providerId == "workbuddy" }
        assertEquals("WorkBuddy", workbuddy.displayName)
        assertEquals("Free plan", workbuddy.plan)
        assertEquals(4, workbuddy.accountCount)
        assertEquals(16, workbuddy.windowCount)
        assertEquals(4, workbuddy.mergedWindows.size)
        val secondAccount = workbuddy.accounts.first { it.accountId == "153****8998" }
        // The page prints remaining by default, so an untouched window shows its whole
        // allowance as still available.
        assertEquals(
            "500 / 500 credits",
            secondAccount.windows.first { it.name == "Credits" }.countText
        )

        val yayoiapi = cards.first { it.providerId == "yayoiapi" }
        assertEquals("\$-53582.01", yayoiapi.balance)
        assertEquals(0, yayoiapi.windowCount)
        assertTrue(yayoiapi.accounts.isEmpty())
    }

    @Test
    fun theCapturedAnswerOverviewCountsEveryWindowAndReadsRemaining() = runBlocking {
        val overview = quotaOverview(realCards(QuotaDisplayMode.REMAINING), QuotaDisplayMode.REMAINING)

        assertEquals(37, overview.windowCount)
        // The mock in the design prints 36%; the captured answer's real average is 41.5%.
        assertEquals(41.501, overview.averageRemaining ?: 0.0, 0.01)
        // The same reading also finds 18 windows under 30%, not the mock's 17.
        assertEquals(18, overview.lowCount)
        assertEquals("WorkBuddy", overview.lowest?.first)
        assertEquals(0.0, overview.lowest?.second ?: -1.0, 0.01)
        assertEquals("StepFun (China)", overview.highest?.first)
        assertEquals(99.893, overview.highest?.second ?: 0.0, 0.01)
    }

    @Test
    fun workBuddyMergedWindowsAddAmountAndAllowancePerWindowName() = runBlocking {
        // The sums are used amounts, so this test reads them in the used direction, the one
        // that prints the sum as it was added up.
        val workbuddy = realCards(QuotaDisplayMode.USED)
            .first { it.providerId == "workbuddy" }
        val merged = workbuddy.mergedWindows.associateBy { it.name }

        assertEquals(4, merged.size)
        assertEquals("1,500 / 2,000 credits", merged.getValue("Credits").countText)
        assertEquals("14,463.1 / 16,300 credits", merged.getValue("Extra credits").countText)
        assertEquals("15,963.1 / 18,300 credits", merged.getValue("Total credits").countText)
        assertEquals("670.7M / 800.0M", merged.getValue("deepseek-v4.1-flash tokens (est.)").countText)
    }

    @Test
    fun aMergedPercentWindowAveragesOnlyAccountsOnTheSamePlan() {
        val samePlan = percentAccounts("Pro" to 20.0, "Pro" to 40.0)
        assertEquals(
            30.0,
            samePlan.single().mergedWindows.single().percent ?: -1.0,
            0.001
        )

        val mixedPlans = percentAccounts("Pro" to 20.0, "Free" to 40.0)
        assertTrue(mixedPlans.single().mergedWindows.isEmpty())
    }

    @Test
    fun theTightestWindowFollowsTheDisplayModeWithoutChangingWhichWindowIsTightest() = runBlocking {
        val remainingCards = realCards(QuotaDisplayMode.REMAINING)
        val usedCards = realCards(QuotaDisplayMode.USED)
        val remaining = remainingCards.first { it.providerId == "workbuddy" }
        val used = usedCards.first { it.providerId == "workbuddy" }

        assertEquals(0.0, remaining.tightestPercent ?: -1.0, 0.01)
        assertEquals(100.0, used.tightestPercent ?: -1.0, 0.01)
        assertEquals(
            remainingCards.map { it.providerId },
            usedCards.map { it.providerId }
        )
    }

    @Test
    fun eachAccountReadsItsMostUsedWindowAsItsTightest() = runBlocking {
        val workbuddy = realCards(QuotaDisplayMode.REMAINING)
            .first { it.providerId == "workbuddy" }

        // 180***201 has spent its whole Credits allowance: 100% used, 0% remaining.
        val spent = workbuddy.accounts.first { it.label == "180***201" }
        val spentTightest = quotaAccountTightest(spent, QuotaDisplayMode.REMAINING)
        assertEquals("Credits", spentTightest?.name)
        assertEquals(0.0, spentTightest?.percent ?: -1.0, 0.01)

        // The captured answer has no 180***202. Its second account still reports four windows:
        // the untouched Credits row reads 100% remaining, but the token window at 99.8% used is
        // the one actually closest to its limit and therefore the one the account line prints.
        val second = workbuddy.accounts.first { it.accountId == "153****8998" }
        val credits = second.windows.first { it.name == "Credits" }
        // Untouched, so in the remaining direction the whole 500 is still there.
        assertEquals("500 / 500 credits", credits.countText)
        assertEquals(100.0, credits.percent ?: -1.0, 0.01)
        val secondTightest = quotaAccountTightest(second, QuotaDisplayMode.REMAINING)
        assertEquals("deepseek-v4.1-flash tokens (est.)", secondTightest?.name)
        assertEquals(0.207, secondTightest?.percent ?: -1.0, 0.01)

        // The same window stays tightest when the page prints used shares instead.
        val usedWorkbuddy = realCards(QuotaDisplayMode.USED)
            .first { it.providerId == "workbuddy" }
        val usedSpent = usedWorkbuddy.accounts.first { it.label == "180***201" }
        assertEquals(
            "Credits",
            quotaAccountTightest(usedSpent, QuotaDisplayMode.USED)?.name
        )
        val usedSecond = usedWorkbuddy.accounts.first { it.accountId == "153****8998" }
        assertEquals(
            "deepseek-v4.1-flash tokens (est.)",
            quotaAccountTightest(usedSecond, QuotaDisplayMode.USED)?.name
        )
    }

    @Test
    fun aCountWindowReadsTheDirectionThePagePrints() = runBlocking {
        // 180***201 has spent its whole Credits allowance, so the one window reads "500 / 500"
        // used and "0 / 500" remaining: a spent count never sits under a "0% remaining" caption.
        val spentRemaining = realCards(QuotaDisplayMode.REMAINING)
            .first { it.providerId == "workbuddy" }
            .accounts.first { it.label == "180***201" }
            .windows.first { it.name == "Credits" }
        assertEquals("0 / 500 credits", spentRemaining.countText)
        assertEquals(0.0, spentRemaining.percent ?: -1.0, 0.01)

        val spentUsed = realCards(QuotaDisplayMode.USED)
            .first { it.providerId == "workbuddy" }
            .accounts.first { it.label == "180***201" }
            .windows.first { it.name == "Credits" }
        assertEquals("500 / 500 credits", spentUsed.countText)
        assertEquals(100.0, spentUsed.percent ?: -1.0, 0.01)
    }

    @Test
    fun anAccountWithOnlyUnreadableWindowsHasNoTightestWindow() {
        val card = quotaProviderCards(
            quotas = listOf(
                AccountQuota(
                    providerId = "vendor",
                    displayName = "Vendor",
                    accountId = "account-a",
                    known = true,
                    windows = listOf(
                        QuotaWindow(id = "Unlimited", unit = "", unlimited = true),
                        QuotaWindow(id = "Text", unit = "", display = "In use", text = true)
                    )
                )
            ),
            accounts = emptyList(),
            maskAccountNames = false,
            displayMode = QuotaDisplayMode.REMAINING,
            nowMillis = RequestTimestamps.parseMillis("2026-11-11T00:00:00Z")!!
        )

        assertNull(
            quotaAccountTightest(card.single().accounts.single(), QuotaDisplayMode.REMAINING)
        )
    }

    @Test
    fun paceComparesUsedShareWithTheElapsedPartOfTheWindow() {
        val now = RequestTimestamps.parseMillis("2026-11-11T00:00:00Z")!!
        val start = RequestTimestamps.formatUtcIso(now - 10L * DAY_MILLIS)
        val reset = RequestTimestamps.formatUtcIso(now + 10L * DAY_MILLIS)

        assertEquals("与均速持平", paceText(now, start, reset, used = 50.0))
        assertEquals("比均速快 10%", paceText(now, start, reset, used = 60.0))
        assertEquals("比均速慢 10%", paceText(now, start, reset, used = 40.0))
        assertNull(paceText(now, null, reset, used = 50.0))
        assertNull(paceText(now, start, null, used = 50.0))
    }

    @Test
    fun unlimitedAndTextWindowsStayOutOfTheAverage() {
        val card = quotaProviderCards(
            quotas = listOf(
                AccountQuota(
                    providerId = "vendor",
                    displayName = "Vendor",
                    known = true,
                    windows = listOf(
                        QuotaWindow(id = "Unlimited", unit = "", unlimited = true),
                        QuotaWindow(id = "Text", unit = "", display = "In use", text = true),
                        QuotaWindow(id = "Share", unit = "", usedPercent = 50.0),
                        QuotaWindow(id = "Count", unit = "credits", amount = 0.0, allowanceLimit = 10.0)
                    )
                )
            ),
            accounts = emptyList(),
            maskAccountNames = false,
            displayMode = QuotaDisplayMode.REMAINING,
            nowMillis = RequestTimestamps.parseMillis("2026-11-11T00:00:00Z")!!
        )

        val overview = quotaOverview(card, QuotaDisplayMode.REMAINING)
        assertEquals(4, overview.windowCount)
        assertEquals(75.0, overview.averageRemaining ?: 0.0, 0.001)
        assertEquals(0, overview.lowCount)
    }

    private fun paceText(now: Long, start: String?, reset: String?, used: Double): String? {
        val card = quotaProviderCards(
            quotas = listOf(
                AccountQuota(
                    providerId = "vendor",
                    displayName = "Vendor",
                    known = true,
                    windows = listOf(
                        QuotaWindow(
                            id = "Credits",
                            unit = "credits",
                            amount = used,
                            allowanceLimit = 100.0,
                            startsAt = start,
                            resetsAt = reset
                        )
                    )
                )
            ),
            accounts = emptyList(),
            maskAccountNames = false,
            displayMode = QuotaDisplayMode.REMAINING,
            nowMillis = now
        )
        return card.single().accounts.single().windows.single().paceText
    }

    private fun percentAccounts(vararg planAndUsed: Pair<String, Double>): List<QuotaProviderCard> =
        quotaProviderCards(
            quotas = planAndUsed.mapIndexed { index, (plan, used) ->
                AccountQuota(
                    providerId = "vendor",
                    displayName = "Vendor",
                    accountId = "account-" + index,
                    plan = plan,
                    known = true,
                    windows = listOf(QuotaWindow(id = "Credits", unit = "", usedPercent = used))
                )
            },
            accounts = emptyList(),
            maskAccountNames = false,
            displayMode = QuotaDisplayMode.USED,
            nowMillis = RequestTimestamps.parseMillis("2026-11-11T00:00:00Z")!!
        )

    private suspend fun realGatewayQuotas(): List<AccountQuota> {
        val repo = LiveMagpieRepository(
            server.url("/").toString(),
            "quota-cards",
            MagpieApiClient()
        )
        return repo.quotas()
    }

    private suspend fun realCards(displayMode: QuotaDisplayMode): List<QuotaProviderCard> =
        quotaProviderCards(
            quotas = realGatewayQuotas(),
            accounts = emptyList(),
            maskAccountNames = true,
            displayMode = displayMode,
            nowMillis = RequestTimestamps.parseMillis("2026-11-11T00:00:00Z")!!
        )

    private fun fixtureText(name: String): String =
        javaClass.classLoader!!.getResource("upstream/$name")!!.readText()

    private companion object {
        const val DAY_MILLIS = 86_400_000L
    }
}
