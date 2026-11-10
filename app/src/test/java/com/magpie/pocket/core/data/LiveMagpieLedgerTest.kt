package com.magpie.pocket.core.data

import com.magpie.pocket.core.model.FinalState
import com.magpie.pocket.core.model.Period
import com.magpie.pocket.core.model.RequestKey
import com.magpie.pocket.core.network.MagpieApiClient
import com.magpie.pocket.core.util.MetricCalculators
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** LIVE ledger behaviour that DEMO used to be the only place for: reroute, running, null tokens. */
class LiveMagpieLedgerTest {

    private lateinit var server: MockWebServer
    private lateinit var repo: LiveMagpieRepository

    private val ledgerJson = """
        {
          "period": "today",
          "offset": 0,
          "total": 3,
          "rows": [
            {"route_id": 201, "t": "2026-10-22T09:00:00Z", "req": "group/coding", "served": "m1", "provider": "vendor", "providerAccount": "acc-1", "status": 200, "retried": true, "in": 100, "out": 50},
            {"route_id": 202, "t": "2026-10-22T09:01:00Z", "req": "group/coding", "status": 200},
            {"route_id": 203, "t": "2026-10-22T09:02:00Z", "req": "group/coding", "status": 0}
          ]
        }
    """.trimIndent()

    /** Two tries: one reroute. The ledger alone could only report the retried flag. */
    private val routeJson = """
        {
          "id": 201,
          "time": "2026-10-22T09:00:00Z",
          "group": {"id": "coding"},
          "done": true,
          "status": 200,
          "tries": [
            {"id": "a", "provider": "vendor", "account": "acc-1", "start": "2026-10-22T09:00:00Z", "status": 429},
            {"id": "b", "provider": "vendor", "account": "acc-2", "start": "2026-10-22T09:00:01Z", "status": 200}
          ]
        }
    """.trimIndent()

    /** A priced zero with unpriced calls, and no token categories at all. */
    private val usageJson = """
        {
          "calls": 2,
          "errors": 0,
          "cost": 0.0,
          "unpriced": 3,
          "decode_ms": 2000,
          "decode_out": 1000,
          "series": [{"label": "2026-10-20", "calls": 2, "cost": 0.5}]
        }
    """.trimIndent()

    private val historyJson = """
        {"days": [{"day": "2026-10-19", "requests": 1}, {"day": "2026-10-20", "requests": 2}]}
    """.trimIndent()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path?.startsWith("/api/usage/requests") == true -> json(ledgerJson)
                request.path?.startsWith("/api/gateway/route") == true -> json(routeJson)
                request.path?.startsWith("/api/gateway/history") == true -> json(historyJson)
                request.path?.startsWith("/api/usage") == true -> json(usageJson)
                else -> MockResponse().setResponseCode(404)
            }
        }
        repo = LiveMagpieRepository(server.url("/").toString(), "ledger", MagpieApiClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun reroutedFilterUsesTheLedgerRetriedFlag() = runBlocking {
        val rerouted = repo.requests(RequestQuery(statusFilter = "REROUTED"))

        assertEquals(listOf("201"), rerouted.items.map { it.key.routeId })
        assertEquals(1, rerouted.items.single().rerouteCount)
        // All three upstream rows were consumed, so there is no next page to fetch.
        assertEquals(3, rerouted.nextOffset)
        assertEquals(false, rerouted.hasMore)
        // The server never saw a reroute-specific query: the local rows answer it.
        assertTrue(server.takeRequest().path!!.startsWith("/api/usage/requests?"))
    }

    @Test
    fun runningFilterKeepsInProgressLedgerRows() = runBlocking {
        val running = repo.requests(RequestQuery(statusFilter = "RUNNING"))

        assertEquals(listOf("203"), running.items.map { it.key.routeId })
        assertEquals(FinalState.IN_PROGRESS, running.items.single().finalState)
    }

    @Test
    fun ledgerRowsWithoutTokenEvidenceHaveNoUsage() = runBlocking {
        val rows = repo.requests(RequestQuery()).items.associateBy { it.key.routeId }

        assertEquals("150", MetricCalculators.formatTotalTokens(rows.getValue("201").usage))
        assertNull(rows.getValue("202").usage)
        assertEquals("—", MetricCalculators.formatTotalTokens(rows.getValue("202").usage))
    }

    @Test
    fun detailRerouteCountComesFromAttemptsNotTheRetriedFlag() = runBlocking {
        val detail = repo.requestDetail(RequestKey("ledger", "", "201"))

        assertEquals(2, detail.attempts.size)
        assertEquals(1, detail.summary.rerouteCount)
    }

    @Test
    fun usageWithoutTokenCategoriesKeepsThemMissing() = runBlocking {
        val usage = repo.usage(Period.TODAY)

        assertNull(usage.tokens.inputUncached)
        assertNull(usage.tokens.output)
        assertEquals("—", MetricCalculators.formatTotalTokens(usage.tokens))
        assertEquals(0.0, usage.cost ?: -1.0, 0.0)
        assertEquals(3L, usage.unpriced ?: 0L)
    }

    @Test
    fun usageKeepsTheSeriesForEveryPeriod() = runBlocking {
        for (period in Period.values()) {
            val usage = repo.usage(period)

            val point = usage.dailyTrends.single()
            assertEquals("2026-10-20", point.dayLabel)
            assertEquals(2L, point.completedRequests)
            assertEquals(0.5, point.cost ?: 0.0, 0.0001)
        }
    }

    @Test
    fun dailyTrendsMergeSpendFromTheUsageSeries() = runBlocking {
        val overview = repo.overview(Period.TODAY)

        assertEquals(listOf("2026-10-19", "2026-10-20"), overview.dailyTrends.map { it.dayLabel })
        assertNull(overview.dailyTrends.first().cost)
        assertEquals(0.5, overview.dailyTrends.last().cost ?: 0.0, 0.0001)
    }

    private fun json(body: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
