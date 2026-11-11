package com.magpie.pocket.core.data

import com.magpie.pocket.core.model.FinalState
import com.magpie.pocket.core.model.Period
import com.magpie.pocket.core.model.RequestKey
import com.magpie.pocket.core.network.MagpieApiClient
import com.magpie.pocket.core.network.MagpieSession
import com.magpie.pocket.core.util.MetricCalculators
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class LiveMagpieRepositoryCompatTest {

    private lateinit var server: MockWebServer
    private lateinit var repo: LiveMagpieRepository

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/api/state" -> json("""{"version":"Magpie current","timezone":"Asia/Shanghai"}""")
                request.path == "/api/groups" -> fixture("groups-current.json")
                request.path?.startsWith("/api/gateway/trace") == true -> fixture("trace-current.json")
                request.path?.startsWith("/api/gateway/route") == true -> {
                    val root = org.json.JSONObject(fixtureText("trace-current.json"))
                    json(root.getJSONArray("routes").getJSONObject(0).toString())
                }
                request.path?.startsWith("/api/usage/requests") == true -> fixture("ledger-current.json")
                request.path == "/api/usage/quotas" -> fixture("quotas-current.json")
                request.path?.startsWith("/api/usage") == true -> fixture("usage-current.json")
                else -> MockResponse().setResponseCode(404)
            }
        }
        repo = LiveMagpieRepository(server.url("/").toString(), "compat", MagpieApiClient())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun currentGroupsUseModelMembersAndMemberInfoProviders() = runBlocking {
        val cap = repo.probe()
        assertTrue(cap.groups)
        assertTrue(cap.trace)

        val groups = repo.groups()
        assertEquals("coding", groups.single().id)
        assertEquals("round-robin", groups.single().strategy)
        assertEquals(listOf("vendor"), groups.single().providerIds)
        assertEquals(1, groups.single().enabledMemberCount)

        // A string member is a model id, never an account id, and the deployed memberInfo
        // carries no account either; the group answer alone yields no account rows.
        val groupAccounts = repo.accounts()
        assertTrue(groupAccounts.isEmpty())

        // The ViewModel merges the panels of the same refresh: usage names accounts with
        // traffic, quotas names accounts the server can price, and groups wins any id it names.
        val merged = mergeAccountSources(
            groups = groupAccounts,
            usage = repo.usage(Period.TODAY).accounts,
            quotas = repo.quotas()
        )
        val traffic = merged.first { it.id == "account-a" }
        assertEquals("vendor", traffic.providerId)
        assertEquals("Account A", traffic.alias)
        assertEquals("UNKNOWN", traffic.availability)
        assertTrue(traffic.fromUsage)
        val quotaOnly = merged.first { it.id == "unlimited-account" }
        assertEquals("vendor", quotaOnly.providerId)
        assertFalse(quotaOnly.fromUsage)
    }

    @Test
    fun currentTraceMapsNumericIdsOrderEvidenceAndUsageArray() = runBlocking {
        repo.probe()
        val trace = repo.observeTrace().first()
        assertEquals("101", trace.retainedRouteIds.single())
        assertTrue(trace.runningRouteIds.isEmpty())

        val detail = repo.requestDetail(RequestKey("compat", "", "101"))
        assertEquals("2026-10-22T09:59:55Z", detail.summary.startedAt)
        assertEquals("coding", detail.summary.groupId)
        assertEquals("vendor", detail.summary.providerId)
        assertEquals("account-a", detail.summary.accountId)
        assertEquals("model-a", detail.summary.servedModel)
        assertEquals(3000L, detail.summary.durationMs)
        assertEquals(800L, detail.summary.ttftMs)
        assertEquals(FinalState.SUCCEEDED, detail.summary.finalState)
        assertEquals(100L, detail.summary.usage?.inputUncached)
        assertEquals(50L, detail.summary.usage?.output)
        assertEquals(200L, detail.summary.usage?.cacheRead)
        // Reasoning is already part of output and is not counted twice.
        assertEquals(10L, detail.summary.usage?.reasoningIncludedInOutput)
        assertEquals(350L, MetricCalculators.calculateTotalTokens(detail.summary.usage))
        assertEquals("tool_results", detail.context?.parts?.first()?.kind)
        assertEquals("turn · same turn", detail.decisionEvidence.first().substringAfter(": "))
    }

    @Test
    fun currentLedgerRowsAndUsageSeriesMapWithoutInventedTotals() = runBlocking {
        repo.probe()
        val page = repo.requests(RequestQuery())
        assertEquals(1, page.total)
        val row = page.items.single()
        assertEquals("101", row.key.routeId)
        assertEquals("coding", row.groupId)
        assertEquals("group/coding", "group/${row.groupId}")
        assertEquals("model-a", row.requestedModel)
        assertEquals("model-a", row.servedModel)
        assertEquals("account-a", row.accountId)
        assertEquals(3000L, row.durationMs)
        assertEquals(800L, row.ttftMs)
        assertEquals(FinalState.SUCCEEDED, row.finalState)

        val overview = repo.overview(Period.TODAY)
        assertEquals("2026-10-22T00:00:00Z", overview.rangeStart)
        assertEquals(10L, overview.hourlyTrends.single().completedRequests)
        assertEquals("09", overview.hourlyTrends.single().hourLabel)

        val usage = repo.usage(Period.TODAY)
        assertEquals(3500L, MetricCalculators.calculateTotalTokens(usage.tokens))
        assertEquals(3500L, usage.models.single().totalTokens)
    }

    @Test
    fun currentQuotasRootArrayKeepsPercentAndDecimals() = runBlocking {
        repo.probe()
        val quotas = repo.quotas()
        val account = quotas.first { it.accountId == "account-a" }
        // The provider is the row identity; the legacy user field stays its account.
        assertEquals("vendor", account.providerId)
        assertEquals("Vendor", account.displayName)
        assertTrue(account.known)
        assertEquals(3, account.windows.size)

        val credits = account.windows.first()
        assertEquals("credits", credits.id)
        // A used value inside 0..100 is a share even when a limit rides along, and the absolute
        // pair the same answer reported is the more precise figure for the ratio.
        assertEquals(52.5, credits.usedPercent ?: 0.0, 0.0001)
        assertEquals(10.5, credits.amount ?: 0.0, 0.0001)
        assertEquals(20.0, credits.allowanceLimit ?: 0.0, 0.0001)
        assertEquals(0.525, MetricCalculators.calculateQuotaUsedRatio(credits) ?: 0.0, 0.0001)

        val percentageOnly = account.windows[1]
        assertTrue(percentageOnly.known)
        assertEquals(0.525, MetricCalculators.calculateQuotaUsedRatio(percentageOnly) ?: 0.0, 0.0001)
        assertNull(percentageOnly.amount)
        assertNull(percentageOnly.allowanceLimit)

        val unknown = account.windows[2]
        assertFalse(unknown.known)
        assertNull(MetricCalculators.calculateQuotaUsedRatio(unknown))

        val unlimited = quotas.first { it.accountId == "unlimited-account" }
        // The answer itself is known; only the window has no figure to show.
        assertTrue(unlimited.known)
        assertFalse(unlimited.windows.single().known)
        assertNull(MetricCalculators.calculateQuotaUsedRatio(unlimited.windows.single()))
    }

    @Test
    fun quotaWindowsKeepAbsoluteCountsAndSharesApart() = runBlocking {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/api/state" ->
                    json("""{"version":"Magpie current","timezone":"Asia/Shanghai"}""")
                request.path == "/api/usage/quotas" -> json(
                    """
                    [
                      {"provider":"count-vendor","name":"Count vendor","windows":[
                        {"name":"daily","used":301,"limit":350,"unit":"credits"}]},
                      {"provider":"count-only","name":"Count only","windows":[
                        {"name":"tokens","used":1240}]},
                      {"provider":"share-vendor","name":"Share vendor","windows":[
                        {"name":"7 days","used":13}]},
                      {"provider":"limit-vendor","name":"Limit vendor","windows":[
                        {"name":"credits","used":50,"limit":500,"unit":"credits"}]}
                    ]
                    """.trimIndent()
                )
                else -> MockResponse().setResponseCode(404)
            }
        }

        val quotas = repo.quotas()

        // used + limit is an absolute count: 301 / 350, never a 301% share.
        val counted = quotas.first { it.providerId == "count-vendor" }.windows.single()
        assertNull(counted.usedPercent)
        assertEquals(301L, counted.used)
        assertEquals(350L, counted.limit)
        assertEquals(0.86, MetricCalculators.calculateQuotaUsedRatio(counted) ?: 0.0, 0.0001)

        // used above 100 with no limit is still a count, and has no share to show.
        val countOnly = quotas.first { it.providerId == "count-only" }.windows.single()
        assertNull(countOnly.usedPercent)
        assertEquals(1240L, countOnly.used)
        assertNull(MetricCalculators.calculateQuotaUsedRatio(countOnly))

        // used alone is a share: 13 means 13%, not thirteen credits.
        val share = quotas.first { it.providerId == "share-vendor" }.windows.single()
        assertNull(share.used)
        assertEquals(13.0, share.usedPercent ?: 0.0, 0.0001)
        assertEquals(0.13, MetricCalculators.calculateQuotaUsedRatio(share) ?: 0.0, 0.0001)

        // A limit next to a 0..100 used value used to make the window a count: 50 / 500 = 10%.
        // The used value is the share, so this window is half spent, not a tenth.
        val limited = quotas.first { it.providerId == "limit-vendor" }.windows.single()
        assertEquals(50.0, limited.usedPercent ?: 0.0, 0.0001)
        // The answer reported a share and a limit, but no absolute used count to pair with it.
        assertNull(limited.amount)
        assertEquals(500.0, limited.allowanceLimit ?: 0.0, 0.0001)
        assertEquals(0.5, MetricCalculators.calculateQuotaUsedRatio(limited) ?: 0.0, 0.0001)
    }

    @Test
    fun traceCachePrunesRetainedIdsAndResetsOnEpoch() = runBlocking {
        val requests = AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path == "/api/state") {
                    return json("""{"version":"Magpie current","timezone":"Asia/Shanghai"}""")
                }
                if (request.path?.startsWith("/api/gateway/trace") == true) {
                    return when (requests.incrementAndGet()) {
                        1 -> json("""{"epoch":"e1","seq":1,"mine":true,"ids":["1"],"routes":[{"id":1,"time":"2026-10-22T09:59:55Z","group":{"id":"g1"},"done":false,"tries":[{"id":"a","provider":"p1","account":"a1"}]}]}""")
                        2 -> json("""{"epoch":"e1","seq":2,"mine":true,"ids":["2"],"routes":[{"id":2,"time":"2026-10-22T10:00:00Z","group":{"id":"g1"},"done":true,"status":200,"tries":[{"id":"b","provider":"p2","account":"a2","done":true,"status":200}]}]}""")
                        else -> json("""{"epoch":"e2","seq":1,"mine":true,"ids":["3"],"routes":[{"id":3,"time":"2026-10-22T10:00:01Z","group":{"id":"g1"},"done":false}]}""")
                    }
                }
                return MockResponse().setResponseCode(404)
            }
        }
        repo.probe()
        // The probe's trace read is emitted first, then the loop continues after its seq.
        val seeded = repo.observeTrace().first()
        assertEquals(listOf("1"), seeded.recentRoutes.map { it.key.routeId })

        val first = repo.observeTrace().first()
        assertEquals(listOf("2"), first.recentRoutes.map { it.key.routeId })

        val second = repo.observeTrace().first()
        assertEquals(listOf("3"), second.recentRoutes.map { it.key.routeId })

        val third = repo.observeTrace().first()
        assertEquals(listOf("3"), third.recentRoutes.map { it.key.routeId })
        assertEquals("e2", third.epoch)
    }

    @Test
    fun unchangedTraceSequencePausesInsteadOfBusyLooping() {
        assertEquals(5000L, repo.traceIdleDelayMs(0L, 0L))
        assertEquals(1500L, repo.traceIdleDelayMs(7L, 7L))
        assertNull(repo.traceIdleDelayMs(7L, 8L))
    }

    @Test
    fun deployedTraceWithoutIdsResetsOnSeqRollbackAndCapsAtSixty() = runBlocking {
        val requests = AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path == "/api/state") {
                    return json("""{"version":"Magpie current","timezone":"Asia/Shanghai"}""")
                }
                if (request.path?.startsWith("/api/gateway/trace") == true) {
                    val n = requests.incrementAndGet()
                    return when (n) {
                        // n=1 is the probe's snapshot and therefore the loop's seed.
                        1 -> json("""{"seq":10,"mine":true,"routes":[{"id":"old","time":"2026-10-22T09:00:00Z","group":{"id":"g1"},"done":true,"status":200}]}""")
                        2 -> json("""{"seq":11,"mine":true,"routes":[{"id":"new","time":"2026-10-22T10:00:00Z","group":{"id":"g1"},"done":false}]}""")
                        3 -> json("""{"seq":2,"mine":true,"routes":[{"id":"restart","time":"2026-10-22T11:00:00Z","group":{"id":"g1"},"done":false}]}""")
                        else -> {
                            val routes = (1..65).joinToString(",") { i ->
                                """{"id":"$i","time":"2026-10-22T12:00:${(i % 60).toString().padStart(2, '0')}Z","group":{"id":"g1"},"done":true,"status":200}"""
                            }
                            json("""{"seq":${n + 2},"mine":true,"routes":[$routes]}""")
                        }
                    }
                }
                return MockResponse().setResponseCode(404)
            }
        }
        repo.probe()

        // The probe's trace read is the seed: the loop emits it and then polls after its
        // sequence, so the dispatcher's first response is consumed by the probe.
        val snapshots = repo.observeTrace().take(4).toList()

        val first = snapshots[0]
        assertEquals(listOf("old"), first.recentRoutes.map { it.key.routeId })
        assertFalse(first.restarted)

        val second = snapshots[1]
        assertEquals(setOf("old", "new"), second.recentRoutes.map { it.key.routeId }.toSet())

        val restart = snapshots[2]
        assertTrue(restart.restarted)
        assertEquals(listOf("restart"), restart.recentRoutes.map { it.key.routeId })

        val capped = snapshots[3]
        assertEquals(60, capped.recentRoutes.size)
        assertEquals(60, capped.recentDetails.size)
        assertEquals(60, capped.retainedRouteIds.size)
    }

    @Test
    fun currentSessionProbeConfirmsGroupsAndTraceAndLeavesPanelsUnknown() = runBlocking {
        val result = MagpieSession.connect(MagpieApiClient(), server.url("/").toString(), null)
        // Probed here because whole screens are gated on them.
        assertTrue(result.capabilities.groups)
        assertTrue(result.capabilities.trace)
        // Panel endpoints are not guessed: a 200 or 404 during the data stage decides.
        assertNull(result.capabilities.usage)
        assertNull(result.capabilities.ledger)
        assertNull(result.capabilities.quotas)
        assertNull(result.capabilities.providers)
        assertNull(result.capabilities.masking)
        assertFalse(result.capabilities.history)
        assertFalse(result.capabilities.requestDetail)
    }

    @Test
    fun groupListAndAccountRowsShareOneGroupsRead() = runBlocking {
        val groupsReads = AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when (request.path) {
                "/api/state" -> json("""{"version":"Magpie current","timezone":"Asia/Shanghai"}""")
                "/api/groups" -> {
                    groupsReads.incrementAndGet()
                    fixture("groups-current.json")
                }
                else -> MockResponse().setResponseCode(404)
            }
        }

        repo.probe()
        // One refresh: the probe's groups body is reused by both data-stage callers.
        coroutineScope {
            val groups = async { repo.groups() }
            val accounts = async { repo.accounts() }
            assertEquals("coding", groups.await().single().id)
            // Only the group source is asked here; usage and quotas are 404 in this
            // dispatcher, and the ViewModel's merge is covered separately.
            assertTrue(accounts.await().isEmpty())
        }
        assertEquals(1, groupsReads.get())
    }

    @Test
    fun probeTraceSnapshotSeedsTheLoopWithoutASecondInitialRead() = runBlocking {
        val traceReads = AtomicInteger(0)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse = when {
                request.path == "/api/state" ->
                    json("""{"version":"Magpie current","timezone":"Asia/Shanghai"}""")
                request.path?.startsWith("/api/gateway/trace") == true -> {
                    traceReads.incrementAndGet()
                    json("""{"seq":7,"mine":true,"routes":[{"id":"old","time":"2026-10-22T09:00:00Z","group":{"id":"g1"},"done":true,"status":200}]}""")
                }
                else -> MockResponse().setResponseCode(404)
            }
        }

        repo.probe()
        val first = repo.observeTrace().first()

        assertEquals(listOf("old"), first.recentRoutes.map { it.key.routeId })
        assertEquals("probe trace is reused by the loop", 1, traceReads.get())
    }

    private fun fixture(name: String) = json(fixtureText(name))

    private fun fixtureText(name: String): String =
        javaClass.classLoader!!.getResource("upstream/$name")!!.readText()

    private fun json(body: String) = MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body)
}
