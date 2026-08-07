package com.example.mobibox;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import com.example.mobibox.network.HttpApiClient;

import com.example.mobibox.util.TimeUtils;

public class DailyLogActivity extends AppCompatActivity {

    // UI控件
    private ScrollView scrollView;
    private TextView dailyLogContent;
    private TextView dailyLogDateInfo;
    private RadioGroup ratingGroup;
    private EditText feedbackTextInput;
    private Button btnRefreshDailyLog;
    private Button btnSubmitFeedback;

    // 数据
    private String userId;
    private String currentDailyLog;
    private String currentDate;
    private String startTime;
    private String endTime;
    private String currentLogId = ""; // 用于存储日志ID，用于反馈提交

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_daily_log);

        // 初始化视图
        initViews();

        // 获取用户ID
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        userId = prefs.getString("userId", null);
        if (userId == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // 加载本地缓存数据（不请求服务器）
        loadLocalData();

        // 处理从通知跳转过来的Intent
        handleNotificationIntent(getIntent());

        // 设置按钮点击事件
        btnRefreshDailyLog.setOnClickListener(v -> fetchDailyLogFromServer(true));  // 手动刷新，显示提示
        btnSubmitFeedback.setOnClickListener(v -> submitFeedback());
    }

    private void initViews() {
        scrollView = findViewById(R.id.scroll_view_daily_log);
        dailyLogContent = findViewById(R.id.daily_log_content);
        dailyLogDateInfo = findViewById(R.id.daily_log_date_info);
        ratingGroup = findViewById(R.id.rating_group);
        feedbackTextInput = findViewById(R.id.feedback_text_input);
        feedbackTextInput.setHint("Share your thoughts on today's summary (required)");
        btnRefreshDailyLog = findViewById(R.id.btn_refresh_daily_log);
        btnSubmitFeedback = findViewById(R.id.btn_submit_daily_log_feedback);
        
        // 为 feedbackTextInput 添加焦点监听，确保键盘弹出时不被遮挡
        feedbackTextInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                // 延迟执行滚动，确保键盘已经弹出
                scrollView.postDelayed(() -> {
                    // 计算 feedbackTextInput 的位置
                    int[] location = new int[2];
                    feedbackTextInput.getLocationOnScreen(location);
                    int editTextY = location[1];
                    
                    // 获取 ScrollView 的位置
                    int[] scrollLocation = new int[2];
                    scrollView.getLocationOnScreen(scrollLocation);
                    int scrollViewY = scrollLocation[1];
                    
                    // 计算需要滚动的距离
                    int scrollTo = editTextY - scrollViewY - 100; // 留出100dp的上边距
                    
                    // 平滑滚动到目标位置
                    scrollView.smoothScrollTo(0, scrollTo);
                }, 300); // 延迟300ms，等待键盘弹出动画完成
            }
        });
    }

    private void loadLocalData() {
        SharedPreferences prefs = getSharedPreferences("DailyLogPrefs", MODE_PRIVATE);
        currentDailyLog = prefs.getString("last_daily_log", "");
        currentDate = prefs.getString("daily_log_date", "");
        startTime = prefs.getString("daily_log_start_time", "");
        endTime = prefs.getString("daily_log_end_time", "");
        currentLogId = prefs.getString("daily_log_id", "");

        if (!TextUtils.isEmpty(currentDailyLog)) {
            dailyLogContent.setText(currentDailyLog);
            if (!TextUtils.isEmpty(currentDate)) {
                if (!TextUtils.isEmpty(startTime) && !TextUtils.isEmpty(endTime)) {
                    dailyLogDateInfo.setText(String.format("Date: %s (%s - %s)",
                            currentDate, TimeUtils.formatUtcToHKTime(startTime), TimeUtils.formatUtcToHKTime(endTime)));
                } else {
                    dailyLogDateInfo.setText("Date: " + currentDate);
                }
            }
        } else {
            dailyLogContent.setText("No daily summary available yet. Click 'Refresh' to load.");
        }
    }

    private void fetchDailyLogFromServer() {
        fetchDailyLogFromServer(false);  // 默认不显示提示
    }

    private void fetchDailyLogFromServer(boolean showToast) {
        if (TextUtils.isEmpty(userId)) {
            if (showToast) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        // 只在手动刷新时显示加载提示
        if (showToast) {
        Toast.makeText(this, "Loading daily log...", Toast.LENGTH_SHORT).show();
        }

        new Thread(() -> {
            Response response = null;
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                        .writeTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                        .retryOnConnectionFailure(true)
                        .build();

                JSONObject payload = new JSONObject();
                payload.put("user", userId);
                payload.put("log_type", "daily");
                // 可选：如果需要获取特定日期的daily log，可以添加date字段
                // 格式：YYYY-MM-DD，例如 "2025-11-02"
                // payload.put("date", "2025-11-02");

                RequestBody body = RequestBody.create(
                        payload.toString(),
                        MediaType.get("application/json; charset=utf-8"));

                Request request = new Request.Builder()
                        .url(Constants.getSummaryLogUrl())
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .build();

                response = client.newCall(request).execute();

                if (!response.isSuccessful()) {
                    String errorBody = "";
                    if (response.body() != null) {
                        try {
                            errorBody = response.body().string();
                        } catch (IOException e) {
                            errorBody = "Unable to read error response";
                        }
                    }
                    handleDailyLogError(response.code(), errorBody);
                    return;
                }

                if (response.body() == null) {
                    Log.e("DailyLogError", "Response body is empty");
                    makeToast("Server returned empty response");
                    return;
                }

                String responseData = response.body().string();
                Log.d("DAILY_LOG_RESPONSE", "Received data: " + responseData);

                JSONObject jsonResponse = new JSONObject(responseData);

                // 检查响应状态
                String status = jsonResponse.optString("status", "");
                if (!"success".equals(status)) {
                    String message = jsonResponse.optString("message", "Failed to retrieve");
                    Log.e("DailyLogError", "服务器返回非成功状态: " + status + ", 信息: " + message);
                    makeToast("Failed to retrieve: " + message);
                    return;
                }

                // Extract from data object - backend returns {status: "success", data: {...}}
                JSONObject data = jsonResponse.optJSONObject("data");
                if (data == null) {
                    Log.e("DailyLogError", "Server returned empty data");
                    makeToast("Server returned empty data");
                    return;
                }

                currentDailyLog = data.optString("log_content", "");
                currentDate = jsonResponse.optString("date", "");
                startTime = data.optString("start_timestamp", "");
                endTime = data.optString("end_timestamp", "");
                // 尝试获取日志ID（后端可能返回id、log_id或summary_logs_id）
                currentLogId = data.optString("id", "");
                if (currentLogId.isEmpty()) {
                    currentLogId = data.optString("log_id", "");
                }
                if (currentLogId.isEmpty()) {
                    currentLogId = data.optString("summary_logs_id", "");
                }

                // 保存到本地
                saveDailyLogToLocal();

                // 更新UI
                runOnUiThread(() -> {
                    dailyLogContent.setText(currentDailyLog);
                    if (!TextUtils.isEmpty(currentDate)) {
                        dailyLogDateInfo.setText("Date: " + currentDate);
                    }
                    if (!TextUtils.isEmpty(startTime) && !TextUtils.isEmpty(endTime)) {
                        dailyLogDateInfo.setText(String.format("Date: %s (%s - %s)",
                                currentDate, TimeUtils.formatUtcToHKTime(startTime), TimeUtils.formatUtcToHKTime(endTime)));
                    }
                    if (showToast) {
                    makeToast("Daily log loaded successfully");
                    }
                });

            } catch (Exception e) {
                Log.e("DailyLogError", "Failed to fetch daily log: " + e.getMessage(), e);
                makeToast("Failed to load daily log: " + e.getMessage());
            } finally {
                if (response != null) {
                    response.close();
                }
            }
        }).start();
    }

    private void handleDailyLogError(int errorCode, String errorBody) {
        runOnUiThread(() -> {
            String message;
            switch (errorCode) {
                case 400:
                    message = "Invalid date format";
                    break;
                case 431:
                    message = "User ID missing (please re-login)";
                    break;
                case 432:
                    message = "Unknown user (please check user info)";
                    break;
                case 436:
                    message = "No daily log found (it may not be generated yet)";
                    break;
                case 500:
                    message = "Server error, please try again later";
                    break;
                default:
                    message = "Error: " + errorCode;
                    if (!TextUtils.isEmpty(errorBody)) {
                        message += " - " + errorBody;
                    }
            }
            makeToast(message);
            Log.e("DailyLogError", "HTTP " + errorCode + ", body=" + errorBody);
        });
    }

    private void saveDailyLogToLocal() {
        SharedPreferences prefs = getSharedPreferences("DailyLogPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("last_daily_log", currentDailyLog);
        editor.putString("daily_log_date", currentDate);
        editor.putString("daily_log_start_time", startTime);
        editor.putString("daily_log_end_time", endTime);
        editor.putString("daily_log_id", currentLogId);
        editor.putString("last_fetch_timestamp", String.valueOf(System.currentTimeMillis()));
        editor.apply();
    }

    private void submitFeedback() {
        // 验证反馈内容
        String feedbackText = feedbackTextInput.getText().toString().trim();
        if (TextUtils.isEmpty(feedbackText)) {
            Toast.makeText(this, "Please provide your feedback", Toast.LENGTH_SHORT).show();
            return;
        }

        if (TextUtils.isEmpty(currentDailyLog)) {
            Toast.makeText(this, "No daily log to provide feedback for", Toast.LENGTH_SHORT).show();
            return;
        }

        // 获取评分
        int rating = getRatingFromRadioGroup();

        // 显示提交中提示
        Toast.makeText(this, "Submitting feedback...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            Response response = null;
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .retryOnConnectionFailure(true)
                        .build();

                JSONObject feedbackJson = new JSONObject();
                // Backend expects user as string
                feedbackJson.put("user", userId);
                // 使用日志ID而不是内容，如果没有ID则使用-1
                if (!currentLogId.isEmpty()) {
                    feedbackJson.put("summary_logs_id", currentLogId);
                }
                feedbackJson.put("feedback", feedbackText);
                // 注意：rating字段已移除，后端不支持

                Log.d("DailyLogFeedback", "Submitting feedback: " + feedbackJson.toString());

                RequestBody body = RequestBody.create(
                        feedbackJson.toString(),
                        MediaType.get("application/json; charset=utf-8"));

                Request request = new Request.Builder()
                        .url(Constants.getSendLogFeedbackUrl())
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .build();

                response = client.newCall(request).execute();

                if (!response.isSuccessful()) {
                    String errorBody = "";
                    if (response.body() != null) {
                        try {
                            errorBody = response.body().string();
                        } catch (IOException e) {
                            errorBody = "Unable to read error response";
                        }
                    }
                    handleFeedbackError(response.code(), errorBody);
                    return;
                }

                String responseBody = response.body() != null ? response.body().string() : "";
                Log.d("DailyLogFeedback", "Response: " + responseBody);

                // 检查响应状态
                try {
                    JSONObject jsonResponse = new JSONObject(responseBody);
                    String status = jsonResponse.optString("status", "");
                    if (!"success".equals(status)) {
                        String message = jsonResponse.optString("message", "Submission failed");
                        Log.e("DailyLogFeedback", "服务器返回非成功状态: " + status + ", 信息: " + message);
                        runOnUiThread(() -> Toast.makeText(DailyLogActivity.this,
                                "Submission failed: " + message, Toast.LENGTH_LONG).show());
                        return;
                    }
                } catch (JSONException e) {
                    Log.w("DailyLogFeedback", "解析响应JSON失败，但HTTP状态码为200，视为成功: " + e.getMessage());
                }

                runOnUiThread(() -> {
                    // ✅ 成功提交后清除所有 daily log 缓存
                    clearDailyLogCache();
                    
                    Toast.makeText(DailyLogActivity.this, "Feedback submitted successfully!", Toast.LENGTH_SHORT).show();
                    
                    // 关闭页面
                    finish();
                });

            } catch (Exception e) {
                Log.e("DailyLogFeedbackError", "Failed to submit feedback: " + e.getMessage(), e);
                runOnUiThread(() -> Toast.makeText(DailyLogActivity.this,
                        "Submission failed: " + e.getMessage(), Toast.LENGTH_LONG).show());
            } finally {
                if (response != null) {
                    response.close();
                }
            }
        }).start();
    }

    private void handleFeedbackError(int errorCode, String errorBody) {
        runOnUiThread(() -> {
            String message;
            switch (errorCode) {
                case 431:
                    message = "User ID missing (please re-login)";
                    break;
                case 432:
                    message = "Unknown user (please check user info)";
                    break;
                case 434:
                    message = "Missing required fields";
                    break;
                case 500:
                    message = "Server error, please try again later";
                    break;
                default:
                    message = "Submission failed, error: " + errorCode;
                    if (!TextUtils.isEmpty(errorBody)) {
                        message += "\n" + errorBody;
                    }
            }
            Toast.makeText(DailyLogActivity.this, message, Toast.LENGTH_LONG).show();
            Log.e("DailyLogFeedbackError", "Code: " + errorCode + ", Body: " + errorBody);
        });
    }

    private int getRatingFromRadioGroup() {
        int checkedId = ratingGroup.getCheckedRadioButtonId();
        if (checkedId == R.id.rating_1) return 1;
        if (checkedId == R.id.rating_2) return 2;
        if (checkedId == R.id.rating_3) return 3;
        if (checkedId == R.id.rating_4) return 4;
        if (checkedId == R.id.rating_5) return 5;
        return 3; // 默认3星
    }

    private void makeToast(String message) {
        runOnUiThread(() -> Toast.makeText(DailyLogActivity.this, message, Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleNotificationIntent(intent);
    }

    /**
     * 处理从通知传递过来的daily log内容
     */
    private void handleNotificationIntent(Intent intent) {
        if (intent == null) {
            return;
        }

        String content = intent.getStringExtra("daily_log_content");
        String date = intent.getStringExtra("daily_log_date");

        if (!TextUtils.isEmpty(content)) {
            currentDailyLog = content;
            dailyLogContent.setText(content);

            if (!TextUtils.isEmpty(date)) {
                currentDate = date;
                dailyLogDateInfo.setText("Date: " + date);
            }

            Log.d("DailyLogActivity", "已显示通知传递的daily log内容");
            Toast.makeText(this, "Showing latest daily summary", Toast.LENGTH_SHORT).show();
        } else {
            Log.d("DailyLogActivity", "通知未携带daily log内容，显示本地缓存");
        }
    }

    /**
     * 清除 Daily Log 缓存
     */
    private void clearDailyLogCache() {
        try {
            // 1️⃣ 清空 DailyLogPrefs（页面显示缓存）
            SharedPreferences dailyLogPrefs = getSharedPreferences("DailyLogPrefs", MODE_PRIVATE);
            SharedPreferences.Editor editor1 = dailyLogPrefs.edit();
            editor1.clear();  // 清空所有数据
            editor1.commit();
            Log.d("DailyLogActivity", "✅ DailyLogPrefs 缓存已清空");

            // 2️⃣ 清空 DailyLogCache（通知管理器缓存）
            SharedPreferences dailyLogCache = getSharedPreferences("DailyLogCache", MODE_PRIVATE);
            SharedPreferences.Editor editor2 = dailyLogCache.edit();
            editor2.clear();  // 清空所有数据
            editor2.commit();
            Log.d("DailyLogActivity", "✅ DailyLogCache 缓存已清空");

            Log.d("DailyLogActivity", "✅✅✅ 所有 Daily Log 缓存已完全清空！");
        } catch (Exception e) {
            Log.e("DailyLogActivity", "清空 daily log 缓存失败: " + e.getMessage(), e);
        }

        // 清空当前 UI 数据
        runOnUiThread(() -> {
            try {
                if (dailyLogContent != null)
                    dailyLogContent.setText("No daily summary available yet");
                if (dailyLogDateInfo != null)
                    dailyLogDateInfo.setText("");
                if (feedbackTextInput != null)
                    feedbackTextInput.setText("");
                if (ratingGroup != null)
                    ratingGroup.clearCheck();
            } catch (Exception ignore) {
            }
        });
    }
}

