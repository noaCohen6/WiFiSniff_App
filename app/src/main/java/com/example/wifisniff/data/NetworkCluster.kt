package com.example.wifisniff.data

import com.example.wifisniff.utils.WifiUtils

/**
 * Class for grouping networks with the same SSID
 */
data class NetworkCluster(
    val ssid: String,
    val networks: List<WifiNetwork>
) {

    val bssids: List<String>
        get() = networks.map { it.bssid }

    val networkCount: Int
        get() = networks.size

    val strongestNetwork: WifiNetwork
        get() = networks.maxByOrNull { it.rssi } ?: networks.first()

    val averageRssi: Int
        get() = networks.map { it.rssi }.average().toInt()

    val securityTypes: List<SecurityType>
        get() = networks.map { it.securityType }.distinct()

    val isOpenNetwork: Boolean
        get() = securityTypes.contains(SecurityType.OPEN)

    val primarySecurityType: SecurityType
        get() = securityTypes.minByOrNull { it.ordinal } ?: SecurityType.UNKNOWN

    val frequencies: List<Int>
        get() = networks.map { it.frequency }.distinct()

    val channels: List<Int>
        get() = networks.map { it.channel }.distinct()

    /**
     * Calculating the central location of the cluster
     */
    val centerLocation: WifiLocation?
        get() {
            val locationsWithSignal = networks.mapNotNull { network ->
                network.location?.let { location ->
                    val weight = WifiUtils.rssiToPercentage(network.rssi) / 100.0
                    Triple(location.latitude, location.longitude, weight)
                }
            }

            if (locationsWithSignal.isEmpty()) return null

            val totalWeight = locationsWithSignal.sumOf { it.third }
            val weightedLat = locationsWithSignal.sumOf { it.first * it.third } / totalWeight
            val weightedLng = locationsWithSignal.sumOf { it.second * it.third } / totalWeight

            val averageAccuracy = networks.mapNotNull { it.location?.accuracy }.average().toFloat()

            return WifiLocation(
                latitude = weightedLat,
                longitude = weightedLng,
                accuracy = averageAccuracy,
                provider = "clustered"
            )
        }


    val coverageRadius: Double
        get() {
            val center = centerLocation ?: return 0.0
            return networks.mapNotNull { network ->
                network.location?.let { location ->
                    WifiLocation(center.latitude, center.longitude).distanceTo(location).toDouble()
                }
            }.maxOrNull() ?: 0.0
        }


    val overallRisk: NetworkRisk
        get() {
            val risks = networks.map { WifiUtils.assessNetworkRisk(it) }
            return when {
                risks.any { it == NetworkRisk.HIGH } -> NetworkRisk.HIGH
                risks.any { it == NetworkRisk.MEDIUM } -> NetworkRisk.MEDIUM
                risks.all { it == NetworkRisk.LOW } -> NetworkRisk.LOW
                risks.all { it == NetworkRisk.VERY_LOW } -> NetworkRisk.VERY_LOW
                else -> NetworkRisk.UNKNOWN
            }
        }


    fun getDetailedInfo(): String {
        return buildString {
            appendLine("📡 Network Cluster: $ssid")
            appendLine("━━━━━━━━━━━━━━━━━━━━")
            appendLine()
            appendLine("📊 Access Points: $networkCount")
            appendLine("🔒 Security: ${securityTypes.joinToString(", ") { it.name }}")
            appendLine("⚠️ Risk Level: ${overallRisk.description}")
            appendLine("📶 Signal Range: ${networks.minOf { it.rssi }} to ${networks.maxOf { it.rssi }} dBm")
            appendLine("📡 Frequencies: ${frequencies.joinToString(", ")} MHz")
            appendLine("📺 Channels: ${channels.joinToString(", ")}")
            appendLine("📏 Coverage: ~${String.format("%.0f", coverageRadius)} m radius")
            appendLine()
            appendLine("🏠 Access Points Details:")
            networks.forEachIndexed { index, network ->
                appendLine("${index + 1}. ${network.bssid}")
                appendLine("   Signal: ${network.rssi} dBm | Channel: ${network.channel}")
                appendLine("   Security: ${network.securityType.name}")
                network.location?.let { loc ->
                    appendLine("   Distance: ~${String.format("%.0f", network.getEstimatedDistance())} m")
                }
                if (index < networks.size - 1) appendLine()
            }
        }
    }

    fun getMapSnippet(): String {
        val bandsInfo = frequencies.map { WifiUtils.getWifiBand(it) }.distinct()
        val securityInfo = if (isOpenNetwork) "⚠️ OPEN" else primarySecurityType.name

        return "${bandsInfo.joinToString("+")} • $securityInfo • $networkCount APs"
    }
}