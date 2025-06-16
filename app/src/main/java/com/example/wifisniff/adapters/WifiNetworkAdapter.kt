package com.example.wifisniff.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.wifisniff.R
import com.example.wifisniff.data.SecurityType
import com.example.wifisniff.data.SignalLevel
import com.example.wifisniff.data.WifiNetwork
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.textview.MaterialTextView

class WifiNetworkAdapter(
    private var networks: List<WifiNetwork>,
    private val onNetworkClick: (WifiNetwork) -> Unit
) : RecyclerView.Adapter<WifiNetworkAdapter.WifiNetworkViewHolder>() {

    fun updateNetworks(newNetworks: List<WifiNetwork>) {
        networks = newNetworks
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WifiNetworkViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_wifi_network, parent, false)
        return WifiNetworkViewHolder(view)
    }

    override fun onBindViewHolder(holder: WifiNetworkViewHolder, position: Int) {
        holder.bind(networks[position])
    }

    override fun getItemCount(): Int = networks.size

    inner class WifiNetworkViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView.findViewById(R.id.cardView)
        private val tvSsid: MaterialTextView = itemView.findViewById(R.id.tvSsid)
        private val tvBssid: MaterialTextView = itemView.findViewById(R.id.tvBssid)
        private val tvSecurity: MaterialTextView = itemView.findViewById(R.id.tvSecurity)
        private val tvSignal: MaterialTextView = itemView.findViewById(R.id.tvSignal)
        private val tvChannel: MaterialTextView = itemView.findViewById(R.id.tvChannel)
        private val tvDistance: MaterialTextView = itemView.findViewById(R.id.tvDistance)
        private val progressSignal: LinearProgressIndicator = itemView.findViewById(R.id.progressSignal)
        private val securityIndicator: View = itemView.findViewById(R.id.securityIndicator)

        fun bind(network: WifiNetwork) {
            // SSID
            tvSsid.text = if (network.ssid.isBlank()) "Hidden Network" else network.ssid

            // BSSID
            tvBssid.text = network.bssid

            // Security
            tvSecurity.text = network.securityType.name
            setupSecurityIndicator(network.securityType)

            // Signal strength
            val signalPercentage = network.getSignalPercentage()
            tvSignal.text = "${network.rssi} dBm (${signalPercentage}%)"
            progressSignal.progress = signalPercentage
            setupSignalProgress(network.signalLevel)

            // Channel
            tvChannel.text = "Ch ${network.channel}"

            // Distance
            val distance = network.getEstimatedDistance()
            tvDistance.text = if (distance > 0) {
                "${String.format("%.0f", distance)}m"
            } else {
                "Unknown"
            }

            // Card styling based on security
            setupCardStyling(network)

            // Click listener
            cardView.setOnClickListener {
                onNetworkClick(network)
            }
        }

        private fun setupSecurityIndicator(securityType: SecurityType) {
            val color = when (securityType) {
                SecurityType.OPEN -> Color.parseColor("#FF4444") // red
                SecurityType.WEP -> Color.parseColor("#FF8800") // orange
                SecurityType.WPA -> Color.parseColor("#FFBB33") // yellow
                SecurityType.WPA2 -> Color.parseColor("#00AA00") // green
                SecurityType.WPA3 -> Color.parseColor("#0066CC") // blue
                SecurityType.WPS -> Color.parseColor("#AA00AA") // purple
                else -> Color.parseColor("#808080") // gray
            }

            securityIndicator.setBackgroundColor(color)
            tvSecurity.setTextColor(color)
        }

        private fun setupSignalProgress(signalLevel: SignalLevel) {
            val (color, trackColor) = when (signalLevel) {
                SignalLevel.EXCELLENT -> Pair(
                    ContextCompat.getColor(itemView.context, R.color.teal_700),
                    ContextCompat.getColor(itemView.context, R.color.teal_200)
                )
                SignalLevel.GOOD -> Pair(
                    ContextCompat.getColor(itemView.context, android.R.color.holo_green_dark),
                    ContextCompat.getColor(itemView.context, android.R.color.holo_green_light)
                )
                SignalLevel.FAIR -> Pair(
                    ContextCompat.getColor(itemView.context, android.R.color.holo_orange_dark),
                    ContextCompat.getColor(itemView.context, android.R.color.holo_orange_light)
                )
                SignalLevel.WEAK -> Pair(
                    ContextCompat.getColor(itemView.context, android.R.color.holo_red_dark),
                    ContextCompat.getColor(itemView.context, android.R.color.holo_red_light)
                )
                SignalLevel.VERY_WEAK -> Pair(
                    Color.parseColor("#B71C1C"),
                    Color.parseColor("#FFCDD2")
                )
            }

            progressSignal.setIndicatorColor(color)
            progressSignal.trackColor = trackColor
        }

        private fun setupCardStyling(network: WifiNetwork) {
            // Highlight open (unsecured) networks
            if (network.isOpen()) {
                cardView.strokeWidth = 2
                cardView.strokeColor = Color.parseColor("#FF4444")
                cardView.elevation = 6f
            } else {
                cardView.strokeWidth = 0
                cardView.elevation = 4f
            }

            // Transparency based on signal strength
            val alpha = when (network.signalLevel) {
                SignalLevel.EXCELLENT, SignalLevel.GOOD -> 1.0f
                SignalLevel.FAIR -> 0.9f
                SignalLevel.WEAK -> 0.8f
                SignalLevel.VERY_WEAK -> 0.7f
            }
            cardView.alpha = alpha
        }
    }
}