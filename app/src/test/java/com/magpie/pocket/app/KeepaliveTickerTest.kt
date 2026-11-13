package com.magpie.pocket.app

import com.magpie.pocket.core.model.Coverage
import com.magpie.pocket.core.model.DataMode
import com.magpie.pocket.core.model.FinalState
import com.magpie.pocket.core.model.RequestKey
import com.magpie.pocket.core.model.RequestSummary
import com.magpie.pocket.core.model.SourceMeta
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** The keepalive redraw clock: it only runs while the notification has a live row to age. */
@OptIn(ExperimentalCoroutinesApi::class)
class KeepaliveTickerTest {

    private fun running(routeId: String): RequestSummary = RequestSummary(
        key = RequestKey("connection", "epoch", routeId),
        startedAt = "2026-11-12T12:00:00Z",
        agentId = "DimAgent",
        requestedModel = "deepseek-v4.1-flash",
        finalState = FinalState.IN_PROGRESS,
        meta = SourceMeta(
            connectionId = "connection",
            mode = DataMode.LIVE,
            observedAt = "2026-11-12T12:00:00Z",
            fetchedAt = "2026-11-12T12:00:00Z",
            coverage = Coverage.COMPLETE
        )
    )

    @Test
    fun ticksOnceASecondWhileRequestsAreRunning() = runTest {
        val state = MutableStateFlow(listOf(running("r1")))
        val revisions = mutableListOf<Long>()
        val job = launch { keepaliveTicks(state, 1_000L).collect { revisions += it } }
        runCurrent()

        assertEquals(1, revisions.size)

        advanceTimeBy(1_001L)
        runCurrent()
        assertEquals(2, revisions.size)

        advanceTimeBy(2_000L)
        runCurrent()
        assertEquals(4, revisions.size)
        assertEquals(revisions.size, revisions.distinct().size)

        job.cancelAndJoin()
    }

    @Test
    fun anEmptyListFlushesOnceAndNeverTicksAgain() = runTest {
        val state = MutableStateFlow(emptyList<RequestSummary>())
        val revisions = mutableListOf<Long>()
        val job = launch { keepaliveTicks(state, 1_000L).collect { revisions += it } }
        runCurrent()

        assertEquals(1, revisions.size)

        advanceTimeBy(5_000L)
        runCurrent()
        assertEquals(1, revisions.size)

        job.cancelAndJoin()
    }

    @Test
    fun drainingTheRunningListStopsTheTicks() = runTest {
        val state = MutableStateFlow(listOf(running("r1")))
        val revisions = mutableListOf<Long>()
        val job = launch { keepaliveTicks(state, 1_000L).collect { revisions += it } }
        runCurrent()

        advanceTimeBy(2_001L)
        runCurrent()
        assertEquals(3, revisions.size)

        state.value = emptyList()
        runCurrent()
        assertEquals(4, revisions.size)

        advanceTimeBy(5_000L)
        runCurrent()
        assertEquals(4, revisions.size)

        job.cancelAndJoin()
    }
}
