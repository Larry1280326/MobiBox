package com.example.mobibox.unit;

import com.example.mobibox.util.TimeUtils;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for TimeUtils UTC → Hong Kong time conversion.
 */
public class TimeUtilsTest {

    @Test
    public void testFormatUtcToHKTime_withMilliseconds() {
        // 2026-03-07T15:01:24.290Z (UTC) → 2026-03-07 23:01 (HKT, UTC+8)
        String utc = "2026-03-07T15:01:24.290Z";
        String result = TimeUtils.formatUtcToHKTime(utc);
        assertNotNull("Result should not be null", result);
        assertFalse("Result should not be empty", result.isEmpty());
        // HKT is UTC+8: 15:01 + 8 = 23:01
        assertTrue("Should contain HKT hour 23",
                result.contains("23:01"));
    }

    @Test
    public void testFormatUtcToHKTime_withoutMilliseconds() {
        // 2026-03-07T15:01:24Z → 2026-03-07 23:01
        String utc = "2026-03-07T15:01:24Z";
        String result = TimeUtils.formatUtcToHKTime(utc);
        assertNotNull(result);
        assertTrue("Should contain 23:01 for HKT",
                result.contains("23:01"));
    }

    @Test
    public void testFormatUtcToHKTime_midnight() {
        // Midnight UTC → 8:00 AM HKT
        String utc = "2026-03-07T00:00:00Z";
        String result = TimeUtils.formatUtcToHKTime(utc);
        assertNotNull(result);
    }

    @Test
    public void testFormatUtcToHKTime_nullInput() {
        String result = TimeUtils.formatUtcToHKTime(null);
        assertEquals("Null input should return empty string", "", result);
    }

    @Test
    public void testFormatUtcToHKTime_emptyInput() {
        String result = TimeUtils.formatUtcToHKTime("");
        assertEquals("Empty input should return empty string", "", result);
    }

    @Test
    public void testFormatUtcToHKTime_invalidInput() {
        // Invalid format should return the original string
        String invalid = "not-a-timestamp";
        String result = TimeUtils.formatUtcToHKTime(invalid);
        assertEquals("Invalid input should be returned as-is", invalid, result);
    }

    @Test
    public void testFormatUtcToHKTime_outputFormat() {
        String utc = "2026-08-07T15:01:24Z";
        String result = TimeUtils.formatUtcToHKTime(utc);
        // Expected format: "yyyy-MM-dd HH:mm"
        assertTrue("Output should match 'yyyy-MM-dd HH:mm' pattern",
                result.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}"));
    }

    @Test
    public void testFormatUtcToHKTime_hktOffset() {
        // 12:00 UTC → 20:00 HKT
        String utc = "2026-08-07T12:00:00Z";
        String result = TimeUtils.formatUtcToHKTime(utc);
        assertTrue("12:00 UTC should become 20:xx HKT (UTC+8)",
                result.contains("20:"));
    }

    @Test
    public void testFormatUtcToHKTime_endOfDay() {
        // 23:59 UTC → 07:59 next day HKT
        String utc = "2026-08-07T23:59:00Z";
        String result = TimeUtils.formatUtcToHKTime(utc);
        assertNotNull(result);
        // Next day in HKT
        assertTrue("Should contain 07:59 for next day HKT",
                result.contains("07:59"));
    }
}
