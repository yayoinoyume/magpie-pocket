package com.magpie.pocket.app

import com.magpie.pocket.core.model.Account
import com.magpie.pocket.core.model.AccountQuota
import com.magpie.pocket.core.model.QuotaDisplayMode
import com.magpie.pocket.core.model.QuotaWindow
import com.magpie.pocket.core.util.MetricCalculators
import com.magpie.pocket.core.util.RequestTimestamps
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** One window row in a provider card, already read in the page's chosen direction. */
data class QuotaWindowRow(
    /** Stable identity for the expanded-state set: provider, window name and occurrence. */
    val key: String,
    val name: String,
    /** "0 / 500 credits"; null for a window that reports a share only. */
    val countText: String?,
    /** "0.0% 剩余" or "100.0% 已用"; null when the window has no reading. */
    val shareText: String?,
    /** The printed share, 0..100, in the page's display mode; null when there is no bar. */
    val percent: Double?,
    /** 0 danger, 1 warning, 2 normal. */
    val color: Int,
    /** The upstream's own display text for a text-only window. */
    val text: String?,
    val unlimited: Boolean,
    val hasBar: Boolean,
    /** "11月16日 · 5 天后"; null when the provider named no reset. */
    val resetText: String?,
    /** "比均速快 12%"; null without both window ends or a reading. */
    val paceText: String?
)

/** One account inside a provider card; its windows are already numbered for repeated names. */
data class QuotaAccountView(
    val accountId: String?,
    val label: String,
    val plan: String?,
    val lastServedAt: String?,
    val windows: List<QuotaWindowRow>
)

/**
 * One provider card. [mergedWindows] only holds a window that at least two accounts report, so a
 * single account's window is never shown twice. [tightestPercent] is the printed share of the
 * provider's tightest window, in the page's display mode.
 */
data class QuotaProviderCard(
    val providerId: String,
    val displayName: String,
    val plan: String?,
    /** A provider-level balance, e.g. "¥3.57"; a card that has one prints it as its headline. */
    val balance: String?,
    val accounts: List<QuotaAccountView>,
    val mergedWindows: List<QuotaWindowRow>,
    val tightestPercent: Double?,
    val windowCount: Int,
    val accountCount: Int
)

/** The page-wide totals: remaining is always the remaining share, whatever the page prints. */
data class QuotaOverview(
    val averageRemaining: Double?,
    val windowCount: Int,
    val lowCount: Int,
    val lowest: Pair<String, Double>?,
    val highest: Pair<String, Double>?
)

/**
 * Builds the quota page's provider cards from the upstream answers.
 *
 * A provider is identified by its id, an account by the account the answer named. Rows for the
 * same account merge their windows: the upstream can answer a provider twice (a window row plus
 * a balance row), and both belong to the same card. A provider with neither a window nor a
 * balance has nothing to show and is dropped.
 *
 * The provider and account names come from the answer; only an account the upstream actually
 * named is masked. [nowMillis] is injectable so reset distance and pace have one clock in tests.
 */
internal fun quotaProviderCards(
    quotas: List<AccountQuota>,
    accounts: List<Account>,
    maskAccountNames: Boolean,
    displayMode: QuotaDisplayMode,
    nowMillis: Long = System.currentTimeMillis()
): List<QuotaProviderCard> {
    val rowsByProvider = LinkedHashMap<String, MutableList<AccountQuota>>()
    quotas.forEach { quota ->
        rowsByProvider.getOrPut(quota.providerId) { mutableListOf() }.add(quota)
    }

    val built = rowsByProvider.mapNotNull { (providerId, rows) ->
        buildProviderCard(providerId, rows, accounts, maskAccountNames, displayMode, nowMillis)
    }
    return built
        .sortedWith(
            // Worst first: the card closest to its limit must stay visible, whichever side the
            // page prints. Ties break on the provider id so masking can never reorder the page.
            compareByDescending<BuiltCard> { it.tightestUsed ?: Double.NEGATIVE_INFINITY }
                .thenBy { it.card.providerId }
        )
        .map { it.card }
}

