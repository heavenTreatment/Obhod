package com.heaventreat.obhod.diagnostics

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import com.heaventreat.obhod.logging.AppLog
import com.heaventreat.obhod.network.NetworkCalibrator
import com.heaventreat.obhod.strategy.StrategyProfiles
import com.heaventreat.obhod.vpn.VpnPreferences
import java.text.DateFormat
import java.util.Date
import java.util.Locale

internal object ActivityDiagnosticReport {
    fun create(
        context: Context,
        operatorRegion: String,
        vpnRunning: Boolean,
        starting: Boolean
    ): String {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val network = connectivity?.let(::underlyingNetwork)
        val capabilities = network?.let(connectivity::getNetworkCapabilities)
        val details = network?.let { NetworkCalibrator.inspectNetwork(context, it) }
        val entries = AppLog.entries.value
        val messages = entries.map { it.message }
        val calibrationSignal = messages.asReversed().firstNotNullOfOrNull { message ->
            message.takeIf { it.startsWith("Калибровка сети: ") }
                ?.removePrefix("Калибровка сети: ")
                ?.substringBefore(" (кэш)")
        } ?: "неизвестно"
        val dnsComparison = messages.asReversed()
            .firstNotNullOfOrNull { message ->
                message.takeIf { it.startsWith("DNS Discord/system-vs-DoH: ") }
                    ?.removePrefix("DNS Discord/system-vs-DoH: ")
            } ?: "неизвестно"
        val selectedProfile = VpnPreferences.get(context)
            .getString(VpnPreferences.SELECTED_PROFILE, StrategyProfiles.DEFAULT_ID)
            ?: "неизвестно"
        val networkClass = when {
            capabilities == null -> "неизвестно"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "мобильная"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "другая"
        }
        val data = DiagnosticReportData(
            generatedAt = DateFormat.getDateTimeInstance(
                DateFormat.SHORT,
                DateFormat.MEDIUM,
                Locale.ROOT
            ).format(Date()),
            appVersion = appVersionName(context),
            androidVersion = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            device = "${Build.MANUFACTURER} ${Build.MODEL}".trim().take(80),
            networkClass = networkClass,
            networkValidated = capabilities?.hasCapability(
                NetworkCapabilities.NET_CAPABILITY_VALIDATED
            ),
            networkMetered = capabilities?.let {
                !it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            },
            privateDnsActive = details?.privateDnsActive,
            ipv6Only = details?.ipv6Only,
            calibrationSignal = calibrationSignal,
            dnsComparison = dnsComparison,
            selectedProfile = selectedProfile,
            operatorRegion = operatorRegion,
            selfTest = DiagnosticReport.latestSelfTest(messages),
            logs = entries.map { it.asLine() },
            connectionEntries = entries,
            vpnRunning = vpnRunning,
            vpnStarting = starting
        )
        return DiagnosticReport.format(data)
    }

    @Suppress("DEPRECATION")
    fun underlyingNetwork(connectivity: ConnectivityManager): Network? {
        val candidates = (listOfNotNull(connectivity.activeNetwork) +
            connectivity.allNetworks).distinct()
        return candidates.firstOrNull { candidate ->
            val capabilities = connectivity.getNetworkCapabilities(candidate)
                ?: return@firstOrNull false
            !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                    capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
        }
    }

    @Suppress("DEPRECATION")
    fun appVersionName(context: Context): String =
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
}
