package com.magpie.pocket.core.network

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Upstream uses numeric IDs; the app keeps their exact string form internally. */
object FlexibleStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val input = decoder as? JsonDecoder ?: return decoder.decodeString()
        val value = input.decodeJsonElement().jsonPrimitive
        return value.contentOrNull ?: value.content
    }

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)
}

object FlexibleStringListSerializer : KSerializer<List<String>> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleStringList", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): List<String> {
        val input = decoder as? JsonDecoder ?: return emptyList()
        val element = input.decodeJsonElement()
        return when {
            element is kotlinx.serialization.json.JsonArray ->
                element.map { it.jsonPrimitive.contentOrNull ?: it.jsonPrimitive.content }
            element is JsonPrimitive -> listOf(element.contentOrNull ?: element.content)
            else -> emptyList()
        }
    }

    override fun serialize(encoder: Encoder, value: List<String>) {
        encoder.encodeSerializableValue(kotlinx.serialization.builtins.ListSerializer(String.serializer()), value)
    }
}

/** Groups currently return member model IDs as strings, while older dummies used objects. */
object GroupMembersSerializer : KSerializer<List<UpstreamGroupMember>?> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("GroupMembers")

    override fun deserialize(decoder: Decoder): List<UpstreamGroupMember>? {
        val input = decoder as? JsonDecoder ?: return null
        val element = input.decodeJsonElement()
        if (element !is kotlinx.serialization.json.JsonArray) return null
        return element.mapNotNull { item ->
            when (item) {
                is JsonPrimitive -> UpstreamGroupMember(id = item.contentOrNull ?: item.content)
                is JsonObject -> input.json.decodeFromJsonElement(UpstreamGroupMember.serializer(), item)
                else -> null
            }
        }
    }

    override fun serialize(encoder: Encoder, value: List<UpstreamGroupMember>?) {
        if (value == null) return
        encoder.encodeSerializableValue(
            kotlinx.serialization.builtins.ListSerializer(UpstreamGroupMember.serializer()),
            value
        )
    }
}

/** A trace route's group was a string in the old guessed DTO and is an object now. */
object RouteGroupSerializer : KSerializer<UpstreamRouteGroup?> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("RouteGroup")

    override fun deserialize(decoder: Decoder): UpstreamRouteGroup? {
        val input = decoder as? JsonDecoder ?: return null
        return when (val element = input.decodeJsonElement()) {
            is JsonPrimitive -> UpstreamRouteGroup(id = element.contentOrNull ?: element.content)
            is JsonObject -> input.json.decodeFromJsonElement(UpstreamRouteGroup.serializer(), element)
            else -> null
        }
    }

    override fun serialize(encoder: Encoder, value: UpstreamRouteGroup?) {
        if (value == null) return
        encoder.encodeSerializableValue(UpstreamRouteGroup.serializer(), value)
    }
}

object AffinitySerializer : KSerializer<UpstreamAffinity?> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("Affinity")

    override fun deserialize(decoder: Decoder): UpstreamAffinity? {
        val input = decoder as? JsonDecoder ?: return null
        return when (val element = input.decodeJsonElement()) {
            is JsonPrimitive -> UpstreamAffinity(mode = element.contentOrNull ?: element.content)
            is JsonObject -> input.json.decodeFromJsonElement(UpstreamAffinity.serializer(), element)
            else -> null
        }
    }

    override fun serialize(encoder: Encoder, value: UpstreamAffinity?) {
        if (value == null) return
        encoder.encodeSerializableValue(UpstreamAffinity.serializer(), value)
    }
}

/** Usage is an array of billable tries now; accept the old single-object shape too. */
object RouteUsageListSerializer : KSerializer<List<UpstreamRouteUsage>?> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("RouteUsageList")

    override fun deserialize(decoder: Decoder): List<UpstreamRouteUsage>? {
        val input = decoder as? JsonDecoder ?: return null
        return when (val element = input.decodeJsonElement()) {
            is kotlinx.serialization.json.JsonArray ->
                element.map { input.json.decodeFromJsonElement(UpstreamRouteUsage.serializer(), it) }
            is JsonObject ->
                listOf(input.json.decodeFromJsonElement(UpstreamRouteUsage.serializer(), element))
            else -> null
        }
    }

    override fun serialize(encoder: Encoder, value: List<UpstreamRouteUsage>?) {
        if (value == null) return
        encoder.encodeSerializableValue(
            kotlinx.serialization.builtins.ListSerializer(UpstreamRouteUsage.serializer()),
            value
        )
    }
}

