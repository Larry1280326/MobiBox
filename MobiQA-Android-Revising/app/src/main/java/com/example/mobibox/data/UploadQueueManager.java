package com.example.mobibox.data;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Manages a persistent queue of pending uploads.
 * When uploads fail (e.g., due to network issues), they are queued here
 * and can be retried when network becomes available.
 *
 * This ensures data is not lost during extended offline periods.
 */
public class UploadQueueManager {

    private static final String TAG = "UploadQueueManager";
    private static final String PREFS_NAME = "UploadQueuePrefs";
    private static final String KEY_PENDING_UPLOADS = "pending_uploads";
    private static final int MAX_QUEUE_SIZE = 100; // Prevent unbounded growth

    private static UploadQueueManager instance;
    private final Context context;
    private final SharedPreferences prefs;

    /**
     * Types of data that can be queued for upload
     */
    public enum UploadType {
        IMU("IMU"),
        SENSOR("Sensor"),
        IMU_BACKUP("IMU_Backup"),
        SENSOR_BACKUP("Sensor_Backup");

        private final String displayName;

        UploadType(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * Represents a pending upload item
     */
    public static class PendingUpload {
        public String id;
        public UploadType type;
        public String filePath;
        public String content;       // CSV content (may be null for backup files)
        public long timestamp;        // When it was added to queue
        public int retryCount;       // Number of retry attempts
        public long lastRetryTime;    // Last retry timestamp

        public PendingUpload(UploadType type, String filePath, String content) {
            this.id = generateId(type);
            this.type = type;
            this.filePath = filePath;
            this.content = content;
            this.timestamp = System.currentTimeMillis();
            this.retryCount = 0;
            this.lastRetryTime = 0;
        }

        private static String generateId(UploadType type) {
            return type.name() + "_" + System.currentTimeMillis() + "_" +
                   String.format(Locale.US, "%04d", (int)(Math.random() * 10000));
        }

        public JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("id", id);
            json.put("type", type.name());
            json.put("filePath", filePath);
            json.put("content", content != null ? content : "");
            json.put("timestamp", timestamp);
            json.put("retryCount", retryCount);
            json.put("lastRetryTime", lastRetryTime);
            return json;
        }

        public static PendingUpload fromJson(JSONObject json) throws JSONException {
            String typeStr = json.getString("type");
            UploadType type = UploadType.valueOf(typeStr);
            String filePath = json.getString("filePath");
            String content = json.optString("content", null);

            PendingUpload upload = new PendingUpload(type, filePath, content);
            upload.id = json.getString("id");
            upload.timestamp = json.getLong("timestamp");
            upload.retryCount = json.getInt("retryCount");
            upload.lastRetryTime = json.getLong("lastRetryTime");
            return upload;
        }
    }

