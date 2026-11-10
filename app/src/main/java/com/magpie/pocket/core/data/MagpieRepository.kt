package com.magpie.pocket.core.data

import com.magpie.pocket.core.model.Account
import com.magpie.pocket.core.model.AccountQuota
import com.magpie.pocket.core.model.Capabilities
import com.magpie.pocket.core.model.MaskingRecordsPage
import com.magpie.pocket.core.model.MaskingSummary
import com.magpie.pocket.core.model.OverviewSnapshot
import com.magpie.pocket.core.model.Period
import com.magpie.pocket.core.model.ProviderCatalogItem
import com.magpie.pocket.core.model.RequestDetail
import com.magpie.pocket.core.model.RequestKey
import com.magpie.pocket.core.model.RequestPage
import com.magpie.pocket.core.model.RouteGroup
import com.magpie.pocket.core.model.TraceSnapshot
import com.magpie.pocket.core.model.UsageSnapshot
import kotlinx.coroutines.flow.Flow

/** Filters for one masking-records page; [before] is the cursor returned as a record key. */
data class MaskingQuery(
    val limit: Int = 50,
    val before: String? = null,
    val kind: String? = null,
    val by: String? = null,
    val agent: String? = null,
    val provider: String? = null
)

data class RequestQuery(
    val offset: Int = 0,
    val limit: Int = 50,
    val query: String? = null,
    val statusFilter: String? = null, // "ALL", "RUNNING", "REROUTED", "ERROR"
    val day: String? = null
)

interface MagpieRepository {
    suspend fun probe(): Capabilities
    /** Lightweight authenticated state check used by the 30s heartbeat. */
    suspend fun probeLight() { probe() }
    suspend fun overview(period: Period = Period.TODAY): OverviewSnapshot
    fun observeTrace(): Flow<TraceSnapshot>
    suspend fun groups(): List<RouteGroup>
    suspend fun accounts(): List<Account>
    suspend fun usage(period: Period = Period.TODAY): UsageSnapshot
    suspend fun quotas(): List<AccountQuota>
    suspend fun requests(query: RequestQuery = RequestQuery()): RequestPage
    suspend fun requestDetail(key: RequestKey): RequestDetail

    // Provider catalog and privacy views are lazy loads; they are never part of the
    // refresh snapshot or the heartbeat.
    suspend fun providers(): List<ProviderCatalogItem>
    suspend fun maskingSummary(days: Int): MaskingSummary
    suspend fun maskingRecords(query: MaskingQuery = MaskingQuery()): MaskingRecordsPage
    suspend fun maskingValue(placeholder: String): String?
}
