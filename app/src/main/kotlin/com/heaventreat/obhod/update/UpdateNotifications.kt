package com.heaventreat.obhod.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.heaventreat.obhod.R
import com.heaventreat.obhod.logging.AppLog

internal object UpdateNotifications {
    private const val CHANNEL_ID = "app_updates"
    private const val NOTIFICATION_ID = 4201
    private const val TAG = "AppUpdate"

    fun showInstall(context: Context, installIntent: Intent, version: String) {
        createChannel(context)
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        notify(
            context,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle("Обновление Obhod готово")
                .setContentText("Нажмите, чтобы установить версию $version")
                .setContentIntent(pendingIntent)
                .addAction(R.drawable.ic_shield, "Установить", pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        )
    }

    fun showInstallError(context: Context, version: String) {
        createChannel(context)
        val intent = Intent(
            Intent.ACTION_VIEW,
            android.net.Uri.parse("https://github.com/heavenTreatment/Obhod/releases/tag/$version")
        )
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        notify(
            context,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_shield)
                .setContentTitle("Не удалось установить обновление")
                .setContentText("Скачайте APK версии $version с GitHub")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        )
    }

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun notify(context: Context, notification: NotificationCompat.Builder) {
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification.build())
        } catch (error: SecurityException) {
            AppLog.w(TAG, "Notification permission is unavailable", error)
        }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Обновления приложения",
            NotificationManager.IMPORTANCE_DEFAULT
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
