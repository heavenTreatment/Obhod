package com.heaventreat.obhod.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import java.net.Inet6Address
import java.net.NetworkInterface
import java.security.MessageDigest

internal class VpnNetworkIdentity(
    private val context: Context,
    private val connectivity: ConnectivityManager
) {
    fun currentNetworkKey(network: Network? = underlyingNetwork()): String {
        network ?: return "default"
        val caps = connectivity.getNetworkCapabilities(network) ?: return "default"
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
            val telephony = context.getSystemService(TelephonyManager::class.java)
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
            context.getSystemService(WifiManager::class.java)?.connectionInfo?.let { info ->
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

    fun underlyingNetwork(): Network? {
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

    fun currentFingerprint(): String {
        val network = underlyingNetwork() ?: return "none"
        val networkKey = currentNetworkKey(network)
        val mtu = linkMtu(connectivity.getLinkProperties(network))
        val ipv6 = hasUnderlyingIpv6Route()
        return sha1("$networkKey|$mtu|$ipv6")
    }

    fun underlyingMtu(ipv6Enabled: Boolean): Int {
        val network = underlyingNetwork() ?: return 1500
        val mtu = linkMtu(connectivity.getLinkProperties(network))
        if (mtu <= 0) return 1500
        val minimum = if (ipv6Enabled) 1280 else 576
        return mtu.coerceIn(minimum, 1500)
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

    fun hasUnderlyingIpv6Route(): Boolean {
        val network = underlyingNetwork() ?: return false
        val link = connectivity.getLinkProperties(network) ?: return false
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

    private fun sha1(value: String): String =
        MessageDigest.getInstance("SHA-1").digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
