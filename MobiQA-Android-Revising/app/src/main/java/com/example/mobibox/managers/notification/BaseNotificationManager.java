package com.example.mobibox.managers.notification;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

/**
 * Base class for notification managers.
 * Provides common functionality for singleton pattern, notification channel creation,
 * SharedPreferences caching, and notification sending.
 *
 * Subclasses should implement:
 * - getChannelId(): Return the notification channel ID
 * - getChannelName(): Return the notification channel name
 * - getChannelDescription(): Return the notification channel description
 * - getPrefsName(): Return the SharedPreferences file name
 * - getCacheKey(): Return the key for cached content
 * - getNotificationIcon(): Return the small icon resource ID
 * - createNotificationIntent(): Create the Intent for notification click
 * - extractContentForComparison(): Extract comparable content from JSON
 */
public abstract class BaseNotificationManager {
    private static final String TAG = "BaseNotificationManager";

    protected final Context mContext;
    protected final SharedPreferences mSharedPrefs;
    protected String mLastCachedContent;

    /**
     * Constructor for BaseNotificationManager.
     * @param context Application context
     */
    protected BaseNotificationManager(Context context) {
        this.mContext = context.getApplicationContext();
        this.mSharedPrefs = mContext.getSharedPreferences(getPrefsName(), Context.MODE_PRIVATE);
        loadLastContent();
        createNotificationChannel();
    }

    // =====================
    // Abstract Methods - Subclasses must implement
    // =====================

    /**
     * Get the notification channel ID.
     */
    protected abstract String getChannelId();

    /**
     * Get the notification channel name (for display in settings).
     */
    protected abstract String getChannelName();

    /**
     * Get the notification channel description.
     */
    protected abstract String getChannelDescription();

    /**
     * Get the SharedPreferences file name for caching.
     */
    protected abstract String getPrefsName();

    /**
     * Get the key for the cached content in SharedPreferences.
     */
    protected abstract String getCacheKey();

    /**
     * Get the small icon resource ID for notifications.
     */
    protected abstract int getNotificationIcon();

    /**
     * Create the Intent to launch when notification is clicked.
     * @param jsonContent The JSON content that triggered the notification
     * @return The Intent to launch
     */
    protected abstract Intent createNotificationIntent(String jsonContent);

    /**
     * Extract content for comparison from JSON.
     * Used to determine if content has changed.
     * @param jsonContent The full JSON content
     * @return A string suitable for comparison
     */
    protected abstract String extractContentForComparison(String jsonContent);

    /**
     * Get the notification title.
     * @param jsonContent The JSON content
     * @return The notification title
     */
    protected abstract String getNotificationTitle(String jsonContent);

    /**
     * Get the notification content text.
     * @param jsonContent The JSON content
     * @return The notification content text
     */
    protected abstract String getNotificationContent(String jsonContent);

    /**
     * Get the notification ID for this type of notification.
     * @param jsonContent The JSON content (may contain ID field)
     * @return The notification ID
     */
    protected int getNotificationId(String jsonContent) {
        return (int) System.currentTimeMillis();
    }

    // =====================
    // Common Methods
    // =====================

    /**
     * Create the notification channel (Android 8.0+).
     */
    protected void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager notificationManager = mContext.getSystemService(NotificationManager.class);
            if (notificationManager != null && notificationManager.getNotificationChannel(getChannelId()) == null) {
                NotificationChannel channel = new NotificationChannel(
                        getChannelId(),
                        getChannelName(),
                        NotificationManager.IMPORTANCE_DEFAULT);
                channel.setDescription(getChannelDescription());
                channel.enableVibration(true);
                channel.setVibrationPattern(new long[] { 0, 100, 200, 100 });
                notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "Notification channel created: " + getChannelId());
            }
        }
    }

    /**
     * Load the last cached content from SharedPreferences.
     */
    protected void loadLastContent() {
        mLastCachedContent = mSharedPrefs.getString(getCacheKey(), null);
    }

    /**
     * Update the cache with new content (asynchronous).
     * @param newContent The new content to cache
     */
    protected void updateCache(String newContent) {
        mLastCachedContent = newContent;
        mSharedPrefs.edit().putString(getCacheKey(), newContent).apply();
        Log.d(TAG, "Cache updated (async)");
    }

    /**
     * Update the cache with new content (synchronous).
     * @param newContent The new content to cache
     * @return true if saved successfully
     */
    protected boolean updateCacheSync(String newContent) {
        mLastCachedContent = newContent;
        boolean success = mSharedPrefs.edit().putString(getCacheKey(), newContent).commit();
        Log.d(TAG, "Cache updated (sync): " + (success ? "success" : "failed"));
        return success;
    }

    /**
     * Check if content has changed.
     * @param newContent The new JSON content
     * @return true if content has changed or cache is empty
     */
    protected boolean isContentChanged(String newContent) {
        if (mLastCachedContent == null || mLastCachedContent.isEmpty()) {
            Log.d(TAG, "No cached content, treating as new");
            return true;
        }

        String newComparable = extractContentForComparison(newContent);
        String oldComparable = extractContentForComparison(mLastCachedContent);

        boolean changed = !oldComparable.equals(newComparable);
        Log.d(TAG, "Content changed: " + changed);
        return changed;
    }

    /**
     * Check if notification permission is granted (Android 13+).
     * @return true if permission is granted or not required
     */
    protected boolean hasNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(mContext,
                    Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    /**
     * Send a notification.
     * @param jsonContent The JSON content for the notification
     * @return true if notification was sent successfully
     */
    protected boolean sendNotification(String jsonContent) {
        if (!hasNotificationPermission()) {
            Log.w(TAG, "No notification permission, skipping notification");
            return false;
        }

        try {
            String title = getNotificationTitle(jsonContent);
            String content = getNotificationContent(jsonContent);

            Intent intent = createNotificationIntent(jsonContent);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

            int requestCode = getNotificationId(jsonContent);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    mContext,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext, getChannelId())
                    .setSmallIcon(getNotificationIcon())
                    .setContentTitle(title)
                    .setContentText(content.length() > 50 ? content.substring(0, 50) + "..." : content)
                    .setSubText("点击查看详情")
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

            // Use BigTextStyle for longer content
            if (content.length() > 50) {
                builder.setStyle(new NotificationCompat.BigTextStyle().bigText(content));
            }

            NotificationManagerCompat notificationManager = NotificationManagerCompat.from(mContext);
            notificationManager.notify(getNotificationId(jsonContent), builder.build());
            Log.d(TAG, "Notification sent: " + title);
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Failed to send notification: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Clear the cached content.
     */
    public void clearCache() {
        mSharedPrefs.edit().remove(getCacheKey()).apply();
        mLastCachedContent = null;
        Log.d(TAG, "Cache cleared");
    }

    /**
     * Get the last cached content.
     * @return The cached content, or null if not set
     */
    public String getLastCachedContent() {
        return mLastCachedContent;
    }

    /**
     * Get the SharedPreferences instance.
     * @return SharedPreferences for this manager
     */
    protected SharedPreferences getSharedPreferences() {
        return mSharedPrefs;
    }

    /**
     * Get the application context.
     * @return Application context
     */
    protected Context getContext() {
        return mContext;
    }
}