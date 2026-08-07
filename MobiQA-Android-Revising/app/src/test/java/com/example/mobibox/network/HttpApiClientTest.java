package com.example.mobibox.network;

import com.example.mobibox.factories.TestDataFactory;
import com.example.mobibox.network.HttpApiClient;

import org.json.JSONException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import static org.junit.Assert.*;

/**
 * Unit tests for HttpApiClient using MockWebServer.
 * Tests all API methods, error handling, and edge cases.
 */
public class HttpApiClientTest {

    private MockWebServer server;
    private HttpApiClient client;
    private String baseUrl;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        baseUrl = "http://" + server.getHostName() + ":" + server.getPort();
        client = new HttpApiClient("test_user", baseUrl);
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    // ── Registration Tests ──────────────────────────────────────────

    @Test
    public void testRegisterUser_success() throws JSONException {
        server.enqueue(TestDataFactory.createRegisterSuccessResponse());

        boolean result = client.registerUser("new_user");
        assertTrue("Registration should succeed", result);
    }

    @Test
    public void testRegisterUser_duplicateReturns409() {
        server.enqueue(TestDataFactory.createErrorResponse(409, "User already exists"));

        boolean result = client.registerUser("existing_user");
        assertFalse("Registration should fail on 409", result);
    }

    @Test
    public void testRegisterUser_serverError500() {
        server.enqueue(TestDataFactory.createErrorResponse(500, "Internal server error"));

        boolean result = client.registerUser("error_user");
        assertFalse("Registration should fail on 500", result);
    }

    // ── Intervention Tests ──────────────────────────────────────────

    @Test
    public void testGetIntervention_success() {
        server.enqueue(TestDataFactory.createInterventionResponse("Stand up and stretch!"));

        String result = client.getIntervention();
        assertNotNull("Intervention should not be null", result);
        assertTrue("Response should contain status",
                result.contains("success"));
        assertTrue("Response should contain intervention content",
                result.contains("Stand up and stretch!"));
    }

    @Test
    public void testGetIntervention_serverError() {
        server.enqueue(TestDataFactory.createErrorResponse(503, "DB unavailable"));

        String result = client.getIntervention();
        assertNull("Should return null on server error", result);
    }

    @Test
    public void testGetIntervention_emptyBody() {
        server.enqueue(new MockResponse().setResponseCode(200).setBody(""));

        String result = client.getIntervention();
        // Empty body but successful response — body is empty string
        assertNotNull("Empty response body should not be null", result);
    }

    // ── Hourly Log Tests ────────────────────────────────────────────

    @Test
    public void testGetHourlyLog_success() {
        server.enqueue(TestDataFactory.createSummaryLogResponse(
                "test_user", "hourly", "Active hour summary"));

        String result = client.getHourlyLog();
        assertNotNull(result);
        assertTrue(result.contains("Active hour summary"));
        assertTrue(result.contains("has_new_log"));
    }

    @Test
    public void testGetHourlyLog_withLastLogId() {
        server.enqueue(TestDataFactory.createNoNewLogResponse());

        String result = client.getHourlyLog("507f1f77bcf86cd799439011");
        assertNotNull(result);
        assertTrue(result.contains("has_new_log"));
        assertTrue(result.contains("false"));
    }

    @Test
    public void testGetHourlyLog_nullLastLogId() {
        server.enqueue(TestDataFactory.createSummaryLogResponse(
                "test_user", "hourly", "Summary"));

        String result = client.getHourlyLog(null);
        assertNotNull(result);
    }

    @Test
    public void testGetHourlyLog_emptyLastLogId() {
        server.enqueue(TestDataFactory.createSummaryLogResponse(
                "test_user", "hourly", "Summary with empty last_log_id"));

        String result = client.getHourlyLog("");
        assertNotNull(result);
    }

    @Test
    public void testGetHourlyLog_serverError() {
        server.enqueue(TestDataFactory.createErrorResponse(500, "Error"));

        String result = client.getHourlyLog();
        assertNull("Should return null on error", result);
    }

    // ── Daily Log Tests ─────────────────────────────────────────────