/** `/api/usage/quotas` is a root array now and was `{quotas:[...]}` in the old guess. */
object QuotasResponseSerializer : KSerializer<UpstreamQuotasResponse> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("UpstreamQuotasResponse")

    override fun deserialize(decoder: Decoder): UpstreamQuotasResponse {
        val input = decoder as? JsonDecoder ?: return UpstreamQuotasResponse()
        val element = input.decodeJsonElement()
        return when (element) {
            is kotlinx.serialization.json.JsonArray -> UpstreamQuotasResponse(
                quotas = element.map {
                    input.json.decodeFromJsonElement(UpstreamQuotaAccount.serializer(), it)
                }
            )
            is JsonObject -> UpstreamQuotasResponse(
                quotas = element["quotas"]?.let { quotas ->
                    if (quotas == kotlinx.serialization.json.JsonNull) null
                    else input.json.decodeFromJsonElement(
                        kotlinx.serialization.builtins.ListSerializer(UpstreamQuotaAccount.serializer()),
                        quotas
                    )
                }
            )
            else -> UpstreamQuotasResponse()
        }
    }

    override fun serialize(encoder: Encoder, value: UpstreamQuotasResponse) {
        encoder.encodeSerializableValue(
            kotlinx.serialization.builtins.ListSerializer(UpstreamQuotaAccount.serializer()),
            value.quotas ?: emptyList()
        )
    }
}

/** The history endpoint listed days as bare strings and now sends {day,requests} objects. */
object HistoryDaysSerializer : KSerializer<List<UpstreamHistoryDay>?> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("HistoryDays")

    override fun deserialize(decoder: Decoder): List<UpstreamHistoryDay>? {
        val input = decoder as? JsonDecoder ?: return null
        val element = input.decodeJsonElement()
        if (element !is kotlinx.serialization.json.JsonArray) return null
        return element.mapNotNull { item ->
            when (item) {
                is JsonPrimitive -> UpstreamHistoryDay(day = item.contentOrNull ?: item.content)
                is JsonObject -> input.json.decodeFromJsonElement(UpstreamHistoryDay.serializer(), item)
                else -> null
            }
        }
    }

    override fun serialize(encoder: Encoder, value: List<UpstreamHistoryDay>?) {
        if (value == null) return
        encoder.encodeSerializableValue(
            kotlinx.serialization.builtins.ListSerializer(UpstreamHistoryDay.serializer()),
            value
        )
    }
}

@Serializable
data class UpstreamTraceResponse(
    @SerialName("epoch") val epoch: String = "",
    @SerialName("seq") val seq: Long = 0L,
    @SerialName("now") val now: String? = null,
    @SerialName("mine") val mine: Boolean = true,
    @SerialName("routes") val routes: List<UpstreamRoute>? = null,
    @SerialName("ids") @Serializable(with = FlexibleStringListSerializer::class)
    val ids: List<String>? = null,
    @SerialName("totals") val totals: UpstreamTraceTotals? = null
)

@Serializable
data class UpstreamTraceTotals(
    @SerialName("requests") val requests: Long = 0L,
    @SerialName("rerouted") val rerouted: Long = 0L,
    @SerialName("errors") val errors: Long = 0L
)

@Serializable
data class UpstreamRoute(
    @SerialName("id") @Serializable(with = FlexibleStringSerializer::class) val id: String,
    @SerialName("group") @Serializable(with = RouteGroupSerializer::class)
    val group: UpstreamRouteGroup? = null,
    @SerialName("agent") val agent: String? = null,
    @SerialName("model") val model: String? = null,
    @SerialName("provider") val provider: String? = null,
    @SerialName("time") val time: String? = null,
    @SerialName("started") val started: String? = null,
    @SerialName("done") val done: Boolean? = null,
    @SerialName("status") val status: Int? = null,
    @SerialName("error") val error: String? = null,
    @SerialName("ttft") val ttft: Long? = null,
    @SerialName("ms") val ms: Long? = null,
    @SerialName("elapsed") val elapsed: Long? = null,
    @SerialName("served") val served: String? = null,
    @SerialName("tries") val tries: List<UpstreamTry>? = null,
    @SerialName("order") val order: List<UpstreamOrder>? = null,
    @SerialName("prompt") val prompt: UpstreamPrompt? = null,
    @SerialName("usage") @Serializable(with = RouteUsageListSerializer::class)
    val usage: List<UpstreamRouteUsage>? = null,
    @SerialName("affinity") @Serializable(with = AffinitySerializer::class)
    val affinity: UpstreamAffinity? = null,
    @SerialName("rule") val rule: String? = null
)

