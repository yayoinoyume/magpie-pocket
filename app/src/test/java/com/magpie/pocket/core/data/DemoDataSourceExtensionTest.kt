package com.magpie.pocket.core.data

import com.magpie.pocket.core.model.Coverage
import com.magpie.pocket.core.model.Period
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class DemoDataSourceExtensionTest {

    private lateinit var repo: DemoRepository

    @Before
    fun setUp() {
        val fixture = File("src/main/assets/normalized-demo.json")
        val json = if (fixture.exists()) fixture.readText() else DemoRepository.FALLBACK_DEMO_JSON
        repo = DemoRepository(initialJsonString = json)
    }

    @Test
    fun allFourPeriodsShareOneSampleWithPartialCoverage() = runBlocking {
        for (period in Period.values()) {
            val overview = repo.overview(period)
            assertEquals(period, overview.period)
            assertEquals(Coverage.PARTIAL, overview.coverage)
            assertEquals(1728L, overview.completedRequests)
            assertNotNull(overview.cost)
            assertNotNull(overview.decodeMs)
            assertEquals(7, overview.dailyTrends.size)

            val usage = repo.usage(period)
            assertEquals(period, usage.period)
            assertEquals(Coverage.PARTIAL, usage.coverage)
            assertNotNull(usage.credits)
            assertEquals(3, usage.agents.size)
            assertEquals(2, usage.sessions.size)
            assertEquals(2, usage.accounts.size)
            assertEquals(2, usage.callerKeys.size)
            assertEquals(2, usage.providerKeys.size)
        }
    }

    @Test
    fun demoProvidersExposeModelsAndPricing() = runBlocking {
        val providers = repo.providers()
        assertEquals(2, providers.size)

        val workbuddy = providers.first()
        assertEquals("workbuddy", workbuddy.id)
        assertEquals(true, workbuddy.ready)
        assertEquals(listOf("workbuddy-key-01", "workbuddy-key-02"), workbuddy.keyNames)
        assertEquals(listOf("openai-chat", "anthropic-messages"), workbuddy.speaks)

        val model = workbuddy.models.first()
        assertEquals("deepseek-v4.1-flash", model.id)
        assertEquals(128000L, model.context ?: 0L)
        assertEquals(0.14, model.inputPrice ?: 0.0, 0.0001)
        assertEquals(listOf("low", "high"), model.efforts)

        // Unknown upstream values stay unknown instead of becoming false or zero.
        val moonshotModel = providers.last().models.first()
        assertNull(moonshotModel.enabled)
        assertNull(moonshotModel.context)
        assertNull(moonshotModel.inputPrice)
    }

    @Test
    fun demoMaskingSummaryRecordsAndValuesAreAvailable() = runBlocking {
        val summary = repo.maskingSummary(30)
        assertEquals(42L, summary.total ?: 0L)
        assertEquals(5L, summary.distinct ?: 0L)
        assertEquals(3, summary.days.size)
        assertEquals("EMAIL", summary.kinds.first().kind)
        assertEquals(18, summary.by["EMAIL"])
        assertEquals(3, summary.agents.size)
        assertEquals(2, summary.providers.size)

        val page = repo.maskingRecords(MaskingQuery(limit = 2))
        assertEquals(2, page.items.size)
        assertEquals("701", page.items.first().key)
        assertTrue(page.more)

        val next = repo.maskingRecords(MaskingQuery(limit = 2, before = "702"))
        assertEquals(1, next.items.size)
        assertEquals("703", next.items.single().key)
        assertEquals(false, next.more)

        assertEquals("demo.user@example.test", repo.maskingValue("<EMAIL_1>"))
        assertNull(repo.maskingValue("<MISSING_1>"))
    }
}
