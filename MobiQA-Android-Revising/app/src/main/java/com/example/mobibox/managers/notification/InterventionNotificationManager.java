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
 * 干预通知管理器：负责干预内容比对、通知创建和发送
 * 继承自 BaseNotificationManager
 */
public class InterventionNotificationManager extends BaseNotificationManager {
    private static final String TAG = "InterventionNotifier";

    private static volatile InterventionNotificationManager sInstance;

    // Private constructor
    private InterventionNotificationManager(Context context) {
        super(context);
    }

    public static InterventionNotificationManager getInstance(Context context) {
        if (sInstance == null) {
            synchronized (InterventionNotificationManager.class) {
                if (sInstance == null) {
                    sInstance = new InterventionNotificationManager(context);
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
        return Constants.CHANNEL_INTERVENTION;
    }

    @Override
    protected String getChannelName() {
        return "干预内容通知";
    }

    @Override
    protected String getChannelDescription() {
        return "接收新的个性化干预建议通知";
    }

    @Override
    protected String getPrefsName() {
        return Constants.PREFS_INTERVENTION_CACHE;
    }

    @Override
    protected String getCacheKey() {
        return Constants.KEY_LAST_INTERVENTION_JSON;
    }

    @Override
    protected int getNotificationIcon() {
        return R.drawable.ic_notification;
    }

    @Override
    protected Intent createNotificationIntent(String jsonContent) {
        Intent intent = new Intent(mContext, InterventionActivity.class);
        try {
            JSONObject json = new JSONObject(jsonContent);
            String id = json.optString("id", "default_id");
            String content = json.optString("content", "");
            intent.putExtra("intervention_id", id);
            intent.putExtra("intervention_content", content);
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing JSON for intent: " + e.getMessage());
        }
        return intent;
    }

    @Override
    protected String extractContentForComparison(String jsonContent) {
        try {
            JSONObject json = new JSONObject(jsonContent);
            // Compare by content and time range
            String content = json.optString("content", "");
            String start = json.optString("start_time", "");
            String end = json.optString("end_time", "");
            return content + "|" + start + "|" + end;
        } catch (JSONException e) {
            return jsonContent;
        }
    }

    @Override
    protected String getNotificationTitle(String jsonContent) {
        try {
            JSONObject json = new JSONObject(jsonContent);
            return json.optString("title", "新的干预建议");
        } catch (JSONException e) {
            return "新的干预建议";
        }
    }

    @Override
    protected String getNotificationContent(String jsonContent) {
        try {
            JSONObject json = new JSONObject(jsonContent);
            return json.optString("content", "点击查看详情");
        } catch (JSONException e) {
            return "点击查看详情";
        }
    }

    @Override
    protected int getNotificationId(String jsonContent) {
        try {
            JSONObject json = new JSONObject(jsonContent);
            String id = json.optString("id", "");
            return Math.abs(id.hashCode());
        } catch (JSONException e) {
            return (int) System.currentTimeMillis();
        }
    }

    // =====================
    // Public Methods
    // =====================

    /**
     * 检查 Intervention 是否有新内容（不保存和通知，只检查）
     * @param interventionJson intervention 的 JSON 响应
     * @return true 如果有新内容，false 如果没有新内容
     */
    public boolean checkIfNew(String interventionJson) {
        if (interventionJson == null || interventionJson.isEmpty()) {
            return false;
        }
        return isContentChanged(interventionJson);
    }

    /**
     * 处理新的干预内容，如果内容有变化则保存并发送通知
     * @param newInterventionJson 新的干预内容 JSON
     */
    public void handleNewIntervention(String newInterventionJson) {
        if (newInterventionJson == null || newInterventionJson.isEmpty()) {
            Log.w(TAG, "新干预内容为空，不处理");
            return;
        }

        if (isContentChanged(newInterventionJson)) {
            Log.d(TAG, "干预内容有更新，准备发送通知");
            try {
                JSONObject json = new JSONObject(newInterventionJson);

                // 先保存数据，再发送通知
                Log.d(TAG, "步骤1：更新缓存");
                updateCacheSync(newInterventionJson);

                Log.d(TAG, "步骤2：持久化到 InterventionPrefs");
                persistToInterventionPrefs(json);

                // 短暂延迟确保数据写入完成
                try {
                    Log.d(TAG, "步骤3：等待2秒，确保数据写入完成");
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Log.w(TAG, "等待被中断: " + e.getMessage());
                    Thread.currentThread().interrupt();
                }

                Log.d(TAG, "步骤4：发送通知");
                sendNotification(newInterventionJson);

                Log.d(TAG, "Intervention 保存和通知流程完成");
            } catch (JSONException e) {
                Log.e(TAG, "解析干预JSON失败: " + e.getMessage());
            }
        } else {
            Log.d(TAG, "干预内容未变化，不发送通知");
        }
    }

    /**
     * 持久化干预内容到 InterventionPrefs
     */
    private void persistToInterventionPrefs(JSONObject json) {
        try {
            String content = json.optString("content", "");
            String start = json.optString("start_time", "");
            String end = json.optString("end_time", "");
            if (content == null) content = "";

            SharedPreferences prefs = mContext.getSharedPreferences(
                    Constants.PREFS_INTERVENTION, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(Constants.KEY_LAST_INTERVENTION, content);
            editor.putString(Constants.KEY_INTERVENTION_START_TIME, start);
            editor.putString(Constants.KEY_INTERVENTION_END_TIME, end);
            editor.putString(Constants.KEY_LAST_INTERVENTION_TIMESTAMP,
                    String.valueOf(System.currentTimeMillis()));

            // 使用 commit() 确保同步写入
            boolean success = editor.commit();
            if (success) {
                Log.d(TAG, "Intervention 数据已同步写入 InterventionPrefs");
            } else {
                Log.e(TAG, "Intervention 数据写入 InterventionPrefs 失败");
            }
        } catch (Exception e) {
            Log.e(TAG, "持久化干预内容失败: " + e.getMessage());
        }
    }
}