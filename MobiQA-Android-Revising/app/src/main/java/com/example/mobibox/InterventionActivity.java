package com.example.mobibox;

import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.CheckBox;
import android.widget.RadioGroup;
import android.widget.Toast;
import android.widget.TextView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.ItemTouchHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

// 导入拖动排序相关类
import androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback;
import static androidx.recyclerview.widget.ItemTouchHelper.UP;
import static androidx.recyclerview.widget.ItemTouchHelper.DOWN;
import static androidx.recyclerview.widget.ItemTouchHelper.LEFT;
import static androidx.recyclerview.widget.ItemTouchHelper.RIGHT;

import com.google.android.flexbox.FlexDirection;
import com.google.android.flexbox.FlexWrap;
import com.google.android.flexbox.FlexboxLayoutManager;
import com.google.android.flexbox.JustifyContent;

import com.example.mobibox.util.TimeUtils;

import com.example.mobibox.network.HttpApiClient;
import com.example.mobibox.ui.adapters.AtomicAdapter;
import com.example.mobibox.ui.adapters.ItemMoveCallback;

public class InterventionActivity extends AppCompatActivity implements AtomicAdapter.OnActivityClickListener {

    // UI控件
    private TextView hourlyLogContent;
    private TextView interventionContent;
    private TextView interventionTimeInfo;
    private RadioGroup[] feedbackRadioGroups;
    private EditText feedbackInput;
    private Button submitButton;
    private Button btnRefreshIntervention; // Refresh Log and Suggestion Button

    // Log Feedback相关UI控件
    private RadioGroup[] logFeedbackRadioGroups;  // Only Q1 and Q2 now
    private EditText logFeedbackGroundTruth;
    private EditText logFeedbackSuggestions;
    private Button submitLogFeedbackButton;

    // Q2b preference selection (conditional, shown when Q2 is "no")
    private LinearLayout q2bContainer;
    private CheckBox cbPrefStudyWork;
    private CheckBox cbPrefSportsHealth;
    private CheckBox cbPrefPhoneUsage;
    private CheckBox cbPrefOther;
    private EditText etPrefOtherText;

    private ScrollView scrollView;
    private LinearLayout mainLayout;

    // 原子活动相关UI控件
    private RecyclerView recyclerViewSport;
    private RecyclerView recyclerViewAppCategory;
    private RecyclerView recyclerViewLocation;
    private RecyclerView recyclerViewMovement;
    private RecyclerView recyclerViewStep;
    private RecyclerView recyclerViewPhoneCategory;
    private RecyclerView recyclerViewStartTimestamp;
    private RecyclerView recyclerViewEndTimestamp;

    // 原子活动适配器
    private AtomicAdapter sportAdapter;
    private AtomicAdapter appCategoryAdapter;
    private AtomicAdapter locationAdapter;
    private AtomicAdapter movementAdapter;
    private AtomicAdapter stepAdapter;
    private AtomicAdapter phoneCategoryAdapter;
    private AtomicAdapter startTimestampAdapter;
    private AtomicAdapter endTimestampAdapter;

    // 原子活动数据
    private List<String> generatedSport = new ArrayList<>();
    private List<String> generatedAppCategory = new ArrayList<>();
    private List<String> generatedLocation = new ArrayList<>();
    private List<String> generatedMovement = new ArrayList<>();
    private List<String> generatedStep = new ArrayList<>();
    private List<String> generatedPhoneCategory = new ArrayList<>();
    private List<String> generatedStartTimestamp = new ArrayList<>();
    private List<String> generatedEndTimestamp = new ArrayList<>();

    // 数据
    private String userId;
    private String currentHourlyLog;
    private String currentIntervention;
    private String currentAtomicActivities;
    private String lastInterventionTimestamp;
    private String interventionStartTime;
    private String interventionEndTime;

    // IDs for feedback submission
    private String currentInterventionId = "";
    private String currentHourlyLogId = "";

    // 反馈数据
    private String[] feedbackAnswers = new String[6];

    // .Add新的UI控件变量
    private Button btnAddActivity;
    private Button btnRemoveActivity;
    private String selectedCategory = ""; // 记录当前选择的类别
    private int selectedPosition = -1; // 记录当前选择的位置

    // 快速Add按钮（只保留Sport Type）
    private Button btnAddSport;
    // 其他快速Add按钮已Remove，不再需要
    // private Button btnAddAppCategory;
    // private Button btnAddLocation;
    // private Button btnAddMovement;
    // private Button btnAddStep;
    // private Button btnAddPhoneCategory;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_intervention);

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

        // 检查是否从通知启动，如果是则显示通知传递的干预内容
        handleNotificationIntent(getIntent());

        // 设置刷新按钮点击事件
        btnRefreshIntervention.setOnClickListener(v -> {
            fetchHourlyLogFromServer(true); // 手动刷新Log，显示提示
            fetchInterventionFromServer(true); // 手动刷新Suggestion，显示提示
        });

        submitButton.setOnClickListener(v -> submitFeedback());
        submitLogFeedbackButton.setOnClickListener(v -> submitLogFeedback());

