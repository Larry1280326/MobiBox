package com.example.mobibox.workers;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.mobibox.Constants;
import com.example.mobibox.managers.notification.HourlyUpdateNotificationManager;
import com.example.mobibox.managers.notification.InterventionNotificationManager;

import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * 每小时后台任务：获取 Hourly Log 和 Intervention
 *
 * Uses polling mechanism:
 * - Sends last_log_id to server to detect new logs
 * - Only processes new logs (has_new_log=true)
 * - Stores last_log_id for next poll
 */
public class HourlyUpdateWorker extends Worker {
    private static final String TAG = "HourlyUpdateWorker";

    public HourlyUpdateWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        // 设置中国时区用于日志输出
        TimeZone chinaTimeZone = TimeZone.getTimeZone("Asia/Shanghai");
        Calendar calendar = Calendar.getInstance(chinaTimeZone);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        sdf.setTimeZone(chinaTimeZone);

        Log.d(TAG, "===== Hourly Update Worker 开始执行 =====");
        Log.d(TAG, "执行时间（中国时区）: " + sdf.format(calendar.getTime()));

        try {
            // 获取用户ID
            SharedPreferences prefs = getApplicationContext().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
            String userId = prefs.getString("userId", null);

            if (userId == null) {
                Log.e(TAG, "用户未登录，跳过Hourly Update");
                return Result.failure();
            }

            Log.d(TAG, "用户ID: " + userId);

            // Get the last known hourly log ID for polling
            com.example.mobibox.data.SharedPreferencesHelper prefsHelper =
                com.example.mobibox.data.SharedPreferencesHelper.getInstance(getApplicationContext());
            String lastLogId = prefsHelper.getLastHourlyLogId();
            Log.d(TAG, "Last hourly log ID: " + lastLogId);

            // 1. 获取 Hourly Log (with polling mechanism)
            String hourlyLogJson = fetchHourlyLogFromServer(userId, lastLogId);

            // 2. 获取 Intervention（交给 InterventionNotificationManager 处理，与 DataService 的 5 分钟检查统一）
            String interventionJson = fetchInterventionFromServer(userId);
            if (interventionJson != null && !interventionJson.isEmpty()) {
                // 使用 InterventionNotificationManager 统一处理 Intervention 通知
                // 这样可以与 DataService 的 5 分钟检查共享缓存，避免重复通知
                InterventionNotificationManager.getInstance(getApplicationContext())
                        .handleNewIntervention(interventionJson);
            }

            // 3. 处理 Hourly Log 并发送通知
            if (hourlyLogJson != null && !hourlyLogJson.isEmpty()) {
                // Parse response to check for new log and extract ID
                try {
                    org.json.JSONObject response = new org.json.JSONObject(hourlyLogJson);
                    boolean hasNewLog = response.optBoolean("has_new_log", true);

                    if (hasNewLog && response.has("data") && !response.isNull("data")) {
                        org.json.JSONObject data = response.getJSONObject("data");
                        String newLogId = data.optString("id", "");

                        // Store the new log ID for next poll
                        if (!newLogId.isEmpty()) {
                            prefsHelper.setLastHourlyLogId(newLogId);
                            Log.d(TAG, "Stored new hourly log ID: " + newLogId);
                        }

                        // Process and show notification
                        HourlyUpdateNotificationManager notificationManager =
                            HourlyUpdateNotificationManager.getInstance(getApplicationContext());
                        notificationManager.handleNewHourlyLog(hourlyLogJson);

                        Log.d(TAG, "New hourly log found and processed, ID: " + newLogId);
                    } else {
                        Log.d(TAG, "No new hourly log (has_new_log=" + hasNewLog + ")");
                    }
                } catch (org.json.JSONException e) {
                    Log.e(TAG, "Error parsing hourly log response: " + e.getMessage());
                    // Fallback: still try to process as before
                    HourlyUpdateNotificationManager notificationManager =
                        HourlyUpdateNotificationManager.getInstance(getApplicationContext());
                    notificationManager.handleNewHourlyLog(hourlyLogJson);
                }
            }

            Log.d(TAG, "===== Hourly Update Worker 执行完成 =====");
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "Hourly Update Worker 执行失败: " + e.getMessage(), e);
            return Result.retry();
        }
    }

    /**
     * 从服务器获取 Hourly Log (with polling support)
     * @param userId 用户ID
     * @param lastLogId 上次获取的日志ID，-1表示不使用polling机制
     * @return Hourly Log JSON字符串，或null
     */
    private String fetchHourlyLogFromServer(String userId, String lastLogId) {
        Log.d(TAG, "开始获取 Hourly Log (last_log_id=" + lastLogId + ")...");

        try {
            com.example.mobibox.network.HttpApiClient client =
                com.example.mobibox.network.HttpApiClient.getInstance();
            if (client == null) {
                Log.e(TAG, "HttpApiClient not initialized");
                return null;
            }

            return client.getHourlyLog(lastLogId);

        } catch (Exception e) {
            Log.e(TAG, "Hourly Log 获取异常: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * 从服务器获取 Intervention
     */
    private String fetchInterventionFromServer(String userId) {
        Log.d(TAG, "开始获取 Intervention...");

        try {
            OkHttpClient client = new OkHttpClient();
            JSONObject json = new JSONObject();
            json.put("user", userId);

            RequestBody body = RequestBody.create(
                json.toString(),
                MediaType.parse("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                    .url(Constants.getInterventionUrl())
                    .post(body)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String result = response.body().string();
                    Log.d(TAG, "Intervention 获取成功: " + result);
                    return result;
                } else {
                    Log.e(TAG, "Intervention 获取失败，HTTP状态码: " + response.code());
                    return null;
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Intervention 网络请求失败: " + e.getMessage());
            return null;
        } catch (Exception e) {
            Log.e(TAG, "Intervention 获取异常: " + e.getMessage(), e);
            return null;
        }
    }
}


