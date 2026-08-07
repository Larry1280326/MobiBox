package com.example.mobibox.unit;

import com.example.mobibox.Constants;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for Constants URL construction and configuration values.
 */
public class ConstantsTest {

    @Test
    public void testApiHost_isSet() {
        assertNotNull("API_HOST should not be null", Constants.API_HOST);
        assertFalse("API_HOST should not be empty", Constants.API_HOST.isEmpty());
    }

    @Test
    public void testGetInterventionUrl() {
        String url = Constants.getInterventionUrl();
        assertTrue("URL should contain endpoint",
                url.contains(Constants.ENDPOINT_GET_INTERVENTION));
        assertTrue("URL should start with API host",
                url.startsWith(Constants.API_HOST));
    }

    @Test
    public void testGetSummaryLogUrl() {
        String url = Constants.getSummaryLogUrl();
        assertTrue(url.contains(Constants.ENDPOINT_GET_SUMMARY_LOG));
        assertTrue(url.startsWith(Constants.API_HOST));
    }

    @Test
    public void testGetSensorUploadUrl() {
        String url = Constants.getSensorUploadUrl();
        assertTrue(url.contains(Constants.ENDPOINT_UPLOAD_DOCUMENTS));
        assertTrue(url.startsWith(Constants.API_HOST));
    }

    @Test
    public void testGetImuUploadUrl() {
        String url = Constants.getImuUploadUrl();
        assertTrue(url.contains(Constants.ENDPOINT_UPLOAD_IMU));
        assertTrue(url.startsWith(Constants.API_HOST));
    }

    @Test
    public void testGetAtomicActivitiesUrl() {
        String url = Constants.getAtomicActivitiesUrl();
        assertTrue(url.contains(Constants.ENDPOINT_GET_ATOMIC_ACTIVITIES));
        assertTrue(url.startsWith(Constants.API_HOST));
    }

    @Test
    public void testGetRegisterUrl() {
        String url = Constants.getRegisterUrl();
        assertTrue(url.contains(Constants.ENDPOINT_REGISTER));
        assertTrue(url.startsWith(Constants.API_HOST));
    }

    @Test
    public void testGetSendInterventionFeedbackUrl() {
        String url = Constants.getSendInterventionFeedbackUrl();
        assertTrue(url.contains(Constants.ENDPOINT_SEND_INTERVENTION_FEEDBACK));
        assertTrue(url.startsWith(Constants.API_HOST));
    }

    @Test
    public void testGetSendLogFeedbackUrl() {
        String url = Constants.getSendLogFeedbackUrl();
        assertTrue(url.contains(Constants.ENDPOINT_SEND_LOG_FEEDBACK));
        assertTrue(url.startsWith(Constants.API_HOST));
    }

    @Test
    public void testAllEndpointsEndWithCorrectPath() {
        // Verify URL format: API_HOST + "/endpoint"
        assertEquals(Constants.API_HOST + Constants.ENDPOINT_GET_INTERVENTION,
                Constants.getInterventionUrl());
        assertEquals(Constants.API_HOST + Constants.ENDPOINT_GET_SUMMARY_LOG,
                Constants.getSummaryLogUrl());
        assertEquals(Constants.API_HOST + Constants.ENDPOINT_UPLOAD_DOCUMENTS,
                Constants.getSensorUploadUrl());
        assertEquals(Constants.API_HOST + Constants.ENDPOINT_UPLOAD_IMU,
                Constants.getImuUploadUrl());
    }

    @Test
    public void testIntervals_arePositive() {
        assertTrue("Intervention check interval must be positive",
                Constants.INTERVENTION_CHECK_INTERVAL_MS > 0);
        assertTrue("Sensor check interval must be positive",
                Constants.SENSOR_CHECK_INTERVAL_MS > 0);
        assertTrue("Data write interval must be positive",
                Constants.DATA_WRITE_INTERVAL_MS > 0);
        assertTrue("IMU write interval must be positive",
                Constants.IMU_WRITE_INTERVAL_MS > 0);
        assertTrue("IMU upload interval must be positive",
                Constants.IMU_UPLOAD_INTERVAL_MS > 0);
    }

    @Test
    public void testInterventionCheckInterval_is5Minutes() {
        assertEquals(5 * 60 * 1000, Constants.INTERVENTION_CHECK_INTERVAL_MS);
    }

    @Test
    public void testImuWriteInterval_is20ms() {
        assertEquals(20, Constants.IMU_WRITE_INTERVAL_MS);
    }

    @Test
    public void testFilePaths_matchPattern() {
        assertTrue("Sensor path should be in DATA_DIR",
                Constants.getSensorFilePath().startsWith(Constants.DATA_DIR));
        assertTrue("IMU path should be in DATA_DIR",
                Constants.getImuFilePath().startsWith(Constants.DATA_DIR));
        assertTrue("App names path should be in DATA_DIR",
                Constants.getAppNamesFilePath().startsWith(Constants.DATA_DIR));
    }

    @Test
    public void testNetworkTimeouts_arePositive() {
        assertTrue(Constants.NETWORK_CONNECT_TIMEOUT_SEC > 0);
        assertTrue(Constants.NETWORK_READ_TIMEOUT_SEC > 0);
        assertTrue(Constants.NETWORK_WRITE_TIMEOUT_SEC > 0);
        assertTrue(Constants.NETWORK_CALL_TIMEOUT_SEC > 0);
    }

    @Test
    public void testUploadLimits() {
        assertEquals("IMU chunk size should be 1000", 1000, Constants.IMU_UPLOAD_CHUNK_SIZE);
        assertEquals("Max retries should be 5", 5, Constants.MAX_UPLOAD_RETRIES);
        assertEquals("Max file size should be 5MB",
                5 * 1024 * 1024, Constants.MAX_FILE_SIZE_BYTES);
    }

    @Test
    public void testSensorDelay_imuIs20ms() {
        assertEquals("IMU sensor delay should be 20ms for 50Hz",
                20_000, Constants.SENSOR_DELAY_IMU_US);
    }

    @Test
    public void testSharedPrefsFileNames_areDistinct() {
        // Verify all prefs file names are different
        assertNotEquals(Constants.PREFS_APP, Constants.PREFS_INTERVENTION);
        assertNotEquals(Constants.PREFS_INTERVENTION, Constants.PREFS_DAILY_LOG);
        assertNotEquals(Constants.PREFS_DAILY_LOG, Constants.PREFS_HOURLY_UPDATE_CACHE);
        assertNotEquals(Constants.PREFS_HOURLY_UPDATE_CACHE, Constants.PREFS_ATOMIC_ACTIVITIES);
    }

    @Test
    public void testNotificationChannelIds_areSet() {
        assertNotNull(Constants.CHANNEL_INTERVENTION);
        assertFalse(Constants.CHANNEL_INTERVENTION.isEmpty());
        assertNotNull(Constants.CHANNEL_DAILY_LOG);
        assertFalse(Constants.CHANNEL_DAILY_LOG.isEmpty());
        assertNotNull(Constants.CHANNEL_FOREGROUND);
        assertFalse(Constants.CHANNEL_FOREGROUND.isEmpty());
    }
}
