package com.example.mobibox.util;

import android.text.TextUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Utility class for time formatting operations.
 */
public class TimeUtils {

    /**
     * Converts a UTC timestamp string to Hong Kong timezone (UTC+8) for display.
     * Handles multiple ISO 8601 formats with and without milliseconds.
     *
     * @param utcTimestamp The UTC timestamp string (e.g., "2026-03-07T15:01:24.29Z" or "2026-03-07T15:01:24Z")
     * @return Formatted string in Hong Kong timezone (e.g., "2026-03-07 23:01") or original string if parsing fails
     */
    public static String formatUtcToHKTime(String utcTimestamp) {
        if (TextUtils.isEmpty(utcTimestamp)) {
            return "";
        }

        // Try parsing with milliseconds first (e.g., "2026-03-07T15:01:24.29Z")
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
            inputFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = inputFormat.parse(utcTimestamp);

            if (date != null) {
                SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
                outputFormat.setTimeZone(TimeZone.getTimeZone("Asia/Hong_Kong"));
                return outputFormat.format(date);
            }
        } catch (Exception e) {
            // Fall through to try without milliseconds
        }

        // Try parsing without milliseconds (e.g., "2026-03-07T15:01:24Z")
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
            inputFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = inputFormat.parse(utcTimestamp);

            if (date != null) {
                SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
                outputFormat.setTimeZone(TimeZone.getTimeZone("Asia/Hong_Kong"));
                return outputFormat.format(date);
            }
        } catch (Exception e) {
            // Fall through to return original
        }

        // Try parsing ISO 8601 with timezone offset (e.g., "2026-03-08T00:52:00+08:00")
        try {
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
            Date date = inputFormat.parse(utcTimestamp);

            if (date != null) {
                SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US);
                outputFormat.setTimeZone(TimeZone.getTimeZone("Asia/Hong_Kong"));
                return outputFormat.format(date);
            }
        } catch (Exception e) {
            // Fall through to return original
        }

        // Return original string if all parsing attempts fail
        return utcTimestamp;
    }
}