private data class BuiltCard(
    val card: QuotaProviderCard,
    val tightestUsed: Double?
)

private class QuotaAccountAccumulator(
    val accountId: String?,
    val label: String,
    var plan: String?,
    var lastServedAt: String?
) {
    private val nameCounts = LinkedHashMap<String, Int>()
    val slots = mutableListOf<QuotaWindowSlot>()

    fun addWindows(windows: List<QuotaWindow>) {
        windows.forEach { window ->
            val baseName = window.id.takeIf { it.isNotBlank() } ?: "额度"
            val occurrence = (nameCounts[baseName] ?: 0) + 1
            nameCounts[baseName] = occurrence
            val name = if (occurrence == 1) baseName else baseName + " #" + occurrence
            slots += QuotaWindowSlot(baseName, name, occurrence, window)
        }
    }
}

private data class QuotaWindowSlot(
    val baseName: String,
    val name: String,
    val occurrence: Int,
    val window: QuotaWindow
)

private fun buildProviderCard(
    providerId: String,
    rows: List<AccountQuota>,
    accounts: List<Account>,
    maskAccountNames: Boolean,
    displayMode: QuotaDisplayMode,
    nowMillis: Long
): BuiltCard? {
    val displayName = rows.firstNotNullOfOrNull { row ->
        row.displayName?.takeIf { it.isNotBlank() }
    } ?: providerId
    val providerPlan = rows.firstNotNullOfOrNull { row ->
        row.plan?.takeIf { it.isNotBlank() }
    }
    val balance = rows.firstNotNullOfOrNull { row ->
        row.balance?.takeIf { it.isNotBlank() }
    }

    val byAccount = LinkedHashMap<String, QuotaAccountAccumulator>()
    rows.forEach { quota ->
        val accountId = quota.accountId?.takeIf { it.isNotBlank() }
        val key = accountId ?: ""
        val accumulator = byAccount.getOrPut(key) {
            QuotaAccountAccumulator(
                accountId = accountId,
                label = quotaAccountLabel(accountId, displayName, accounts, maskAccountNames),
                plan = quota.plan?.takeIf { it.isNotBlank() } ?: providerPlan,
                lastServedAt = quota.lastServedAt?.takeIf { it.isNotBlank() }
            )
        }
        if (accumulator.plan == null) {
            accumulator.plan = quota.plan?.takeIf { it.isNotBlank() } ?: providerPlan
        }
        accumulator.lastServedAt = laterTimestamp(
            accumulator.lastServedAt,
            quota.lastServedAt?.takeIf { it.isNotBlank() }
        )
        accumulator.addWindows(quota.windows)
    }

    val accountViews = byAccount.values.map { accumulator ->
        QuotaAccountView(
            accountId = accumulator.accountId,
            label = accumulator.label,
            plan = accumulator.plan,
            lastServedAt = accumulator.lastServedAt,
            windows = accumulator.slots.map { slot ->
                quotaWindowRow(providerId, slot, displayMode, nowMillis)
            }
        )
    }.filter { it.windows.isNotEmpty() }

    val allWindows = accountViews.flatMap { it.windows }
    if (allWindows.isEmpty() && balance == null) return null

    val tightestWindow = allWindows
        .filter { it.percent != null }
        .minByOrNull { remainingPercent(it, displayMode) }
    val tightestUsed = allWindows
        .mapNotNull { usedPercent(it, displayMode) }
        .maxOrNull()

    return BuiltCard(
        card = QuotaProviderCard(
            providerId = providerId,
            displayName = displayName,
            plan = providerPlan,
            balance = balance,
            accounts = accountViews,
            mergedWindows = mergedQuotaWindows(byAccount.values, providerId, displayMode, nowMillis),
            tightestPercent = tightestWindow?.percent,
            windowCount = allWindows.size,
            accountCount = accountViews.size
        ),
        tightestUsed = tightestUsed
    )
}

