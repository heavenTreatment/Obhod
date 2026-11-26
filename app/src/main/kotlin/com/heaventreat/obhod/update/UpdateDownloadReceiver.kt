package com.heaventreat.obhod.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.heaventreat.obhod.logging.AppLog

class UpdateDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        if (downloadId < 0L || UpdatePreferences.downloadId(context) != downloadId) return

        val pendingResult = goAsync()
        Thread {
            try {
                if (UpdateDownloader.progress(context)?.stage == UpdateDownloadStage.FAILED) {
                    AppLog.w(TAG, "Update APK download failed")
                    return@Thread
                }
                UpdateInstallFlow.installDownloadedApk(context, downloadId)
            } catch (error: Exception) {
                val tag = UpdatePreferences.downloadTag(context).orEmpty()
                AppLog.e(TAG, "Could not prepare downloaded update", error)
                UpdatePreferences.clearDismissedTag(context, tag)
                UpdatePreferences.clearDownload(context)
                context.getSystemService(DownloadManager::class.java).remove(downloadId)
                UpdateNotifications.showInstallError(context, tag)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private companion object {
        const val TAG = "AppUpdate"
    }
}
