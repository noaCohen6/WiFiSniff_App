package com.example.wifisniff.data

enum class NetworkRisk(val description: String, val color: String) {
    VERY_LOW("Very Secure", "#0066CC"),
    LOW("Secure", "#00AA00"),
    MEDIUM("Moderate Risk", "#FFBB33"),
    HIGH("High Risk", "#FF4444"),
    UNKNOWN("Unknown", "#808080")
}