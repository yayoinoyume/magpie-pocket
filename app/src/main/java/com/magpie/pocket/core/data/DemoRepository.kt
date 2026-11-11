package com.magpie.pocket.core.data

import android.content.Context
import com.magpie.pocket.core.model.Account
import com.magpie.pocket.core.model.AccountQuota
import com.magpie.pocket.core.model.Capabilities
import com.magpie.pocket.core.model.ContextBreakdown
import com.magpie.pocket.core.model.Coverage
import com.magpie.pocket.core.model.DailyTrendPoint
import com.magpie.pocket.core.model.DataMode
import com.magpie.pocket.core.model.FinalState
import com.magpie.pocket.core.model.HourlyTrendPoint
import com.magpie.pocket.core.model.MaskingBucket
import com.magpie.pocket.core.model.MaskingCount
import com.magpie.pocket.core.model.MaskingDayPoint
import com.magpie.pocket.core.model.MaskingRecord
import com.magpie.pocket.core.model.MaskingRecordsPage
import com.magpie.pocket.core.model.MaskingSummary
import com.magpie.pocket.core.model.OverviewSnapshot
import com.magpie.pocket.core.model.Period
import com.magpie.pocket.core.model.ProviderCatalogItem
import com.magpie.pocket.core.model.ProviderModelInfo
import com.magpie.pocket.core.model.QuotaWindow
import com.magpie.pocket.core.model.RequestDetail
import com.magpie.pocket.core.model.RequestKey
import com.magpie.pocket.core.model.RequestPage
import com.magpie.pocket.core.model.RequestSummary
import com.magpie.pocket.core.model.RouteGroup
import com.magpie.pocket.core.model.SourceMeta
import com.magpie.pocket.core.model.TokenUsage
import com.magpie.pocket.core.model.TraceSnapshot
import com.magpie.pocket.core.model.TraceTotals
import com.magpie.pocket.core.model.UsageBreakdownItem
import com.magpie.pocket.core.model.UsageModelItem
import com.magpie.pocket.core.model.UsageSnapshot
import com.magpie.pocket.core.network.AuthenticationRequiredException
import com.magpie.pocket.core.network.ResourceNotFoundException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONArray
import org.json.JSONObject

class DemoRepository(private val context: Context? = null, initialJsonString: String? = null) : MagpieRepository {

    private val jsonContent: String = initialJsonString ?: run {
        context?.assets?.open("normalized-demo.json")?.bufferedReader().use { it?.readText() }
            ?: FALLBACK_DEMO_JSON
    }

    private var activeScenario: String = "normal"

    fun setScenario(scenario: String) {
        activeScenario = scenario
    }

    override suspend fun probe(): Capabilities {
        if (activeScenario == "authenticationExpired") {
            throw AuthenticationRequiredException("演示场景：认证过期 (401)")
        }
        val root = JSONObject(jsonContent)
        val cap = root.optJSONObject("capabilities") ?: JSONObject()
        return Capabilities(
            trace = if (activeScenario == "unsupportedTrace") false else cap.optBoolean("trace", true),
            history = cap.optBoolean("history", true),
            requestDetail = cap.optBoolean("requestDetail", true),
            usage = cap.optBoolean("usage", true),
            ledger = cap.optBoolean("ledger", true),
            quotas = cap.optBoolean("quotas", true),
            groups = cap.optBoolean("groups", true),
            providers = cap.optBoolean("providers", true),
            masking = cap.optBoolean("masking", true),
            statisticsTimeZone = cap.optString("statisticsTimeZone", "Asia/Shanghai"),
            verifiedDeploymentVersion = "Magpie 2.4-preview"
        )
    }

