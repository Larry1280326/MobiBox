# MobiBox Android Frontend

Android mobile application for the MobiBox health monitoring system.

## Features

- **Sensor Data Collection**: Collects IMU, GPS, Bluetooth, and app usage data
- **Health Interventions**: Receives and displays personalized health interventions
- **Activity Summaries**: Shows hourly and daily activity summaries
- **Polling Mechanism**: Efficiently detects new content without unnecessary downloads

## Project Structure

```
MobiQA-Android/
├── app/src/main/java/com/example/mobibox/
│   ├── Constants.java              # API URLs, SharedPreferences keys, intervals
│   ├── MainActivity.java           # Main activity
│   ├── AuthorizationActivity.java  # User registration
│   ├── DailyLogActivity.java       # Daily log display
│   ├── InterventionActivity.java   # Intervention display
│   ├── network/
│   │   └── HttpApiClient.java      # Centralized HTTP client
│   ├── data/
│   │   ├── FileRepository.java     # File storage
│   │   └── SharedPreferencesHelper.java  # Preferences management
│   ├── util/
│   │   └── TimeUtils.java          # Timestamp formatting utilities
│   ├── managers/
│   │   ├── SensorDataManager.java  # Sensor data collection
│   │   ├── BluetoothScanner.java   # Bluetooth scanning
│   │   └── notification/           # Notification managers
│   ├── workers/
│   │   ├── HourlyUpdateWorker.java # Hourly background task
│   │   └── DailyLogWorker.java      # Daily background task
│   ├── service/
│   │   └── DataService.java        # Foreground service
│   └── ui/adapters/                # RecyclerView adapters
└── app/build/                      # Build outputs
```

## API Integration

### HttpApiClient

Centralized HTTP client for all API requests:

```java
// Get singleton instance
HttpApiClient client = HttpApiClient.getInstance();

// Get hourly log (backward compatible)
String log = client.getHourlyLog();

// Get hourly log with polling support (new)
String log = client.getHourlyLog(lastLogId);
```

### Polling Mechanism

The app uses a polling mechanism to efficiently detect new content:

#### How It Works

1. **First Request**: Call API without `last_log_id`
   ```java
   String response = client.getHourlyLog();  // last_log_id = -1
   // Returns: {"status": "success", "data": {...}, "has_new_log": true}
   ```

2. **Store ID**: Save the returned log ID
   ```java
   JSONObject response = new JSONObject(result);
   JSONObject data = response.getJSONObject("data");
   int logId = data.getInt("id");
   prefsHelper.setLastHourlyLogId(logId);
   ```

3. **Poll for Updates**: Use the stored ID for subsequent requests
   ```java
   int lastLogId = prefsHelper.getLastHourlyLogId();
   String response = client.getHourlyLog(lastLogId);
   // Returns: {"status": "success", "data": null, "has_new_log": false}
   // No new content, skip notification
   ```

4. **Process New Content**: Only when `has_new_log` is `true`
   ```java
   if (response.has("has_new_log") && response.getBoolean("has_new_log")) {
       // Process and show notification
   }
   ```

### SharedPreferences Keys

| Key | Description |
|-----|-------------|
| `last_hourly_log_id` | Last received hourly log ID (for polling) |
| `last_daily_log_id` | Last received daily log ID |
| `last_intervention` | Last intervention content |
| `last_intervention_json` | Cached intervention JSON |

## Background Tasks

### HourlyUpdateWorker

Runs hourly to fetch new logs and interventions:

```java
public Result doWork() {
    // 1. Get last log ID from SharedPreferences
    int lastLogId = prefsHelper.getLastHourlyLogId();

    // 2. Poll API with last_log_id
    String response = client.getHourlyLog(lastLogId);

    // 3. Check if new content available
    JSONObject json = new JSONObject(response);
    if (json.getBoolean("has_new_log")) {
        // 4. Extract and store new log ID
        int newLogId = json.getJSONObject("data").getInt("id");
        prefsHelper.setLastHourlyLogId(newLogId);

        // 5. Show notification
        notificationManager.handleNewHourlyLog(response);
    }

    return Result.success();
}
```

### DailyLogWorker

Runs daily to fetch daily summaries (similar pattern).

## Key Classes

### TimeUtils

Timestamp formatting utility for converting UTC to Hong Kong timezone:

```java
// Convert UTC timestamp to Hong Kong time (UTC+8)
String hkTime = TimeUtils.formatUtcToHKTime("2026-03-07T15:01:24.29Z");
// Returns: "2026-03-07 23:01"

// Handles multiple ISO 8601 formats:
// - "2026-03-07T15:01:24.29Z" (with milliseconds)
// - "2026-03-07T15:01:24Z" (without milliseconds)
// - "2026-03-08T00:52:00+08:00" (with timezone offset)
```

### SharedPreferencesHelper

Centralized preferences management:

```java
SharedPreferencesHelper prefs = SharedPreferencesHelper.getInstance(context);

// Hourly log polling
int lastId = prefs.getLastHourlyLogId();      // Get stored ID
prefs.setLastHourlyLogId(123);                 // Store new ID

// Daily log polling
int dailyId = prefs.getDailyLogId();
prefs.setDailyLogId(456);

// User data
String userId = prefs.getUserId();
prefs.setUserId("user123");
```

### Constants

Centralized constants for API URLs and configuration:

