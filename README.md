# WiFi Scanner Android App

A comprehensive Android application for scanning, analyzing, and mapping nearby WiFi networks with advanced security analysis and geographic visualization.

## 📱 Features

- **Real-time WiFi Network Scanning** - Continuous scanning and detection of nearby WiFi networks
- **Interactive Google Maps Integration** - Visual representation of WiFi networks on a map
- **Security Analysis** - Detailed security assessment and risk evaluation
- **Network Clustering** - Group networks by SSID for better visualization
- **Advanced Analytics** - Network density, signal analysis, and channel recommendations
- **Location-based Detection** - Estimate network locations based on signal strength
- **Comprehensive Network Details** - Complete information about each network

## 🏗️ Project Structure

### Core Activity Files

#### `MainActivity.kt`
**Purpose**: Entry point and permission management
- Handles all location and WiFi permissions requests
- Manages Android 10+ background location permission flow
- Validates location services and WiFi state
- Guides users through permission setup process
- Implements proper permission request patterns for different Android versions

**Key Features**:
- Progressive permission requesting (fine → coarse → background location)
- Location services validation
- Permission rationale dialogs
- Automatic navigation to app settings when needed

#### `WifiScanActivity.kt`
**Purpose**: Main scanning interface with Google Maps integration
- Real-time WiFi network scanning and processing
- Interactive Google Maps with network visualization
- Network clustering and individual view modes
- Advanced signal analysis and location estimation
- Continuous scanning with configurable intervals

**Key Features**:
- **Scanning Modes**: Start/stop continuous scanning
- **View Modes**: Toggle between clustered and individual network views
- **Map Integration**: Interactive markers with network information
- **Real-time Updates**: Live network detection and map updates
- **Advanced Analysis**: Network density, channel congestion analysis

#### `WifiListActivity.kt`
**Purpose**: List view of discovered networks with filtering
- Comprehensive list of all discovered WiFi networks
- Security-based filtering (All, Open, WEP, WPA, WPA2, WPA3, WPS)
- Detailed network information dialogs
- Network sharing capabilities
- Signal strength visualization

**Key Features**:
- **Smart Filtering**: Filter networks by security type
- **Sorting**: Networks sorted by signal strength (RSSI)
- **Network Details**: Complete information popup for each network
- **Share Functionality**: Export network information

### Data Models

#### `WifiNetwork.kt`
**Purpose**: Core network data structure
- Represents a single WiFi access point
- Includes SSID, BSSID, security info, signal strength, location
- Provides calculated properties (distance estimation, signal percentage)
- Security analysis methods

**Key Properties**:
```kotlin
data class WifiNetwork(
    val ssid: String,           // Network name
    val bssid: String,          // MAC address
    val rssi: Int,              // Signal strength in dBm
    val frequency: Int,         // Frequency in MHz
    val capabilities: String,   // Security capabilities
    val location: WifiLocation?, // Estimated location
    val securityType: SecurityType,
    val signalLevel: SignalLevel
)
```

#### `NetworkCluster.kt`
**Purpose**: Groups multiple access points with same SSID
- Represents networks with multiple access points
- Calculates center location and coverage radius
- Provides security analysis for the entire cluster
- Generates detailed cluster information

**Features**:
- **Smart Clustering**: Groups APs by SSID
- **Location Calculation**: Weighted center location based on signal strength
- **Coverage Analysis**: Calculates coverage radius
- **Security Assessment**: Overall security evaluation

#### `SecurityType.kt`
**Purpose**: WiFi security classification
```kotlin
enum class SecurityType {
    OPEN,     // No security
    WEP,      // Weak encryption
    WPA,      // Moderate security
    WPA2,     // Good security
    WPA3,     // Excellent security
    WPS,      // Setup protocol
    UNKNOWN   // Unknown security
}
```

