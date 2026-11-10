package com.magpie.pocket.core.util

import com.magpie.pocket.core.model.Attempt
import com.magpie.pocket.core.model.TokenUsage

/** Request-level derivations shared by the demo and live repositories. */
object RequestDerivations {

    /**
     * One reroute count for every surface. A route detail knows its attempts, so the
     * count is the handoffs between them. The ledger omits attempts and can only report
     * the upstream `retried` flag. No evidence means no reroute, never a guess.
     */
    fun rerouteCount(attempts: List<Attempt>, retried: Boolean = false): Int = when {
        attempts.isNotEmpty() -> (attempts.size - 1).coerceAtLeast(0)
        retried -> 1
        else -> 0
    }

    /**
     * Token usage that keeps the upstream's missing categories missing. A row with no
     * token evidence at all has no usage, so the UI shows an em dash instead of zero.
     */
    fun tokenUsage(
        inputUncached: Long?,
        output: Long?,
        cacheRead: Long?,
        cacheWrite: Long?,
        reasoningIncludedInOutput: Long? = null
    ): TokenUsage? {
        if (inputUncached == null && output == null && cacheRead == null && cacheWrite == null) {
            return null
        }
        return TokenUsage(
            inputUncached = inputUncached,
            output = output,
            cacheRead = cacheRead,
            cacheWrite = cacheWrite,
            reasoningIncludedInOutput = reasoningIncludedInOutput
        )
    }
}
