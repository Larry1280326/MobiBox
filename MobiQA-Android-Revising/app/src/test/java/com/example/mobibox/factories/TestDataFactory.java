package com.example.mobibox.factories;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Factory for generating test data payloads and mock server responses.
 * All generated data matches the backend Pydantic schemas.
 */
public class TestDataFactory {

    // =====================
    // Request Body Factories
    // =====================

    /** Generate a register request body: {"name": "..."} */
    public static JSONObject createRegisterRequest(String userName) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("name", userName);
        return json;
    }

    /** Generate a summary log request: {"user": "...", "log_type": "hourly", "last_log_id": "..."} */
    public static JSONObject createSummaryLogRequest(String userId, String logType,
                                                      String lastLogId) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("user", userId);
        json.put("log_type", logType);
        if (lastLogId != null) {
            json.put("last_log_id", lastLogId);
        }
        return json;
    }

    /** Generate an intervention request: {"user": "..."} */
    public static JSONObject createInterventionRequest(String userId) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("user", userId);
        return json;
    }

    /** Generate a document upload request: {"items": [...]} */
    public static JSONObject createDocumentUpload(String userId, int count) throws JSONException {
        JSONArray items = new JSONArray();
        for (int i = 0; i < count; i++) {
            JSONObject item = new JSONObject();
            item.put("user", userId);
            item.put("battery", 80 + (i % 20));
            item.put("screen_on_ratio", 0.5);
            item.put("wifi_connected", true);
            item.put("stepcount_sensor", i * 10);
            item.put("gpsLat", 22.3367 + i * 0.001);
            item.put("gpsLon", 114.2650 + i * 0.001);
            item.put("timestamp", "2026-08-07T12:00:00Z");
            items.put(item);
        }
        JSONObject json = new JSONObject();
        json.put("items", items);
        return json;
    }

    /** Generate an IMU upload request: {"items": [...]} */
    public static JSONObject createImuUpload(String userId, int samples) throws JSONException {
        JSONArray items = new JSONArray();
        for (int i = 0; i < samples; i++) {
            JSONObject item = new JSONObject();
            item.put("user", userId);
            item.put("acc_X", 0.1 * Math.sin(i * 0.1));
            item.put("acc_Y", 0.2 * Math.cos(i * 0.1));
            item.put("acc_Z", 9.8 + 0.1 * Math.sin(i * 0.05));
            item.put("gyro_X", 0.01);
            item.put("gyro_Y", 0.02);
            item.put("gyro_Z", 0.03);
            item.put("timestamp", "2026-08-07T12:00:00Z");
            items.put(item);
        }
        JSONObject json = new JSONObject();
        json.put("items", items);
        return json;
    }

    /** Generate intervention feedback: {"user": "...", "intervention_id": "...", ...} */
    public static JSONObject createInterventionFeedback(String userId, String interventionId,
                                                         String feedback) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("user", userId);
        json.put("intervention_id", interventionId);
        json.put("feedback", feedback);
        json.put("mc1", "yes");
        json.put("mc2", "4");
        json.put("mc3", "3");
        json.put("mc4", "5");
        json.put("mc5", "4");
        json.put("mc6", "3");
        return json;
    }

    /** Generate log feedback: {"user": "...", "summary_logs_id": "...", ...} */
    public static JSONObject createLogFeedback(String userId, String logId,
                                                String groundTruth) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("user", userId);
        json.put("summary_logs_id", logId);
        json.put("q1", "4");
        json.put("q2", "yes");
        json.put("ground_truth", groundTruth);
        json.put("suggestions", "More detail would be helpful");
        return json;
    }

    /** Generate atomic activities request: {"user": "...", "duration": 3600} */
    public static JSONObject createAtomicActivitiesRequest(String userId,
                                                            int duration) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("user", userId);
        json.put("duration", duration);
        return json;
    }

    // =====================
    // MockResponse Factories
    // =====================

    /** Create a 200 success response with JSON body. */
    public static MockResponse createSuccessResponse(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setBody(body)
                .addHeader("Content-Type", "application/json");
    }

    /** Create an error response. */
    public static MockResponse createErrorResponse(int code, String message) {
        return new MockResponse()
                .setResponseCode(code)
                .setBody("{\"detail\":\"" + message + "\"}")
                .addHeader("Content-Type", "application/json");
    }

    /** Create a summary log success response. */
    public static MockResponse createSummaryLogResponse(String userId, String logType,
                                                         String content) {
        String body = "{" +
                "\"status\":\"success\"," +
                "\"data\":{" +
                "\"id\":\"507f1f77bcf86cd799439011\"," +
                "\"log_content\":\"" + content + "\"," +
                "\"start_timestamp\":\"2026-08-07T12:00:00Z\"," +
                "\"end_timestamp\":\"2026-08-07T13:00:00Z\"," +
                "\"generation_timestamp\":\"2026-08-07T13:00:00Z\"" +
                "}," +
                "\"has_new_log\":true," +
                "\"date\":" + (logType.equals("daily") ? "\"2026-08-07\"" : "null") +
                "}";
        return createSuccessResponse(body);
    }

    /** Create a polling response with no new log. */
    public static MockResponse createNoNewLogResponse() {
        String body = "{" +
                "\"status\":\"success\"," +
                "\"data\":null," +
                "\"has_new_log\":false," +
                "\"date\":null" +
                "}";
        return createSuccessResponse(body);
    }

    /** Create an intervention success response. */
    public static MockResponse createInterventionResponse(String message) {
        String body = "{" +
                "\"status\":\"success\"," +
                "\"data\":{" +
                "\"id\":\"507f1f77bcf86cd799439011\"," +
                "\"intervention_content\":\"" + message + "\"," +
                "\"start_timestamp\":\"2026-08-07T12:00:00Z\"," +
                "\"end_timestamp\":\"2026-08-07T13:00:00Z\"," +
                "\"generation_timestamp\":\"2026-08-07T13:00:00Z\"" +
                "}" +
                "}";
        return createSuccessResponse(body);
    }

    /** Create a register success response. */
    public static MockResponse createRegisterSuccessResponse() {
        return createSuccessResponse("{\"status\":\"success\",\"message\":\"User registered\"}");
    }

    /** Create an upload success response. */
    public static MockResponse createUploadSuccessResponse(int count) {
        return createSuccessResponse("{\"status\":\"success\",\"count\":" + count + "}");
    }

    /** Create a feedback success response. */
    public static MockResponse createFeedbackSuccessResponse() {
        return createSuccessResponse("{\"status\":\"success\",\"message\":\"Feedback submitted successfully\"}");
    }

    /** Create a network timeout response (simulated via body delay). */
    public static MockResponse createTimeoutResponse(long delayMs) {
        return new MockResponse()
                .setResponseCode(200)
                .setBody("{}")
                .setBodyDelay(delayMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                .addHeader("Content-Type", "application/json");
    }
}
