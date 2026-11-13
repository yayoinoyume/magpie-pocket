package com.magpie.pocket.app

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.magpie.pocket.core.design.DarkScheme
import com.magpie.pocket.core.design.LightScheme
import com.magpie.pocket.core.design.MagpieColorScheme
import com.magpie.pocket.core.model.DataMode
import com.magpie.pocket.core.model.FinalState
import com.magpie.pocket.core.model.OverviewSnapshot
import com.magpie.pocket.core.model.RequestSummary
import com.magpie.pocket.core.model.ThemeMode
import com.magpie.pocket.core.security.PreferencesManager
import com.magpie.pocket.widget.WidgetTheme
import com.magpie.pocket.widget.WidgetThemeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps the already-authenticated process-owned LIVE session alive while the user has
 * enabled background keepalive. It owns no session or network state of its own: the
 * application ViewModel remains the single runtime owner.
 *
 * The foreground notification is a read-only view of that state: it shows today's request
 * count and the oldest in-flight request, and grows into an inbox of up to three running
 * calls. Quota and final-failure notifications are driven by the same ViewModel flows; the
 * pure decisions live in [NotificationText] and [QuotaAlertGate].
 */
class LiveKeepaliveService : Service() {

    private var foregroundStarted = false
    private var notifiedActive = false
    private var model: AppViewModel? = null
    private var stateJob: Job? = null
    private var summaryJob: Job? = null
    private var quotaJob: Job? = null
    private var failureJob: Job? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var notificationPreferences: PreferencesManager
    private lateinit var quotaAlertGate: QuotaAlertGate
    private val seenFailures = LinkedHashSet<String>()
    private var failureBaselineReady = false

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        notificationPreferences = (application as MagpieApplication).preferencesManager
        quotaAlertGate = QuotaAlertGate(SharedPreferencesQuotaAlertStore(this))

        // Satisfy the startForegroundService() contract before reading model or
        // preferences. Android enforces this even when the service is about to
        // stop itself because keepalive is disabled or the connection is Demo.
        if (!startAsForeground()) {
            stopSelf()
            return
        }

        val model = appViewModel()
        if (model == null) {
            stopSelf()
            return
        }
        this.model = model

        if (definitivelyDisallowed(model)) {
            stopSelf()
            return
        }

        // The process may have been recreated by the OS from a sticky service
        // before DataStore emitted. Mark the service present; AppViewModel still
        // gates all background networking on preference readiness.
        notifyActive(model)
        observePreferenceState(model)
        observeKeepaliveSummary(model)
        observeQuotaAlerts(model)
        observeFinalFailures(model)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        handleNotificationAction(intent)?.let { return it }

        if (!foregroundStarted) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        val model = model ?: appViewModel()
        if (model == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        this.model = model

        if (!model.displayPreferencesReady.value) {
            if (definitivelyDisallowed(model)) {
                stopSelf(startId)
                return START_NOT_STICKY
            }
            // Stay promoted and wait for the shared model's definitive preference.
            return START_STICKY
        }
        if (!serviceAllowed(model)) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        notifyActive(model)
        return START_STICKY
    }

