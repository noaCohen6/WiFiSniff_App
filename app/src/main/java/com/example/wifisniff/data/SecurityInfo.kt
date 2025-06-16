package com.example.wifisniff.data

data class SecurityInfo(
    val securityType: SecurityType,
    val hasWps: Boolean,
    val encryptionMethods: List<String>,
    val authenticationMethods: List<String>
)