@Serializable
data class UpstreamRouteGroup(
    @SerialName("id") val id: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("routing") val routing: String? = null,
    @SerialName("affinity") val affinity: String? = null,
    @SerialName("members") val members: List<String>? = null
)

@Serializable
data class UpstreamAffinity(
    @SerialName("mode") val mode: String? = null,
    @SerialName("kept") val kept: Boolean? = null,
    @SerialName("why") val why: String? = null
)

@Serializable
data class UpstreamOrder(
    @SerialName("id") @Serializable(with = FlexibleStringSerializer::class) val id: String,
    @SerialName("provider") val provider: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("model") val model: String? = null
)

@Serializable
data class UpstreamTry(
    @SerialName("id") @Serializable(with = FlexibleStringSerializer::class) val id: String? = null,
    @SerialName("provider") val provider: String? = null,
    @SerialName("account") val account: String? = null,
    @SerialName("model") val model: String? = null,
    @SerialName("start") val start: String? = null,
    @SerialName("started") val started: String? = null,
    @SerialName("done") val done: Boolean? = null,
    @SerialName("status") val status: Int? = null,
    @SerialName("ms") val ms: Long? = null,
    @SerialName("elapsed") val elapsed: Long? = null,
    @SerialName("ttft") val ttft: Long? = null,
    @SerialName("served") val served: String? = null,
    @SerialName("error") val error: String? = null
)

@Serializable
data class UpstreamPrompt(
    @SerialName("tokens") val tokens: Long? = null,
    @SerialName("window") val window: Long? = null,
    @SerialName("parts") val parts: List<UpstreamPromptPart>? = null
)

@Serializable
data class UpstreamPromptPart(
    @SerialName("kind") val kind: String? = null,
    @SerialName("type") val type: String? = null,
    @SerialName("tokens") val tokens: Long? = null
)

@Serializable
data class UpstreamRouteUsage(
    @SerialName("provider") val provider: String? = null,
    @SerialName("model") val model: String? = null,
    @SerialName("in") val input: Long? = null,
    @SerialName("out") val output: Long? = null,
    @SerialName("cache_read") val cacheRead: Long? = null,
    @SerialName("cache_write") val cacheWrite: Long? = null,
    @SerialName("reasoning") val reasoning: Long? = null
)

@Serializable
data class UpstreamHistoryResponse(
    @SerialName("days") @Serializable(with = HistoryDaysSerializer::class)
    val days: List<UpstreamHistoryDay>? = null,
    @SerialName("routes") val routes: List<UpstreamRoute>? = null,
    @SerialName("cut") val cut: Boolean = false
)

@Serializable
data class UpstreamHistoryDay(
    @SerialName("day") @Serializable(with = FlexibleStringSerializer::class) val day: String? = null,
    @SerialName("requests") val requests: Long? = null
)

