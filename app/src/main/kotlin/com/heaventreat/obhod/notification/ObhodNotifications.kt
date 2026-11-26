package com.heaventreat.obhod.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.heaventreat.obhod.R
import com.heaventreat.obhod.strategy.StrategyProfiles
import com.heaventreat.obhod.ui.MainActivity
import com.heaventreat.obhod.vpn.VpnPreferences

object ObhodNotifications {
    private const val LEGACY_TELEGRAM_NOTIFICATION_ID = 41
    const val CHANNEL_ID = "obhod_status"
    const val NOTIFICATION_ID = 42
    const val ENGINE_NOTIFICATION_ID = 43

    private object Text {
        const val CHANNEL_NAME = "Обход"
        const val TITLE = "Обход"
        const val RUNNING = "Системный обход и MTProxy работают"
        const val KERNEL_RUNNING = "nZAPRET активен: ядерный обход без VPN"
        const val STARTING = "Запуск обхода…"
        const val STOPPING = "Остановка обхода…"
        const val ENGINE_RUNNING = "Движок ByeByeDPI работает"
    }

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            Text.CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
        manager.cancel(LEGACY_TELEGRAM_NOTIFICATION_ID)
    }

    fun markStarting(context: Context) {
        VpnPreferences.get(context)
            .edit()
            .putBoolean(VpnPreferences.STARTING, true)
            .apply()
        ObhodTileService.requestUpdate(context)
    }

    fun build(context: Context) = buildNotification(context, statusText(context))

    fun buildEngine(context: Context) = buildNotification(context, Text.ENGINE_RUNNING)

    private fun buildNotification(context: Context, content: String) =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(Text.TITLE)
            .setContentText(content)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    fun refresh(context: Context) {
        val vpnPrefs = VpnPreferences.get(context)
        val hasActiveService = vpnPrefs.getBoolean(VpnPreferences.RUNNING, false) ||
            vpnPrefs.getBoolean(VpnPreferences.STARTING, false)
        val manager = context.getSystemService(NotificationManager::class.java)
        if (hasActiveService) manager.notify(NOTIFICATION_ID, build(context))
        else manager.cancel(NOTIFICATION_ID)
        ObhodTileService.requestUpdate(context)
    }

    private fun statusText(context: Context): String {
        val vpnPrefs = VpnPreferences.get(context)
        val running = vpnPrefs.getBoolean(VpnPreferences.RUNNING, false)
        val profile = vpnPrefs.getString(VpnPreferences.PROFILE, "")
        return when {
            running && profile == StrategyProfiles.ROOT_ID -> Text.KERNEL_RUNNING
            running -> Text.RUNNING
            vpnPrefs.getBoolean(VpnPreferences.STARTING, false) -> Text.STARTING
            else -> Text.STOPPING
        }
    }
}
