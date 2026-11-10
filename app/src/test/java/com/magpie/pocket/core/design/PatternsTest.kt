package com.magpie.pocket.core.design

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PatternsTest {

    @Test
    fun normalizedSeriesPreservesGapsInsteadOfInventingZeros() {
        val series = normalizedSeries(listOf(2f, null, 4f))

        assertEquals(0.5f, series[0]!!, 0.0001f)
        assertNull(series[1])
        assertEquals(1f, series[2]!!, 0.0001f)
    }

    @Test
    fun eachSeriesGetsItsOwnScale() {
        val requests = normalizedSeries(listOf(10f, 20f))
        val money = normalizedSeries(listOf(1f, 3f))

        assertEquals(0.5f, requests[0]!!, 0.0001f)
        assertEquals(1f, requests[1]!!, 0.0001f)
        assertEquals(1f / 3f, money[0]!!, 0.0001f)
        assertEquals(1f, money[1]!!, 0.0001f)
    }

    @Test
    fun zeroAndEmptySeriesStayFlat() {
        assertEquals(listOf(0f, 0f), normalizedSeries(listOf(0f, 0f)))
        assertEquals(listOf<Float?>(null, null), normalizedSeries(listOf(null, null)))
        assertEquals(emptyList<Float?>(), normalizedSeries(emptyList()))
    }

    @Test
    fun numericSpanRangesFindPercentagesAndCounts() {
        val text = "Anthropic 主账号额度已用 87%，按当前用量约 6 小时触顶"
        val highlighted = numericSpanRanges(text).map { text.substring(it.first, it.last + 1) }

        assertEquals(listOf("87%", "6"), highlighted)
    }

    @Test
    fun numericSpanRangesKeepThousandsAndDecimalsTogether() {
        val text = "11-10 共 1,284 条，费用 ¥3.42"
        val highlighted = numericSpanRanges(text).map { text.substring(it.first, it.last + 1) }

        assertEquals(listOf("11", "10", "1,284", "3.42"), highlighted)
    }

    @Test
    fun numericSpanRangesIgnoreTextWithoutNumbers() {
        assertEquals(emptyList<IntRange>(), numericSpanRanges("暂无趋势"))
    }

    @Test
    fun blankValuesPrintAsEmDash() {
        assertEquals("—", displayOrDash(null))
        assertEquals("—", displayOrDash(""))
        assertEquals("—", displayOrDash("   "))
        assertEquals("3.7 s", displayOrDash("3.7 s"))
    }

    @Test
    fun contiguousRunsSplitAtEveryGap() {
        assertEquals(listOf(0..1, 3..4), contiguousRuns(listOf(1f, 2f, null, 4f, 5f)))
        assertEquals(listOf(1..1), contiguousRuns(listOf(null, 2f)))
        assertEquals(listOf(0..2), contiguousRuns(listOf(1f, 2f, 3f)))
        assertEquals(emptyList<IntRange>(), contiguousRuns(listOf(null, null)))
        assertEquals(emptyList<IntRange>(), contiguousRuns(emptyList()))
    }
}
