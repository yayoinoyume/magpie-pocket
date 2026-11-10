package com.magpie.pocket.feature.usage

internal enum class UsageSort(val label: String) {
    COST("费用"),
    TOKENS("Token"),
    CALLS("调用")
}

/** One usage breakdown row; a null metric means the upstream never reported it. */
internal data class UsageRow(
    val name: String,
    val calls: Long?,
    val cost: Double?,
    val tokens: Long?,
    val errors: Long?
)

/**
 * A sort key no row carries cannot order anything, so its chip stays disabled. A dimension
 * whose rows carry no sortable number at all keeps [fallback] alive as the only chip, so the
 * selector never renders a fully dead row.
 */
internal fun availableUsageSorts(rows: List<UsageRow>, fallback: UsageSort): List<UsageSort> =
    UsageSort.values()
        .filter { sort -> rows.any { sortMetric(it, sort) != null } }
        .ifEmpty { listOf(fallback) }

internal fun sortMetric(row: UsageRow, sort: UsageSort): Double? = when (sort) {
    UsageSort.COST -> row.cost
    UsageSort.TOKENS -> row.tokens?.toDouble()
    UsageSort.CALLS -> row.calls?.toDouble()
}
