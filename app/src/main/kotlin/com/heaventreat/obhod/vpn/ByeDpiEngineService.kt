package com.heaventreat.obhod.vpn

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Process
import com.heaventreat.obhod.logging.AppLog
import com.heaventreat.obhod.notification.ObhodNotifications
import com.heaventreat.obhod.proxy.ByeDpiEngine

class ByeDpiEngineService : Service() {
    companion object {
        const val ACTION_START = "com.heaventreat.obhod.byedpi.START"
        const val ACTION_STOP = "com.heaventreat.obhod.byedpi.STOP"
        const val ACTION_LOG = "com.heaventreat.obhod.byedpi.LOG"
        const val EXTRA_ARGS = "args"
        const val EXTRA_FOREGROUND = "foreground"
        const val EXTRA_PRIORITY = "priority"
        const val EXTRA_TAG = "tag"
        const val EXTRA_MESSAGE = "message"
        private const val TAG = "ByeDpiEngine"
    }

    @Volatile private var stopping = false
    @Volatile private var worker: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startEngine(
                intent.getStringArrayExtra(EXTRA_ARGS),
                intent.getBooleanExtra(EXTRA_FOREGROUND, false)
            )
            ACTION_STOP -> stopEngine()
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    private fun startEngine(args: Array<String>?, foreground: Boolean) {
        if (worker?.isAlive == true) return
        if (args.isNullOrEmpty()) {
            AppLog.e(TAG, "ByeByeDPI engine received an empty argument list")
            stopSelf()
            return
        }
        if (foreground) enterForeground()
        stopping = false
        ByeDpiEngine.setLogSink { priority, tag, message ->
            sendBroadcast(
                Intent(ACTION_LOG)
                    .setPackage(packageName)
                    .putExtra(EXTRA_PRIORITY, priority)
                    .putExtra(EXTRA_TAG, tag)
                    .putExtra(EXTRA_MESSAGE, message)
            )
        }
        worker = Thread({
            val result = try {
                ByeDpiEngine.startProxy(args)
            } catch (error: Throwable) {
                AppLog.e(TAG, "ByeByeDPI native engine crashed", error)
                -1
            }
            AppLog.i(TAG, "ByeByeDPI proxy exited with code $result")
            clearForeground()
            stopSelf()
            if (stopping) Process.killProcess(Process.myPid())
        }, "ByeDPI-proxy").apply { start() }
    }

    private fun enterForeground() {
        ObhodNotifications.createChannel(this)
        val notification = ObhodNotifications.buildEngine(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                ObhodNotifications.ENGINE_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(ObhodNotifications.ENGINE_NOTIFICATION_ID, notification)
        }
    }

    private fun clearForeground() {
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
    }

    private fun stopEngine() {
        stopping = true
        clearForeground()
        runCatching { ByeDpiEngine.stopProxy() }
            .onFailure { AppLog.w(TAG, "Failed to signal ByeByeDPI shutdown", it) }
        Thread({
            val runningWorker = worker
            runningWorker?.join(1_500L)
            if (runningWorker?.isAlive == true) {
                Process.killProcess(Process.myPid())
            } else {
                stopSelf()
                Process.killProcess(Process.myPid())
            }
        }, "ByeDPI-stop").start()
    }

    override fun onDestroy() {
        clearForeground()
        runCatching { ByeDpiEngine.setLogSink(null) }
        if (!stopping && worker?.isAlive == true) {
            stopping = true
            runCatching { ByeDpiEngine.stopProxy() }
        }
        super.onDestroy()
    }
}