private fun quotaAccountLabel(
    accountId: String?,
    providerName: String,
    accounts: List<Account>,
    maskAccountNames: Boolean
): String {
    if (accountId == null) return providerName
    val alias = accounts.firstOrNull { it.id == accountId }?.alias
    return MetricCalculators.maskAccountName(alias ?: accountId, maskAccountNames)
}

/** The later of two ISO instants; an unparsable value still counts as present when alone. */
private fun laterTimestamp(current: String?, candidate: String?): String? {
    if (candidate == null) return current
    if (current == null) return candidate
    val currentMillis = RequestTimestamps.parseMillis(current)
    val candidateMillis = RequestTimestamps.parseMillis(candidate)
    return when {
        currentMillis == null -> candidate
        candidateMillis == null -> current
        candidateMillis >= currentMillis -> candidate
        else -> current
    }
}

/**
 * Windows shared by at least two accounts, grouped by their upstream name. A count window adds
 * its amount and allowance; a share-only window is averaged only across accounts with the same
 * plan. Text and unlimited windows never enter a total, and a name reported by one account is
 * left to that account's own row.
 */
private fun mergedQuotaWindows(
    accounts: Collection<QuotaAccountAccumulator>,
    providerId: String,
    displayMode: QuotaDisplayMode,
    nowMillis: Long
): List<QuotaWindowRow> {
    val byName = LinkedHashMap<String, MutableList<Pair<QuotaAccountAccumulator, QuotaWindowSlot>>>()
    accounts.forEach { account ->
        account.slots.forEach { slot ->
            byName.getOrPut(slot.baseName) { mutableListOf() }.add(account to slot)
        }
    }

    val merged = mutableListOf<QuotaWindowRow>()
    byName.forEach { (name, entries) ->
        if (entries.map { it.first }.distinct().size < 2) return@forEach
        val windows = entries.map { it.second.window }
        if (windows.any { it.unlimited || it.text }) return@forEach

        if (windows.all { it.allowanceLimit != null }) {
            val amounts = windows.map { window ->
                countAmount(window) ?: return@forEach
            }
            val limits = windows.map { it.allowanceLimit!! }
            val totalAmount = amounts.sum()
            val totalLimit = limits.sum()
            if (totalLimit <= 0.0) return@forEach
            val used = 100.0 * totalAmount / totalLimit
            val unit = windows.firstNotNullOfOrNull { window ->
                window.unit.takeIf { it.isNotBlank() }
            }
            merged += quotaWindowRow(
                key = providerId + "\n" + name + "#1",
                name = name,
                used = used,
                countText = countText(totalAmount, totalLimit, unit),
                text = null,
                unlimited = false,
                resetsAt = null,
                startsAt = null,
                displayMode = displayMode,
                nowMillis = nowMillis
            )
        } else if (windows.all { it.allowanceLimit == null && it.usedPercent != null }) {
            val plans = entries.map { it.first.plan }.distinct()
            if (plans.size != 1) return@forEach
            val used = windows.map { it.usedPercent!! }.average()
            merged += quotaWindowRow(
                key = providerId + "\n" + name + "#1",
                name = name,
                used = used,
                countText = null,
                text = null,
                unlimited = false,
                resetsAt = null,
                startsAt = null,
                displayMode = displayMode,
                nowMillis = nowMillis
            )
        }
    }
    return merged
}

/** A count window's used amount, recovering it from a reported share when the answer omitted it. */
private fun countAmount(window: QuotaWindow): Double? {
    window.amount?.let { return it }
    val limit = window.allowanceLimit ?: return null
    val used = window.usedPercent ?: return null
    return used / 100.0 * limit
}

