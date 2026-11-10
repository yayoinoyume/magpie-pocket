package com.magpie.pocket.core.model

/**
 * Spend with its unpriced tail kept visible: a priced zero and "no price at all" are
 * different answers, so [value] stays null while [unpriced] says how many calls are unknown.
 */
data class CostDisplay(
    val value: Double?,
    val unpriced: Long
) {
    val hasUnpriced: Boolean get() = unpriced > 0L
}

/**
 * One account reduced to the single window that decides its health. The ring and the
 * account row read this, so the same account can never show two percentages.
 */
data class AccountQuotaView(
    val accountId: String,
    val label: String,
    val known: Boolean,
    val tightestWindow: QuotaWindow?,
    val ratio: Double?
)

/** A quota window already inside the warning zone; [accountLabel] is masked per preference. */
data class QuotaAlert(
    val accountLabel: String,
    val ratio: Double,
    val windowId: String,
    val resetsAt: String?
)

/**
 * How much of the shown data the upstream actually covers. Enum order is severity:
 * COMPLETE, RECENT_ONLY, PARTIAL, UNKNOWN.
 */
data class DataCoverage(
    val coverage: Coverage,
    val stale: Boolean
)

/** A request-list filter the active connection can apply. [code] is the wire value. */
enum class RequestFilter(val code: String) {
    ALL("ALL"),
    RUNNING("RUNNING"),
    REROUTED("REROUTED"),
    ERROR("ERROR")
}
