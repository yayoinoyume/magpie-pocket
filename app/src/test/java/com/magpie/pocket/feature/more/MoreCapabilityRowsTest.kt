package com.magpie.pocket.feature.more

import com.magpie.pocket.core.model.Capabilities
import org.junit.Assert.assertEquals
import org.junit.Test

class MoreCapabilityRowsTest {

    @Test
    fun unknownCapabilitiesStayDashInsteadOfBorrowingDefaults() {
        val rows = capabilityRows(null)

        assertEquals(listOf("路由追踪", "历史账本", "用量", "额度", "路由组"), rows.map { it.first })
        assertEquals(listOf(null, null, null, null, null), rows.map { it.second })
    }

    @Test
    fun eachRowReportsItsOwnCapability() {
        val rows = capabilityRows(
            Capabilities(trace = false, ledger = true, usage = false, quotas = true, groups = false)
        )

        assertEquals(listOf(false, true, false, true, false), rows.map { it.second })
    }

    @Test
    fun unsupportedAndUnknownAreDifferentAnswers() {
        val rows = capabilityRows(Capabilities(trace = false, ledger = true))

        assertEquals(false, rows.first { it.first == "路由追踪" }.second)
        assertEquals(true, rows.first { it.first == "历史账本" }.second)
    }
}
