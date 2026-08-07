package com.example.mobibox.managers;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;

import com.example.mobibox.Constants;

import java.util.List;

/**
 * Manages sensor data collection for IMU (accelerometer, gyroscope, magnetometer)
 * and step counter sensors.
 *
 * This class handles:
 * - Sensor initialization and registration
 * - IMU data collection at 40Hz via a background thread
 * - Step counter management
 * - Sensor availability checking and restart
 */
public class SensorDataManager implements SensorEventListener {
    private static final String TAG = "SensorDataManager";

    private final Context context;
    private SensorManager sensorManager;

    // Sensors
    private Sensor accelerometer;
    private Sensor gyroscope;
    private Sensor magnetometer;
    private Sensor stepCounter;

    // IMU data arrays (public for direct access by DataService)
    public float[] accelData = new float[3];
    public float[] gyroData = new float[3];
    public float[] magData = new float[3];

    // Step counter data
    public int startSteps = 0;
    public int endSteps = 0;
    public int stepCountSensor = 0;
    public boolean stepFlag = true;
    public long lastSensorUpdateTime = 0;

    // Background thread for sensor processing
    private HandlerThread sensorThread;
    private Handler sensorHandler;

    // Sensor availability flags
    private boolean accelerometerAvailable = false;
    private boolean gyroscopeAvailable = false;
    private boolean magnetometerAvailable = false;
    private boolean stepCounterAvailable = false;

    // Monitoring flag
    private boolean isMonitoring = false;

    // Listener for sensor events
    private SensorDataListener listener;

    /**
     * Interface for receiving sensor data events.
     */
    public interface SensorDataListener {
        void onAccelDataChanged(float[] values);
        void onGyroDataChanged(float[] values);
        void onMagDataChanged(float[] values);
        void onStepCountChanged(int steps, int increment);
    }

    /**
     * Get the singleton instance of SensorDataManager.
     */
    private static volatile SensorDataManager instance;

