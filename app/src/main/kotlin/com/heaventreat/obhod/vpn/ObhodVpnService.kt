package com.heaventreat.obhod.vpn

import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.system.OsConstants
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.heaventreat.obhod.bridge.NativeBridge
import com.heaventreat.obhod.logging.AppLog
import com.heaventreat.obhod.network.NetworkCalibrator
import com.heaventreat.obhod.notification.ObhodNotifications
import com.heaventreat.obhod.proxy.NativeTelegramProxy
import com.heaventreat.obhod.proxy.TelegramProxyConfig
import com.heaventreat.obhod.root.NzapretManager
import com.heaventreat.obhod.strategy.ByeByeDpiManager
import com.heaventreat.obhod.strategy.StrategyProfiles
import java.io.File
import java.net.Inet6Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.security.MessageDigest
import java.util.concurrent.Executors

class ObhodVpnService : VpnService() {
    companion object {
        const val ACTION_START = "com.heaventreat.obhod.vpn.START"
        const val ACTION_STOP = "com.heaventreat.obhod.vpn.STOP"
        const val ACTION_ROOT_RESELECT = "com.heaventreat.obhod.vpn.ROOT_RESELECT"
        const val ACTION_BYEBYEDPI_RESELECT = "com.heaventreat.obhod.vpn.BYEBYEDPI_RESELECT"
        const val ACTION_STATE_CHANGED = "com.heaventreat.obhod.vpn.STATE_CHANGED"
        const val EXTRA_PROFILE = "profile"
        private const val TAG = "ObhodVpnService"
        private const val NETWORK_REFRESH_DEBOUNCE_MS = 1500L
    }

