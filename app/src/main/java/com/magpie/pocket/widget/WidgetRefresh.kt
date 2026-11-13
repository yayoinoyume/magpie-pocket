package com.magpie.pocket.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.updateAll
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * One refresh path for every home-screen size. A new size only appends its GlanceAppWidget
 * to [widgets]; the app's refresh hook and the stored refresh policy stay shared.
 */
object WidgetRefresh {

    private val widgets: List<GlanceAppWidget> = listOf(
        WidgetMini(),
        WidgetOverview(),
        WidgetQuota(),
        WidgetLive(),
        WidgetQuotaAlert()
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Persists the app's current state and repaints every widget. Called after a successful
     * overview refresh; it returns immediately so one screen never waits on RemoteViews.
     */
    fun updateAll(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            WidgetStateStore.live(appContext)?.let { state ->
                runCatching { WidgetStateStore.save(appContext, state) }
            }
            WidgetQuotaStateStore.live(appContext)?.let { state ->
                runCatching { WidgetQuotaStateStore.save(appContext, state) }
            }
            repaint(appContext)
        }
    }

    /** Repaints every widget without writing a new snapshot; used for theme changes. */
    fun repaintAll(context: Context) {
        val appContext = context.applicationContext
        scope.launch { repaint(appContext) }
    }

    /** The WorkManager fallback: repaint from the app's live state or the last snapshot. */
    suspend fun refreshFromSnapshot(context: Context) {
        WidgetStateStore.live(context)?.let { WidgetStateStore.save(context, it) }
        WidgetQuotaStateStore.live(context)?.let { WidgetQuotaStateStore.save(context, it) }
        repaint(context)
    }

    /**
     * Applies the stored refresh strategy. Event-driven keeps only the app's own refresh
     * signal; the 15-minute worker is enqueued when the user chooses it. Idempotent across
     * app starts and config saves.
     */
    fun applyRefreshPolicy(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            val config = WidgetConfigStore.load(appContext)
            if (config.refresh == WidgetRefreshMode.EVERY_15_MINUTES) {
                enqueuePeriodic(appContext)
            } else {
                WorkManager.getInstance(appContext)
                    .cancelUniqueWork(WidgetRefreshWorker.UNIQUE_WORK_NAME)
            }
        }
    }

    private fun enqueuePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<WidgetRefreshWorker>(
            PERIOD_MINUTES,
            TimeUnit.MINUTES
        )
            .setConstraints(Constraints.NONE)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WidgetRefreshWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private suspend fun repaint(context: Context) {
        // One failing size must not leave the other sizes stale.
        for (widget in widgets) {
            try {
                widget.updateAll(context)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A repaint is best-effort; the next refresh retries.
            }
        }
    }

    private const val PERIOD_MINUTES = 15L
}

/** A silent fallback; a failed repaint must not surface a retry or a notification. */
class WidgetRefreshWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        WidgetRefresh.refreshFromSnapshot(applicationContext)
        Result.success()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        Result.success()
    }

    companion object {
        const val UNIQUE_WORK_NAME = "magpie-widget-refresh"
    }
}
