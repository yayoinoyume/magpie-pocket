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
 * by provider id and windows merge by window id, keeping the first window that reported a value.
 *
 * A provider that reported no window is dropped: it has nothing to show.
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
        val windows = mergeQuotaWindows(rows.flatMap { it.windows })
        if (windows.isEmpty()) return@mapNotNull null
        val tightest = windows
            .mapNotNull { window ->
                val ratio = MetricCalculators.calculateQuotaUsedRatio(window)
                    ?: return@mapNotNull null
                window to ratio
            }
            .maxByOrNull { it.second }
        AccountQuotaView(
            providerId = providerId,
            label = providerLabel(rows, accounts, maskAccountNames),
            known = rows.any { it.known },
            tightestWindow = tightest?.first,
            ratio = tightest?.second,
            windows = windows
        )
    }
}

/** The first window per id wins, unless it reported nothing and a later duplicate did. */
private fun mergeQuotaWindows(windows: List<QuotaWindow>): List<QuotaWindow> {
    val byId = LinkedHashMap<String, QuotaWindow>()
    windows.forEach { window ->
        val existing = byId[window.id]
        if (existing == null || (!existing.hasReportedValue() && window.hasReportedValue())) {
            byId[window.id] = window
        }
    }
    return byId.values.toList()
}

private fun QuotaWindow.hasReportedValue(): Boolean =
    unlimited || used != null || usedPercent != null || amount != null || display != null

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
