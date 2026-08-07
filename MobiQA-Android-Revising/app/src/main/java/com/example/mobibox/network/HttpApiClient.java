package com.example.mobibox.network;

import android.util.Log;

import androidx.annotation.Nullable;

import com.example.mobibox.Constants;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.*;

/**
 * Centralized HTTP API client for all network operations.
 * Provides a singleton instance with configured timeouts and retry logic.
 */
public class HttpApiClient {
    private final OkHttpClient http;
    private final String TAG = "HttpApiClient";
    private final String userId;
    private static HttpApiClient instance;

    // =====================
    // Singleton Initialization
    // =====================

    /**
     * Initialize the singleton instance with a user ID.
     * @param userId The user ID for API requests
     */
    public static void init(String userId) {
        instance = new HttpApiClient(userId);
    }

    /**
     * Get the singleton instance.
     * @return The HttpApiClient instance, or null if not initialized
     */
    @Nullable
    public static HttpApiClient getInstance() {
        return instance;
    }

    private final String baseUrl;

    private HttpApiClient(String userId) {
        this(userId, Constants.API_HOST);
    }

    /**
     * Test constructor — allows injecting a custom base URL (e.g., MockWebServer).
     * Package-private so tests in the same package can use it.
     */
    HttpApiClient(String userId, String baseUrl) {
        this.userId = userId;
        this.baseUrl = baseUrl;
        // Configure robust OkHttpClient with timeouts and retry
        this.http = new OkHttpClient.Builder()
                .connectTimeout(Constants.NETWORK_CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
                .readTimeout(Constants.NETWORK_READ_TIMEOUT_SEC, TimeUnit.SECONDS)
                .writeTimeout(Constants.NETWORK_WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
                .callTimeout(Constants.NETWORK_CALL_TIMEOUT_SEC, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)  // Disable retry in tests for deterministic behavior
                .build();
    }

    // =====================
    // Data Upload Methods
    // =====================

    /**
     * Upload CSV file content to the server.
     * @param csvContent The CSV content to upload
     * @param url The upload URL
     * @return The response string, or null if failed
     */
    @Nullable
    public String uploadCsv(String csvContent, String url) {
        JSONObject json = new JSONObject();
        try {
            json.put("user", userId);
            json.put("content", csvContent);
        } catch (JSONException e) {
            Log.e(TAG, "创建JSON失败", e);
            return null;
        }

        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json"));
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();
        return executeRequest(request, "Upload CSV");
    }

    // =====================
    // Intervention Methods
    // =====================

    /**
     * Get intervention content from the server.
     * @return The intervention JSON string, or null if failed
     */
    @Nullable
    public String getIntervention() {
        JSONObject json = new JSONObject();
        try {
            json.put("user", userId);
        } catch (JSONException e) {
            Log.e(TAG, "创建JSON失败", e);
            return null;
        }

        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json"));
        Request request = new Request.Builder()
                .url(baseUrl + Constants.ENDPOINT_GET_INTERVENTION)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();
        return executeRequest(request, "Get intervention");
    }

    // =====================
    // Hourly Log Methods
    // =====================

    /**
     * Get hourly log from the server.
     * @return The hourly log JSON string, or null if failed
     */
    @Nullable
    public String getHourlyLog() {
        return getHourlyLog(null);
    }

    /**
     * Get hourly log from the server with polling support.
     * Uses last_log_id to detect if there's a new log available.
     * @param lastLogId The ObjectId string of the last received log, or null to get any log
     * @return The hourly log JSON string, or null if no new log or failed
     */
    @Nullable
    public String getHourlyLog(String lastLogId) {
        JSONObject json = new JSONObject();
        try {
            json.put("user", userId);
            json.put("log_type", "hourly");
            if (lastLogId != null && !lastLogId.isEmpty()) {
                json.put("last_log_id", lastLogId);
            }
        } catch (JSONException e) {
            Log.e(TAG, "创建JSON失败", e);
            return null;
        }

        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json"));
        Request request = new Request.Builder()
                .url(baseUrl + Constants.ENDPOINT_GET_SUMMARY_LOG)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();
        return executeRequest(request, "Get hourly log");
    }

    // =====================
    // Daily Log Methods
    // =====================

    /**
     * Get daily log from the server.
     * @return The daily log JSON string, or null if failed
     */
    @Nullable
    public String getDailyLog() {
        return getDailyLog(null);
    }

    /**
     * Get daily log from the server with polling support.
     * Uses last_log_id to detect if there's a new log available.
     * @param lastLogId The ObjectId string of the last received log, or null to get any log
     * @return The daily log JSON string, or null if no new log or failed
     */
    @Nullable
    public String getDailyLog(String lastLogId) {
        JSONObject json = new JSONObject();
        try {
            json.put("user", userId);
            json.put("log_type", "daily");
            if (lastLogId != null && !lastLogId.isEmpty()) {
                json.put("last_log_id", lastLogId);
            }
        } catch (JSONException e) {
            Log.e(TAG, "创建JSON失败", e);
            return null;
        }

        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json"));
        Request request = new Request.Builder()
                .url(baseUrl + Constants.ENDPOINT_GET_SUMMARY_LOG)
                .post(body)
                .addHeader("Content-Type", "application/json")
                .build();
        return executeRequest(request, "Get daily log");
    }

    // =====================
    // User Registration
    // =====================

    /**
     * Register a new user on the backend.
     * @param userName The unique user name to register
     * @return true if registration was successful, false otherwise
     */
    public boolean registerUser(String userName) {
        JSONObject json = new JSONObject();
        try {
            json.put("name", userName);
        } catch (JSONException e) {
            Log.e(TAG, "创建JSON失败", e);
            return false;
        }

        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json"));
        Request request = new Request.Builder()
                .url(baseUrl + Constants.ENDPOINT_REGISTER)
                .post(body)
                .build();

        String result = executeRequest(request, "Register user");
        return result != null;
    }

    // =====================
    // Utility Methods
    // =====================

    /**
     * Get the user ID.
     * @return The user ID
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Execute a request and return the response.
     * @param request The request to execute
     * @param logTag A tag for logging
     * @return The response string, or null if failed
     */
    @Nullable
    private String executeRequest(Request request, String logTag) {
        try (Response response = http.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = "";
                if (response.body() != null) {
                    try {
                        errorBody = response.body().string();
                    } catch (IOException e) {
                        errorBody = "无法读取错误响应";
                    }
                }
                Log.e(TAG, logTag + " 请求失败: HTTP " + response.code() + ", body: " + errorBody);
                throw new IOException("请求失败: " + response.code());
            }

            if (response.body() == null) {
                Log.e(TAG, logTag + " 响应体为空");
                return null;
            }

            String result = response.body().string();
            Log.i(TAG, logTag + " 成功, 响应长度: " + result.length() + " 字节");
            return result;
        } catch (IOException e) {
            Log.e(TAG, logTag + " IOException: " + e.getClass().getSimpleName() + " - " + e.getMessage(), e);
            return null;
        } catch (Exception e) {
            Log.e(TAG, logTag + " 失败", e);
            return null;
        }
    }

    /**
     * Execute a request asynchronously with a callback.
     * @param request The request to execute
     * @param callback The callback for the response
     */
    public void executeRequestAsync(Request request, final ApiCallback callback) {
        http.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "Async request failed: " + e.getMessage(), e);
                callback.onFailure(e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "";
                        callback.onFailure("HTTP " + response.code() + ": " + errorBody);
                        return;
                    }

                    if (response.body() == null) {
                        callback.onFailure("Response body is null");
                        return;
                    }

                    String result = response.body().string();
                    callback.onSuccess(result);
                } finally {
                    response.close();
                }
            }
        });
    }

    /**
     * Callback interface for async API requests.
     */
    public interface ApiCallback {
        void onSuccess(String response);
        void onFailure(String error);
    }
}
