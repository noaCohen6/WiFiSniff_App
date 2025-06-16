package com.example.wifisniff

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.wifisniff.data.*
import com.example.wifisniff.utils.WifiUtils
import com.example.wifisniff.data.NetworkRisk
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.textview.MaterialTextView

class WifiScanActivity : AppCompatActivity(), OnMapReadyCallback {

    // UI Components
    private lateinit var btnScan: MaterialButton
    private lateinit var btnList: MaterialButton
    private lateinit var btnToggleCluster: MaterialButton
    private lateinit var fabMyLocation: FloatingActionButton
    private lateinit var tvNetworkCount: MaterialTextView
    private lateinit var tvSecureCount: MaterialTextView
    private lateinit var tvOpenCount: MaterialTextView
    private lateinit var progressIndicator: CircularProgressIndicator
    private lateinit var scanStatusCard: MaterialCardView
    private lateinit var tvScanStatus: MaterialTextView

    // Maps & Location
    private lateinit var googleMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var currentLocation: Location? = null
    private var userLocationMarker: Marker? = null
    private var scanRadiusCircle: Circle? = null

    // WiFi & Data - גישה פשוטה יותר
    private lateinit var wifiManager: WifiManager
    private val currentNetworks = mutableListOf<WifiNetwork>()
    private val networkClusters = mutableMapOf<String, NetworkCluster>()
    private val networkMarkers = mutableMapOf<String, Marker>()
    private val clusterMarkers = mutableMapOf<String, Marker>()
    private val coverageCircles = mutableListOf<Circle>()
    private val temporaryMarkers = mutableMapOf<String, Marker>()

    private var isScanning = false
    private var scanCount = 0
    private val maxScanRadius = 200.0
    private var showClustered = true

    private var isShowingIndividualFromCluster = false
    private var currentlyShownClusterSsid: String? = null

    // Handlers
    private val scanHandler = Handler(Looper.getMainLooper())
    private val autoScanRunnable = object : Runnable {
        override fun run() {
            if (isScanning) {
                performWifiScan()
                scanHandler.postDelayed(this, 30000)
            }
        }
    }

    // WiFi Scan Receiver
    private val wifiScanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiManager.SCAN_RESULTS_AVAILABLE_ACTION -> {
                    handleScanResults()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi_scan)