#### `SignalLevel.kt`
**Purpose**: Signal strength classification
```kotlin
enum class SignalLevel {
    EXCELLENT, // > -50 dBm
    GOOD,      // -50 to -60 dBm
    FAIR,      // -60 to -70 dBm
    WEAK,      // -70 to -80 dBm
    VERY_WEAK  // < -80 dBm
}
```

#### `WifiLocation.kt`
**Purpose**: Geographic location data
- Stores latitude, longitude, accuracy
- Provides distance calculation methods
- Supports location estimation algorithms

#### `ScanResults.kt`
**Purpose**: Container for scan session data
- Aggregates all networks found in scanning session
- Provides statistical analysis and security metrics
- Includes utility methods for range and signal filtering (currently unused in UI)
- Calculates unique networks and security statistics

#### `SecurityStats.kt`
**Purpose**: Security statistics aggregation
- Counts networks by security type
- Calculates secure vs vulnerable ratios
- Provides comprehensive security overview

#### `SecurityInfo.kt`
**Purpose**: Detailed security information
- Advanced security capability parsing
- Encryption and authentication method details
- WPS support detection

#### `NetworkRisk.kt`
**Purpose**: Risk level classification
```kotlin
enum class NetworkRisk {
    VERY_LOW("Very Secure", "#0066CC"),
    LOW("Secure", "#00AA00"),
    MEDIUM("Moderate Risk", "#FFBB33"),
    HIGH("High Risk", "#FF4444"),
    UNKNOWN("Unknown", "#808080")
}
```

### Utilities

#### `WifiUtils.kt`
**Purpose**: Comprehensive WiFi analysis utilities
- **Distance Calculation**: Multiple algorithms for distance estimation
- **Signal Analysis**: RSSI to percentage conversion, quality assessment
- **Channel Analysis**: Frequency/channel conversion, congestion detection
- **Security Analysis**: Advanced capability parsing, risk assessment
- **Network Analysis**: Density calculation, band detection

**Key Methods**:
- `calculateDistance()` - RSSI-based distance estimation
- `parseSecurityCapabilities()` - Advanced security parsing
- `assessNetworkRisk()` - Security risk evaluation
- `findLeastCongestedChannel()` - Channel recommendation
- `calculateNetworkDensity()` - Area network density

### Adapter

#### `WifiNetworkAdapter.kt`
**Purpose**: RecyclerView adapter for network list
- Displays network information in cards
- Color-coded security indicators
- Signal strength progress bars
- Visual styling based on security level

**Features**:
- **Security Color Coding**: Visual security level indication
- **Signal Visualization**: Progress bars with color coding
- **Network Cards**: Material Design cards with comprehensive info
- **Click Handling**: Navigation to detailed network information

## 🔐 Required Permissions

### Manifest Permissions

```xml
<!-- Essential WiFi Permissions -->
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />

<!-- Location Permissions (Required for WiFi scanning on Android 6+) -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- Background Location (Android 10+ for background scanning) -->
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />

<!-- Internet for Google Maps -->
<uses-permission android:name="android.permission.INTERNET" />
```

### Permission Requirements by Android Version

#### Android 6.0+ (API 23+)
- **Location permissions required** for WiFi scanning
- Runtime permission requests implemented
- Users must grant location access to scan WiFi

#### Android 10+ (API 29+)
- **Fine location required** for WiFi scan results
- Background location permission separated
- Manual permission granting for background location

#### Android 11+ (API 30+)
- **Background location must be granted manually** in settings
- App guides users to settings for background permission
- "Allow all the time" option required for background scanning

## 🚀 How It Works

### 1. App Initialization
- User launches app and clicks "Start"
- App checks all required permissions progressively
- Validates location services are enabled
- Ensures WiFi is available and enabled

### 2. Scanning Process
- **Continuous Scanning**: Scans every 30 seconds when active
- **Signal Processing**: Converts scan results to WifiNetwork objects
- **Location Estimation**: Calculates approximate network locations using signal strength
- **Real-time Updates**: Updates map and statistics continuously

