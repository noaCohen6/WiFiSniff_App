package com.example.wifisniff

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.wifisniff.adapters.WifiNetworkAdapter
import com.example.wifisniff.data.WifiNetwork
import com.example.wifisniff.data.SecurityType
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textview.MaterialTextView

class WifiListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: WifiNetworkAdapter
    private lateinit var chipGroup: ChipGroup
    private lateinit var tvEmptyState: MaterialTextView
    private lateinit var tvNetworkCount: MaterialTextView

    private var allNetworks = listOf<WifiNetwork>()
    private var filteredNetworks = listOf<WifiNetwork>()
    private var currentFilter: SecurityType? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi_list)

        setupToolbar()
        initializeViews()
        loadNetworks()
        setupRecyclerView()
        setupFilters()
    }

    private fun setupToolbar() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "WiFi Networks"

        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)?.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun initializeViews() {
        recyclerView = findViewById(R.id.recyclerView)
        chipGroup = findViewById(R.id.chipGroup)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        tvNetworkCount = findViewById(R.id.tvNetworkCount)
    }

    private fun loadNetworks() {
        val networksArray = intent.getSerializableExtra("networks") as? Array<WifiNetwork>
        allNetworks = networksArray?.toList() ?: emptyList()
        filteredNetworks = allNetworks.sortedByDescending { it.rssi }
        updateNetworkCount()
    }

    private fun setupRecyclerView() {
        adapter = WifiNetworkAdapter(filteredNetworks) { network ->
            showNetworkDetails(network)
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        updateEmptyState()
    }

    private fun setupFilters() {
        // Adding a chip to all networks
        addFilterChip("All", null, true)

        // Adding chips according to existing security types
        val securityTypes = allNetworks.map { it.securityType }.distinct().sorted()
        securityTypes.forEach { securityType ->
            val count = allNetworks.count { it.securityType == securityType }
            addFilterChip("${securityType.name} ($count)", securityType, false)
        }
    }

    private fun addFilterChip(text: String, securityType: SecurityType?, isChecked: Boolean) {
        val chip = Chip(this).apply {
            this.text = text
            isCheckable = true
            this.isChecked = isChecked

            setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    for (i in 0 until chipGroup.childCount) {
                        val otherChip = chipGroup.getChildAt(i) as? Chip
                        if (otherChip != this) {
                            otherChip?.isChecked = false
                        }
                    }

                    currentFilter = securityType
                    applyFilter()
                }
            }
        }

        chipGroup.addView(chip)
    }

    private fun applyFilter() {
        filteredNetworks = if (currentFilter == null) {
            allNetworks
        } else {
            allNetworks.filter { it.securityType == currentFilter }
        }.sortedByDescending { it.rssi }

        adapter.updateNetworks(filteredNetworks)
        updateNetworkCount()
        updateEmptyState()
    }

    private fun updateNetworkCount() {
        val total = allNetworks.size
        val showing = filteredNetworks.size

        tvNetworkCount.text = if (currentFilter == null) {
            "Showing $total networks"
        } else {
            "Showing $showing of $total networks"
        }
    }

    private fun updateEmptyState() {
        if (filteredNetworks.isEmpty()) {
            recyclerView.visibility = View.GONE
            tvEmptyState.visibility = View.VISIBLE
            tvEmptyState.text = if (allNetworks.isEmpty()) {
                "No networks found.\nTry scanning for WiFi networks."
            } else {
                "No networks match the current filter."
            }
        } else {
            recyclerView.visibility = View.VISIBLE
            tvEmptyState.visibility = View.GONE
        }
    }

    private fun showNetworkDetails(network: WifiNetwork) {
        val details = buildString {
            appendLine("Network Details")
            appendLine("━━━━━━━━━━━━━━━━━━━━")
            appendLine()
            appendLine("📶 SSID: ${network.ssid}")
            appendLine("📍 BSSID: ${network.bssid}")
            appendLine("🔒 Security: ${network.securityType.name}")
            appendLine("📊 Signal: ${network.rssi} dBm (${network.getSignalPercentage()}%)")
            appendLine("📡 Frequency: ${network.frequency} MHz")
            appendLine("📋 Channel: ${network.channel}")
            appendLine("📏 Est. Distance: ~${String.format("%.1f", network.getEstimatedDistance())} m")
            appendLine("🛡️ Capabilities: ${network.capabilities}")
            appendLine()
            appendLine("Signal Level: ${network.signalLevel.name}")

            network.location?.let { location ->
                appendLine("📍 Location: ${String.format("%.6f", location.latitude)}, ${String.format("%.6f", location.longitude)}")
                appendLine("🎯 Accuracy: ${location.accuracy} m")
            }
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Network Information")
            .setMessage(details)
            .setPositiveButton("OK", null)
            .setNeutralButton("Share") { _, _ ->
                shareNetworkInfo(network)
            }
            .show()
    }

    private fun shareNetworkInfo(network: WifiNetwork) {
        val shareText = buildString {
            appendLine("WiFi Network Information:")
            appendLine("SSID: ${network.ssid}")
            appendLine("Security: ${network.securityType.name}")
            appendLine("Signal: ${network.rssi} dBm")
            appendLine("Frequency: ${network.frequency} MHz")
            appendLine("Distance: ~${String.format("%.1f", network.getEstimatedDistance())} m")
        }

        val shareIntent = android.content.Intent().apply {
            action = android.content.Intent.ACTION_SEND
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, shareText)
            putExtra(android.content.Intent.EXTRA_SUBJECT, "WiFi Network: ${network.ssid}")
        }

        startActivity(android.content.Intent.createChooser(shareIntent, "Share Network Info"))
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}