package com.magpie.pocket.core.data

import com.magpie.pocket.core.model.Period
import com.magpie.pocket.core.network.MagpieApiClient
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

class LiveMagpiePeriodTest {

    private lateinit var server: MockWebServer
    private lateinit var repo: LiveMagpieRepository

    private val usageJson = """
        {
          "period": "today",
          "calls": 12,
          "errors": 1,
          "timed": 10,
          "ttft_ms": 5000,
          "input": 100,
          "output": 200,
          "cache_read": 300,
          "cache_write": 400,
          "cost": 1.25,
          "unpriced": 3,
          "credits": 7.5,
          "decode_ms": 4000,
          "decode_out": 600,
          "series": [{"label": "2026-10-21", "calls": 9, "cost": 0.75}],
          "models": [{"provider": "workbuddy", "model": "m1", "calls": 2, "total": 700}],
          "agents": [{"id": "ag1", "name": "orchestrator", "calls": 5, "cost": 0.5, "errors": 1}],
          "sessions": [{"id": "s1", "agent": "orchestrator", "calls": 2, "cost": 0.2, "credits": 1.0}],
          "accounts": [{"id": "wb-01", "account": "wb-01", "name": "main", "provider": "workbuddy", "calls": 3, "cost": 0.4}],
          "callerKeys": [{"id": "ck1", "name": "IDE", "callerKeyName": "IDE", "calls": 4, "cost": 0.6, "unpriced": 2}],
          "providerKeys": [{"id": "pk1", "provider": "workbuddy", "providerKeyName": "workbuddy-key-01", "calls": 6, "cost": 0.7}]
        }
    """.trimIndent()

    private val providersJson = """
        {"providers":[{
          "id": "workbuddy",
          "name": "WorkBuddy",
          "host": "api.example",
          "ready": true,
          "off": false,
          "speaks": ["openai-chat"],
          "maxConcurrency": 8,
          "keyList": [{"name": "k1"}, {"name": "k2"}],
          "models": [{"id": "m1", "name": "Model 1", "on": true, "context": 128000, "output": 8192, "images": false, "efforts": ["low"], "list": {"input": 0.14, "output": 0.28}}]
        }]}
    """.trimIndent()

    private val maskingSummaryJson = """
        {
          "n": 42,
          "distinct": 5,
          "requests": 37,
          "blocked": 3,
          "warned": 12,
          "days": [{"day": "2026-10-21", "n": 18, "requests": 16}],
          "kinds": [{"k": "EMAIL", "n": 18}],
          "by": {"EMAIL": 18},
          "agents": [{"id": "orchestrator", "name": "orchestrator", "n": 21}],
          "providers": [{"id": "workbuddy", "name": "workbuddy", "n": 26}],
          "values": [{"id": "<EMAIL_1>", "name": "<EMAIL_1>", "n": 6}]
        }
    """.trimIndent()

