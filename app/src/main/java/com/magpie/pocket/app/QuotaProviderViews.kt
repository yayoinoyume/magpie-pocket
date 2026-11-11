package com.magpie.pocket.app

import com.magpie.pocket.core.model.Account
import com.magpie.pocket.core.model.AccountQuota
import com.magpie.pocket.core.model.AccountQuotaView
import com.magpie.pocket.core.model.QuotaWindow
import com.magpie.pocket.core.util.MetricCalculators

/**
 * Quotas are a provider dimension: /api/usage/quotas names the provider and its display name, and
 * an account only when the answer happens to carry one. It can answer the same provider more than
 * once (one row per window set, plus an empty row for a provider it can only name), so rows merge
 * by provider id and every window they reported is kept, in first-seen name order.
 *
 * A provider that reported no window is dropped: it has nothing to show. The survivors are sorted
 * worst first, so the ring and the first visible row name the same provider.
 *
 * The provider name is never masked — masking hides credentials, and a provider name is public
 * catalog data. Only an account the upstream actually named is masked and appended.
 */
internal fun providerQuotaViews(
    quotas: List<AccountQuota>,
    accounts: List<Account>,
    maskAccountNames: Boolean
): List<AccountQuotaView> {
    val rowsByProvider = LinkedHashMap<String, MutableList<AccountQuota>>()
    quotas.forEach { quota ->
        rowsByProvider.getOrPut(quota.providerId) { mutableListOf() }.add(quota)
    }
    return rowsByProvider.mapNotNull { (providerId, rows) ->
        val windows = rows.flatMap { it.windows }.numberRepeatedTitles()
        if (windows.isEmpty()) return@mapNotNull null
        val tightest = windows
            .mapNotNull { window ->
                val ratio = MetricCalculators.calculateQuotaUsedRatio(window)
                    ?: return@mapNotNull null
                window to ratio
            }
            .maxByOrNull { it.second }
        // A display-only window has no share to compare, but it does have text to print, so it
        // decides the row when nothing can be measured. An unlimited window keeps saying so on
        // its own and never becomes the row's figure.
        val deciding = tightest?.first ?: windows.firstOrNull { it.text }
        AccountQuotaView(
            providerId = providerId,
            label = providerLabel(rows, accounts, maskAccountNames),
            known = rows.any { it.known },
            tightestWindow = deciding,
            ratio = tightest?.second,
            windows = windows,
            usedPercent = quotaUsedPercent(deciding),
            remainingPercent = quotaRemainingPercent(deciding),
            usedAmount = deciding?.amount,
            remainingAmount = quotaRemainingAmount(deciding),
            unitLabel = deciding?.unit?.takeIf { it.isNotBlank() },
            displayText = deciding?.display?.takeIf { it.isNotBlank() }
        )
    }.sortedWith(
        // Worst first: the ring reads the first row, and the worst provider must stay visible.
        // Ties break on the provider id, never on the label, so flipping masking cannot reorder.
        compareByDescending<AccountQuotaView> { it.ratio ?: Double.NEGATIVE_INFINITY }
            .thenBy { it.providerId }
    )
}

/**
 * Every window a provider reported, grouped by name. A name the provider repeated gets #2, #3 …
 * so two windows never read the same; the SVC dashboard numbers them the same way.
 */
private fun List<QuotaWindow>.numberRepeatedTitles(): List<QuotaWindow> {
    val byName = LinkedHashMap<String, MutableList<QuotaWindow>>()
    forEach { window -> byName.getOrPut(window.id) { mutableListOf() }.add(window) }
    return byName.flatMap { (name, windows) ->
        windows.mapIndexed { index, window ->
            if (index == 0) window else window.copy(id = name + " #" + (index + 1))
        }
    }
}

/** The provider name, plus the masked alias of the first account its rows named. */
private fun providerLabel(
    rows: List<AccountQuota>,
    accounts: List<Account>,
    maskAccountNames: Boolean
): String {
    val name = rows.firstNotNullOfOrNull { row ->
        row.displayName?.takeIf { it.isNotBlank() }
    } ?: rows.first().providerId
    val accountId = rows.firstNotNullOfOrNull { it.accountId } ?: return name
    val alias = accounts.firstOrNull { it.id == accountId }?.alias
    return name + " · " + MetricCalculators.maskAccountName(alias ?: accountId, maskAccountNames)
}

/**
 * The used share of one window as a 0..100 percentage. A display-only, unknown or unlimited
 * window has no number, and neither side of the pair is invented for it.
 */
internal fun quotaUsedPercent(window: QuotaWindow?): Double? = window
    ?.let { MetricCalculators.calculateQuotaUsedRatio(it) }
    ?.let { (it * 100.0).coerceAtMost(100.0) }

/** The same share read the other way: what is still left of the allowance. */
internal fun quotaRemainingPercent(window: QuotaWindow?): Double? =
    quotaUsedPercent(window)?.let { 100.0 - it }

/** Allowance minus used, only when the answer reported both; a share alone cannot give it. */
internal fun quotaRemainingAmount(window: QuotaWindow?): Double? {
    val amount = window?.amount ?: return null
    val allowance = window.allowanceLimit ?: return null
    return (allowance - amount).coerceAtLeast(0.0)
}
