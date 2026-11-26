package com.heaventreat.obhod.vpn

import android.content.Context
import android.content.SharedPreferences
import com.heaventreat.obhod.strategy.StrategyProfiles

internal object VpnPreferences {
    private const val NAME = "obhod"
    private const val LEGACY_NAME = "zapret"
    private const val MIGRATED_KEY = "legacy_prefs_migrated"

    const val RUNNING = "running"
    const val STARTING = "starting"
    const val PROFILE = "profile"
    const val SELECTED_PROFILE = "selected_profile"
    const val LAST_ERROR = "last_error"
    const val BYEBYEDPI_FULL_ANALYSIS = "byebydpi_full_analysis"

    fun get(context: Context): SharedPreferences {
        val preferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        if (!preferences.contains(MIGRATED_KEY)) migrate(context, preferences)
        return preferences
    }

    private fun migrate(context: Context, preferences: SharedPreferences) {
        val legacy = context.getSharedPreferences(LEGACY_NAME, Context.MODE_PRIVATE).all
        val editor = preferences.edit().putBoolean(MIGRATED_KEY, true)
        legacy.forEach { (key, value) ->
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is String -> editor.putString(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        val legacyRootId = StrategyProfiles.LEGACY_ROOT_ID
        if (legacy[SELECTED_PROFILE] == legacyRootId) {
            editor.putString(SELECTED_PROFILE, StrategyProfiles.ROOT_ID)
        }
        if (legacy[PROFILE] == legacyRootId) {
            editor.putString(PROFILE, StrategyProfiles.ROOT_ID)
        }
        editor.apply()
    }
}
