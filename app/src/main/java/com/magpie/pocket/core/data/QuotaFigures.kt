package com.magpie.pocket.core.data

/**
 * A quota figure is either a share of its allowance or an absolute count. The gateway sends
 * `used` as a 0..100 share even when the window also carries a `limit`, so the presence of a
 * limit says nothing about the unit; only a value above 100 is a count on its own. The live and
 * the demo answers both map through this rule, and the mapping keeps the absolute pair
 * (amount/limit) next to the share so the ratio can prefer the more precise one.
 */
internal fun quotaFigureIsCount(used: Double?): Boolean = (used ?: 0.0) > FULL_PERCENT

/** A share can never exceed 100. */
private const val FULL_PERCENT = 100.0