### 3. Data Processing
- **Network Creation**: Each scan result becomes a WifiNetwork object
- **Security Analysis**: Parses capabilities string for security details
- **Distance Calculation**: Uses RSSI and frequency for distance estimation
- **Location Mapping**: Estimates network locations relative to user position

### 4. Visualization
- **Map Markers**: Color-coded markers based on security level
- **Clustering**: Groups networks by SSID for cleaner visualization
- **Coverage Circles**: Shows estimated coverage areas
- **Interactive UI**: Click markers for detailed information

### 5. Advanced Features
- **Network Analysis**: Density calculation, channel congestion
- **Security Assessment**: Risk level evaluation for each network
- **Filtering**: View networks by security type
- **Export**: Share network information via standard Android sharing

## 🎯 Key Algorithms

### Distance Estimation
```kotlin
fun calculateDistance(rssi: Int, frequency: Int): Double {
    val ratio = (27.55 - (20 * log10(frequency.toDouble())) + abs(rssi)) / 20.0
    return 10.0.pow(ratio)
}
```

### Location Estimation
- Uses signal strength to estimate distance
- Applies random angle for approximate location
- Accounts for signal propagation characteristics

### Security Risk Assessment
- Analyzes encryption methods and authentication
- Considers WPS vulnerability
- Evaluates overall network security posture

## 📊 Network Analysis

### Signal Quality Assessment
- **Excellent**: > -50 dBm
- **Good**: -50 to -60 dBm  
- **Fair**: -60 to -70 dBm
- **Weak**: -70 to -80 dBm
- **Very Weak**: < -80 dBm

### Security Classification
- **Open Networks**: No encryption (High Risk)
- **WEP**: Weak encryption (High Risk)
- **WPA**: Moderate security (Medium Risk)
- **WPA2**: Good security (Low Risk)
- **WPA3**: Excellent security (Very Low Risk)

### Channel Analysis
- Identifies 2.4GHz, 5GHz, and 6GHz bands
- Calculates channel congestion
- Recommends optimal channels (1, 6, 11 for 2.4GHz)

## 🛠️ Technical Implementation

### Architecture
- **MVVM Pattern**: Clean separation of concerns
- **Material Design**: Modern Android UI components
- **Google Maps SDK**: Interactive mapping capabilities
- **Location Services**: GPS and network location

### Key Technologies
- **Android WiFi Manager**: Core WiFi scanning functionality
- **Google Maps**: Interactive network visualization
- **FusedLocationProviderClient**: Accurate location services
- **Material Design Components**: Modern UI elements

### Performance Optimizations
- **Efficient Scanning**: Configurable scan intervals
- **Memory Management**: Proper cleanup of map markers and listeners
- **Background Handling**: Manages permissions for background operation

## 🔧 Setup Instructions

1. **Clone the repository**
2. **Add Google Maps API key** to your `google_services.json`
3. **Build and install** the app
4. **Grant permissions** when prompted
5. **Enable location services** for accurate results
6. **Start scanning** and explore nearby networks!

## 📝 Usage Tips

- **Indoor Scanning**: Works best in areas with multiple WiFi networks
- **Permission Setup**: Allow "all the time" location access for best results
- **Map Navigation**: Use clustering mode for areas with many networks
- **Security Analysis**: Pay attention to open networks and security warnings
- **Channel Planning**: Use channel recommendations for setting up your own networks

## 🔒 Privacy & Security

- **No Network Passwords**: App only detects publicly broadcast information
- **Local Processing**: All analysis performed on device
- **No Data Collection**: Network information not transmitted externally
- **Permission Transparency**: Clear explanation of why each permission is needed

This WiFi Scanner app provides a comprehensive tool for network analysis, security assessment, and wireless environment mapping while maintaining user privacy and following Android best practices.