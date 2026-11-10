package com.magpie.pocket.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.magpie.pocket.core.data.DemoRepository
import com.magpie.pocket.core.data.LiveMagpieRepository
import com.magpie.pocket.core.data.MagpieRepository
import com.magpie.pocket.core.data.MaskingQuery
import com.magpie.pocket.core.data.RequestQuery
import com.magpie.pocket.core.design.AppTab
import com.magpie.pocket.core.model.Account
import com.magpie.pocket.core.model.AccountQuotaView
import com.magpie.pocket.core.model.AccountQuota
import com.magpie.pocket.core.model.Capabilities
import com.magpie.pocket.core.model.ConnectionConfig
import com.magpie.pocket.core.model.ConnectionHealth
import com.magpie.pocket.core.model.ConnectionStatus
import com.magpie.pocket.core.model.CostDisplay
import com.magpie.pocket.core.model.Coverage
import com.magpie.pocket.core.model.DailyTrendPoint
import com.magpie.pocket.core.model.DataCoverage
import com.magpie.pocket.core.model.DataMode
import com.magpie.pocket.core.model.DisplayPreferences
import com.magpie.pocket.core.model.GlassTier
import com.magpie.pocket.core.model.OverviewSnapshot
import com.magpie.pocket.core.model.Period
import com.magpie.pocket.core.model.PrivacyState
import com.magpie.pocket.core.model.QuotaAlert
import com.magpie.pocket.core.model.ProviderCatalogState
import com.magpie.pocket.core.model.RequestDetail
import com.magpie.pocket.core.model.RequestFilter
import com.magpie.pocket.core.model.RequestPage
import com.magpie.pocket.core.model.RequestSummary
import com.magpie.pocket.core.model.RouteGroup
import com.magpie.pocket.core.model.ThemeMode
import com.magpie.pocket.core.model.TraceSnapshot
import com.magpie.pocket.core.model.UsageSnapshot
import com.magpie.pocket.core.routing.RouteSettle
import com.magpie.pocket.core.routing.TraceRoutingState
import com.magpie.pocket.core.routing.chooseAutoRoute
import com.magpie.pocket.core.routing.detailFor
import com.magpie.pocket.core.routing.reduceTraceRouting
import com.magpie.pocket.core.network.AuthenticationRequiredException
import com.magpie.pocket.core.network.CloudflareAccessRequiredException
import com.magpie.pocket.core.network.MagpieApiClient
import com.magpie.pocket.core.network.MagpieApiException
import com.magpie.pocket.core.network.NetworkUnavailableException
import com.magpie.pocket.core.network.PermissionDeniedException
import com.magpie.pocket.core.network.ResourceNotFoundException
import com.magpie.pocket.core.network.UnexpectedResponseException
import com.magpie.pocket.core.security.CredentialStore
import com.magpie.pocket.core.security.PreferencesManager
import com.magpie.pocket.core.util.MetricCalculators
import com.magpie.pocket.core.util.RequestTimestamps
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

interface ConnectionStateSource {
    fun getConnection(): ConnectionConfig
    fun saveConnection(config: ConnectionConfig)
    fun clearCredentials()
}

interface DisplayPreferencesSource {
    val displayPreferencesFlow: kotlinx.coroutines.flow.Flow<DisplayPreferences>
    suspend fun updateThemeMode(mode: ThemeMode)
    suspend fun updateGlassTier(tier: GlassTier)
    suspend fun updateReduceMotion(enabled: Boolean)
    suspend fun updateMaskAccounts(enabled: Boolean)
    suspend fun updateKeepLiveConnectionInBackground(enabled: Boolean)
}

class CredentialStoreConnectionSource(private val store: CredentialStore) : ConnectionStateSource {
    override fun getConnection(): ConnectionConfig = store.getConnection()
    override fun saveConnection(config: ConnectionConfig) = store.saveConnection(config)
    override fun clearCredentials() = store.clearCredentials()
}

class PreferencesManagerDisplaySource(private val manager: PreferencesManager) : DisplayPreferencesSource {
    override val displayPreferencesFlow = manager.displayPreferencesFlow
    override suspend fun updateThemeMode(mode: ThemeMode) = manager.updateThemeMode(mode)
    override suspend fun updateGlassTier(tier: GlassTier) = manager.updateGlassTier(tier)
    override suspend fun updateReduceMotion(enabled: Boolean) = manager.updateReduceMotion(enabled)
    override suspend fun updateMaskAccounts(enabled: Boolean) = manager.updateMaskAccounts(enabled)
    override suspend fun updateKeepLiveConnectionInBackground(enabled: Boolean) =
        manager.updateKeepLiveConnectionInBackground(enabled)
}

