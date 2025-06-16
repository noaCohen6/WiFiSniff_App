package com.example.wifisniff.data

enum class SignalLevel {
    EXCELLENT, // over -50 dBm
    GOOD,      // -50 up to -60 dBm
    FAIR,      // -60 up to -70 dBm
    WEAK,      // -70 up to -80 dBm
    VERY_WEAK; // under -80 dBm

    companion object {
        fun fromRssi(rssi: Int): SignalLevel {
            return when {
                rssi > -50 -> EXCELLENT
                rssi > -60 -> GOOD
                rssi > -70 -> FAIR
                rssi > -80 -> WEAK
                else -> VERY_WEAK
            }
        }
    }
}