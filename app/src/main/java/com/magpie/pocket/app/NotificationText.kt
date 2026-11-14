package com.magpie.pocket.app

import com.magpie.pocket.core.model.AccountQuota
import com.magpie.pocket.core.model.RequestSummary
import com.magpie.pocket.core.util.MetricCalculators
import com.magpie.pocket.core.util.RequestTimestamps
import java.util.Locale
import kotlin.math.roundToInt

/** One in-flight request reduced to the two fields the keepalive notification prints. */
internal data class RunningLine(
    val model: String,
    val elapsedMs: Long?
) {
    fun printed(): String = NotificationText.formatElapsed(elapsedMs) + " · " + model
}

/** The collapsed text and expanded inbox lines of one keepalive notification. */
data class KeepaliveNotificationText(
    val title: String,
    val text: String?,
    val inboxLines: List<String>
)

/** One quota window that may cross the alert threshold; [windowKey] keeps accounts apart. */
data class QuotaReading(
    val providerId: String,
    val providerLabel: String,
    val windowLabel: String,
    val windowKey: String,
    val ratio: Double
)

/** The title, collapsed body and expanded lines of one quota alert notification. */
data class QuotaAlertNotificationText(
    val title: String,
    val text: String,
    val inboxLines: List<String>
)

/**
 * Text decisions for the notification surface. Everything here is pure so the JVM unit tests can
 * pin the exact copy without an Android runtime; the service only assembles the notification.
 */
object NotificationText {

    const val FALLBACK_TITLE = "Magpie Pocket"
    const val FALLBACK_TEXT = "后台保持实时连接"
    const val FAILURE_TITLE = "请求最终失败"

    private const val MAX_RUNNING_LINES = 3

    fun keepalive(
        completedRequests: Long?,
        running: List<RequestSummary>,
        nowMillis: Long,
        verbose: Boolean = true
    ): KeepaliveNotificationText {
        if (completedRequests == null) {
            return KeepaliveNotificationText(
                title = FALLBACK_TITLE,
                text = FALLBACK_TEXT,
                inboxLines = emptyList()
            )
        }
        val lines = running
            .sortedBy { RequestTimestamps.parseMillis(it.startedAt) ?: Long.MAX_VALUE }
            .map { it.toRunningLine(nowMillis) }
        val inboxLines: List<String> = if (verbose) {
            val printed = lines.take(MAX_RUNNING_LINES).map { it.printed() }
            if (lines.size > MAX_RUNNING_LINES) {
                printed + "还有 " + (lines.size - MAX_RUNNING_LINES) + " 条处理中"
            } else {
                printed
            }
        } else {
            emptyList()
        }
        return KeepaliveNotificationText(
            title = "今日 " + formatCount(completedRequests) + " 请求",
            text = if (lines.isEmpty()) null else lines.size.toString() + " 条处理中",
            inboxLines = inboxLines
        )
    }