class AppViewModel(
    private val credentialStore: ConnectionStateSource,
    private val preferencesManager: DisplayPreferencesSource,
    private val demoRepo: DemoRepository,
    private val apiClient: MagpieApiClient,
    private val keepaliveController: KeepaliveController = NoOpKeepaliveController,
    private val heartbeatIntervalMs: Long = 30_000L,
    private val retryDelaysMs: List<Long> = listOf(1_000L, 2_000L, 4_000L, 8_000L, 15_000L, 30_000L),
    private val clientFactory: (ConnectionConfig) -> MagpieApiClient = { config ->
        MagpieApiClient(
            cfAccessClientId = config.cfAccessClientId,
            cfAccessClientSecret = config.cfAccessClientSecret
        )
    }
) : ViewModel() {

    private var activityResumed = false
    private var serviceActive = false
    private var lastValidatedAtMs = 0L
    private var validationPending = false
    private var notificationPermissionRequestClaimed = false

    private val _connectionConfig = MutableStateFlow(credentialStore.getConnection())
    val connectionConfig: StateFlow<ConnectionConfig> = _connectionConfig.asStateFlow()

    private val _displayPreferences = MutableStateFlow(DisplayPreferences())
    val displayPreferences: StateFlow<DisplayPreferences> = _displayPreferences.asStateFlow()

    /** False until DataStore has emitted the persisted preferences at least once. */
    private val _displayPreferencesReady = MutableStateFlow(false)
    val displayPreferencesReady: StateFlow<Boolean> = _displayPreferencesReady.asStateFlow()

    private val _activeTab = MutableStateFlow(AppTab.OVERVIEW)
    val activeTab: StateFlow<AppTab> = _activeTab.asStateFlow()

    private val _trace = MutableStateFlow<TraceSnapshot?>(null)
    val trace: StateFlow<TraceSnapshot?> = _trace.asStateFlow()

    private val _overview = MutableStateFlow<OverviewSnapshot?>(null)
    val overview: StateFlow<OverviewSnapshot?> = _overview.asStateFlow()

    private val _groups = MutableStateFlow<List<RouteGroup>>(emptyList())
    val groups: StateFlow<List<RouteGroup>> = _groups.asStateFlow()

    private val _accounts = MutableStateFlow<List<Account>>(emptyList())
    val accounts: StateFlow<List<Account>> = _accounts.asStateFlow()

    private val _selectedGroupId = MutableStateFlow<String?>(null)
    val selectedGroupId: StateFlow<String?> = _selectedGroupId.asStateFlow()

    private val _usage = MutableStateFlow<UsageSnapshot?>(null)
    val usage: StateFlow<UsageSnapshot?> = _usage.asStateFlow()

    private val _quotas = MutableStateFlow<List<AccountQuota>>(emptyList())
    val quotas: StateFlow<List<AccountQuota>> = _quotas.asStateFlow()

    private val _requestsPage = MutableStateFlow<RequestPage?>(null)
    val requestsPage: StateFlow<RequestPage?> = _requestsPage.asStateFlow()

    private val _runningRequest = MutableStateFlow<RequestSummary?>(null)
    val runningRequest: StateFlow<RequestSummary?> = _runningRequest.asStateFlow()

    /** Newest three in-flight requests; the overview lists all of them, not just the first. */
    private val _runningRequests = MutableStateFlow<List<RequestSummary>>(emptyList())
    val runningRequests: StateFlow<List<RequestSummary>> = _runningRequests.asStateFlow()

    private val _selectedRequest = MutableStateFlow<RequestSummary?>(null)
    val selectedRequest: StateFlow<RequestSummary?> = _selectedRequest.asStateFlow()

    /** The route canvas focus. Unlike selectedRequest, live trace updates may advance it. */
    private val _routingRequest = MutableStateFlow<RequestSummary?>(null)
    val routingRequest: StateFlow<RequestSummary?> = _routingRequest.asStateFlow()

    /** Full context/decision evidence for the route canvas, independent of the modal. */
    private val _routingDetail = MutableStateFlow<RequestDetail?>(null)
    val routingDetail: StateFlow<RequestDetail?> = _routingDetail.asStateFlow()

    private val _isFollowingLive = MutableStateFlow(true)
    val isFollowingLive: StateFlow<Boolean> = _isFollowingLive.asStateFlow()

    private val _routeSettle = MutableStateFlow<RouteSettle?>(null)
    val routeSettle: StateFlow<RouteSettle?> = _routeSettle.asStateFlow()

    private var routingTraceState = TraceRoutingState()
    private var consumedSettleToken: Long = 0L

    private val _selectedRequestDetail = MutableStateFlow<RequestDetail?>(null)
    val selectedRequestDetail: StateFlow<RequestDetail?> = _selectedRequestDetail.asStateFlow()

    private val _isRequestDetailOpen = MutableStateFlow(false)
    val isRequestDetailOpen: StateFlow<Boolean> = _isRequestDetailOpen.asStateFlow()

    private val _selectedPeriod = MutableStateFlow(Period.TODAY)
    val selectedPeriod: StateFlow<Period> = _selectedPeriod.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _statusFilter = MutableStateFlow("ALL")
    val statusFilter: StateFlow<String> = _statusFilter.asStateFlow()

    private val _selectedDay = MutableStateFlow<String?>(null)
    val selectedDay: StateFlow<String?> = _selectedDay.asStateFlow()

    private val _isOnline = MutableStateFlow(true)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _connectionStatus = MutableStateFlow(ConnectionStatus(ConnectionHealth.CONNECTING))
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _capabilities = MutableStateFlow<Capabilities?>(null)
    val capabilities: StateFlow<Capabilities?> = _capabilities.asStateFlow()

    private val _providerCatalogState =
        MutableStateFlow<ProviderCatalogState>(ProviderCatalogState.Idle)
    val providerCatalogState: StateFlow<ProviderCatalogState> = _providerCatalogState.asStateFlow()

    private val _privacyState = MutableStateFlow<PrivacyState>(PrivacyState.Idle)
    val privacyState: StateFlow<PrivacyState> = _privacyState.asStateFlow()

    /**
     * Revealed masking values keyed by placeholder; only an explicit reveal writes here.
     * A present key with a null value means the upstream no longer holds that value.
     */
    private val _revealedMaskingValues = MutableStateFlow<Map<String, String?>>(emptyMap())
    val revealedMaskingValues: StateFlow<Map<String, String?>> = _revealedMaskingValues.asStateFlow()

    /*
     * Page-ready derivations. Screens read these instead of recomputing money, quota
     * ratios, day labels or token totals themselves, so one number has one definition.
     */

    /** Daily request counts plus the spend the upstream reported for the same day. */
    val dailyTrends: StateFlow<List<DailyTrendPoint>> = _overview
        .map { it?.dailyTrends.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Decode throughput for the selected usage period; same formula as the overview. */
    val usageDecodeSpeed: StateFlow<Double?> = _usage
        .map { MetricCalculators.decodeTokensPerSecond(it?.decodeOut, it?.decodeMs) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Overview spend that still reports how many calls the upstream could not price. */
    val overviewCost: StateFlow<CostDisplay> = _overview
        .map { CostDisplay(value = it?.cost, unpriced = it?.unpriced ?: 0L) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, CostDisplay(value = null, unpriced = 0L))

    /** Usage-period spend with the same unpriced tail as [overviewCost]. */
    val usageCost: StateFlow<CostDisplay> = _usage
        .map { CostDisplay(value = it?.cost, unpriced = it?.unpriced ?: 0L) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, CostDisplay(value = null, unpriced = 0L))

    /** Every account with exactly one deciding window, masked and alias-first. */
    val accountQuotas: StateFlow<List<AccountQuotaView>> = combine(
        _quotas, _accounts, _displayPreferences
    ) { quotas, accounts, preferences ->
        quotas.map { it.toView(accounts, preferences.maskAccountNames) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Windows already in the warning zone, worst first. */
    val quotaAlerts: StateFlow<List<QuotaAlert>> = accountQuotas
        .map { views ->
            views.mapNotNull { view ->
                val window = view.tightestWindow ?: return@mapNotNull null
                val ratio = view.ratio ?: return@mapNotNull null
                if (!MetricCalculators.isQuotaWarning(window)) return@mapNotNull null
                QuotaAlert(
                    accountLabel = view.label,
                    ratio = ratio,
                    windowId = window.id,
                    resetsAt = window.resetsAt
                )
            }.sortedByDescending { it.ratio }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The server's own statistics day as "MM-dd"; null until the overview names a range end. */
    val statisticsDay: StateFlow<String?> = combine(
        _capabilities, _overview, _requestsPage
    ) { capabilities, overview, page ->
        statisticsDayOf(capabilities, overview, page)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Coverage of everything currently shown; enum order is severity, so the worst wins. */
    val dataCoverage: StateFlow<DataCoverage> = combine(
        _overview, _usage, _requestsPage
    ) { overview, usage, page ->
        val coverage = listOfNotNull(overview?.coverage, usage?.coverage, page?.coverage)
            .maxByOrNull { it.ordinal }
            ?: Coverage.UNKNOWN
        DataCoverage(
            coverage = coverage,
            stale = page?.items?.any { it.meta.stale } == true
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DataCoverage(Coverage.UNKNOWN, false))

    /** Request-list filters the active connection can really apply. */
    val supportedRequestFilters: StateFlow<Set<RequestFilter>> = _connectionConfig
        .map { filtersFor(it.mode) }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            filtersFor(_connectionConfig.value.mode)
        )

    private var activeRepo: MagpieRepository = demoRepo
    private var repoGeneration: Long = 0L
    private var activeLiveClient: MagpieApiClient? = null
    private var refreshJob: Job? = null
    private var traceJob: Job? = null
    private var detailJob: Job? = null
    private var requestJob: Job? = null
    private var periodJob: Job? = null
    private var providerJob: Job? = null
    private var privacyJob: Job? = null
    private var revealJob: Job? = null
    private var heartbeatJob: Job? = null
    private var retryJob: Job? = null
    private var validationJob: Job? = null
    private var selectionGeneration: Long = 0L
    private var lifecycleStarted = false
    private var traceGeneration: Long = 0L
    private var routingTraceGeneration: Long = 0L

    init {
        // Collect display preferences
        viewModelScope.launch {
            preferencesManager.displayPreferencesFlow.collect { preferences ->
                val firstLoad = !_displayPreferencesReady.value
                _displayPreferences.value = preferences
                _displayPreferencesReady.value = true
                if (firstLoad) {
                    onPreferencesFirstLoaded()
                } else {
                    reconcileKeepalive()
                }
            }
        }

        // Initialize active repository
        setupRepository(_connectionConfig.value)
    }

    private fun setupRepository(config: ConnectionConfig) {
        repoGeneration += 1
        val generation = repoGeneration
        cancelAllNetworkJobs()
        selectionGeneration += 1
        lastValidatedAtMs = 0L
        validationPending = false
        _isLoading.value = false

        // Never let a previous connection's in-memory Magpie session leak into a new one.
        activeLiveClient?.cookieJar?.clear()
        activeLiveClient = null

        // Lazy provider/privacy views belong to the connection that is being replaced.
        cancelPrivacyJobs()
        _providerCatalogState.value = ProviderCatalogState.Idle
        _privacyState.value = PrivacyState.Idle
        _revealedMaskingValues.value = emptyMap()

        _trace.value = null
        _overview.value = null
        _groups.value = emptyList()
        _accounts.value = emptyList()
        _usage.value = null
        _quotas.value = emptyList()
        _requestsPage.value = null
        _runningRequest.value = null
        _runningRequests.value = emptyList()
        _routingRequest.value = null
        _routingDetail.value = null
        _isFollowingLive.value = true
        _routeSettle.value = null
        routingTraceState = TraceRoutingState()
        consumedSettleToken = 0L
        traceGeneration = 0L
        routingTraceGeneration = 0L
        _capabilities.value = null
        _selectedGroupId.value = null
        _selectedRequest.value = null
        _selectedRequestDetail.value = null
        _isRequestDetailOpen.value = false

        _isOnline.value = config.mode == DataMode.DEMO
        _connectionStatus.value = ConnectionStatus(
            if (config.mode == DataMode.DEMO) ConnectionHealth.CONNECTED
            else ConnectionHealth.CONNECTING
        )

        activeRepo = if (config.mode == DataMode.LIVE && !config.baseUrl.isNullOrBlank()) {
            val client = clientFactory(config)
            activeLiveClient = client
            LiveMagpieRepository(
                baseUrl = config.baseUrl,
                connectionId = config.id,
                apiClient = client,
                webKey = config.webKey
            )
        } else {
            demoRepo
        }

        reconcileKeepalive()
        when {
            config.mode == DataMode.DEMO -> refreshAll(generation)
            runtimeMayRun(generation) -> requestRecovery()
        }
    }

    fun refreshAll() {
        val generation = repoGeneration
        if (!runtimePermitted(generation)) return
        validationPending = false
        if (_connectionConfig.value.mode == DataMode.DEMO) {
            refreshAll(generation)
            return
        }
        clearAuthFailureForExplicitRetry()
        cancelAllNetworkJobs()
        startRecovery(generation)
    }

    private fun refreshAll(generation: Long) {
        if (!runtimeMayRun(generation)) return
        cancelRefreshAndRetry()
        refreshJob = viewModelScope.launch {
            try {
                performRefresh(generation)
                if (generation == repoGeneration) {
                    lastValidatedAtMs = System.currentTimeMillis()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation != repoGeneration) return@launch
                handleFailure(e, generation, clearTrace = true)
            } finally {
                if (generation == repoGeneration) {
                    _isLoading.value = false
                }
            }
        }
    }

    /**
     * Loads one complete snapshot. Callers own retry/backoff; this method never
     * swallows failures or starts a second recovery owner.
     */
    private suspend fun performRefresh(generation: Long) {
        if (!runtimeMayRun(generation)) return
        _isLoading.value = true
        if (_connectionConfig.value.mode == DataMode.LIVE && !isAuthFailureState()) {
            _connectionStatus.value = ConnectionStatus(ConnectionHealth.CONNECTING)
        }

        // Probe first: for live connections this establishes the Magpie session
        // (using the encrypted Web Key) before any protected API load or trace.
        val cap = activeRepo.probe()
        if (generation != repoGeneration) return
        _capabilities.value = cap

        if (cap.usage) {
            // Overview is always "today"; the usage period must not relabel it.
            val ov = activeRepo.overview(Period.TODAY)
            if (generation != repoGeneration) return
            _overview.value = ov
        }

        if (cap.groups) {
            val grps = activeRepo.groups()
            if (generation != repoGeneration) return
            _groups.value = grps
            val accs = activeRepo.accounts()
            if (generation != repoGeneration) return
            _accounts.value = accs
        }

        if (cap.usage) {
            val us = activeRepo.usage(_selectedPeriod.value)
            if (generation != repoGeneration) return
            _usage.value = us
        }

        if (cap.quotas) {
            val qts = activeRepo.quotas()
            if (generation != repoGeneration) return
            _quotas.value = qts
        }

        if (cap.ledger) loadRequests(generation = generation)

        if (generation != repoGeneration) return
        if (isAuthFailureState()) return
        if (!runtimeMayRun(generation)) return
        markConnected()
        lastValidatedAtMs = System.currentTimeMillis()
        validationPending = false
        if (cap.trace) {
            ensureTraceLoop(generation)
        } else {
            traceJob?.cancel()
            traceJob = null
            _trace.value = TraceSnapshot(
                epoch = "",
                seq = 0L,
                observable = false
            )
        }
        ensureHeartbeatLoop(generation)
        reconcileKeepalive()
    }

    private fun isAuthFailure(error: Throwable): Boolean =
        error is AuthenticationRequiredException ||
            error is PermissionDeniedException ||
            error is CloudflareAccessRequiredException

    /**
     * One failure path for trace, requests, periods and detail enrichment.
     * A 401/403 on the current connection is sticky until an explicit retry
     * or connection switch; late trace emissions cannot turn it green.
     */
    private fun handleFailure(error: Throwable, generation: Long, clearTrace: Boolean = false) {
        if (generation != repoGeneration) return
        val status = statusFor(error)
        if (isAuthFailure(error)) {
            cancelAllNetworkJobs()
            _isOnline.value = false
            _connectionStatus.value = status
            if (clearTrace) _trace.value = null
            return
        }
        if (isAuthFailureState()) return
        _connectionStatus.value = status
        _isOnline.value = false
    }

    private fun isAuthFailureState(): Boolean {
        val health = _connectionStatus.value.health
        return health == ConnectionHealth.AUTH_REQUIRED || health == ConnectionHealth.ACCESS_DENIED
    }

    private fun statusFor(error: Throwable): ConnectionStatus = when (error) {
        is AuthenticationRequiredException -> ConnectionStatus(
            ConnectionHealth.AUTH_REQUIRED,
            "需要 Magpie 登录：请在连接设置中填写有效的 Web Key（不是 Cloudflare Access 凭证）。"
        )
        is CloudflareAccessRequiredException -> ConnectionStatus(
            ConnectionHealth.ACCESS_DENIED,
            "Cloudflare Access 拒绝了访问：请检查 Service Token 或网络访问权限。"
        )
        is PermissionDeniedException -> ConnectionStatus(
            ConnectionHealth.ACCESS_DENIED,
            "访问被拒绝：请检查 Cloudflare Access Service Token 权限。"
        )
        is NetworkUnavailableException -> ConnectionStatus(
            ConnectionHealth.NETWORK_UNREACHABLE,
            "网络不可达：请检查网络连接与管理服务地址。"
        )
        is UnexpectedResponseException -> ConnectionStatus(
            ConnectionHealth.INCOMPATIBLE,
            "服务响应不兼容：该地址可能不是 Magpie 管理服务。"
        )
        is MagpieApiException -> ConnectionStatus(
            ConnectionHealth.OFFLINE,
            error.message ?: "连接服务失败，请稍后重试。"
        )
        else -> ConnectionStatus(ConnectionHealth.OFFLINE, "连接服务失败，请稍后重试。")
    }

    private fun ensureTraceLoop(generation: Long) {
        if (!runtimeMayRun(generation) || _capabilities.value?.trace != true) return
        if (traceJob?.isActive == true) return
        startObservingTrace(generation)
    }

    private fun ensureHeartbeatLoop(generation: Long) {
        if (_connectionConfig.value.mode != DataMode.LIVE) return
        if (!runtimeMayRun(generation) || _capabilities.value == null) return
        if (heartbeatJob?.isActive == true) return
        startHeartbeat(generation)
    }

    private fun startObservingTrace(generation: Long) {
        traceJob?.cancel()
        traceJob = viewModelScope.launch {
            var failures = 0
            while (isActive && generation == repoGeneration) {
                if (!runtimeMayRun(generation)) return@launch
                try {
                    activeRepo.observeTrace().collect { tr ->
                        if (generation != repoGeneration || !runtimeMayRun(generation)) return@collect
                        // A current-connection auth failure is sticky; an already
                        // in-flight trace emission must not clear it.
                        if (isAuthFailureState()) return@collect
                        failures = 0
                        applyTraceSnapshot(tr)
                    }
                    // The repository flow ended without cancellation: treat it as a
                    // recoverable interruption and re-establish the session.
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (generation != repoGeneration) return@launch
                    handleFailure(e, generation)
                    if (!isRetryable(e)) return@launch
                }
                if (generation != repoGeneration || !runtimeMayRun(generation)) return@launch
                failures += 1
                delay(retryDelay(failures))
            }
        }
    }

    private fun startHeartbeat(generation: Long) {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch {
            while (isActive && generation == repoGeneration) {
                delay(heartbeatIntervalMs)
                if (generation != repoGeneration) return@launch
                if (!runtimeMayRun(generation)) continue
                try {
                    activeRepo.probeLight()
                    if (generation != repoGeneration || !runtimeMayRun(generation)) return@launch
                    // A successful /api/state is a health signal, never permission to
                    // erase an auth/access failure from another endpoint.
                    if (!isAuthFailureState()) {
                        lastValidatedAtMs = System.currentTimeMillis()
                        markConnected()
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (generation != repoGeneration) return@launch
                    handleFailure(e, generation)
                    if (!isRetryable(e)) return@launch
                }
            }
        }
    }

    private fun retryDelay(failures: Int): Long =
        retryDelaysMs[(failures - 1).coerceIn(0, retryDelaysMs.lastIndex)]

    private fun isRetryable(error: Throwable): Boolean = when (error) {
        is AuthenticationRequiredException,
        is PermissionDeniedException,
        is CloudflareAccessRequiredException,
        is UnexpectedResponseException -> false
        is NetworkUnavailableException -> true
        is MagpieApiException -> error.statusCode == null || error.statusCode in 500..599
        else -> false
    }

    private fun markConnected() {
        if (isAuthFailureState()) return
        if (!runtimeMayRun(repoGeneration)) return
        _isOnline.value = true
        _connectionStatus.value = ConnectionStatus(ConnectionHealth.CONNECTED)
    }

    /** Demo is local; LIVE networking requires foreground or an active opted-in service. */
    private fun runtimeMayRun(generation: Long): Boolean {
        if (generation != repoGeneration) return false
        if (_connectionConfig.value.mode == DataMode.DEMO) return true
        if (isAuthFailureState()) return false
        return runtimePermitted(generation)
    }

    /** Explicit user actions may retry after auth recovery; automatic signals never use this. */
    private fun runtimePermitted(generation: Long): Boolean {
        if (generation != repoGeneration) return false
        if (_connectionConfig.value.mode == DataMode.DEMO) return true
        return activityResumed ||
            (_displayPreferencesReady.value &&
                _displayPreferences.value.keepLiveConnectionInBackground &&
                serviceActive)
    }

    private fun clearAuthFailureForExplicitRetry() {
        if (!isAuthFailureState()) return
        _connectionStatus.value = ConnectionStatus(ConnectionHealth.CONNECTING)
        _isOnline.value = false
    }

    fun onActivityStarted() {
        lifecycleStarted = true
        reconcileKeepalive()
    }

    fun onActivityResumed() {
        val wasResumed = activityResumed
        lifecycleStarted = true
        activityResumed = true
        if (_connectionConfig.value.mode == DataMode.DEMO) return
        ensureRuntimeLoops()
        if (!wasResumed) requestRuntimeValidation()
        reconcileKeepalive()
    }

    fun onActivityPaused() {
        activityResumed = false
        pauseOrStopRuntime()
    }

    fun onActivityStopped() {
        lifecycleStarted = false
        activityResumed = false
        pauseOrStopRuntime()
    }

    fun onKeepaliveServiceActive(active: Boolean) {
        val wasActive = serviceActive
        serviceActive = active
        if (active) {
            keepaliveController.onServiceActive()
            if (!wasActive && runtimeMayRun(repoGeneration)) {
                ensureRuntimeLoops()
                requestRuntimeValidation()
            }
        } else {
            keepaliveController.onServiceInactive()
            pauseOrStopRuntime()
        }
    }

    fun onNetworkAvailable() {
        if (_connectionConfig.value.mode != DataMode.LIVE) return
        validationPending = true
        requestRecovery()
    }

    fun onKeepalivePreferenceChanged(enabled: Boolean) {
        reconcileKeepalive()
    }

    /**
     * Claims the one process-lifetime notification prompt. Activity recreation must
     * not show the permission dialog again after the user denies it.
     */
    fun claimNotificationPermissionRequest(): Boolean {
        if (notificationPermissionRequestClaimed) return false
        notificationPermissionRequestClaimed = true
        return true
    }

    private fun pauseOrStopRuntime() {
        if (activityResumed) return
        val preferencesReady = _displayPreferencesReady.value
        if (!preferencesReady) {
            // An OS-recreated service may already be promoted while DataStore is
            // still loading. Do not run background work, but let that service wait
            // for the definitive preference instead of stopping it on the default.
            stopBackgroundNetwork()
            if (!serviceActive) keepaliveController.stop()
            return
        }
        val keep = _connectionConfig.value.mode == DataMode.LIVE &&
            _displayPreferences.value.keepLiveConnectionInBackground &&
            serviceActive
        if (!keep) {
            stopBackgroundNetwork()
        }
        // A service requested while the Activity was visible may still be starting
        // when Home is pressed. Let that valid start finish; networking remains
        // paused until the service reports foreground promotion.
        val mayKeepServiceStarting = _connectionConfig.value.mode == DataMode.LIVE &&
            _displayPreferences.value.keepLiveConnectionInBackground
        if (!mayKeepServiceStarting) {
            keepaliveController.stop()
        }
    }

    private fun onPreferencesFirstLoaded() {
        // A sticky service may have been recreated by the OS before DataStore
        // delivered the saved preference. Now that the choice is definitive,
        // either recover the opted-in runtime or tear everything down promptly.
        if (serviceActive && !activityResumed) {
            val live = _connectionConfig.value.mode == DataMode.LIVE
            if (live && _displayPreferences.value.keepLiveConnectionInBackground) {
                ensureRuntimeLoops()
                requestRuntimeValidation()
            } else {
                stopBackgroundNetwork()
            }
        }
        reconcileKeepalive()
    }

    private fun reconcileKeepalive() {
        if (!_displayPreferencesReady.value) {
            // Never start a foreground service from the default preference value.
            // Foreground LIVE networking remains available through activityResumed.
            if (!activityResumed) stopBackgroundNetwork()
            if (!serviceActive) keepaliveController.stop()
            return
        }
        val live = _connectionConfig.value.mode == DataMode.LIVE
        val enabled = _displayPreferences.value.keepLiveConnectionInBackground
        val visible = lifecycleStarted || activityResumed
        val shouldRun = live && enabled && (visible || serviceActive)
        if (shouldRun) {
            keepaliveController.start()
        } else {
            keepaliveController.stop()
            if (!activityResumed) stopBackgroundNetwork()
        }
    }

    private fun stopBackgroundNetwork() {
        // Demo data is local and remains available while the Activity is backgrounded.
        if (_connectionConfig.value.mode == DataMode.DEMO) return
        cancelAllNetworkJobs()
        validationPending = false
        _isLoading.value = false
    }

    private fun cancelRefreshAndRetry() {
        refreshJob?.cancel()
        refreshJob = null
        retryJob?.cancel()
        retryJob = null
    }

    private fun cancelRuntimeJobs() {
        validationJob?.cancel()
        validationJob = null
        refreshJob?.cancel()
        refreshJob = null
        traceJob?.cancel()
        traceJob = null
        heartbeatJob?.cancel()
        heartbeatJob = null
        retryJob?.cancel()
        retryJob = null
    }

    private fun cancelAllNetworkJobs() {
        cancelRuntimeJobs()
        requestJob?.cancel()
        requestJob = null
        detailJob?.cancel()
        detailJob = null
        periodJob?.cancel()
        periodJob = null
    }

    private fun ensureRuntimeLoops() {
        val generation = repoGeneration
        if (!runtimeMayRun(generation)) return
        if (_capabilities.value?.trace == true) ensureTraceLoop(generation)
        ensureHeartbeatLoop(generation)
    }

    /** Foreground/network/service signals share one bounded, deduplicated recovery loop. */
    private fun requestRecovery() {
        val generation = repoGeneration
        if (_connectionConfig.value.mode != DataMode.LIVE) return
        if (!runtimeMayRun(generation)) return
        if (isAuthFailureState()) return
        if (retryJob?.isActive == true) return
        if (refreshJob?.isActive == true) return
        if (validationJob?.isActive == true) return

        if (_capabilities.value == null) {
            validationPending = false
            startRecovery(generation)
            return
        }

        if (validationPending) {
            validationPending = false
            startLightweightValidation(generation)
            return
        }

        val now = System.currentTimeMillis()
        val recentlyValidated = lastValidatedAtMs > 0L &&
            now - lastValidatedAtMs < heartbeatIntervalMs
        val loopsAlive = heartbeatJob?.isActive == true &&
            (_capabilities.value?.trace != true || traceJob?.isActive == true)
        if (recentlyValidated && loopsAlive && _connectionStatus.value.health == ConnectionHealth.CONNECTED) {
            return
        }
        startRecovery(generation)
    }

    private fun startRecovery(generation: Long) {
        if (retryJob?.isActive == true || refreshJob?.isActive == true) return
        validationJob?.cancel()
        validationJob = null
        refreshJob?.cancel()
        refreshJob = null
        retryJob?.cancel()
        retryJob = viewModelScope.launch {
            var attempt = 0
            try {
                while (isActive && generation == repoGeneration) {
                    if (!runtimeMayRun(generation)) return@launch
                    try {
                        // A fresh probe/load repairs both initial startup and a stale status.
                        performRefresh(generation)
                        if (generation != repoGeneration) return@launch
                        lastValidatedAtMs = System.currentTimeMillis()
                        validationPending = false
                        ensureRuntimeLoops()
                        return@launch
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        if (generation != repoGeneration) return@launch
                        handleFailure(e, generation, clearTrace = true)
                        if (!isRetryable(e) || isAuthFailureState()) return@launch
                        val wait = retryDelaysMs.getOrElse(attempt) { retryDelaysMs.lastOrNull() ?: 0L }
                        if (attempt < retryDelaysMs.lastIndex) attempt += 1
                        delay(wait)
                    }
                }
            } finally {
                if (generation == repoGeneration) {
                    _isLoading.value = false
                }
            }
        }
    }

    private fun requestRuntimeValidation() {
        if (_connectionConfig.value.mode != DataMode.LIVE) return
        validationPending = true
        requestRecovery()
    }

    /** A resume/network signal validates without replacing a healthy long-poll trace job. */
    private fun startLightweightValidation(generation: Long) {
        validationJob?.cancel()
        validationJob = viewModelScope.launch {
            try {
                activeRepo.probeLight()
                if (
                    generation == repoGeneration &&
                    runtimeMayRun(generation) &&
                    !isAuthFailureState()
                ) {
                    lastValidatedAtMs = System.currentTimeMillis()
                    markConnected()
                    ensureRuntimeLoops()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation != repoGeneration) return@launch
                handleFailure(e, generation)
                if (isRetryable(e) && !isAuthFailureState() && runtimeMayRun(generation)) {
                    startRecovery(generation)
                }
            } finally {
                if (generation == repoGeneration) validationPending = false
            }
        }
    }

    internal fun applyTraceSnapshot(tr: TraceSnapshot) {
        if (isAuthFailureState()) return
        if (_connectionConfig.value.mode == DataMode.LIVE && !runtimeMayRun(repoGeneration)) return
        // Trace evidence can only bind to the current live epoch. When an epoch
        // restarts, the ledger belongs to the previous epoch and must not select a
        // new live route by a recycled route id.
        val previousEpoch = _trace.value?.epoch
        val epochRestarted = tr.restarted || (previousEpoch != null && previousEpoch != tr.epoch)
        if (epochRestarted) {
            traceGeneration += 1
            // Old ledger rows cannot become evidence for the new epoch. This also
            // covers later snapshots that no longer carry the one-shot restart flag.
            requestJob?.cancel()
            requestJob = null
            _requestsPage.value = null
        }
        _trace.value = tr
        _isOnline.value = true
        _connectionStatus.value = ConnectionStatus(ConnectionHealth.CONNECTED)
        lastValidatedAtMs = System.currentTimeMillis()

        val ledger = if (epochRestarted) emptyList() else _requestsPage.value?.items.orEmpty()
        routingTraceState = reduceTraceRouting(
            routingTraceState,
            tr,
            selectedGroupId = _selectedGroupId.value,
            focusedRouteId = _routingRequest.value?.key?.routeId
        )
        if (epochRestarted) {
            consumedSettleToken = 0L
            _routeSettle.value = null
        }
        routingTraceState.settles
            .lastOrNull { settle ->
                settle.token > consumedSettleToken &&
                    tr.recentRoutes.find { it.key.routeId == settle.routeId }?.groupId == _selectedGroupId.value
            }
            ?.let { _routeSettle.value = it }

        val runningIds = tr.runningRouteIds.toSet()
        val running = tr.recentRoutes
            .filter { it.key.routeId in runningIds }
            .sortedByDescending { RequestTimestamps.parseMillis(it.startedAt) ?: Long.MIN_VALUE }
        _runningRequests.value = running.take(RUNNING_REQUEST_LIMIT)
        _runningRequest.value = running.firstOrNull()

        if (_isFollowingLive.value) {
            val live = chooseAutoRoute(tr, ledger)
            if (live != null) {
                _selectedGroupId.value = live.groupId
                _routingRequest.value = live
                _routingDetail.value = tr.detailFor(live)
                if (!_isRequestDetailOpen.value) _selectedRequest.value = live
            } else {
                _routingRequest.value = null
                _routingDetail.value = null
                _selectedGroupId.value = null
                if (!_isRequestDetailOpen.value) {
                    _selectedRequest.value = null
                    _selectedRequestDetail.value = null
                }
            }
            return
        }

        // A historical pin keeps its own group identity. Only refresh the pinned
        // route when the exact connection/epoch/route key still exists.
        val focused = _routingRequest.value ?: return
        if (epochRestarted) {
            // Keep the historical summary visible as pinned/unavailable, but detach
            // it from new-epoch evidence. Do not bind by route id alone.
            _routingDetail.value = null
        } else if (routingTraceGeneration == traceGeneration) {
            val refreshed = tr.recentRoutes.find { route ->
                route.key.connectionId == focused.key.connectionId &&
                    route.key.epoch == focused.key.epoch &&
                    route.key.routeId == focused.key.routeId
            }
            if (refreshed != null) {
                _routingRequest.value = refreshed
                _routingDetail.value = tr.detailFor(refreshed) ?: _routingDetail.value
            }
        } else {
            _routingDetail.value = null
        }
    }

    fun loadRequests(append: Boolean = false) {
        loadRequests(repoGeneration, append)
    }

    private fun loadRequests(generation: Long, append: Boolean = false) {
        requestJob?.cancel()
        requestJob = viewModelScope.launch {
            val currentOffset = if (append) {
                val loaded = _requestsPage.value
                loaded?.nextOffset ?: loaded?.let { it.offset + it.items.size } ?: 0
            } else {
                0
            }
            val query = RequestQuery(
                offset = currentOffset,
                limit = 50,
                query = _searchQuery.value,
                statusFilter = _statusFilter.value,
                day = _selectedDay.value
            )

            try {
                val page = activeRepo.requests(query)
                if (generation != repoGeneration) return@launch
                if (append && _requestsPage.value != null) {
                    val combined = (_requestsPage.value?.items ?: emptyList()) + page.items
                    _requestsPage.value = page.copy(items = combined)
                } else {
                    _requestsPage.value = page
                    reconcileRoutingSelection(page.items)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation != repoGeneration) return@launch
                handleFailure(e, generation)
            }
        }
    }

    fun selectRequest(request: RequestSummary) {
        selectionGeneration += 1
        detailJob?.cancel()
        _selectedRequest.value = request
        _routingRequest.value = request
        _isFollowingLive.value = false
        routingTraceGeneration = traceGeneration
        _selectedGroupId.value = request.groupId
        _isRequestDetailOpen.value = true
        _selectedRequestDetail.value = _trace.value?.detailFor(request)
        loadRequestDetail(request, selectionGeneration)
        _routingDetail.value = _trace.value?.detailFor(request)
    }

    /** Selects the routing focus without opening the request-detail modal. */
    fun selectRoutingRequest(request: RequestSummary?) {
        selectionGeneration += 1
        detailJob?.cancel()
        _selectedRequest.value = request
        _routingRequest.value = request
        _isFollowingLive.value = false
        routingTraceGeneration = traceGeneration
        _selectedGroupId.value = request?.groupId
        _routingDetail.value = request?.let { _trace.value?.detailFor(it) }
        if (request != null) loadRequestDetail(request, selectionGeneration)
    }

    private fun loadRoutingDetail(request: RequestSummary, generation: Long) {
        detailJob?.cancel()
        val expectedTraceGeneration = traceGeneration
        detailJob = viewModelScope.launch {
            try {
                val detail = activeRepo.requestDetail(request.key)
                if (
                    generation == selectionGeneration &&
                    expectedTraceGeneration == traceGeneration &&
                    _routingRequest.value?.key == request.key
                ) {
                    _routingDetail.value = detail
                }
            } catch (_: Exception) {}
        }
    }

    private fun loadRoutingDetailIfMissing(request: RequestSummary) {
        if (_routingDetail.value?.summary?.key == request.key) return
        val cached = _trace.value?.detailFor(request)
        if (cached != null) {
            _routingDetail.value = cached
            return
        }
        loadRoutingDetail(request, selectionGeneration)
    }

    private fun loadRequestDetail(request: RequestSummary, generation: Long) {
        detailJob?.cancel()
        val expectedTraceGeneration = traceGeneration
        detailJob = viewModelScope.launch {
            try {
                val detail = activeRepo.requestDetail(request.key)
                // Both the full key and the selection generation must still match. A late
                // response for request A can never overwrite the current request B.
                if (
                    generation == selectionGeneration &&
                    expectedTraceGeneration == traceGeneration &&
                    _selectedRequest.value?.key == request.key
                ) {
                    _selectedRequestDetail.value = detail
                    if (_routingRequest.value?.key == request.key) _routingDetail.value = detail
                }
            } catch (_: Exception) {}
        }
    }

    fun closeRequestDetail() {
        _isRequestDetailOpen.value = false
    }

    fun selectTab(tab: AppTab) {
        _activeTab.value = tab
    }

    /**
     * Automatic routing focus is global and always follows the actual request. A missing
     * match clears the focus instead of leaving a request from another group on screen.
     */
    private fun reconcileRoutingSelection(items: List<RequestSummary>, force: Boolean = false) {
        val current = _routingRequest.value
        val auto = chooseAutoRoute(_trace.value, items)
        if (!force && _isFollowingLive.value && current?.key == auto?.key) return
        if (!_isFollowingLive.value && current != null) return

        val latest = auto
        if (latest == null) {
            selectionGeneration += 1
            detailJob?.cancel()
            _selectedRequest.value = null
            _routingRequest.value = null
            _selectedRequestDetail.value = null
            _routingDetail.value = null
        } else if (_isFollowingLive.value) {
            focusLiveRoute(latest)
        }
    }

    private fun focusLiveRoute(request: RequestSummary) {
        selectionGeneration += 1
        detailJob?.cancel()
        _routingRequest.value = request
        _selectedGroupId.value = request.groupId
        if (!_isRequestDetailOpen.value) _selectedRequest.value = request
        _routingDetail.value = _trace.value?.detailFor(request)
    }

    fun setPeriod(period: Period) {
        _selectedPeriod.value = period
        val generation = repoGeneration
        periodJob?.cancel()
        periodJob = viewModelScope.launch {
            try {
                // Switching the usage period never changes the overview's own scope.
                val overview = activeRepo.overview(Period.TODAY)
                if (generation != repoGeneration) return@launch
                _overview.value = overview
                val usage = activeRepo.usage(period)
                if (generation != repoGeneration) return@launch
                _usage.value = usage
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation != repoGeneration) return@launch
                handleFailure(e, generation)
            }
        }
    }

    /**
     * Lazy provider catalog load. It never participates in the periodic refresh; a broker
     * endpoint failure only marks this view as failed instead of flipping the connection.
     */
    fun loadProviders(force: Boolean = false) {
        val current = _providerCatalogState.value
        if (!force && (current is ProviderCatalogState.Ready || current is ProviderCatalogState.Loading)) {
            return
        }
        if (_capabilities.value?.providers == false) {
            _providerCatalogState.value = ProviderCatalogState.Unsupported
            return
        }
        val generation = repoGeneration
        providerJob?.cancel()
        _providerCatalogState.value = ProviderCatalogState.Loading
        providerJob = viewModelScope.launch {
            try {
                val providers = activeRepo.providers()
                if (generation != repoGeneration) return@launch
                _providerCatalogState.value = ProviderCatalogState.Ready(providers)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ResourceNotFoundException) {
                if (generation != repoGeneration) return@launch
                _providerCatalogState.value = ProviderCatalogState.Unsupported
            } catch (e: Exception) {
                if (generation != repoGeneration) return@launch
                // Auth failures keep the sticky connection status; other failures stay local.
                if (isAuthFailure(e)) handleFailure(e, generation)
                _providerCatalogState.value = ProviderCatalogState.Error(
                    statusFor(e).message ?: "供应商目录加载失败"
                )
            }
        }
    }

    /**
     * Lazy privacy load: one summary plus the first record page for [days]. [kind] keeps
     * the active record filter across day switches; null means every kind.
     */
    fun loadPrivacy(days: Int, force: Boolean = false, kind: String? = null) {
        val current = _privacyState.value
        if (!force) {
            if (current is PrivacyState.Ready && current.selectedDays == days && current.selectedKind == kind) return
            if (current is PrivacyState.Loading && current.selectedDays == days && current.selectedKind == kind) return
        }
        if (_capabilities.value?.masking == false) {
            _privacyState.value = PrivacyState.Unsupported
            return
        }
        val generation = repoGeneration
        privacyJob?.cancel()
        // A new query invalidates every original revealed for the previous result set.
        revealJob?.cancel()
        revealJob = null
        _revealedMaskingValues.value = emptyMap()
        _privacyState.value = PrivacyState.Loading(days, kind)
        privacyJob = viewModelScope.launch {
            try {
                val summary = activeRepo.maskingSummary(days)
                if (generation != repoGeneration) return@launch
                val page = activeRepo.maskingRecords(
                    MaskingQuery(limit = PRIVACY_PAGE_SIZE, kind = kind)
                )
                if (generation != repoGeneration) return@launch
                _privacyState.value = PrivacyState.Ready(
                    selectedDays = days,
                    summary = summary,
                    records = page.items,
                    more = page.more,
                    selectedKind = kind
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: ResourceNotFoundException) {
                if (generation != repoGeneration) return@launch
                _privacyState.value = PrivacyState.Unsupported
            } catch (e: Exception) {
                if (generation != repoGeneration) return@launch
                if (isAuthFailure(e)) handleFailure(e, generation)
                _privacyState.value = PrivacyState.Error(
                    selectedDays = days,
                    message = statusFor(e).message ?: "隐私掩码数据加载失败",
                    selectedKind = kind
                )
            }
        }
    }

    /** Appends the next masking-records page using the last record key as its cursor. */
    fun loadMoreMaskingRecords() {
        val current = _privacyState.value as? PrivacyState.Ready ?: return
        if (!current.more || current.records.isEmpty()) return
        if (privacyJob?.isActive == true) return
        val before = current.records.last().key ?: return
        val generation = repoGeneration
        privacyJob = viewModelScope.launch {
            try {
                val page = activeRepo.maskingRecords(
                    MaskingQuery(
                        limit = PRIVACY_PAGE_SIZE,
                        before = before,
                        kind = current.selectedKind
                    )
                )
                if (generation != repoGeneration) return@launch
                val latest = _privacyState.value as? PrivacyState.Ready ?: return@launch
                if (latest.selectedDays != current.selectedDays) return@launch
                if (latest.selectedKind != current.selectedKind) return@launch
                _privacyState.value = latest.copy(
                    records = latest.records + page.items,
                    more = page.more
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation != repoGeneration) return@launch
                // Keep the already loaded page; a failed append never clears evidence.
                if (isAuthFailure(e)) handleFailure(e, generation)
            }
        }
    }

    /**
     * Reveals one masked placeholder. A null upstream answer is cached as null so the UI
     * can report that the value is no longer in memory instead of showing nothing.
     */
    fun revealMaskingValue(placeholder: String) {
        if (placeholder.isBlank()) return
        if (_revealedMaskingValues.value.containsKey(placeholder)) return
        // Only one reveal is shown at a time; a newer request supersedes the older one.
        revealJob?.cancel()
        val generation = repoGeneration
        revealJob = viewModelScope.launch {
            try {
                val value = activeRepo.maskingValue(placeholder)
                if (generation != repoGeneration) return@launch
                _revealedMaskingValues.value =
                    _revealedMaskingValues.value + (placeholder to value)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation != repoGeneration) return@launch
                if (isAuthFailure(e)) handleFailure(e, generation)
            }
        }
    }

    /** Drops every cached original; the sheet calls this when it closes. */
    fun clearRevealedMaskingValues() {
        revealJob?.cancel()
        revealJob = null
        _revealedMaskingValues.value = emptyMap()
    }

    /** Switches the record-kind filter and reloads from the first page. */
    fun setPrivacyKind(kind: String?) {
        val current = _privacyState.value
        if (current.selectedKind == kind) return
        val days = current.selectedDays ?: return
        loadPrivacy(days, force = true, kind = kind)
    }

    fun setPrivacyDays(days: Int) {
        loadPrivacy(days, force = true, kind = _privacyState.value.selectedKind)
    }

    private fun cancelPrivacyJobs() {
        providerJob?.cancel()
        providerJob = null
        privacyJob?.cancel()
        privacyJob = null
        revealJob?.cancel()
        revealJob = null
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
        loadRequests(append = false)
    }

    fun setStatusFilter(filter: String) {
        _statusFilter.value = filter
        loadRequests(append = false)
    }

    fun setSelectedDay(day: String?) {
        _selectedDay.value = day
        loadRequests(append = false)
    }

    fun resetToLatest() {
        _isFollowingLive.value = true
        consumedSettleToken = routingTraceState.settle?.token ?: consumedSettleToken
        _routeSettle.value = null
        val latest = chooseAutoRoute(
            _trace.value,
            _requestsPage.value?.items ?: emptyList()
        )
        _selectedGroupId.value = latest?.groupId
        if (latest != null) {
            focusLiveRoute(latest)
        } else {
            _routingRequest.value = null
            _routingDetail.value = null
            if (!_isRequestDetailOpen.value) {
                _selectedRequest.value = null
                _selectedRequestDetail.value = null
            }
        }
    }

    fun consumeRouteSettle(token: Long) {
        consumedSettleToken = maxOf(consumedSettleToken, token)
        if (_routeSettle.value?.token == token) _routeSettle.value = null
    }

    fun saveConnection(newConfig: ConnectionConfig) {
        credentialStore.saveConnection(newConfig)
        _connectionConfig.value = newConfig
        setupRepository(newConfig)
    }

    fun disconnect() {
        // The active connection always falls back to demo, even when the keystore refuses
        // to erase the saved key: a failed wipe must never keep the live session alive.
        runCatching { credentialStore.clearCredentials() }
        val demoConfig = ConnectionConfig(
            id = "demo-main",
            name = "主网关",
            mode = DataMode.DEMO
        )
        _connectionConfig.value = demoConfig
        setupRepository(demoConfig)
    }

    fun updateTheme(mode: ThemeMode) {
        viewModelScope.launch { preferencesManager.updateThemeMode(mode) }
    }

    fun updateGlassTier(tier: GlassTier) {
        viewModelScope.launch { preferencesManager.updateGlassTier(tier) }
    }

    fun updateReduceMotion(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.updateReduceMotion(enabled) }
    }

    fun updateMaskAccounts(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.updateMaskAccounts(enabled) }
    }

    /**
     * A connection-specific filter set: the ledger applies ERROR server-side while
     * RUNNING and REROUTED are answered from the same rows locally.
     */
    private fun filtersFor(mode: DataMode): Set<RequestFilter> = when (mode) {
        DataMode.LIVE -> LIVE_REQUEST_FILTERS
        DataMode.DEMO -> DEMO_REQUEST_FILTERS
    }

    /**
     * The upstream announces its statistics timezone; that zone decides which calendar
     * day a UTC range end belongs to. Without a zone the literal server date is used
     * instead of guessing from the device zone.
     */
    private fun statisticsDayOf(
        capabilities: Capabilities?,
        overview: OverviewSnapshot?,
        page: RequestPage?
    ): String? {
        val rangeEnd = overview?.rangeEnd?.trim().orEmpty()
        if (rangeEnd.isEmpty()) return null
        val zoneId = capabilities?.statisticsTimeZone?.takeIf { it.isNotBlank() }
            ?: page?.items?.firstOrNull()?.meta?.statisticsTimeZone?.takeIf { it.isNotBlank() }
        if (zoneId == null) {
            return IsoDayPrefix.find(rangeEnd)?.let {
                it.groupValues[2] + "-" + it.groupValues[3]
            }
        }
        val millis = RequestTimestamps.parseMillis(rangeEnd) ?: return null
        return SimpleDateFormat("MM-dd", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone(zoneId) }
            .format(Date(millis))
    }

    /** Alias first, then the account id, then the user's masking preference. */
    private fun AccountQuota.toView(
        accounts: List<Account>,
        maskAccountNames: Boolean
    ): AccountQuotaView {
        val alias = accounts.firstOrNull { it.id == accountId }?.alias
        val tightest = windows
            .mapNotNull { window ->
                val ratio = MetricCalculators.calculateQuotaUsedRatio(window)
                    ?: return@mapNotNull null
                window to ratio
            }
            .maxByOrNull { it.second }
        return AccountQuotaView(
            accountId = accountId,
            label = MetricCalculators.maskAccountName(alias ?: accountId, maskAccountNames),
            known = known,
            tightestWindow = tightest?.first,
            ratio = tightest?.second
        )
    }

    fun updateKeepLiveConnectionInBackground(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.updateKeepLiveConnectionInBackground(enabled)
            onKeepalivePreferenceChanged(enabled)
        }
    }

    override fun onCleared() {
        cancelAllNetworkJobs()
        cancelPrivacyJobs()
        keepaliveController.stop()
        super.onCleared()
    }
}

private const val PRIVACY_PAGE_SIZE = 40

/** The overview lists at most three in-flight requests. */
private const val RUNNING_REQUEST_LIMIT = 3

/** ERROR is server-side; RUNNING and REROUTED are applied to the returned ledger rows. */
private val LIVE_REQUEST_FILTERS: Set<RequestFilter> = setOf(
    RequestFilter.ALL, RequestFilter.RUNNING, RequestFilter.REROUTED, RequestFilter.ERROR
)

/** Demo data implements every filter locally. */
private val DEMO_REQUEST_FILTERS: Set<RequestFilter> = RequestFilter.entries.toSet()

private val IsoDayPrefix = Regex("^(\\d{4})-(\\d{2})-(\\d{2})")