        initializeViews()
        setupToolbar()
        initializeServices()
        setupMapFragment()
        setupClickListeners()
        updateClusterButtonText()
    }

    private fun initializeViews() {
        btnScan = findViewById(R.id.btnScan)
        btnList = findViewById(R.id.btnList)
        btnToggleCluster = findViewById(R.id.btnToggleCluster)
        fabMyLocation = findViewById(R.id.fabMyLocation)
        tvNetworkCount = findViewById(R.id.tvNetworkCount)
        tvSecureCount = findViewById(R.id.tvSecureCount)
        tvOpenCount = findViewById(R.id.tvOpenCount)
        progressIndicator = findViewById(R.id.progressIndicator)
        scanStatusCard = findViewById(R.id.scanStatusCard)
        tvScanStatus = findViewById(R.id.tvScanStatus)
    }

    private fun setupToolbar() {
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "WiFi Scanner"

        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)?.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun initializeServices() {
        wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (!WifiUtils.isWifiEnabled(this)) {
            Toast.makeText(this, "Please enable WiFi for scanning", Toast.LENGTH_LONG).show()
        }
    }

    private fun setupMapFragment() {
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    private fun setupClickListeners() {
        btnScan.setOnClickListener {
            if (isScanning) {
                stopScanning()
            } else {
                startScanning()
            }
        }

        btnList.setOnClickListener {
            openNetworkList()
        }

        btnToggleCluster.setOnClickListener {
            toggleClusterMode()
        }

        fabMyLocation.setOnClickListener {
            moveToCurrentLocation()
        }
    }

    private fun updateClusterButtonText() {
        btnToggleCluster.text = when {
            isShowingIndividualFromCluster -> "Back to Clusters"
            showClustered -> "Individual"
            else -> "Cluster"
        }

        val colorRes = if (isShowingIndividualFromCluster) {
            android.R.color.holo_orange_dark
        } else {
            R.color.purple_500
        }
        btnToggleCluster.setBackgroundColor(ContextCompat.getColor(this, colorRes))
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        setupMap()
        getCurrentLocation()
    }

    @SuppressLint("MissingPermission")
    private fun setupMap() {
        try {
            googleMap.isMyLocationEnabled = true
            googleMap.uiSettings.isMyLocationButtonEnabled = false
            googleMap.uiSettings.isZoomControlsEnabled = true
            googleMap.uiSettings.isCompassEnabled = true

            googleMap.mapType = GoogleMap.MAP_TYPE_NORMAL

            googleMap.setOnMarkerClickListener { marker ->
                handleMarkerClick(marker)
                true
            }

        } catch (e: SecurityException) {
            Log.e("WifiScanActivity", "Location permission not granted", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun getCurrentLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {

            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    currentLocation = it
                    updateUserLocationOnMap(it)
                    moveToCurrentLocation()
                }
            }
        }
    }

    private fun updateUserLocationOnMap(location: Location) {
        val userLatLng = LatLng(location.latitude, location.longitude)

        userLocationMarker?.remove()
        userLocationMarker = googleMap.addMarker(
            MarkerOptions()
                .position(userLatLng)
                .title("Your Location")
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE))
        )

        scanRadiusCircle?.remove()
        scanRadiusCircle = googleMap.addCircle(
            CircleOptions()
                .center(userLatLng)
                .radius(maxScanRadius)
                .strokeColor(0x550000FF)
                .fillColor(0x220000FF)
                .strokeWidth(2f)
        )
    }

    private fun moveToCurrentLocation() {
        currentLocation?.let { location ->
            val userLatLng = LatLng(location.latitude, location.longitude)
            googleMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(userLatLng, 16f)
            )
        }
    }

    private fun startScanning() {
        if (!WifiUtils.isWifiEnabled(this)) {
            Toast.makeText(this, "Please enable WiFi first", Toast.LENGTH_SHORT).show()
            return
        }

        if (isShowingIndividualFromCluster) {
            returnToNormalView()
        }

        isScanning = true
        scanCount = 0

        clearAllData()

        updateScanUI(true)

        val intentFilter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        registerReceiver(wifiScanReceiver, intentFilter)

        scanHandler.post(autoScanRunnable)
        performWifiScan()

        Log.d("WifiScanActivity", "Started WiFi scanning - cleared all previous data")
    }

    private fun stopScanning() {
        isScanning = false
        scanHandler.removeCallbacks(autoScanRunnable)
        updateScanUI(false)

        try {
            unregisterReceiver(wifiScanReceiver)
        } catch (e: IllegalArgumentException) {
            Log.w("WifiScanActivity", "Receiver was not registered")
        }

        showFinalStatistics()
        Log.d("WifiScanActivity", "Stopped WiFi scanning")
    }

    @SuppressLint("MissingPermission")
    private fun performWifiScan() {
        if (!isScanning) return

        updateScanStatus("Scanning WiFi networks...")

        val success = wifiManager.startScan()
        if (!success) {
            Log.w("WifiScanActivity", "WiFi scan failed to start")
            updateScanStatus("Scan failed, retrying...")
        } else {
            scanCount++
            Log.d("WifiScanActivity", "WiFi scan started (scan #$scanCount)")
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleScanResults() {
        val scanResults = wifiManager.scanResults
        updateScanStatus("Processing ${scanResults.size} networks...")

        currentLocation?.let { userLocation ->
            currentNetworks.clear()
            Log.d("WifiScanActivity", "Cleared current networks list")

            clearAllNetworkMarkers()

            scanResults.forEach { scanResult ->
                val wifiNetwork = convertScanResultToWifiNetwork(scanResult, userLocation)
                currentNetworks.add(wifiNetwork)
                Log.d("WifiScanActivity", "Added network: ${wifiNetwork.ssid} (${wifiNetwork.bssid})")
            }

            Log.d("WifiScanActivity", "Total networks after scan: ${currentNetworks.size}")

            if (!isShowingIndividualFromCluster) {
                updateMapDisplay()
            }
        }

        performAdvancedAnalysis()
        updateStatistics()
        updateScanStatus("Found ${currentNetworks.size} networks")

        Handler(Looper.getMainLooper()).postDelayed({
            if (scanStatusCard.visibility == View.VISIBLE) {
                scanStatusCard.visibility = View.GONE
            }
        }, 2000)
    }


    private fun clearAllData() {
        currentNetworks.clear()
        networkClusters.clear()
        clearAllNetworkMarkers()
        isShowingIndividualFromCluster = false
        currentlyShownClusterSsid = null
        Log.d("WifiScanActivity", "Cleared all data completely")
    }


    private fun clearAllNetworkMarkers() {
        networkMarkers.values.forEach { marker ->
            marker.remove()
        }
        networkMarkers.clear()

        clusterMarkers.values.forEach { marker ->
            marker.remove()
        }
        clusterMarkers.clear()

        temporaryMarkers.values.forEach { marker ->
            marker.remove()
        }
        temporaryMarkers.clear()

        coverageCircles.forEach { circle ->
            circle.remove()
        }
        coverageCircles.clear()

        Log.d("WifiScanActivity", "Cleared all network markers from map")
    }


    private fun updateMapDisplay() {
        if (isShowingIndividualFromCluster) {
            Log.d("WifiScanActivity", "Skipping map update - in individual view mode")
            return
        }

        if (showClustered) {
            createNetworkClusters()
            addClusterMarkersToMap()
        } else {
            addIndividualNetworkMarkers()
        }

        Log.d("WifiScanActivity", "Updated map display - clustered: $showClustered")
    }


    private fun createNetworkClusters() {
        networkClusters.clear()
        val clustersMap = currentNetworks.groupBy { it.ssid }

        clustersMap.forEach { (ssid, networkList) ->
            if (networkList.isNotEmpty()) {
                val cluster = NetworkCluster(ssid, networkList)
                networkClusters[ssid] = cluster
            }
        }

        Log.d("WifiScanActivity", "Created ${networkClusters.size} network clusters from ${currentNetworks.size} networks")
    }


    private fun addClusterMarkersToMap() {
        networkClusters.values.forEach { cluster ->
            addClusterMarkerToMap(cluster)
        }
        Log.d("WifiScanActivity", "Added ${networkClusters.size} cluster markers to map")
    }


    private fun addClusterMarkerToMap(cluster: NetworkCluster) {
        val centerLocation = cluster.centerLocation ?: return

        val position = LatLng(centerLocation.latitude, centerLocation.longitude)

        val markerColor = when {
            cluster.isOpenNetwork -> BitmapDescriptorFactory.HUE_RED
            cluster.primarySecurityType == SecurityType.WEP -> BitmapDescriptorFactory.HUE_ORANGE
            cluster.primarySecurityType == SecurityType.WPA -> BitmapDescriptorFactory.HUE_YELLOW
            cluster.primarySecurityType == SecurityType.WPA2 -> BitmapDescriptorFactory.HUE_GREEN
            cluster.primarySecurityType == SecurityType.WPA3 -> BitmapDescriptorFactory.HUE_BLUE
            else -> BitmapDescriptorFactory.HUE_CYAN
        }

        val title = if (cluster.networkCount > 1) {
            "${cluster.ssid} (${cluster.networkCount} APs)"
        } else {
            cluster.ssid
        }

        val marker = googleMap.addMarker(
            MarkerOptions()
                .position(position)
                .title(title)
                .snippet(cluster.getMapSnippet())
                .icon(BitmapDescriptorFactory.defaultMarker(markerColor))
        )

        marker?.tag = cluster
        if (marker != null) {
            clusterMarkers[cluster.ssid] = marker

            if (cluster.networkCount > 1 && cluster.coverageRadius > 10) {
                val coverageCircle = googleMap.addCircle(
                    CircleOptions()
                        .center(position)
                        .radius(cluster.coverageRadius)
                        .strokeColor(0x33000000)
                        .fillColor(0x11000000)
                        .strokeWidth(1f)
                )
                coverageCircles.add(coverageCircle)
            }
        }
    }


    private fun addIndividualNetworkMarkers() {
        currentNetworks.forEach { network ->
            addNetworkMarkerToMap(network)
        }
        Log.d("WifiScanActivity", "Added ${currentNetworks.size} individual network markers to map")
    }


    private fun toggleClusterMode() {
        if (isShowingIndividualFromCluster) {
            returnToNormalView()
            return
        }

        showClustered = !showClustered

        clearAllNetworkMarkers()
        updateMapDisplay()

        val message = if (showClustered) "Showing clustered networks" else "Showing individual networks"
        showToast(message)

        updateStatistics()
        updateClusterButtonText()
    }

    private fun convertScanResultToWifiNetwork(scanResult: ScanResult, userLocation: Location): WifiNetwork {
        val estimatedDistance = WifiUtils.calculateDistance(scanResult.level, scanResult.frequency)
        val estimatedLocation = WifiUtils.calculateEstimatedLocation(userLocation, estimatedDistance)

        val securityType = SecurityType.fromCapabilities(scanResult.capabilities)

        return WifiNetwork(
            ssid = scanResult.SSID ?: "Hidden Network",
            bssid = scanResult.BSSID,
            rssi = scanResult.level,
            frequency = scanResult.frequency,
            capabilities = scanResult.capabilities,
            location = WifiLocation(
                latitude = estimatedLocation.first,
                longitude = estimatedLocation.second,
                accuracy = estimatedDistance.toFloat(),
                provider = "estimated"
            ),
            channel = WifiUtils.getChannelFromFrequency(scanResult.frequency),
            securityType = securityType,
            signalLevel = SignalLevel.fromRssi(scanResult.level)
        )
    }

    private fun performAdvancedAnalysis() {
        if (currentNetworks.isNotEmpty()) {
            val networkDensity = WifiUtils.calculateNetworkDensity(currentNetworks, maxScanRadius)
            val bestChannel = WifiUtils.findLeastCongestedChannel(currentNetworks)
            val bandGroups = currentNetworks.groupBy { WifiUtils.getWifiBand(it.frequency) }

            Log.d("WifiScanActivity", "=== Advanced Analysis ===")
            Log.d("WifiScanActivity", "Active networks: ${currentNetworks.size}")
            Log.d("WifiScanActivity", "Network density: ${String.format("%.1f", networkDensity)} networks/km²")
            Log.d("WifiScanActivity", "Recommended 2.4GHz channel: $bestChannel")
            Log.d("WifiScanActivity", "2.4GHz networks: ${bandGroups["2.4GHz"]?.size ?: 0}")
            Log.d("WifiScanActivity", "5GHz networks: ${bandGroups["5GHz"]?.size ?: 0}")
            Log.d("WifiScanActivity", "6GHz networks: ${bandGroups["6GHz"]?.size ?: 0}")
        }
    }

    private fun addNetworkMarkerToMap(network: WifiNetwork) {
        network.location?.let { location ->
            val position = LatLng(location.latitude, location.longitude)

            val markerColor = when (network.securityType) {
                SecurityType.OPEN -> BitmapDescriptorFactory.HUE_RED
                SecurityType.WEP -> BitmapDescriptorFactory.HUE_ORANGE
                SecurityType.WPA -> BitmapDescriptorFactory.HUE_YELLOW
                SecurityType.WPA2 -> BitmapDescriptorFactory.HUE_GREEN
                SecurityType.WPA3 -> BitmapDescriptorFactory.HUE_BLUE
                SecurityType.WPS -> BitmapDescriptorFactory.HUE_VIOLET
                else -> BitmapDescriptorFactory.HUE_CYAN
            }

            val band = WifiUtils.getWifiBand(network.frequency)
            val quality = WifiUtils.getSignalQuality(network.rssi)

            val marker = googleMap.addMarker(
                MarkerOptions()
                    .position(position)
                    .title(network.ssid)
                    .snippet("$band • ${network.securityType.name} • $quality")
                    .icon(BitmapDescriptorFactory.defaultMarker(markerColor))
            )

            marker?.tag = network
            if (marker != null) {
                networkMarkers[network.bssid] = marker
            }
        }
    }

    private fun handleMarkerClick(marker: Marker): Boolean {
        when (val tag = marker.tag) {
            is NetworkCluster -> {
                showClusterDetails(tag)
            }
            is WifiNetwork -> {
                showNetworkDetails(tag)
            }
        }
        return true
    }


    private fun showClusterDetails(cluster: NetworkCluster) {
        val details = cluster.getDetailedInfo()

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Network Cluster Information")
            .setMessage(details)
            .setPositiveButton("OK", null)
            .setNeutralButton("Share") { _, _ ->
                shareClusterInfo(cluster)
            }

        if (!isShowingIndividualFromCluster) {
            dialog.setNegativeButton("Show Individual") { _, _ ->
                showIndividualNetworksInCluster(cluster)
            }
        }

        dialog.show()
    }


    private fun showIndividualNetworksInCluster(cluster: NetworkCluster) {
        isShowingIndividualFromCluster = true
        currentlyShownClusterSsid = cluster.ssid

        clearAllNetworkMarkers()

        cluster.networks.forEach { network ->
            val marker = addTemporaryNetworkMarker(network)
            if (marker != null) {
                temporaryMarkers[network.bssid] = marker
            }
        }

        updateClusterButtonText()

        showToast("Showing individual networks for ${cluster.ssid}. Click 'Back to Clusters' to return.")

        Handler(Looper.getMainLooper()).postDelayed({
            if (isShowingIndividualFromCluster) {
                returnToNormalView()
            }
        }, 15000)
    }


    private fun addTemporaryNetworkMarker(network: WifiNetwork): Marker? {
        network.location?.let { location ->
            val position = LatLng(location.latitude, location.longitude)

            val markerColor = when (network.securityType) {
                SecurityType.OPEN -> BitmapDescriptorFactory.HUE_RED
                SecurityType.WEP -> BitmapDescriptorFactory.HUE_ORANGE
                SecurityType.WPA -> BitmapDescriptorFactory.HUE_YELLOW
                SecurityType.WPA2 -> BitmapDescriptorFactory.HUE_GREEN
                SecurityType.WPA3 -> BitmapDescriptorFactory.HUE_BLUE
                SecurityType.WPS -> BitmapDescriptorFactory.HUE_VIOLET
                else -> BitmapDescriptorFactory.HUE_CYAN
            }

            val band = WifiUtils.getWifiBand(network.frequency)
            val quality = WifiUtils.getSignalQuality(network.rssi)

            val marker = googleMap.addMarker(
                MarkerOptions()
                    .position(position)
                    .title("${network.ssid} [Individual View]")
                    .snippet("BSSID: ${network.bssid} • $band • ${network.securityType.name} • $quality")
                    .icon(BitmapDescriptorFactory.defaultMarker(markerColor))
            )

            marker?.tag = network
            return marker
        }
        return null
    }


    private fun returnToNormalView() {
        temporaryMarkers.values.forEach { marker ->
            marker.remove()
        }
        temporaryMarkers.clear()

        isShowingIndividualFromCluster = false
        currentlyShownClusterSsid = null

        updateMapDisplay()

        updateClusterButtonText()

        showToast("Returned to normal view")
    }


    private fun shareClusterInfo(cluster: NetworkCluster) {
        val shareText = buildString {
            appendLine("WiFi Network Cluster: ${cluster.ssid}")
            appendLine("Access Points: ${cluster.networkCount}")
            appendLine("Security Types: ${cluster.securityTypes.joinToString(", ") { it.name }}")
            appendLine("Risk Level: ${cluster.overallRisk.description}")
            appendLine("Signal Range: ${cluster.networks.minOf { it.rssi }} to ${cluster.networks.maxOf { it.rssi }} dBm")
            appendLine("Coverage Radius: ~${String.format("%.0f", cluster.coverageRadius)} m")
        }

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "WiFi Cluster: ${cluster.ssid}")
        }

        startActivity(Intent.createChooser(shareIntent, "Share Cluster Info"))
    }

    private fun showNetworkDetails(network: WifiNetwork) {
        val risk = WifiUtils.assessNetworkRisk(network)
        val band = WifiUtils.getWifiBand(network.frequency)
        val quality = WifiUtils.getSignalQuality(network.rssi)
        val percentage = WifiUtils.rssiToPercentage(network.rssi)
        val securityInfo = WifiUtils.parseSecurityCapabilities(network.capabilities)

        val details = buildString {
            appendLine("📱 Network Details")
            appendLine("━━━━━━━━━━━━━━━━━━━━")
            appendLine()
            appendLine("📶 SSID: ${network.ssid}")
            appendLine("📍 BSSID: ${network.bssid}")
            appendLine("🔒 Security: ${network.securityType.name}")
            appendLine("⚠️ Risk Level: ${risk.description}")
            appendLine("📊 Signal: ${network.rssi} dBm ($percentage%)")
            appendLine("📈 Quality: $quality")
            appendLine("📡 Frequency: ${network.frequency} MHz")
            appendLine("📺 Band: $band")
            appendLine("📋 Channel: ${network.channel}")
            appendLine("📏 Est. Distance: ~${String.format("%.1f", network.getEstimatedDistance())} m")
            appendLine()
            appendLine("🔐 Security Details:")
            appendLine("• Type: ${securityInfo.securityType.name}")
            appendLine("• WPS: ${if (securityInfo.hasWps) "Yes" else "No"}")
            if (securityInfo.encryptionMethods.isNotEmpty()) {
                appendLine("• Encryption: ${securityInfo.encryptionMethods.joinToString(", ")}")
            }
            if (securityInfo.authenticationMethods.isNotEmpty()) {
                appendLine("• Auth: ${securityInfo.authenticationMethods.joinToString(", ")}")
            }
            appendLine()
            appendLine("🛡️ Capabilities: ${network.capabilities}")

            network.location?.let { location ->
                appendLine()
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
        val risk = WifiUtils.assessNetworkRisk(network)
        val band = WifiUtils.getWifiBand(network.frequency)
        val quality = WifiUtils.getSignalQuality(network.rssi)

        val shareText = buildString {
            appendLine("WiFi Network Information:")
            appendLine("SSID: ${network.ssid}")
            appendLine("Security: ${network.securityType.name}")
            appendLine("Risk Level: ${risk.description}")
            appendLine("Signal: ${network.rssi} dBm ($quality)")
            appendLine("Band: $band")
            appendLine("Frequency: ${network.frequency} MHz")
            appendLine("Distance: ~${String.format("%.1f", network.getEstimatedDistance())} m")
        }

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "WiFi Network: ${network.ssid}")
        }

        startActivity(Intent.createChooser(shareIntent, "Share Network Info"))
    }


    private fun updateStatistics() {
        val stats = ScanResults(currentNetworks, null).securityStats

        tvNetworkCount.text = if (showClustered) {
            networkClusters.size.toString()
        } else {
            stats.totalNetworks.toString()
        }
        tvSecureCount.text = stats.secureNetworks.toString()
        tvOpenCount.text = stats.openNetworks.toString()
    }


    private fun showFinalStatistics() {
        if (currentNetworks.isNotEmpty()) {
            val networkDensity = WifiUtils.calculateNetworkDensity(currentNetworks, maxScanRadius)
            val bestChannel = WifiUtils.findLeastCongestedChannel(currentNetworks)
            val stats = ScanResults(currentNetworks, null).securityStats

            val message = buildString {
                appendLine("📊 Scan Complete!")
                appendLine("━━━━━━━━━━━━━━━━")
                appendLine("📶 Active Networks: ${stats.totalNetworks}")
                if (showClustered) {
                    appendLine("🏠 Network Groups: ${networkClusters.size}")
                }
                appendLine("🛡️ Secure: ${stats.secureNetworks}")
                appendLine("⚠️ Vulnerable: ${stats.vulnerableNetworks}")
                appendLine("📡 Density: ${String.format("%.1f", networkDensity)} networks/km²")
                appendLine("📺 Best 2.4GHz Channel: $bestChannel")
            }

            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }

    private fun updateScanUI(scanning: Boolean) {
        btnScan.text = if (scanning) "Stop" else "Scan"
        btnScan.icon = ContextCompat.getDrawable(
            this,
            if (scanning) R.drawable.ic_close else R.drawable.ic_refresh
        )

        if (scanning) {
            scanStatusCard.visibility = View.VISIBLE
        }
    }

    private fun updateScanStatus(status: String) {
        tvScanStatus.text = status
    }


    private fun openNetworkList() {
        val intent = Intent(this, WifiListActivity::class.java)
        intent.putExtra("networks", currentNetworks.toTypedArray())
        startActivity(intent)
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
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

    override fun onDestroy() {
        super.onDestroy()
        stopScanning()
    }

    override fun onPause() {
        super.onPause()
        // Scanning is not paused to allow scanning in the background
    }

    override fun onResume() {
        super.onResume()
        // Check if WiFi is still on
        if (!WifiUtils.isWifiEnabled(this) && isScanning) {
            stopScanning()
            Toast.makeText(this, "WiFi was disabled. Scanning stopped.", Toast.LENGTH_SHORT).show()
        }
    }
}