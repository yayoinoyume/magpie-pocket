package com.magpie.pocket.app

import com.magpie.pocket.core.model.AccountQuota
import com.magpie.pocket.core.model.Attempt
import com.magpie.pocket.core.model.Coverage
import com.magpie.pocket.core.model.DataMode
import com.magpie.pocket.core.model.FinalState
import com.magpie.pocket.core.model.QuotaWindow
import com.magpie.pocket.core.model.RequestKey
import com.magpie.pocket.core.model.RequestSummary
import com.magpie.pocket.core.model.SourceMeta
import com.magpie.pocket.core.util.RequestTimestamps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationTextTest {

    private fun running(
        routeId: String,
        startedAt: String,
        agentId: String? = "DimAgent",
        model: String? = "deepseek-v4.1-flash"
    ): RequestSummary = RequestSummary(
        key = RequestKey("connection", "epoch", routeId),
        startedAt = startedAt,
        agentId = agentId,
        requestedModel = model,
        finalState = FinalState.IN_PROGRESS,
        meta = SourceMeta(
            connectionId = "connection",
            mode = DataMode.LIVE,
            observedAt = startedAt,
            fetchedAt = startedAt,
            coverage = Coverage.COMPLETE
        )
    )

    private fun reading(
        ratio: Double,
        windowKey: String = "account|5h",
        providerId: String = "volcengine",
        providerLabel: String = "Volcengine Ark"
    ): QuotaReading = QuotaReading(
        providerId = providerId,
        providerLabel = providerLabel,
        windowLabel = "5 小时",
        windowKey = windowKey,
        ratio = ratio
    )

    @Test
    fun summaryWithDataPrintsCountAndTheOldestRunningRequest() {
        val started = "2026-11-12T12:00:00Z"
        val start = RequestTimestamps.parseMillis(started)!!

        val text = NotificationText.keepalive(
            completedRequests = 6774,
            running = listOf(running("r1", started)),
            nowMillis = start + 27_700
        )

        assertEquals("今日 6,774 请求 · 1 条处理中", text.title)
        assertEquals("deepseek-v4.1-flash · DimAgent · 27.7s", text.text)
        assertEquals(listOf("deepseek-v4.1-flash · DimAgent · 27.7s"), text.inboxLines)
        assertEquals("1 条正在运行 · 实时更新", text.footer)
    }

    @Test
    fun summaryWithoutOverviewFallsBackToTheKeepaliveCopy() {
        val text = NotificationText.keepalive(
            completedRequests = null,
            running = emptyList(),
            nowMillis = 1_000_000L
        )

        assertEquals("Magpie Pocket", text.title)
        assertEquals("后台保持实时连接", text.text)
        assertTrue(text.inboxLines.isEmpty())
        assertNull(text.footer)
    }

    @Test
    fun summaryWithZeroRunningKeepsTheTodayCount() {
        val text = NotificationText.keepalive(
            completedRequests = 6774,
            running = emptyList(),
            nowMillis = 1_000_000L
        )

        assertEquals("今日 6,774 请求 · 0 条处理中", text.title)
        assertNull(text.text)
        assertTrue(text.inboxLines.isEmpty())
        assertNull(text.footer)
    }

    @Test
    fun summaryExpandsOnlyTheThreeOldestRunningRequests() {
        val now = RequestTimestamps.parseMillis("2026-11-12T12:00:10Z")!!
        val requests = (1..5).map { secondsAgo ->
            running("r$secondsAgo", "2026-11-12T12:00:0${10 - secondsAgo}Z")
        }
        val shuffled = listOf(requests[1], requests[4], requests[2], requests[0], requests[3])

        val text = NotificationText.keepalive(
            completedRequests = 42,
            running = shuffled,
            nowMillis = now
        )

        assertEquals(
            listOf(
                "deepseek-v4.1-flash · DimAgent · 5.0s",
                "deepseek-v4.1-flash · DimAgent · 4.0s",
                "deepseek-v4.1-flash · DimAgent · 3.0s"
            ),
            text.inboxLines
        )
        assertEquals("5 条正在运行 · 实时更新", text.footer)
    }

    @Test
    fun compactModeDropsTheExpandedInbox() {
        val started = "2026-11-12T12:00:00Z"
        val start = RequestTimestamps.parseMillis(started)!!

        val text = NotificationText.keepalive(
            completedRequests = 12,
            running = listOf(running("r1", started)),
            nowMillis = start + 4_100,
            verbose = false
        )

        assertEquals("今日 12 请求 · 1 条处理中", text.title)
        assertEquals("deepseek-v4.1-flash · DimAgent · 4.1s", text.text)
        assertTrue(text.inboxLines.isEmpty())
        assertNull(text.footer)
    }

    @Test
    fun elapsedFormattingKeepsMillisAndOneDecimalSeconds() {
        assertEquals("756ms", NotificationText.formatElapsed(756))
        assertEquals("4.1s", NotificationText.formatElapsed(4_100))
        assertEquals("27.7s", NotificationText.formatElapsed(27_700))
    }

    @Test
    fun quotaReadingsUseTheExistingQuotaFigureRatio() {
        val quota = AccountQuota(
            providerId = "p",
            displayName = "Provider",
            known = true,
            windows = listOf(
                QuotaWindow(id = "5h", unit = "", amount = 90.0, allowanceLimit = 100.0)
            )
        )

        val reading = NotificationText.quotaReadings(listOf(quota)).single()

        assertEquals(0.9, reading.ratio, 1e-9)
        assertEquals("|5h", reading.windowKey)
    }

    @Test
    fun eightyNinePointNinePercentDoesNotCross() {
        val gate = QuotaAlertGate(FakeQuotaAlertStore()) { 1_000_000L }

        assertTrue(gate.evaluate(listOf(reading(0.899))).isEmpty())
    }

    @Test
    fun ninetyPercentCrossesOncePerTier() {
        val gate = QuotaAlertGate(FakeQuotaAlertStore()) { 1_000_000L }

        assertEquals(1, gate.evaluate(listOf(reading(0.9))).size)
        assertTrue(gate.evaluate(listOf(reading(0.9))).isEmpty())
    }

    @Test
    fun fallingBelowTheThresholdRearmsTheSameTier() {
        val gate = QuotaAlertGate(FakeQuotaAlertStore()) { 1_000_000L }

        assertEquals(1, gate.evaluate(listOf(reading(0.95))).size)
        assertTrue(gate.evaluate(listOf(reading(0.5))).isEmpty())
        assertEquals(1, gate.evaluate(listOf(reading(0.95))).size)
    }

    @Test
    fun muteSuppressesUntilItExpiresThenAllowsTheSameTier() {
        var now = 1_000_000L
        val gate = QuotaAlertGate(FakeQuotaAlertStore()) { now }

        assertEquals(1, gate.evaluate(listOf(reading(0.95))).size)
        gate.muteFor()

        now += QuotaAlertGate.MUTE_DURATION_MILLIS - 1L
        assertTrue(gate.evaluate(listOf(reading(0.95))).isEmpty())

        now += 2L
        assertEquals(1, gate.evaluate(listOf(reading(0.95))).size)
    }

    @Test
    fun quotaAlertMergesABatchIntoOneNotification() {
        val text = NotificationText.quotaAlert(
            listOf(
                reading(0.92, "a|5h", "a", "Alpha"),
                reading(1.0, "b|day", "b", "Beta")
            )
        )

        assertEquals("Beta 额度已用 100%", text.title)
        assertEquals("另有 1 个额度窗口接近上限。路由可能会被上游拒绝。", text.text)
        assertEquals(2, text.inboxLines.size)
    }

    @Test
    fun failureTextReadsTheLastAttempt() {
        val failed = running("r1", "2026-11-12T12:00:00Z").copy(
            finalState = FinalState.FAILED,
            attempts = listOf(
                Attempt(index = 1, httpStatus = 429, errorCode = "rate_limited", handedOn = true),
                Attempt(index = 2, httpStatus = 504, errorCode = "gateway_timeout")
            )
        )

        assertEquals("HTTP 504 · gateway_timeout", NotificationText.failureText(failed))
    }
}

private class FakeQuotaAlertStore : QuotaAlertStore {
    private val values = mutableMapOf<String, String>()

    override fun read(key: String): String? = values[key]

    override fun write(key: String, value: String) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }

    override fun keys(): Set<String> = values.keys.toSet()
}