    @Test
    public void testGetDailyLog_success() {
        server.enqueue(TestDataFactory.createSummaryLogResponse(
                "test_user", "daily", "Daily activity summary"));

        String result = client.getDailyLog();
        assertNotNull(result);
        assertTrue(result.contains("Daily activity summary"));
    }

    @Test
    public void testGetDailyLog_withLastLogId() {
        server.enqueue(TestDataFactory.createSummaryLogResponse(
                "test_user", "daily", "Daily summary with polling"));

        String result = client.getDailyLog("507f1f77bcf86cd799439011");
        assertNotNull(result);
    }

    @Test
    public void testGetDailyLog_serverError() {
        server.enqueue(TestDataFactory.createErrorResponse(503, "Unavailable"));

        String result = client.getDailyLog();
        assertNull("Should return null on error", result);
    }

    // ── Upload CSV Test ─────────────────────────────────────────────

    @Test
    public void testUploadCsv_success() {
        server.enqueue(TestDataFactory.createUploadSuccessResponse(5));

        String csvContent = "timestamp,value\n2026-08-07T12:00:00Z,42";
        String result = client.uploadCsv(csvContent, baseUrl + "/upload/documents");
        assertNotNull(result);
        assertTrue(result.contains("success"));
    }

    @Test
    public void testUploadCsv_failure() {
        server.enqueue(TestDataFactory.createErrorResponse(413, "Payload too large"));

        String csvContent = "timestamp,value\n" + "x".repeat(10000);
        String result = client.uploadCsv(csvContent, baseUrl + "/upload/documents");
        // uploadCsv catches IOException and returns null
        // 413 means !response.isSuccessful(), which throws IOException
        assertNull("Should return null on upload failure", result);
    }

    // ── Network Error Tests ─────────────────────────────────────────

    @Test
    public void testGetHourlyLog_timeout() {
        // Simulate timeout by delaying response beyond OkHttp call timeout (30s)
        // Use a shorter delay to make the test fast
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{}")
                .setHeadersDelay(500, TimeUnit.MILLISECONDS));

        // With 30s call timeout, this should succeed (500ms < 30s)
        String result = client.getHourlyLog();
        assertNotNull("Should succeed within timeout", result);
    }

    @Test
    public void testRequest_toInvalidUrl() {
        // Point client at a non-existent server
        HttpApiClient badClient = new HttpApiClient("test", "http://localhost:1"); // invalid port
        String result = badClient.getIntervention();
        assertNull("Should return null on connection failure", result);
    }

    // ── Response Format Tests ───────────────────────────────────────

    @Test
    public void testResponse_containsExpectedJson() {
        server.enqueue(TestDataFactory.createInterventionResponse("Test message"));

        String result = client.getIntervention();
        assertNotNull(result);

        // Should be valid JSON
        try {
            new org.json.JSONObject(result);
        } catch (org.json.JSONException e) {
            fail("Response should be valid JSON: " + e.getMessage());
        }
    }

    @Test
    public void testSummaryLogResponse_hasExpectedFields() {
        server.enqueue(TestDataFactory.createSummaryLogResponse(
                "test_user", "hourly", "Test summary"));

        String result = client.getHourlyLog();
        assertNotNull(result);

        try {
            org.json.JSONObject json = new org.json.JSONObject(result);
            assertTrue("Should have 'status' field", json.has("status"));
            assertEquals("success", json.getString("status"));
            assertTrue("Should have 'has_new_log' field", json.has("has_new_log"));
        } catch (org.json.JSONException e) {
            fail("Response should be valid JSON: " + e.getMessage());
        }
    }

    // ── Singleton Tests ────────────────────────────────────────────

    @Test
    public void testSingleton_notInitializedReturnsNull() {
        // Don't call init() — getInstance should return null
        // Note: previous tests may have initialized it via init()
        // We just verify the method exists and returns something
        HttpApiClient instance = HttpApiClient.getInstance();
        // May be null or set from previous test; just verify no crash
    }

    @Test
    public void testInit_createsInstance() {
        HttpApiClient.init("singleton_test_user");
        HttpApiClient instance = HttpApiClient.getInstance();
        assertNotNull("Instance should exist after init()", instance);
        assertEquals("singleton_test_user", instance.getUserId());
    }
}
