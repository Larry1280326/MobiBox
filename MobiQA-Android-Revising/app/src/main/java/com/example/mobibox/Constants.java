package com.example.mobibox;

/**
 * Centralized constants for the MobiBox application.
 * All API URLs, SharedPreferences keys, file paths, and intervals are defined here.
 */
public final class Constants {

    private Constants() {
        // Prevent instantiation
    }

    // =====================
    // API Configuration
    // =====================

    /** Base API host URL */
    public static final String API_HOST = "http://120.25.178.24:8001";

    // API Endpoints
    public static final String ENDPOINT_GET_INTERVENTION = "/get_intervention";
    public static final String ENDPOINT_SEND_INTERVENTION_FEEDBACK = "/send_intervention_feedback";
    public static final String ENDPOINT_GET_SUMMARY_LOG = "/get_summary_log";
    public static final String ENDPOINT_REGISTER = "/register";
    public static final String ENDPOINT_UPLOAD_DOCUMENTS = "/upload/documents";
    public static final String ENDPOINT_UPLOAD_IMU = "/upload/imu";
    public static final String ENDPOINT_GET_ATOMIC_ACTIVITIES = "/get_compressed_atomic_activities";
    public static final String ENDPOINT_SEND_LOG_FEEDBACK = "/send_log_feedback";

    // Full URL getters
    public static String getInterventionUrl() {
        return API_HOST + ENDPOINT_GET_INTERVENTION;
    }

    public static String getSummaryLogUrl() {
        return API_HOST + ENDPOINT_GET_SUMMARY_LOG;
    }

    public static String getSensorUploadUrl() {
        return API_HOST + ENDPOINT_UPLOAD_DOCUMENTS;
    }

    public static String getImuUploadUrl() {
        return API_HOST + ENDPOINT_UPLOAD_IMU;
    }

    public static String getAtomicActivitiesUrl() {
        return API_HOST + ENDPOINT_GET_ATOMIC_ACTIVITIES;
    }

    public static String getRegisterUrl() {
        return API_HOST + ENDPOINT_REGISTER;
    }

    public static String getSendInterventionFeedbackUrl() {
        return API_HOST + ENDPOINT_SEND_INTERVENTION_FEEDBACK;
    }

    public static String getSendLogFeedbackUrl() {
        return API_HOST + ENDPOINT_SEND_LOG_FEEDBACK;
    }

    // =====================
    // SharedPreferences
    // =====================

    // SharedPreferences File Names
    public static final String PREFS_APP = "AppPrefs";
    public static final String PREFS_INTERVENTION = "InterventionPrefs";
    public static final String PREFS_INTERVENTION_CACHE = "InterventionCache";
    public static final String PREFS_DAILY_LOG = "DailyLogPrefs";
    public static final String PREFS_DAILY_LOG_CACHE = "DailyLogCache";
    public static final String PREFS_HOURLY_UPDATE_CACHE = "HourlyUpdateCache";
    public static final String PREFS_ATOMIC_ACTIVITIES = "AtomicActivitiesPrefs";

    // AppPrefs Keys
    public static final String KEY_USER_ID = "userId";

    // InterventionPrefs Keys
    public static final String KEY_LAST_INTERVENTION = "last_intervention";
    public static final String KEY_INTERVENTION_START_TIME = "intervention_start_time";
    public static final String KEY_INTERVENTION_END_TIME = "intervention_end_time";
    public static final String KEY_LAST_INTERVENTION_TIMESTAMP = "last_intervention_timestamp";
    public static final String KEY_LAST_HOURLY_LOG = "last_hourly_log";
    public static final String KEY_LAST_HOURLY_LOG_START_TIME = "last_hourly_log_start_time";
    public static final String KEY_LAST_HOURLY_LOG_END_TIME = "last_hourly_log_end_time";
    public static final String KEY_LAST_ATOMIC_ACTIVITIES = "last_atomic_activities";

    // InterventionCache Keys
    public static final String KEY_LAST_INTERVENTION_JSON = "last_intervention_json";

    // DailyLogPrefs Keys
    public static final String KEY_LAST_DAILY_LOG = "last_daily_log";
    public static final String KEY_DAILY_LOG_DATE = "daily_log_date";
    public static final String KEY_DAILY_LOG_START_TIME = "daily_log_start_time";
    public static final String KEY_DAILY_LOG_END_TIME = "daily_log_end_time";
    public static final String KEY_DAILY_LOG_ID = "daily_log_id";
    public static final String KEY_LAST_FETCH_TIMESTAMP = "last_fetch_timestamp";

    // DailyLogCache Keys
    public static final String KEY_LAST_DAILY_LOG_JSON = "last_daily_log_json";

    // HourlyUpdateCache Keys
    public static final String KEY_CACHED_HOURLY_LOG = "cached_hourly_log";
    public static final String KEY_LAST_HOURLY_LOG_ID = "last_hourly_log_id";

    // AtomicActivitiesPrefs Keys
    public static final String KEY_LAST_ATOMIC_ACTIVITIES_DATA = "last_atomic_activities";

    // =====================
    // File Paths
    // =====================

    /** Base directory for data files */
    public static final String DATA_DIR = "0.Mobibox";

    /** Sensor data CSV file name */
    public static final String FILE_SENSOR_CSV = "sensor.csv";

    /** IMU data CSV file name */
    public static final String FILE_IMU_CSV = "IMU.csv";

    /** App names text file */
    public static final String FILE_APP_NAMES = "app_names.txt";

