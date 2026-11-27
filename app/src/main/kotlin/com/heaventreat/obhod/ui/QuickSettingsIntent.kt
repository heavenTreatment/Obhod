package com.heaventreat.obhod.ui

import android.content.Intent

internal object QuickSettingsIntent {
    const val ACTION_START_FROM_TILE = "com.heaventreat.obhod.action.START_FROM_TILE"

    fun consume(intent: Intent?): Boolean {
        if (intent?.action != ACTION_START_FROM_TILE) return false
        intent.action = null
        return true
    }
}
