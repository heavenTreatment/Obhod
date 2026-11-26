package com.heaventreat.obhod.notification

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat
import com.heaventreat.obhod.strategy.StrategyProfiles
import com.heaventreat.obhod.ui.MainActivity
import com.heaventreat.obhod.vpn.ObhodVpnService
import com.heaventreat.obhod.vpn.VpnPreferences

class ObhodTileService : TileService() {
    override fun onTileAdded() {
        super.onTileAdded()
        updateTileState()
    }

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val prefs = VpnPreferences.get(this)
        val active = prefs.getBoolean(VpnPreferences.RUNNING, false) ||
            prefs.getBoolean(VpnPreferences.STARTING, false)
        if (active) {
            startService(
                Intent(this, ObhodVpnService::class.java)
                    .setAction(ObhodVpnService.ACTION_STOP)
            )
            return
        }
        val kernelMode = prefs.getString(
            VpnPreferences.SELECTED_PROFILE,
            StrategyProfiles.DEFAULT_ID
        ) == StrategyProfiles.ROOT_ID
        if (kernelMode) {
            ObhodNotifications.markStarting(this)
            val started = runCatching {
                ContextCompat.startForegroundService(
                    this,
                    Intent(this, ObhodVpnService::class.java)
                        .setAction(ObhodVpnService.ACTION_START)
                )
            }.isSuccess
            if (started) {
                updateTileState()
                return
            }
            prefs.edit().putBoolean(VpnPreferences.STARTING, false).apply()
        }
        val activityIntent = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_START_FROM_TILE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this,
                    0,
                    activityIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(activityIntent)
        }
        updateTileState()
    }

    private fun updateTileState() {
        val prefs = VpnPreferences.get(this)
        val running = prefs.getBoolean(VpnPreferences.RUNNING, false)
        val starting = prefs.getBoolean(VpnPreferences.STARTING, false)
        val active = running || starting
        val kernelMode = prefs.getString(
            VpnPreferences.SELECTED_PROFILE,
            StrategyProfiles.DEFAULT_ID
        ) == StrategyProfiles.ROOT_ID
        qsTile?.apply {
            state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = if (active) "Обход включён" else "Обход выключен"
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                subtitle = when {
                    starting -> "Запуск…"
                    running && kernelMode -> "nZAPRET (root)"
                    running -> "Включён"
                    kernelMode -> "nZAPRET: нужен root"
                    else -> "Нажмите, чтобы включить"
                }
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                contentDescription = when {
                    starting -> "Обход запускается. Нажмите, чтобы остановить."
                    running -> "Обход включён. Нажмите, чтобы выключить."
                    else -> "Обход выключен. Нажмите, чтобы включить."
                }
            }
            updateTile()
        }
    }

    companion object {
        fun requestUpdate(context: Context) {
            requestListeningState(
                context,
                ComponentName(context, ObhodTileService::class.java)
            )
        }
    }
}