@Serializable
data class UpstreamUsageResponse(
    @SerialName("period") val period: String? = null,
    @SerialName("since") val since: String? = null,
    @SerialName("calls") val calls: Long = 0L,
    @SerialName("errors") val errors: Long = 0L,
    @SerialName("timed") val timed: Long? = null,
    @SerialName("ttft_ms") val ttftMs: Long? = null,
    @SerialName("input") val input: Long = 0L,
    @SerialName("output") val output: Long = 0L,
    @SerialName("cache_read") val cacheRead: Long = 0L,
    @SerialName("cache_write") val cacheWrite: Long = 0L,
    @SerialName("models") val models: List<UpstreamUsageModel>? = null,
    @SerialName("series") val series: List<UpstreamSeriesPoint>? = null,
    @SerialName("hourly") val hourly: List<UpstreamHourlyBucket>? = null,
    @SerialName("range_start") val rangeStart: String? = null,
    @SerialName("range_end") val rangeEnd: String? = null,
    @SerialName("cost") val cost: Double? = null,
    @SerialName("unpriced") val unpriced: Long? = null,
    @SerialName("credits") val credits: Double? = null,
    @SerialName("decode_ms") val decodeMs: Long? = null,
    @SerialName("decode_out") val decodeOut: Long? = null,
    @SerialName("agents") val agents: List<UpstreamUsageAgent>? = null,
    @SerialName("sessions") val sessions: List<UpstreamUsageBreakdown>? = null,
    @SerialName("accounts") val accounts: List<UpstreamUsageBreakdown>? = null,
    @SerialName("callerKeys") val callerKeys: List<UpstreamUsageBreakdown>? = null,
    @SerialName("providerKeys") val providerKeys: List<UpstreamUsageBreakdown>? = null
)

@Serializable
data class UpstreamUsageModel(
    @SerialName("id") val id: String? = null,
    @SerialName("provider") val provider: String? = null,
    @SerialName("model") val model: String? = null,
    @SerialName("calls") val calls: Long = 0L,
    @SerialName("total") val total: Long? = null,
    @SerialName("input") val input: Long? = null,
    @SerialName("output") val output: Long? = null,
    @SerialName("cache_read") val cacheRead: Long? = null,
    @SerialName("cache_write") val cacheWrite: Long? = null
)

@Serializable
data class UpstreamSeriesPoint(
    @SerialName("label") val label: String? = null,
    @SerialName("time") val time: String? = null,
    @SerialName("calls") val calls: Long = 0L,
    @SerialName("cost") val cost: Double? = null
)

@Serializable
data class UpstreamHourlyBucket(
    @SerialName("hour") val hour: String,
    @SerialName("calls") val calls: Long = 0L
)

@Serializable
data class UpstreamLedgerResponse(
    @SerialName("period") val period: String? = null,
    @SerialName("offset") val offset: Int = 0,
    @SerialName("total") val total: Int = 0,
    @SerialName("records") val records: List<UpstreamLedgerRecord>? = null,
    @SerialName("rows") val rows: List<UpstreamLedgerRecord>? = null
)

@Serializable
data class UpstreamLedgerRecord(
    @SerialName("id") val id: String? = null,
    @SerialName("route_id") @Serializable(with = FlexibleStringSerializer::class)
    val routeId: String? = null,
    @SerialName("route") val route: String? = null,
    @SerialName("req") val req: String? = null,
    @SerialName("served") val served: String? = null,
    @SerialName("t") val t: String? = null,
    @SerialName("day") val day: String? = null,
    @SerialName("time") val time: String? = null,
    @SerialName("agent") val agent: String? = null,
    @SerialName("model") val model: String? = null,
    @SerialName("provider") val provider: String? = null,
    @SerialName("account") val account: String? = null,
    @SerialName("providerAccount") val providerAccount: String? = null,
    @SerialName("status") val status: Int? = null,
    @SerialName("failed") val failed: Boolean = false,
    @SerialName("retried") val retried: Boolean = false,
    @SerialName("ttft_ms") val ttftMs: Long? = null,
    @SerialName("ms") val ms: Long? = null,
    @SerialName("elapsed_ms") val elapsedMs: Long? = null,
    @SerialName("in") val inputUncached: Long? = null,
    @SerialName("out") val output: Long? = null,
    @SerialName("cache_read") val cacheRead: Long? = null,
    @SerialName("cache_write") val cacheWrite: Long? = null,
    @SerialName("reasoning") val reasoning: Long? = null
)

@Serializable(with = QuotasResponseSerializer::class)
data class UpstreamQuotasResponse(
    @SerialName("quotas") val quotas: List<UpstreamQuotaAccount>? = null
)

@Serializable
data class UpstreamQuotaAccount(
    @SerialName("account") val account: String? = null,
    @SerialName("provider") val provider: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("user") val user: String? = null,
    @SerialName("readAt") val readAt: String? = null,
    @SerialName("known") val known: Boolean = true,
    @SerialName("observed") val observed: String? = null,
    @SerialName("windows") val windows: List<UpstreamQuotaWindow>? = null
)

