package com.heaventreat.obhod.update

import android.app.DownloadManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.ParcelFileDescriptor
import com.heaventreat.obhod.logging.AppLog
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

internal object UpdateInstallFlow {
    private const val TAG = "AppUpdate"
    private const val STATUS_ACTION = "com.heaventreat.obhod.UPDATE_INSTALL_STATUS"
    private const val DOWNLOAD_ID_EXTRA = "download_id"
    private const val MAX_APK_BYTES = 512L * 1024 * 1024

    fun installDownloadedApk(context: Context, downloadId: Long) {
        val tag = UpdatePreferences.downloadTag(context)
            ?: throw IOException("Update release tag is missing")
        val downloadManager = context.getSystemService(DownloadManager::class.java)
        val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
            ?: throw IOException("Downloaded APK record is unavailable")
        val downloadedSize = cursor.use {
            if (!it.moveToFirst()) throw IOException("Downloaded APK record is unavailable")
            it.getLong(it.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
        }
        require(downloadedSize in 1..MAX_APK_BYTES) { "Downloaded APK size is invalid" }

        val apkFile = File(context.cacheDir, "obhod-update-$downloadId.apk")
        try {
            ParcelFileDescriptor.AutoCloseInputStream(
                downloadManager.openDownloadedFile(downloadId)
            ).use { input ->
                FileOutputStream(apkFile).use { output -> input.copyTo(output) }
            }
            verifyApk(context, apkFile, tag)
            commitInstall(context, apkFile, downloadId)
        } finally {
            apkFile.delete()
        }
    }

    private fun verifyApk(context: Context, apkFile: File, tag: String) {
        @Suppress("DEPRECATION")
        val archive = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
            ?: throw IOException("Downloaded file is not a valid APK")
        require(archive.packageName == context.packageName) {
            "Downloaded APK belongs to a different application"
        }
        val expectedVersion = ReleaseVersion.parse(tag)
            ?: throw IOException("Invalid update release tag: $tag")
        val apkVersion = ReleaseVersion.parse(archive.versionName.orEmpty())
            ?: throw IOException("APK version does not match the release tag")
        require(apkVersion == expectedVersion) {
            "APK version ${archive.versionName} does not match $tag"
        }
        val installedVersion = context.packageManager.getPackageInfo(context.packageName, 0)
            .versionName?.let(ReleaseVersion::parse)
            ?: throw IOException("Installed application version is invalid")
        require(apkVersion > installedVersion) { "Downloaded APK is not newer than the installed app" }
    }

    private fun commitInstall(context: Context, apkFile: File, downloadId: Long) {
        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            .apply { setAppPackageName(context.packageName) }
        val sessionId = packageInstaller.createSession(params)
        try {
            packageInstaller.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apkFile.length()).use { output ->
                    FileInputStream(apkFile).use { input -> input.copyTo(output) }
                    session.fsync(output)
                }
                UpdatePreferences.setInstalling(context, true)
                val statusIntent = Intent(context, UpdateInstallStatusReceiver::class.java)
                    .setAction(STATUS_ACTION)
                    .putExtra(DOWNLOAD_ID_EXTRA, downloadId)
                val mutabilityFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_MUTABLE
                } else {
                    0
                }
                val statusPendingIntent = PendingIntent.getBroadcast(
                    context,
                    downloadId.toInt(),
                    statusIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or mutabilityFlag
                )
                session.commit(statusPendingIntent.intentSender)
            }
        } catch (error: Exception) {
            packageInstaller.abandonSession(sessionId)
            throw error
        }
    }

    fun handleInstallStatus(context: Context, intent: Intent) {
        val downloadId = intent.getLongExtra(DOWNLOAD_ID_EXTRA, -1L)
        val tag = UpdatePreferences.downloadTag(context).orEmpty()
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmationIntent = getConfirmationIntent(intent)
                if (confirmationIntent == null) {
                    fail(context, downloadId, tag, "Installer confirmation intent is missing")
                    return
                }
                UpdateNotifications.showInstall(context, confirmationIntent, tag)
                try {
                    context.startActivity(
                        confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (error: Exception) {
                    AppLog.w(TAG, "Could not launch installer UI; notification was posted", error)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                context.getSystemService(DownloadManager::class.java).remove(downloadId)
                UpdatePreferences.clearDownload(context)
                UpdateNotifications.cancel(context)
                AppLog.i(TAG, "Version $tag installed")
            }
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?: "Package installer rejected the update"
                fail(context, downloadId, tag, message)
            }
        }
    }

    private fun getConfirmationIntent(intent: Intent): Intent? {
        @Suppress("DEPRECATION")
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }
    }

    private fun fail(context: Context, downloadId: Long, tag: String, message: String) {
        AppLog.e(TAG, "Update installation failed: $message")
        UpdatePreferences.clearDismissedTag(context, tag)
        UpdatePreferences.clearDownload(context)
        if (downloadId >= 0L) {
            context.getSystemService(DownloadManager::class.java).remove(downloadId)
        }
        UpdateNotifications.showInstallError(context, tag)
    }
}