    private UploadQueueManager(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized UploadQueueManager getInstance(Context context) {
        if (instance == null) {
            instance = new UploadQueueManager(context);
        }
        return instance;
    }

    /**
     * Add a pending upload to the queue.
     * The content will be saved to a backup file before queuing.
     *
     * @param type Upload type
     * @param originalFilePath Original file path (e.g., "0.Mobibox/IMU.csv")
     * @param content CSV content to upload
     * @return true if successfully queued, false otherwise
     */
    public boolean addToQueue(UploadType type, String originalFilePath, String content) {
        if (content == null || content.isEmpty()) {
            Log.w(TAG, "Cannot queue empty content for " + type);
            return false;
        }

        // Save content to a backup file
        String backupPath = saveToBackupFile(type, content);
        if (backupPath == null) {
            Log.e(TAG, "Failed to save backup file for " + type);
            return false;
        }

        PendingUpload upload = new PendingUpload(type, backupPath, null); // Content is in file, not in memory

        synchronized (this) {
            List<PendingUpload> queue = loadQueue();

            // Check queue size limit
            if (queue.size() >= MAX_QUEUE_SIZE) {
                Log.w(TAG, "Upload queue full (" + MAX_QUEUE_SIZE + "), removing oldest item");
                removeOldestItem(queue);
            }

            queue.add(upload);
            saveQueue(queue);

            Log.i(TAG, "✅ Added to upload queue: " + upload.id +
                   " (queue size: " + queue.size() + ")");
            return true;
        }
    }

    /**
     * Add a backup file to the queue (file already exists on disk).
     *
     * @param type Upload type (IMU_BACKUP or SENSOR_BACKUP)
     * @param backupFilePath Full path to the backup file
     * @return true if successfully queued
     */
    public boolean addBackupFileToQueue(UploadType type, String backupFilePath) {
        PendingUpload upload = new PendingUpload(type, backupFilePath, null);

        synchronized (this) {
            List<PendingUpload> queue = loadQueue();

            if (queue.size() >= MAX_QUEUE_SIZE) {
                Log.w(TAG, "Upload queue full, removing oldest item");
                removeOldestItem(queue);
            }

            queue.add(upload);
            saveQueue(queue);

            Log.i(TAG, "✅ Added backup file to queue: " + upload.id +
                   " (queue size: " + queue.size() + ")");
            return true;
        }
    }

    /**
     * Get all pending uploads.
     * @return List of pending uploads
     */
    public synchronized List<PendingUpload> getPendingUploads() {
        return loadQueue();
    }

    /**
     * Remove a pending upload from the queue after successful upload.
     * Also deletes the associated backup file.
     *
     * @param uploadId The ID of the upload to remove
     */
    public synchronized void removeFromQueue(String uploadId) {
        List<PendingUpload> queue = loadQueue();
        PendingUpload toRemove = null;

        for (PendingUpload upload : queue) {
            if (upload.id.equals(uploadId)) {
                toRemove = upload;
                break;
            }
        }

        if (toRemove != null) {
            queue.remove(toRemove);
            saveQueue(queue);

            // Delete the backup file
            deleteBackupFile(toRemove.filePath);

            Log.i(TAG, "✅ Removed from queue: " + uploadId + " (queue size: " + queue.size() + ")");
        } else {
            Log.w(TAG, "Upload not found in queue: " + uploadId);
        }
    }

    /**
     * Update retry count for a pending upload.
     *
     * @param uploadId The ID of the upload
     * @param maxRetries Maximum allowed retries before removing
     * @return true if upload should be retried, false if max retries exceeded
     */
    public synchronized boolean incrementRetry(String uploadId, int maxRetries) {
        List<PendingUpload> queue = loadQueue();

        for (PendingUpload upload : queue) {
            if (upload.id.equals(uploadId)) {
                upload.retryCount++;
                upload.lastRetryTime = System.currentTimeMillis();

                if (upload.retryCount >= maxRetries) {
                    Log.w(TAG, "Max retries (" + maxRetries + ") exceeded for " + uploadId + ", removing");
                    queue.remove(upload);
                    saveQueue(queue);
                    deleteBackupFile(upload.filePath);
                    return false;
                }

                saveQueue(queue);
                Log.d(TAG, "Retry " + upload.retryCount + "/" + maxRetries + " for " + uploadId);
                return true;
            }
        }

        return false;
    }

    /**
     * Get the number of pending uploads.
     */
    public synchronized int getQueueSize() {
        return loadQueue().size();
    }

    /**
     * Check if queue is empty.
     */
    public synchronized boolean isEmpty() {
        return loadQueue().isEmpty();
    }

    /**
     * Clear all pending uploads and delete backup files.
     */
    public synchronized void clearQueue() {
        List<PendingUpload> queue = loadQueue();

        for (PendingUpload upload : queue) {
            deleteBackupFile(upload.filePath);
        }

        prefs.edit().remove(KEY_PENDING_UPLOADS).apply();
        Log.i(TAG, "Cleared upload queue (" + queue.size() + " items removed)");
    }

    // =====================
    // Private Methods
    // =====================

    private List<PendingUpload> loadQueue() {
        List<PendingUpload> queue = new ArrayList<>();
        String jsonStr = prefs.getString(KEY_PENDING_UPLOADS, "[]");

        try {
            JSONArray jsonArray = new JSONArray(jsonStr);
            for (int i = 0; i < jsonArray.length(); i++) {
                try {
                    JSONObject json = jsonArray.getJSONObject(i);
                    queue.add(PendingUpload.fromJson(json));
                } catch (JSONException e) {
                    Log.e(TAG, "Failed to parse pending upload item: " + e.getMessage());
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Failed to load upload queue: " + e.getMessage());
        }

        return queue;
    }

    private void saveQueue(List<PendingUpload> queue) {
        JSONArray jsonArray = new JSONArray();
        for (PendingUpload upload : queue) {
            try {
                jsonArray.put(upload.toJson());
            } catch (JSONException e) {
                Log.e(TAG, "Failed to serialize upload: " + e.getMessage());
            }
        }

        prefs.edit()
             .putString(KEY_PENDING_UPLOADS, jsonArray.toString())
             .apply();
    }

    private void removeOldestItem(List<PendingUpload> queue) {
        if (queue.isEmpty()) return;

        PendingUpload oldest = queue.get(0);
        for (PendingUpload upload : queue) {
            if (upload.timestamp < oldest.timestamp) {
                oldest = upload;
            }
        }

        queue.remove(oldest);
        deleteBackupFile(oldest.filePath);
        Log.w(TAG, "Removed oldest pending upload: " + oldest.id);
    }

    private String saveToBackupFile(UploadType type, String content) {
        try {
            // Create backup directory if needed
            File backupDir = new File(Environment.getExternalStorageDirectory(),
                    "0.Mobibox/pending_uploads");
            if (!backupDir.exists()) {
                backupDir.mkdirs();
            }

            // Generate unique filename
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
            String timestamp = sdf.format(new Date());
            String fileName = type.name().toLowerCase() + "_" + timestamp + "_" +
                    String.format("%04d", (int)(Math.random() * 10000)) + ".csv";
            File backupFile = new File(backupDir, fileName);

            // Write content
            try (FileWriter writer = new FileWriter(backupFile, false)) {
                writer.write(content);
            }

            Log.d(TAG, "Saved backup file: " + backupFile.getAbsolutePath());
            return backupFile.getAbsolutePath();

        } catch (IOException e) {
            Log.e(TAG, "Failed to save backup file: " + e.getMessage());
            return null;
        }
    }

    private void deleteBackupFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return;
        }

        // Only delete files managed by the upload queue.
        if (!filePath.contains("pending_uploads") && !filePath.contains("_upload_")) {
            Log.w(TAG, "Skipping deletion of non-backup file: " + filePath);
            return;
        }

        File file = new File(filePath);
        if (file.exists()) {
            boolean deleted = file.delete();
            if (deleted) {
                Log.d(TAG, "Deleted backup file: " + filePath);
            } else {
                Log.w(TAG, "Failed to delete backup file: " + filePath);
            }
        }
    }

    /**
     * Read content from a pending upload's backup file.
     *
     * @param upload The pending upload
     * @return The file content, or null if failed
     */
    public String readUploadContent(PendingUpload upload) {
        if (upload.content != null && !upload.content.isEmpty()) {
            return upload.content;
        }

        // Read from backup file
        if (upload.filePath != null) {
            try {
                File file = new File(upload.filePath);
                if (!file.exists()) {
                    Log.e(TAG, "Backup file not found: " + upload.filePath);
                    return null;
                }

                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.FileReader(file));
                StringBuilder content = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
                reader.close();

                return content.toString();

            } catch (IOException e) {
                Log.e(TAG, "Failed to read backup file: " + e.getMessage());
                return null;
            }
        }

        return null;
    }
}
