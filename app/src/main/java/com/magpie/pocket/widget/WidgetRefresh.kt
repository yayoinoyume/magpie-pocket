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
 * to [widgets]; the app's refresh hook and the periodic fallback stay unchanged.
 */
object WidgetRefresh {

    private val widgets: List<GlanceAppWidget> = listOf(WidgetMini())

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
            repaint(appContext)
        }
    }

    /** The WorkManager fallback: repaint from the app's live state or the last snapshot. */
    suspend fun refreshFromSnapshot(context: Context) {
        WidgetStateStore.live(context)?.let { WidgetStateStore.save(context, it) }
        repaint(context)
    }

    /** Idempotent: the unique periodic work is kept across app starts. */
    fun schedulePeriodic(context: Context) {
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
                // A repaint is best-effort; the next refresh or the periodic fallback retries.
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
