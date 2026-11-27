package com.heaventreat.obhod.update

import android.content.Context
import com.heaventreat.obhod.preferences.privatePreferences

internal object UpdatePreferences {
    private const val NAME = "obhod_updates"
    private const val LAUNCH_COUNT = "launch_count"
    private const val REMIND_AT_LAUNCH = "remind_at_launch"
    private const val DISMISSED_TAG = "dismissed_tag"
    private const val DOWNLOAD_ID = "download_id"
    private const val DOWNLOAD_TAG = "download_tag"
    private const val INSTALLING = "installing"

    fun registerLaunch(context: Context): Int {
        val preferences = preferences(context)
        val count = preferences.getInt(LAUNCH_COUNT, 0) + 1
        preferences.edit().putInt(LAUNCH_COUNT, count).apply()
        return count
    }

    fun launchCount(context: Context): Int =
        preferences(context).getInt(LAUNCH_COUNT, 0)

    fun shouldPrompt(context: Context, tag: String, launchCount: Int): Boolean {
        val preferences = preferences(context)
        return preferences.getString(DISMISSED_TAG, null) != tag &&
            launchCount >= preferences.getInt(REMIND_AT_LAUNCH, 0)
    }

    fun remindLater(context: Context, launchCount: Int) {
        preferences(context).edit()
            .putInt(REMIND_AT_LAUNCH, launchCount + 2)
            .apply()
    }

    fun dismiss(context: Context, tag: String) {
        preferences(context).edit().putString(DISMISSED_TAG, tag).apply()
    }

    fun clearDismissedTag(context: Context, tag: String) {
        val preferences = preferences(context)
        if (preferences.getString(DISMISSED_TAG, null) == tag) {
            preferences.edit().remove(DISMISSED_TAG).apply()
        }
    }

    fun saveDownload(context: Context, id: Long, tag: String) {
        preferences(context).edit()
            .putLong(DOWNLOAD_ID, id)
            .putString(DOWNLOAD_TAG, tag)
            .putBoolean(INSTALLING, false)
            .apply()
    }

    fun downloadId(context: Context): Long? =
        preferences(context).getLong(DOWNLOAD_ID, -1L).takeIf { it >= 0L }

    fun downloadTag(context: Context): String? =
        preferences(context).getString(DOWNLOAD_TAG, null)

    fun setInstalling(context: Context, installing: Boolean) {
        preferences(context).edit().putBoolean(INSTALLING, installing).apply()
    }

    fun isInstalling(context: Context): Boolean =
        preferences(context).getBoolean(INSTALLING, false)

    fun clearDownload(context: Context) {
        preferences(context).edit()
            .remove(DOWNLOAD_ID)
            .remove(DOWNLOAD_TAG)
            .remove(INSTALLING)
            .apply()
    }

    private fun preferences(context: Context) =
        context.privatePreferences(NAME)
}
