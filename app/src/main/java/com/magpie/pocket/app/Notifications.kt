package com.magpie.pocket.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

/**
 * Notification channels, ids and navigation extras shared by the foreground service and the
 * activity. Channels are created in one place so their names and descriptions never drift.
 */
object Notifications {

    const val CHANNEL_KEEPALIVE = "magpie_live_keepalive"
    const val CHANNEL_ALERT = "magpie_quota_alert"
    const val CHANNEL_COMPLETION = "magpie_request_completion"
    const val CHANNEL_FAILURE = "magpie_request_failure"

    const val NOTIFICATION_ID_KEEPALIVE = 4102
    const val NOTIFICATION_ID_ALERT = 4103
    const val NOTIFICATION_ID_FAILURE = 4104

    /** Service actions raised by the keepalive and alert notification buttons. */
    const val ACTION_STOP = "com.magpie.pocket.action.STOP_KEEPALIVE"
    const val ACTION_MUTE_ALERTS = "com.magpie.pocket.action.MUTE_QUOTA_ALERTS"

    /** Navigation extras read by MainActivity when a notification opens a page or a request. */
    const val EXTRA_USAGE_SECTION = "com.magpie.pocket.extra.USAGE_SECTION"
    const val EXTRA_ROUTE_ID = "com.magpie.pocket.extra.ROUTE_ID"
    const val USAGE_SECTION_QUOTA = "quota"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_KEEPALIVE,
                "后台连接",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "常驻显示今日请求与处理中的调用，不发出声音"
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERT,
                "额度告警",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "供应商额度接近上限时提醒"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_COMPLETION,
                "请求完成",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                // The channel exists so the user can tune it; this build sends no completion
                // notification yet, so notif_completion (default false) is the only switch.
                description = "请求完成时提醒，默认关闭"
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_FAILURE,
                "请求失败",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "请求最终失败时提醒"
            }
        )
    }
}

/** The alert gate's memory. SharedPreferences keeps it across process restarts. */
internal class SharedPreferencesQuotaAlertStore(context: Context) : QuotaAlertStore {

    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    override fun read(key: String): String? = preferences.getString(key, null)

    override fun write(key: String, value: String) {
        preferences.edit().putString(key, value).apply()
    }

    override fun remove(key: String) {
        preferences.edit().remove(key).apply()
    }

    override fun keys(): Set<String> = preferences.all.keys.toSet()

    private companion object {
        const val PREFERENCES_NAME = "magpie_notification_alerts"
    }
}
