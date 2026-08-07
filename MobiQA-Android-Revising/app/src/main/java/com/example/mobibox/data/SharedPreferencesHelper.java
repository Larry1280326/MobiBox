package com.example.mobibox.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.mobibox.Constants;

/**
 * Centralized helper for SharedPreferences access.
 * Provides type-safe getters and setters for all preference operations.
 */
public final class SharedPreferencesHelper {
    private static final String TAG = "SharedPreferencesHelper";

    private final Context context;

    // Singleton instance
    private static volatile SharedPreferencesHelper instance;

    /**
     * Get the singleton instance of SharedPreferencesHelper.
     * @param context Application context
     * @return The singleton instance
     */
    public static SharedPreferencesHelper getInstance(Context context) {
        if (instance == null) {
            synchronized (SharedPreferencesHelper.class) {
                if (instance == null) {
                    instance = new SharedPreferencesHelper(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private SharedPreferencesHelper(Context context) {
        this.context = context;
    }

    // =====================
    // Private Helper Methods
    // =====================

    private SharedPreferences getPrefs(String prefsName) {
        return context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
    }

    private SharedPreferences.Editor getEditor(String prefsName) {
        return getPrefs(prefsName).edit();
    }

    // =====================
    // AppPrefs Operations
    // =====================

    /**
     * Get the user ID from AppPrefs.
     * @return The user ID, or empty string if not set
     */
    public String getUserId() {
        return getPrefs(Constants.PREFS_APP).getString(Constants.KEY_USER_ID, "");
    }

    /**
     * Set the user ID in AppPrefs.
     * @param userId The user ID to set
     */
    public void setUserId(String userId) {
        getEditor(Constants.PREFS_APP)
                .putString(Constants.KEY_USER_ID, userId)
                .apply();
    }

    // =====================
    // InterventionPrefs Operations
    // =====================

    /**
     * Get the last intervention content.
     * @return The last intervention content, or empty string if not set
     */
    public String getLastIntervention() {
        return getPrefs(Constants.PREFS_INTERVENTION).getString(Constants.KEY_LAST_INTERVENTION, "");
    }

    /**
     * Set the last intervention content.
     * @param content The intervention content
     */
    public void setLastIntervention(String content) {
        getEditor(Constants.PREFS_INTERVENTION)
                .putString(Constants.KEY_LAST_INTERVENTION, content)
                .apply();
    }

    /**
     * Get the intervention start time.
     * @return The start time string, or empty string if not set
     */
    public String getInterventionStartTime() {
        return getPrefs(Constants.PREFS_INTERVENTION).getString(Constants.KEY_INTERVENTION_START_TIME, "");
    }

    /**
     * Set the intervention start time.
     * @param startTime The start time string
     */
    public void setInterventionStartTime(String startTime) {
        getEditor(Constants.PREFS_INTERVENTION)
                .putString(Constants.KEY_INTERVENTION_START_TIME, startTime)
                .apply();
    }

    /**
     * Get the intervention end time.
     * @return The end time string, or empty string if not set
     */
    public String getInterventionEndTime() {
        return getPrefs(Constants.PREFS_INTERVENTION).getString(Constants.KEY_INTERVENTION_END_TIME, "");
    }

    /**
     * Set the intervention end time.
     * @param endTime The end time string
     */
    public void setInterventionEndTime(String endTime) {
        getEditor(Constants.PREFS_INTERVENTION)
                .putString(Constants.KEY_INTERVENTION_END_TIME, endTime)
                .apply();
    }

    /**
     * Get the last intervention timestamp.
     * @return The timestamp in milliseconds, or 0 if not set
     */
    public long getLastInterventionTimestamp() {
        return getPrefs(Constants.PREFS_INTERVENTION).getLong(Constants.KEY_LAST_INTERVENTION_TIMESTAMP, 0);
    }

    /**
     * Set the last intervention timestamp.
     * @param timestamp The timestamp in milliseconds
     */
    public void setLastInterventionTimestamp(long timestamp) {
        getEditor(Constants.PREFS_INTERVENTION)
                .putLong(Constants.KEY_LAST_INTERVENTION_TIMESTAMP, timestamp)
                .apply();
    }

    /**
     * Save intervention data (content, start time, end time, timestamp) atomically.
     * @param content The intervention content
     * @param startTime The start time
     * @param endTime The end time
     */
    public void saveInterventionData(String content, String startTime, String endTime) {
        getEditor(Constants.PREFS_INTERVENTION)
                .putString(Constants.KEY_LAST_INTERVENTION, content)
                .putString(Constants.KEY_INTERVENTION_START_TIME, startTime)
                .putString(Constants.KEY_INTERVENTION_END_TIME, endTime)
                .putLong(Constants.KEY_LAST_INTERVENTION_TIMESTAMP, System.currentTimeMillis())
                .apply();
        Log.d(TAG, "Intervention data saved successfully");
    }

    // =====================
    // InterventionCache Operations
    // =====================

    /**
     * Get the last intervention JSON from cache.
     * @return The cached JSON, or null if not set
     */
    public String getLastInterventionJson() {
        return getPrefs(Constants.PREFS_INTERVENTION_CACHE).getString(Constants.KEY_LAST_INTERVENTION_JSON, null);
    }

    /**
     * Set the last intervention JSON in cache.
     * @param json The JSON string to cache
     */
    public void setLastInterventionJson(String json) {
        getEditor(Constants.PREFS_INTERVENTION_CACHE)
                .putString(Constants.KEY_LAST_INTERVENTION_JSON, json)
                .apply();
    }

    /**
     * Synchronously set the last intervention JSON in cache.
     * @param json The JSON string to cache
     * @return true if saved successfully
     */
    public boolean setLastInterventionJsonSync(String json) {
        return getEditor(Constants.PREFS_INTERVENTION_CACHE)
                .putString(Constants.KEY_LAST_INTERVENTION_JSON, json)
                .commit();
    }

    /**
     * Clear the intervention cache.
     */
    public void clearInterventionCache() {
        getEditor(Constants.PREFS_INTERVENTION_CACHE)
                .remove(Constants.KEY_LAST_INTERVENTION_JSON)
                .apply();
    }

    // =====================
    // DailyLogPrefs Operations
    // =====================

    /**
     * Get the last daily log content.
     * @return The daily log content, or empty string if not set
     */
    public String getLastDailyLog() {
        return getPrefs(Constants.PREFS_DAILY_LOG).getString(Constants.KEY_LAST_DAILY_LOG, "");
    }

    /**
     * Set the last daily log content.
     * @param content The daily log content
     */
    public void setLastDailyLog(String content) {
        getEditor(Constants.PREFS_DAILY_LOG)
                .putString(Constants.KEY_LAST_DAILY_LOG, content)
                .apply();
    }

    /**
     * Get the daily log date.
     * @return The date string, or empty string if not set
     */
    public String getDailyLogDate() {
        return getPrefs(Constants.PREFS_DAILY_LOG).getString(Constants.KEY_DAILY_LOG_DATE, "");
    }

    /**
     * Set the daily log date.
     * @param date The date string
     */
    public void setDailyLogDate(String date) {
        getEditor(Constants.PREFS_DAILY_LOG)
                .putString(Constants.KEY_DAILY_LOG_DATE, date)
                .apply();
    }

    /**
     * Get the daily log start time.
     * @return The start time string, or empty string if not set
     */
    public String getDailyLogStartTime() {
        return getPrefs(Constants.PREFS_DAILY_LOG).getString(Constants.KEY_DAILY_LOG_START_TIME, "");
    }

    /**
     * Set the daily log start time.
     * @param startTime The start time string
     */
    public void setDailyLogStartTime(String startTime) {
        getEditor(Constants.PREFS_DAILY_LOG)
                .putString(Constants.KEY_DAILY_LOG_START_TIME, startTime)
                .apply();
    }

    /**
     * Get the daily log end time.
     * @return The end time string, or empty string if not set
     */
    public String getDailyLogEndTime() {
        return getPrefs(Constants.PREFS_DAILY_LOG).getString(Constants.KEY_DAILY_LOG_END_TIME, "");
    }

    /**
     * Set the daily log end time.
     * @param endTime The end time string
     */
    public void setDailyLogEndTime(String endTime) {
        getEditor(Constants.PREFS_DAILY_LOG)
                .putString(Constants.KEY_DAILY_LOG_END_TIME, endTime)
                .apply();
    }

    /**
     * Get the daily log ID.
     * @return The log ObjectId string, or empty string if not set
     */
    public String getDailyLogId() {
        return getPrefs(Constants.PREFS_DAILY_LOG).getString(Constants.KEY_DAILY_LOG_ID, "");
    }

    /**
     * Set the daily log ID.
     * @param id The log ObjectId string
     */
    public void setDailyLogId(String id) {
        getEditor(Constants.PREFS_DAILY_LOG)
                .putString(Constants.KEY_DAILY_LOG_ID, id)
                .apply();
    }

    /**
     * Save daily log data atomically.
     * @param content The daily log content
     * @param date The date
     * @param startTime The start time
     * @param endTime The end time
     */
    public void saveDailyLogData(String content, String date, String startTime, String endTime) {
        getEditor(Constants.PREFS_DAILY_LOG)
                .putString(Constants.KEY_LAST_DAILY_LOG, content)
                .putString(Constants.KEY_DAILY_LOG_DATE, date)
                .putString(Constants.KEY_DAILY_LOG_START_TIME, startTime)
                .putString(Constants.KEY_DAILY_LOG_END_TIME, endTime)
                .putLong(Constants.KEY_LAST_FETCH_TIMESTAMP, System.currentTimeMillis())
                .apply();
        Log.d(TAG, "Daily log data saved successfully");
    }

    /**
     * Clear all daily log preferences.
     */
    public void clearDailyLogPrefs() {
        getEditor(Constants.PREFS_DAILY_LOG).clear().apply();
        getEditor(Constants.PREFS_DAILY_LOG_CACHE).clear().apply();
        Log.d(TAG, "Daily log preferences cleared");
    }

    // =====================
    // DailyLogCache Operations
    // =====================

    /**
     * Get the last daily log JSON from cache.
     * @return The cached JSON, or null if not set
     */
    public String getLastDailyLogJson() {
        return getPrefs(Constants.PREFS_DAILY_LOG_CACHE).getString(Constants.KEY_LAST_DAILY_LOG_JSON, null);
    }

    /**
     * Set the last daily log JSON in cache.
     * @param json The JSON string to cache
     */
    public void setLastDailyLogJson(String json) {
        getEditor(Constants.PREFS_DAILY_LOG_CACHE)
                .putString(Constants.KEY_LAST_DAILY_LOG_JSON, json)
                .apply();
    }

    // =====================
    // HourlyUpdateCache Operations
    // =====================

    /**
     * Get the cached hourly log JSON.
     * @return The cached JSON, or null if not set
     */
    public String getCachedHourlyLog() {
        return getPrefs(Constants.PREFS_HOURLY_UPDATE_CACHE).getString(Constants.KEY_CACHED_HOURLY_LOG, null);
    }

    /**
     * Set the cached hourly log JSON.
     * @param json The JSON string to cache
     */
    public void setCachedHourlyLog(String json) {
        getEditor(Constants.PREFS_HOURLY_UPDATE_CACHE)
                .putString(Constants.KEY_CACHED_HOURLY_LOG, json)
                .apply();
    }

    /**
     * Get the last hourly log ID (for polling mechanism).
     * @return The log ObjectId string, or empty string if not set
     */
    public String getLastHourlyLogId() {
        return getPrefs(Constants.PREFS_HOURLY_UPDATE_CACHE).getString(Constants.KEY_LAST_HOURLY_LOG_ID, "");
    }

    /**
     * Set the last hourly log ID (for polling mechanism).
     * @param id The log ObjectId string
     */
    public void setLastHourlyLogId(String id) {
        getEditor(Constants.PREFS_HOURLY_UPDATE_CACHE)
                .putString(Constants.KEY_LAST_HOURLY_LOG_ID, id)
                .apply();
    }

    // =====================
    // AtomicActivitiesPrefs Operations
    // =====================

    /**
     * Get the last atomic activities data.
     * @return The cached activities JSON, or null if not set
     */
    public String getLastAtomicActivities() {
        return getPrefs(Constants.PREFS_ATOMIC_ACTIVITIES).getString(Constants.KEY_LAST_ATOMIC_ACTIVITIES_DATA, null);
    }

    /**
     * Set the last atomic activities data.
     * @param activities The activities JSON string
     */
    public void setLastAtomicActivities(String activities) {
        getEditor(Constants.PREFS_ATOMIC_ACTIVITIES)
                .putString(Constants.KEY_LAST_ATOMIC_ACTIVITIES_DATA, activities)
                .apply();
    }

    /**
     * Get the last atomic activities fetch timestamp.
     * @return The timestamp in milliseconds, or 0 if not set
     */
    public long getLastAtomicActivitiesFetchTime() {
        return getPrefs(Constants.PREFS_ATOMIC_ACTIVITIES).getLong(Constants.KEY_LAST_FETCH_TIMESTAMP, 0);
    }

    /**
     * Set the last atomic activities fetch timestamp.
     * @param timestamp The timestamp in milliseconds
     */
    public void setLastAtomicActivitiesFetchTime(long timestamp) {
        getEditor(Constants.PREFS_ATOMIC_ACTIVITIES)
                .putLong(Constants.KEY_LAST_FETCH_TIMESTAMP, timestamp)
                .apply();
    }

    /**
     * Reset the atomic activities fetch timestamp to 0.
     */
    public void resetAtomicActivitiesFetchTime() {
        setLastAtomicActivitiesFetchTime(0);
        Log.d(TAG, "Atomic activities timestamp reset to 0");
    }

    // =====================
    // Utility Methods
    // =====================

    /**
     * Clear all preferences for a specific preferences file.
     * @param prefsName The preferences file name
     */
    public void clearPrefs(String prefsName) {
        getEditor(prefsName).clear().apply();
        Log.d(TAG, "Cleared preferences: " + prefsName);
    }

    /**
     * Clear all application preferences.
     */
    public void clearAllPrefs() {
        clearPrefs(Constants.PREFS_APP);
        clearPrefs(Constants.PREFS_INTERVENTION);
        clearPrefs(Constants.PREFS_INTERVENTION_CACHE);
        clearPrefs(Constants.PREFS_DAILY_LOG);
        clearPrefs(Constants.PREFS_DAILY_LOG_CACHE);
        clearPrefs(Constants.PREFS_HOURLY_UPDATE_CACHE);
        clearPrefs(Constants.PREFS_ATOMIC_ACTIVITIES);
        Log.d(TAG, "All preferences cleared");
    }
}