private fun quotaWindowRow(
    providerId: String,
    slot: QuotaWindowSlot,
    displayMode: QuotaDisplayMode,
    nowMillis: Long
): QuotaWindowRow {
    val window = slot.window
    val ratio = MetricCalculators.calculateQuotaUsedRatio(window)
    val used = ratio?.times(100.0)
    val limit = window.allowanceLimit
    val usedAmount = if (limit != null) countAmount(window) else null
    val count = if (usedAmount != null && limit != null) {
        countText(usedAmount, limit, window.unit.takeIf { it.isNotBlank() })
    } else {
        null
    }
    val text = when {
        window.unlimited -> null
        window.text -> window.display?.takeIf { it.isNotBlank() }
        ratio == null -> window.display?.takeIf { it.isNotBlank() }
        else -> null
    }
    return quotaWindowRow(
        key = providerId + "\n" + slot.name + "#" + slot.occurrence,
        name = slot.name,
        used = used,
        countText = count,
        text = text,
        unlimited = window.unlimited,
        resetsAt = window.resetsAt,
        startsAt = window.startsAt,
        displayMode = displayMode,
        nowMillis = nowMillis
    )
}

private fun quotaWindowRow(
    key: String,
    name: String,
    used: Double?,
    countText: String?,
    text: String?,
    unlimited: Boolean,
    resetsAt: String?,
    startsAt: String?,
    displayMode: QuotaDisplayMode,
    nowMillis: Long
): QuotaWindowRow {
    val hasBar = used != null && !unlimited && text == null
    return QuotaWindowRow(
        key = key,
        name = name,
        countText = countText,
        shareText = if (hasBar) shareText(used!!, displayMode) else null,
        percent = if (hasBar) displayPercent(used!!, displayMode) else null,
        color = quotaColor(used),
        text = text,
        unlimited = unlimited,
        hasBar = hasBar,
        resetText = formatQuotaReset(resetsAt, nowMillis),
        paceText = if (hasBar) quotaPaceText(used!!, startsAt, resetsAt, nowMillis) else null
    )
}

/** The printed share of a window, 0..100, as the page reads it. */
private fun displayPercent(used: Double, displayMode: QuotaDisplayMode): Double =
    when (displayMode) {
        QuotaDisplayMode.USED -> used
        QuotaDisplayMode.REMAINING -> 100.0 - used
    }.coerceIn(0.0, 100.0)

private fun shareText(used: Double, displayMode: QuotaDisplayMode): String =
    when (displayMode) {
        QuotaDisplayMode.USED -> formatQuotaPercent(used.coerceAtMost(100.0)) + " 已用"
        QuotaDisplayMode.REMAINING ->
            formatQuotaPercent((100.0 - used).coerceAtLeast(0.0)) + " 剩余"
    }

private fun usedPercent(row: QuotaWindowRow, displayMode: QuotaDisplayMode): Double? =
    row.percent?.let { percent ->
        when (displayMode) {
            QuotaDisplayMode.USED -> percent
            QuotaDisplayMode.REMAINING -> 100.0 - percent
        }
    }

private fun remainingPercent(row: QuotaWindowRow, displayMode: QuotaDisplayMode): Double =
    usedPercent(row, displayMode)?.let { 100.0 - it } ?: 0.0

/** Remaining below 30 is danger, below 60 warning, otherwise normal. */
private fun quotaColor(used: Double?): Int {
    if (used == null) return 2
    val remaining = 100.0 - used
    return when {
        remaining < 30.0 -> 0
        remaining < 60.0 -> 1
        else -> 2
    }
}

internal fun formatQuotaPercent(value: Double?): String {
    if (value == null) return "—"
    val rounded = value.roundToInt()
    return if (abs(value - rounded) < 0.05) {
        rounded.toString() + "%"
    } else {
        String.format(Locale.US, "%.1f%%", value)
    }
}

private fun countText(amount: Double, limit: Double, unit: String?): String {
    val unitSuffix = unit?.let { " " + it }.orEmpty()
    return formatQuotaCount(amount) + " / " + formatQuotaCount(limit) + unitSuffix
}

/** Counts keep a decimal while small and turn compact once they are token-sized. */
private fun formatQuotaCount(value: Double): String {
    if (value >= 1_000_000.0) {
        return MetricCalculators.formatCompactNumber(value.toLong())
    }
    val rounded = value.roundToLong()
    return if (abs(value - rounded.toDouble()) < 0.05) {
        String.format(Locale.US, "%,d", rounded)
    } else {
        String.format(Locale.US, "%,.1f", value)
    }
}