    private val maskingRecordsJson = """
        {
          "records": [
            {"key": "701", "values": [{"k": "EMAIL", "n": 1}], "blocked": false, "t": "2026-10-22T01:12:00Z", "what": "body", "agent": "orchestrator", "provider": "workbuddy", "model": "m1", "snips": {"EMAIL_1": "<EMAIL_1>"}},
            {"key": 702, "values": [{"k": "API_KEY", "n": 2}], "blocked": true}
          ],
          "more": true
        }
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path?.startsWith("/api/usage/quotas") == true -> json("[]")
                request.path?.startsWith("/api/usage") == true -> json(usageJson)
                request.path == "/api/providers" -> json(providersJson)
                request.path?.startsWith("/api/masking/summary") == true -> json(maskingSummaryJson)
                request.path?.startsWith("/api/masking/records") == true -> json(maskingRecordsJson)
                request.path?.startsWith("/api/masking/value") == true -> json("""{"value":"demo@example.test"}""")
                else -> MockResponse().setResponseCode(404)
            }
        }
        repo = LiveMagpieRepository(server.url("/").toString(), "period", MagpieApiClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun json(body: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)

    @Test
    fun periodsMapToUpstreamPeriodValues() = runBlocking {
        val expected = listOf(
            Period.TODAY to "today",
            Period.SEVEN_DAYS to "7d",
            Period.THIRTY_DAYS to "30d",
            Period.ALL to "all"
        )

        for ((period, param) in expected) {
            repo.usage(period)
            assertEquals("/api/usage?period=" + param, server.takeRequest().path)
            assertEquals(period, repo.overview(period).period)
            assertEquals("/api/usage?period=" + param, server.takeRequest().path)
        }
    }

    @Test
    fun onlyMultiDayPeriodsExposeDailyTrends() = runBlocking {
        val today = repo.overview(Period.TODAY)
        assertTrue(today.dailyTrends.isEmpty())
        assertEquals(1, today.hourlyTrends.size)

        val month = repo.overview(Period.THIRTY_DAYS)
        assertEquals(1, month.dailyTrends.size)
        assertEquals("2026-10-21", month.dailyTrends.single().dayLabel)
        assertEquals(9L, month.dailyTrends.single().completedRequests)
        assertEquals(1.25, month.cost ?: 0.0, 0.0001)
        assertEquals(3L, month.unpriced ?: 0L)
        assertEquals(4000L, month.decodeMs ?: 0L)
        assertEquals(600L, month.decodeOut ?: 0L)
    }

    @Test
    fun usageMapsCostCreditsDecodeAndBreakdownDimensions() = runBlocking {
        val usage = repo.usage(Period.THIRTY_DAYS)

        assertEquals(1.25, usage.cost ?: 0.0, 0.0001)
        assertEquals(3L, usage.unpriced ?: 0L)
        assertEquals(7.5, usage.credits ?: 0.0, 0.0001)
        assertEquals(4000L, usage.decodeMs ?: 0L)
        assertEquals(600L, usage.decodeOut ?: 0L)
        assertEquals(700L, usage.models.single().totalTokens)

        val agent = usage.agents.single()
        assertEquals("ag1", agent.id)
        assertEquals("orchestrator", agent.label)
        assertEquals(1L, agent.errors ?: 0L)

        // A session row is labelled by its owning agent, an account row by its account name.
        assertEquals("orchestrator", usage.sessions.single().label)
        assertEquals("main", usage.accounts.single().label)
        assertEquals("workbuddy", usage.accounts.single().provider)
        assertEquals("IDE", usage.callerKeys.single().label)
        assertEquals(2L, usage.callerKeys.single().unpriced ?: 0L)
        assertEquals("workbuddy-key-01", usage.providerKeys.single().label)
        assertNull(usage.agents.single().credits)
    }

    @Test
    fun providersAndMaskingMapToDomain() = runBlocking {
        val provider = repo.providers().single()
        assertEquals("workbuddy", provider.id)
        assertEquals("WorkBuddy", provider.name)
        assertEquals(true, provider.ready)
        assertEquals(false, provider.off)
        assertEquals(listOf("k1", "k2"), provider.keyNames)
        assertEquals(8, provider.maxConcurrency)
        assertEquals(listOf("openai-chat"), provider.speaks)
        assertEquals("/api/providers", server.takeRequest().path)

        val model = provider.models.single()
        assertEquals("m1", model.id)
        assertEquals(true, model.enabled)
        assertEquals(128000L, model.context ?: 0L)
        assertEquals(0.14, model.inputPrice ?: 0.0, 0.0001)
        assertEquals(0.28, model.outputPrice ?: 0.0, 0.0001)

        val summary = repo.maskingSummary(7)
        assertEquals("/api/masking/summary?days=7", server.takeRequest().path)
        assertEquals(42L, summary.total ?: 0L)
        assertEquals(5L, summary.distinct ?: 0L)
        assertEquals("2026-10-21", summary.days.single().dayLabel)
        assertEquals("EMAIL", summary.kinds.single().kind)
        assertEquals(18, summary.by["EMAIL"])
        assertEquals(21L, summary.agents.single().n ?: 0L)
        assertEquals(1, summary.providers.size)
        assertEquals(6L, summary.values.single().n ?: 0L)

        val page = repo.maskingRecords(MaskingQuery(limit = 20))
        assertEquals("/api/masking/records?limit=20", server.takeRequest().path)
        assertEquals("701", page.items.first().key)
        // A numeric upstream key is normalized to text, never cast to Int.
        assertEquals("702", page.items.last().key)
        assertEquals("EMAIL", page.items.first().values.single().kind)
        assertEquals("body", page.items.first().what)
        assertEquals(true, page.items.last().blocked)
        assertFalse(page.items.first().snippets.isEmpty())
        assertTrue(page.more)

        assertEquals("demo@example.test", repo.maskingValue("<EMAIL_1>"))
        assertEquals("/api/masking/value?p=%3CEMAIL_1%3E", server.takeRequest().path)
    }
}
