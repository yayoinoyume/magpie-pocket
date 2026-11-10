package com.magpie.pocket.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class DataMode {
    DEMO, LIVE
}

@Serializable
enum class Coverage {
    COMPLETE, RECENT_ONLY, PARTIAL, UNKNOWN
}

@Serializable
enum class FinalState {
    PENDING, IN_PROGRESS, SUCCEEDED, FAILED, CANCELLED, UNCONFIRMED
}

@Serializable
enum class Period {
    TODAY, SEVEN_DAYS, THIRTY_DAYS, ALL
}

@Serializable
enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

@Serializable
enum class GlassTier {
    CLEAR, TINTED, SOLID
}

enum class ConnectionHealth {
    CONNECTING, CONNECTED, AUTH_REQUIRED, ACCESS_DENIED, NETWORK_UNREACHABLE, INCOMPATIBLE, OFFLINE
}

data class ConnectionStatus(
    val health: ConnectionHealth,
    val message: String? = null
)

@Serializable
data class SourceMeta(
    val connectionId: String,
    val mode: DataMode,
    val observedAt: String,
    val fetchedAt: String,
    val statisticsTimeZone: String? = null,
    val coverage: Coverage = Coverage.COMPLETE,
    val stale: Boolean = false
)

@Serializable
data class RequestKey(
    val connectionId: String,
    val epoch: String,
    val routeId: String,
    val sourceDay: String? = null
)

@Serializable
data class TokenUsage(
    val inputUncached: Long? = 0L,
    val output: Long? = 0L,
    val cacheRead: Long? = 0L,
    val cacheWrite: Long? = 0L,
    val reasoningIncludedInOutput: Long? = 0L
)

@Serializable
data class Attempt(
    val index: Int,
    val providerId: String? = null,
    val accountId: String? = null,
    val model: String? = null,
    val startedAt: String? = null,
    val httpStatus: Int? = null,
    val durationMs: Long? = null,
    val ttftMs: Long? = null,
    val errorCode: String? = null,
    val handedOn: Boolean = false
)

@Serializable
data class RequestSummary(
    val key: RequestKey,
    val startedAt: String,
    val agentId: String? = null,
    val groupId: String? = null,
    val requestedModel: String? = null,
    val servedModel: String? = null,
    val providerId: String? = null,
    val accountId: String? = null,
    val finalState: FinalState,
    val rerouteCount: Int = 0,
    val ttftMs: Long? = null,
    val durationMs: Long? = null,
    val usage: TokenUsage? = null,
    val meta: SourceMeta,
    val attempts: List<Attempt> = emptyList()
)

@Serializable
data class RouteGroup(
    val id: String,
    val name: String,
    val strategy: String? = null,
    val affinity: String? = null,
    val enabledMemberCount: Int = 0,
    val providerIds: List<String> = emptyList(),
    val detailsComplete: Boolean = true
)

@Serializable
data class Account(
    val id: String,
    val providerId: String,
    val alias: String,
    val availability: String = "AVAILABLE", // AVAILABLE, BUSY, COOLDOWN, UNKNOWN
    val cooldownUntil: String? = null
)

@Serializable
data class QuotaWindow(
    val id: String,
    val unit: String,
    val used: Long,
    val limit: Long,
    val resetsAt: String? = null,
    /** Upstream's used share, 0..100, when the server reports a percentage. */
    val usedPercent: Double? = null,
    /** Upstream's own count and allowance, which can be fractional. */
    val amount: Double? = null,
    val allowanceLimit: Double? = null,
    val display: String? = null,
    val unlimited: Boolean = false,
    val known: Boolean = true
)

@Serializable
data class AccountQuota(
    val accountId: String,
    val known: Boolean,
    val observedAt: String? = null,
    val windows: List<QuotaWindow> = emptyList()
)

@Serializable
data class ContextPart(
    val kind: String,
    val tokens: Long
)

@Serializable
data class ContextBreakdown(
    val requestKey: RequestKey,
    val totalTokens: Long,
    val windowLimit: Long? = null,
    val estimated: Boolean = false,
    val parts: List<ContextPart> = emptyList()
)

@Serializable
data class TraceTotals(
    val completedRequests: Long = 0L,
    val rerouteHandoffs: Long = 0L,
    val finalErrors: Long = 0L
)