    override fun onDestroy() {
        stateJob?.cancel()
        stateJob = null
        summaryJob?.cancel()
        summaryJob = null
        quotaJob?.cancel()
        quotaJob = null
        failureJob?.cancel()
        failureJob = null
        serviceScope.cancel()
        // Clear the controller's pending-start/running ownership even when
        // promotion failed before the ViewModel was notified. Otherwise the next
        // visible lifecycle would believe a start is still pending and skip retry.
        model?.onKeepaliveServiceActive(false)
        notifiedActive = false
        if (foregroundStarted) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            foregroundStarted = false
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun appViewModel(): AppViewModel? =
        (application as? MagpieApplication)?.appViewModel

    /** The stop and mute buttons are service actions; returning null means a normal start. */
    private fun handleNotificationAction(intent: Intent?): Int? = when (intent?.action) {
        Notifications.ACTION_STOP -> {
            // The existing stop path is the preference: turning background keepalive off
            // keeps it off on the next Activity transition instead of restarting the service.
            (model ?: appViewModel())?.updateKeepLiveConnectionInBackground(false)
            stopSelf()
            START_NOT_STICKY
        }
        Notifications.ACTION_MUTE_ALERTS -> if (!foregroundStarted) {
            null
        } else {
            quotaAlertGate.muteFor()
            getSystemService(NotificationManager::class.java)
                .cancel(Notifications.NOTIFICATION_ID_ALERT)
            START_STICKY
        }
        else -> null
    }

    private fun observePreferenceState(model: AppViewModel) {
        stateJob?.cancel()
        stateJob = serviceScope.launch {
            combine(
                model.connectionConfig,
                model.displayPreferences,
                model.displayPreferencesReady
            ) { config, preferences, ready -> Triple(config, preferences, ready) }
                .collect { (config, preferences, ready) ->
                    if (!ready) return@collect
                    val allowed = config.mode == DataMode.LIVE &&
                        preferences.keepLiveConnectionInBackground
                    if (allowed) {
                        notifyActive(model)
                    } else {
                        stopSelf()
                    }
                }
        }
    }

    /**
     * One second of throttle keeps trace ticks from rewriting the notification on every frame;
     * conflate keeps only the newest state while the collector waits. The ticker is the only
     * source of repeated emissions: without it an unchanged running list would keep printing
     * the elapsed time computed at the last trace update. Ticks stop as soon as the list drains.
     */
    private fun observeKeepaliveSummary(model: AppViewModel) {
        summaryJob?.cancel()
        summaryJob = serviceScope.launch {
            // Theme inputs are part of the summary so an idle notification recolors when the
            // app ThemeMode or the system night mode changes, without waking every second.
            val settings = combine(
                notificationPreferences.notifVerboseFlow,
                model.displayPreferences.map { it.themeMode }.distinctUntilChanged(),
                WidgetThemeSource.systemDarkFlow
            ) { verbose, themeMode, systemDark ->
                KeepaliveSettings(verbose, themeMode, systemDark)
            }
            combine(
                model.overview,
                model.runningRequests,
                model.connectionStatus,
                settings,
                keepaliveTicks(model.runningRequests, KEEPALIVE_TICK_MS)
            ) { overview, running, _, settings, revision ->
                KeepaliveState(
                    overview = overview,
                    running = running,
                    verbose = settings.verbose,
                    revision = revision,
                    themeMode = settings.themeMode,
                    systemDark = settings.systemDark
                )
            }
                .distinctUntilChanged()
                .conflate()
                .collect { state ->
                    delay(KEEPALIVE_UPDATE_THROTTLE_MS)
                    showKeepalive(state)
                }
        }
    }

    private fun observeQuotaAlerts(model: AppViewModel) {
        quotaJob?.cancel()
        quotaJob = serviceScope.launch {
            model.quotas.collect { quotas ->
                val readings = NotificationText.quotaReadings(quotas)
                val crossed = quotaAlertGate.evaluate(readings)
                if (crossed.isNotEmpty()) {
                    // The batch opens one notification; its body counts every window still
                    // near the limit, including windows this batch did not re-alert.
                    showQuotaAlert(readings.filter { it.ratio >= QuotaAlertGate.THRESHOLD })
                }
            }
        }
    }

    /**
     * A trace epoch restart replays old routes; they establish a new baseline instead of
     * alerting. A rerouted success is SUCCEEDED, so only the final FAILED state notifies.
     */
    private fun observeFinalFailures(model: AppViewModel) {
        failureJob?.cancel()
        failureJob = serviceScope.launch {
            model.trace.collect { trace ->
                if (trace == null) return@collect
                val failed = trace.recentRoutes.filter { it.finalState == FinalState.FAILED }
                if (!failureBaselineReady || trace.restarted) {
                    failureBaselineReady = true
                    seenFailures.clear()
                    seenFailures.addAll(failed.map { it.key.routeId })
                    return@collect
                }
                failed
                    .filter { seenFailures.add(it.key.routeId) }
                    .forEach { showFailure(it) }
                trimSeenFailures()
            }
        }
    }

    private fun trimSeenFailures() {
        if (seenFailures.size <= MAX_SEEN_FAILURES) return
        val retained = seenFailures.toList().takeLast(MAX_SEEN_FAILURES)
        seenFailures.clear()
        seenFailures.addAll(retained)
    }

    private fun notifyActive(model: AppViewModel) {
        if (notifiedActive) return
        notifiedActive = true
        model.onKeepaliveServiceActive(true)
    }

    private fun serviceAllowed(model: AppViewModel): Boolean {
        val config = model.connectionConfig.value
        return config.mode == DataMode.LIVE &&
            model.displayPreferences.value.keepLiveConnectionInBackground
    }

    private fun definitivelyDisallowed(model: AppViewModel): Boolean {
        if (model.connectionConfig.value.mode != DataMode.LIVE) return true
        return model.displayPreferencesReady.value && !serviceAllowed(model)
    }

    private fun showKeepalive(state: KeepaliveState) {
        val text = NotificationText.keepalive(
            completedRequests = state.overview?.completedRequests,
            running = state.running,
            nowMillis = System.currentTimeMillis(),
            verbose = state.verbose
        )
        getSystemService(NotificationManager::class.java)
            .notify(
                Notifications.NOTIFICATION_ID_KEEPALIVE,
                keepaliveNotification(text, state.themeMode, state.systemDark)
            )
    }

    private fun showQuotaAlert(readings: List<QuotaReading>) {
        val text = NotificationText.quotaAlert(readings)
        val style = NotificationCompat.InboxStyle()
        text.inboxLines.forEach(style::addLine)
        style.setSummaryText(readings.size.toString() + " 个额度窗口接近上限")
        val notification = NotificationCompat.Builder(this, Notifications.CHANNEL_ALERT)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setColor(notificationScheme().warning.toArgb())
            .setContentTitle(text.title)
            .setContentText(text.text)
            .setStyle(style)
            .setContentIntent(openUsageIntent())
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "查看额度", openUsageIntent())
            .addAction(0, "静音 8 小时", muteAlertsIntent())
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(Notifications.NOTIFICATION_ID_ALERT, notification)
    }

