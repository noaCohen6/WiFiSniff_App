package com.example.wifisniff.data
import java.io.Serializable

data class ScanResults(
    val networks: List<WifiNetwork>,
    val scanLocation: WifiLocation?,
    val scanTime: Long = System.currentTimeMillis(),
    val scanDuration: Long = 0
) : Serializable {

    val securityStats: SecurityStats
        get() {
            val groups = networks.groupBy { it.securityType }
            return SecurityStats(
                openNetworks = groups[SecurityType.OPEN]?.size ?: 0,
                wepNetworks = groups[SecurityType.WEP]?.size ?: 0,
                wpaNetworks = groups[SecurityType.WPA]?.size ?: 0,
                wpa2Networks = groups[SecurityType.WPA2]?.size ?: 0,
                wpa3Networks = groups[SecurityType.WPA3]?.size ?: 0,
                wpsNetworks = groups[SecurityType.WPS]?.size ?: 0,
                unknownNetworks = groups[SecurityType.UNKNOWN]?.size ?: 0
            )
        }


}