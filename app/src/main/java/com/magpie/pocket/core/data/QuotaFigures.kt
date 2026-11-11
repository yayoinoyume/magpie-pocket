package com.magpie.pocket.core.data

/**
 * A quota figure is either a share of its allowance or an absolute count. The gateway sends
 * `used` alone for a share, and amount/limit (or a count above 100) for a count; reading a count
 * as a share would show a 301% window. The live and the demo answers both map through this rule.
 */
internal fun quotaFigureIsCount(used: Double?, amount: Double?, limit: Double?): Boolean =
    amount != null || limit != null || (used ?: 0.0) > FULL_PERCENT

/** A share can never exceed 100. */
private const val FULL_PERCENT = 100.0