    @Volatile private var vpnInterface: ParcelFileDescriptor? = null
    @Volatile private var telegramProxyStarted = false
    @Volatile private var byedpiStarted = false
    @Volatile private var rootActive = false
    @Volatile private var starting = false
    @Volatile private var stopRequested = false
    @Volatile private var networkCallbackRegistered = false
    @Volatile private var networkRefreshQueued = false
    @Volatile private var configuredNetworkFingerprint = ""
    private var networkChangeGeneration = 0L
    private val vpnExecutor = Executors.newSingleThreadExecutor()
    private val connectivity by lazy { getSystemService(ConnectivityManager::class.java) }
    private val networkRefreshHandler by lazy { Handler(Looper.getMainLooper()) }
    private val networkRefreshRunnable = Runnable { processNetworkRefresh() }
    private val byeDpiLogReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: Intent?) {
            val message = intent?.getStringExtra(ByeDpiEngineService.EXTRA_MESSAGE) ?: return
            val tag = "ByeDPI/" + intent.getStringExtra(ByeDpiEngineService.EXTRA_TAG).orEmpty()
            when (intent.getIntExtra(ByeDpiEngineService.EXTRA_PRIORITY, android.util.Log.INFO)) {
                android.util.Log.ERROR, android.util.Log.ASSERT -> AppLog.e(tag, message)
                android.util.Log.WARN -> AppLog.w(tag, message)
                android.util.Log.INFO -> AppLog.i(tag, message)
                else -> AppLog.d(tag, message)
            }
        }
    }
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = queueNetworkRefresh()
        override fun onLost(network: Network) = queueNetworkRefresh()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
            queueNetworkRefresh()
        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) =
            queueNetworkRefresh()
    }

    override fun onCreate() {
        super.onCreate()
        ContextCompat.registerReceiver(
            this,
            byeDpiLogReceiver,
            IntentFilter(ByeDpiEngineService.ACTION_LOG),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        ObhodNotifications.createChannel(this)
    }

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopRequested = true
            starting = false
            vpnExecutor.execute {
                deactivateRootIfNeeded()
                shutdown()
                stopSelf()
            }
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_ROOT_RESELECT) {
            if (!rootActive || starting) return START_NOT_STICKY
            starting = true
            vpnExecutor.execute {
                try {
                    val result = NzapretManager.reselect(this, currentNetworkKey())
                    if (result.ok) AppLog.i(TAG, result.message)
                    else VpnPreferences.get(this).edit()
                        .putString(VpnPreferences.LAST_ERROR, result.message).apply()
                    sendBroadcast(android.content.Intent(ACTION_STATE_CHANGED).setPackage(packageName))
                } finally {
                    starting = false
                }
            }
            return START_STICKY
        }
        if (intent?.action == ACTION_BYEBYEDPI_RESELECT) {
            if (currentProfile() != StrategyProfiles.BYEBYEDPI_ID || vpnInterface == null) {
                return START_NOT_STICKY
            }
            starting = true
            ObhodNotifications.markStarting(this)
            vpnExecutor.execute {
                try {
                    val networkKey = currentNetworkKey()
                    AppLog.i(TAG, "Повторный авто-подбор ByeByeDPI для $networkKey")
                    unregisterNetworkCallback()
                    releaseNativeAndVpn()
                    val outcome = ByeByeDpiManager.reselect(this, networkKey) { message ->
                        AppLog.i(TAG, message)
                    }
                    if (!outcome.ok) error(outcome.message)
                    AppLog.i(TAG, outcome.message)
                    configuredNetworkFingerprint = ""
                    startVpn()
                } catch (error: Throwable) {
                    AppLog.e(TAG, "Повторный авто-подбор ByeByeDPI не удался", error)
                    shutdown()
                    stopForeground(STOP_FOREGROUND_DETACH)
                    VpnPreferences.get(this).edit()
                        .putString(VpnPreferences.LAST_ERROR, "ByeByeDPI: не удалось подобрать стратегию")
                        .apply()
                    publishState(false, StrategyProfiles.BYEBYEDPI_ID)
                    stopSelf()
                } finally {
                    starting = false
                }
            }
            return START_STICKY
        }
        if (vpnInterface != null || starting || rootActive) {
            return START_STICKY
        }

        val requestedProfile = intent?.getStringExtra(EXTRA_PROFILE)
            ?: VpnPreferences.get(this)
                .getString(VpnPreferences.SELECTED_PROFILE, StrategyProfiles.DEFAULT_ID)
            ?: StrategyProfiles.DEFAULT_ID
        val profileId = if (StrategyProfiles.isValid(requestedProfile)) {
            StrategyProfiles.normalize(requestedProfile)
        } else {
            AppLog.w(TAG, "Неизвестный режим $requestedProfile; используется ByeByeDPI")
            StrategyProfiles.DEFAULT_ID
        }
        stopRequested = false
        ObhodNotifications.markStarting(this)
        val notification = ObhodNotifications.build(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                ObhodNotifications.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else startForeground(ObhodNotifications.NOTIFICATION_ID, notification)
        starting = true
        vpnExecutor.execute {
            try {
                if (profileId == StrategyProfiles.ROOT_ID) {
                    startRoot(profileId)
                } else {
                    val rootMode = NzapretManager.deactivate()
                    if (!rootMode.ok) {
                        VpnPreferences.get(this).edit()
                            .putString(VpnPreferences.LAST_ERROR, rootMode.message).apply()
                        shutdown()
                        stopSelf()
                    } else {
                        if (rootMode.message.isNotBlank()) AppLog.i(TAG, rootMode.message)
                        startVpn()
                    }
                }
            } finally {
                starting = false
            }
        }
        return START_STICKY
    }

    private fun startRoot(profileId: String) {
        rootActive = true
        VpnPreferences.get(this).edit()
            .remove(VpnPreferences.LAST_ERROR)
            .apply()
        val networkKey = currentNetworkKey()
        AppLog.i(TAG, "Запуск режима Zapret Root через KernelSU-модуль nZAPRET ($networkKey)")
        val outcome = NzapretManager.activate(this, networkKey)
        if (!outcome.ok) {
            if (outcome.rebootRequired) AppLog.i(TAG, outcome.message)
            else AppLog.e(TAG, "nZAPRET не запустился: ${outcome.message}")
            rootActive = false
            shutdown()
            stopForeground(STOP_FOREGROUND_DETACH)
            VpnPreferences.get(this).edit()
                .putString(VpnPreferences.LAST_ERROR, outcome.message)
                .apply()
            publishState(false, profileId)
            stopSelf()
            return
        }
        AppLog.i(TAG, outcome.message)
        publishState(true, profileId)
        configuredNetworkFingerprint = currentUnderlyingNetworkFingerprint()
        registerNetworkCallback()
    }

    private fun deactivateRootIfNeeded() {
        rootActive = false
        val outcome = NzapretManager.deactivate()
        if (outcome.ok) AppLog.i(TAG, outcome.message)
        else AppLog.w(TAG, outcome.message)
    }

    private fun startVpn() {
        VpnPreferences.get(this).edit()
            .putBoolean(VpnPreferences.STARTING, true).apply()
        val baseProfileId = StrategyProfiles.BYEBYEDPI_ID
        AppLog.i(TAG, "Запуск: ByeByeDPI")
        try {
            NativeBridge.load()
            val network = underlyingNetwork()
            val networkKey = currentNetworkKey(network)
            val selection = ByeByeDpiManager.select(this, networkKey) { message ->
                AppLog.i(TAG, message)
            }
            val calibration = NetworkCalibrator.resolve(this, networkKey, network)
            AppLog.i(
                TAG,
                "Калибровка сети: ${calibration.signal.storageValue}" +
                    if (calibration.fromCache) " (кэш)" else ""
            )
            AppLog.i(TAG, "DNS Discord/system-vs-DoH: ${calibration.dnsComparison.storageValue}")
            logNetworkDetails(calibration.networkDetails)
            AppLog.i(TAG, "ByeByeDPI CLI-стратегия: ${selection.winnerId}")
            val ipv6Available = hasUnderlyingIpv6Route()
            val mtu = underlyingMtu(ipv6Available)
            val resolverAddresses = network
                ?.let { connectivity.getLinkProperties(it)?.dnsServers }
                .orEmpty()
                .filter { ipv6Available || it !is Inet6Address }
                .ifEmpty {
                    listOf(InetAddress.getByName("1.1.1.1"), InetAddress.getByName("8.8.8.8"))
                }
            val vpnBuilder = Builder()
                .setSession("Obhod · ${StrategyProfiles.label(baseProfileId)}")
                .setMtu(mtu)
                .addAddress("198.18.0.1", 15)
                .addRoute("0.0.0.0", 0)
                .addDisallowedApplication(packageName)
                .allowFamily(OsConstants.AF_INET6)
            resolverAddresses.forEach { vpnBuilder.addDnsServer(it) }
            if (ipv6Available) {
                vpnBuilder
                    .addAddress("fc00::1", 64)
                    .addRoute("::", 0)
            }
            AppLog.i(TAG, "IPv6 ${if (ipv6Available) "через стратегии" else "в обход VPN"}, TUN MTU $mtu")
            vpnInterface = vpnBuilder
                .establish() ?: error("Android не создал VPN-интерфейс")

            val engineIntent = Intent(this, ByeDpiEngineService::class.java)
                .setAction(ByeDpiEngineService.ACTION_START)
                .putExtra(ByeDpiEngineService.EXTRA_ARGS, ByeByeDpiManager.proxyArguments(selection))
                .putExtra(ByeDpiEngineService.EXTRA_FOREGROUND, true)
            ContextCompat.startForegroundService(this, engineIntent)
            byedpiStarted = true
            if (!waitForByeDpiSocket()) error("ByeByeDPI SOCKS5 не начал принимать соединения")

            val tunnelConfig = File(filesDir, "hev-socks5-tunnel.yml").apply {
                val ipv6Config = if (ipv6Available) "  ipv6: 'fc00::1'\n" else ""
                writeText(
                    """tunnel:
  name: tun0
  mtu: $mtu
  ipv4: 198.18.0.1
${ipv6Config}  icmp: 'off'
socks5:
  port: 10800
  address: 127.0.0.1
  udp: 'udp'
misc:
  connect-timeout: 10000
  tcp-read-write-timeout: 300000
  udp-read-write-timeout: 60000
"""
                )
            }
            if (!NativeBridge.startTunnel(vpnInterface!!.fd, tunnelConfig.absolutePath)) {
                error("Не удалось запустить tun2socks")
            }
            if (!awaitTunnelRunning()) {
                error("Hev tun2socks завершился сразу после запуска")
            }
            AppLog.i(TAG, "Hev tun2socks передаёт TUN-трафик в ByeByeDPI SOCKS5")
            val telegramResult = NativeTelegramProxy.start(
                TelegramProxyConfig.HOST,
                TelegramProxyConfig.PORT,
                TelegramProxyConfig.getOrCreateSecret(this),
                cacheDir.absolutePath
            )
            if (telegramResult != 0) error("Не удалось запустить MTProxy ($telegramResult)")
            telegramProxyStarted = true
            AppLog.i(
                TAG,
                "TUN, SOCKS5 и MTProxy готовы: ${StrategyProfiles.label(baseProfileId)}"
            )
            publishState(true, baseProfileId)
            configuredNetworkFingerprint = currentUnderlyingNetworkFingerprint()
            registerNetworkCallback()
        } catch (error: Throwable) {
            AppLog.e(TAG, "Не удалось запустить системный обход", error)
            shutdown()
            stopForeground(STOP_FOREGROUND_DETACH)
            publishState(false, StrategyProfiles.BYEBYEDPI_ID)
            stopSelf()
        }
    }

    private fun waitForByeDpiSocket(): Boolean {
        val deadline = System.currentTimeMillis() + 8_000L
        while (System.currentTimeMillis() < deadline) {
            try {
                java.net.Socket().use { socket ->
                    socket.connect(java.net.InetSocketAddress("127.0.0.1", 10800), 250)
                    socket.soTimeout = 500
                    socket.getOutputStream().write(byteArrayOf(5, 1, 0))
                    return socket.getInputStream().read() == 5 && socket.getInputStream().read() == 0
                }
            } catch (_: Exception) {
                try {
                    Thread.sleep(80L)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return false
                }
            }
        }
        return false
    }

    private fun awaitTunnelRunning(): Boolean {
        val deadline = System.currentTimeMillis() + 1_500L
        val minimumStartupTime = System.currentTimeMillis() + 300L
        while (System.currentTimeMillis() < deadline) {
            if (!NativeBridge.isTunnelRunning()) return false
            if (System.currentTimeMillis() >= minimumStartupTime) return true
            try {
                Thread.sleep(50L)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        return NativeBridge.isTunnelRunning()
    }

    private fun shutdown() {
        starting = false
        networkRefreshHandler.removeCallbacks(networkRefreshRunnable)
        networkRefreshQueued = false
        unregisterNetworkCallback()
        releaseNativeAndVpn()
        configuredNetworkFingerprint = ""
        stopForeground(STOP_FOREGROUND_DETACH)
        publishState(false, "")
    }

    private fun releaseNativeAndVpn() {
        if (telegramProxyStarted) {
            runCatching { NativeTelegramProxy.stop() }
                .onFailure { AppLog.w(TAG, "Не удалось остановить MTProxy", it) }
            telegramProxyStarted = false
        }
        if (NativeBridge.isLoaded()) {
            runCatching { NativeBridge.stopTunnel() }
                .onFailure { AppLog.w(TAG, "Не удалось остановить tun2socks", it) }
        }
        if (byedpiStarted) {
            runCatching { ByeByeDpiManager.stopEngineService(this) }
                .onFailure { AppLog.w(TAG, "Не удалось остановить ByeByeDPI", it) }
            byedpiStarted = false
        }
        runCatching { vpnInterface?.close() }
        vpnInterface = null
    }

    private fun currentProfile(): String =
        StrategyProfiles.normalize(
            VpnPreferences.get(this).getString(VpnPreferences.PROFILE, "") ?: ""
        )

    private fun currentNetworkKey(network: Network? = underlyingNetwork()): String {
        network ?: return "default"
        val caps = connectivity.getNetworkCapabilities(network) ?: return "default"
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            val telephony = getSystemService(TelephonyManager::class.java)
            val operator = runCatching { telephony.networkOperator }.getOrNull().orEmpty()
                .ifBlank { runCatching { telephony.simOperator }.getOrNull().orEmpty() }
            val radio = runCatching { radioGeneration(telephony) }.getOrDefault("")
            return buildString {
                append("mobile_")
                append(operator.ifBlank { "unknown" })
                if (radio.isNotBlank()) append('_').append(radio)
            }
        }
        val wifiName = runCatching {
            @Suppress("DEPRECATION")
            getSystemService(WifiManager::class.java)?.connectionInfo?.let { info ->
                info.ssid?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
                    ?: info.bssid?.takeIf {
                        it.isNotBlank() && it != "02:00:00:00:00:00"
                    }
            }
        }.getOrNull()
        if (!wifiName.isNullOrBlank()) {
            return "wifi_" + sha1(wifiName).take(12)
        }
        val link = connectivity.getLinkProperties(network)
        val gateway = link?.routes
            ?.firstOrNull { it.destination.prefixLength == 0 && it.gateway != null }
            ?.gateway?.hostAddress
        if (!gateway.isNullOrBlank()) {
            return "wifi_" + sha1("gw:$gateway").take(12)
        }
        val interfaceName = link?.interfaceName
        if (!interfaceName.isNullOrBlank()) {
            return "wifi_" + sha1(interfaceName).take(12)
        }
        return "wifi_unknown"
    }

    private fun radioGeneration(telephony: TelephonyManager): String {
        val type = runCatching { telephony.dataNetworkType }.getOrNull() ?: return ""
        return when {
            type == TelephonyManager.NETWORK_TYPE_LTE -> "4g"
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                type == TelephonyManager.NETWORK_TYPE_NR -> "5g"
            type in setOf(
                TelephonyManager.NETWORK_TYPE_UMTS,
                TelephonyManager.NETWORK_TYPE_EVDO_0,
                TelephonyManager.NETWORK_TYPE_EVDO_A,
                TelephonyManager.NETWORK_TYPE_HSDPA,
                TelephonyManager.NETWORK_TYPE_HSUPA,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_EVDO_B,
                TelephonyManager.NETWORK_TYPE_EHRPD,
                TelephonyManager.NETWORK_TYPE_HSPAP,
                TelephonyManager.NETWORK_TYPE_TD_SCDMA
            ) -> "3g"
            type in setOf(
                TelephonyManager.NETWORK_TYPE_GPRS,
                TelephonyManager.NETWORK_TYPE_EDGE,
                TelephonyManager.NETWORK_TYPE_CDMA,
                TelephonyManager.NETWORK_TYPE_1xRTT,
                TelephonyManager.NETWORK_TYPE_IDEN,
                TelephonyManager.NETWORK_TYPE_GSM
            ) -> "2g"
            else -> ""
        }
    }

    private fun logNetworkDetails(details: NetworkCalibrator.NetworkDetails) {
        val privateDns = details.privateDnsActive?.let { if (it) "active" else "inactive" }
            ?: "unknown"
        val ipv6Only = details.ipv6Only?.let { if (it) "yes" else "no" } ?: "unknown"
        AppLog.i(TAG, "Network diagnostics: Private DNS=$privateDns, IPv6-only=$ipv6Only")
    }

    private fun hasUnderlyingIpv6Route(): Boolean {
        val network = underlyingNetwork() ?: return false
        val link = connectivity.getLinkProperties(network) ?: return false
        return linkHasIpv6DefaultRoute(link)
    }

    private fun linkHasIpv6DefaultRoute(link: LinkProperties): Boolean {
        val hasGlobalIpv6Address = link.linkAddresses.any { linkAddress ->
            val address = linkAddress.address as? Inet6Address
            val firstByte = address?.address?.firstOrNull()?.toInt()?.and(0xff)
            val isUniqueLocal = firstByte != null && (firstByte and 0xfe) == 0xfc
            address != null && !address.isLinkLocalAddress && !address.isLoopbackAddress &&
                !address.isAnyLocalAddress && !address.isMulticastAddress &&
                !address.isSiteLocalAddress && !isUniqueLocal
        }
        val hasIpv6DefaultRoute = link.routes.any { route ->
            route.destination.address is Inet6Address && route.destination.prefixLength == 0
        }
        return hasGlobalIpv6Address && hasIpv6DefaultRoute
    }

    private fun underlyingNetwork(): Network? {
        val activeNetwork = connectivity.activeNetwork
        val candidates = buildList {
            activeNetwork?.let { add(it) }
            addAll(connectivity.allNetworks.filterNot { it == activeNetwork })
        }
        return candidates.firstOrNull { network ->
            val caps = connectivity.getNetworkCapabilities(network) ?: return@firstOrNull false
            !caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
        }
    }

    @Synchronized
    private fun queueNetworkRefresh() {
        networkChangeGeneration++
        networkRefreshHandler.removeCallbacks(networkRefreshRunnable)
        networkRefreshHandler.postDelayed(
            networkRefreshRunnable,
            NETWORK_REFRESH_DEBOUNCE_MS
        )
    }

    private fun processNetworkRefresh() {
        try {
            vpnExecutor.execute {
                if (networkRefreshQueued) return@execute
                networkRefreshQueued = true
                try {
                    while (true) {
                        val generation = synchronized(this) { networkChangeGeneration }
                        refreshUnderlyingNetwork()
                        synchronized(this) {
                            if (generation == networkChangeGeneration) {
                                networkRefreshQueued = false
                                return@execute
                            }
                        }
                    }
                } catch (error: Throwable) {
                    AppLog.e(TAG, "Не удалось обновить сеть", error)
                    shutdown()
                    stopSelf()
                    synchronized(this) { networkRefreshQueued = false }
                }
            }
        } catch (error: java.util.concurrent.RejectedExecutionException) {
            networkRefreshQueued = false
            AppLog.w(TAG, "Сетевое событие после остановки сервиса проигнорировано", error)
        }
    }

    private fun refreshUnderlyingNetwork() {
        if (rootActive) {
            val fingerprint = currentUnderlyingNetworkFingerprint()
            if (fingerprint == configuredNetworkFingerprint) return
            configuredNetworkFingerprint = fingerprint
            val networkKey = currentNetworkKey()
            AppLog.i(TAG, "Сеть изменилась; перезапускаю nZAPRET для $networkKey")
            val outcome = NzapretManager.reselect(this, networkKey)
            if (outcome.ok) AppLog.i(TAG, outcome.message)
            else AppLog.w(TAG, outcome.message)
            return
        }
        if (stopRequested || vpnInterface == null) return
        val fingerprint = currentUnderlyingNetworkFingerprint()
        if (fingerprint == configuredNetworkFingerprint) return
        configuredNetworkFingerprint = fingerprint

        val network = underlyingNetwork()
        val networkKey = currentNetworkKey(network)
        val storedProfile = currentProfile().ifBlank {
            VpnPreferences.get(this)
                .getString(VpnPreferences.SELECTED_PROFILE, StrategyProfiles.DEFAULT_ID)
                ?: StrategyProfiles.DEFAULT_ID
        }
        val profileId = if (StrategyProfiles.isValid(storedProfile)) {
            StrategyProfiles.normalize(storedProfile)
        } else {
            StrategyProfiles.DEFAULT_ID
        }
        AppLog.i(TAG, "Сеть изменилась; обновляю ByeByeDPI для $networkKey")
        releaseNativeAndVpn()
        starting = true
        try {
            startVpn()
        } finally {
            starting = false
        }
    }

    private fun currentUnderlyingNetworkFingerprint(): String {
        val network = underlyingNetwork() ?: return "none"
        val networkKey = currentNetworkKey(network)
        val mtu = linkMtu(connectivity.getLinkProperties(network))
        val ipv6 = hasUnderlyingIpv6Route()
        return sha1("$networkKey|$mtu|$ipv6")
    }

    private fun underlyingMtu(ipv6Enabled: Boolean): Int {
        val network = underlyingNetwork() ?: return 1500
        val mtu = linkMtu(connectivity.getLinkProperties(network))
        if (mtu <= 0) return 1500
        val minimum = if (ipv6Enabled) 1280 else 576
        return mtu.coerceIn(minimum, 1500)
    }

    private fun linkMtu(link: LinkProperties?): Int {
        if (link == null) return 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val mtu = link.mtu
            if (mtu > 0) return mtu
        }
        val interfaceName = link.interfaceName ?: return 0
        return runCatching { NetworkInterface.getByName(interfaceName)?.mtu ?: 0 }
            .getOrDefault(0)
    }

    private fun registerNetworkCallback() {
        if (networkCallbackRegistered) return
        runCatching {
            connectivity.registerDefaultNetworkCallback(networkCallback)
            networkCallbackRegistered = true
        }.onFailure { AppLog.w(TAG, "Не удалось подписаться на события сети", it) }
    }

    private fun unregisterNetworkCallback() {
        if (!networkCallbackRegistered) return
        runCatching { connectivity.unregisterNetworkCallback(networkCallback) }
            .onFailure { AppLog.w(TAG, "Не удалось отписаться от событий сети", it) }
        networkCallbackRegistered = false
    }

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }

    private fun publishState(running: Boolean, profileId: String) {
        val prefs = VpnPreferences.get(this)
        val wasRunning = prefs.getBoolean(VpnPreferences.RUNNING, false)
        prefs.edit()
            .putBoolean(VpnPreferences.RUNNING, running)
            .putBoolean(VpnPreferences.STARTING, false)
            .putString(VpnPreferences.PROFILE, profileId)
            .apply()
        ObhodNotifications.refresh(this)
        if (wasRunning != running) {
            if (running) AppLog.i("VPN", "Системный обход включён: ${StrategyProfiles.label(profileId)}")
            else AppLog.i("VPN", "Системный обход выключен")
        }
        sendBroadcast(android.content.Intent(ACTION_STATE_CHANGED).setPackage(packageName))
    }

    override fun onDestroy() {
        stopRequested = true
        runCatching { unregisterReceiver(byeDpiLogReceiver) }
        shutdown()
        vpnExecutor.shutdown()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopRequested = true
        vpnExecutor.execute {
            deactivateRootIfNeeded()
            shutdown()
            stopSelf()
        }
    }
}