```java
// API Endpoints
String ENDPOINT_GET_SUMMARY_LOG = "/get_summary_log";
String ENDPOINT_GET_INTERVENTION = "/get_intervention";

// SharedPreferences Keys
String KEY_LAST_HOURLY_LOG_ID = "last_hourly_log_id";
String KEY_LAST_DAILY_LOG_ID = "daily_log_id";

// Network Timeouts (seconds)
int NETWORK_CONNECT_TIMEOUT_SEC = 15;
int NETWORK_READ_TIMEOUT_SEC = 20;
```

## API Endpoints

### Summary Logs

```java
// Request with polling support
POST /get_summary_log
{
  "user": "username",
  "log_type": "hourly",      // or "daily"
  "last_log_id": 123         // optional, for polling
}

// Response
{
  "status": "success",
  "data": {
    "id": 124,
    "log_content": "...",
    "start_timestamp": "2024-01-01T00:00:00Z",
    "end_timestamp": "2024-01-01T01:00:00Z",
    "generation_timestamp": "2024-01-01T01:05:00Z"
  },
  "has_new_log": true      // false if last_log_id matches latest
}
```

### Interventions

```java
// Request
POST /get_intervention
{
  "user": "username"
}

// Response
{
  "status": "success",
  "data": {
    "id": 1,
    "intervention_content": "...",
    "start_timestamp": "...",
    "end_timestamp": "...",
    "generation_timestamp": "..."
  }
}
```

## Data Flow

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  Mobile App     │     │  Backend API    │     │   Supabase      │
│                 │     │                 │     │   Database      │
│ ┌─────────────┐ │     │ ┌─────────────┐ │     │                 │
│ │ Hourly      │ │────▶│ │ /get_      │ │────▶│ summary_logs    │
│ │ UpdateWorker│ │     │ │ summary_log │ │     │ interventions   │
│ └─────────────┘ │     │ └─────────────┘ │     │ atomic_activities│
│                 │◀────│                 │◀────│                 │
│ ┌─────────────┐ │     │ ┌─────────────┐ │     │                 │
│ │ SharedPrefs │ │     │ │ JSON       │ │     │                 │
│ │ (last_log_id)│ │     │ │ Response   │ │     │                 │
│ └─────────────┘ │     │ └─────────────┘ │     │                 │
└─────────────────┘     └─────────────────┘     └─────────────────┘
```

## Notification System

### HourlyUpdateNotificationManager

Handles hourly log notifications:

```java
public void handleNewHourlyLog(String json) {
    JSONObject response = new JSONObject(json);
    JSONObject data = response.getJSONObject("data");

    String content = data.getString("log_content");
    String startTime = data.getString("start_timestamp");
    String endTime = data.getString("end_timestamp");

    // Show notification
    showNotification("Activity Summary", content);
}
```

### InterventionNotificationManager

Handles intervention notifications with deduplication:

```java
public void handleNewIntervention(String json) {
    // Check if already shown (cached)
    String cached = prefsHelper.getLastInterventionJson();
    if (json.equals(cached)) {
        return;  // Skip duplicate
    }

    // Cache and show
    prefsHelper.setLastInterventionJson(json);
    showNotification("Health Tip", interventionContent);
}
```

## Configuration

### Environment Setup

1. Clone the repository
2. Open in Android Studio
3. Update `Constants.API_HOST` with your backend URL:
   ```java
   public static final String API_HOST = "http://your-server:8000";
   ```

### Build Variants

- **Debug**: Full logging, test server
- **Release**: Minimal logging, production server

## Testing

### Unit Tests

```bash
./gradlew test
```

### Instrumented Tests

```bash
./gradlew connectedAndroidTest
```

## Dependencies

| Dependency | Version | Purpose |
|------------|---------|---------|
| OkHttp | 4.x | HTTP client |
| WorkManager | 2.x | Background tasks |
| SharedPreferences | - | Local storage |
| AndroidX | - | UI components |

## Troubleshooting

### Common Issues

1. **No logs appearing**: Check SharedPreferences for `last_hourly_log_id` value
2. **Duplicate notifications**: Ensure intervention JSON is being cached
3. **API errors**: Check `NETWORK_*_TIMEOUT_SEC` in Constants.java

### Debug Logging

Enable verbose logging:
```java
// In HourlyUpdateWorker
Log.d(TAG, "Last hourly log ID: " + lastLogId);
Log.d(TAG, "Response: " + response);
Log.d(TAG, "has_new_log: " + hasNewLog);
```

## Recent Changes

### Version 1.2.0 - Hong Kong Timezone Display

- Added `TimeUtils.formatUtcToHKTime()` utility method for timestamp conversion
- All timestamps from backend (stored in UTC) are now displayed in Hong Kong timezone (UTC+8)
- Updated `InterventionActivity` to show time range in HK time
- Updated `DailyLogActivity` to show log timestamps in HK time
- Supports multiple ISO 8601 timestamp formats (with/without milliseconds, with timezone offset)

### Version 1.1.0 - Polling Mechanism

- Added `last_log_id` parameter to `/get_summary_log` API
- Added `has_new_log` field to response
- Updated `HourlyUpdateWorker` to use polling
- Added `SharedPreferencesHelper.getLastHourlyLogId()` and `setLastHourlyLogId()`
- Added `HttpApiClient.getHourlyLog(int lastLogId)` overload

### Migration Guide

If upgrading from an older version:

1. Clear SharedPreferences (or app data) to initialize `last_hourly_log_id` to `-1`
2. First API call will return the latest log and its ID
3. Subsequent calls will only return new content

## License

[Your License Here]