    public static SensorDataManager getInstance(Context context) {
        if (instance == null) {
            synchronized (SensorDataManager.class) {
                if (instance == null) {
                    instance = new SensorDataManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private SensorDataManager(Context context) {
        this.context = context;
        initSensorManager();
    }

    private void initSensorManager() {
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager == null) {
            Log.e(TAG, "SensorManager not available");
            return;
        }

        // Get sensors
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);

        // Check availability
        accelerometerAvailable = accelerometer != null;
        gyroscopeAvailable = gyroscope != null;
        magnetometerAvailable = magnetometer != null;
        stepCounterAvailable = stepCounter != null;

        // Log sensor info
        logSensorInfo();
    }

    private void logSensorInfo() {
        Log.i(TAG, "=== Sensor Availability ===");
        Log.i(TAG, "Accelerometer: " + (accelerometerAvailable ? "Available" : "Not available"));
        Log.i(TAG, "Gyroscope: " + (gyroscopeAvailable ? "Available" : "Not available"));
        Log.i(TAG, "Magnetometer: " + (magnetometerAvailable ? "Available" : "Not available"));
        Log.i(TAG, "Step Counter: " + (stepCounterAvailable ? "Available" : "Not available"));

        if (stepCounterAvailable) {
            Log.i(TAG, "Step counter sensor: " + stepCounter.getName());
            Log.i(TAG, "Step counter vendor: " + stepCounter.getVendor());
            Log.i(TAG, "Step counter max range: " + stepCounter.getMaximumRange());
        }

        // List all sensors
        List<Sensor> allSensors = sensorManager.getSensorList(Sensor.TYPE_ALL);
        Log.d(TAG, "Total sensors available: " + allSensors.size());
    }

    /**
     * Start the sensor handler thread.
     */
    public void startHandlerThread() {
        if (sensorThread == null) {
            Log.i(TAG, "Starting sensor handler thread");
            sensorThread = new HandlerThread("SensorProcessingThread");
            sensorThread.start();
            sensorHandler = new Handler(sensorThread.getLooper());
            Log.i(TAG, "Sensor handler thread started");
        }
    }

    /**
     * Stop the sensor handler thread.
     */
    public void stopHandlerThread() {
        if (sensorThread != null) {
            Log.i(TAG, "Stopping sensor handler thread");
            sensorThread.quitSafely();
            try {
                sensorThread.join(1000);
            } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while waiting for sensor thread to stop", e);
            }
            sensorThread = null;
            sensorHandler = null;
            Log.i(TAG, "Sensor handler thread stopped");
        }
    }

    /**
     * Start monitoring all sensors.
     */
    public void startMonitoring() {
        if (isMonitoring) {
            Log.w(TAG, "Already monitoring sensors");
            return;
        }

        if (!checkSensorAvailability()) {
            Log.e(TAG, "Required sensors not available");
            return;
        }

        Log.i(TAG, "Starting sensor monitoring");
        isMonitoring = true;

        // Register IMU sensors with background handler
        if (sensorHandler != null) {
            sensorManager.registerListener(this, accelerometer, Constants.SENSOR_DELAY_IMU_US, 0, sensorHandler);
            sensorManager.registerListener(this, gyroscope, Constants.SENSOR_DELAY_IMU_US, 0, sensorHandler);
            sensorManager.registerListener(this, magnetometer, Constants.SENSOR_DELAY_IMU_US, 0, sensorHandler);
            Log.i(TAG, "IMU sensors registered with background handler at 50Hz");
        } else {
            // Fallback to main thread handler
            Handler mainHandler = new Handler(Looper.getMainLooper());
            sensorManager.registerListener(this, accelerometer, Constants.SENSOR_DELAY_IMU_US, SensorManager.SENSOR_DELAY_FASTEST);
            sensorManager.registerListener(this, gyroscope, Constants.SENSOR_DELAY_IMU_US, SensorManager.SENSOR_DELAY_FASTEST);
            sensorManager.registerListener(this, magnetometer, Constants.SENSOR_DELAY_IMU_US, SensorManager.SENSOR_DELAY_FASTEST);
            Log.w(TAG, "IMU sensors registered with main thread handler (fallback)");
        }

        // Register step counter
        if (stepCounter != null) {
            boolean registered = sensorManager.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_NORMAL);
            Log.i(TAG, "Step counter registered: " + (registered ? "success" : "failed"));
        }
    }

    /**
     * Stop monitoring all sensors.
     */
    public void stopMonitoring() {
        if (!isMonitoring) {
            return;
        }

        Log.i(TAG, "Stopping sensor monitoring");
        isMonitoring = false;
        sensorManager.unregisterListener(this);
    }

    /**
     * Restart sensors if they become unresponsive.
     */
    public void restartSensors() {
        sensorManager.unregisterListener(this);

        if (sensorHandler != null) {
            sensorHandler.post(() -> {
                sensorManager.registerListener(this, accelerometer, Constants.SENSOR_DELAY_IMU_US, 0, sensorHandler);
                sensorManager.registerListener(this, gyroscope, Constants.SENSOR_DELAY_IMU_US, 0, sensorHandler);
                sensorManager.registerListener(this, magnetometer, Constants.SENSOR_DELAY_IMU_US, 0, sensorHandler);
                Log.i(TAG, "IMU sensors restarted with background handler at 50Hz");
            });
        } else {
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(() -> {
                sensorManager.registerListener(this, accelerometer, Constants.SENSOR_DELAY_IMU_US, SensorManager.SENSOR_DELAY_FASTEST);
                sensorManager.registerListener(this, gyroscope, Constants.SENSOR_DELAY_IMU_US, SensorManager.SENSOR_DELAY_FASTEST);
                sensorManager.registerListener(this, magnetometer, Constants.SENSOR_DELAY_IMU_US, SensorManager.SENSOR_DELAY_FASTEST);
                Log.w(TAG, "IMU sensors restarted with main thread handler (fallback)");
            });
        }
    }

