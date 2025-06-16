package com.example.wifisniff

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.widget.Toast
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.LocationSettingsStatusCodes
import com.google.android.gms.location.Priority
import com.google.android.gms.location.SettingsClient
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private lateinit var btnStart: MaterialButton

    private var hasUserInteracted = false
    private var lastWifiState = false

    // Activity Result Launchers
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d("MainActivity", "Permission granted")
            checkLocationPermissions()
        } else {
            Log.d("MainActivity", "Permission NOT granted")
            handlePermissionDenied()
        }
    }

    private val locationSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // Location settings satisfied, start scanning
            startScanning()
        } else {
            showToast("Location settings are required for scanning")
        }
    }

    private val appSettingsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // User returned from app settings, check permissions again
        checkLocationPermissions()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViews()
        initViews()

        lastWifiState = isWifiCurrentlyEnabled()
    }

    private fun findViews() {
        btnStart = findViewById(R.id.btnStart)
    }

    private fun initViews() {
        btnStart.setOnClickListener {
            hasUserInteracted = true
            checkLocationPermissions()
        }
    }

    private fun checkLocationPermissions() {
        when {
            !isLocationEnabled() -> {
                showLocationServiceDialog()
            }
            !hasAllLocationPermissions() -> {
                requestLocationPermissions()
            }
            !hasWifiPermissions() -> {
                requestWifiPermissions()
            }
            else -> {
                validateLocationSettings()
            }
        }
    }

    /**
     * Full check of all location permissions
     */
    private fun hasAllLocationPermissions(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocation = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        // Background location check only from Android 10 and above
        val backgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required in older versions
        }

        return fineLocation && coarseLocation && backgroundLocation
    }

    /**
     * Basic location permission check (without background)
     */
    private fun hasBasicLocationPermissions(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocation = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineLocation && coarseLocation
    }

    private fun hasWifiPermissions(): Boolean {
        val wifiState = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_WIFI_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val changeWifiState = ContextCompat.checkSelfPermission(
            this, Manifest.permission.CHANGE_WIFI_STATE
        ) == PackageManager.PERMISSION_GRANTED

        return wifiState && changeWifiState
    }

    private fun requestLocationPermissions() {
        val missingPermission = getMissingLocationPermission()
        if (missingPermission != null) {
            when {
                shouldShowRequestPermissionRationale(missingPermission) -> {
                    showPermissionRationaleDialog(missingPermission)
                }
                else -> {
                    requestPermissionLauncher.launch(missingPermission)
                }
            }
        }
    }

    private fun requestWifiPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                showToast("Fine location permission is required for WiFi scanning on Android 10+")
                requestPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                return
            }
        }
        checkLocationPermissions()
    }

    private fun getMissingLocationPermission(): String? {
        return when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED -> Manifest.permission.ACCESS_FINE_LOCATION

            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED -> Manifest.permission.ACCESS_COARSE_LOCATION

            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    != PackageManager.PERMISSION_GRANTED -> Manifest.permission.ACCESS_BACKGROUND_LOCATION

            else -> null
        }
    }

    private fun isLocationEnabled(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            locationManager.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            val mode = Settings.Secure.getInt(
                contentResolver,
                Settings.Secure.LOCATION_MODE,
                Settings.Secure.LOCATION_MODE_OFF
            )
            mode != Settings.Secure.LOCATION_MODE_OFF
        }
    }

    private fun showLocationServiceDialog() {
        AlertDialog.Builder(this)
            .setTitle("Enable Location Services")
            .setMessage("WiFi scanning requires location services to be enabled. Please turn on location services.")
            .setPositiveButton("Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showPermissionRationaleDialog(permission: String) {
        val message = when (permission) {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION ->
                "Location permission is required for WiFi scanning functionality. This allows the app to discover nearby WiFi networks and map their locations."

            Manifest.permission.ACCESS_BACKGROUND_LOCATION ->
                "Background location permission allows the app to collect WiFi data even when the app is in the background. This helps provide continuous network monitoring and better mapping accuracy."

            else -> "This permission is required for the app to function properly."
        }

        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage(message)
            .setPositiveButton("Grant") { _, _ ->
                if (permission == Manifest.permission.ACCESS_BACKGROUND_LOCATION &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    // Android 11+ requires manual background location setting
                    showBackgroundLocationManualDialog()
                } else {
                    requestPermissionLauncher.launch(permission)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     *Special message for background location on Android 11+
     */
    private fun showBackgroundLocationManualDialog() {
        val suffix = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            "Allow all the time"
        } else {
            "Allow"
        }

        AlertDialog.Builder(this)
            .setTitle("Background Location Permission")
            .setMessage("You need to enable background location permission manually.\n\nOn the page that opens:\n1. Click on PERMISSIONS\n2. Click on LOCATION\n3. Select '$suffix'")
            .setCancelable(false)
            .setPositiveButton("Open Settings") { _, _ ->
                openAppSettings()
            }
            .setNegativeButton("Skip") { _, _ ->
                // Continue without background location
                validateLocationSettings()
            }
            .show()
    }

    private fun handlePermissionDenied() {
        val missingPermission = getMissingLocationPermission() ?: return

        if (shouldShowRequestPermissionRationale(missingPermission)) {
            showToast("Location permission is required for WiFi scanning")

            Snackbar.make(
                findViewById(android.R.id.content),
                "Location permission is needed for WiFi scanning",
                Snackbar.LENGTH_LONG
            ).setAction("Try Again") {
                requestPermissionLauncher.launch(missingPermission)
            }.show()
        } else {
            if (missingPermission == Manifest.permission.ACCESS_BACKGROUND_LOCATION) {
                showBackgroundLocationManualDialog()
            } else {
                showToast("Please enable location permission in app settings")
                openAppSettings()
            }
        }
    }

    private fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        )
        appSettingsLauncher.launch(intent)
    }

    private fun validateLocationSettings() {
        val locationRequest = LocationRequest.Builder(5000)
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMinUpdateDistanceMeters(10.0f)
            .setMinUpdateIntervalMillis(5000)
            .build()

        val locationSettingsRequest = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true)
            .build()

        val settingsClient: SettingsClient = LocationServices.getSettingsClient(this)

        settingsClient.checkLocationSettings(locationSettingsRequest)
            .addOnSuccessListener {
                startScanning()
            }
            .addOnFailureListener { exception ->
                val statusCode = (exception as ApiException).statusCode
                when (statusCode) {
                    LocationSettingsStatusCodes.RESOLUTION_REQUIRED -> {
                        try {
                            val resolvable = exception as ResolvableApiException
                            val intentSenderRequest = IntentSenderRequest.Builder(resolvable.resolution).build()
                            locationSettingsLauncher.launch(intentSenderRequest)
                        } catch (e: Exception) {
                            Log.e("MainActivity", "Unable to execute request", e)
                            showToast("Unable to configure location settings")
                        }
                    }
                    LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE -> {
                        showToast("Location settings cannot be changed automatically. Please enable high accuracy location manually.")
                    }
                }
            }
    }

    private fun startScanning() {
        if (!hasBasicLocationPermissions()) {
            showToast("Basic location permissions are required")
            return
        }

        showToast("Starting WiFi Scanner...")

        // Navigate to WiFi scanning activity
        val intent = Intent(this, WifiScanActivity::class.java)
        startActivity(intent)

        Log.d("MainActivity", "All permissions granted, starting WiFi scan activity")
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()

        if (hasUserInteracted) {
            checkPermissionsAfterResume()
        }

        checkWifiStateChange()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus && hasUserInteracted) {
            Handler(Looper.getMainLooper()).postDelayed({
                performLightweightChecks()
            }, 500)
        }
    }


    private fun checkPermissionsAfterResume() {
        if (hasAllLocationPermissions() && hasWifiPermissions() && isLocationEnabled()) {
            updateUIForPermissionsGranted()
        } else {
            updateUIForMissingPermissions()
        }
    }


    private fun performLightweightChecks() {
        val isWifiEnabled = isWifiCurrentlyEnabled()
        if (isWifiEnabled != lastWifiState) {
            onWifiStateChanged(isWifiEnabled)
            lastWifiState = isWifiEnabled
        }

        if (!isLocationEnabled()) {
            showLocationDisabledHint()
        }
    }


    private fun checkWifiStateChange() {
        val currentWifiState = isWifiCurrentlyEnabled()
        if (currentWifiState != lastWifiState) {
            onWifiStateChanged(currentWifiState)
            lastWifiState = currentWifiState
        }
    }


    private fun onWifiStateChanged(isEnabled: Boolean) {
        if (isEnabled) {
            Log.d("MainActivity", "WiFi was enabled")
            if (hasAllLocationPermissions()) {
                showToast("WiFi enabled! Ready to scan.")
                updateScanButtonState(true)
            }
        } else {
            Log.d("MainActivity", "WiFi was disabled")
            showToast("WiFi disabled. Enable WiFi to scan networks.")
            updateScanButtonState(false)
        }
    }


    private fun updateUIForPermissionsGranted() {
        Log.d("MainActivity", "All permissions granted after resume")

        btnStart.text = "Start Scanning"
        btnStart.icon = ContextCompat.getDrawable(this, R.drawable.ic_refresh)

        if (isWifiCurrentlyEnabled()) {
            showToast("Ready to scan WiFi networks!")
        }
    }


    private fun updateUIForMissingPermissions() {
        Log.d("MainActivity", "Some permissions still missing after resume")

        btnStart.text = "Grant Permissions"
        btnStart.icon = ContextCompat.getDrawable(this, R.drawable.ic_close)
    }


    private fun showLocationDisabledHint() {
        Snackbar.make(
            findViewById(android.R.id.content),
            "Location services are disabled",
            Snackbar.LENGTH_SHORT
        ).setAction("Enable") {
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        }.show()
    }


    private fun updateScanButtonState(enabled: Boolean) {
        btnStart.isEnabled = enabled && hasAllLocationPermissions()

        btnStart.alpha = if (btnStart.isEnabled) 1.0f else 0.6f
    }


    private fun isWifiCurrentlyEnabled(): Boolean {
        return try {
            val wifiManager = getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiManager.isWifiEnabled
        } catch (e: Exception) {
            Log.e("MainActivity", "Error checking WiFi state", e)
            false
        }
    }
}