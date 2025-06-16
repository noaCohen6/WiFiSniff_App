package com.example.wifisniff.data

enum class SecurityType {
    OPEN,     // Open network
    WEP,      // WEP - weak encryption
    WPA,      // WPA - moderate encryption
    WPA2,     // WPA2 - good encryption
    WPA3,     // WPA3 - excellent encryption
    WPS,      // WPS - setup protocol
    UNKNOWN;

    companion object {

        fun fromCapabilities(capabilities: String): SecurityType {
            val caps = capabilities.uppercase()
            return when {
                caps.contains("WPA3") -> WPA3
                caps.contains("WPA2") -> WPA2
                caps.contains("WPA") && !caps.contains("WPA2") -> WPA
                caps.contains("WEP") -> WEP
                caps.contains("WPS") -> WPS
                caps.contains("ESS") && !caps.contains("WPA") && !caps.contains("WEP") -> OPEN
                else -> UNKNOWN
            }
        }
    }
}