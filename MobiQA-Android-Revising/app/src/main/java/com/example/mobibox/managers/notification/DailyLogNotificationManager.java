package com.example.mobibox.managers.notification;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import com.example.mobibox.Constants;
import com.example.mobibox.DailyLogActivity;
import com.example.mobibox.R;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Daily Log通知管理器：负责daily log内容比对、通知创建和发送
 * 继承自 BaseNotificationManager
 */
public class DailyLogNotificationManager extends BaseNotificationManager {
    private static final String TAG = "DailyLogNotifier";

    private static volatile DailyLogNotificationManager sInstance;

    // Private constructor
    private DailyLogNotificationManager(Context context) {
        super(context);
    }

    public static DailyLogNotificationManager getInstance(Context context) {
        if (sInstance == null) {
            synchronized (DailyLogNotificationManager.class) {
                if (sInstance == null) {
                    sInstance = new DailyLogNotificationManager(context);
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
        return Constants.CHANNEL_DAILY_LOG;
    }

    @Override
    protected String getChannelName() {
        return "每日总结通知";
    }

    @Override
    protected String getChannelDescription() {
        return "接收每日行为总结通知";
    }

    @Override
    protected String getPrefsName() {
        return Constants.PREFS_DAILY_LOG_CACHE;
    }

    @Override
    protected String getCacheKey() {
        return Constants.KEY_LAST_DAILY_LOG_JSON;
    }

    @Override
    protected int getNotificationIcon() {
        return R.drawable.ic_notification;
    }

    @Override
    protected Intent createNotificationIntent(String jsonContent) {
        Intent intent = new Intent(mContext, DailyLogActivity.class);
        try {
            JSONObject json = new JSONObject(jsonContent);
            String logContent = json.optString("log_content", "");
            String date = json.optString("date", "");
            intent.putExtra("daily_log_content", logContent);
            intent.putExtra("daily_log_date", date);
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
            String date = json.optString("date", "");
            return content + "|" + date;
        } catch (JSONException e) {
            return jsonContent;
        }
    }

    @Override
    protected String getNotificationTitle(String jsonContent) {
        return "📅 您的每日总结已生成";
    }

    @Override
    protected String getNotificationContent(String jsonContent) {
        try {
            JSONObject json = new JSONObject(jsonContent);
            String date = json.optString("date", "");
            return date.isEmpty() ? "点击查看今日行为总结" : date + " 的行为总结已生成";
        } catch (JSONException e) {
            return "点击查看今日行为总结";
        }
    }

    @Override
    protected int getNotificationId(String jsonContent) {
        return Constants.NOTIFICATION_ID_DAILY_LOG;
    }

    // =====================
    // Public Methods
    // =====================

    /**
     * 处理新的daily log，如果内容有变化则发送通知
     */
    public void handleNewDailyLog(String newDailyLogJson) {
        if (newDailyLogJson == null || newDailyLogJson.isEmpty()) {
            Log.w(TAG, "新daily log内容为空，不处理");
            return;
        }

        try {
            JSONObject json = new JSONObject(newDailyLogJson);

            // 检查响应状态
            String status = json.optString("status", "");
            if (!"success".equals(status)) {
                String message = json.optString("message", "获取失败");
                Log.w(TAG, "服务器返回非成功状态: " + status + ", 信息: " + message);
                return;
            }

            if (isContentChanged(newDailyLogJson)) {
                Log.d(TAG, "Daily log内容有更新，准备发送通知");
                sendNotification(newDailyLogJson);
                updateCache(newDailyLogJson);
                persistToDailyLogPrefs(json);
            } else {
                Log.d(TAG, "Daily log内容未变化，不发送通知");
            }
        } catch (JSONException e) {
            Log.e(TAG, "解析daily log JSON失败: " + e.getMessage());
        }
    }

    /**
     * 持久化到DailyLogPrefs（供DailyLogActivity使用）
     */
    private void persistToDailyLogPrefs(JSONObject json) {
        try {
            SharedPreferences prefs = mContext.getSharedPreferences(
                    Constants.PREFS_DAILY_LOG, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();

            String logContent = json.optString("log_content", "");
            String date = json.optString("date", "");
            String startTime = json.optString("start_time", "");
            String endTime = json.optString("end_time", "");

            editor.putString(Constants.KEY_LAST_DAILY_LOG, logContent);
            editor.putString(Constants.KEY_DAILY_LOG_DATE, date);
            editor.putString(Constants.KEY_DAILY_LOG_START_TIME, startTime);
            editor.putString(Constants.KEY_DAILY_LOG_END_TIME, endTime);
            editor.putString(Constants.KEY_LAST_FETCH_TIMESTAMP,
                    String.valueOf(System.currentTimeMillis()));
            editor.apply();

            Log.d(TAG, "Daily log已保存到DailyLogPrefs");
        } catch (Exception e) {
            Log.e(TAG, "保存daily log到DailyLogPrefs失败: " + e.getMessage());
        }
    }
}