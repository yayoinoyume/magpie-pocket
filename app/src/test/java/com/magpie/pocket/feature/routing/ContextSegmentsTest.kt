package com.magpie.pocket.feature.routing

import com.magpie.pocket.core.model.ContextPart
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextSegmentsTest {
    @Test
    fun everyKnownKindHasItsOwnLabel() {
        assertEquals("系统提示", "system".contextLabel())
        assertEquals("工具", "tools".contextLabel())
        assertEquals("技能", "skills".contextLabel())
        assertEquals("记忆", "memory".contextLabel())
        assertEquals("文件", "files".contextLabel())
        assertEquals("工具结果", "results".contextLabel())
        assertEquals("会话", "chat".contextLabel())
    }

    @Test
    fun unknownKindIsShownAsIs() {
        assertEquals("custom", "custom".contextLabel())
    }

    @Test
    fun legacyDemoKindsKeepTheirLabels() {
        assertEquals("工具结果", "tool_results".contextLabel())
        assertEquals("会话", "conversation".contextLabel())
        assertEquals("其他", "other".contextLabel())
    }

    @Test
    fun fivePartsFoldTheTailIntoOneMergedSegment() {
        val parts = listOf(
            part("files", 50),
            part("memory", 40),
            part("chat", 30),
            part("tools", 20),
            part("results", 10)
        )
        val segments = contextSegments(parts, totalTokens = 150)
        assertEquals(3, segments.size)
        assertEquals(listOf("文件", "记忆", "其他"), segments.map { it.label })
        assertEquals(listOf(50L, 40L, 60L), segments.map { it.tokens })
        assertFalse(segments[0].merged)
        assertFalse(segments[1].merged)
        assertTrue(segments[2].merged)
    }

    @Test
    fun threePartsStaySeparate() {
        val segments = contextSegments(
            listOf(part("system", 70), part("tools", 20), part("chat", 10)),
            totalTokens = 100
        )
        assertEquals(listOf("系统提示", "工具", "会话"), segments.map { it.label })
        assertEquals(listOf(70L, 20L, 10L), segments.map { it.tokens })
        assertTrue(segments.none { it.merged })
    }

    @Test
    fun aSinglePartIsOneUnmergedSegment() {
        val segments = contextSegments(listOf(part("skills", 12)), totalTokens = 100)
        assertEquals(1, segments.size)
        assertEquals("技能", segments.single().label)
        assertEquals(12L, segments.single().tokens)
        assertFalse(segments.single().merged)
    }

    @Test
    fun noPartsMeansNoSegments() {
        assertTrue(contextSegments(emptyList(), totalTokens = 100).isEmpty())
        assertTrue(contextSegments(emptyList(), totalTokens = 0).isEmpty())
    }

    @Test
    fun equalTokensSortByKindForStableOrder() {
        val segments = contextSegments(
            listOf(part("tools", 10), part("chat", 10), part("files", 10)),
            totalTokens = 30
        )
        assertEquals(listOf("会话", "文件", "工具"), segments.map { it.label })
    }

    @Test
    fun fourPartsFoldOnlyTheSmallestIntoOneMergedSegment() {
        val segments = contextSegments(
            listOf(part("system", 40), part("tools", 30), part("chat", 20), part("files", 10)),
            totalTokens = 100
        )
        assertEquals(3, segments.size)
        assertEquals(listOf("系统提示", "工具", "其他"), segments.map { it.label })
        assertEquals(listOf(40L, 30L, 30L), segments.map { it.tokens })
        assertFalse(segments[0].merged)
        assertFalse(segments[1].merged)
        assertTrue(segments[2].merged)
    }

    @Test
    fun zeroTotalTokensMeansNoSegments() {
        assertTrue(contextSegments(listOf(part("system", 10)), totalTokens = 0).isEmpty())
    }

    @Test
    fun mergedTailUsesAnotherLabelWhenOtherIsAlreadyShown() {
        val segments = contextSegments(
            listOf(part("other", 50), part("system", 30), part("tools", 15), part("chat", 5)),
            totalTokens = 100
        )
        assertEquals(listOf("其他", "系统提示", "其余"), segments.map { it.label })
        assertTrue(segments[2].merged)
    }

    @Test
    fun partsWithoutTokensAreDroppedBeforeSegmentation() {
        val segments = contextSegments(
            listOf(part("system", 0), part("tools", 30), part("chat", 70)),
            totalTokens = 100
        )
        assertEquals(listOf("会话", "工具"), segments.map { it.label })
        assertEquals(listOf(70L, 30L), segments.map { it.tokens })
    }

    private fun part(kind: String, tokens: Long) = ContextPart(kind = kind, tokens = tokens)
}