@Serializable
data class UpstreamQuotaWindow(
    @SerialName("id") val id: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("unit") val unit: String? = null,
    @SerialName("used") val used: Double? = null,
    @SerialName("amount") val amount: Double? = null,
    @SerialName("limit") val limit: Double? = null,
    @SerialName("display") val display: String? = null,
    @SerialName("unlimited") val unlimited: Boolean = false,
    @SerialName("resetsAt") val resetsAt: String? = null,
    @SerialName("resets") val resets: String? = null
)

@Serializable
data class UpstreamGroupsResponse(
    @SerialName("groups") val groups: List<UpstreamGroup>? = null
)

@Serializable
data class UpstreamGroup(
    @SerialName("id") val id: String,
    @SerialName("name") val name: String? = null,
    @SerialName("routing") val routing: String? = null,
    @SerialName("strategy") val strategy: String? = null,
    @SerialName("affinity") val affinity: String? = null,
    @SerialName("disabled") val disabled: Boolean = false,
    @SerialName("ready") val ready: Boolean? = null,
    @SerialName("members") @Serializable(with = GroupMembersSerializer::class)
    val members: List<UpstreamGroupMember>? = null,
    @SerialName("memberInfo") val memberInfo: List<UpstreamGroupMember>? = null
)

@Serializable
data class UpstreamGroupMember(
    @SerialName("id") val id: String? = null,
    @SerialName("provider") val provider: String? = null,
    @SerialName("account") val account: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("ready") val ready: Boolean = true,
    @SerialName("on") val on: Int? = null,
    @SerialName("group") val group: Boolean = false,
    @SerialName("providerOff") val providerOff: Boolean = false,
    @SerialName("enabled") val enabled: Boolean = true
)

@Serializable
data class UpstreamStateResponse(
    @SerialName("version") val version: String? = null,
    @SerialName("timezone") val timezone: String? = null,
    @SerialName("read_only") val readOnly: Boolean = false
)

/** One agent row of the usage aggregate. */
@Serializable
data class UpstreamUsageAgent(
    @SerialName("id") @Serializable(with = FlexibleStringSerializer::class) val id: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("calls") val calls: Long? = null,
    @SerialName("cost") val cost: Double? = null,
    @SerialName("errors") val errors: Long? = null,
    @SerialName("input") val input: Long? = null,
    @SerialName("output") val output: Long? = null,
    @SerialName("cache_read") val cacheRead: Long? = null,
    @SerialName("cache_write") val cacheWrite: Long? = null,
    @SerialName("reasoning") val reasoning: Long? = null,
    @SerialName("ttft_ms") val ttftMs: Long? = null,
    @SerialName("timed") val timed: Long? = null,
    @SerialName("decode_out") val decodeOut: Long? = null,
    @SerialName("decode_ms") val decodeMs: Long? = null
)

/** One breakdown row for sessions, accounts, caller keys and provider keys. */
@Serializable
data class UpstreamUsageBreakdown(
    @SerialName("id") @Serializable(with = FlexibleStringSerializer::class) val id: String? = null,
    @SerialName("agent") val agent: String? = null,
    @SerialName("account") val account: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("provider") val provider: String? = null,
    @SerialName("calls") val calls: Long? = null,
    @SerialName("cost") val cost: Double? = null,
    @SerialName("credits") val credits: Double? = null,
    @SerialName("errors") val errors: Long? = null,
    @SerialName("input") val input: Long? = null,
    @SerialName("output") val output: Long? = null,
    @SerialName("callerKeyId") @Serializable(with = FlexibleStringSerializer::class)
    val callerKeyId: String? = null,
    @SerialName("callerKeyName") val callerKeyName: String? = null,
    @SerialName("providerKeyId") @Serializable(with = FlexibleStringSerializer::class)
    val providerKeyId: String? = null,
    @SerialName("providerKeyName") val providerKeyName: String? = null,
    @SerialName("unpriced") val unpriced: Long? = null,
    @SerialName("reasoning") val reasoning: Long? = null,
    @SerialName("ttft_ms") val ttftMs: Long? = null,
    @SerialName("timed") val timed: Long? = null,
    @SerialName("decode_out") val decodeOut: Long? = null,
    @SerialName("decode_ms") val decodeMs: Long? = null
)

