package com.example.wifisniff.data
import android.location.Location
import java.io.Serializable
import kotlin.math.pow

data class WifiNetwork(
    val ssid: String,
    val bssid: String, // MAC Address
    val rssi: Int, // Signal strength in dBm
    val frequency: Int, // Frequency in MHz
    val capabilities: String, // Security capabilities
    val location: WifiLocation? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val channel: Int = 0,
    val securityType: SecurityType = SecurityType.UNKNOWN,
    val signalLevel: SignalLevel = SignalLevel.WEAK
) : Serializable {


    fun getEstimatedDistance(): Double {
        if (rssi == 0) return -1.0

        val ratio = (27.55 - (20 * kotlin.math.log10(frequency.toDouble())) + kotlin.math.abs(rssi)) / 20.0
        return 10.0.pow(ratio)
    }


    fun getSignalPercentage(): Int {
        return when {
            rssi <= -100 -> 0
            rssi >= -50 -> 100
            else -> 2 * (rssi + 100)
        }
    }


    fun isOpen(): Boolean {
        return securityType == SecurityType.OPEN
    }


}
