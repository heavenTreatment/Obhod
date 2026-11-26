package com.heaventreat.obhod.update

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import java.io.IOException

internal object UpdateDownloader {
    fun enqueue(context: Context, update: AppUpdate): Long {
        val downloadUrl = update.apkUrl ?: throw IOException("В релизе нет APK-файла")
        val uri = Uri.parse(downloadUrl)
        require(uri.scheme == "https" && uri.host == "github.com") {
            "Недопустимый адрес APK для обновления"
        }
        val request = DownloadManager.Request(uri)
            .setTitle("Обновление Obhod ${update.tag}")
            .setDescription("Загрузка APK")
            .setMimeType("application/vnd.android.package-archive")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(
                context,
                Environment.DIRECTORY_DOWNLOADS,
                "Obhod-${update.tag}.apk"
            )
        val id = downloadManager(context).enqueue(request)
        UpdatePreferences.saveDownload(context, id, update.tag)
        return id
    }

    fun progress(context: Context): UpdateDownloadProgress? {
        val id = UpdatePreferences.downloadId(context) ?: return null
        if (UpdatePreferences.isInstalling(context)) {
            return UpdateDownloadProgress(UpdateDownloadStage.INSTALLING, 100)
        }
        val query = DownloadManager.Query().setFilterById(id)
        downloadManager(context).query(query)?.use { cursor ->
            if (!cursor.moveToFirst()) return null
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val downloaded = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            )
            val total = cursor.getLong(
                cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            )
            return when (status) {
                DownloadManager.STATUS_PENDING,
                DownloadManager.STATUS_RUNNING,
                DownloadManager.STATUS_PAUSED -> UpdateDownloadProgress(
                    UpdateDownloadStage.DOWNLOADING,
                    if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else 0
                )
                DownloadManager.STATUS_SUCCESSFUL ->
                    UpdateDownloadProgress(UpdateDownloadStage.INSTALLING, 100)
                DownloadManager.STATUS_FAILED -> UpdateDownloadProgress(
                    UpdateDownloadStage.FAILED,
                    0,
                    "Загрузка не удалась (код ${cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))})"
                )
                else -> null
            }
        }
        return null
    }

    fun cancel(context: Context) {
        val id = UpdatePreferences.downloadId(context) ?: return
        downloadManager(context).remove(id)
        UpdatePreferences.clearDownload(context)
    }

    private fun downloadManager(context: Context) =
        context.getSystemService(DownloadManager::class.java)
}
