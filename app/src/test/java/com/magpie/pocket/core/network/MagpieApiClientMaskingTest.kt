package com.magpie.pocket.core.network

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MagpieApiClientMaskingTest {

    private lateinit var server: MockWebServer
    private lateinit var client: MagpieApiClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = MagpieApiClient()
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
    fun maskingRecordsPercentEncodeEveryFilter() = runBlocking {
        server.enqueue(json("""{"records":[],"more":false}"""))

        client.getMaskingRecords(
            baseUrl = server.url("/").toString(),
            limit = 5,
            kind = "API KEY",
            by = "agent",
            agent = "a&b",
            provider = "p=q"
        )

        assertEquals(
            "/api/masking/records?limit=5&kind=API%20KEY&by=agent&agent=a%26b&provider=p%3Dq",
            server.takeRequest().path
        )
    }

    @Test
    fun maskingRecordsSkipBlankFilters() = runBlocking {
        server.enqueue(json("""{"records":[],"more":false}"""))

        client.getMaskingRecords(server.url("/").toString(), limit = 10, agent = "  ")

        assertEquals("/api/masking/records?limit=10", server.takeRequest().path)
    }

    @Test
    fun maskingValuePercentEncodesPlaceholder() = runBlocking {
        server.enqueue(json("""{"value":"demo@example.test"}"""))

        val value = client.getMaskingValue(server.url("/").toString(), "<EMAIL 1>&x")

        assertEquals("demo@example.test", value)
        val path = server.takeRequest().path ?: ""
        assertTrue(path.startsWith("/api/masking/value?p="))
        assertEquals("/api/masking/value?p=%3CEMAIL%201%3E%26x", path)
    }

    @Test
    fun maskingValueReturnsNullOnBareJsonNull() = runBlocking {
        server.enqueue(json("null"))

        val value = client.getMaskingValue(server.url("/").toString(), "{{EMAIL_1}}")

        assertNull(value)
    }

    @Test
    fun maskingValueReturnsNullOnExplicitNullValue() = runBlocking {
        server.enqueue(json("""{"value":null}"""))

        val value = client.getMaskingValue(server.url("/").toString(), "{{EMAIL_1}}")

        assertNull(value)
    }

    @Test
    fun maskingSummarySendsTheRequestedDayWindow() = runBlocking {
        server.enqueue(json("""{"n":1}"""))

        val summary = client.getMaskingSummary(server.url("/").toString(), 30)

        assertEquals(1L, summary.n ?: 0L)
        assertEquals("/api/masking/summary?days=30", server.takeRequest().path)
    }

    @Test
    fun providersUseTheCatalogEndpoint() = runBlocking {
        server.enqueue(json("""{"providers":[]}"""))

        val resp = client.getProviders(server.url("/").toString())

        assertTrue(resp.providers == null || resp.providers!!.isEmpty())
        assertEquals("/api/providers", server.takeRequest().path)
    }
}