        Button btnRefreshAtomicActivities = findViewById(R.id.btn_refresh_atomic_activities);
        btnRefreshAtomicActivities.setOnClickListener(v -> {
            // 显示Toast提示用户正在刷新
            Toast.makeText(this, "Refreshing atomic activities...", Toast.LENGTH_SHORT).show();

            // 调用刷新原子活动的方法
            fetchAtomicActivitiesManually();
        });
    }

    private void initViews() {
        scrollView = findViewById(R.id.scroll_view);
        mainLayout = findViewById(R.id.main_layout);

        hourlyLogContent = findViewById(R.id.hourly_log_content);
        interventionContent = findViewById(R.id.intervention_content);
        interventionTimeInfo = findViewById(R.id.intervention_time_info);

        feedbackInput = findViewById(R.id.feedback_input);
        feedbackInput.setHint("Please enter quality suggestion reminder content (optional)");

        submitButton = findViewById(R.id.submit_button);

        // 初始化Log Feedback相关控件
        logFeedbackGroundTruth = findViewById(R.id.log_feedback_ground_truth);
        logFeedbackSuggestions = findViewById(R.id.log_feedback_suggestions);
        submitLogFeedbackButton = findViewById(R.id.submit_log_feedback_button);

        // 初始化Add/Remove按钮
        btnAddActivity = findViewById(R.id.btn_add_activity);
        btnRemoveActivity = findViewById(R.id.btn_remove_activity);

        // 设置按钮点击事件
        btnAddActivity.setOnClickListener(v -> showAddActivityDialog());
        btnRemoveActivity.setOnClickListener(v -> showRemoveActivityDialog());
        Button btnRefreshAtomicActivities = findViewById(R.id.btn_refresh_atomic_activities);

        // 初始化快速Add按钮（只保留Sport Type）
        btnAddSport = findViewById(R.id.btn_add_sport);
        // 其他快速Add按钮已Remove，不再需要
        // btnAddAppCategory = findViewById(R.id.btn_add_app_category);
        // btnAddLocation = findViewById(R.id.btn_add_location);
        // btnAddMovement = findViewById(R.id.btn_add_movement);
        // btnAddStep = findViewById(R.id.btn_add_step);
        // btnAddPhoneCategory = findViewById(R.id.btn_add_phone_category);

        // 设置快速Add按钮点击事件（只保留Sport Type）
        btnAddSport.setOnClickListener(v -> showQuickAddDialog("sport", "Sport Type"));
        // 其他快速Add按钮已Remove，不再需要
        // btnAddAppCategory.setOnClickListener(v -> showQuickAddDialog("appCategory",
        // "App Category"));
        // btnAddLocation.setOnClickListener(v -> showQuickAddDialog("location",
        // "Location Information"));
        // btnAddMovement.setOnClickListener(v -> showQuickAddDialog("movement",
        // "Movement Status"));
        // btnAddStep.setOnClickListener(v -> showQuickAddDialog("step", "Step
        // Information"));
        // btnAddPhoneCategory.setOnClickListener(v ->
        // showQuickAddDialog("phoneCategory", "Phone Category"));

        // 初始化反馈单选按钮组
        initFeedbackRadioGroups();
        initLogFeedbackRadioGroups();

        // 初始化原子活动视图并设置拖动排序
        initAtomicActivityViews();
        setupItemTouchHelpers();

        // Initialize refresh button
        btnRefreshIntervention = findViewById(R.id.btn_refresh_intervention);
    }

    private void initAtomicActivityViews() {
        recyclerViewSport = findViewById(R.id.recycler_view_sport);
        recyclerViewAppCategory = findViewById(R.id.recycler_view_app_category);
        recyclerViewLocation = findViewById(R.id.recycler_view_location);
        recyclerViewMovement = findViewById(R.id.recycler_view_movement);
        recyclerViewStep = findViewById(R.id.recycler_view_step);
        recyclerViewPhoneCategory = findViewById(R.id.recycler_view_phone_category);
        recyclerViewStartTimestamp = findViewById(R.id.recycler_view_start_timestamp);
        recyclerViewEndTimestamp = findViewById(R.id.recycler_view_end_timestamp);

        // 设置水平布局管理器
        FlexboxLayoutManager sportLayoutManager = new FlexboxLayoutManager(this);
        sportLayoutManager.setFlexDirection(FlexDirection.ROW); // 水平方向排列
        sportLayoutManager.setFlexWrap(FlexWrap.WRAP); // 允许换行
        sportLayoutManager.setJustifyContent(JustifyContent.FLEX_START); // 左对齐
        recyclerViewSport.setLayoutManager(sportLayoutManager);

        // 其他RecyclerView同理设置

        FlexboxLayoutManager appCategoryLayoutManager = new FlexboxLayoutManager(this);
        appCategoryLayoutManager.setFlexDirection(FlexDirection.ROW); // 水平方向排列
        appCategoryLayoutManager.setFlexWrap(FlexWrap.WRAP); // 允许换行
        appCategoryLayoutManager.setJustifyContent(JustifyContent.FLEX_START); // 左对齐
        recyclerViewAppCategory.setLayoutManager(appCategoryLayoutManager);

        // Location Information
        FlexboxLayoutManager locationLayoutManager = new FlexboxLayoutManager(this);
        locationLayoutManager.setFlexDirection(FlexDirection.ROW);
        locationLayoutManager.setFlexWrap(FlexWrap.WRAP);
        locationLayoutManager.setJustifyContent(JustifyContent.FLEX_START);
        recyclerViewLocation.setLayoutManager(locationLayoutManager);

        // Movement Status
        FlexboxLayoutManager movementLayoutManager = new FlexboxLayoutManager(this);
        movementLayoutManager.setFlexDirection(FlexDirection.ROW);
        movementLayoutManager.setFlexWrap(FlexWrap.WRAP);
        movementLayoutManager.setJustifyContent(JustifyContent.FLEX_START);
        recyclerViewMovement.setLayoutManager(movementLayoutManager);

        // Step Information
        FlexboxLayoutManager stepLayoutManager = new FlexboxLayoutManager(this);
        stepLayoutManager.setFlexDirection(FlexDirection.ROW);
        stepLayoutManager.setFlexWrap(FlexWrap.WRAP);
        stepLayoutManager.setJustifyContent(JustifyContent.FLEX_START);
        recyclerViewStep.setLayoutManager(stepLayoutManager);

        // Phone Category
        FlexboxLayoutManager phoneCategoryLayoutManager = new FlexboxLayoutManager(this);
        phoneCategoryLayoutManager.setFlexDirection(FlexDirection.ROW);
        phoneCategoryLayoutManager.setFlexWrap(FlexWrap.WRAP);
        phoneCategoryLayoutManager.setJustifyContent(JustifyContent.FLEX_START);
        recyclerViewPhoneCategory.setLayoutManager(phoneCategoryLayoutManager);
        recyclerViewStartTimestamp
                .setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        recyclerViewEndTimestamp.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        // 初始化适配器
        sportAdapter = new AtomicAdapter(generatedSport, this, "sport", true, this);
        appCategoryAdapter = new AtomicAdapter(generatedAppCategory, this, "appCategory", true, this);
        locationAdapter = new AtomicAdapter(generatedLocation, this, "location", true, this);
        movementAdapter = new AtomicAdapter(generatedMovement, this, "movement", true, this);
        stepAdapter = new AtomicAdapter(generatedStep, this, "step", true, this);
        phoneCategoryAdapter = new AtomicAdapter(generatedPhoneCategory, this, "phoneCategory", true, this);
        startTimestampAdapter = new AtomicAdapter(generatedStartTimestamp, this, "startTimestamp", true, this);
        endTimestampAdapter = new AtomicAdapter(generatedEndTimestamp, this, "endTimestamp", true, this);

        // 设置适配器
        recyclerViewSport.setAdapter(sportAdapter);
        recyclerViewAppCategory.setAdapter(appCategoryAdapter);
        recyclerViewLocation.setAdapter(locationAdapter);
        recyclerViewMovement.setAdapter(movementAdapter);
        recyclerViewStep.setAdapter(stepAdapter);
        recyclerViewPhoneCategory.setAdapter(phoneCategoryAdapter);
        recyclerViewStartTimestamp.setAdapter(startTimestampAdapter);
        recyclerViewEndTimestamp.setAdapter(endTimestampAdapter);
    }

    private void setupItemTouchHelpers() {
        setupTouchHelper(recyclerViewSport, sportAdapter);
        setupTouchHelper(recyclerViewAppCategory, appCategoryAdapter);
        setupTouchHelper(recyclerViewLocation, locationAdapter);
        setupTouchHelper(recyclerViewMovement, movementAdapter);
        setupTouchHelper(recyclerViewStep, stepAdapter);
        setupTouchHelper(recyclerViewPhoneCategory, phoneCategoryAdapter);
        setupTouchHelper(recyclerViewStartTimestamp, startTimestampAdapter);
        setupTouchHelper(recyclerViewEndTimestamp, endTimestampAdapter);
    }

    private void setupTouchHelper(RecyclerView recyclerView, AtomicAdapter adapter) {
        ItemMoveCallback callback = new ItemMoveCallback(adapter);
        ItemTouchHelper touchHelper = new ItemTouchHelper(callback);
        touchHelper.attachToRecyclerView(recyclerView);
    }

    private void fetchHourlyLogFromServer() {
        fetchHourlyLogFromServer(false); // 默认不显示提示
    }

    private void fetchHourlyLogFromServer(boolean showToast) {
        if (TextUtils.isEmpty(userId)) {
            return;
        }

        new Thread(() -> {
            Response response = null;
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .retryOnConnectionFailure(true)
                        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                        .writeTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                        .build();

                JSONObject payload = new JSONObject();
                payload.put("user", userId);
                payload.put("log_type", "hourly");

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
                    Log.w("HourlyLog", "Failed to get hourly log: " + response.code());
                    return;
                }

                if (response.body() == null) {
                    Log.w("HourlyLog", "Response body is empty");
                    return;
                }

                String responseData = response.body().string();
                JSONObject jsonResponse = new JSONObject(responseData);

                // 检查响应状态
                String status = jsonResponse.optString("status", "");
                if (!"success".equals(status)) {
                    Log.w("HourlyLog", "服务器返回非成功状态: " + status);
                    String message = jsonResponse.optString("message", "获取失败");
                    Log.w("HourlyLog", "错误信息: " + message);
                    return;
                }

                // Extract log_content from data object
                JSONObject data = jsonResponse.optJSONObject("data");
                if (data == null) {
                    Log.w("HourlyLog", "服务器返回数据为空");
                    return;
                }
                currentHourlyLog = data.optString("log_content", "");
                currentHourlyLogId = data.optString("id", "");  // Store the log ID for feedback
                saveHourlyLogToLocal();

                runOnUiThread(() -> {
                    if (hourlyLogContent != null) {
                        hourlyLogContent.setText(currentHourlyLog);
                    }
                });

            } catch (Exception e) {
                Log.w("HourlyLog", "Failed to fetch hourly log: " + e.getMessage());
            } finally {
                if (response != null) {
                    response.close();
                }
            }
        }).start();
    }

    private void fetchInterventionFromServer() {
        fetchInterventionFromServer(false); // 默认不显示提示
    }

    private void fetchInterventionFromServer(boolean showToast) {
        fetchInterventionFromServerWithRetry(0, showToast);
    }

    private void fetchInterventionFromServerWithRetry(int retryCount, boolean showToast) {
        final int MAX_RETRIES = 2; // 最多重试2次

        if (TextUtils.isEmpty(userId)) {
            makeToast("User not logged in, cannot fetch intervention");
            return;
        }

        new Thread(() -> {
            Response response = null;
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .retryOnConnectionFailure(true)
                        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                        .writeTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                        .callTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .build();

                JSONObject payload = new JSONObject();
                payload.put("user", userId);

                RequestBody body = RequestBody.create(
                        payload.toString(),
                        MediaType.get("application/json; charset=utf-8"));

                Request request = new Request.Builder()
                        .url(Constants.getInterventionUrl())
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
                            errorBody = "无法读取错误响应";
                        }
                    }
                    handleInterventionError(response.code(), errorBody);
                    return;
                }

                if (response.body() == null) {
                    Log.e("InterventionError", "响应体为空");
                    makeToast("服务器返回空响应");
                    return;
                }

                String responseData = response.body().string();
                Log.d("SERVER_RESPONSE", "收到的原始数据: " + responseData);

                JSONObject jsonResponse = new JSONObject(responseData);

                // 比较是否为新的干预内容
                SharedPreferences prefsBefore = getSharedPreferences("InterventionPrefs", Context.MODE_PRIVATE);
                String previousIntervention = prefsBefore.getString("last_intervention", "");

                // Extract from data object - backend returns {status: "success", data: {...}}
                JSONObject data = jsonResponse.optJSONObject("data");
                if (data == null) {
                    Log.e("InterventionError", "服务器返回数据为空");
                    makeToast("Server returned empty data");
                    return;
                }

                // Backend returns intervention_content, start_timestamp, end_timestamp, id
                currentIntervention = data.optString("intervention_content", "");
                interventionStartTime = data.optString("start_timestamp", "");
                interventionEndTime = data.optString("end_timestamp", "");
                currentInterventionId = data.optString("id", "");  // Store the intervention ID for feedback
                saveInterventionToLocal();

                runOnUiThread(() -> {
                    interventionContent.setText(currentIntervention);
                    interventionTimeInfo.setText(
                            String.format("from %s to %s",
                                    TimeUtils.formatUtcToHKTime(interventionStartTime),
                                    TimeUtils.formatUtcToHKTime(interventionEndTime)));
                    if (showToast) {
                        makeToast("Log refreshed");
                    }
                });
            } catch (Exception e) {
                String errorMsg = "Failed to fetch intervention (attempt " + (retryCount + 1) + "): ";
                boolean shouldRetry = false;

                if (e instanceof java.net.ProtocolException && e.getMessage() != null &&
                        e.getMessage().contains("unexpected end of stream")) {
                    errorMsg += "ProtocolException(服务器数据传输中断) - " + e.getMessage();
                    shouldRetry = true; // unexpected end of stream 可以重试
                    if (retryCount < MAX_RETRIES) {
                        Log.w("InterventionError", errorMsg + " - 将在2秒后重试", e);
                        makeToast("Server response interrupted, retrying... (" + (retryCount + 1) + "/" + MAX_RETRIES + ")");
                    } else {
                        Log.e("InterventionError", errorMsg + " - 已达最大重试次数", e);
                        makeToast("Server error persists, please try later or contact admin");
                    }
                } else if (e instanceof java.net.SocketTimeoutException) {
                    errorMsg += "SocketTimeout - " + e.getMessage();
                    shouldRetry = true;
                    if (retryCount >= MAX_RETRIES) {
                        makeToast("Request timed out, please check network");
                    }
                } else if (e instanceof java.net.UnknownHostException) {
                    errorMsg += "UnknownHost - " + e.getMessage();
                    makeToast("Network unavailable or server unreachable");
                } else if (e instanceof org.json.JSONException) {
                    errorMsg += "JSONException - " + e.getMessage();
                    makeToast("Server returned malformed data");
                } else if (e instanceof java.io.IOException) {
                    errorMsg += "IOException(" + e.getClass().getSimpleName() + ") - " + e.getMessage();
                    shouldRetry = true; // 其他IO异常也可以重试
                    if (retryCount >= MAX_RETRIES) {
                        makeToast("Network error: " + e.getMessage());
                    }
                } else {
                    errorMsg += e.getClass().getSimpleName() + " - " + e.getMessage();
                    makeToast("Failed to fetch intervention: " + e.getMessage());
                }

                if (!shouldRetry || retryCount >= MAX_RETRIES) {
                    Log.e("InterventionError", errorMsg, e);
                } else {
                    Log.w("InterventionError", errorMsg, e);
                }

                // 如果需要重试且未达最大重试次数，等待2秒后重试
                if (shouldRetry && retryCount < MAX_RETRIES) {
                    try {
                        Thread.sleep(2000); // 等待2秒
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                    fetchInterventionFromServerWithRetry(retryCount + 1, showToast);
                }
            } finally {
                // 确保关闭响应
                if (response != null) {
                    response.close();
                }
            }
        }).start();
    }

    private void handleInterventionError(int errorCode, String errorBody) {
        runOnUiThread(() -> {
            String serverMsg = extractServerMessage(errorBody);
            String message;
            switch (errorCode) {
                case 430:
                    message = "No intervention found (no data available yet)";
                    break;
                case 431:
                    message = "User ID missing (please log in again)";
                    break;
                case 432:
                    message = "Unknown user (please verify user info)";
                    break;
                default:
                    message = TextUtils.isEmpty(serverMsg) ? ("Server error: " + errorCode)
                            : ("Server error: " + errorCode + " - " + serverMsg);
            }
            makeToast(message);
            Log.e("InterventionError", "HTTP " + errorCode + ", body=" + errorBody);
        });
    }

    private String extractServerMessage(String errorBody) {
        if (TextUtils.isEmpty(errorBody))
            return "";
        try {
            JSONObject obj = new JSONObject(errorBody);
            if (obj.has("message"))
                return obj.optString("message", "");
            if (obj.has("error"))
                return obj.optString("error", "");
        } catch (Exception ignore) {
            // 非JSON，截取前80字符作为摘要
        }
        return errorBody.length() > 80 ? errorBody.substring(0, 80) + "..." : errorBody;
    }

    private void makeToast(String message) {
        runOnUiThread(() -> Toast.makeText(InterventionActivity.this, message, Toast.LENGTH_SHORT).show());
    }

    private void saveInterventionToLocal() {
        SharedPreferences prefs = getSharedPreferences("InterventionPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("last_intervention", currentIntervention);
        editor.putString("intervention_start_time", interventionStartTime);
        editor.putString("intervention_end_time", interventionEndTime);
        editor.putString("last_intervention_id", currentInterventionId);  // Save intervention ID
        editor.putString("last_intervention_timestamp", String.valueOf(System.currentTimeMillis()));
        editor.apply();
    }

    private void saveHourlyLogToLocal() {
        SharedPreferences prefs = getSharedPreferences("InterventionPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("last_hourly_log", currentHourlyLog);
        editor.putString("last_hourly_log_id", currentHourlyLogId);  // Save log ID
        editor.apply();
    }

    private void initFeedbackRadioGroups() {
        feedbackRadioGroups = new RadioGroup[6];
        feedbackRadioGroups[0] = findViewById(R.id.feedback_group_1);
        feedbackRadioGroups[1] = findViewById(R.id.feedback_group_2);
        feedbackRadioGroups[2] = findViewById(R.id.feedback_group_3);
        feedbackRadioGroups[3] = findViewById(R.id.feedback_group_4);
        feedbackRadioGroups[4] = findViewById(R.id.feedback_group_5);
        feedbackRadioGroups[5] = findViewById(R.id.feedback_group_6);
    }

    private void initLogFeedbackRadioGroups() {
        // Initialize Q1 and Q2 RadioGroups (only 2 questions now)
        logFeedbackRadioGroups = new RadioGroup[2];
        logFeedbackRadioGroups[0] = findViewById(R.id.log_feedback_group_1);
        logFeedbackRadioGroups[1] = findViewById(R.id.log_feedback_group_2);

        // Initialize Q2b preference selection components
        q2bContainer = findViewById(R.id.log_feedback_q2b_container);
        cbPrefStudyWork = findViewById(R.id.log_feedback_pref_study_work);
        cbPrefSportsHealth = findViewById(R.id.log_feedback_pref_sports_health);
        cbPrefPhoneUsage = findViewById(R.id.log_feedback_pref_phone_usage);
        cbPrefOther = findViewById(R.id.log_feedback_pref_other);
        etPrefOtherText = findViewById(R.id.log_feedback_pref_other_text);

        // Set up listener for Q2 RadioGroup to show/hide Q2b container
        logFeedbackRadioGroups[1].setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.log_feedback_2_no) {
                // Q2 is "No" - show preference selection
                q2bContainer.setVisibility(View.VISIBLE);
            } else if (checkedId == R.id.log_feedback_2_yes) {
                // Q2 is "Yes" - hide preference selection
                q2bContainer.setVisibility(View.GONE);
            }
        });

        // Set up listener for "Other" checkbox to show/hide text input
        cbPrefOther.setOnCheckedChangeListener((buttonView, isChecked) -> {
            etPrefOtherText.setVisibility(isChecked ? View.VISIBLE : View.GONE);
            if (!isChecked) {
                etPrefOtherText.setText("");  // Clear text when unchecked
            }
        });
    }

    private void loadLocalData() {
        SharedPreferences prefs = getSharedPreferences("InterventionPrefs", Context.MODE_PRIVATE);
        currentHourlyLog = prefs.getString("last_hourly_log", "");
        currentIntervention = prefs.getString("last_intervention", "");
        currentAtomicActivities = prefs.getString("last_atomic_activities", "");
        lastInterventionTimestamp = prefs.getString("last_intervention_timestamp", "");
        interventionStartTime = prefs.getString("intervention_start_time", "");
        interventionEndTime = prefs.getString("intervention_end_time", "");
        // Restore IDs for feedback submission
        currentHourlyLogId = prefs.getString("last_hourly_log_id", "");
        currentInterventionId = prefs.getString("last_intervention_id", "");

        if (!TextUtils.isEmpty(currentHourlyLog)) {
            hourlyLogContent.setText(currentHourlyLog);
        } else {
            hourlyLogContent.setText("No hourly summary available");
        }

        if (!TextUtils.isEmpty(currentIntervention)) {
            interventionContent.setText(currentIntervention);
            interventionTimeInfo.setText(String.format("from %s to %s",
                    TimeUtils.formatUtcToHKTime(interventionStartTime),
                    TimeUtils.formatUtcToHKTime(interventionEndTime)));
        } else {
            interventionContent.setText("No intervention content available");
        }

        if (!TextUtils.isEmpty(currentAtomicActivities)) {
            parseAndDisplayAtomicActivities(currentAtomicActivities);
        }
    }

    private void parseAndDisplayAtomicActivities(String activitiesJson) {
        try {
            JSONObject activityObject = new JSONObject(activitiesJson);

            // Filter out "unknown" labels from sport/HAR activities
            generatedSport = parseActivities(activityObject, "sport", true);
            generatedAppCategory = parseActivities(activityObject, "appCategory", false);
            generatedLocation = parseActivities(activityObject, "location", false);
            generatedMovement = parseActivities(activityObject, "movement", false);
            // Backend returns "stepCategory", but local JSON uses "step" (mapped in fetch code)
            generatedStep = parseActivities(activityObject, "step", false);
            generatedPhoneCategory = parseActivities(activityObject, "phoneCategory", false);
            generatedStartTimestamp = parseActivities(activityObject, "startTimestamp", false);
            generatedEndTimestamp = parseActivities(activityObject, "endTimestamp", false);

            updateAdapters();
        } catch (JSONException e) {
            Log.e("AtomicParseError", "Failed to parse atomic activities: " + e.getMessage());
        }
    }

    private List<String> parseActivities(JSONObject obj, String key, boolean filterUnknown) throws JSONException {
        List<String> list = new ArrayList<>();
        if (obj.has(key)) {
            JSONArray array = obj.getJSONArray(key);
            for (int i = 0; i < array.length(); i++) {
                String value = array.getString(i);
                // Filter out "unknown" labels when filterUnknown is true
                if (!filterUnknown || !"unknown".equalsIgnoreCase(value)) {
                    list.add(value);
                }
            }
        }
        return list;
    }

    // Overloaded method for backward compatibility
    private List<String> parseActivities(JSONObject obj, String key) throws JSONException {
        return parseActivities(obj, key, false);
    }

    private void updateAdapters() {
        sportAdapter.updateData(generatedSport);
        appCategoryAdapter.updateData(generatedAppCategory);
        locationAdapter.updateData(generatedLocation);
        movementAdapter.updateData(generatedMovement);
        stepAdapter.updateData(generatedStep);
        phoneCategoryAdapter.updateData(generatedPhoneCategory);
        startTimestampAdapter.updateData(generatedStartTimestamp);
        endTimestampAdapter.updateData(generatedEndTimestamp);
    }

    private void submitFeedback() {
        if (!validateFeedback()) {
            Toast.makeText(this, "Please complete all 6 multiple choice questions for Suggestion feedback", Toast.LENGTH_SHORT).show();
            return;
        }

        collectFeedbackData();
        submitSuggestionFeedbackToServer();
    }

    private void submitLogFeedback() {
        // Debug: log current state
        Log.d("LogFeedbackDebug", "userId: " + userId);
        Log.d("LogFeedbackDebug", "currentHourlyLog: " + (TextUtils.isEmpty(currentHourlyLog) ? "empty" : "has content"));
        Log.d("LogFeedbackDebug", "currentHourlyLogId: " + currentHourlyLogId);

        String validationError = validateLogFeedbackWithMessage();
        if (validationError != null) {
            Log.d("LogFeedbackDebug", "Validation failed: " + validationError);
            Toast.makeText(this, validationError, Toast.LENGTH_LONG).show();
            return;
        }

        // 调试：显示即将提交的答案
        String debugInfo = "准备提交:\n";
        debugInfo += "Q1: " + getLogFeedbackAnswer(0) + "\n";
        debugInfo += "Q2: " + getLogFeedbackAnswer(1) + "\n";
        if ("no".equals(getLogFeedbackAnswer(1))) {
            debugInfo += "Q2 Preference: " + getQ2Preference() + "\n";
        }
        debugInfo += "Ground Truth: " + (logFeedbackGroundTruth.getText().toString().isEmpty() ? "空" : "已填写");
        Log.d("LogFeedbackDebug", debugInfo);

        submitLogFeedbackToServer();
    }

    private boolean validateFeedback() {
        // 检查6道选择题
        for (RadioGroup group : feedbackRadioGroups) {
            if (group.getCheckedRadioButtonId() == -1) {
                return false;
            }
        }

        // reminder content 是选填的，不需要验证

        return true;
    }

    private boolean validateLogFeedback() {
        // 检查2道选择题 (Q1 and Q2 only)
        for (RadioGroup group : logFeedbackRadioGroups) {
            if (group.getCheckedRadioButtonId() == -1) {
                return false;
            }
        }

        // If Q2 is "No", check Q2b preference selection
        if (logFeedbackRadioGroups[1].getCheckedRadioButtonId() == R.id.log_feedback_2_no) {
            if (!isAtLeastOneQ2bCheckboxChecked()) {
                return false;
            }
            // If "Other" is checked, text input is required
            if (cbPrefOther.isChecked() && TextUtils.isEmpty(etPrefOtherText.getText().toString().trim())) {
                return false;
            }
        }

        // 检查必答的Ground Truth
        if (TextUtils.isEmpty(logFeedbackGroundTruth.getText().toString().trim())) {
            return false;
        }

        return true;
    }

    private boolean isAtLeastOneQ2bCheckboxChecked() {
        return cbPrefStudyWork.isChecked() || cbPrefSportsHealth.isChecked()
            || cbPrefPhoneUsage.isChecked() || cbPrefOther.isChecked();
    }

    private String validateLogFeedbackWithMessage() {
        // 检查logFeedbackRadioGroups是否已初始化
        if (logFeedbackRadioGroups == null) {
            return "System error: Log Feedback not properly initialized";
        }

        // 检查Q1和Q2选择题
        for (int i = 0; i < logFeedbackRadioGroups.length; i++) {
            if (logFeedbackRadioGroups[i] == null) {
                return "System error: Log Feedback question " + (i + 1) + " not properly initialized";
            }
            if (logFeedbackRadioGroups[i].getCheckedRadioButtonId() == -1) {
                return "Please complete Log Feedback question " + (i + 1);
            }
        }

        // If Q2 is "No", validate Q2b preference selection
        if (logFeedbackRadioGroups[1].getCheckedRadioButtonId() == R.id.log_feedback_2_no) {
            if (!isAtLeastOneQ2bCheckboxChecked()) {
                return "Please select at least one content preference category";
            }
            // If "Other" is checked, text input is required
            if (cbPrefOther.isChecked() && TextUtils.isEmpty(etPrefOtherText.getText().toString().trim())) {
                return "Please specify details for 'Other' option";
            }
        }

        // 检查必答的Ground Truth
        if (logFeedbackGroundTruth == null) {
            return "System error: Ground Truth input not properly initialized";
        }
        if (TextUtils.isEmpty(logFeedbackGroundTruth.getText().toString().trim())) {
            return "Please fill in Ground Truth (required)";
        }

        return null; // 验证通过
    }

    private boolean validateAllFeedback() {
        // 验证 Log Feedback（2道选择题 + Q2b条件选择 + Ground Truth必填）
        if (!validateLogFeedback()) {
            return false;
        }

        // 验证 Suggestion Feedback（6道选择题 + 填空题必填）
        if (!validateFeedback()) {
            return false;
        }

        return true;
    }

    private void collectFeedbackData() {
        for (int i = 0; i < feedbackRadioGroups.length; i++) {
            int checkedId = feedbackRadioGroups[i].getCheckedRadioButtonId();
            feedbackAnswers[i] = getFeedbackAnswerFromId(checkedId, i);
        }
    }

    // 【修改1：补全选择题答案映射逻辑】
    private String getFeedbackAnswerFromId(int radioId, int questionIndex) {
        if (questionIndex == 0) {
            if (radioId == R.id.feedback_1_yes)
                return "yes";
            if (radioId == R.id.feedback_1_no)
                return "no";
        } else if (questionIndex == 1) {
            if (radioId == R.id.feedback_2_1)
                return "1";
            if (radioId == R.id.feedback_2_2)
                return "2";
            if (radioId == R.id.feedback_2_3)
                return "3";
            if (radioId == R.id.feedback_2_4)
                return "4";
            if (radioId == R.id.feedback_2_5)
                return "5";
        } else if (questionIndex == 2) {
            if (radioId == R.id.feedback_3_1)
                return "1";
            if (radioId == R.id.feedback_3_2)
                return "2";
            if (radioId == R.id.feedback_3_3)
                return "3";
            if (radioId == R.id.feedback_3_4)
                return "4";
            if (radioId == R.id.feedback_3_5)
                return "5";
        } else if (questionIndex == 3) {
            if (radioId == R.id.feedback_4_1)
                return "1";
            if (radioId == R.id.feedback_4_2)
                return "2";
            if (radioId == R.id.feedback_4_3)
                return "3";
            if (radioId == R.id.feedback_4_4)
                return "4";
            if (radioId == R.id.feedback_4_5)
                return "5";
        } else if (questionIndex == 4) {
            if (radioId == R.id.feedback_5_1)
                return "1";
            if (radioId == R.id.feedback_5_2)
                return "2";
            if (radioId == R.id.feedback_5_3)
                return "3";
            if (radioId == R.id.feedback_5_4)
                return "4";
            if (radioId == R.id.feedback_5_5)
                return "5";
        } else if (questionIndex == 5) {
            if (radioId == R.id.feedback_6_1)
                return "1";
            if (radioId == R.id.feedback_6_2)
                return "2";
            if (radioId == R.id.feedback_6_3)
                return "3";
            if (radioId == R.id.feedback_6_4)
                return "4";
            if (radioId == R.id.feedback_6_5)
                return "5";
        }
        return ""; // 无效选项（理论上不会触发，因validateFeedback已校验）
    }

    // 获取Log Feedback的选择题答案
    private String getLogFeedbackAnswer(int questionIndex) {
        if (logFeedbackRadioGroups == null || questionIndex >= logFeedbackRadioGroups.length) {
            Log.e("LogFeedback", "logFeedbackRadioGroups未初始化或索引越界: " + questionIndex);
            return "";
        }

        RadioGroup group = logFeedbackRadioGroups[questionIndex];
        if (group == null) {
            Log.e("LogFeedback", "RadioGroup为null，问题索引: " + questionIndex);
            return "";
        }

        int checkedId = group.getCheckedRadioButtonId();
        Log.d("LogFeedback", "问题 " + (questionIndex + 1) + " 选中的ID: " + checkedId);

        if (questionIndex == 0) {
            // Question 1: 0-5 (Log Quality/Accuracy Score)
            if (checkedId == R.id.log_feedback_1_0)
                return "0";
            if (checkedId == R.id.log_feedback_1_1)
                return "1";
            if (checkedId == R.id.log_feedback_1_2)
                return "2";
            if (checkedId == R.id.log_feedback_1_3)
                return "3";
            if (checkedId == R.id.log_feedback_1_4)
                return "4";
            if (checkedId == R.id.log_feedback_1_5)
                return "5";
        } else if (questionIndex == 1) {
            // Question 2: Yes/No (Content Preference Match)
            if (checkedId == R.id.log_feedback_2_yes)
                return "yes";
            if (checkedId == R.id.log_feedback_2_no)
                return "no";
        }

        Log.e("LogFeedback", "无法匹配答案，问题: " + (questionIndex + 1) + ", checkedId: " + checkedId);
        return "";
    }

    // Get Q2 preference categories as comma-separated string (when Q2 is "no")
    private String getQ2Preference() {
        StringBuilder preferences = new StringBuilder();

        if (cbPrefStudyWork.isChecked()) {
            if (preferences.length() > 0) preferences.append(",");
            preferences.append("学习和工作");
        }
        if (cbPrefSportsHealth.isChecked()) {
            if (preferences.length() > 0) preferences.append(",");
            preferences.append("运动健康");
        }
        if (cbPrefPhoneUsage.isChecked()) {
            if (preferences.length() > 0) preferences.append(",");
            preferences.append("手机使用");
        }
        if (cbPrefOther.isChecked()) {
            String otherText = etPrefOtherText.getText().toString().trim();
            if (preferences.length() > 0) preferences.append(",");
            if (!TextUtils.isEmpty(otherText)) {
                preferences.append(otherText);
            } else {
                preferences.append("其他");
            }
        }

        return preferences.toString();
    }

    // 清空Log Feedback表单
    private void clearLogFeedbackForm() {
        for (RadioGroup group : logFeedbackRadioGroups) {
            group.clearCheck();
        }
        // Clear Q2b preference checkboxes
        cbPrefStudyWork.setChecked(false);
        cbPrefSportsHealth.setChecked(false);
        cbPrefPhoneUsage.setChecked(false);
        cbPrefOther.setChecked(false);
        etPrefOtherText.setText("");
        q2bContainer.setVisibility(View.GONE);
        // Clear text fields
        logFeedbackGroundTruth.setText("");
        logFeedbackSuggestions.setText("");
    }

    // 提交Log Feedback到服务器
    private void submitLogFeedbackToServer() {
        Log.d("LogFeedbackSubmit", "submitLogFeedbackToServer called");
        Log.d("LogFeedbackSubmit", "userId: " + userId);
        Log.d("LogFeedbackSubmit", "currentHourlyLog empty: " + TextUtils.isEmpty(currentHourlyLog));
        Log.d("LogFeedbackSubmit", "currentHourlyLogId: " + currentHourlyLogId);

        if (TextUtils.isEmpty(userId) || TextUtils.isEmpty(currentHourlyLog)) {
            Log.e("LogFeedbackSubmit", "Validation failed: userId=" + userId + ", hourlyLog empty=" + TextUtils.isEmpty(currentHourlyLog));
            Toast.makeText(this, "Incomplete data, cannot submit Log feedback", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check if we have the log ID
        if (currentHourlyLogId.isEmpty()) {
            Log.e("LogFeedbackSubmit", "Log ID is -1, need to refresh data first");
            Toast.makeText(this, "Log ID missing, please refresh data first", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d("LogFeedbackSubmit", "All validations passed, preparing request");

        new Thread(() -> {
            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .retryOnConnectionFailure(true)
                        .build();

                JSONObject feedbackJson = new JSONObject();

                // Backend expects: user (string), summary_logs_id (int), q1, q2, q2_preference, ground_truth, suggestions
                feedbackJson.put("user", userId);  // userId is a string identifier
                feedbackJson.put("summary_logs_id", currentHourlyLogId);

                // Log Feedback Q1 (accuracy score 0-5) and Q2 (yes/no)
                String q1 = getLogFeedbackAnswer(0);
                String q2 = getLogFeedbackAnswer(1);

                // 检查是否有空答案
                if (q1.isEmpty() || q2.isEmpty()) {
                    runOnUiThread(() -> {
                        String errorMsg = "Data collection failed:\n";
                        if (q1.isEmpty())
                            errorMsg += "Q1 answer not collected\n";
                        if (q2.isEmpty())
                            errorMsg += "Q2 answer not collected\n";
                        errorMsg += "Please re-select and submit";
                        Toast.makeText(InterventionActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                    });
                    return;
                }

                // Put Q1 and Q2 answers
                feedbackJson.put("q1", q1);
                feedbackJson.put("q2", q2);

                // If Q2 is "no", add q2_preference
                if ("no".equals(q2)) {
                    String q2Preference = getQ2Preference();
                    if (!TextUtils.isEmpty(q2Preference)) {
                        feedbackJson.put("q2_preference", q2Preference);
                    }
                }

                // Ground truth (standard answer)
                String groundTruth = logFeedbackGroundTruth.getText().toString().trim();
                if (!TextUtils.isEmpty(groundTruth)) {
                    feedbackJson.put("ground_truth", groundTruth);
                }

                // Suggestions (optimization suggestions)
                String suggestions = logFeedbackSuggestions.getText().toString().trim();
                if (!TextUtils.isEmpty(suggestions)) {
                    feedbackJson.put("suggestions", suggestions);
                }

                Log.d("LogFeedbackSubmit", "提交的Log反馈数据: " + feedbackJson.toString());

                RequestBody body = RequestBody.create(
                        feedbackJson.toString(),
                        MediaType.get("application/json; charset=utf-8"));

                Request request = new Request.Builder()
                        .url(Constants.getSendLogFeedbackUrl())
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .build();

                Log.d("LogFeedbackSubmit", "发送请求到: " + Constants.getSendLogFeedbackUrl());

                Response response = null;
                try {
                    response = client.newCall(request).execute();

                    if (!response.isSuccessful()) {
                        String errorBody = "";
                        if (response.body() != null) {
                            try {
                                errorBody = response.body().string();
                            } catch (IOException e) {
                                errorBody = "无法读取错误响应";
                            }
                        }
                        int errorCode = response.code();
                        final String finalErrorBody = errorBody;
                        Log.e("LogFeedbackError", "提交失败，错误码: " + errorCode);
                        Log.e("LogFeedbackError", "错误详情: " + finalErrorBody);

                        runOnUiThread(() -> {
                            String errorMsg;
                            switch (errorCode) {
                                case 431:
                                    errorMsg = "User ID missing, please log in again";
                                    break;
                                case 432:
                                    errorMsg = "Unknown user, please check user info";
                                    break;
                                case 433:
                                    errorMsg = "Log content missing, cannot submit feedback";
                                    break;
                                case 434:
                                    errorMsg = "Log feedback incomplete, please check all MC questions and Ground Truth are filled";
                                    break;
                                case 500:
                                    errorMsg = "Server internal error, please try later";
                                    break;
                                default:
                                    errorMsg = "Submission failed, error code: " + errorCode;
                                    if (!TextUtils.isEmpty(finalErrorBody)) {
                                        errorMsg = errorMsg + "\n详情：" + finalErrorBody;
                                    }
                            }
                            Toast.makeText(InterventionActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                        });
                        return;
                    }

                    String responseBody = response.body() != null ? response.body().string() : "";
                    Log.d("LogFeedbackSubmit", "响应状态码: " + response.code());
                    Log.d("LogFeedbackSubmit", "响应内容: " + responseBody);

                    runOnUiThread(() -> {
                        Toast.makeText(InterventionActivity.this, "Log feedback submitted successfully！", Toast.LENGTH_SHORT).show();
                        // 清空Log Feedback表单
                        clearLogFeedbackForm();
                    });
                } finally {
                    if (response != null) {
                        response.close();
                    }
                }
            } catch (Exception e) {
                Log.e("LogFeedbackSubmitError", "提交失败: " + e.getMessage(), e);
                runOnUiThread(() -> Toast.makeText(InterventionActivity.this,
                        "Log feedback submission failed, please retry", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // 【修改2：完善反馈提交逻辑，增加错误码处理】
    private void submitSuggestionFeedbackToServer() {
        if (TextUtils.isEmpty(userId) || TextUtils.isEmpty(currentIntervention)) {
            Toast.makeText(this, "Incomplete data, cannot submit Suggestion feedback", Toast.LENGTH_SHORT).show();
            return;
        }

        // Check if we have the intervention ID
        if (currentInterventionId.isEmpty()) {
            Toast.makeText(this, "Intervention ID missing, please refresh data first", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                // 配置OkHttpClient，Add超时和连接设置以避免"unexpected end of stream"错误
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                        .retryOnConnectionFailure(true) // 自动重试连接失败
                        .build();

                JSONObject feedbackJson = new JSONObject();

                // Backend expects: user, intervention_id, mc1-mc6, feedback
                feedbackJson.put("user", userId);
                feedbackJson.put("intervention_id", currentInterventionId);

                // Multiple choice answers - backend expects strings (mc1-mc6 are optional)
                // Only include non-null, non-empty values
                if (feedbackAnswers.length >= 6) {
                    if (feedbackAnswers[0] != null && !feedbackAnswers[0].isEmpty())
                        feedbackJson.put("mc1", feedbackAnswers[0]);
                    if (feedbackAnswers[1] != null && !feedbackAnswers[1].isEmpty())
                        feedbackJson.put("mc2", feedbackAnswers[1]);
                    if (feedbackAnswers[2] != null && !feedbackAnswers[2].isEmpty())
                        feedbackJson.put("mc3", feedbackAnswers[2]);
                    if (feedbackAnswers[3] != null && !feedbackAnswers[3].isEmpty())
                        feedbackJson.put("mc4", feedbackAnswers[3]);
                    if (feedbackAnswers[4] != null && !feedbackAnswers[4].isEmpty())
                        feedbackJson.put("mc5", feedbackAnswers[4]);
                    if (feedbackAnswers[5] != null && !feedbackAnswers[5].isEmpty())
                        feedbackJson.put("mc6", feedbackAnswers[5]);
                }

                // Feedback text from input field - required by backend
                String feedbackText = feedbackInput.getText().toString().trim();
                if (feedbackText.isEmpty()) {
                    // Provide a default feedback text since backend requires it
                    feedbackText = "Submitted via app";
                }
                feedbackJson.put("feedback", feedbackText);

                // 打印请求数据用于调试
                Log.d("FeedbackSubmit", "提交的反馈数据: " + feedbackJson.toString());

                // 创建请求体
                RequestBody body = RequestBody.create(
                        feedbackJson.toString(),
                        MediaType.get("application/json; charset=utf-8"));

                Request request = new Request.Builder()
                        .url(Constants.getSendInterventionFeedbackUrl())
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .build();

                Log.d("FeedbackSubmit", "发送请求到: " + Constants.getSendInterventionFeedbackUrl());
                Log.d("FeedbackSubmit", "请求体大小: " + feedbackJson.toString().length() + " 字符");

                // 执行请求
                Response response = null;
                try {
                    response = client.newCall(request).execute();

                    if (!response.isSuccessful()) {
                        String errorBody = "";
                        if (response.body() != null) {
                            try {
                                errorBody = response.body().string();
                            } catch (IOException e) {
                                errorBody = "无法读取错误响应";
                            }
                        }
                        handleFeedbackError(response.code(), errorBody);
                        return;
                    }

                    String responseBody = response.body() != null ? response.body().string() : "";
                    Log.d("FeedbackSubmit", "响应状态码: " + response.code());
                    Log.d("FeedbackSubmit", "响应内容: " + responseBody);

                    runOnUiThread(() -> {
                        // 成功后清空所有后台缓存与显示内容
                        clearAllInterventionCaches();
                        Toast.makeText(InterventionActivity.this, "Feedback submitted successfully！", Toast.LENGTH_SHORT).show();
                        finish(); // 成功后关闭页面
                    });
                } finally {
                    // 确保关闭响应
                    if (response != null) {
                        response.close();
                    }
                }
            } catch (java.net.ProtocolException e) {
                Log.e("FeedbackSubmitError", "协议错误(可能是服务器提前关闭连接): " + e.getMessage(), e);
                runOnUiThread(() -> Toast.makeText(InterventionActivity.this,
                        "Submission failed: server connection error, please retry later", Toast.LENGTH_LONG).show());
            } catch (java.net.SocketTimeoutException e) {
                Log.e("FeedbackSubmitError", "请求超时: " + e.getMessage(), e);
                runOnUiThread(() -> Toast.makeText(InterventionActivity.this,
                        "Submission failed: request timed out, please check network", Toast.LENGTH_LONG).show());
            } catch (java.io.IOException e) {
                Log.e("FeedbackSubmitError", "网络IO错误: " + e.getMessage(), e);
                runOnUiThread(() -> Toast.makeText(InterventionActivity.this,
                        "Submission failed: network error, please check connection", Toast.LENGTH_LONG).show());
            } catch (Exception e) {
                Log.e("FeedbackSubmitError", "提交失败: " + e.getMessage(), e);
                runOnUiThread(() -> Toast.makeText(InterventionActivity.this,
                        "Submission failed, please retry", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // 处理反馈提交错误
    private void handleFeedbackError(int errorCode, String errorBody) {
        runOnUiThread(() -> {
            String errorMsg;
            switch (errorCode) {
                case 431:
                    errorMsg = "User ID missing, please log in again";
                    break;
                case 432:
                    errorMsg = "Unknown user, please check user info";
                    break;
                case 433:
                    errorMsg = "Intervention content missing, cannot submit feedback";
                    break;
                case 434:
                    errorMsg = "Feedback incomplete (MC or text fields missing)";
                    break;
                case 500:
                    errorMsg = "Server internal error, please try later";
                    break;
                default:
                    errorMsg = "Submission failed, error code: " + errorCode;
                    if (!TextUtils.isEmpty(errorBody)) {
                        errorMsg += "\n详情：" + errorBody;
                    }
            }
            Log.e("FeedbackError", "Code: " + errorCode + ", Body: " + errorBody);
            Toast.makeText(InterventionActivity.this, errorMsg, Toast.LENGTH_LONG).show();
        });
    }

    // 辅助方法：将List<String>转换为JSONArray
    private JSONArray convertListToJSONArray(List<String> list) {
        JSONArray array = new JSONArray();
        if (list != null) {
            for (String item : list) {
                if (!TextUtils.isEmpty(item)) {
                    array.put(item);
                }
            }
        }
        return array;
    }

    // 辅助方法：将答案转换为整数（第一题特殊处理）
    private Object convertAnswerToInt(String answer, int questionIndex) {
        if (questionIndex == 0) {
            // 第一题改为数值：yes -> 1, no -> 0
            if ("yes".equalsIgnoreCase(answer))
                return 1;
            if ("no".equalsIgnoreCase(answer))
                return 0;
            return 0; // 默认回退
        } else {
            // 其他题目返回整数
            try {
                return Integer.parseInt(answer);
            } catch (NumberFormatException e) {
                return 1; // 默认值
            }
        }
    }

    // 辅助方法：将活动列表Add到数组中
    private void addActivitiesToArray(JSONArray array, List<String> activities) {
        for (String activity : activities) {
            if (!TextUtils.isEmpty(activity)) {
                array.put(activity);
            }
        }
    }

    // 成功提交后，清空所有相关缓存与显示
    private void clearAllInterventionCaches() {

        try {
            // 1️⃣ 清空 InterventionPrefs（页面显示缓存）
            SharedPreferences interventionPrefs = getSharedPreferences("InterventionPrefs", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor1 = interventionPrefs.edit();
            editor1.clear(); // 清空所有数据
            editor1.commit();
            Log.d("InterventionActivity", "✅ InterventionPrefs 缓存已清空");

            // 2️⃣ 清空 InterventionCache（通知管理器缓存）
            SharedPreferences interventionCache = getSharedPreferences("InterventionCache", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor2 = interventionCache.edit();
            editor2.clear(); // 清空所有数据
            editor2.commit();
            Log.d("InterventionActivity", "✅ InterventionCache 缓存已清空");

            // 3️⃣ 清空 HourlyUpdateCache（Hourly Log 通知管理器缓存）
            SharedPreferences hourlyCache = getSharedPreferences("HourlyUpdateCache", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor3 = hourlyCache.edit();
            editor3.clear(); // 清空所有数据
            editor3.commit();
            Log.d("InterventionActivity", "✅ HourlyUpdateCache 缓存已清空");

            // 4️⃣ 清空 AtomicActivitiesPrefs（原子活动缓存）
            SharedPreferences atomicPrefs = getSharedPreferences("AtomicActivitiesPrefs", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor4 = atomicPrefs.edit();
            editor4.clear(); // 清空所有数据
            editor4.commit();
            Log.d("InterventionActivity", "✅ AtomicActivitiesPrefs 缓存已清空");

            Log.d("InterventionActivity", "✅✅✅ 所有缓存已完全清空！");
        } catch (Exception e) {
            Log.e("InterventionActivity", "清空缓存失败: " + e.getMessage(), e);
        }

        // 清空当前 UI 数据（如果页面仍在）
        runOnUiThread(() -> {
            try {
                if (hourlyLogContent != null)
                    hourlyLogContent.setText("No hourly summary available");
                if (interventionContent != null)
                    interventionContent.setText("No intervention content available");
                if (interventionTimeInfo != null)
                    interventionTimeInfo.setText("");
            } catch (Exception ignore) {
            }
        });
    }

    // 【修改3：实现原子活动点击接口方法】
    @Override
    public void onActivityClick(int position, String category, boolean isCustom) {
        // 此处可Add Atomic Activity项点击逻辑（如编辑、查看详情等）
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Edit Activity");
        EditText input = new EditText(this);
        input.setText(getActivityByCategory(category, position));
        builder.setView(input);

        builder.setPositiveButton("Confirm", (dialog, which) -> {
            String newValue = input.getText().toString().trim();
            if (!TextUtils.isEmpty(newValue)) {
                updateActivityByCategory(category, position, newValue);
            }
        });
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    // 辅助方法：根据分类获取活动内容
    private String getActivityByCategory(String category, int position) {
        switch (category) {
            case "sport":
                return sportAdapter.getActivityAt(position);
            case "appCategory":
                return appCategoryAdapter.getActivityAt(position);
            case "location":
                return locationAdapter.getActivityAt(position);
            case "movement":
                return movementAdapter.getActivityAt(position);
            case "step":
                return stepAdapter.getActivityAt(position);
            case "phoneCategory":
                return phoneCategoryAdapter.getActivityAt(position);
            case "startTimestamp":
                return startTimestampAdapter.getActivityAt(position);
            case "endTimestamp":
                return endTimestampAdapter.getActivityAt(position);
            default:
                return "";
        }
    }

    // 辅助方法：根据分类更新活动内容
    private void updateActivityByCategory(String category, int position, String newValue) {
        switch (category) {
            case "sport":
                sportAdapter.editItem(position, newValue);
                break;
            case "appCategory":
                appCategoryAdapter.editItem(position, newValue);
                break;
            case "location":
                locationAdapter.editItem(position, newValue);
                break;
            case "movement":
                movementAdapter.editItem(position, newValue);
                break;
            case "step":
                stepAdapter.editItem(position, newValue);
                break;
            case "phoneCategory":
                phoneCategoryAdapter.editItem(position, newValue);
                break;
            case "startTimestamp":
                startTimestampAdapter.editItem(position, newValue);
                break;
            case "endTimestamp":
                endTimestampAdapter.editItem(position, newValue);
                break;
        }
    }

    private void addActivityToCategory(String category, String content) {
        switch (category) {
            case "sport":
                sportAdapter.addItem(content);
                break;
            case "appCategory":
                appCategoryAdapter.addItem(content);
                break;
            case "location":
                locationAdapter.addItem(content);
                break;
            case "movement":
                movementAdapter.addItem(content);
                break;
            case "step":
                stepAdapter.addItem(content);
                break;
            case "phoneCategory":
                phoneCategoryAdapter.addItem(content);
                break;
        }
    }

    // 6. Add显示Remove活动对话框的方法
    private void showRemoveActivityDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Remove Atomic Activity");

        // 创建类别选择布局
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 40);

        TextView categoryLabel = new TextView(this);
        categoryLabel.setText("选择活动类别：");
        categoryLabel.setTextSize(16);
        layout.addView(categoryLabel);

        String[] categories = { "sport", "appCategory", "location", "movement", "step", "phoneCategory" };
        String[] categoryDisplayNames = { "sport type", "appCategory", "location", "movement", "step",
                "phoneCategory" };
        android.widget.Spinner categorySpinner = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> spinnerAdapter = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, categoryDisplayNames);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(spinnerAdapter);
        layout.addView(categorySpinner);

        TextView positionLabel = new TextView(this);
        positionLabel.setText("选择要Remove的位置（从0开始）：");
        positionLabel.setTextSize(16);
        positionLabel.setPadding(0, 20, 0, 0);
        layout.addView(positionLabel);

        EditText positionInput = new EditText(this);
        positionInput.setHint("请输入位置索引");
        positionInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        layout.addView(positionInput);

        builder.setView(layout);

        builder.setPositiveButton("Remove", (dialog, which) -> {
            int selectedIndex = categorySpinner.getSelectedItemPosition();
            String category = categories[selectedIndex];
            String positionStr = positionInput.getText().toString().trim();

            if (!TextUtils.isEmpty(positionStr)) {
                try {
                    int position = Integer.parseInt(positionStr);
                    if (removeActivityFromCategory(category, position)) {
                        Toast.makeText(this, "Activity removed successfully", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Invalid position index", Toast.LENGTH_SHORT).show();
                    }
                } catch (NumberFormatException e) {
                    Toast.makeText(this, "Please enter a valid number", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Position cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    // 7. Add根据类别Remove活动的方法
    private boolean removeActivityFromCategory(String category, int position) {
        switch (category) {
            case "sport":
                // Check against the adapter's item count for accuracy
                if (position >= 0 && position < sportAdapter.getItemCount()) {
                    sportAdapter.removeItem(position);
                    return true;
                }
                break;
            case "appCategory":
                if (position >= 0 && position < appCategoryAdapter.getItemCount()) {
                    appCategoryAdapter.removeItem(position);
                    return true;
                }
                break;
            case "location":
                if (position >= 0 && position < locationAdapter.getItemCount()) {
                    locationAdapter.removeItem(position);
                    return true;
                }
                break;
            case "movement":
                if (position >= 0 && position < movementAdapter.getItemCount()) {
                    movementAdapter.removeItem(position);
                    return true;
                }
                break;
            case "step":
                if (position >= 0 && position < stepAdapter.getItemCount()) {
                    stepAdapter.removeItem(position);
                    return true;
                }
                break;
            case "phoneCategory":
                if (position >= 0 && position < phoneCategoryAdapter.getItemCount()) {
                    phoneCategoryAdapter.removeItem(position);
                    return true;
                }
                break;
        }
        return false;
    }

    private void showAddActivityDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add Atomic Activity");

        // 创建类别选择布局
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 40);

        TextView categoryLabel = new TextView(this);
        categoryLabel.setText("选择活动类别：");
        categoryLabel.setTextSize(16);
        layout.addView(categoryLabel);

        // 创建类别选择下拉框（不包含timestamp）
        String[] categories = { "sport", "appCategory", "location", "movement", "step", "phoneCategory" };
        String[] categoryDisplayNames = { "sport", "appCategory", "location", "movement", "step", "phoneCategory" };

        android.widget.Spinner categorySpinner = new android.widget.Spinner(this);
        android.widget.ArrayAdapter<String> spinnerAdapter = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, categoryDisplayNames);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        categorySpinner.setAdapter(spinnerAdapter);
        layout.addView(categorySpinner);

        TextView contentLabel = new TextView(this);
        contentLabel.setText("活动内容：");
        contentLabel.setTextSize(16);
        contentLabel.setPadding(0, 20, 0, 0);
        layout.addView(contentLabel);

        EditText contentInput = new EditText(this);
        contentInput.setHint("请输入活动内容");
        layout.addView(contentInput);

        builder.setView(layout);

        builder.setPositiveButton("Add", (dialog, which) -> {
            int selectedIndex = categorySpinner.getSelectedItemPosition();
            String category = categories[selectedIndex];
            String content = contentInput.getText().toString().trim();

            if (!TextUtils.isEmpty(content)) {
                addActivityToCategory(category, content);
                Toast.makeText(this, "Activity added successfully", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Activity content cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();
    }

    // 快速Add活动对话框（类别已预选）
    private void showQuickAddDialog(String category, String categoryDisplayName) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Add " + categoryDisplayName);

        // 创建输入框布局
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 40);

        TextView contentLabel = new TextView(this);
        contentLabel.setText("活动内容：");
        contentLabel.setTextSize(16);
        layout.addView(contentLabel);

        EditText contentInput = new EditText(this);
        contentInput.setHint("请输入活动内容");
        contentInput.setPadding(20, 20, 20, 20);
        layout.addView(contentInput);

        builder.setView(layout);

        builder.setPositiveButton("Add", (dialog, which) -> {
            String content = contentInput.getText().toString().trim();

            if (!TextUtils.isEmpty(content)) {
                addActivityToCategory(category, content);
                Toast.makeText(this, categoryDisplayName + " added successfully", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Activity content cannot be empty", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Cancel", null);
        builder.show();

        // 自动聚焦输入框并弹出键盘
        contentInput.requestFocus();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent); // 更新Intent

        // 处理新Intent传递的数据
        handleNotificationIntent(intent);
    }

    // 处理从通知传递过来的干预内容和 hourly log
    private void handleNotificationIntent(Intent intent) {
        if (intent == null) {
            return;
        }

        boolean hasUpdates = false;

        // 1. 处理 Hourly Log（来自 HourlyUpdateNotificationManager）
        String hourlyLogContentStr = intent.getStringExtra("hourly_log_content");
        String hourlyLogStartTime = intent.getStringExtra("hourly_log_start_time");
        String hourlyLogEndTime = intent.getStringExtra("hourly_log_end_time");

        if (!TextUtils.isEmpty(hourlyLogContentStr)) {
            currentHourlyLog = hourlyLogContentStr;
            this.hourlyLogContent.setText(hourlyLogContentStr);
            hasUpdates = true;
            Log.d("InterventionActivity", "已显示通知传递的 Hourly Log");
        }

        // 2. 处理 Intervention（来自 HourlyUpdateNotificationManager 或旧通知）
        String interventionContentStr = intent.getStringExtra("intervention_content");
        String interventionGenerationTime = intent.getStringExtra("intervention_generation_time");

        if (!TextUtils.isEmpty(interventionContentStr)) {
            currentIntervention = interventionContentStr;
            interventionContent.setText(interventionContentStr);
            hasUpdates = true;

            // 如果有生成时间，显示时间信息
            if (!TextUtils.isEmpty(interventionGenerationTime)) {
                interventionTimeInfo.setText(String.format("生成时间: %s",
                        TimeUtils.formatUtcToHKTime(interventionGenerationTime)));
            } else {
                // 尝试从InterventionPrefs加载时间信息（兼容旧通知）
                SharedPreferences interventionPrefs = getSharedPreferences("InterventionPrefs", MODE_PRIVATE);
                String startTime = interventionPrefs.getString("intervention_start_time", "");
                String endTime = interventionPrefs.getString("intervention_end_time", "");

                if (!TextUtils.isEmpty(startTime) && !TextUtils.isEmpty(endTime)) {
                    interventionStartTime = startTime;
                    interventionEndTime = endTime;
                    interventionTimeInfo.setText(String.format("from %s to %s",
                            TimeUtils.formatUtcToHKTime(startTime),
                            TimeUtils.formatUtcToHKTime(endTime)));
                }
            }

            Log.d("InterventionActivity", "已显示通知传递的 Intervention");
        } else {
            // 如果通知没有携带 intervention 内容，延迟后主动从服务器获取
            // 这样可以确保后端已经完全生成并写入了 intervention 数据
            Log.d("InterventionActivity", "通知未携带 intervention 内容，将延迟3秒后主动获取");
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                Log.d("InterventionActivity", "延迟获取 intervention 开始");
                fetchInterventionFromServer();
            }, 3000); // 延迟3秒，确保后端数据已经完全生成和写入
        }

        // 3. 显示提示信息
        if (hasUpdates) {
            Toast.makeText(this, "Showing latest content", Toast.LENGTH_SHORT).show();
        } else {
            Log.d("InterventionActivity", "通知未携带内容，将从服务器获取最新数据");
        }
    }

    private void fetchAtomicActivitiesManually() {
        if (TextUtils.isEmpty(userId)) {
            Toast.makeText(this, "User not logged in, cannot get atomic activities", Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            String requestUrl = Constants.getAtomicActivitiesUrl();
            JSONObject payload = null;

            try {
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                        .build();

                // 手动刷新时，从DataService的last_fetch_timestamp计算duration
                // 但确保最小为300秒（5分钟），避免duration过小导致数据不足
                SharedPreferences atomicPrefs = getSharedPreferences("AtomicActivitiesPrefs", MODE_PRIVATE);
                long lastFetchTime = atomicPrefs.getLong("last_fetch_timestamp", 0);

                int duration;
                if (lastFetchTime == 0) {
                    // 首次获取，使用较大的默认值
                    duration = 3600; // 1小时
                    Log.d("AtomicActivities", "首次手动刷新，duration设为3600秒(1小时)");
                } else {
                    long currentTime = System.currentTimeMillis();
                    int calculatedDuration = (int) ((currentTime - lastFetchTime) / 1000);
                    // 确保最小300秒（5分钟）
                    duration = Math.max(calculatedDuration, 300);
                    Log.d("AtomicActivities",
                            "手动刷新: 计算duration=" + calculatedDuration + "秒, 使用duration=" + duration + "秒");
                }

                payload = new JSONObject();
                payload.put("user", userId);
                payload.put("duration", duration);

                Log.d("AtomicActivities", "请求参数: " + payload.toString());

                RequestBody body = RequestBody.create(
                        payload.toString(),
                        MediaType.get("application/json; charset=utf-8"));

                Log.d("AtomicActivities", "请求URL: " + requestUrl);

                okhttp3.Request request = new okhttp3.Request.Builder()
                        .url(requestUrl)
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    int statusCode = response.code();
                    Log.d("AtomicActivities", "响应状态码: " + statusCode);

                    if (response.body() != null) {
                        String responseData = response.body().string();
                        Log.d("AtomicActivities", "响应内容: " + responseData);

                        JSONObject jsonResponse = new JSONObject(responseData);
                        String status = jsonResponse.optString("status", "");
                        String message = jsonResponse.optString("message", "");

                        Log.d("AtomicActivities", "status字段: " + status);
                        Log.d("AtomicActivities", "message字段: " + message);

                        if ("success".equals(status)) {
                            // 解析完整的响应数据
                            JSONObject data = jsonResponse.getJSONObject("data");

                            // 构建完整的原子活动JSON
                            JSONObject activities = new JSONObject();
                            activities.put("sport", data.optJSONArray("sport"));
                            activities.put("appCategory", data.optJSONArray("appCategory"));
                            activities.put("location", data.optJSONArray("location"));
                            activities.put("movement", data.optJSONArray("movement"));
                            activities.put("step", data.optJSONArray("stepCategory"));
                            activities.put("phoneCategory", data.optJSONArray("phoneCategory"));

                            // Add时间戳信息
                            String startTime = jsonResponse.optString("start_timestamp", "");
                            String endTime = jsonResponse.optString("end_timestamp", "");

                            JSONArray startTimestampArray = new JSONArray();
                            JSONArray endTimestampArray = new JSONArray();
                            if (!TextUtils.isEmpty(startTime)) {
                                startTimestampArray.put(startTime);
                            }
                            if (!TextUtils.isEmpty(endTime)) {
                                endTimestampArray.put(endTime);
                            }

                            activities.put("startTimestamp", startTimestampArray);
                            activities.put("endTimestamp", endTimestampArray);

                            // 保存到本地
                            SharedPreferences prefs = getSharedPreferences("InterventionPrefs", MODE_PRIVATE);
                            prefs.edit().putString("last_atomic_activities", activities.toString()).apply();

                            // 注意：手动刷新时不更新last_fetch_timestamp
                            // 保持DataService的自动获取逻辑不受影响，避免duration被重置

                            // 更新UI
                            runOnUiThread(() -> {
                                parseAndDisplayAtomicActivities(activities.toString());
                                Toast.makeText(this, "Atomic activities refreshed successfully!", Toast.LENGTH_SHORT).show();
                            });

                            Log.d("AtomicActivities", "原子活动已更新(手动刷新不更新timestamp)");
                        } else if ("error".equals(status)) {
                            // 服务器返回error状态（如：没有找到数据）
                            String errorMessage = !TextUtils.isEmpty(message) ? message : "服务器返回错误状态";
                            Log.w("AtomicActivities", "服务器返回error: " + errorMessage);
                            runOnUiThread(() -> Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show());
                        } else {
                            // status字段缺失或未知
                            String errorMsg = "服务器返回未知状态: " + status;
                            Log.w("AtomicActivities", errorMsg);
                            runOnUiThread(() -> Toast.makeText(this, errorMsg, Toast.LENGTH_SHORT).show());
                        }
                    } else {
                        int code = response.code();
                        String errorBody = "";
                        try {
                            if (response.body() != null) {
                                errorBody = response.body().string();
                            }
                        } catch (Exception e) {
                            errorBody = "无法读取错误响应";
                        }

                        String finalErrorBody = errorBody;
                        String errorMsg = "获取失败,HTTP " + code;
                        String payloadStr = payload != null ? payload.toString() : "null";

                        Log.e("AtomicActivities", "HTTP错误码: " + code);
                        Log.e("AtomicActivities", "错误响应体: " + errorBody);
                        Log.e("AtomicActivities", "请求URL: " + requestUrl);
                        Log.e("AtomicActivities", "请求参数: " + payloadStr);

                        runOnUiThread(() -> {
                            String displayMsg = errorMsg;
                            if (!TextUtils.isEmpty(finalErrorBody)) {
                                displayMsg += "\n详情: " + finalErrorBody;
                            }
                            Toast.makeText(this, displayMsg, Toast.LENGTH_LONG).show();
                        });
                    }
                }
            } catch (Exception e) {
                Log.e("AtomicActivities", "获取原子活动异常: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(this, "Refresh failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }
}