    override suspend fun overview(period: Period): OverviewSnapshot {
        if (activeScenario == "empty") {
            return OverviewSnapshot(
                period = period,
                coverage = Coverage.COMPLETE,
                completedRequests = 0L,
                finalErrors = 0L,
                ttftSumMs = null,
                ttftSampleCount = null,
                sameTimeYesterdayCompletedRequests = null,
                hourlyTrends = emptyList()
            )
        }

        val root = JSONObject(jsonContent)
        val ov = root.optJSONObject("overview") ?: JSONObject()

        // Hourly trends for a realistic today view
        val hourly = listOf(
            HourlyTrendPoint("00:00", 45),
            HourlyTrendPoint("02:00", 20),
            HourlyTrendPoint("04:00", 12),
            HourlyTrendPoint("06:00", 35),
            HourlyTrendPoint("08:00", 180),
            HourlyTrendPoint("10:00", 340),
            HourlyTrendPoint("12:00", 290),
            HourlyTrendPoint("14:00", 420),
            HourlyTrendPoint("16:00", 386)
        )

        return OverviewSnapshot(
            period = period,
            rangeStart = ov.optString("rangeStart", "2026-10-21T16:00:00Z"),
            rangeEnd = ov.optString("rangeEnd", "2026-10-22T01:41:12Z"),
            // One sample window feeds all four periods, so the coverage is never complete.
            coverage = Coverage.PARTIAL,
            completedRequests = ov.optLong("completedRequests", 1728L),
            finalErrors = ov.optLong("finalErrors", 4L),
            ttftSumMs = ov.optLong("ttftSumMs", 1800000L),
            ttftSampleCount = ov.optLong("ttftSampleCount", 1000L),
            sameTimeYesterdayCompletedRequests = null,
            hourlyTrends = hourly,
            cost = ov.optNullableDouble("cost"),
            unpriced = ov.optNullableLong("unpriced"),
            decodeMs = ov.optNullableLong("decodeMs"),
            decodeOut = ov.optNullableLong("decodeOut"),
            dailyTrends = ov.optJSONArray("dailyTrends").toDailyTrends()
        )
    }

    override fun observeTrace(): Flow<TraceSnapshot> = flow {
        val root = JSONObject(jsonContent)
        val tr = root.optJSONObject("trace") ?: JSONObject()
        val totalsObj = tr.optJSONObject("totals") ?: JSONObject()

        var currentSeq = tr.optLong("seq", 480L)
        var completed = totalsObj.optLong("completedRequests", 6240L)
        val rerouted = totalsObj.optLong("rerouteHandoffs", 324L)
        val errors = totalsObj.optLong("finalErrors", 101L)

        val routeSummaries = requests(RequestQuery(limit = 100)).items
        val routeDetails = routeSummaries.map { requestDetail(it.key) }
        while (true) {
            val isObservable = activeScenario != "unsupportedTrace"
            emit(
                TraceSnapshot(
                    epoch = tr.optString("epoch", "demo-epoch-1"),
                    seq = currentSeq,
                    serverNow = tr.optString("serverNow", "2026-10-22T01:41:12Z"),
                    observable = isObservable,
                    retainedRouteIds = routeSummaries.map { it.key.routeId },
                    runningRouteIds = if (activeScenario == "empty") emptyList() else listOf("101"),
                    recentRoutes = routeSummaries,
                    recentDetails = routeDetails,
                    totals = TraceTotals(
                        completedRequests = completed,
                        rerouteHandoffs = rerouted,
                        finalErrors = errors
                    )
                )
            )
            delay(5000)
            currentSeq += 1
            completed += 1
        }
    }

