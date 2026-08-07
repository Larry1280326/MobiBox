package com.example.mobibox.managers.notification;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.mobibox.Constants;
import com.example.mobibox.InterventionActivity;
import com.example.mobibox.R;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * 管理 Hourly Update 通知
 * 负责检测新的 hourly log，并发送通知
 * 继承自 BaseNotificationManager
 */
public class HourlyUpdateNotificationManager extends BaseNotificationManager {
    private static final String TAG = "HourlyUpdateNotification";

    private static volatile HourlyUpdateNotificationManager sInstance;

    // Private constructor
    private HourlyUpdateNotificationManager(Context context) {
        super(context);
    }

    public static HourlyUpdateNotificationManager getInstance(Context context) {
        if (sInstance == null) {
            synchronized (HourlyUpdateNotificationManager.class) {
                if (sInstance == null) {
                    sInstance = new HourlyUpdateNotificationManager(context);
                }
            }
        }
        return sInstance;
    }

    // =====================
    // BaseNotificationManager Implementation
    // =====================

    @Override
    protected String getChannelId() {
        return Constants.CHANNEL_HOURLY_UPDATE;
    }

    @Override
    protected String getChannelName() {
        return "Hourly Updates";
    }

    @Override
    protected String getChannelDescription() {
        return "Notifications for hourly logs and interventions";
    }

    @Override
    protected String getPrefsName() {
        return Constants.PREFS_HOURLY_UPDATE_CACHE;
    }

    @Override
    protected String getCacheKey() {
        return Constants.KEY_CACHED_HOURLY_LOG;
    }

    @Override
    protected int getNotificationIcon() {
        return R.drawable.ic_launcher_foreground;
    }

    @Override
    protected Intent createNotificationIntent(String jsonContent) {
        Intent intent = new Intent(mContext, InterventionActivity.class);
        intent.putExtra("from_notification", true);
        try {
            JSONObject json = new JSONObject(jsonContent);
            intent.putExtra("hourly_log_content", json.optString("log_content", ""));
            intent.putExtra("hourly_log_start_time", json.optString("start_time", ""));
            intent.putExtra("hourly_log_end_time", json.optString("end_time", ""));
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing JSON for intent: " + e.getMessage());
        }
        return intent;
    }

    @Override
    protected String extractContentForComparison(String jsonContent) {
        try {
            JSONObject json = new JSONObject(jsonContent);
            String content = json.optString("log_content", "");
            String time = json.optString("generation_time", "");
            return content + "|" + time;
        } catch (JSONException e) {
            return jsonContent;
        }
    }

    @Override
    protected String getNotificationTitle(String jsonContent) {
        return "📊 New Hourly Summary";
    }

    @Override
    protected String getNotificationContent(String jsonContent) {
        try {
            JSONObject json = new JSONObject(jsonContent);
            String logContent = json.optString("log_content", "");
            if (logContent.isEmpty()) {
                return "Tap to view your hourly summary";
            }
            return logContent.length() > 100 ?
                    logContent.substring(0, 100) + "..." : logContent;
        } catch (JSONException e) {
            return "Tap to view your hourly summary";
        }
    }

    // =====================
    // Public Methods
    // =====================

    /**
     * 检查 Hourly Log 是否有新内容（不保存和通知，只检查）
     * @param hourlyLogJson hourly log 的 JSON 响应
     * @return true 如果有新内容，false 如果没有新内容
     */
    public boolean checkIfNew(String hourlyLogJson) {
        if (hourlyLogJson == null || hourlyLogJson.isEmpty()) {
            return false;
        }

        try {
            JSONObject hourlyLogObj = new JSONObject(hourlyLogJson);

            // 检查 status
            if (!"success".equals(hourlyLogObj.optString("status"))) {
                return false;
            }

            return isContentChanged(hourlyLogJson);
        } catch (Exception e) {
            Log.e(TAG, "检查 Hourly Log 是否有新内容失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 处理新的 Hourly Log
     * @param hourlyLogJson hourly log 的 JSON 响应
     */
    public void handleNewHourlyLog(String hourlyLogJson) {
        if (hourlyLogJson == null || hourlyLogJson.isEmpty()) {
            Log.w(TAG, "Hourly Log 内容为空，跳过处理");
            return;
        }

        try {
            Log.d(TAG, "处理新的 Hourly Log...");

            JSONObject hourlyLogObj = new JSONObject(hourlyLogJson);

            // 检查 status
            if (!"success".equals(hourlyLogObj.optString("status"))) {
                Log.w(TAG, "Hourly Log status 不是 success: " + hourlyLogObj.optString("status"));
                return;
            }

            // 检查是否有内容变化
            if (isContentChanged(hourlyLogJson)) {
                Log.d(TAG, "检测到新的 Hourly Log，准备发送通知");

                // 发送通知
                sendNotification(hourlyLogJson);

                // 更新缓存
                updateCache(hourlyLogJson);

                // 保存到 InterventionActivity 的 SharedPreferences
                persistHourlyLogToInterventionPrefs(hourlyLogObj);
            } else {
                Log.d(TAG, "Hourly Log 内容未变化，不发送通知");
            }

        } catch (Exception e) {
            Log.e(TAG, "处理 Hourly Log 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 持久化 Hourly Log 到 InterventionActivity 的 SharedPreferences
     */
    private void persistHourlyLogToInterventionPrefs(JSONObject hourlyLogObj) {
        try {
            SharedPreferences prefs = mContext.getSharedPreferences(
                    Constants.PREFS_INTERVENTION, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            editor.putString(Constants.KEY_LAST_HOURLY_LOG,
                    hourlyLogObj.optString("log_content", ""));
            editor.putString(Constants.KEY_LAST_HOURLY_LOG_START_TIME,
                    hourlyLogObj.optString("start_time", ""));
            editor.putString(Constants.KEY_LAST_HOURLY_LOG_END_TIME,
                    hourlyLogObj.optString("end_time", ""));
            editor.apply();

            Log.d(TAG, "已同步 Hourly Log 到 InterventionActivity SharedPreferences");
        } catch (Exception e) {
            Log.e(TAG, "持久化 Hourly Log 到 InterventionActivity 失败: " + e.getMessage(), e);
        }
    }
}