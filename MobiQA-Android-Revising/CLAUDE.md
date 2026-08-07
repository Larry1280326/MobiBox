# CLAUDE.md

## Project Overview

**MobiBox** is an Android research data-collection application for a behavioral monitoring study at The Hong Kong University of Science and Technology (HKUST). It continuously collects sensor data (IMU at 50Hz, GPS via Baidu Maps SDK, Bluetooth LE, WiFi, battery, app usage, step counter) and uploads it to a backend server. In return, it receives AI-generated hourly summaries and behavioral intervention suggestions, then collects multi-question user feedback on both.

- **Package:** `com.example.mobibox`
- **Language:** Java 17
- **Min SDK:** 26 | **Target SDK:** 35 | **Compile SDK:** 35
- **Build:** Gradle 8.7 / AGP 8.6.0
- **Backend:** `http://120.25.178.24:8000` (defined in `Constants.java`)

## Build & Run

```bash
# Build the project
./gradlew assembleDebug

# Run tests
./gradlew test
./gradlew connectedAndroidTest
```

The app requires Android 8.0+ (API 26). It needs numerous permissions (location, Bluetooth, notifications, storage, usage access, etc.) granted at first launch via `AuthorizationActivity`.

## Architecture

```
Activity Layer
├── AuthorizationActivity   → First-launch: user ID entry + permission grants
├── MainActivity             → Dashboard: start/stop data collection, live sensor display
├── InterventionActivity     → View hourly logs + interventions, submit dual feedback, edit atomic activities
└── DailyLogActivity         → View daily summaries, submit rating + text feedback

Background Service
└── DataService (~3400 lines) → Foreground service: all sensor collection, file I/O, uploads,
                                  intervention/log/atomic-activity polling every 5 minutes

Data Layer
├── FileRepository           → Singleton: CSV file I/O management
├── SharedPreferencesHelper  → Singleton: typed access to 7 preference files
└── UploadQueueManager       → Persistent queue for failed uploads (retry on network recovery)

Network Layer
├── HttpApiClient            → Singleton OkHttp wrapper for all API calls
└── NetworkReceiver          → BroadcastReceiver: triggers pending upload retry on connectivity

Managers
├── BluetoothScanner         → BLE scanning with OUI-based vendor identification
├── SensorDataManager        → Extracted sensor management (not yet wired into DataService)
├── InterventionNotificationManager → Intervention dedup + notification
├── HourlyUpdateNotificationManager  → Hourly log dedup + notification
└── DailyLogNotificationManager      → Daily log dedup + notification

Workers (WorkManager)
├── DailyLogWorker           → Scheduled daily at 8 PM China time
└── HourlyUpdateWorker       → Superseded by DataService's 5-min polling

Utilities
├── TimeUtils                → UTC → Hong Kong timezone formatting
├── Constants                → All config: API URLs, intervals, keys, limits
├── AtomicAdapter            → RecyclerView adapter for atomic activity tags (drag-to-reorder)
└── ItemMoveCallback         → ItemTouchHelper for drag-and-drop
```

## Key Data Flows

### Data Collection → Upload
1. Sensors registered on background `HandlerThread` at 50Hz (IMU) / normal (step counter)
2. `imuWriterRunnable` writes IMU data to `IMU.csv` every 20ms (50Hz ticker)
3. `writeData()` writes consolidated sensor data to `sensor.csv` every 10 seconds
4. **Seal-and-upload:** Active CSV periodically renamed to `*_upload_*.csv`, new file created, sealed file uploaded as JSON `{"items": [...]}`, deleted on success, queued for retry on failure

### Intervention/Log Polling
1. Every 5 minutes, `DataService` fetches Hourly Log, Intervention, and Atomic Activities in parallel (30s timeout)
2. Notification managers compare against cached content
3. If new, system notification sent + content cached in SharedPreferences

### Offline Retry
1. Failed upload → `UploadQueueManager` saves to backup file + JSON queue in SharedPreferences
2. `NetworkReceiver` detects connectivity → triggers `processPendingUploads()`
3. Max 100 queued items, 5 retries each

## Important Conventions

- **All API endpoints** are defined in `Constants.java` with getter methods
- **Time formatting** goes through `TimeUtils.formatUtcToHKTime()` for UTC→HKT display
- **Timestamp format** for CSV data is China timezone ISO 8601: `yyyy-MM-dd'T'HH:mm:ss.SSSXXX`
- **File operations** should use `FileRepository` singleton rather than direct File I/O
- **SharedPreferences** should use `SharedPreferencesHelper` singleton rather than direct access
- **Uploads** use the reusable `OkHttpClient` from `DataService.getUploadHttpClient()`, not per-call instances
- **User-facing strings** should be in English (toast messages, dialog text, UI labels)
- **Error handling** for API calls: check status field in JSON response before processing data

## Key Dependencies

- **OkHttp 4.12.0** — HTTP client
- **Volley 1.2.1** — Legacy HTTP (used minimally)
- **Gson 2.10.1** — JSON serialization
- **Guava 31.1-android** — Utilities
- **WorkManager 2.8.0** — Periodic background tasks
- **Apache Commons CSV 1.9.0** — CSV parsing
- **FlexboxLayout 3.0.0** — Horizontal wrapping RecyclerView layouts
- **Baidu LBS SDK** — GPS/location services (JAR + native .so in `app/libs/`)
- **Health Connect 1.1.0-alpha11** — Health data integration

## Notable Configuration

| Setting | Value |
|---------|-------|
| Data directory | `0.Mobibox/` on external storage |
| IMU sampling rate | 50Hz (20ms interval) |
| Sensor data write interval | 10 seconds |
| IMU upload interval | 60 seconds |
| IMU upload chunk size | 1000 rows |
| Intervention poll interval | 5 minutes |
| Bluetooth scan interval | 2 minutes (15s scan) |
| Max file size before rotation | 5 MB |
| Max upload retries | 5 |
| Max upload queue size | 100 |
| Dummy location (Baidu SDK fallback) | 22.3193, 114.1694 (Hong Kong) |
