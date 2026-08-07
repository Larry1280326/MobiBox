package com.example.mobibox.data;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.example.mobibox.Constants;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Repository for managing data files (CSV files for sensor and IMU data).
 * Handles file creation, rotation, reading, and writing operations.
 */
public class FileRepository {
    private static final String TAG = "FileRepository";

    private final Context context;

    // File instances
    private File imuFile;
    private File sensorFile;
    private File appNamesFile;

    // File writers
    private FileWriter imuWriter;
    private FileWriter sensorWriter;
    private FileWriter appNamesWriter;

    // Singleton instance
    private static volatile FileRepository instance;

    /**
     * Get the singleton instance of FileRepository.
     * @param context Application context
     * @return The singleton instance
     */
    public static FileRepository getInstance(Context context) {
        if (instance == null) {
            synchronized (FileRepository.class) {
                if (instance == null) {
                    instance = new FileRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private FileRepository(Context context) {
        this.context = context;
    }

    // =====================
    // Directory Management
    // =====================

    /**
     * Get the base data directory.
     * @return The data directory File
     */
    public File getDataDirectory() {
        return new File(Environment.getExternalStorageDirectory(), Constants.DATA_DIR);
    }

    /**
     * Ensure the data directory exists.
     * @return true if directory exists or was created successfully
     */
    public boolean ensureDataDirectoryExists() {
        File dir = getDataDirectory();
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (created) {
                Log.i(TAG, "Created data directory: " + dir.getAbsolutePath());
            } else {
                Log.e(TAG, "Failed to create data directory: " + dir.getAbsolutePath());
            }
            return created;
        }
        return true;
    }

    // =====================
    // File Initialization
    // =====================

    /**
     * Initialize all data files and writers.
     * @return true if initialization was successful
     */
    public boolean initializeFiles() {
        if (!ensureDataDirectoryExists()) {
            return false;
        }

        File dir = getDataDirectory();

        try {
            // Initialize sensor file
            sensorFile = new File(dir, Constants.FILE_SENSOR_CSV);
            sensorWriter = new FileWriter(sensorFile, true);
            Log.i(TAG, "Initialized sensor file: " + sensorFile.getAbsolutePath());

            // Initialize IMU file
            imuFile = new File(dir, Constants.FILE_IMU_CSV);
            imuWriter = new FileWriter(imuFile, true);
            Log.i(TAG, "Initialized IMU file: " + imuFile.getAbsolutePath());

            // Initialize app names file
            appNamesFile = new File(dir, Constants.FILE_APP_NAMES);
            appNamesWriter = new FileWriter(appNamesFile, true);
            Log.i(TAG, "Initialized app names file: " + appNamesFile.getAbsolutePath());

            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to initialize files", e);
            return false;
        }
    }

    /**
     * Initialize a specific file writer.
     * @param fileName The file name (e.g., "sensor.csv", "IMU.csv")
     */
    public void initializeFileWriter(String fileName) {
        if (!ensureDataDirectoryExists()) {
            return;
        }

        File dir = getDataDirectory();

        try {
            if (fileName.contains("sensor.csv")) {
                sensorFile = new File(dir, Constants.FILE_SENSOR_CSV);
                sensorWriter = new FileWriter(sensorFile, true);
                Log.d(TAG, "Reinitialized sensor writer");
            } else if (fileName.contains("IMU.csv")) {
                imuFile = new File(dir, Constants.FILE_IMU_CSV);
                imuWriter = new FileWriter(imuFile, true);
                Log.d(TAG, "Reinitialized IMU writer");
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to initialize file writer: " + fileName, e);
        }
    }

    // =====================
    // File Size Management
    // =====================

    /**
     * Check if a file exceeds the maximum size.
     * @param filePath The relative file path (e.g., "0.Mobibox/sensor.csv")
     * @param maxSizeInBytes The maximum size in bytes
     * @return true if file size exceeds the limit
     */
    public boolean checkFileSize(String filePath, long maxSizeInBytes) {
        File file = new File(Environment.getExternalStorageDirectory(), filePath);
        if (file.exists()) {
            long fileSize = file.length();
            return fileSize > maxSizeInBytes;
        }
        return false;
    }

    /**
     * Check if a file exceeds the default maximum size (5MB).
     * @param filePath The relative file path
     * @return true if file size exceeds 5MB
     */
    public boolean checkFileSizeExceeded(String filePath) {
        return checkFileSize(filePath, Constants.MAX_FILE_SIZE_BYTES);
    }

    // =====================
    // File Rotation
    // =====================

    /**
     * Rotate a file when it exceeds the size limit.
     * Renames the file with a timestamp and creates a new empty file.
     * @param filePath The relative file path (e.g., "0.Mobibox/IMU.csv")
     * @return true if rotation was successful
     */
    public boolean rotateFile(String filePath) {
        try {
            File file = new File(Environment.getExternalStorageDirectory(), filePath);
            if (!file.exists()) {
                Log.w(TAG, "File does not exist, no need to rotate: " + filePath);
                return false;
            }

            // Generate backup file name with timestamp
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
            String timestamp = sdf.format(new Date());

            String fileName = file.getName();
            String fileNameWithoutExt = fileName.substring(0, fileName.lastIndexOf('.'));
            String fileExt = fileName.substring(fileName.lastIndexOf('.'));

            String backupFileName = fileNameWithoutExt + "_backup_" + timestamp + fileExt;
            File backupFile = new File(file.getParent(), backupFileName);

            // Close the current writer
            closeFileWriter(filePath);

            // Rename the file
            boolean renamed = file.renameTo(backupFile);
            if (!renamed) {
                Log.e(TAG, "Failed to rename file: " + filePath);
                initializeFileWriter(filePath);
                return false;
            }

            long fileSizeMB = backupFile.length() / (1024 * 1024);
            Log.i(TAG, "File rotated: " + fileName + " -> " + backupFileName + " (Size: " + fileSizeMB + "MB)");

            // Create new empty file and initialize writer
            initializeFileWriter(filePath);

            // Notify user
            new Handler(Looper.getMainLooper()).post(() -> {
                Toast.makeText(context,
                        fileName + " backed up (Size: " + fileSizeMB + "MB)",
                        Toast.LENGTH_SHORT).show();
            });

            return true;
        } catch (Exception e) {
            Log.e(TAG, "File rotation error: " + filePath, e);
            initializeFileWriter(filePath);
            return false;
        }
    }

    // =====================
    // File Reading
    // =====================

    /**
     * Read CSV file content with deduplication by timestamp.
     * @param filePath The relative file path
     * @return The CSV content as string, or empty string if failed
     */
    public String readCSVFile(String filePath) {
        StringBuilder csvContent = new StringBuilder();
        File file = new File(Environment.getExternalStorageDirectory(), filePath);
        Set<String> timestampSet = new HashSet<>();

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                String[] columns = line.split(",");
                if (columns.length > 0) {
                    String timestamp = columns[0];
                    if (!timestampSet.contains(timestamp)) {
                        timestampSet.add(timestamp);
                        csvContent.append(line).append("\n");
                    }
                }
            }
            Log.i(TAG, "Read CSV file successfully: " + filePath);
        } catch (IOException e) {
            Log.e(TAG, "Failed to read CSV file: " + filePath, e);
        }
        return csvContent.toString();
    }

    // =====================
    // File Clearing
    // =====================

    /**
     * Clear a CSV file's content.
     * Closes the writer, clears the file, and reinitializes the writer.
     * @param filePath The relative file path
     */
    public void clearCSVFile(String filePath) {
        File file = new File(Environment.getExternalStorageDirectory(), filePath);

        // Close existing writer
        closeFileWriter(filePath);

        // Clear file content
        try (FileWriter writer = new FileWriter(file, false)) {
            writer.write("");
            Log.i(TAG, "CSV file cleared: " + filePath);
        } catch (IOException e) {
            Log.e(TAG, "Failed to clear CSV file: " + filePath, e);
        }

        // Reinitialize writer
        initializeFileWriter(filePath);
    }

    // =====================
    // Writer Management
    // =====================

    /**
     * Close a specific file writer.
     * @param filePath The file path to close writer for
     */
    public void closeFileWriter(String filePath) {
        try {
            if (filePath.contains("sensor.csv") && sensorWriter != null) {
                sensorWriter.close();
                sensorWriter = null;
                Log.d(TAG, "Closed sensor writer");
            } else if (filePath.contains("IMU.csv") && imuWriter != null) {
                imuWriter.close();
                imuWriter = null;
                Log.d(TAG, "Closed IMU writer");
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to close file writer: " + filePath, e);
        }
    }

    /**
     * Close all file writers.
     */
    public void closeAllWriters() {
        try {
            if (sensorWriter != null) {
                sensorWriter.close();
                sensorWriter = null;
                Log.d(TAG, "Closed sensor writer");
            }
            if (imuWriter != null) {
                imuWriter.close();
                imuWriter = null;
                Log.d(TAG, "Closed IMU writer");
            }
            if (appNamesWriter != null) {
                appNamesWriter.close();
                appNamesWriter = null;
                Log.d(TAG, "Closed app names writer");
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to close writers", e);
        }
    }

    // =====================
    // Data Writing
    // =====================

    /**
     * Write sensor data to the sensor CSV file.
     * @param data The data line to write
     * @return true if write was successful
     */
    public boolean writeSensorData(String data) {
        if (sensorWriter == null) {
            Log.e(TAG, "Sensor writer is null, reinitializing");
            initializeFileWriter(Constants.FILE_SENSOR_CSV);
        }

        if (sensorWriter == null) {
            Log.e(TAG, "Failed to initialize sensor writer");
            return false;
        }

        try {
            sensorWriter.append(data).append("\n");
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to write sensor data", e);
            return false;
        }
    }

    /**
     * Write IMU data to the IMU CSV file.
     * @param data The data line to write
     * @return true if write was successful
     */
    public boolean writeImuData(String data) {
        if (imuWriter == null) {
            Log.e(TAG, "IMU writer is null, reinitializing");
            initializeFileWriter(Constants.FILE_IMU_CSV);
        }

        if (imuWriter == null) {
            Log.e(TAG, "Failed to initialize IMU writer");
            return false;
        }

        try {
            imuWriter.append(data).append("\n");
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Failed to write IMU data", e);
            return false;
        }
    }

    /**
     * Flush the sensor writer.
     */
    public void flushSensorWriter() {
        if (sensorWriter != null) {
            try {
                sensorWriter.flush();
            } catch (IOException e) {
                Log.e(TAG, "Failed to flush sensor writer", e);
            }
        }
    }

    /**
     * Flush the IMU writer.
     */
    public void flushImuWriter() {
        if (imuWriter != null) {
            try {
                imuWriter.flush();
            } catch (IOException e) {
                Log.e(TAG, "Failed to flush IMU writer", e);
            }
        }
    }

    // =====================
    // Getters
    // =====================

    public FileWriter getImuWriter() {
        return imuWriter;
    }

    public FileWriter getSensorWriter() {
        return sensorWriter;
    }

    public File getImuFile() {
        return imuFile;
    }

    public File getSensorFile() {
        return sensorFile;
    }
}