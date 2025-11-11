# LocationTracker

A lightweight Android app for opportunistic GPS location tracking with minimal battery drain.

## Features

- **Opportunistic GPS Tracking**: Uses Android's PASSIVE_PROVIDER to piggyback on other apps' location requests
- **Local SQLite Storage**: All data stored locally, no internet required
- **Smart Location Grouping**: Coordinates within 100m radius are grouped together
- **GPS Status Indicator**: Shows last GPS update time
- **Time Spent Tracking**: Calculates duration at each location
- **Last 10 Locations**: Displays recent grouped locations with visit counts
- **JSON Export**: Export all location data with timestamps
- **Map Integration**: Open locations in external map apps
- **Material 3 Design**: Modern UI with dark/light theme support
- **Battery Optimized**: Minimal battery drain through passive location updates

## Technical Stack

- **Language**: Kotlin 2.2.21
- **UI**: XML layouts with Material 3 (Material Design Components)
- **Database**: SQLite with custom helper
- **Min SDK**: 26 (Android 8.0)
- **Target SDK**: 35 (Android 15)
- **Architecture**: MVVM-lite with View Binding

## Build

### Prerequisites

- JDK 17
- Android SDK with API 35
- Gradle 8.11.1 (included via wrapper)

### Building APKs

```bash
./gradlew assembleDebug
```

This generates platform-specific APKs for:
- `armeabi-v7a` - 32-bit ARM devices
- `arm64-v8a` - 64-bit ARM devices (most modern phones)
- `x86_64` - Intel/AMD 64-bit (emulators, Chromebooks)
- `universal` - All platforms combined

APKs will be in `app/build/outputs/apk/debug/`

### Release Build

```bash
./gradlew assembleRelease
```

## Installation

1. Download the appropriate APK for your device architecture
2. Enable "Install from Unknown Sources" in Android Settings
3. Install the APK
4. Grant location and notification permissions when prompted

## Permissions

- `ACCESS_FINE_LOCATION` - For precise GPS coordinates
- `ACCESS_COARSE_LOCATION` - For approximate location
- `FOREGROUND_SERVICE` - For background location tracking
- `FOREGROUND_SERVICE_LOCATION` - Required for location service type
- `POST_NOTIFICATIONS` - For Android 13+ notification support

## Architecture

### Components

- **MainActivity**: Main UI with RecyclerView for locations
- **LocationService**: Foreground service for passive location updates
- **DatabaseHelper**: SQLite operations with location grouping logic
- **LocationAdapter**: RecyclerView adapter with DiffUtil
- **LocationData**: Data class for location entities

### Location Grouping Algorithm

Locations are grouped using the Haversine formula:
- Radius: 100 meters
- New locations within radius update existing entry
- Tracks first visit, last visit, and visit count
- Calculates time spent (last visit - first visit)

### Database Schema

```sql
CREATE TABLE locations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    latitude REAL NOT NULL,
    longitude REAL NOT NULL,
    timestamp INTEGER NOT NULL,
    first_visit INTEGER DEFAULT 0,
    visit_count INTEGER DEFAULT 1
)
```

## JSON Export Format

```json
[
  {
    "latitude": 37.7749,
    "longitude": -122.4194,
    "timestamp": 1699876543210,
    "first_visit": 1699870000000,
    "visit_count": 5,
    "time_spent_ms": 6543210
  }
]
```

## APK Size

- Size: ~5.6 MB per variant
- Optimizations:
  - English resources only
  - Platform-specific builds
  - No Jetpack Compose (uses XML layouts)
  - Material 3 components only

## Development Decisions

### Why XML Layouts Instead of Jetpack Compose?

- **Size**: XML + Material 3 = 5.6 MB vs Compose = 17-19 MB
- **Simplicity**: Straightforward UI doesn't need Compose complexity
- **Battery**: Lighter framework = less overhead

### Why Passive Location Provider?

- **Battery**: No active GPS polling
- **Opportunistic**: Uses location updates from other apps
- **Sufficient**: For tracking visited places, not real-time navigation

### Why SQLite Instead of Room?

- **Size**: Room adds ~1 MB to APK
- **Simplicity**: Direct SQL is sufficient for this use case
- **Control**: Fine-grained control over queries and migrations

## CI/CD

GitHub Actions workflows:

### Build Workflow
Automatically builds APKs on push:
- Builds for armeabi-v7a, arm64-v8a, x86_64
- Uploads artifacts for download
- Runs on Ubuntu latest with JDK 17

### E2E Test Workflow
Automated end-to-end testing on Android emulator:
- Runs on Android API 30 and API 33 emulators (x86_64)
- Tests location tracking with mock GPS coordinates
- Simulates multiple locations (London → Paris → London)
- Validates UI interactions (export JSON, map integration)
- Captures screenshots at each test step
- Uploads test results and screenshots as artifacts
- Tests both older Android (pre-POST_NOTIFICATIONS) and newer Android (with POST_NOTIFICATIONS)

The E2E test validates:
1. App launch and permissions
2. Location tracking with London coordinates (51.5074, -0.1278)
3. Location change to Paris (48.8566, 2.3522)
4. Location change back to London
5. Export JSON functionality
6. Map integration (geo intent)

## License

This project is provided as-is for educational and personal use.

## Version History

### v1.0.0 (2024-11-11)
- Initial release
- Opportunistic GPS tracking
- Location grouping (100m radius)
- SQLite storage
- GPS status indicator
- Time spent tracking
- JSON export
- Map integration
- Material 3 UI
