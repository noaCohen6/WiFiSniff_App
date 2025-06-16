package com.example.wifisniff.data

data class SecurityStats(
    val openNetworks: Int = 0,
    val wepNetworks: Int = 0,
    val wpaNetworks: Int = 0,
    val wpa2Networks: Int = 0,
    val wpa3Networks: Int = 0,
    val wpsNetworks: Int = 0,
    val unknownNetworks: Int = 0
) {
    val totalNetworks: Int
        get() = openNetworks + wepNetworks + wpaNetworks + wpa2Networks + wpa3Networks + wpsNetworks + unknownNetworks

    val secureNetworks: Int
        get() = wpaNetworks + wpa2Networks + wpa3Networks

    val vulnerableNetworks: Int
        get() = openNetworks + wepNetworks + wpsNetworks
}
