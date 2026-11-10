package com.magpie.pocket.app

import androidx.lifecycle.viewModelScope
import com.magpie.pocket.core.data.DemoRepository
import com.magpie.pocket.core.model.Attempt
import com.magpie.pocket.core.model.Coverage
import com.magpie.pocket.core.model.ConnectionConfig
import com.magpie.pocket.core.model.DataMode
import com.magpie.pocket.core.model.DisplayPreferences
import com.magpie.pocket.core.model.FinalState
import com.magpie.pocket.core.model.GlassTier
import com.magpie.pocket.core.model.RequestFilter
import com.magpie.pocket.core.model.RequestKey
import com.magpie.pocket.core.model.RequestSummary
import com.magpie.pocket.core.model.SourceMeta
import com.magpie.pocket.core.model.ThemeMode
import com.magpie.pocket.core.model.TraceSnapshot
import com.magpie.pocket.core.network.MagpieApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The page-ready derivations: one definition per number, missing values kept missing. */
@OptIn(ExperimentalCoroutinesApi::class)
class DashboardDerivedStateTest {

    @get:org.junit.Rule
    val testTimeout = org.junit.rules.Timeout.seconds(10)

    private val testDispatcher = UnconfinedTestDispatcher()
    private val viewModels = mutableListOf<AppViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        runBlocking {
            viewModels.forEach { it.viewModelScope.coroutineContext[Job]?.cancelAndJoin() }
        }
        viewModels.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun overviewCostKeepsUnpricedCallsVisibleNextToAZero() = runBlocking {
        val viewModel = newViewModel()

        val cost = viewModel.overviewCost.first()

        assertEquals(0.0, cost.value ?: -1.0, 0.0)
        assertEquals(3L, cost.unpriced)
        assertTrue(cost.hasUnpriced)
    }

    @Test
    fun quotaViewsExposeOnlyTheTightestWindowAndMaskTheAlias() = runBlocking {
        val viewModel = newViewModel()

        val account = viewModel.accountQuotas.first().single()

        assertEquals("acc-1", account.accountId)
        assertEquals("wor***ary", account.label)
        assertEquals("daily", account.tightestWindow?.id)
        assertEquals(0.9, account.ratio ?: 0.0, 0.0001)
    }

    @Test
    fun quotaAlertsCarryTheMaskedLabelAndTheSameTightestWindow() = runBlocking {
        val viewModel = newViewModel()

        val alert = viewModel.quotaAlerts.first().single()

        assertEquals("wor***ary", alert.accountLabel)
        assertEquals("daily", alert.windowId)
        assertEquals(0.9, alert.ratio, 0.0001)
        assertEquals("2026-11-11T00:00:00Z", alert.resetsAt)
    }

    @Test
    fun statisticsDayUsesTheAnnouncedServerZone() = runBlocking {
        val viewModel = newViewModel()

        assertEquals("11-11", viewModel.statisticsDay.first())
    }

    @Test
    fun statisticsDayFallsBackToTheLiteralServerDateWithoutAZone() = runBlocking {
        val viewModel = newViewModel(statisticsTimeZone = "")

        assertEquals("11-10", viewModel.statisticsDay.first())
    }

    @Test
    fun dataCoverageReportsTheWorstSourceAndStaleRows() = runBlocking {
        val viewModel = newViewModel()

        val coverage = viewModel.dataCoverage.first()

        assertEquals(Coverage.PARTIAL, coverage.coverage)
        assertTrue(coverage.stale)
    }

    @Test
    fun dailyTrendsAndUsageDecodeSpeedComeFromTheMatchingSnapshots() = runBlocking {
        val viewModel = newViewModel()

        val day = viewModel.dailyTrends.first().single()
        assertEquals("2026-11-10", day.dayLabel)
        assertEquals(3L, day.completedRequests)
        assertEquals(0.0, day.cost ?: -1.0, 0.0)
        assertEquals(200.0, viewModel.usageDecodeSpeed.first() ?: 0.0, 0.0001)
    }

    @Test
    fun runningRequestsKeepTheNewestThree() = runBlocking {
        val viewModel = newViewModel()
        val routes = (1..4).map { index ->
            request("90" + index, "2026-11-10T12:00:0" + index + "Z")
        }

        viewModel.applyTraceSnapshot(
            TraceSnapshot(
                epoch = "e",
                seq = 1,
                retainedRouteIds = routes.map { it.key.routeId },
                runningRouteIds = routes.map { it.key.routeId },
                recentRoutes = routes,
                recentDetails = emptyList()
            )
        )

        assertEquals(listOf("904", "903", "902"), viewModel.runningRequests.first().map { it.key.routeId })
        assertEquals("904", viewModel.runningRequest.first()?.key?.routeId)
    }

