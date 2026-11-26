package com.heaventreat.obhod.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class UpdateInstallStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        UpdateInstallFlow.handleInstallStatus(context, intent)
    }
}