@Serializable
data class UpstreamProvidersResponse(
    @SerialName("providers") val providers: List<UpstreamProvider>? = null
)

@Serializable
data class UpstreamProvider(
    @SerialName("id") @Serializable(with = FlexibleStringSerializer::class) val id: String,
    @SerialName("name") val name: String? = null,
    @SerialName("host") val host: String? = null,
    @SerialName("ready") val ready: Boolean? = null,
    @SerialName("off") val off: Boolean? = null,
    @SerialName("speaks") @Serializable(with = FlexibleStringListSerializer::class)
    val speaks: List<String>? = null,
    @SerialName("maxConcurrency") val maxConcurrency: Int? = null,
    @SerialName("keyList") val keyList: List<UpstreamProviderKey>? = null,
    @SerialName("models") val models: List<UpstreamProviderModel>? = null
)

@Serializable
data class UpstreamProviderKey(
    @SerialName("name") val name: String? = null
)

@Serializable
data class UpstreamProviderModel(
    @SerialName("id") @Serializable(with = FlexibleStringSerializer::class) val id: String,
    @SerialName("name") val name: String? = null,
    @SerialName("on") val on: Boolean? = null,
    @SerialName("context") val context: Long? = null,
    @SerialName("output") val output: Long? = null,
    @SerialName("images") val images: Boolean? = null,
    @SerialName("efforts") @Serializable(with = FlexibleStringListSerializer::class)
    val efforts: List<String>? = null,
    @SerialName("list") val list: UpstreamModelPricing? = null
)

@Serializable
data class UpstreamModelPricing(
    @SerialName("input") val input: Double? = null,
    @SerialName("output") val output: Double? = null
)

@Serializable
data class UpstreamMaskingSummary(
    @SerialName("n") val n: Long? = null,
    @SerialName("distinct") val distinct: Long? = null,
    @SerialName("requests") val requests: Long? = null,
    @SerialName("blocked") val blocked: Long? = null,
    @SerialName("warned") val warned: Long? = null,
    @SerialName("days") val days: List<UpstreamMaskingDay>? = null,
    @SerialName("kinds") val kinds: List<UpstreamMaskingCount>? = null,
    @SerialName("by") val by: Map<String, Int>? = null,
    @SerialName("agents") val agents: List<UpstreamMaskingBucket>? = null,
    @SerialName("providers") val providers: List<UpstreamMaskingBucket>? = null,
    @SerialName("values") val values: List<UpstreamMaskingBucket>? = null
)

@Serializable
data class UpstreamMaskingDay(
    @SerialName("day") val day: String? = null,
    @SerialName("n") val n: Long? = null,
    @SerialName("requests") val requests: Long? = null
)

@Serializable
data class UpstreamMaskingCount(
    /** Records report the kind as "k"; the summary reports the same value as "name". */
    @SerialName("k") val kind: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("n") val n: Long? = null
)

@Serializable
data class UpstreamMaskingBucket(
    @SerialName("id") val id: String? = null,
    @SerialName("name") val name: String? = null,
    /** Placeholder used by the reveal endpoint, e.g. {{EMAIL_1}}. */
    @SerialName("p") val placeholder: String? = null,
    @SerialName("kind") val kind: String? = null,
    @SerialName("head") val head: String? = null,
    @SerialName("len") val len: Long? = null,
    @SerialName("n") val n: Long? = null
)

@Serializable
data class UpstreamMaskingRecords(
    @SerialName("records") val records: List<UpstreamMaskingRecord>? = null,
    @SerialName("more") val more: Boolean? = null
)

@Serializable
data class UpstreamMaskingRecord(
    /** A string on some builds and a number on others, so the raw element is kept. */
    @SerialName("key") val key: JsonElement? = null,
    @SerialName("values") val values: List<UpstreamMaskingCount>? = null,
    @SerialName("blocked") val blocked: Boolean? = null,
    @SerialName("t") val t: String? = null,
    @SerialName("what") val what: String? = null,
    @SerialName("agent") val agent: String? = null,
    @SerialName("provider") val provider: String? = null,
    @SerialName("model") val model: String? = null,
    @SerialName("snips") val snips: Map<String, String>? = null
)

