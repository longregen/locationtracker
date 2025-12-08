# LocationTracker

Passive GPS location tracking for Android. Records where you've been without active polling.

## Highlights

- Piggybacks on other apps' location requests via Android's `PASSIVE_PROVIDER`
- Groups nearby coordinates (100m radius) into single locations
- Stores everything locally in SQLite — no network required
- Export to JSON

## How it Works

The app runs a foreground service that listens for location updates requested by other apps. When a new coordinate comes in, it either creates a new location entry or merges it with an existing one if within 100 meters (Haversine distance). Visit count, first visit, and last visit times are tracked for each location.

## Installation

Download the APK for your architecture from [Releases](../../releases):
- `arm64-v8a` — most modern phones
- `armeabi-v7a` — older 32-bit ARM devices
- `x86_64` — emulators, Chromebooks

Grant location and notification permissions when prompted.

## Building from Source

Requires JDK 17 and Android SDK 36.

```bash
./gradlew assembleDebug
```

APKs output to `app/build/outputs/apk/debug/`

## Technical Details

| | |
|---|---|
| Language | Kotlin |
| Min SDK | 26 (Android 8.0) |
| Target SDK | 36 |
| UI | XML layouts, Material 3 |
| Database | SQLite |

### Database Schema

```sql
CREATE TABLE locations (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    latitude REAL NOT NULL,
    longitude REAL NOT NULL,
    timestamp INTEGER NOT NULL,
    first_visit INTEGER NOT NULL,
    visit_count INTEGER DEFAULT 1
)
```

### Export Format

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

## Why These Choices

**Passive location provider** — No battery drain from active GPS polling. Updates come opportunistically when other apps request location.

**XML over Jetpack Compose** — Smaller APK size. The UI is simple enough that Compose adds unnecessary overhead.

**SQLite over Room** — Direct SQL keeps the APK smaller and provides explicit control over queries.

## License

Provided as-is for personal use.
