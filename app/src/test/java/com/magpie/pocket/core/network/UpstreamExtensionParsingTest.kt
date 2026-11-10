package com.magpie.pocket.core.network

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpstreamExtensionParsingTest {

    private val json = MagpieApiClient().json

    @Test
    fun usageParsesCostCreditsDecodeAndEveryBreakdownDimension() {
        val usage = json.decodeFromString(UpstreamUsageResponse.serializer(), 
            """
            {
              "calls": 12,
              "cost": 1.25,
              "unpriced": 3,
              "credits": 7.5,
              "decode_ms": 4000,
              "decode_out": 600,
              "series": [{"label": "2026-10-21", "calls": 9, "cost": 0.75}],
              "agents": [{"id": 7, "name": "orchestrator", "calls": 5, "cost": 0.5, "errors": 1, "input": 10, "output": 20, "cache_read": 30, "cache_write": 40}],
              "sessions": [{"id": "s1", "agent": "orchestrator", "calls": 2, "cost": 0.2, "credits": 1.0, "input": 5, "output": 6, "errors": 0, "reasoning": 7, "ttft_ms": 900, "timed": 4, "decode_out": 120, "decode_ms": 1000}],
              "accounts": [{"id": "a1", "account": "wb-01", "name": "main", "provider": "workbuddy", "calls": 3, "cost": 0.4, "credits": 2.0, "errors": 1}],
              "callerKeys": [{"id": "ck1", "callerKeyId": "ck1", "name": "IDE", "callerKeyName": "IDE", "calls": 4, "cost": 0.6, "credits": 0.0, "errors": 0, "unpriced": 2}],
              "providerKeys": [{"id": "pk1", "providerKeyId": "pk1", "provider": "workbuddy", "providerKeyName": "workbuddy-key-01", "calls": 6, "cost": 0.7, "credits": 0.0}]
            }
            """.trimIndent()
        )

        assertEquals(1.25, usage.cost ?: 0.0, 0.0001)
        assertEquals(3L, usage.unpriced ?: 0L)
        assertEquals(7.5, usage.credits ?: 0.0, 0.0001)
        assertEquals(4000L, usage.decodeMs ?: 0L)
        assertEquals(600L, usage.decodeOut ?: 0L)
        assertEquals(0.75, usage.series?.single()?.cost ?: 0.0, 0.0001)

        val agent = usage.agents?.single() ?: error("missing agent row")
        assertEquals("7", agent.id)
        assertEquals("orchestrator", agent.name)
        assertEquals(1L, agent.errors ?: 0L)
        assertEquals(40L, agent.cacheWrite ?: 0L)

        val session = usage.sessions?.single() ?: error("missing session row")
        assertEquals("orchestrator", session.agent)
        assertEquals(7L, session.reasoning ?: 0L)
        assertEquals(900L, session.ttftMs ?: 0L)
        assertEquals(4L, session.timed ?: 0L)
        assertEquals(120L, session.decodeOut ?: 0L)
        assertEquals(1000L, session.decodeMs ?: 0L)
        assertEquals(2.0, usage.accounts?.single()?.credits ?: 0.0, 0.0001)
        assertEquals("IDE", usage.callerKeys?.single()?.callerKeyName)
        assertEquals(2L, usage.callerKeys?.single()?.unpriced ?: 0L)
        assertEquals("workbuddy-key-01", usage.providerKeys?.single()?.providerKeyName)
    }

    @Test
    fun usageLeavesMissingCostAndBreakdownsNull() {
        val usage = json.decodeFromString(UpstreamUsageResponse.serializer(), """{"calls": 2}""")

        assertNull(usage.cost)
        assertNull(usage.unpriced)
        assertNull(usage.credits)
        assertNull(usage.decodeMs)
        assertNull(usage.decodeOut)
        assertNull(usage.agents)
        assertNull(usage.sessions)
        assertNull(usage.accounts)
        assertNull(usage.callerKeys)
        assertNull(usage.providerKeys)
    }

    @Test
    fun providersParseNestedModelsAndPriceList() {
        val resp = json.decodeFromString(UpstreamProvidersResponse.serializer(), 
            """
            {"providers":[{
              "id": 7,
              "name": "WorkBuddy",
              "host": "api.example",
              "ready": true,
              "off": false,
              "speaks": ["openai-chat", "anthropic-messages"],
              "maxConcurrency": 8,
              "keyList": [{"name": "k1"}, {"name": "k2"}],
              "models": [
                {"id": "m1", "name": "Model 1", "on": true, "context": 128000, "output": 8192, "images": false, "efforts": ["low", "high"], "list": {"input": 0.14, "output": 0.28}},
                {"id": "m2", "on": null}
              ]
            }]}
            """.trimIndent()
        )

        val provider = resp.providers?.single() ?: error("missing provider")
        assertEquals("7", provider.id)
        assertEquals(listOf("openai-chat", "anthropic-messages"), provider.speaks)
        assertEquals(listOf("k1", "k2"), provider.keyList?.map { it.name })

        val first = provider.models?.first() ?: error("missing model")
        assertEquals("m1", first.id)
        assertEquals(128000L, first.context ?: 0L)
        assertEquals(listOf("low", "high"), first.efforts)
        assertEquals(0.14, first.list?.input ?: 0.0, 0.0001)
        assertEquals(0.28, first.list?.output ?: 0.0, 0.0001)

        val second = provider.models?.last() ?: error("missing second model")
        assertNull(second.name)
        assertNull(second.on)
        assertNull(second.list)
    }

    @Test
    fun maskingSummaryParsesNamedKindsAndPlaceholderBuckets() {
        val summary = json.decodeFromString(UpstreamMaskingSummary.serializer(), 
            """
            {
              "n": 42,
              "distinct": 5,
              "requests": 37,
              "blocked": 3,
              "warned": 12,
              "days": [{"day": "2026-10-21", "n": 18, "requests": 16}],
              "kinds": [{"name": "EMAIL", "n": 18}, {"k": "API_KEY", "n": 9}],
              "by": {"EMAIL": 18, "API_KEY": 9},
              "agents": [{"id": "orchestrator", "name": "orchestrator", "n": 21}],
              "providers": [{"id": "workbuddy", "name": "workbuddy", "n": 26}],
              "values": [{"p": "{{EMAIL_1}}", "kind": "EMAIL", "head": "ab", "len": 18, "n": 4}]
            }
            """.trimIndent()
        )

        assertEquals(42L, summary.n ?: 0L)
        assertEquals(5L, summary.distinct ?: 0L)
        assertEquals(12L, summary.warned ?: 0L)
        assertEquals("2026-10-21", summary.days?.single()?.day)
        assertEquals(16L, summary.days?.single()?.requests ?: 0L)
        // The summary names the kind, records use "k"; both must resolve to the same value.
        assertEquals(listOf("EMAIL", "API_KEY"), summary.kinds?.map { it.kind ?: it.name })
        assertEquals("EMAIL", summary.kinds?.first()?.name)
        assertNull(summary.kinds?.first()?.kind)
        assertEquals("API_KEY", summary.kinds?.last()?.kind)
        assertEquals(18, summary.by?.get("EMAIL"))
        assertEquals(21L, summary.agents?.single()?.n ?: 0L)
        assertEquals("workbuddy", summary.providers?.single()?.name)
        val value = summary.values?.single() ?: error("missing value bucket")
        assertEquals("{{EMAIL_1}}", value.placeholder)
        assertEquals("EMAIL", value.kind)
        assertEquals("ab", value.head)
        assertEquals(18L, value.len ?: 0L)
        assertEquals(4L, value.n ?: 0L)
        assertNull(value.id)
    }

    @Test
    fun maskingRecordsAcceptTextAndNumericKeys() {
        val resp = json.decodeFromString(UpstreamMaskingRecords.serializer(), 
            """
            {
              "records": [
                {"key": "701", "values": [{"k": "EMAIL", "n": 1}], "blocked": false, "t": "2026-10-22T01:12:00Z", "what": "body", "agent": "orchestrator", "provider": "workbuddy", "model": "m1", "snips": {"EMAIL_1": "<EMAIL_1>"}},
                {"key": 702, "values": [{"k": "API_KEY", "n": 2}], "blocked": true}
              ],
              "more": true
            }
            """.trimIndent()
        )

        val records = resp.records ?: error("missing records")
        assertEquals(2, records.size)
        assertEquals("701", (records[0].key as JsonPrimitive).content)
        assertEquals("EMAIL", records[0].values?.single()?.kind)
        assertEquals("body", records[0].what)
        assertEquals("<EMAIL_1>", records[0].snips?.get("EMAIL_1"))
        assertEquals("702", (records[1].key as JsonPrimitive).content)
        assertEquals(true, records[1].blocked)
        assertEquals(true, resp.more)
    }

    @Test
    fun historyDaysAcceptBareStringsAndObjects() {
        val history = json.decodeFromString(UpstreamHistoryResponse.serializer(), 
            """{"days": ["2026-10-20", {"day": "2026-10-21", "requests": 7}]}"""
        )

        val days = history.days ?: error("missing days")
        assertEquals(2, days.size)
        assertEquals("2026-10-20", days[0].day)
        assertNull(days[0].requests)
        assertEquals("2026-10-21", days[1].day)
        assertEquals(7L, days[1].requests ?: 0L)
        assertTrue(days[1].requests != null)
    }
}