    /**
     * Notification elapsed clock: "4:38" below an hour, "1:04:38" above it. Whole seconds keep
     * the line stable while the service ticks once a second.
     */
    fun formatElapsed(durationMs: Long?): String {
        if (durationMs == null) return "—"
        val totalSeconds = durationMs.coerceAtLeast(0L) / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%d:%02d", minutes, seconds)
        }
    }

    fun formatCount(value: Long): String = String.format(Locale.US, "%,d", value)

    fun failureText(request: RequestSummary): String {
        val attempt = request.attempts.lastOrNull()
        val httpStatus = attempt?.httpStatus
        val error = attempt?.errorCode?.takeIf { it.isNotBlank() }
        return when {
            httpStatus != null && error != null -> "HTTP " + httpStatus + " · " + error
            httpStatus != null -> "HTTP " + httpStatus
            error != null -> error
            else -> "上游未返回错误详情"
        }
    }

    /**
     * Reads every window the quota answer carries, not only the ones over the limit, so the gate
     * can also see a window fall back below its threshold. The share comes from
     * [MetricCalculators.calculateQuotaUsedRatio], which already applies the quota figure rule
     * (count pair first, then the upstream share).
     */
    fun quotaReadings(quotas: List<AccountQuota>): List<QuotaReading> = quotas.flatMap { quota ->
        val providerLabel = quota.displayName?.takeIf { it.isNotBlank() } ?: quota.providerId
        val accountKey = quota.accountId.orEmpty()
        quota.windows.mapNotNull { window ->
            val ratio = MetricCalculators.calculateQuotaUsedRatio(window) ?: return@mapNotNull null
            QuotaReading(
                providerId = quota.providerId,
                providerLabel = providerLabel,
                windowLabel = window.id.takeIf { it.isNotBlank() } ?: "额度",
                windowKey = accountKey + "|" + window.id,
                ratio = ratio
            )
        }
    }

    /** One alert for a whole crossing batch: the worst window titles it, the rest are counted. */
    fun quotaAlert(readings: List<QuotaReading>): QuotaAlertNotificationText {
        val ordered = readings.sortedWith(
            compareByDescending<QuotaReading> { it.ratio }.thenBy { it.providerId }
        )
        val worst = ordered.first()
        val others = ordered.size - 1
        return QuotaAlertNotificationText(
            title = worst.providerLabel + " 额度已用 " + formatPercent(worst.ratio) + "%",
            text = if (others > 0) {
                "另有 " + others + " 个额度窗口接近上限。路由可能会被上游拒绝。"
            } else {
                "额度接近上限，路由可能会被上游拒绝。"
            },
            inboxLines = ordered.map { reading ->
                reading.providerLabel + " · " + reading.windowLabel + " " +
                    formatPercent(reading.ratio) + "%"
            }
        )
    }

    private fun formatPercent(ratio: Double): Int = (ratio * 100.0).roundToInt()

    private fun RequestSummary.toRunningLine(nowMillis: Long): RunningLine {
        val started = RequestTimestamps.parseMillis(startedAt)
        return RunningLine(
            model = servedModel?.takeIf { it.isNotBlank() }
                ?: requestedModel?.takeIf { it.isNotBlank() }
                ?: "—",
            elapsedMs = started?.let { (nowMillis - it).coerceAtLeast(0L) }
        )
    }
}

/** The minimal key-value view of the alert memory; production backs it with SharedPreferences. */
interface QuotaAlertStore {
    fun read(key: String): String?
    fun write(key: String, value: String)
    fun remove(key: String)
    fun keys(): Set<String>
}

/**
 * Decides which quota windows crossed the 90% threshold since the last look. A crossing is
 * remembered by (providerId, windowKey, threshold tier); dropping back below the threshold
 * forgets it, and an expired mute forgets everything remembered before the mute. This keeps
 * one alert per crossing across process restarts.
 */
class QuotaAlertGate(
    private val store: QuotaAlertStore,
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) {

    fun evaluate(readings: List<QuotaReading>): List<QuotaReading> {
        val now = nowMillis()
        val muted = mutedNow(now)
        val crossed = mutableListOf<QuotaReading>()
        readings.forEach { reading ->
            val key = firedKey(reading)
            if (reading.ratio < THRESHOLD) {
                if (store.read(key) != null) store.remove(key)
            } else if (!muted && store.read(key) == null) {
                store.write(key, now.toString())
                crossed += reading
            }
        }
        return crossed
    }

    /** Suppresses alerts for [durationMillis]; the expired mute also unlocks a new reminder. */
    fun muteFor(durationMillis: Long = MUTE_DURATION_MILLIS) {
        store.write(KEY_MUTED_UNTIL, (nowMillis() + durationMillis).toString())
    }

    private fun mutedNow(now: Long): Boolean {
        val until = store.read(KEY_MUTED_UNTIL)?.toLongOrNull() ?: return false
        if (now < until) return true
        store.keys().filter { it.startsWith(FIRED_PREFIX) }.forEach { store.remove(it) }
        store.remove(KEY_MUTED_UNTIL)
        return false
    }

    private fun firedKey(reading: QuotaReading): String =
        FIRED_PREFIX + reading.providerId + "|" + reading.windowKey + "|" + THRESHOLD_TIER

    companion object {
        const val THRESHOLD = 0.9
        const val THRESHOLD_TIER = "90"
        const val MUTE_DURATION_MILLIS = 8L * 60L * 60L * 1000L
        private const val FIRED_PREFIX = "fired|"
        private const val KEY_MUTED_UNTIL = "muted_until"
    }
}