    /** Full path for sensor data */
    public static String getSensorFilePath() {
        return DATA_DIR + "/" + FILE_SENSOR_CSV;
    }

    /** Full path for IMU data */
    public static String getImuFilePath() {
        return DATA_DIR + "/" + FILE_IMU_CSV;
    }

    /** Full path for app names */
    public static String getAppNamesFilePath() {
        return DATA_DIR + "/" + FILE_APP_NAMES;
    }

    // =====================
    // Intervals (milliseconds)
    // =====================

    /** Intervention check interval: 5 minutes */
    public static final long INTERVENTION_CHECK_INTERVAL_MS = 5 * 60 * 1000;

    /** Sensor status check interval: 30 seconds */
    public static final long SENSOR_CHECK_INTERVAL_MS = 30 * 1000;

    /** Step sensor refresh interval: 60 seconds */
    public static final long STEP_SENSOR_REFRESH_INTERVAL_MS = 60 * 1000;

    /** Bluetooth scan interval: 2 minutes */
    public static final long BLUETOOTH_SCAN_INTERVAL_MS = 2 * 60 * 1000;

    /** Bluetooth scan duration: 15 seconds */
    public static final long BLUETOOTH_SCAN_DURATION_MS = 15 * 1000;

    /** Toast cooldown to prevent spam: 60 seconds */
    public static final long TOAST_COOLDOWN_MS = 60 * 1000;

    /** Data collection write interval: 10 seconds */
    public static final long DATA_WRITE_INTERVAL_MS = 10 * 1000;

    /** IMU write interval for 50Hz: 20ms (matches backend HAR model) */
    public static final long IMU_WRITE_INTERVAL_MS = 20;

    /** IMU upload interval: upload sealed one-minute segments instead of active file */
    public static final long IMU_UPLOAD_INTERVAL_MS = 60 * 1000;

    // =====================
    // Time Constants (nanoseconds)
    // =====================

    /** Time window for sensor data synchronization: 50ms */
    public static final int TIME_WINDOW_NS = 50_000_000;

    /** Maximum data points per sensor collection cycle */
    public static final int MAX_DATA_PER_CYCLE = 200;

    // =====================
    // File Size Limits
    // =====================

    /** Maximum file size before rotation: 5MB */
    public static final long MAX_FILE_SIZE_BYTES = 5 * 1024 * 1024;

    // =====================
    // Notification Channels
    // =====================

    /** Channel ID for intervention notifications */
    public static final String CHANNEL_INTERVENTION = "INTERVENTION_CHANNEL";

    /** Channel ID for daily log notifications */
    public static final String CHANNEL_DAILY_LOG = "DAILY_LOG_CHANNEL";

    /** Channel ID for hourly update notifications */
    public static final String CHANNEL_HOURLY_UPDATE = "hourly_update_channel";

    /** Channel ID for service notifications */
    public static final String CHANNEL_SERVICE = "SERVICE_CHANNEL";

    /** Channel ID for foreground service */
    public static final String CHANNEL_FOREGROUND = "Foreground_Channel";

    // Notification IDs
    public static final int NOTIFICATION_ID_FOREGROUND = 23;
    public static final int NOTIFICATION_ID_UPLOAD_ERROR = 2001;
    public static final int NOTIFICATION_ID_DAILY_LOG = 2000;

    // =====================
    // Network Timeouts (seconds)
    // =====================

    /** Connection timeout: 15 seconds */
    public static final int NETWORK_CONNECT_TIMEOUT_SEC = 15;

    // =====================
    // IMU Upload Configuration
    // =====================

    /** Read timeout: 20 seconds */
    public static final int NETWORK_READ_TIMEOUT_SEC = 20;

    /** Write timeout: 20 seconds */
    public static final int NETWORK_WRITE_TIMEOUT_SEC = 20;

    /** Call timeout: 30 seconds */
    public static final int NETWORK_CALL_TIMEOUT_SEC = 30;

    // =====================
    // Sensor Configuration
    // =====================

    /** Sensor delay for IMU sensors (microseconds) - 50Hz (matches backend HAR model) */
    public static final int SENSOR_DELAY_IMU_US = 20_000;

    /** Sensor delay for step counter */
    public static final int SENSOR_DELAY_STEP_COUNTER = android.hardware.SensorManager.SENSOR_DELAY_NORMAL;

    // =====================
    // Upload Configuration
    // =====================

    /** IMU upload chunk size (rows per request) */
    public static final int IMU_UPLOAD_CHUNK_SIZE = 1000;

    /** Maximum retry attempts for pending uploads */
    public static final int MAX_UPLOAD_RETRIES = 5;

    // =====================
    // Location Configuration
    // =====================

    /** Location update interval: 1 second */
    public static final long LOCATION_UPDATE_INTERVAL_MS = 1000;

    /** Location update minimum distance: 0 meters */
    public static final float LOCATION_UPDATE_MIN_DISTANCE = 0;

    // =====================
    // Dummy Location Data (when Baidu Map SDK is disabled)
    // =====================

    /** Dummy latitude for Hong Kong */
    public static final double DUMMY_LATITUDE = 22.3193;

    /** Dummy longitude for Hong Kong */
    public static final double DUMMY_LONGITUDE = 114.1694;

    /** Dummy address string */
    public static final String DUMMY_ADDRESS = "Dummy Address";

    /** Dummy POI string */
    public static final String DUMMY_POI = "Dummy POI";
}
