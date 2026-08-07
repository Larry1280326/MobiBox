package com.example.mobibox.integration;

import com.example.mobibox.factories.TestDataFactory;
import com.example.mobibox.network.HttpApiClient;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

import static org.junit.Assert.*;

/**
 * Integration tests simulating full API round-trips using MockWebServer.
 * Tests the complete Android → Backend → Android data flow.
 */
public class ApiIntegrationTest {

    private MockWebServer server;
    private HttpApiClient client;
    private String baseUrl;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        baseUrl = "http://" + server.getHostName() + ":" + server.getPort();
        client = new HttpApiClient("integration_test_user", baseUrl);
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    // ── Full Round-Trip ─────────────────────────────────────────────

    @Test
    public void testFullRoundTrip() throws JSONException, InterruptedException {
        // 1. Register user
        server.enqueue(TestDataFactory.createRegisterSuccessResponse());
        boolean registered = client.registerUser("integration_test_user");
        assertTrue("Registration should succeed", registered);

        // Verify the request body was correct
        RecordedRequest regRequest = server.takeRequest();
        assertEquals("POST", regRequest.getMethod());
        assertEquals("/register", regRequest.getPath());
        JSONObject regBody = new JSONObject(regRequest.getBody().readUtf8());
        assertEquals("integration_test_user", regBody.getString("name"));

        // 2. Get intervention
        server.enqueue(TestDataFactory.createInterventionResponse("Time to move!"));
        String intervention = client.getIntervention();
        assertNotNull(intervention);
        assertTrue(intervention.contains("Time to move!"));

        // Verify request
        RecordedRequest intRequest = server.takeRequest();
        assertEquals("/get_intervention", intRequest.getPath());

        // 3. Get hourly log
        server.enqueue(TestDataFactory.createSummaryLogResponse(
                "integration_test_user", "hourly", "Hourly summary content"));
        String hourlyLog = client.getHourlyLog();
        assertNotNull(hourlyLog);
        assertTrue(hourlyLog.contains("Hourly summary content"));

        // 4. Get daily log
        server.enqueue(TestDataFactory.createSummaryLogResponse(
                "integration_test_user", "daily", "Daily summary content"));
        String dailyLog = client.getDailyLog();
        assertNotNull(dailyLog);
        assertTrue(dailyLog.contains("Daily summary content"));
    }

    // ── Polling Mechanism Test ──────────────────────────────────────

    @Test
    public void testPolling_noNewLog() throws JSONException {
        // First fetch — expect new log
        server.enqueue(TestDataFactory.createSummaryLogResponse(
                "test_user", "hourly", "First log"));
        String firstLog = client.getHourlyLog();
        assertNotNull(firstLog);

        // Extract the log ID
        String logId = null;
        try {
            JSONObject json = new JSONObject(firstLog);
            if (json.has("data") && !json.isNull("data")) {
                logId = json.getJSONObject("data").getString("id");
            }
        } catch (JSONException e) {
            fail("Failed to parse first log: " + e.getMessage());
        }

        // Poll with same ID — expect no new log
        if (logId != null) {
            server.enqueue(TestDataFactory.createNoNewLogResponse());
            String secondLog = client.getHourlyLog(logId);
            assertNotNull(secondLog);

            JSONObject json = new JSONObject(secondLog);
            assertFalse("has_new_log should be false",
                    json.getBoolean("has_new_log"));
        }
    }

    // ── Offline Retry Simulation ────────────────────────────────────

    @Test
    public void testOfflineThenOnline() {
        // Step 1: Server down → request fails
        String result1 = client.getIntervention();
        assertNull("Should fail when server is down", result1);

        // Step 2: Start server → request succeeds
        try {
            server.start();
            baseUrl = "http://" + server.getHostName() + ":" + server.getPort();
            client = new HttpApiClient("test_user", baseUrl);

            server.enqueue(TestDataFactory.createInterventionResponse("Back online!"));
            String result2 = client.getIntervention();
            assertNotNull("Should succeed when server is back", result2);
            assertTrue(result2.contains("Back online!"));
        } catch (IOException e) {
            fail("Server restart failed: " + e.getMessage());
        }
    }

    // ── Request/Response Validation ─────────────────────────────────

    @Test
    public void testHourlyLogRequestStructure() throws JSONException, InterruptedException {
        server.enqueue(TestDataFactory.createSummaryLogResponse(
                "test_user", "hourly", "Content"));

        client.getHourlyLog("507f1f77bcf86cd799439011");

        // Verify the request body sent to the server
        RecordedRequest request = server.takeRequest();
        assertEquals("POST", request.getMethod());
        assertEquals("application/json", request.getHeader("Content-Type"));

        JSONObject body = new JSONObject(request.getBody().readUtf8());
        assertEquals("Should include user field", "test_user", body.getString("user"));
        assertEquals("Should include log_type", "hourly", body.getString("log_type"));
        assertEquals("Should include last_log_id",
                "507f1f77bcf86cd799439011", body.getString("last_log_id"));
    }

    @Test
    public void testInterventionRequestStructure() throws JSONException, InterruptedException {
        server.enqueue(TestDataFactory.createInterventionResponse("Content"));

        client.getIntervention();

        RecordedRequest request = server.takeRequest();
        JSONObject body = new JSONObject(request.getBody().readUtf8());
        assertEquals("test_user", body.getString("user"));
    }

    @Test
    public void testRegisterRequestStructure() throws JSONException, InterruptedException {
        server.enqueue(TestDataFactory.createRegisterSuccessResponse());

        client.registerUser("new_test_user");

        RecordedRequest request = server.takeRequest();
        JSONObject body = new JSONObject(request.getBody().readUtf8());
        assertEquals("new_test_user", body.getString("name"));
    }

    // ── Error Response Format ───────────────────────────────────────

    @Test
    public void test422ValidationError() {
        server.enqueue(TestDataFactory.createErrorResponse(422,
                "[{\"loc\":[\"body\",\"user\"],\"msg\":\"field required\"}]"));

        String result = client.getIntervention();
        assertNull("422 should result in null", result);
    }

    @Test
    public void test409Conflict() {
        server.enqueue(new MockResponse()
                .setResponseCode(409)
                .setBody("{\"detail\":\"User already exists\"}")
                .addHeader("Content-Type", "application/json"));

        boolean result = client.registerUser("duplicate");
        assertFalse("409 should return false", result);
    }

    @Test
    public void test503ServiceUnavailable() {
        server.enqueue(TestDataFactory.createErrorResponse(503, "Service unavailable"));

        String result = client.getHourlyLog();
        assertNull("503 should result in null", result);
    }
}