    private fun showFailure(request: RequestSummary) {
        val openRequest = openRequestIntent(request.key.routeId)
        val notification = NotificationCompat.Builder(this, Notifications.CHANNEL_FAILURE)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setColor(notificationScheme().error.toArgb())
            .setContentTitle(NotificationText.FAILURE_TITLE)
            .setContentText(NotificationText.failureText(request))
            .setContentIntent(openRequest)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .addAction(0, "查看这次", openRequest)
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(Notifications.NOTIFICATION_ID_FAILURE, notification)
    }

    private fun startAsForeground(): Boolean {
        if (foregroundStarted) return true
        val notification = keepaliveNotification(
            NotificationText.keepalive(
                completedRequests = null,
                running = emptyList(),
                nowMillis = System.currentTimeMillis()
            )
        )

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(
                this,
                Notifications.NOTIFICATION_ID_KEEPALIVE,
                notification,
                type
            )
            foregroundStarted = true
            return true
        } catch (_: RuntimeException) {
            // Android can reject a foreground promotion (for example after a
            // permission/notification restriction). Stop cleanly; the next visible
            // Activity transition may retry without crashing.
            return false
        }
    }

    private fun keepaliveNotification(
        text: KeepaliveNotificationText,
        themeMode: ThemeMode? = null,
        systemDark: Boolean? = null
    ): Notification {
        val openApp = openAppIntent()
        val builder = NotificationCompat.Builder(this, Notifications.CHANNEL_KEEPALIVE)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setColor(notificationScheme(themeMode, systemDark).accent.toArgb())
            .setContentTitle(text.title)
            .setContentText(text.text)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, "打开 Magpie", openApp)
            .addAction(0, "停止后台", stopServiceIntent())
        if (text.inboxLines.isNotEmpty()) {
            val style = NotificationCompat.InboxStyle()
            text.inboxLines.forEach(style::addLine)
            text.footer?.let(style::setSummaryText)
            builder.setStyle(style)
        }
        return builder.build()
    }

    /**
     * The notification accent comes from the same app tokens as the widgets. A cold service
     * start may briefly read the cached SYSTEM default until DataStore emits; the next
     * keepalive tick repaints it.
     */
    private fun notificationScheme(
        themeMode: ThemeMode? = null,
        systemDark: Boolean? = null
    ): MagpieColorScheme {
        val mode = themeMode
            ?: (application as? MagpieApplication)?.appThemeMode
            ?: ThemeMode.SYSTEM
        val dark = systemDark ?: WidgetThemeSource.systemDark(this)
        val isDark = WidgetThemeSource.isDark(
            theme = WidgetTheme.SYSTEM,
            appThemeMode = mode,
            systemDark = dark
        )
        return if (isDark) DarkScheme else LightScheme
    }

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            REQUEST_OPEN_APP,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun openUsageIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            REQUEST_OPEN_USAGE,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(Notifications.EXTRA_USAGE_SECTION, Notifications.USAGE_SECTION_QUOTA)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun openRequestIntent(routeId: String): PendingIntent =
        PendingIntent.getActivity(
            this,
            REQUEST_OPEN_REQUEST,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(Notifications.EXTRA_ROUTE_ID, routeId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun stopServiceIntent(): PendingIntent =
        PendingIntent.getService(
            this,
            REQUEST_STOP,
            Intent(this, LiveKeepaliveService::class.java).setAction(Notifications.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun muteAlertsIntent(): PendingIntent =
        PendingIntent.getService(
            this,
            REQUEST_MUTE,
            Intent(this, LiveKeepaliveService::class.java)
                .setAction(Notifications.ACTION_MUTE_ALERTS),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private data class KeepaliveSettings(
        val verbose: Boolean,
        val themeMode: ThemeMode,
        val systemDark: Boolean
    )

    private data class KeepaliveState(
        val overview: OverviewSnapshot?,
        val running: List<RequestSummary>,
        val verbose: Boolean,
        /** Makes each periodic redraw distinct while the payload itself is unchanged. */
        val revision: Long,
        val themeMode: ThemeMode,
        val systemDark: Boolean
    )

    companion object {
        private const val KEEPALIVE_UPDATE_THROTTLE_MS = 1_000L
        private const val KEEPALIVE_TICK_MS = 1_000L
        private const val MAX_SEEN_FAILURES = 200
        private const val REQUEST_OPEN_APP = 0
        private const val REQUEST_STOP = 1
        private const val REQUEST_OPEN_USAGE = 2
        private const val REQUEST_OPEN_REQUEST = 3
        private const val REQUEST_MUTE = 4
    }
}

/**
 * Emits a redraw revision once for every running-list change and then, while the list is
 * non-empty, once per [periodMs]. The flush value still fires when the list drains so the
 * notification drops its last running line, then the ticker goes quiet: an idle keepalive
 * notification never wakes once a second.
 */
internal fun keepaliveTicks(
    running: Flow<List<RequestSummary>>,
    periodMs: Long
): Flow<Long> = channelFlow {
    var revision = 0L
    running.collectLatest { active ->
        send(revision++)
        if (active.isEmpty()) return@collectLatest
        while (true) {
            delay(periodMs)
            send(revision++)
        }
    }
}