    override suspend fun groups(): List<RouteGroup> {
        val root = JSONObject(jsonContent)
        val arr = root.optJSONArray("groups") ?: return emptyList()
        val list = mutableListOf<RouteGroup>()
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            val pArr = item.optJSONArray("providerIds")
            val pList = mutableListOf<String>()
            if (pArr != null) {
                for (j in 0 until pArr.length()) {
                    pList.add(pArr.getString(j))
                }
            }
            list.add(
                RouteGroup(
                    id = item.optString("id"),
                    name = item.optString("name"),
                    strategy = item.optString("strategy"),
                    affinity = item.optString("affinity"),
                    enabledMemberCount = item.optInt("enabledMemberCount"),
                    providerIds = pList,
                    detailsComplete = item.optBoolean("detailsComplete", true)
                )
            )
        }
        return list
    }

    override suspend fun accounts(): List<Account> {
        val root = JSONObject(jsonContent)
        val arr = root.optJSONArray("accounts") ?: return emptyList()
        val list = mutableListOf<Account>()
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            list.add(
                Account(
                    id = item.optString("id"),
                    providerId = item.optString("providerId"),
                    alias = item.optString("alias"),
                    availability = item.optString("availability", "AVAILABLE"),
                    cooldownUntil = item.optString("cooldownUntil", null)
                )
            )
        }
        return list
    }

    override suspend fun usage(period: Period): UsageSnapshot {
        val root = JSONObject(jsonContent)
        val us = root.optJSONObject("usage") ?: JSONObject()
        val tok = us.optJSONObject("tokens") ?: JSONObject()
        val mArr = us.optJSONArray("models")

        val modelList = mutableListOf<UsageModelItem>()
        if (mArr != null) {
            for (i in 0 until mArr.length()) {
                val item = mArr.getJSONObject(i)
                modelList.add(
                    UsageModelItem(
                        providerId = item.optString("providerId"),
                        modelId = item.optString("modelId"),
                        totalTokens = item.optLong("totalTokens")
                    )
                )
            }
        }

        return UsageSnapshot(
            period = period,
            // Demo data is one sample window for every period.
            coverage = Coverage.PARTIAL,
            tokens = TokenUsage(
                inputUncached = tok.optNullableLong("inputUncached"),
                output = tok.optNullableLong("output"),
                cacheRead = tok.optNullableLong("cacheRead"),
                cacheWrite = tok.optNullableLong("cacheWrite"),
                reasoningIncludedInOutput = tok.optNullableLong("reasoningIncludedInOutput")
            ),
            models = modelList,
            cost = us.optNullableDouble("cost"),
            unpriced = us.optNullableLong("unpriced"),
            credits = us.optNullableDouble("credits"),
            decodeMs = us.optNullableLong("decodeMs"),
            decodeOut = us.optNullableLong("decodeOut"),
            dailyTrends = us.optJSONArray("series").toSeriesTrends(),
            agents = us.optJSONArray("agents").toBreakdownItems(),
            sessions = us.optJSONArray("sessions").toBreakdownItems(),
            accounts = us.optJSONArray("accounts").toBreakdownItems(),
            callerKeys = us.optJSONArray("callerKeys").toBreakdownItems(),
            providerKeys = us.optJSONArray("providerKeys").toBreakdownItems()
        )
    }

    override suspend fun quotas(): List<AccountQuota> {
        val root = JSONObject(jsonContent)
        val arr = root.optJSONArray("quotas") ?: return emptyList()
        val list = mutableListOf<AccountQuota>()
        for (i in 0 until arr.length()) {
            val item = arr.getJSONObject(i)
            val wArr = item.optJSONArray("windows")
            val wList = mutableListOf<QuotaWindow>()
            if (wArr != null) {
                for (j in 0 until wArr.length()) {
                    val w = wArr.getJSONObject(j)
                    val used = if (w.has("used")) w.optDouble("used") else null
                    val amount = if (w.has("amount")) w.optDouble("amount") else null
                    val limit = if (w.has("limit")) w.optDouble("limit") else null
                    val isCount = quotaFigureIsCount(used, amount, limit)
                    val usedCount = if (isCount) amount ?: used else amount
                    wList.add(
                        QuotaWindow(
                            id = w.optString("id"),
                            unit = w.optString("unit"),
                            used = usedCount?.toLong(),
                            limit = limit?.toLong(),
                            resetsAt = w.optString("resetsAt", null),
                            usedPercent = if (isCount) null else used,
                            amount = usedCount,
                            allowanceLimit = limit,
                            display = w.optString("display", null),
                            unlimited = w.optBoolean("unlimited", false)
                        )
                    )
                }
            }
            list.add(
                AccountQuota(
                    providerId = item.optString("providerId")
                        .ifBlank { item.optString("provider") }
                        .ifBlank { "unknown" },
                    displayName = item.optString("displayName", null)
                        ?: item.optString("name", null),
                    accountId = item.optString("accountId").takeIf { it.isNotBlank() },
                    known = item.optBoolean("known", true),
                    observedAt = item.optString("observedAt", null),
                    windows = wList
                )
            )
        }
        return list
    }

    override suspend fun providers(): List<ProviderCatalogItem> {
        val root = JSONObject(jsonContent)
        val arr = root.optJSONArray("providers") ?: return emptyList()
        val list = mutableListOf<ProviderCatalogItem>()
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i) ?: continue
            val id = item.optString("id", "")
            val models = mutableListOf<ProviderModelInfo>()
            val modelArr = item.optJSONArray("models")
            if (modelArr != null) {
                for (j in 0 until modelArr.length()) {
                    val m = modelArr.optJSONObject(j) ?: continue
                    val pricing = m.optJSONObject("list")
                    models.add(
                        ProviderModelInfo(
                            id = m.optString("id", ""),
                            name = m.optString("name", null),
                            enabled = m.optNullableBoolean("on"),
                            context = m.optNullableLong("context"),
                            output = m.optNullableLong("output"),
                            images = m.optNullableBoolean("images"),
                            efforts = m.optJSONArray("efforts").toStringList(),
                            inputPrice = pricing?.optNullableDouble("input"),
                            outputPrice = pricing?.optNullableDouble("output")
                        )
                    )
                }
            }
            list.add(
                ProviderCatalogItem(
                    id = id,
                    name = item.optString("name", id),
                    host = item.optString("host", null),
                    ready = item.optNullableBoolean("ready"),
                    off = item.optNullableBoolean("off"),
                    speaks = item.optJSONArray("speaks").toStringList(),
                    maxConcurrency = item.optNullableInt("maxConcurrency"),
                    keyNames = item.optJSONArray("keyList").toNames(),
                    models = models
                )
            )
        }
        return list
    }

    /** The demo window is fixed, so every requested day count shows the same sample summary. */
    override suspend fun maskingSummary(days: Int): MaskingSummary {
        val root = JSONObject(jsonContent)
        val summary = root.optJSONObject("masking")?.optJSONObject("summary") ?: return MaskingSummary()
        return MaskingSummary(
            total = summary.optNullableLong("n"),
            distinct = summary.optNullableLong("distinct"),
            requests = summary.optNullableLong("requests"),
            blocked = summary.optNullableLong("blocked"),
            warned = summary.optNullableLong("warned"),
            days = summary.optJSONArray("days").toMaskingDays(),
            kinds = summary.optJSONArray("kinds").toMaskingCounts(),
            by = summary.optJSONObject("by")?.toIntMap() ?: emptyMap(),
            agents = summary.optJSONArray("agents").toMaskingBuckets(),
            providers = summary.optJSONArray("providers").toMaskingBuckets(),
            values = summary.optJSONArray("values").toMaskingBuckets()
        )
    }

    override suspend fun maskingRecords(query: MaskingQuery): MaskingRecordsPage {
        val root = JSONObject(jsonContent)
        val arr = root.optJSONObject("masking")?.optJSONArray("records") ?: return MaskingRecordsPage()
        val all = mutableListOf<MaskingRecord>()
        for (i in 0 until arr.length()) {
            val r = arr.optJSONObject(i) ?: continue
            val record = MaskingRecord(
                key = r.optString("key", null),
                values = r.optJSONArray("values").toMaskingCounts(),
                blocked = r.optNullableBoolean("blocked"),
                observedAt = r.optString("t", null),
                what = r.optString("what", null),
                agent = r.optString("agent", null),
                provider = r.optString("provider", null),
                model = r.optString("model", null),
                snippets = r.optJSONObject("snips")?.toStringMap() ?: emptyMap()
            )
            if (!query.kind.isNullOrBlank() && record.values.none { it.kind == query.kind }) continue
            if (!query.agent.isNullOrBlank() && record.agent != query.agent) continue
            if (!query.provider.isNullOrBlank() && record.provider != query.provider) continue
            all.add(record)
        }

        val before = query.before
        val startIndex = if (before.isNullOrBlank()) {
            0
        } else {
            val found = all.indexOfFirst { it.key == before }
            if (found < 0) 0 else found + 1
        }
        val page = all.drop(startIndex).take(query.limit)
        return MaskingRecordsPage(
            items = page,
            more = startIndex + page.size < all.size
        )
    }

    override suspend fun maskingValue(placeholder: String): String? {
        val root = JSONObject(jsonContent)
        val values = root.optJSONObject("masking")?.optJSONObject("values") ?: return null
        if (!values.has(placeholder) || values.isNull(placeholder)) return null
        return values.optString(placeholder, null)
    }

    private fun JSONObject.optNullableDouble(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        val value = optDouble(key)
        return if (value.isNaN()) null else value
    }

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (!has(key) || isNull(key)) null else optLong(key)

    private fun JSONObject.optNullableInt(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key)

    private fun JSONObject.optNullableBoolean(key: String): Boolean? =
        if (!has(key) || isNull(key)) null else optBoolean(key)

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until length()) {
            val item = opt(i)
            if (item == null || item == JSONObject.NULL) continue
            list.add(item.toString())
        }
        return list
    }

    private fun JSONArray?.toNames(): List<String> {
        if (this == null) return emptyList()
        val list = mutableListOf<String>()
        for (i in 0 until length()) {
            val name = optJSONObject(i)?.optString("name", null) ?: continue
            list.add(name)
        }
        return list
    }

    /** Upstream usage series shape: label (or day), calls and an optional priced amount. */
    private fun JSONArray?.toSeriesTrends(): List<DailyTrendPoint> {
        if (this == null) return emptyList()
        val list = mutableListOf<DailyTrendPoint>()
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            val label = item.optString("label", item.optString("day", ""))
            if (label.isBlank()) continue
            list.add(
                DailyTrendPoint(
                    dayLabel = label,
                    completedRequests = item.optLong("calls"),
                    cost = item.optNullableDouble("cost")
                )
            )
        }
        return list
    }

    private fun JSONArray?.toDailyTrends(): List<DailyTrendPoint> {
        if (this == null) return emptyList()
        val list = mutableListOf<DailyTrendPoint>()
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            list.add(
                DailyTrendPoint(
                    dayLabel = item.optString("day", ""),
                    completedRequests = item.optLong("requests"),
                    cost = item.optNullableDouble("cost")
                )
            )
        }
        return list
    }

    private fun JSONArray?.toBreakdownItems(): List<UsageBreakdownItem> {
        if (this == null) return emptyList()
        val list = mutableListOf<UsageBreakdownItem>()
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            list.add(
                UsageBreakdownItem(
                    id = item.optString("id", null),
                    label = item.optString("label", item.optString("name", null)),
                    provider = item.optString("provider", null),
                    calls = item.optNullableLong("calls"),
                    cost = item.optNullableDouble("cost"),
                    credits = item.optNullableDouble("credits"),
                    errors = item.optNullableLong("errors"),
                    input = item.optNullableLong("input"),
                    output = item.optNullableLong("output"),
                    cacheRead = item.optNullableLong("cacheRead"),
                    cacheWrite = item.optNullableLong("cacheWrite"),
                    unpriced = item.optNullableLong("unpriced")
                )
            )
        }
        return list
    }

    private fun JSONArray?.toMaskingCounts(): List<MaskingCount> {
        if (this == null) return emptyList()
        val list = mutableListOf<MaskingCount>()
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            list.add(MaskingCount(kind = item.optString("k", null), n = item.optNullableLong("n")))
        }
        return list
    }

    private fun JSONArray?.toMaskingDays(): List<MaskingDayPoint> {
        if (this == null) return emptyList()
        val list = mutableListOf<MaskingDayPoint>()
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            list.add(
                MaskingDayPoint(
                    dayLabel = item.optString("day", ""),
                    n = item.optNullableLong("n"),
                    requests = item.optNullableLong("requests")
                )
            )
        }
        return list
    }

    private fun JSONArray?.toMaskingBuckets(): List<MaskingBucket> {
        if (this == null) return emptyList()
        val list = mutableListOf<MaskingBucket>()
        for (i in 0 until length()) {
            val item = optJSONObject(i) ?: continue
            list.add(
                MaskingBucket(
                    id = item.optString("id", null),
                    name = item.optString("name", null),
                    n = item.optNullableLong("n")
                )
            )
        }
        return list
    }

    private fun JSONObject.toIntMap(): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        val keys = keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (isNull(key)) continue
            map[key] = optInt(key)
        }
        return map
    }

    private fun JSONObject.toStringMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val keys = keys()
        while (keys.hasNext()) {
            val key = keys.next()
            if (isNull(key)) continue
            map[key] = optString(key)
        }
        return map
    }

    override suspend fun requests(query: RequestQuery): RequestPage {
        if (activeScenario == "empty") {
            return RequestPage(
                items = emptyList(),
                offset = query.offset,
                total = 0,
                hasMore = false,
                source = "gateway",
                coverage = Coverage.COMPLETE
            )
        }

        val root = JSONObject(jsonContent)
        val arr = root.optJSONArray("requests") ?: return RequestPage(
            items = emptyList(),
            offset = 0,
            total = 0,
            hasMore = false
        )
        val allRequests = mutableListOf<RequestSummary>()

        for (i in 0 until arr.length()) {
            val r = arr.getJSONObject(i)
            val keyObj = r.optJSONObject("key") ?: JSONObject()
            val usageObj = r.optJSONObject("usage")
            val metaObj = r.optJSONObject("meta") ?: JSONObject()

            val tokenUsage = usageObj?.let {
                RequestDerivations.tokenUsage(
                    inputUncached = it.optNullableLong("inputUncached"),
                    output = it.optNullableLong("output"),
                    cacheRead = it.optNullableLong("cacheRead"),
                    cacheWrite = it.optNullableLong("cacheWrite"),
                    reasoningIncludedInOutput = it.optNullableLong("reasoningIncludedInOutput")
                )
            }

            val meta = SourceMeta(
                connectionId = metaObj.optString("connectionId", "demo-main"),
                mode = DataMode.DEMO,
                observedAt = metaObj.optString("observedAt", ""),
                fetchedAt = metaObj.optString("fetchedAt", ""),
                statisticsTimeZone = metaObj.optString("statisticsTimeZone", "Asia/Shanghai"),
                coverage = Coverage.RECENT_ONLY,
                stale = activeScenario == "offlineWithCache"
            )

            allRequests.add(
                RequestSummary(
                    key = RequestKey(
                        connectionId = keyObj.optString("connectionId", "demo-main"),
                        epoch = keyObj.optString("epoch", "demo-epoch-1"),
                        routeId = keyObj.optString("routeId", ""),
                        sourceDay = keyObj.optString("sourceDay", null)
                    ),
                    startedAt = r.optString("startedAt", ""),
                    agentId = r.optString("agentId", null),
                    groupId = r.optString("groupId", null),
                    requestedModel = r.optString("requestedModel", null),
                    servedModel = r.optString("servedModel", null),
                    providerId = r.optString("providerId", null),
                    accountId = r.optString("accountId", null),
                    finalState = try {
                        FinalState.valueOf(r.optString("finalState", "SUCCEEDED"))
                    } catch (_: Exception) {
                        FinalState.SUCCEEDED
                    },
                    rerouteCount = r.optInt("rerouteCount", 0),
                    ttftMs = if (r.has("ttftMs") && !r.isNull("ttftMs")) r.optLong("ttftMs") else null,
                    durationMs = if (r.has("durationMs") && !r.isNull("durationMs")) r.optLong("durationMs") else null,
                    usage = tokenUsage,
                    meta = meta
                )
            )
        }

        // Apply search filter
        var filtered = allRequests.toList()
        if (!query.query.isNullOrBlank()) {
            val q = query.query.lowercase()
            filtered = filtered.filter {
                (it.servedModel?.lowercase()?.contains(q) == true) ||
                (it.agentId?.lowercase()?.contains(q) == true) ||
                (it.providerId?.lowercase()?.contains(q) == true) ||
                (it.groupId?.lowercase()?.contains(q) == true)
            }
        }

        // Apply status filter: "ALL", "RUNNING", "REROUTED", "ERROR"
        if (!query.statusFilter.isNullOrBlank() && query.statusFilter != "ALL") {
            filtered = when (query.statusFilter) {
                "RUNNING" -> filtered.filter { it.finalState == FinalState.IN_PROGRESS }
                "REROUTED" -> filtered.filter { it.rerouteCount > 0 }
                "ERROR" -> filtered.filter { it.finalState == FinalState.FAILED }
                else -> filtered
            }
        }

        val pagedItems = filtered.drop(query.offset).take(query.limit)
        val hasMore = (query.offset + pagedItems.size) < filtered.size

        return RequestPage(
            items = pagedItems,
            offset = query.offset,
            total = filtered.size,
            hasMore = hasMore,
            source = "gateway",
            coverage = if (activeScenario == "historyTruncated") Coverage.PARTIAL else Coverage.RECENT_ONLY
        )
    }

    override suspend fun requestDetail(key: RequestKey): RequestDetail {
        if (activeScenario == "detailExpired") {
            throw ResourceNotFoundException("该请求详情已过保留期 (404)")
        }

        val all = requests(RequestQuery(limit = 100)).items
        val found = all.find { it.key.routeId == key.routeId }
            ?: throw ResourceNotFoundException("未找到路由 ID: ${key.routeId}")

        val root = JSONObject(jsonContent)
        val ctxObj = root.optJSONObject("selectedContext")

        val contextBreakdown = if (ctxObj != null && key.routeId == "101") {
            val pArr = ctxObj.optJSONArray("parts")
            val pList = mutableListOf<com.magpie.pocket.core.model.ContextPart>()
            if (pArr != null) {
                for (i in 0 until pArr.length()) {
                    val p = pArr.getJSONObject(i)
                    pList.add(
                        com.magpie.pocket.core.model.ContextPart(
                            kind = p.optString("kind"),
                            tokens = p.optLong("tokens")
                        )
                    )
                }
            }
            ContextBreakdown(
                requestKey = key,
                totalTokens = ctxObj.optLong("totalTokens", 214000L),
                windowLimit = if (ctxObj.has("windowLimit") && !ctxObj.isNull("windowLimit")) ctxObj.optLong("windowLimit") else null,
                estimated = ctxObj.optBoolean("estimated", true),
                parts = pList
            )
        } else null

        val decisionEvidence = when (key.routeId) {
            "103" -> listOf("首选账号 wb-01 触发 429 速率限制", "自动改道至备选供应商 workbuddy-ai 账号 wba-03", "第 2 次尝试成功应答")
            "105" -> listOf("供应商 moonshot 超时 (504)", "重试次数已达组策略上限", "向 Agent 返回最终超时错误")
            else -> listOf("按组策略轮询到当前账号", "账号健康检查通过，正常分配")
        }

        return RequestDetail(
            summary = found,
            attempts = found.attempts,
            decisionEvidence = decisionEvidence,
            context = contextBreakdown,
            sanitizedError = if (found.finalState == FinalState.FAILED) "供应商连接超时 (504 Gateway Timeout)" else null,
            sourceMeta = found.meta
        )
    }

    companion object {
        const val FALLBACK_DEMO_JSON = """
        {
          "capabilities": { "providers": true, "masking": true },
          "trace": { "epoch": "demo", "seq": 100, "totals": { "completedRequests": 100, "rerouteHandoffs": 5, "finalErrors": 1 } },
          "overview": {
            "completedRequests": 50,
            "finalErrors": 1,
            "ttftSumMs": 50000,
            "ttftSampleCount": 25,
            "cost": 0.42,
            "unpriced": 0,
            "decodeMs": 2000,
            "decodeOut": 1500,
            "dailyTrends": [{ "day": "2026-10-22", "requests": 50 }]
          },
          "usage": {
            "cost": 0.42,
            "unpriced": 0,
            "credits": 1.5,
            "decodeMs": 2000,
            "decodeOut": 1500,
            "agents": [{ "id": "agent-demo", "name": "demo", "calls": 50, "cost": 0.42, "errors": 1 }],
            "sessions": [],
            "accounts": [],
            "callerKeys": [],
            "providerKeys": []
          },
          "providers": [{
            "id": "demo",
            "name": "Demo",
            "host": "demo.invalid",
            "ready": true,
            "off": false,
            "speaks": ["openai-chat"],
            "maxConcurrency": 1,
            "keyList": [{ "name": "demo-key" }],
            "models": [{
              "id": "demo-model",
              "name": "Demo Model",
              "on": true,
              "context": 8192,
              "output": 1024,
              "images": false,
              "efforts": [],
              "list": { "input": 0.1, "output": 0.2 }
            }]
          }],
          "masking": {
            "summary": {
              "n": 2,
              "distinct": 1,
              "requests": 2,
              "blocked": 1,
              "warned": 0,
              "days": [{ "day": "2026-10-22", "n": 2, "requests": 2 }],
              "kinds": [{ "k": "EMAIL", "n": 2 }],
              "by": { "EMAIL": 2 },
              "agents": [{ "id": "demo", "name": "demo", "n": 2 }],
              "providers": [{ "id": "demo", "name": "demo", "n": 2 }],
              "values": [{ "id": "{{EMAIL_1}}", "name": "{{EMAIL_1}}", "n": 2 }]
            },
            "records": [{
              "key": "1",
              "values": [{ "k": "EMAIL", "n": 1 }],
              "blocked": false,
              "t": "2026-10-22T00:00:00Z",
              "what": "body",
              "agent": "demo",
              "provider": "demo",
              "model": "demo-model",
              "snips": { "EMAIL_1": "{{EMAIL_1}}" }
            }],
            "values": { "{{EMAIL_1}}": "demo@example.test" }
          },
          "groups": [],
          "accounts": [],
          "quotas": [],
          "requests": []
        }
        """
    }
}