/** "11月16日 · 5 天后"; a reset already behind us keeps its date, and raw text stays raw. */
private fun formatQuotaReset(raw: String?, nowMillis: Long): String? {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return null
    val millis = RequestTimestamps.parseMillis(value) ?: return value
    val date = SimpleDateFormat("M月d日", Locale.getDefault())
        .apply { timeZone = TimeZone.getDefault() }
        .format(Date(millis))
    val remaining = millis - nowMillis
    if (remaining <= 0L) return date
    val distance = if (remaining < DAY_MILLIS) {
        ceilDiv(remaining, HOUR_MILLIS).toString() + " 小时后"
    } else {
        ceilDiv(remaining, DAY_MILLIS).toString() + " 天后"
    }
    return date + " · " + distance
}

private fun ceilDiv(value: Long, divisor: Long): Long = (value + divisor - 1L) / divisor

/**
 * Pace compares the used share with the share the window would have spent at a uniform rate:
 * positive means ahead of that rate ("比均速快"), negative behind it ("比均速慢").
 */
private fun quotaPaceText(
    used: Double,
    startsAt: String?,
    resetsAt: String?,
    nowMillis: Long
): String? {
    val start = RequestTimestamps.parseMillis(startsAt) ?: return null
    val reset = RequestTimestamps.parseMillis(resetsAt) ?: return null
    val total = reset - start
    if (total <= 0L) return null
    val expected = 100.0 * (nowMillis - start) / total
    val pace = used - expected
    val percent = abs(pace).roundToInt()
    if (percent == 0) return "与均速持平"
    return if (pace > 0.0) {
        "比均速快 " + percent + "%"
    } else {
        "比均速慢 " + percent + "%"
    }
}

private const val HOUR_MILLIS = 3_600_000L
private const val DAY_MILLIS = 86_400_000L

/** The account key used by the expanded-state set: provider id plus the account identity. */
internal fun quotaAccountKey(
    providerId: String,
    account: QuotaAccountView,
    index: Int
): String = providerId + "\n" + (account.accountId ?: "account-" + index)

/** The account with the newest lastServedAt opens by default; a tie keeps the first one. */
internal fun defaultQuotaAccountKey(card: QuotaProviderCard): String? {
    if (card.accounts.isEmpty()) return null
    var bestIndex = 0
    var bestMillis = Long.MIN_VALUE
    card.accounts.forEachIndexed { index, account ->
        val millis = RequestTimestamps.parseMillis(account.lastServedAt)
        if (millis != null && millis > bestMillis) {
            bestMillis = millis
            bestIndex = index
        }
    }
    return quotaAccountKey(card.providerId, card.accounts[bestIndex], bestIndex)
}

internal fun defaultExpandedQuotaAccounts(cards: List<QuotaProviderCard>): Set<String> =
    cards.mapNotNull { defaultQuotaAccountKey(it) }.toSet()

/** The provider's tightest window, printed in the page's chosen direction. */
internal fun QuotaProviderCard.tightestWindow(displayMode: QuotaDisplayMode): QuotaWindowRow? =
    accounts.asSequence()
        .flatMap { it.windows }
        .filter { it.percent != null }
        .minByOrNull { remainingPercent(it, displayMode) }

internal fun quotaOverview(
    cards: List<QuotaProviderCard>,
    displayMode: QuotaDisplayMode
): QuotaOverview {
    val windows = cards.flatMap { card -> card.accounts.flatMap { it.windows } }
    val remaining = windows.mapNotNull { row ->
        usedPercent(row, displayMode)?.let { 100.0 - it }
    }
    val tightest = cards.mapNotNull { card ->
        card.tightestPercent?.let { card.displayName to it }
    }
    return QuotaOverview(
        averageRemaining = remaining.takeIf { it.isNotEmpty() }?.average(),
        windowCount = windows.size,
        lowCount = remaining.count { it < 30.0 },
        lowest = tightest.minByOrNull { it.second },
        highest = tightest.maxByOrNull { it.second }
    )
}
