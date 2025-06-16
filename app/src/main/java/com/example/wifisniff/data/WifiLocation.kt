package com.example.wifisniff.data

import android.location.Location
import java.io.Serializable

data class WifiLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float = 0f,
    val provider: String = "unknown"
) : Serializable {


    fun distanceTo(other: WifiLocation): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            this.latitude, this.longitude,
            other.latitude, other.longitude,
            results
        )
        return results[0]
    }
}