    /**
     * Check if all required sensors are available.
     */
    public boolean checkSensorAvailability() {
        Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor gyro = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        Sensor mag = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        if (accel == null || gyro == null || mag == null) {
            Log.e(TAG, "Missing required sensors: accel=" + (accel != null) + ", gyro=" + (gyro != null) + ", mag=" + (mag != null));
            return false;
        }
        return true;
    }

    /**
     * Restart step counter if it becomes unresponsive.
     */
    public void restartStepCounter() {
        if (stepCounter != null && sensorManager != null) {
            sensorManager.unregisterListener(this, stepCounter);
            boolean registered = sensorManager.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_NORMAL);
            Log.i(TAG, "Step counter restarted: " + (registered ? "success" : "failed"));
            lastSensorUpdateTime = System.currentTimeMillis();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                System.arraycopy(event.values, 0, accelData, 0, 3);
                if (listener != null) {
                    listener.onAccelDataChanged(accelData);
                }
                break;

            case Sensor.TYPE_GYROSCOPE:
                System.arraycopy(event.values, 0, gyroData, 0, 3);
                if (listener != null) {
                    listener.onGyroDataChanged(gyroData);
                }
                break;

            case Sensor.TYPE_MAGNETIC_FIELD:
                System.arraycopy(event.values, 0, magData, 0, 3);
                if (listener != null) {
                    listener.onMagDataChanged(magData);
                }
                break;

            case Sensor.TYPE_STEP_COUNTER:
                endSteps = (int) event.values[0];
                lastSensorUpdateTime = System.currentTimeMillis();

                if (stepFlag) {
                    stepFlag = false;
                    startSteps = endSteps;
                    Log.i(TAG, "Step counter initialized: startSteps=" + startSteps);
                }

                stepCountSensor = endSteps - startSteps;
                Log.d(TAG, "Step counter updated: endSteps=" + endSteps + ", increment=" + stepCountSensor);

                if (listener != null) {
                    listener.onStepCountChanged(endSteps, stepCountSensor);
                }
                break;
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No implementation required
    }

    // =====================
    // Getters
    // =====================

    public float[] getAccelData() {
        return accelData;
    }

    public float[] getGyroData() {
        return gyroData;
    }

    public float[] getMagData() {
        return magData;
    }

    public int getStepCountSensor() {
        return stepCountSensor;
    }

    public int getEndSteps() {
        return endSteps;
    }

    public int getStartSteps() {
        return startSteps;
    }

    public long getLastSensorUpdateTime() {
        return lastSensorUpdateTime;
    }

    public boolean isMonitoring() {
        return isMonitoring;
    }

    public boolean isAccelerometerAvailable() {
        return accelerometerAvailable;
    }

    public boolean isGyroscopeAvailable() {
        return gyroscopeAvailable;
    }

    public boolean isMagnetometerAvailable() {
        return magnetometerAvailable;
    }

    public boolean isStepCounterAvailable() {
        return stepCounterAvailable;
    }

    public Handler getSensorHandler() {
        return sensorHandler;
    }

    // =====================
    // Setters
    // =====================

    public void setListener(SensorDataListener listener) {
        this.listener = listener;
    }

    public void resetStepStart() {
        startSteps = endSteps;
        stepFlag = true;
    }

    /**
     * Get combined IMU data as a formatted string for CSV.
     * Format: accel_x,accel_y,accel_z,gyro_x,gyro_y,gyro_z,mag_x,mag_y,mag_z
     */
    public synchronized String getImuDataCsv() {
        return String.format("%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f",
                accelData[0], accelData[1], accelData[2],
                gyroData[0], gyroData[1], gyroData[2],
                magData[0], magData[1], magData[2]);
    }
}