@Serializable
data class TraceSnapshot(
    val epoch: String,
    val seq: Long,
    val serverNow: String? = null,
    val observable: Boolean = true,
    /** True when the trace sequence/epoch restarted; no old completion may replay. */
    val restarted: Boolean = false,
    val retainedRouteIds: List<String> = emptyList(),
    val runningRouteIds: List<String> = emptyList(),
    /** Normalized recent routes from the same trace response/cache; never an N+1 fetch. */
    val recentRoutes: List<RequestSummary> = emptyList(),
    /** Full details mapped from the same cached routes as [recentRoutes]. */
    val recentDetails: List<RequestDetail> = emptyList(),
    val totals: TraceTotals = TraceTotals()
)

@Serializable
data class HourlyTrendPoint(
    val hourLabel: String,
    val completedRequests: Long
)

@Serializable
data class DailyTrendPoint(
    val dayLabel: String,
    val completedRequests: Long
)

@Serializable
data class OverviewSnapshot(
    val period: Period = Period.TODAY,
    val rangeStart: String? = null,
    val rangeEnd: String? = null,
    val coverage: Coverage = Coverage.COMPLETE,
    val completedRequests: Long = 0L,
    val finalErrors: Long = 0L,
    val ttftSumMs: Long? = null,
    val ttftSampleCount: Long? = null,
    val sameTimeYesterdayCompletedRequests: Long? = null,
    val hourlyTrends: List<HourlyTrendPoint> = emptyList(),
    /** Upstream spend for the period; null when the server reports no priced amount. */
    val cost: Double? = null,
    /** Calls the upstream could not price, never a guessed zero. */
    val unpriced: Long? = null,
    val decodeMs: Long? = null,
    val decodeOut: Long? = null,
    val dailyTrends: List<DailyTrendPoint> = emptyList()
)

@Serializable
data class UsageModelItem(
    val providerId: String,
    val modelId: String,
    val totalTokens: Long
)

@Serializable
data class UsageSnapshot(
    val period: Period = Period.TODAY,
    val coverage: Coverage = Coverage.COMPLETE,
    val tokens: TokenUsage = TokenUsage(),
    val models: List<UsageModelItem> = emptyList(),
    val cost: Double? = null,
    val unpriced: Long? = null,
    val credits: Double? = null,
    /** Upstream decode timing for the period; both stay null when it did not measure them. */
    val decodeMs: Long? = null,
    val decodeOut: Long? = null,
    val agents: List<UsageBreakdownItem> = emptyList(),
    val sessions: List<UsageBreakdownItem> = emptyList(),
    val accounts: List<UsageBreakdownItem> = emptyList(),
    val callerKeys: List<UsageBreakdownItem> = emptyList(),
    val providerKeys: List<UsageBreakdownItem> = emptyList()
)

/** One row of a usage breakdown dimension (agent, session, account, caller key, provider key). */
@Serializable
data class UsageBreakdownItem(
    val id: String? = null,
    val label: String? = null,
    val provider: String? = null,
    val calls: Long? = null,
    val cost: Double? = null,
    val credits: Double? = null,
    val errors: Long? = null,
    val input: Long? = null,
    val output: Long? = null,
    val cacheRead: Long? = null,
    val cacheWrite: Long? = null,
    val unpriced: Long? = null,
    val reasoning: Long? = null,
    val ttftMs: Long? = null,
    val timed: Long? = null,
    val decodeOut: Long? = null,
    val decodeMs: Long? = null
)

@Serializable
data class Capabilities(
    val trace: Boolean = true,
    val history: Boolean = true,
    val requestDetail: Boolean = true,
    val usage: Boolean = true,
    val ledger: Boolean = true,
    val quotas: Boolean = true,
    val groups: Boolean = true,
    val providers: Boolean = false,
    val masking: Boolean = false,
    val statisticsTimeZone: String? = null,
    val verifiedDeploymentVersion: String? = null
)

@Serializable
data class RequestDetail(
    val summary: RequestSummary,
    val attempts: List<Attempt> = emptyList(),
    val decisionEvidence: List<String> = emptyList(),
    val context: ContextBreakdown? = null,
    val sanitizedError: String? = null,
    val sourceMeta: SourceMeta
)

@Serializable
data class RequestPage(
    val items: List<RequestSummary>,
    val offset: Int,
    val total: Int,
    val hasMore: Boolean,
    val source: String = "gateway",
    val coverage: Coverage = Coverage.COMPLETE,
    val sourceDay: String? = null
)

@Serializable
data class ConnectionConfig(
    val id: String,
    val name: String,
    val mode: DataMode,
    val baseUrl: String? = null,
    val webKey: String? = null,
    val serverReadOnlyPermissionConfirmed: Boolean = false,
    val cfAccessClientId: String? = null,
    val cfAccessClientSecret: String? = null
)

