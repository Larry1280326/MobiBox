package com.example.mobibox.workers;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.example.mobibox.Constants;
import com.example.mobibox.managers.notification.DailyLogNotificationManager;
import com.example.mobibox.network.HttpApiClient;

/**
 * Daily Log定时查询Worker
 * 每天晚上8点（中国时区UTC+8）自动查询服务器，如果有新的daily log则发送通知
 *
 * 时区说明：
 * - 服务器在中国，使用UTC+8时区（Asia/Shanghai）
 * - Daily Log在服务器每天早上8点（UTC+8）生成
 * - 客户端在每天晚上8点（UTC+8）查询
 * - 确保服务器有足够时间生成当天的数据
 */
public class DailyLogWorker extends Worker {
    private static final String TAG = "DailyLogWorker";

    public DailyLogWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        // 记录执行时间（使用中国时区）
        java.util.TimeZone chinaTimeZone = java.util.TimeZone.getTimeZone("Asia/Shanghai");
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
        sdf.setTimeZone(chinaTimeZone);
        String currentTime = sdf.format(new java.util.Date());
        
        Log.d(TAG, "===== Daily Log Worker 开始执行 =====");
        Log.d(TAG, "执行时间(UTC+8): " + currentTime);
        Log.d(TAG, "=====================================");

        // 获取用户ID
        SharedPreferences prefs = getApplicationContext().getSharedPreferences("AppPrefs", Context.MODE_PRIVATE);
        String userId = prefs.getString("userId", null);

        if (TextUtils.isEmpty(userId)) {
            Log.w(TAG, "用户未登录，跳过查询");
            return Result.success();
        }

        // 查询服务器获取daily log
        try {
            String dailyLogJson = fetchDailyLogFromServer(userId);
            if (dailyLogJson != null) {
                // 处理新的daily log（比对并发送通知）
                DailyLogNotificationManager notificationManager = 
                        DailyLogNotificationManager.getInstance(getApplicationContext());
                notificationManager.handleNewDailyLog(dailyLogJson);
                
                Log.d(TAG, "Daily log查询任务完成");
                return Result.success();
            } else {
                Log.w(TAG, "未获取到daily log");
                return Result.retry();
            }
        } catch (Exception e) {
            Log.e(TAG, "查询daily log失败: " + e.getMessage(), e);
            return Result.retry();
        }
    }

    /**
     * 从服务器获取daily log，复用HttpApiClient的单例OkHttp连接池。
     */
    @Nullable
    private String fetchDailyLogFromServer(String userId) {
        // Re-init the singleton for this user (needed if userId changed since app startup)
        HttpApiClient.init(userId);
        HttpApiClient client = HttpApiClient.getInstance();
        if (client == null) {
            Log.e(TAG, "HttpApiClient未初始化");
            return null;
        }
        return client.getDailyLog();
    }
}

