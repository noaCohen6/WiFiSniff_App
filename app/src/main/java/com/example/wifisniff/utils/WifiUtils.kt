package com.example.wifisniff.utils
import android.content.Context
import android.net.wifi.WifiManager
import android.location.Location
import com.example.wifisniff.data.SecurityInfo
import com.example.wifisniff.data.SecurityType
import com.example.wifisniff.data.NetworkRisk
import com.example.wifisniff.data.WifiNetwork
import kotlin.math.*

object WifiUtils {

    fun isWifiEnabled(context: Context): Boolean {
        val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return wifiManager.isWifiEnabled
    }



    fun calculateDistance(rssi: Int, frequency: Int): Double {
        if (rssi == 0) return -1.0

        val exp = (27.55 - (20 * log10(frequency.toDouble())) + abs(rssi)) / 20.0
        return 10.0.pow(exp)
    }


    fun calculateEstimatedLocation(
        userLocation: Location,
        distance: Double,
        angle: Double? = null
    ): Pair<Double, Double> {
        val randomAngle = angle ?: (Math.random() * 2 * PI)
        val randomDistance = Math.random() * distance

        val deltaLat = randomDistance * cos(randomAngle) / 111320.0
        val deltaLng = randomDistance * sin(randomAngle) /
                (111320.0 * cos(Math.toRadians(userLocation.latitude)))

        return Pair(
            userLocation.latitude + deltaLat,
            userLocation.longitude + deltaLng
        )
    }


    fun getChannelFromFrequency(frequency: Int): Int {
        return when {
            // 2.4GHz band
            frequency >= 2412 && frequency <= 2484 -> {
                when (frequency) {
                    2484 -> 14 // Channel 14 (Japan only)
                    else -> (frequency - 2412) / 5 + 1
                }
            }
            // 5GHz band
            frequency >= 5170 && frequency <= 5825 -> {
                (frequency - 5000) / 5
            }
            // 6GHz band (WiFi 6E)
            frequency >= 5955 && frequency <= 7125 -> {
                (frequency - 5950) / 5
            }
            else -> 0
        }
    }


    fun getWifiBand(frequency: Int): String {
        return when {
            frequency in 2400..2500 -> "2.4GHz"
            frequency in 5000..6000 -> "5GHz"
            frequency in 5950..7125 -> "6GHz"
            else -> "Unknown"
        }
    }


    fun rssiToPercentage(rssi: Int): Int {
        return when {
            rssi <= -100 -> 0
            rssi >= -50 -> 100
            else -> 2 * (rssi + 100)
        }
    }

    fun getSignalQuality(rssi: Int): String {
        return when {
            rssi > -50 -> "Excellent"
            rssi > -60 -> "Good"
            rssi > -70 -> "Fair"
            rssi > -80 -> "Weak"
            else -> "Very Weak"
        }
    }

    fun parseSecurityCapabilities(capabilities: String): SecurityInfo {
        val caps = capabilities.uppercase()

        val isWpa3 = caps.contains("WPA3") || caps.contains("SAE")
        val isWpa2 = caps.contains("WPA2") || caps.contains("RSN")
        val isWpa = caps.contains("WPA") && !caps.contains("WPA2") && !caps.contains("WPA3")
        val isWep = caps.contains("WEP")
        val hasWps = caps.contains("WPS")
        val isOpen = !isWpa3 && !isWpa2 && !isWpa && !isWep && caps.contains("ESS")

        val securityType = when {
            isWpa3 -> SecurityType.WPA3
            isWpa2 -> SecurityType.WPA2
            isWpa -> SecurityType.WPA
            isWep -> SecurityType.WEP
            hasWps -> SecurityType.WPS
            isOpen -> SecurityType.OPEN
            else -> SecurityType.UNKNOWN
        }

        val encryptionMethods = mutableListOf<String>()
        if (caps.contains("CCMP")) encryptionMethods.add("AES")
        if (caps.contains("TKIP")) encryptionMethods.add("TKIP")
        if (caps.contains("WEP")) encryptionMethods.add("WEP")

        return SecurityInfo(
            securityType = securityType,
            hasWps = hasWps,
            encryptionMethods = encryptionMethods,
            authenticationMethods = parseAuthMethods(caps)
        )
    }

    private fun parseAuthMethods(capabilities: String): List<String> {
        val authMethods = mutableListOf<String>()

        if (capabilities.contains("PSK")) authMethods.add("PSK")
        if (capabilities.contains("EAP")) authMethods.add("EAP")
        if (capabilities.contains("SAE")) authMethods.add("SAE")
        if (capabilities.contains("OWE")) authMethods.add("OWE")

        return authMethods
    }

    fun assessNetworkRisk(network: WifiNetwork): NetworkRisk {
        return when {
            network.securityType == SecurityType.OPEN -> NetworkRisk.HIGH
            network.securityType == SecurityType.WEP -> NetworkRisk.HIGH
            network.securityType == SecurityType.WPS -> NetworkRisk.MEDIUM
            network.securityType == SecurityType.WPA -> NetworkRisk.MEDIUM
            network.securityType == SecurityType.WPA2 -> NetworkRisk.LOW
            network.securityType == SecurityType.WPA3 -> NetworkRisk.VERY_LOW
            else -> NetworkRisk.UNKNOWN
        }
    }


    fun calculateNetworkDensity(networks: List<WifiNetwork>, radiusMeters: Double): Double {
        val areaKm2 = (PI * radiusMeters * radiusMeters) / 1_000_000
        return networks.size / areaKm2
    }


    fun findLeastCongestedChannel(networks: List<WifiNetwork>): Int {
        val channelUsage = networks
            .filter { it.getWifiBand() == "2.4GHz" }
            .groupBy { it.channel }
            .mapValues { it.value.size }

        // Channels 1, 6, 11 are non-overlapping in 2.4GHz
        val recommendedChannels = listOf(1, 6, 11)

        return recommendedChannels.minByOrNull {
            channelUsage[it] ?: 0
        } ?: 1
    }

    private fun WifiNetwork.getWifiBand(): String {
        return getWifiBand(this.frequency)
    }
}