    @Test
    fun supportedRequestFiltersExposeEveryImplementedFilter() = runBlocking {
        val viewModel = newViewModel()

        assertEquals(RequestFilter.entries.toSet(), viewModel.supportedRequestFilters.first())
    }

    private fun request(id: String, startedAt: String) = RequestSummary(
        key = RequestKey("demo-main", "e", id),
        startedAt = startedAt,
        finalState = FinalState.IN_PROGRESS,
        attempts = listOf(Attempt(1)),
        meta = SourceMeta("demo-main", DataMode.DEMO, startedAt, startedAt, coverage = Coverage.RECENT_ONLY)
    )

    private fun newViewModel(statisticsTimeZone: String = "Asia/Shanghai"): AppViewModel {
        val repo = DemoRepository(initialJsonString = demoJson(statisticsTimeZone))
        repo.setScenario("offlineWithCache")
        return AppViewModel(
            credentialStore = DashboardFakeCredentialStore(),
            preferencesManager = DashboardFakePreferencesManager(),
            demoRepo = repo,
            apiClient = MagpieApiClient()
        ).also { viewModels += it }
    }

    /**
     * One fixture with a priced zero plus unpriced calls, two quota windows that disagree,
     * a stale request row and a decode window the usage snapshot can be measured from.
     */
    private fun demoJson(statisticsTimeZone: String) = """
        {
          "capabilities": { "statisticsTimeZone": "${statisticsTimeZone}" },
          "trace": { "epoch": "demo", "seq": 1, "totals": { "completedRequests": 3, "rerouteHandoffs": 0, "finalErrors": 0 } },
          "overview": {
            "rangeEnd": "2026-11-10T23:30:00Z",
            "completedRequests": 3,
            "finalErrors": 0,
            "cost": 0.0,
            "unpriced": 3,
            "decodeMs": 2000,
            "decodeOut": 1000,
            "dailyTrends": [{ "day": "2026-11-10", "requests": 3, "cost": 0.0 }]
          },
          "usage": {
            "cost": 0.0,
            "unpriced": 2,
            "decodeMs": 4000,
            "decodeOut": 800,
            "tokens": { "output": 10 }
          },
          "groups": [],
          "accounts": [
            { "id": "acc-1", "providerId": "vendor", "alias": "workbuddy-primary", "availability": "AVAILABLE" }
          ],
          "quotas": [
            {
              "accountId": "acc-1",
              "known": true,
              "windows": [
                { "id": "hourly", "unit": "%", "used": 10, "limit": 100 },
                { "id": "daily", "unit": "%", "used": 90, "limit": 100, "resetsAt": "2026-11-11T00:00:00Z" }
              ]
            }
          ],
          "requests": [
            {
              "key": { "connectionId": "demo-main", "epoch": "demo", "routeId": "101", "sourceDay": "2026-11-10" },
              "startedAt": "2026-11-10T12:00:00Z",
              "finalState": "SUCCEEDED",
              "rerouteCount": 0,
              "usage": { "inputUncached": 1, "output": 2, "cacheRead": 3, "cacheWrite": 4 },
              "meta": { "connectionId": "demo-main", "coverage": "RECENT_ONLY", "stale": false, "statisticsTimeZone": "" }
            }
          ]
        }
    """.trimIndent()
}

private class DashboardFakeCredentialStore : ConnectionStateSource {
    private var config = ConnectionConfig(id = "demo-main", name = "主网关", mode = DataMode.DEMO)

    override fun getConnection(): ConnectionConfig = config
    override fun saveConnection(config: ConnectionConfig) { this.config = config }
    override fun clearCredentials() { config = ConnectionConfig(id = "demo-main", name = "主网关", mode = DataMode.DEMO) }
}

private class DashboardFakePreferencesManager : DisplayPreferencesSource {
    private val state = MutableStateFlow(DisplayPreferences())
    override val displayPreferencesFlow = state

    override suspend fun updateThemeMode(mode: ThemeMode) { state.value = state.value.copy(themeMode = mode) }
    override suspend fun updateGlassTier(tier: GlassTier) { state.value = state.value.copy(glassTier = tier) }
    override suspend fun updateReduceMotion(enabled: Boolean) { state.value = state.value.copy(reduceMotion = enabled) }
    override suspend fun updateMaskAccounts(enabled: Boolean) { state.value = state.value.copy(maskAccountNames = enabled) }
    override suspend fun updateKeepLiveConnectionInBackground(enabled: Boolean) {
        state.value = state.value.copy(keepLiveConnectionInBackground = enabled)
    }
}
