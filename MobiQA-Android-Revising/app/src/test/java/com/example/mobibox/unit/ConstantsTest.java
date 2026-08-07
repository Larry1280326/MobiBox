package com.example.mobibox.unit;

import com.example.mobibox.Constants;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for Constants URL construction and configuration values.
 * Pure JUnit — no Android framework dependencies.
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
    }

    @Test
    public void testGetSensorUploadUrl() {
        String url = Constants.getSensorUploadUrl();
        assertTrue(url.contains(Constants.ENDPOINT_UPLOAD_DOCUMENTS));
    }

    @Test
    public void testGetImuUploadUrl() {
        String url = Constants.getImuUploadUrl();
        assertTrue(url.contains(Constants.ENDPOINT_UPLOAD_IMU));
    }

    @Test
    public void testGetRegisterUrl() {
        String url = Constants.getRegisterUrl();
        assertTrue(url.contains(Constants.ENDPOINT_REGISTER));
    }

    @Test
    public void testUrlConstruction_matchesPattern() {
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
        assertTrue(Constants.INTERVENTION_CHECK_INTERVAL_MS > 0);
        assertTrue(Constants.SENSOR_CHECK_INTERVAL_MS > 0);
        assertTrue(Constants.DATA_WRITE_INTERVAL_MS > 0);
        assertTrue(Constants.IMU_WRITE_INTERVAL_MS > 0);
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
    public void testUploadLimits() {
        assertEquals("IMU chunk size should be 1000", 1000, Constants.IMU_UPLOAD_CHUNK_SIZE);
        assertEquals("Max retries should be 5", 5, Constants.MAX_UPLOAD_RETRIES);
    }

    @Test
    public void testFilePaths_matchPattern() {
        assertTrue("Sensor path should be in DATA_DIR",
                Constants.getSensorFilePath().startsWith(Constants.DATA_DIR));
        assertTrue("IMU path should be in DATA_DIR",
                Constants.getImuFilePath().startsWith(Constants.DATA_DIR));
    }
}