@Serializable
data class DisplayPreferences(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val glassTier: GlassTier = GlassTier.CLEAR,
    val reduceMotion: Boolean = false,
    val maskAccountNames: Boolean = true,
    /** LIVE only: keep the encrypted session and trace alive while backgrounded. */
    val keepLiveConnectionInBackground: Boolean = true
)

@Serializable
data class ProviderModelInfo(
    val id: String,
    val name: String? = null,
    val enabled: Boolean? = null,
    val context: Long? = null,
    val output: Long? = null,
    val images: Boolean? = null,
    val efforts: List<String> = emptyList(),
    val inputPrice: Double? = null,
    val outputPrice: Double? = null
)

@Serializable
data class ProviderCatalogItem(
    val id: String,
    val name: String,
    val host: String? = null,
    val ready: Boolean? = null,
    val off: Boolean? = null,
    val speaks: List<String> = emptyList(),
    val maxConcurrency: Int? = null,
    val keyNames: List<String> = emptyList(),
    val models: List<ProviderModelInfo> = emptyList()
)

/**
 * One masking counter. Summary kinds use [kind]/[n]; record values additionally carry the
 * reveal placeholder plus the shape and discovery fields the record list renders.
 */
@Serializable
data class MaskingCount(
    val kind: String? = null,
    val n: Long? = null,
    /** Placeholder accepted by the reveal endpoint, e.g. {{EMAIL_1}}. */
    val placeholder: String? = null,
    /** Leading characters kept visible in the masked shape. */
    val head: String? = null,
    /** Original length; everything after [head] becomes at most eight dots. */
    val len: Long? = null,
    /** Discovery path: rule|yours|word|model|known. */
    val by: String? = null
)

@Serializable
data class MaskingDayPoint(
    val dayLabel: String,
    val n: Long? = null,
    val requests: Long? = null
)

/** A summary bucket: value rows identify themselves by [id] (a placeholder); agents/providers by [name]. */
@Serializable
data class MaskingBucket(
    val id: String? = null,
    val name: String? = null,
    val n: Long? = null,
    /** Masking kind for summary value buckets. */
    val kind: String? = null,
    val head: String? = null,
    val len: Long? = null
)

@Serializable
data class MaskingSummary(
    val total: Long? = null,
    val distinct: Long? = null,
    val requests: Long? = null,
    val blocked: Long? = null,
    val warned: Long? = null,
    val days: List<MaskingDayPoint> = emptyList(),
    val kinds: List<MaskingCount> = emptyList(),
    val by: Map<String, Int> = emptyMap(),
    val agents: List<MaskingBucket> = emptyList(),
    val providers: List<MaskingBucket> = emptyList(),
    val values: List<MaskingBucket> = emptyList()
)

@Serializable
data class MaskingRecord(
    /** Upstream record key; its JSON type is not fixed, so it is normalized to text. */
    val key: String? = null,
    val values: List<MaskingCount> = emptyList(),
    val blocked: Boolean? = null,
    val observedAt: String? = null,
    val what: String? = null,
    val agent: String? = null,
    val provider: String? = null,
    val model: String? = null,
    val snippets: Map<String, String> = emptyMap()
)

@Serializable
data class MaskingRecordsPage(
    val items: List<MaskingRecord> = emptyList(),
    val more: Boolean = false
)

/** Lazy provider catalog load; never part of the periodic refresh snapshot. */
sealed interface ProviderCatalogState {
    data object Idle : ProviderCatalogState
    data object Loading : ProviderCatalogState
    data class Ready(val providers: List<ProviderCatalogItem>) : ProviderCatalogState
    data object Unsupported : ProviderCatalogState
    data class Error(val message: String) : ProviderCatalogState
}

/** Lazy privacy (masking) load; never part of the periodic refresh snapshot. */
sealed interface PrivacyState {
    val selectedDays: Int?

    /** Active record-kind filter; null means every kind. */
    val selectedKind: String?
        get() = null

    data object Idle : PrivacyState {
        override val selectedDays: Int? = null
    }

    data class Loading(
        override val selectedDays: Int,
        override val selectedKind: String? = null
    ) : PrivacyState

    data class Ready(
        override val selectedDays: Int,
        val summary: MaskingSummary,
        val records: List<MaskingRecord>,
        val more: Boolean,
        override val selectedKind: String? = null
    ) : PrivacyState

    data object Unsupported : PrivacyState {
        override val selectedDays: Int? = null
    }

    data class Error(
        override val selectedDays: Int?,
        val message: String,
        override val selectedKind: String? = null
    ) : PrivacyState
}
