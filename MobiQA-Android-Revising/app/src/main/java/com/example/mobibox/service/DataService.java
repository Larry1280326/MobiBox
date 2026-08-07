package com.example.mobibox.service;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.TrafficStats;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;
import android.provider.Settings;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.baidu.location.BDAbstractLocationListener;
import com.baidu.location.BDLocation;
import com.baidu.location.LocationClient;
import com.baidu.location.LocationClientOption;
import com.baidu.location.Poi;
import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TimeZone;
import java.util.TreeMap;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import android.Manifest;

import com.example.mobibox.Constants;
import com.example.mobibox.InterventionActivity;
import com.example.mobibox.MainActivity;
import com.example.mobibox.R;
import com.example.mobibox.data.FileRepository;
import com.example.mobibox.data.SharedPreferencesHelper;
import com.example.mobibox.data.UploadQueueManager;
import com.example.mobibox.managers.BluetoothScanner;
import com.example.mobibox.receiver.NetworkReceiver;
import com.example.mobibox.managers.SensorDataManager;
import com.example.mobibox.managers.notification.DailyLogNotificationManager;
import com.example.mobibox.managers.notification.HourlyUpdateNotificationManager;
import com.example.mobibox.managers.notification.InterventionNotificationManager;
import com.example.mobibox.network.HttpApiClient;

@SuppressLint("MissingPermission")
public class DataService extends Service implements SensorEventListener, LocationListener {

    private final String TAG = "DataService";

    public boolean isMonitoring = false;

    public SensorManager sensorManager;
    public Sensor accelerometer, stepCounter, gyroscope, magnetometer;
    public LocationManager locationManager;
    public WifiManager wifiManager;
    public AudioManager audioManager;
    public PowerManager powerManager;
    public File imuFile, txtFile, sensorFile;
    public BluetoothAdapter bluetoothAdapter;
    public BluetoothManager bluetoothManager;

    public FileWriter imuWriter, txtWriter, sensorWriter;

    public long startTime;
    public int screenTouchCount = 0; // 触屏计数
    public float screenOnRatio = -1; // 亮屏时间比例
    public long screenOnTimeCounter = -1;
    public float screenCounter = 0;
    public float screenOnCounter = 1;
    public float volumePercentage = -1;
    public int wifiStatus = -1;
    public String connect_wifi_name = "N/A";
    public String bluetoothDevices;

    public int battery_level = -1;
    public String appName = null;

    // sensorDataList改为同步集合
    public List<String> sensorDataList = Collections.synchronizedList(new ArrayList<>());
    public float[] accelData = new float[3]; // 加速度数据
    public float[] gyroData = new float[3]; // 陀螺仪数据
    public float[] magData = new float[3]; // 磁力计数据
    public float[] combinedData = new float[9];

    public double gpsLat1 = Double.NaN, gpsLon1 = Double.NaN;
    public String currentAppName = "";

    // 记录上次查询时的基准时间，用于计算增量使用时间
    private long lastQueryTime = 0;
    // 存储上次各应用的前台时间，用于计算增量
    private Map<String, Long> lastForegroundTimeMap = new ConcurrentHashMap<>();

    public long startRxBytes, startTxBytes; // 网络流量
    public long endRxBytes, endTxBytes;
    public double networkTrafficInMB = -1; // 每5秒的网络流量（单位：MB）
    public int tx_traffic = -1;
    public int rx_traffic = -1;

    public int startSteps = 0, endSteps = 0; // 步数记录
    public int stepCount = 0; // 步数增量，按照传感器访问次数计
    public int stepcount_sensor = 0; // 步数增量，按照传感器本身步数增量记录

    boolean stepflag = true; // 初次记步标志位
    public long lastStepTime = 0; // 用于存储上一次有效步伐的时间

    public boolean flag2 = false;
    public boolean accelUpdated = false; // 加速度计是否已更新
    public boolean gyroUpdated = false; // 陀螺仪是否已更新
    public boolean magUpdated = false; // 磁场传感器是否已更新
    public boolean allSensorsUpdated = false; // 所有传感器是否均已更新

    private final Map<Long, float[]> sensorCache = new ConcurrentHashMap<>();
    // Time constants moved to Constants.java
    // TIME_WINDOW_NS, MAX_DATA_PER_CYCLE, TOAST_COOLDOWN_MS now in Constants

    private long currentFile;
    private long newFile;
    private String userId;
    private int counter = 0;
    private int errorCounter = 0;
    public boolean writeflag = true;
    Random random = new Random();
    private int randomNumber;
    public Handler handler = new Handler(Looper.getMainLooper());
    // Toast防抖：记录上次显示Toast的时间，避免频繁提示
    private long lastUploadErrorToastTime = 0;
    // IMU实时写入计数器（用于批量刷新缓冲区）
    private int imuWriteCounter = 0;
    // 传感器处理后台线程（解决主线程阻塞问题）
    private android.os.HandlerThread sensorThread;
    private android.os.Handler sensorHandler;
    // URL constants moved to Constants.java - use Constants.API_HOST and Constants.getXXXUrl()

    private PowerManager.WakeLock wakeLock;
    private LocationClient mLocationClient = null;

    private static final int RETRY_DELAY_MS = 5000; // 重試間隔 5 秒
    private int initRetryCount = 0;
    private static final int MAX_RETRY_ATTEMPTS = 3; // 最多重試 3 次
    private boolean isSdkInitialized = false; // 標記 SDK 是否成功初始化

    // 新增：儲存地址和 POI
    public String currentAddress = "N/A"; // 當前地址，初始值為 "N/A"
    public String currentPoi = "N/A"; // 當前 POI，初始值為 "N/A"
    private String lastInterventionContent = "";
    private static final String CHANNEL_ID = "INTERVENTION_CHANNEL";
    private NotificationManager notificationManager;
    private Handler interventionHandler = new Handler(Looper.getMainLooper());
    // Intervals moved to Constants.java - use Constants.INTERVENTION_CHECK_INTERVAL_MS etc.
    private BluetoothLeScanner bluetoothLeScanner;
    private Handler sensorCheckHandler = new Handler(Looper.getMainLooper());
    // Sensor check interval moved to Constants.SENSOR_CHECK_INTERVAL_MS
    private long lastSensorUpdateTime = 0;
    private boolean isScanning = false;
    private Handler bluetoothHandler = new Handler(Looper.getMainLooper());
    private Handler stepSensorRefreshHandler = new Handler(Looper.getMainLooper());
    // Bluetooth intervals moved to Constants: BLUETOOTH_SCAN_INTERVAL_MS, BLUETOOTH_SCAN_DURATION_MS
    public List<BluetoothScanResult> nearbyBluetoothDevices = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, String> bondedNameByAddress = new ConcurrentHashMap<>(); // 地址->已配对名称
    private final Map<String, String> deviceNameCache = new ConcurrentHashMap<>(); // 设备名称缓存：地址->名称
    private final Map<String, Integer> deviceNameRetryCount = new ConcurrentHashMap<>(); // 名称解析重试计数
    private final List<String> unknownDeviceQueue = Collections.synchronizedList(new ArrayList<>()); // 未解析成功的设备队列

    // Extracted managers / repositories
    private FileRepository fileRepository;
    private SharedPreferencesHelper sharedPreferencesHelper;
    private UploadQueueManager uploadQueueManager;
    private NetworkReceiver networkReceiver;
    private OkHttpClient uploadHttpClient;
    private final Object sensorFileLock = new Object();
    private final Object imuFileLock = new Object();
    private final AtomicBoolean sensorUploadInProgress = new AtomicBoolean(false);
    private final AtomicBoolean imuUploadInProgress = new AtomicBoolean(false);
    private final AtomicBoolean pendingUploadInProgress = new AtomicBoolean(false);
    private final AtomicBoolean interventionFetchInProgress = new AtomicBoolean(false);
    private boolean bluetoothPeriodicStarted = false;
    private boolean stepSensorRefreshStarted = false;

    private static class BluetoothScanResult {
        public String name;
        public String address;
        public int rssi;
        public long timestamp;

        public BluetoothScanResult(String name, String address, int rssi) {
            this.name = name != null ? name : "Unknown";
            this.address = address;
            this.rssi = rssi;
            this.timestamp = System.currentTimeMillis();
        }
    }

    // 用來檢查檔案大小
    private boolean checkFileSize(String filePath, long maxSizeInBytes) {
        File file = new File(Environment.getExternalStorageDirectory(), filePath);
        if (file.exists()) {
            long fileSize = file.length();
            return fileSize > maxSizeInBytes;
        }
        return false;
    }

    /**
     * 文件轮转：当文件过大时，重命名为带时间戳的备份文件，创建新文件继续写入
     * 同时将备份文件加入上传队列，确保离线期间的数据不丢失
     *
     * @param filePath 相对路径，如 "0.MobiBox/IMU.csv"
     * @return 是否成功轮转
     */
    private boolean rotateFile(String filePath) {
        try {
            File file = new File(Environment.getExternalStorageDirectory(), filePath);
            if (!file.exists()) {
                Log.w(TAG, "文件不存在，无需轮转: " + filePath);
                return false;
            }

            // 生成带时间戳的备份文件名
            SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US);
            String timestamp = sdf.format(new Date());

            String fileName = file.getName(); // 如 "IMU.csv"
            String fileNameWithoutExt = fileName.substring(0, fileName.lastIndexOf('.')); // "IMU"
            String fileExt = fileName.substring(fileName.lastIndexOf('.')); // ".csv"

            String backupFileName = fileNameWithoutExt + "_backup_" + timestamp + fileExt;
            File backupFile = new File(file.getParent(), backupFileName);

            // 1. 先关闭当前的 FileWriter
            closeFileWriter(filePath);

            // 2. 重命名文件
            boolean renamed = file.renameTo(backupFile);
            if (!renamed) {
                Log.e(TAG, "文件轮转失败：无法重命名 " + filePath);
                // 重新初始化 writer，确保可以继续写入
                initializeFileWriter(filePath);
                return false;
            }

            long fileSizeMB = backupFile.length() / (1024 * 1024);
            Log.i(TAG, "✅ 文件轮转成功: " + fileName + " → " + backupFileName +
                    " (大小: " + fileSizeMB + "MB)");

            // 3. 创建新的空文件并初始化 writer
            initializeFileWriter(filePath);

            // 4. 将备份文件加入上传队列，确保离线期间数据不丢失
            queueBackupFileForUpload(filePath, backupFile.getAbsolutePath());

            // 5. 可选：提示用户已备份
            final String finalFileName = fileName;
            final long finalFileSizeMB = fileSizeMB;
            new Handler(Looper.getMainLooper()).post(() -> {
                Toast.makeText(DataService.this,
                        finalFileName + " backed up (size: " + finalFileSizeMB + "MB)",
                        Toast.LENGTH_SHORT).show();
            });

            return true;
        } catch (Exception e) {
            Log.e(TAG, "文件轮转异常: " + filePath, e);
            // 确保 writer 能继续工作
            initializeFileWriter(filePath);
            return false;
        }
    }

    /**
     * 将轮转后的备份文件加入上传队列
     *
     * @param originalFilePath 原始文件路径 (如 "0.MobiBox/IMU.csv")
     * @param backupFilePath   备份文件的完整路径
     */
    private void queueBackupFileForUpload(String originalFilePath, String backupFilePath) {
        if (uploadQueueManager == null) {
            Log.w(TAG, "UploadQueueManager not initialized, cannot queue backup file");
            return;
        }

        // Determine upload type based on file type
        UploadQueueManager.UploadType uploadType;
        if (originalFilePath.contains("IMU")) {
            uploadType = UploadQueueManager.UploadType.IMU_BACKUP;
        } else {
            uploadType = UploadQueueManager.UploadType.SENSOR_BACKUP;
        }

        // Read the backup file content and add to queue
        try {
            File backupFile = new File(backupFilePath);
            if (!backupFile.exists()) {
                Log.w(TAG, "Backup file does not exist: " + backupFilePath);
                return;
            }

            // Read file content
            StringBuilder content = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.FileReader(backupFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    content.append(line).append("\n");
                }
            }

            if (content.length() > 0) {
                boolean queued = uploadQueueManager.addToQueue(uploadType, backupFilePath, content.toString());
                if (queued) {
                    Log.i(TAG, "📥 Backup file queued for upload: " + backupFilePath +
                            " (type: " + uploadType.getDisplayName() + ")");
                } else {
                    Log.w(TAG, "Failed to queue backup file: " + backupFilePath);
                }
            } else {
                Log.w(TAG, "Backup file is empty: " + backupFilePath);
            }

        } catch (Exception e) {
            Log.e(TAG, "Error reading backup file for queue: " + backupFilePath, e);
        }
    }

    public Runnable runnable = new Runnable() {
        @Override
        public void run() {
            // 如果允许写入数据，则执行写入操作
            if (!flag2 && writeflag) {
                writeData();
                counter++;
            }
            writeflag = true;

            // Sensor.csv upload logic (uses counter)
            // DEBUG: Changed from 12 cycles (2min) to 3 cycles (30s) for debugging
            // 每3次写入(30s)后尝试上传CSV文件
            if (counter >= 3) {
                counter = 0;
                uploadActiveCsvSegment(false);
            }

            // 再次调度此Runnable以延迟执行
            handler.postDelayed(this, 10 * 1000); // 10秒后再次运行
        }
    };

    // IMU 上传 Runnable - 每5秒执行一次IMU数据上传
    private Runnable imuUploadRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isMonitoring) {
                return; // 如果不在监控状态，不执行上传
            }

            uploadActiveCsvSegment(true);

            // Schedule next IMU upload
            handler.postDelayed(this, Constants.IMU_UPLOAD_INTERVAL_MS);
        }
    };

    // IMU 50Hz 写入 Ticker - 解耦数据采集和写入，匹配后端HAR模型采样假设（50 samples / 1s）
    private Runnable imuWriterRunnable = new Runnable() {
        @Override
        public void run() {
            // 1. 采样最新的数据（无论何时更新的）
            long currentTime = System.currentTimeMillis();
            String chinaTimestamp = getChinaISO8601Timestamp(currentTime);

            // 2. 格式化数据行（使用 synchronized 确保数据在读取时不会被 onSensorChanged 写入）
            String dataLine;
            synchronized (DataService.this) {
                dataLine = String.format("%s,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f",
                        chinaTimestamp,
                        accelData[0], accelData[1], accelData[2],
                        gyroData[0], gyroData[1], gyroData[2],
                        magData[0], magData[1], magData[2]);
            }

            // 调试日志：第一次写入时记录
            if (imuWriteCounter == 0) {
                Log.i(TAG, "IMU Ticker首次运行，数据: " + dataLine.substring(0, Math.min(50, dataLine.length())));
            }

            // 3. 写入文件（委托给 FileRepository）
            if (fileRepository == null) {
                fileRepository = FileRepository.getInstance(getApplicationContext());
            }
            synchronized (imuFileLock) {
                boolean imuWritten = fileRepository.writeImuData(dataLine);
                if (!imuWritten) {
                Log.e(TAG, "IMU 50Hz Ticker 写入失败（FileRepository.writeImuData 返回 false）");
                } else {
                    fileRepository.flushImuWriter();
                    if (++imuWriteCounter % 10 == 0) {
                    Log.d(TAG, "IMU数据已写入 " + imuWriteCounter + " 条");
                    }
                }
            }

            // 4. 重新调度自己（如果服务仍在运行）
            if (isMonitoring && sensorHandler != null) {
                sensorHandler.postDelayed(this, Constants.IMU_WRITE_INTERVAL_MS); // 20ms = 50Hz
            } else {
                // 如果Ticker停止，记录日志
                if (!isMonitoring) {
                    Log.w(TAG, "IMU Ticker停止：isMonitoring=false");
                }
                if (sensorHandler == null) {
                    Log.w(TAG, "IMU Ticker停止：sensorHandler=null");
                }
            }
        }
    };

    public static class Binder extends android.os.Binder {
        public DataService service;

        public Binder(DataService service) {
            this.service = service;
        }
    }

    private void restartSensors() {
        sensorManager.unregisterListener(this);
        // 重新注册时使用sensorHandler，确保回调在后台线程
        if (sensorHandler != null) {
            sensorHandler.post(() -> {
                // Use configured IMU sampling rate (see Constants.SENSOR_DELAY_IMU_US)
                // 使用sensorHandler让onSensorChanged在后台线程运行
                sensorManager.registerListener(this, accelerometer, Constants.SENSOR_DELAY_IMU_US, 0, sensorHandler);
                sensorManager.registerListener(this, gyroscope, Constants.SENSOR_DELAY_IMU_US, 0, sensorHandler);
                sensorManager.registerListener(this, magnetometer, Constants.SENSOR_DELAY_IMU_US, 0, sensorHandler);
                Log.i(TAG, "IMU restarted with background thread handler at 50Hz");
            });
        } else {
            // 如果sensorHandler未初始化，回退到主线程
            Handler handler = new Handler(Looper.getMainLooper());
            handler.post(() -> {
                sensorManager.registerListener(this, accelerometer, Constants.SENSOR_DELAY_IMU_US, SensorManager.SENSOR_DELAY_FASTEST);
                sensorManager.registerListener(this, gyroscope, Constants.SENSOR_DELAY_IMU_US, SensorManager.SENSOR_DELAY_FASTEST);
                sensorManager.registerListener(this, magnetometer, Constants.SENSOR_DELAY_IMU_US, SensorManager.SENSOR_DELAY_FASTEST);
                Log.w(TAG, "IMU restarted with main thread handler (sensorHandler not available)");
            });
        }
    }

    private boolean checkSensorAvailability() {
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        Sensor gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        Sensor magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);

        if (accelerometer == null || gyroscope == null || magnetometer == null) {
            Log.e(TAG, "Missing required sensors");
            return false;
        }
        return true;
    }

    private void startInterventionCheck() {
        // 立即执行一次，再开始定时
        fetchInterventionPeriodically();

        interventionHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                fetchInterventionPeriodically();
                interventionHandler.postDelayed(this, Constants.INTERVENTION_CHECK_INTERVAL_MS);
            }
        }, Constants.INTERVENTION_CHECK_INTERVAL_MS);
    }

    // 定时请求干预内容、Hourly Log 和原子活动（每5分钟）
    // 只有当三者都有新内容时，才保存缓存并发送通知
    private void fetchInterventionPeriodically() {
        if (!interventionFetchInProgress.compareAndSet(false, true)) {
            Log.d(TAG, "Intervention fetch already running, skip this tick");
            return;
        }
        new Thread(() -> {
            try {
                // 确保HttpApiClient已初始化
                if (HttpApiClient.getInstance() == null) {
                    Log.e(TAG, "HttpApiClient未初始化，无法请求内容");
                    return;
                }

                Log.d(TAG, "===== 开始并行获取三种内容 =====");

                // 使用 CountDownLatch 等待所有请求完成
                java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(3);

                // 存储结果
                final String[] hourlyLogResult = { null };
                final String[] interventionResult = { null };
                final String[] atomicActivitiesResult = { null };

                // 1. 并行获取 Hourly Log
                new Thread(() -> {
                    try {
                        hourlyLogResult[0] = HttpApiClient.getInstance().getHourlyLog();
                        Log.d(TAG, "Hourly Log 获取完成");
                    } catch (Exception e) {
                        Log.e(TAG, "获取 Hourly Log 失败: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                }).start();

                // 2. 并行获取 Intervention
                new Thread(() -> {
                    try {
                        interventionResult[0] = HttpApiClient.getInstance().getIntervention();
                        Log.d(TAG, "Intervention 获取完成");
                    } catch (Exception e) {
                        Log.e(TAG, "获取 Intervention 失败: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                }).start();

                // 3. 并行获取原子活动
                new Thread(() -> {
                    try {
                        atomicActivitiesResult[0] = fetchAtomicActivitiesJson();
                        Log.d(TAG, "原子活动 获取完成");
                    } catch (Exception e) {
                        Log.e(TAG, "获取原子活动失败: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                }).start();

                // 等待所有请求完成（最多等待30秒）
                boolean allCompleted = latch.await(30, java.util.concurrent.TimeUnit.SECONDS);

                if (!allCompleted) {
                    Log.w(TAG, "部分请求超时，不满足三者都有新内容的条件");
                    return;
                }

                Log.d(TAG, "===== 所有请求完成，开始检查新内容 =====");

                // 检查三者是否都有新内容
                boolean hasNewHourlyLog = false;
                boolean hasNewIntervention = false;
                boolean hasNewAtomicActivities = false;

                // 检查 Hourly Log
                if (!TextUtils.isEmpty(hourlyLogResult[0])) {
                    hasNewHourlyLog = HourlyUpdateNotificationManager.getInstance(this)
                            .checkIfNew(hourlyLogResult[0]);
                    Log.d(TAG, "Hourly Log 是否有新内容: " + hasNewHourlyLog +
                            " (首次运行或缓存为空时视为新内容)");
                } else {
                    Log.w(TAG, "Hourly Log 响应为空，无法检查新内容");
                }

                // 检查 Intervention
                if (!TextUtils.isEmpty(interventionResult[0])) {
                    hasNewIntervention = InterventionNotificationManager.getInstance(this)
                            .checkIfNew(interventionResult[0]);
                    Log.d(TAG, "Intervention 是否有新内容: " + hasNewIntervention +
                            " (首次运行或缓存为空时视为新内容)");
                } else {
                    Log.w(TAG, "Intervention 响应为空，无法检查新内容");
                }

                // 检查原子活动
                if (!TextUtils.isEmpty(atomicActivitiesResult[0])) {
                    hasNewAtomicActivities = checkIfAtomicActivitiesNew(atomicActivitiesResult[0]);
                    Log.d(TAG, "原子活动 是否有新内容: " + hasNewAtomicActivities +
                            " (首次运行或缓存为空时视为新内容)");
                } else {
                    Log.w(TAG, "原子活动 响应为空，无法检查新内容");
                }

                // 只有当三者都有新内容时，才保存缓存并发送通知
                if (hasNewHourlyLog || hasNewIntervention || hasNewAtomicActivities) {
                    Log.d(TAG, "===== 三者都有新内容，保存缓存并发送通知 =====");

                    // 保存并通知 Hourly Log
                    if (hasNewHourlyLog && !TextUtils.isEmpty(hourlyLogResult[0])) {
                        HourlyUpdateNotificationManager.getInstance(this)
                                .handleNewHourlyLog(hourlyLogResult[0]);
                    }

                    // 保存并通知 Intervention
                    if (hasNewIntervention && !TextUtils.isEmpty(interventionResult[0])) {
                        InterventionNotificationManager.getInstance(this)
                                .handleNewIntervention(interventionResult[0]);
                    }

                    // 保存原子活动
                    if (hasNewAtomicActivities && !TextUtils.isEmpty(atomicActivitiesResult[0])) {
                        saveAtomicActivities(atomicActivitiesResult[0]);
                    }

                    Log.d(TAG, "===== 缓存保存和通知发送完成 =====");
                } else {
                    Log.d(TAG, "===== 不满足三者都有新内容的条件，跳过保存 =====");
                    Log.d(TAG, "Hourly Log有新内容: " + hasNewHourlyLog);
                    Log.d(TAG, "Intervention有新内容: " + hasNewIntervention);
                    Log.d(TAG, "原子活动有新内容: " + hasNewAtomicActivities);
                }

            } catch (InterruptedException e) {
                Log.e(TAG, "等待请求完成被中断: " + e.getMessage());
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                Log.e(TAG, "定时请求内容失败: " + e.getMessage());
            } finally {
                interventionFetchInProgress.set(false);
            }
        }).start();
    }

    /**
     * 获取原子活动的 JSON 字符串（不处理，只返回）
     */
    private String fetchAtomicActivitiesJson() {
        try {
            if (TextUtils.isEmpty(userId)) {
                SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                userId = prefs.getString("userId", "");
            }
            if (TextUtils.isEmpty(userId)) {
                Log.w(TAG, "userId为空，跳过获取原子活动");
                return null;
            }

            // 计算距离上次获取原子活动的时间差（秒）
            long currentTime = System.currentTimeMillis();
            SharedPreferences atomicPrefs = getSharedPreferences("AtomicActivitiesPrefs", Context.MODE_PRIVATE);
            long lastFetchTime = atomicPrefs.getLong("last_fetch_timestamp", 0);

            int duration;
            if (lastFetchTime == 0) {
                duration = 0;
            } else {
                duration = (int) ((currentTime - lastFetchTime) / 1000);
            }

            OkHttpClient client = getUploadHttpClient();

            JSONObject payload = new JSONObject();
            payload.put("user", userId);
            payload.put("duration", duration);

            RequestBody body = RequestBody.create(
                    payload.toString(),
                    MediaType.get("application/json; charset=utf-8"));

            Request request = new Request.Builder()
                    .url(Constants.getAtomicActivitiesUrl())
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    String responseData = response.body().string();
                    JSONObject jsonResponse = new JSONObject(responseData);
                    String status = jsonResponse.optString("status", "");

                    if ("success".equals(status)) {
                        // 返回完整的响应JSON字符串
                        return responseData;
                    } else {
                        Log.w(TAG, "原子活动响应status不是success: " + status);
                        return null;
                    }
                } else {
                    Log.w(TAG, "获取原子活动失败: HTTP " + response.code());
                    return null;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取原子活动异常: " + e.getMessage());
            return null;
        }
    }

    /**
     * 检查原子活动是否有新内容
     */
    private boolean checkIfAtomicActivitiesNew(String atomicActivitiesJson) {
        try {
            if (TextUtils.isEmpty(atomicActivitiesJson)) {
                return false;
            }

            JSONObject jsonResponse = new JSONObject(atomicActivitiesJson);
            String status = jsonResponse.optString("status", "");

            if (!"success".equals(status)) {
                return false;
            }

            if (!jsonResponse.has("data")) {
                return false;
            }

            JSONObject data = jsonResponse.getJSONObject("data");

            // 检查是否有新数据（至少有一个数组不为空）
            JSONArray sportArr = data.optJSONArray("sport");
            JSONArray appArr = data.optJSONArray("appCategory");
            JSONArray locArr = data.optJSONArray("location");
            JSONArray moveArr = data.optJSONArray("movement");
            JSONArray stepArr = data.optJSONArray("stepCategory");
            JSONArray phoneArr = data.optJSONArray("phoneCategory");

            boolean hasNewData = (sportArr != null && sportArr.length() > 0) ||
                    (appArr != null && appArr.length() > 0) ||
                    (locArr != null && locArr.length() > 0) ||
                    (moveArr != null && moveArr.length() > 0) ||
                    (stepArr != null && stepArr.length() > 0) ||
                    (phoneArr != null && phoneArr.length() > 0);

            if (!hasNewData) {
                return false;
            }

            // 比对缓存的原子活动
            SharedPreferences atomicPrefs = getSharedPreferences("AtomicActivitiesPrefs", Context.MODE_PRIVATE);
            String cachedActivities = atomicPrefs.getString("last_atomic_activities", null);

            if (cachedActivities == null) {
                // 没有缓存，视为新内容
                return true;
            }

            // 比较内容是否有变化
            String newContent = data.toString();
            boolean changed = !newContent.equals(cachedActivities);

            return changed;
        } catch (Exception e) {
            Log.e(TAG, "检查原子活动是否有新内容失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 保存原子活动到本地缓存
     */
    private void saveAtomicActivities(String atomicActivitiesJson) {
        try {
            if (TextUtils.isEmpty(atomicActivitiesJson)) {
                return;
            }

            JSONObject jsonResponse = new JSONObject(atomicActivitiesJson);
            String status = jsonResponse.optString("status", "");

            if (!"success".equals(status)) {
                return;
            }

            if (!jsonResponse.has("data")) {
                return;
            }

            JSONObject data = jsonResponse.getJSONObject("data");

            // 构建完整的原子活动JSON
            JSONObject activities = new JSONObject();
            activities.put("sport", data.optJSONArray("sport"));
            activities.put("appCategory", data.optJSONArray("appCategory"));
            activities.put("location", data.optJSONArray("location"));
            activities.put("movement", data.optJSONArray("movement"));
            activities.put("step", data.optJSONArray("stepCategory"));
            activities.put("phoneCategory", data.optJSONArray("phoneCategory"));

            // 添加时间戳信息
            String startTime = jsonResponse.optString("start_timestamp", "");
            String endTime = jsonResponse.optString("end_timestamp", "");

            JSONArray startTimestampArray = new JSONArray();
            JSONArray endTimestampArray = new JSONArray();
            if (!TextUtils.isEmpty(startTime)) {
                startTimestampArray.put(startTime);
            }
            if (!TextUtils.isEmpty(endTime)) {
                endTimestampArray.put(endTime);
            }

            activities.put("startTimestamp", startTimestampArray);
            activities.put("endTimestamp", endTimestampArray);

            // 保存到本地
            saveAtomicActivitiesLocally(activities.toString());

            // 更新时间戳（覆盖旧内容，不是追加）
            long currentTime = System.currentTimeMillis();
            SharedPreferences atomicPrefs = getSharedPreferences("AtomicActivitiesPrefs", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = atomicPrefs.edit();
            // 使用 putString 覆盖，确保是覆盖而不是追加
            editor.putString("last_atomic_activities", data.toString());
            editor.putLong("last_fetch_timestamp", currentTime);
            editor.apply(); // apply() 是异步的，更安全

            Log.d(TAG, "原子活动已保存到本地缓存（覆盖旧内容）");
        } catch (Exception e) {
            Log.e(TAG, "保存原子活动失败: " + e.getMessage());
        }
    }

    // 2. 修改 fetchAtomicActivitiesIfPossible() 方法（保留原有功能，用于其他地方调用）
    private void fetchAtomicActivitiesIfPossible() {
        try {
            if (TextUtils.isEmpty(userId)) {
                SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                userId = prefs.getString("userId", "");
            }
            if (TextUtils.isEmpty(userId)) {
                Log.w(TAG, "userId为空，跳过获取原子活动");
                return;
            }

            // 计算距离上次获取原子活动的时间差（秒）
            long currentTime = System.currentTimeMillis();
            SharedPreferences atomicPrefs = getSharedPreferences("AtomicActivitiesPrefs", MODE_PRIVATE);
            long lastFetchTime = atomicPrefs.getLong("last_fetch_timestamp", 0);

            int duration;
            if (lastFetchTime == 0) {
                // 没有记录，第一次获取
                duration = 0;
                Log.d(TAG, "首次获取原子活动，duration设为0");
            } else {
                // 计算时间差（秒）
                duration = (int) ((currentTime - lastFetchTime) / 1000);
                Log.d(TAG, "距离上次获取原子活动已过去 " + duration + " 秒");
            }

            OkHttpClient client = getUploadHttpClient();

            JSONObject payload = new JSONObject();
            payload.put("user", userId);
            payload.put("duration", duration); // 传递计算出的时间差

            RequestBody body = RequestBody.create(
                    payload.toString(),
                    MediaType.get("application/json; charset=utf-8"));

            Request request = new Request.Builder()
                    .url(Constants.getAtomicActivitiesUrl())
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                int statusCode = response.code();
                Log.d(TAG, "原子活动响应状态码: " + statusCode);

                if (response.body() != null) {
                    String responseData = response.body().string();
                    Log.d(TAG, "原子活动响应数据: " + responseData);

                    JSONObject jsonResponse = new JSONObject(responseData);
                    String status = jsonResponse.optString("status", "");
                    String message = jsonResponse.optString("message", "");

                    Log.d(TAG, "响应status字段: " + status);
                    Log.d(TAG, "响应message字段: " + message);

                    if ("success".equals(status)) {
                        // 检查data字段是否存在
                        if (!jsonResponse.has("data")) {
                            Log.e(TAG, "响应中缺少data字段");
                            return;
                        }

                        // 解析完整的响应数据
                        JSONObject data = jsonResponse.getJSONObject("data");
                        Log.d(TAG, "data字段内容: " + data.toString());

                        // 构建完整的原子活动JSON
                        JSONObject activities = new JSONObject();
                        activities.put("sport", data.optJSONArray("sport"));
                        activities.put("appCategory", data.optJSONArray("appCategory"));
                        activities.put("location", data.optJSONArray("location"));
                        activities.put("movement", data.optJSONArray("movement"));
                        activities.put("step", data.optJSONArray("stepCategory")); // 注意字段名映射
                        activities.put("phoneCategory", data.optJSONArray("phoneCategory"));

                        // 添加时间戳信息
                        String startTime = jsonResponse.optString("start_timestamp", "");
                        String endTime = jsonResponse.optString("end_timestamp", "");

                        JSONArray startTimestampArray = new JSONArray();
                        JSONArray endTimestampArray = new JSONArray();
                        if (!TextUtils.isEmpty(startTime)) {
                            startTimestampArray.put(startTime);
                        }
                        if (!TextUtils.isEmpty(endTime)) {
                            endTimestampArray.put(endTime);
                        }

                        activities.put("startTimestamp", startTimestampArray);
                        activities.put("endTimestamp", endTimestampArray);

                        // 检查是否真的有新数据（至少有一个数组不为空）
                        boolean hasNewData = false;
                        JSONArray sportArr = data.optJSONArray("sport");
                        JSONArray appArr = data.optJSONArray("appCategory");
                        JSONArray locArr = data.optJSONArray("location");
                        JSONArray moveArr = data.optJSONArray("movement");
                        JSONArray stepArr = data.optJSONArray("stepCategory");
                        JSONArray phoneArr = data.optJSONArray("phoneCategory");

                        if ((sportArr != null && sportArr.length() > 0) ||
                                (appArr != null && appArr.length() > 0) ||
                                (locArr != null && locArr.length() > 0) ||
                                (moveArr != null && moveArr.length() > 0) ||
                                (stepArr != null && stepArr.length() > 0) ||
                                (phoneArr != null && phoneArr.length() > 0)) {
                            hasNewData = true;
                        }

                        saveAtomicActivitiesLocally(activities.toString());

                        if (hasNewData) {
                            // 只有真正有新数据时才更新时间戳
                            SharedPreferences.Editor editor = atomicPrefs.edit();
                            editor.putLong("last_fetch_timestamp", currentTime);
                            editor.apply();
                            Log.d(TAG, "原子活动已更新，时间戳已记录");
                            Log.d(TAG, "保存的原子活动: " + activities.toString());
                        } else {
                            Log.d(TAG, "响应成功但无新数据，不更新时间戳");
                            Log.d(TAG, "空数据响应: " + activities.toString());
                        }
                    } else if ("error".equals(status)) {
                        // 服务器返回error状态（如：没有找到数据）
                        String errorMessage = !TextUtils.isEmpty(message) ? message : "未知错误";
                        Log.w(TAG, "服务器返回error: " + errorMessage);
                        Log.w(TAG, "完整响应: " + responseData);
                    } else {
                        Log.w(TAG, "服务器返回未知状态: " + status);
                        Log.w(TAG, "完整响应: " + responseData);
                    }
                } else {
                    int code = response != null ? response.code() : -1;
                    String errorBody = "";
                    if (response != null && response.body() != null) {
                        try {
                            errorBody = response.body().string();
                        } catch (Exception e) {
                            errorBody = "无法读取错误信息";
                        }
                    }
                    Log.w(TAG, "获取原子活动失败: HTTP " + code);
                    Log.w(TAG, "错误响应: " + errorBody);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "获取原子活动异常: " + e.getMessage());
        }
    }

    private void saveAtomicActivitiesLocally(String activities) {
        SharedPreferences prefs = getSharedPreferences("InterventionPrefs", MODE_PRIVATE);
        prefs.edit().putString("last_atomic_activities", activities).apply();
    }

    // 已移除：每天凌晨清空本地干预展示数据的逻辑
    private void initAtomicActivitiesTimestamp() {
        SharedPreferences atomicPrefs = getSharedPreferences("AtomicActivitiesPrefs", MODE_PRIVATE);
        long lastFetchTime = atomicPrefs.getLong("last_fetch_timestamp", 0);

        if (lastFetchTime == 0) {
            Log.d(TAG, "首次启动，原子活动获取时间戳初始化为0");
        } else {
            long currentTime = System.currentTimeMillis();
            int timeSinceLastFetch = (int) ((currentTime - lastFetchTime) / 1000);
            Log.d(TAG, "上次获取原子活动时间：" + new java.util.Date(lastFetchTime) +
                    "，距离现在已过去：" + timeSinceLastFetch + "秒");
        }
    }

    /**
     * 启动传感器监控任务
     * 定期检查步数传感器是否还在工作，如果长时间没有更新则重新注册
     * 同时检查网络统计是否需要重新初始化
     */
    private void startSensorMonitoring() {
        lastSensorUpdateTime = System.currentTimeMillis(); // 初始化时间
        sensorCheckHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                long timeSinceLastUpdate = currentTime - lastSensorUpdateTime;

                // 如果超过60秒没有收到步数传感器更新（正常走路应该会有更新）
                if (timeSinceLastUpdate > 60 * 1000) {
                    Log.w(TAG, "⚠️ 步数传感器超过60秒未更新，可能被系统暂停，尝试重新注册");

                    // 重新注册步数传感器
                    if (stepCounter != null) {
                        sensorManager.unregisterListener(DataService.this, stepCounter);
                        boolean registered = sensorManager.registerListener(
                                DataService.this,
                                stepCounter,
                                SensorManager.SENSOR_DELAY_NORMAL);

                        if (registered) {
                            Log.i(TAG, "✅ 步数传感器重新注册成功");
                            lastSensorUpdateTime = currentTime; // 重置时间
                        } else {
                            Log.e(TAG, "❌ 步数传感器重新注册失败");
                        }
                    }
                } else {
                    Log.d(TAG, "✓ 步数传感器工作正常 (最后更新: " + (timeSinceLastUpdate / 1000) + "秒前)");
                }

                // 定期重新初始化网络统计（处理计数器重置问题）
                try {
                    long currentRx = TrafficStats.getTotalRxBytes();
                    long currentTx = TrafficStats.getTotalTxBytes();
                    if (currentRx != TrafficStats.UNSUPPORTED && currentTx != TrafficStats.UNSUPPORTED
                            && currentRx >= 0 && currentTx >= 0) {
                        // 检测计数器是否被重置（当前值小于之前记录的起始值）
                        if (currentRx < startRxBytes || currentTx < startTxBytes) {
                            Log.w(TAG, "网络计数器可能已重置，重新初始化网络统计: Rx " + currentRx + " < " + startRxBytes + " or Tx " + currentTx + " < " + startTxBytes);
                            startRxBytes = currentRx;
                            startTxBytes = currentTx;
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "检查网络统计时发生异常: " + e.getMessage());
                }

                // 继续下一次检查
                if (isMonitoring) {
                    sensorCheckHandler.postDelayed(this, Constants.SENSOR_CHECK_INTERVAL_MS);
                }
            }
        }, Constants.SENSOR_CHECK_INTERVAL_MS);

        Log.i(TAG, "✅ 传感器监控任务已启动，每30秒检查一次");
    }

    public void resetAtomicActivitiesTimestamp() {
        SharedPreferences atomicPrefs = getSharedPreferences("AtomicActivitiesPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = atomicPrefs.edit();
        editor.putLong("last_fetch_timestamp", 0);
        editor.apply();
        Log.d(TAG, "原子活动获取时间戳已重置");
    }

    // 4. 添加获取上次获取时间的方法（用于UI显示）
    public long getLastAtomicActivitiesFetchTime() {
        SharedPreferences atomicPrefs = getSharedPreferences("AtomicActivitiesPrefs", MODE_PRIVATE);
        return atomicPrefs.getLong("last_fetch_timestamp", 0);
    }

    // 5. 添加计算duration的独立方法（便于复用和测试）
    private int calculateDurationSinceLastFetch() {
        SharedPreferences atomicPrefs = getSharedPreferences("AtomicActivitiesPrefs", MODE_PRIVATE);
        long lastFetchTime = atomicPrefs.getLong("last_fetch_timestamp", 0);

        if (lastFetchTime == 0) {
            return 0; // 首次获取
        }

        long currentTime = System.currentTimeMillis();
        return (int) ((currentTime - lastFetchTime) / 1000);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.e(TAG, "========================================");
        Log.e(TAG, "DataService onCreate 开始执行");
        Log.e(TAG, "========================================");

        // Initialize extracted helpers
        fileRepository = FileRepository.getInstance(getApplicationContext());
        fileRepository.initializeFiles(); // Initialize file writers for FileRepository
        sharedPreferencesHelper = SharedPreferencesHelper.getInstance(getApplicationContext());
        uploadQueueManager = UploadQueueManager.getInstance(getApplicationContext());

        // Register network receiver for offline/online handling
        registerNetworkReceiver();

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        stepCounter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER); // 计步传感器

        // 列出所有可用的传感器
        List<Sensor> allSensors = sensorManager.getSensorList(Sensor.TYPE_ALL);
        Log.e(TAG, "========== 设备所有传感器列表 ==========");
        for (Sensor sensor : allSensors) {
            Log.e(TAG, "传感器: " + sensor.getName() + " (类型: " + sensor.getType() + ")");
        }
        Log.e(TAG, "=======================================");

        // 检查步数传感器是否可用
        if (stepCounter != null) {
            Log.e(TAG, "✅✅✅ 步数传感器可用: " + stepCounter.getName());
            Log.e(TAG, "传感器类型: " + stepCounter.getType());
            Log.e(TAG, "传感器供应商: " + stepCounter.getVendor());
            Log.e(TAG, "最大范围: " + stepCounter.getMaximumRange());
        } else {
            Log.e(TAG, "❌❌❌ 步数传感器不可用，设备不支持 TYPE_STEP_COUNTER");

            // 检查是否有 STEP_DETECTOR
            Sensor stepDetector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
            if (stepDetector != null) {
                Log.e(TAG, "⚠️ 设备有 STEP_DETECTOR 传感器，但没有 STEP_COUNTER");
            }
        }

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        bluetoothManager = (BluetoothManager) this.getSystemService(Context.BLUETOOTH_SERVICE);

        // 获取 SharedPreferences 中的 userId（通过 SharedPreferencesHelper）
        userId = sharedPreferencesHelper.getUserId();

        if (!TextUtils.isEmpty(userId)) {
            HttpApiClient.init(userId);
        }
        getUploadHttpClient();

        errorCounter = -1;

        startForegroundServiceWithPersistentNotification(this, this, 23);

        // 获取唤醒锁（使用PARTIAL_WAKE_LOCK保持CPU运行，即使在锁屏状态）
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "DataService::WakeLock");
        wakeLock.acquire();
        Log.i(TAG, "WakeLock acquired - Service will run in background and during screen off");

        // 读取 userId（再次确认）
        userId = sharedPreferencesHelper.getUserId();
        Log.i(TAG, "onCreate success");

        // 初始化百度地圖 SDK
        initBaiduMapSDK();
        // 初始化通知管理器和通知渠道
        initNotificationChannel();
        startInterventionCheck();
        initBluetooth();
        initAtomicActivitiesTimestamp();

        // 启动步数传感器定期刷新任务（每60秒重新注册一次）
        startStepSensorRefresh();

        // 启动传感器处理后台线程（解决主线程阻塞问题）
        Log.i(TAG, "Starting Sensor HandlerThread");
        sensorThread = new android.os.HandlerThread("SensorProcessingThread");
        sensorThread.start();
        // 用这个后台线程的 Looper 来创建一个 Handler
        sensorHandler = new android.os.Handler(sensorThread.getLooper());
        Log.i(TAG, "Sensor HandlerThread started successfully");
    }

    private void initBaiduMapSDK() {
        if (!isNetworkAvailable()) {
            Log.w(TAG, "網絡不可用，延遲初始化百度地圖 SDK");
            handler.postDelayed(this::initBaiduMapSDK, 5000);
            return;
        }

        try {
            // Set privacy agreement (required by Baidu SDK)
            LocationClient.setAgreePrivacy(true);

            // Initialize LocationClient
            mLocationClient = new LocationClient(getApplicationContext());

            // Configure location options
            LocationClientOption option = new LocationClientOption();
            option.setLocationMode(LocationClientOption.LocationMode.Hight_Accuracy);
            option.setCoorType("gcj02"); // GCJ-02 coordinate system
            option.setScanSpan(1000); // Location update interval: 1 second
            option.setIsNeedAddress(true); // Get address information
            option.setIsNeedLocationPoiList(true); // Get POI list
            option.setNeedNewVersionRgc(true); // New version reverse geocoding
            option.setLocationNotify(true); // Notify when location changes
            option.setIgnoreKillProcess(false); // Don't ignore kill process
            option.SetIgnoreCacheException(false);
            option.setEnableSimulateGps(false); // Disable simulated GPS

            mLocationClient.setLocOption(option);

            // Set up location listener
            mLocationClient.registerLocationListener(new BDAbstractLocationListener() {
                @Override
                public void onReceiveLocation(BDLocation location) {
                    if (location != null) {
                        int locType = location.getLocType();
                        double lat = location.getLatitude();
                        double lon = location.getLongitude();

                        // Only accept valid location types (61=GPS, 161=Network)
                        // and non-zero coordinates to avoid showing 0.000000
                        if ((locType == 61 || locType == 161) && !(lat == 0.0 && lon == 0.0)) {
                            gpsLat1 = lat;
                            gpsLon1 = lon;

                            // Get address
                            if (location.getAddrStr() != null) {
                                currentAddress = location.getAddrStr();
                            }

                            // Get POI
                            List<Poi> poiList = location.getPoiList();
                            if (poiList != null && !poiList.isEmpty()) {
                                currentPoi = poiList.get(0).getName();
                            } else {
                                currentPoi = "N/A";
                            }

                            Log.i(TAG, "Baidu Location Update - Lat: " + gpsLat1 +
                                    ", Lon: " + gpsLon1 +
                                    ", Address: " + currentAddress +
                                    ", POI: " + currentPoi +
                                    ", LocType: " + locType);
                        } else {
                            Log.w(TAG, "Invalid location received - Lat: " + lat +
                                    ", Lon: " + lon +
                                    ", LocType: " + locType +
                                    " (valid types: 61=GPS, 161=Network)");
                        }
                    }
                }
            });

            isSdkInitialized = true;
            Log.i(TAG, "百度地圖 SDK 初始化成功");

        } catch (Exception e) {
            Log.e(TAG, "百度地圖 SDK 初始化失敗: " + e.getMessage(), e);
            initRetryCount++;
            if (initRetryCount < MAX_RETRY_ATTEMPTS) {
                handler.postDelayed(this::initBaiduMapSDK, RETRY_DELAY_MS);
            } else {
                Log.e(TAG, "百度地圖 SDK 初始化失敗，使用默認位置數據");
                // Fallback to dummy location data
                gpsLat1 = 22.3193;
                gpsLon1 = 114.1694;
                currentAddress = "Dummy Address";
                currentPoi = "Dummy POI";
                isSdkInitialized = true;
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Notification notification = new NotificationCompat.Builder(this, "Foreground_Channel")
                .setSmallIcon(R.drawable.ic_launcher_background)
                .setContentTitle("MobiBox")
                .setContentText("Service is Running !")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build();

        startForeground(23, notification);

        if (!isMonitoring) {
            handler.post(this::startDataCollection);
        }

        return START_STICKY; // 明确指定服务行为
    }

    private void sendCustomNotification(Context context, String customTitle, String customContent) {
        // 构建跳转到 MainActivity 的 Intent
        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra("custom_notification_title", customTitle); // 传递自定义标题
        intent.putExtra("custom_notification_content", customContent); // 传递自定义内容
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        // 构建通知
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "SERVICE_CHANNEL")
                .setSmallIcon(R.drawable.ic_notification_error) // 替换为你的通知图标
                .setContentTitle(customTitle) // 使用自定义标题
                .setContentText(customContent) // 使用自定义内容
                .setStyle(new NotificationCompat.BigTextStyle().bigText(customContent)) // 支持长文本
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_SERVICE) // 设置为服务类别
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true) // 点击后自动清除通知
                .setFullScreenIntent(pendingIntent, true); // 设置全屏通知

        // 发送通知
        NotificationManager notificationManager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(2001, builder.build()); // 使用唯一的通知 ID
        }
    }

    private void initNotificationChannel() {
        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "干预通知",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("用于接收最新的干预提醒");
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        Log.i(TAG, "onBind success");
        return new Binder(this);
    }

    // 数据采集
    public void startDataCollection() {
        if (isMonitoring)
            return;
        Log.i(TAG, "startDataCollection");

        // 檢查權限
        if (!checkLocationPermission()) {
            Log.e(TAG, "位置權限未授予，無法啟動定位");
            Toast.makeText(this, "Please grant location permission to enable positioning", Toast.LENGTH_LONG).show();
            return;
        }
        // 檢查網絡狀態
        if (!isNetworkAvailable()) {
            Log.e(TAG, "網絡不可用，無法啟動定位");
            Toast.makeText(this, "Please check network connection and retry", Toast.LENGTH_LONG).show();
            return;
        }

        // Start Baidu LocationClient
        if (mLocationClient != null) {
            mLocationClient.start();
            Log.i(TAG, "LocationClient started");
        } else {
            Log.e(TAG, "LocationClient is null, reinitializing");
            initBaiduMapSDK();
            if (mLocationClient != null) {
                mLocationClient.start();
            }
        }

        // 文件创建 - 检查存储权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 需要 "All files access" 权限
            if (!Environment.isExternalStorageManager()) {
                Log.e("FileWriterError", "Missing MANAGE_EXTERNAL_STORAGE permission");
                Toast.makeText(this, "Please grant 'All files access' permission in Settings", Toast.LENGTH_LONG).show();
                // 引导用户到设置页面
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            .setData(Uri.parse("package:" + getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                } catch (Exception e) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                }
                return;
            }
        }

        try {
            File dir = new File(Environment.getExternalStorageDirectory() + "/0.Mobibox");
            if (!dir.exists()) {
                if (!dir.mkdirs()) {
                    Log.e("FileWriterError", "Failed to create directory: " + dir.getAbsolutePath());
                    Toast.makeText(this, "Failed to create directory", Toast.LENGTH_LONG).show();
                    return; // 如果创建目录失败，退出方法以避免后续出错
                }
            }

            txtFile = new File(dir, "app_names.txt");
            sensorFile = new File(dir, "sensor.csv");
            imuFile = new File(dir, "IMU.csv");

            // 生成CSV文件并写入表头
            if (!sensorFile.exists()) {
                sensorWriter = new FileWriter(sensorFile, true);
                Log.i(TAG, "创建sensor.csv文件: " + sensorFile.getAbsolutePath());
            } else {
                sensorWriter = new FileWriter(sensorFile, true);
                Log.i(TAG, "打开已存在的sensor.csv文件: " + sensorFile.getAbsolutePath());
            }

            // 生成imu文件
            if (!imuFile.exists()) {
                imuWriter = new FileWriter(imuFile, true);
                Log.i(TAG, "创建IMU.csv文件: " + imuFile.getAbsolutePath());
            } else {
                imuWriter = new FileWriter(imuFile, true);
                Log.i(TAG, "打开已存在的IMU.csv文件: " + imuFile.getAbsolutePath());
            }

            txtWriter = new FileWriter(txtFile, true);
        } catch (IOException e) {
            Log.e("RecognitionError", "Error in startRecognition", e);
        }

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);

        // MODIFIED: 使用sensorHandler让onSensorChanged在后台线程运行，避免主线程阻塞
        // Use configured IMU sampling rate (see Constants.SENSOR_DELAY_IMU_US)
        // 第四个参数改为0（等价于SENSOR_DELAY_FASTEST，但更清晰），第五个参数传入sensorHandler
        if (sensorHandler != null) {
            sensorManager.registerListener(this, accelerometer, Constants.SENSOR_DELAY_IMU_US, 0, sensorHandler);
            sensorManager.registerListener(this, gyroscope, Constants.SENSOR_DELAY_IMU_US, 0, sensorHandler);
            sensorManager.registerListener(this, magnetometer, Constants.SENSOR_DELAY_IMU_US, 0, sensorHandler);
            Log.i(TAG, "传感器已注册到后台线程，将实现稳定的IMU采集");

            // 启动 IMU 写入 Ticker（解耦数据采集和写入，匹配后端HAR模型采样假设）
            // 注意：Ticker会在isMonitoring设置后继续运行
            Log.i(TAG, "准备启动IMU Ticker，isMonitoring=" + isMonitoring + ", sensorHandler=" + (sensorHandler != null));
        } else {
            // 如果sensorHandler未初始化，回退到主线程（不应该发生）
            Log.w(TAG, "sensorHandler未初始化，回退到主线程注册");
            sensorManager.registerListener(this, accelerometer, Constants.SENSOR_DELAY_IMU_US, SensorManager.SENSOR_DELAY_FASTEST);
            sensorManager.registerListener(this, gyroscope, Constants.SENSOR_DELAY_IMU_US, SensorManager.SENSOR_DELAY_FASTEST);
            sensorManager.registerListener(this, magnetometer, Constants.SENSOR_DELAY_IMU_US, SensorManager.SENSOR_DELAY_FASTEST);
        }

        // 注册计步传感器，并检查是否成功
        Log.e(TAG, "========================================");
        Log.e(TAG, "开始注册步数传感器监听器");
        Log.e(TAG, "========================================");
        if (stepCounter != null) {
            boolean registered = sensorManager.registerListener(this, stepCounter, SensorManager.SENSOR_DELAY_NORMAL);
            if (registered) {
                Log.e(TAG, "✅✅✅ 步数传感器监听器注册成功");
            } else {
                Log.e(TAG, "❌❌❌ 步数传感器监听器注册失败");
            }
        } else {
            Log.e(TAG, "⚠️⚠️⚠️ 步数传感器为null，无法注册监听器");
        }

        // 初始化网络流量（带UNSUPPORTED检查）
        long initRxBytes = TrafficStats.getTotalRxBytes();
        long initTxBytes = TrafficStats.getTotalTxBytes();
        if (initRxBytes != TrafficStats.UNSUPPORTED && initTxBytes != TrafficStats.UNSUPPORTED
                && initRxBytes >= 0 && initTxBytes >= 0) {
            startRxBytes = initRxBytes;
            startTxBytes = initTxBytes;
            Log.i(TAG, "网络流量统计初始化成功: startRx=" + startRxBytes + ", startTx=" + startTxBytes);
        } else {
            startRxBytes = 0;
            startTxBytes = 0;
            Log.w(TAG, "TrafficStats不支持或返回无效值，网络流量统计将返回-1");
        }

        startTime = System.currentTimeMillis();
        initBluetooth();
        startPeriodicBluetoothScan();
        // 初始化线程
        handler.post(runnable);
        // 启动传感器监控任务（定期检查传感器是否还在工作）
        startSensorMonitoring();
        isMonitoring = true;
        Log.d("DataCollection", "Scheduled writeData task.");

        // MODIFIED: 在isMonitoring设置为true后启动IMU Ticker，确保Ticker能持续运行
        if (sensorHandler != null) {
            sensorHandler.post(imuWriterRunnable); // 立即开始 Ticker 循环
            Log.i(TAG, "IMU 写入 Ticker 已启动（isMonitoring已设置为true）");
        }

        // 启动IMU上传任务（每5秒执行一次）
        handler.post(imuUploadRunnable);
        Log.i(TAG, "IMU 上传任务已启动");
    }

    synchronized public void stopDataCollection() {
        if (!isMonitoring)
            return;
        Log.i(TAG, "stopDataCollection");

        sensorManager.unregisterListener(this);
        locationManager.removeUpdates(this);

        // Stop Baidu LocationClient
        if (mLocationClient != null && mLocationClient.isStarted()) {
            mLocationClient.stop();
        }

        stepcount_sensor = 0;
        stepCount = 0;
        stepflag = true;

        handler.removeCallbacks(runnable);
        handler.removeCallbacks(imuUploadRunnable);
        bluetoothHandler.removeCallbacksAndMessages(null);
        sensorCheckHandler.removeCallbacksAndMessages(null);

        // 停止 IMU 写入 Ticker
        if (sensorHandler != null) {
            sensorHandler.removeCallbacks(imuWriterRunnable);
            Log.i(TAG, "IMU 写入 Ticker 已停止");
        }

        stopBluetoothScan();
        try {
            // 在调用 close() 之前，先检查 FileWriter 是否为 null
            if (imuWriter != null) {
                imuWriter.close();
            }
            if (sensorWriter != null) {
                sensorWriter.close();
            }
            if (txtWriter != null) {
                txtWriter.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("CloseError", "FileWriter cannot be closed.");
        }
        isMonitoring = false;
    }

    // 新增網絡檢查方法
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.isConnected();
    }

    /**
     * Register network connectivity receiver for offline/online handling
     */
    private void registerNetworkReceiver() {
        if (networkReceiver == null) {
            networkReceiver = new NetworkReceiver();
        }

        // Set up listener for network state changes
        NetworkReceiver.setListener(new NetworkReceiver.NetworkListener() {
            @Override
            public void onNetworkAvailable() {
                Log.i(TAG, "🌐 Network recovered - processing pending uploads");
                processPendingUploads();
            }

            @Override
            public void onNetworkLost() {
                Log.w(TAG, "🌐 Network lost - uploads will be queued");
            }
        });

        // Register receiver for connectivity changes
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        registerReceiver(networkReceiver, filter);
        Log.i(TAG, "Network receiver registered");

        // Process any pending uploads from previous sessions if network is available
        if (NetworkReceiver.isNetworkConnected(getApplicationContext())) {
            Log.i(TAG, "Network available on service start - processing pending uploads");
            // Delay slightly to allow service to fully initialize
            handler.postDelayed(this::processPendingUploads, 2000);
        } else {
            Log.w(TAG, "Network not available on service start - uploads will be queued");
        }
    }

    /**
     * Unregister network connectivity receiver
     */
    private void unregisterNetworkReceiver() {
        if (networkReceiver != null) {
            try {
                unregisterReceiver(networkReceiver);
                Log.i(TAG, "Network receiver unregistered");
            } catch (Exception e) {
                Log.e(TAG, "Failed to unregister network receiver: " + e.getMessage());
            }
        }
        NetworkReceiver.clearListener();
    }

    /**
     * Process pending uploads from the queue when network becomes available.
     */
    private void processPendingUploads() {
        if (!pendingUploadInProgress.compareAndSet(false, true)) {
            Log.d(TAG, "Pending upload processing already running");
            return;
        }
        if (uploadQueueManager == null || uploadQueueManager.isEmpty()) {
            Log.d(TAG, "No pending uploads to process");
            pendingUploadInProgress.set(false);
            return;
        }

        Log.i(TAG, "Processing " + uploadQueueManager.getQueueSize() + " pending uploads...");

        new Thread(() -> {
            try {
            List<UploadQueueManager.PendingUpload> pendingUploads = uploadQueueManager.getPendingUploads();
            Log.d(TAG, "Found " + pendingUploads.size() + " pending uploads to process");

            for (UploadQueueManager.PendingUpload upload : pendingUploads) {
                // Skip if no network
                if (!NetworkReceiver.isNetworkConnected(getApplicationContext())) {
                    Log.w(TAG, "Network lost during pending upload processing, stopping");
                    break;
                }

                String content = uploadQueueManager.readUploadContent(upload);
                if (content == null || content.isEmpty()) {
                    Log.w(TAG, "Empty content for pending upload: " + upload.id + ", removing");
                    uploadQueueManager.removeFromQueue(upload.id);
                    continue;
                }

                Log.d(TAG, "Processing pending upload: " + upload.id + " (type: " + upload.type + ")");

                // Determine upload URL based on type
                String uploadUrl;
                if (upload.type == UploadQueueManager.UploadType.IMU ||
                    upload.type == UploadQueueManager.UploadType.IMU_BACKUP) {
                    uploadUrl = Constants.getImuUploadUrl();
                } else {
                    uploadUrl = Constants.getSensorUploadUrl();
                }

                // Try to upload
                final String uploadId = upload.id;
                uploadCSV(userId, content, uploadUrl, new UploadCallback() {
                    @Override
                    public void onSuccess() {
                        Log.i(TAG, "✅ Pending upload succeeded: " + uploadId);
                        uploadQueueManager.removeFromQueue(uploadId);
                    }

                    @Override
                    public void onFailure(String error) {
                        Log.e(TAG, "❌ Pending upload failed: " + uploadId + ", error: " + error);
                        uploadQueueManager.incrementRetry(uploadId, 5); // Max 5 retries
                    }
                });
            }
            } finally {
                pendingUploadInProgress.set(false);
            }
        }).start();
    }

    @Override
    synchronized public void onSensorChanged(SensorEvent event) {
        // MODIFIED: 使用Ticker方案 - onSensorChanged只负责更新数据数组，不负责写入
        // 写入由独立的Ticker负责（采样率配置在Constants中），解耦数据采集和写入
        // 即使磁力计更新慢，也能保持稳定写入频率

        // 1. IMU 数据更新（只更新数组，不写入文件）
        switch (event.sensor.getType()) {
            case Sensor.TYPE_ACCELEROMETER:
                System.arraycopy(event.values, 0, accelData, 0, 3);
                // 调试日志：第一次收到加速度数据时记录
                if (accelData[0] != 0 || accelData[1] != 0 || accelData[2] != 0) {
                    Log.d(TAG, "加速度传感器数据更新: [" + accelData[0] + ", " + accelData[1] + ", " + accelData[2] + "]");
                }
                break;
            case Sensor.TYPE_GYROSCOPE:
                System.arraycopy(event.values, 0, gyroData, 0, 3);
                // 调试日志：第一次收到陀螺仪数据时记录
                if (gyroData[0] != 0 || gyroData[1] != 0 || gyroData[2] != 0) {
                    Log.d(TAG, "陀螺仪传感器数据更新: [" + gyroData[0] + ", " + gyroData[1] + ", " + gyroData[2] + "]");
                }
                break;
            case Sensor.TYPE_MAGNETIC_FIELD:
                System.arraycopy(event.values, 0, magData, 0, 3);
                // 调试日志：第一次收到磁力计数据时记录
                if (magData[0] != 0 || magData[1] != 0 || magData[2] != 0) {
                    Log.d(TAG, "磁力计传感器数据更新: [" + magData[0] + ", " + magData[1] + ", " + magData[2] + "]");
                }
                break;
        }

        // 步数记录
        if (event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            endSteps = (int) event.values[0];
            lastSensorUpdateTime = System.currentTimeMillis(); // 记录最后更新时间
            if (stepflag == true) {
                stepflag = false;
                startSteps = endSteps; // 初次记步进行统一
                Log.e(TAG, "📊📊📊 步数传感器初始化: startSteps = " + startSteps);
            }
            Log.e(TAG, "👣👣👣 步数传感器更新: endSteps = " + endSteps + ", 增量 = " + (endSteps - startSteps));
        }

        // 每1s记录一次屏幕开启状态+更新IMU信息
        long currentScreenTime = System.currentTimeMillis();
        if (currentScreenTime - screenOnTimeCounter >= 1000) {
            if (powerManager.isInteractive()) {
                screenOnCounter = 1;
            } else
                screenOnCounter = 0;
        }
    }

    synchronized public void writeData() {
        Log.d(TAG, "writeData");
        try {
            flag2 = true; // 防止在同一周期内多次写入

            // 确保FileWriter已初始化
            if (sensorWriter == null) {
                sensorFile = new File(Environment.getExternalStorageDirectory() + "/0.Mobibox/sensor.csv");
                sensorWriter = new FileWriter(sensorFile, true);
            }
            if (imuWriter == null) {
                imuFile = new File(Environment.getExternalStorageDirectory() + "/0.Mobibox/IMU.csv");
                imuWriter = new FileWriter(imuFile, true);
            }

            // 同步获取前台应用名称（不使用新线程，避免竞态条件）
            appName = getForegroundAppName();
            if (appName == null) {
                appName = "N/A"; // 如果获取失败，设置为N/A
            }
            Log.i(TAG, "getForegroundAppName: " + appName);

            // 记录时间段内的网络流量（带UNSUPPORTED检查）
            try {
                long currentRxBytes = TrafficStats.getTotalRxBytes();
                long currentTxBytes = TrafficStats.getTotalTxBytes();

                // 检查TrafficStats是否返回UNSUPPORTED (-1)
                if (currentRxBytes != TrafficStats.UNSUPPORTED && currentTxBytes != TrafficStats.UNSUPPORTED
                        && currentRxBytes >= 0 && currentTxBytes >= 0) {
                    // 检查startRxBytes和startTxBytes是否有效
                    if (startRxBytes != TrafficStats.UNSUPPORTED && startTxBytes != TrafficStats.UNSUPPORTED
                            && startRxBytes >= 0 && startTxBytes >= 0) {
                        endRxBytes = currentRxBytes;
                        endTxBytes = currentTxBytes;
                        long rxBytes = endRxBytes - startRxBytes; // 接收流量增量
                        long txBytes = endTxBytes - startTxBytes; // 发送流量增量

                        // 处理计数器重置的情况（如果end < start，说明计数器被重置了）
                        if (rxBytes < 0) rxBytes = endRxBytes; // 设备重启或计数器重置
                        if (txBytes < 0) txBytes = endTxBytes;

                        long totalBytes = rxBytes + txBytes; // 总流量增量
                        rx_traffic = (int) (rxBytes / 1024); // 接收 KB
                        tx_traffic = (int) (txBytes / 1024); // 发送 KB
                        networkTrafficInMB = totalBytes / (1024f * 1024f); // 将字节转换为MB
                        networkTrafficInMB = Math.round(networkTrafficInMB * 100.00) / 100.00;
                        Log.d(TAG, "网络流量统计成功: rx=" + rx_traffic + "KB, tx=" + tx_traffic + "KB");
                    } else {
                        // startRxBytes/startTxBytes无效，重新初始化
                        Log.w(TAG, "startRxBytes或startTxBytes无效，重新初始化: startRx=" + startRxBytes + ", startTx=" + startTxBytes);
                        startRxBytes = currentRxBytes;
                        startTxBytes = currentTxBytes;
                        rx_traffic = -1;
                        tx_traffic = -1;
                        networkTrafficInMB = -1;
                    }
                } else {
                    // TrafficStats不支持，设置默认值
                    Log.w(TAG, "TrafficStats返回UNSUPPORTED，无法获取网络流量");
                    rx_traffic = -1;
                    tx_traffic = -1;
                    networkTrafficInMB = -1;
                    // 尝试重新初始化，以便下次可能成功
                    startRxBytes = TrafficStats.getTotalRxBytes();
                    startTxBytes = TrafficStats.getTotalTxBytes();
                }
            } catch (Exception e) {
                Log.e(TAG, "获取网络流量时发生异常: " + e.getMessage(), e);
                rx_traffic = -1;
                tx_traffic = -1;
                networkTrafficInMB = -1;
            }

            // 计算步数增量
            stepcount_sensor = endSteps - startSteps;
            Log.e(TAG,
                    "📝📝📝 写入步数数据: stepcount_sensor = " + stepcount_sensor + " (endSteps=" + endSteps + ", startSteps="
                            + startSteps + ")");
            startSteps = endSteps; // 更新起始步数

            // 获取WiFi连接状态（带异常处理）
            try {
                if (wifiManager != null && wifiManager.getConnectionInfo() != null) {
                    int networkId = wifiManager.getConnectionInfo().getNetworkId();
                    wifiStatus = networkId != -1 ? 1 : 0;
                    String ssid = wifiManager.getConnectionInfo().getSSID();
                    if (ssid != null && !ssid.equals("<unknown ssid>")) {
                        connect_wifi_name = ssid.replace("\"", "");
                    } else {
                        connect_wifi_name = "N/A";
                    }
                } else {
                    Log.w(TAG, "WifiManager或getConnectionInfo返回null");
                    wifiStatus = 0;
                    connect_wifi_name = "N/A";
                }
            } catch (SecurityException se) {
                Log.e(TAG, "获取WiFi信息时权限被拒绝: " + se.getMessage());
                wifiStatus = 0;
                connect_wifi_name = "N/A";
            } catch (Exception e) {
                Log.e(TAG, "获取WiFi信息时发生异常: " + e.getMessage(), e);
                wifiStatus = 0;
                connect_wifi_name = "N/A";
            }

            // 获取音量百分比
            int currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
            int maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            volumePercentage = ((float) currentVolume / maxVolume) * 100;

            // 获取屏幕亮屏比例
            screenOnRatio = powerManager.isInteractive() ? 1f : 0f;
            screenOnCounter = screenOnRatio;

            // 處理經緯度（直接使用百度地圖更新的 gpsLat1 和 gpsLon1）
            DecimalFormat df = new DecimalFormat("#.######");
            df.setRoundingMode(RoundingMode.HALF_UP);
            String latStr, lonStr;
            if (!Double.isNaN(gpsLat1) && !Double.isNaN(gpsLon1)) {
                String formattedLat = df.format(gpsLat1);
                String formattedLon = df.format(gpsLon1);
                gpsLat1 = Double.parseDouble(formattedLat);
                gpsLon1 = Double.parseDouble(formattedLon);
                latStr = String.valueOf(gpsLat1);
                lonStr = String.valueOf(gpsLon1);
            } else {
                Log.w(TAG, "GPS 位置無效，跳過格式化");
                latStr = "N/A";
                lonStr = "N/A";
            }

            // 电池电量比例
            battery_level = getBatteryLevel(this);
            // 蓝牙设备
            bluetoothDevices = getPairedDevicesNames(); // 这个起码能get到所有配对过的设备

            if (imuWriter == null || sensorWriter == null || txtWriter == null) {
                Log.e("FileWriterError", "FileWriter is null. Cannot write data.");
            }

            // 记录app名称（亮屏时记录实际应用，息屏时不收集Appname数据）
            String appNameForCsv = ""; // 用于写入CSV的Appname字段，默认为空字符串
            if (screenOnCounter == 1) {
                // 亮屏时，只有当appName不为空且不是N/A时才收集Appname数据
                if (appName != null && !appName.isEmpty() && !"N/A".equals(appName)) {
                    currentAppName = appName;
                    appNameForCsv = appName; // 收集Appname数据
                } else {
                    currentAppName = "N/A";
                    appNameForCsv = ""; // N/A时不收集Appname数据
                    Log.i(TAG, "Appname为N/A，不收集Appname维度数据");
                }
            } else {
                currentAppName = "ScreenOff"; // 息屏时明确标记为ScreenOff
                appNameForCsv = ""; // ScreenOff时不收集Appname数据
                Log.i(TAG, "锁屏状态(ScreenOff)，不收集Appname维度数据");
                // 息屏时重置前台时间记录，避免影响下次亮屏的判断
                resetForegroundTimeTracking();
            }

            // IMU数据现在是实时写入的，不再需要批量写入sensorDataList
            // 传感器可用性检查
            if (!checkSensorAvailability()) {
                restartSensors();
            }

            // 注意：由于IMU数据现在是实时写入的，无法通过sensorDataList.size()来校验数据量
            // 如果需要校验，可以通过检查文件大小或读取文件行数来实现

            // 获取蓝牙扫描数据（包含详细信息）
            int nearbyBluetoothCount = getNearbyBluetoothDevicesCount();
            String topBluetoothDevices = getTopThreeBluetoothDevices(); // 格式：名称|RSSI|地址;名称|RSSI|地址;...

            // 写入sensor.csv，包含详细的蓝牙扫描数据（使用中国时区 ISO 8601 时间）
            // 注意：当Appname为ScreenOff或N/A时，appNameForCsv为空字符串，不收集该维度数据
            String chinaTimestamp = getChinaISO8601Timestamp(System.currentTimeMillis()); // 获取当前中国时区时间（ISO 8601格式）
            sensorWriter.write(chinaTimestamp + ","
                    + volumePercentage + "," + screenOnCounter + "," +
                    wifiStatus + "," + escapeCsvField(connect_wifi_name) + "," + networkTrafficInMB + "," + rx_traffic
                    + "," +
                    tx_traffic + "," + stepcount_sensor + "," + latStr + "," + lonStr + "," + battery_level + "," +
                    escapeCsvField(appNameForCsv) + "," + escapeCsvField(bluetoothDevices) + ","
                    + escapeCsvField(currentAddress) + "," + escapeCsvField(currentPoi) + "," +
                    nearbyBluetoothCount + "," + escapeCsvField(topBluetoothDevices) + "\n");
            // 字段说明：
            // - nearbyBluetoothCount: 扫描到的设备总数
            // - topBluetoothDevices: 前3个信号最强的设备（名称|RSSI值|MAC地址）

            // 强制刷新缓冲区
            sensorWriter.flush();
            Log.d(TAG, "sensor.csv数据已写入，文件路径: " + sensorFile.getAbsolutePath());
            // IMU数据现在是实时写入的，但需要确保缓冲区已刷新
            if (imuWriter != null) {
                try {
                    imuWriter.flush();
                } catch (IOException e) {
                    Log.e(TAG, "刷新IMU写入缓冲区失败", e);
                }
            }

            // 重置计数器等
            screenOnCounter = 0;
            screenCounter = 0;
            stepCount = 0;
            // 只有在endRxBytes和endTxBytes有效时才更新start值
            if (endRxBytes >= 0 && endTxBytes >= 0) {
                startRxBytes = endRxBytes;
                startTxBytes = endTxBytes;
            }
            // IMU数据现在是实时写入的，不再需要sensorDataList
            // synchronized (sensorDataList) {
            // sensorDataList.clear(); // 清空列表，为下次数据收集做准备
            // }
            flag2 = false; // 允许下次周期写入
        } catch (Exception e) {
            e.printStackTrace();
            Log.e("FileWriterError", "Error writing data: " + e.getMessage(), e);
            // 处理异常后重新初始化
            initializeFileWriter("sensor.csv");
            initializeFileWriter("IMU.csv");
            // 重新初始化网络流量统计（以防异常导致数据错误）
            startRxBytes = TrafficStats.getTotalRxBytes();
            startTxBytes = TrafficStats.getTotalTxBytes();
        } finally {
            flag2 = false; // 允许下次周期写入
        }
    }

    // Baidu Map SDK disabled - handleLocationError no longer used
    // private void handleLocationError(int locType) { ... }

    private void restartLocationClient() {
        if (mLocationClient != null && mLocationClient.isStarted()) {
            mLocationClient.stop();
        }
        handler.postDelayed(() -> {
            if (mLocationClient != null) {
                mLocationClient.start();
                Log.i(TAG, "定位客戶端已重新啟動");
            }
        }, 2000);
    }

    // Baidu Map SDK disabled - retryInitBaiduMapSDK no longer needed
    // private void retryInitBaiduMapSDK() { ... }

    private boolean checkLocationPermission() {
        return ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                && ContextCompat.checkSelfPermission(this,
                        Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onTimeout(int startId, int fgsType) {
        Log.w(TAG, "Foreground service timeout, stopping cleanly. startId=" + startId + ", type=" + fgsType);
        try {
            uploadActiveCsvSegment(false);
            uploadActiveCsvSegment(true);
            stopDataCollection();
        } catch (Exception e) {
            Log.e(TAG, "Error while handling foreground-service timeout", e);
        } finally {
            stopSelf(startId);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Unregister network receiver
        unregisterNetworkReceiver();
        if (handler != null && runnable != null) {
            handler.removeCallbacks(runnable);
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        // Cleanup Baidu LocationClient
        if (mLocationClient != null) {
            mLocationClient.stop();
            mLocationClient = null;
        }
        this.stopDataCollection();
        interventionHandler.removeCallbacksAndMessages(null);
        bluetoothHandler.removeCallbacksAndMessages(null);
        stopBluetoothScan();

        // 停止步数传感器刷新任务
        if (stepSensorRefreshHandler != null) {
            stepSensorRefreshHandler.removeCallbacksAndMessages(null);
            Log.d(TAG, "步数传感器刷新任务已停止");
        }

        // 停止传感器处理后台线程
        if (sensorThread != null) {
            sensorThread.quitSafely();
            try {
                sensorThread.join(1000); // 等待最多1秒
            } catch (InterruptedException e) {
                Log.e(TAG, "等待sensorThread停止时被中断", e);
            }
            Log.i(TAG, "Sensor HandlerThread stopped.");
        }
    }

    public void startForegroundServiceWithPersistentNotification(Context context, Service service, int notificationId) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "Foreground_Channel",
                    "Data Collection Service",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Service running in foreground");
            channel.setImportance(NotificationManager.IMPORTANCE_HIGH); // 设置为高重要性
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC); // 锁屏可见
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        Intent notificationIntent = new Intent(context, MainActivity.class);
        notificationIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                0,
                notificationIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification notification = new NotificationCompat.Builder(context, "Foreground_Channel")
                .setSmallIcon(R.drawable.ic_launcher_cat) // 设置小图标
                .setContentTitle("MobiBox") // 设置标题
                .setContentText("❗⚠ The service is Running ❗") // 设置内容
                .setPriority(NotificationCompat.PRIORITY_MAX) // 设置优先级
                .setOngoing(true) // 设置为常驻通知（无法被划掉）
                .setContentIntent(pendingIntent) // 设置点击跳转
                .setColorized(true)
                .setAutoCancel(false) // 禁止自动取消
                .build();

        service.startForeground(notificationId, notification);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No implementation required
    }

    @Override
    synchronized public void onLocationChanged(@NonNull Location location) {
        // No implementation required
    }

    @Override
    public void onProviderEnabled(@NonNull String provider) {
    }

    @Override
    public void onProviderDisabled(@NonNull String provider) {
    }

    // 获取电量信息
    public int getBatteryLevel(Context context) {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, ifilter);

        if (batteryStatus != null) {
            int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

            float batteryPct = (level / (float) scale) * 100;
            return Math.round(batteryPct);
        }
        return -1; // 如果无法获取电池状态，则返回-1或其他错误码
    }

    // 想要获取最近使用应用的名称和包名（格式：AppName(packageName)）
    public String getForegroundAppName() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                UsageStatsManager usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
                if (usageStatsManager == null) {
                    Log.w(TAG, "UsageStatsManager is null");
                    return "N/A";
                }

                long time = System.currentTimeMillis();
                // 扩大时间窗口到30秒，提高获取成功率
                List<UsageStats> appList = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY,
                        time - 30 * 1000, time);

                if (appList != null && !appList.isEmpty()) {
                    // 使用前台时间增量排序，而不是lastTimeUsed
                    long currentQueryTime = time;
                    String maxUsagePackage = null;
                    long maxForegroundTime = 0;

                    for (UsageStats usageStats : appList) {
                        String pkgName = usageStats.getPackageName();
                        // 只过滤掉系统桌面
                        if (!isSystemLauncher(pkgName)) {
                            long currentTotalTime = usageStats.getTotalTimeInForeground();
                            long lastTotalTime = lastForegroundTimeMap.getOrDefault(pkgName, 0L);

                            // 计算这段时间的增量使用时间
                            long incrementalTime = currentTotalTime - lastTotalTime;

                            // 找到增量时间最长的应用
                            if (incrementalTime > maxForegroundTime) {
                                maxForegroundTime = incrementalTime;
                                maxUsagePackage = pkgName;
                            }

                            // 更新记录
                            lastForegroundTimeMap.put(pkgName, currentTotalTime);
                        }
                    }

                    // 更新查询时间
                    lastQueryTime = currentQueryTime;

                    if (maxUsagePackage != null && maxForegroundTime > 0) {
                        String appName = getAppNameFromPackage(maxUsagePackage);
                        if (appName != null) {
                            Log.d(TAG, "前台时间最长的应用: " + appName + ", 增量时间: " + (maxForegroundTime / 1000) + "秒");
                            // 返回格式：AppName(packageName)
                            return appName + "(" + maxUsagePackage + ")";
                        }
                        // 如果无法获取应用名称，只返回包名
                        return "Unknown(" + maxUsagePackage + ")";
                    } else {
                        // 降级策略：如果增量时间都为0，使用lastTimeUsed（最近使用时间）
                        Log.w(TAG, "未找到有效的前台应用（增量时间都为0），降级使用lastTimeUsed");
                        UsageStats recentApp = null;
                        long recentTime = 0;
                        for (UsageStats usageStats : appList) {
                            if (!isSystemLauncher(usageStats.getPackageName())) {
                                if (usageStats.getLastTimeUsed() > recentTime) {
                                    recentTime = usageStats.getLastTimeUsed();
                                    recentApp = usageStats;
                                }
                            }
                        }
                        if (recentApp != null) {
                            String appName = getAppNameFromPackage(recentApp.getPackageName());
                            if (appName != null) {
                                Log.d(TAG, "降级方案: 最近使用的应用: " + appName);
                                return appName + "(" + recentApp.getPackageName() + ")";
                            }
                            return "Unknown(" + recentApp.getPackageName() + ")";
                        }
                    }
                } else {
                    Log.w(TAG, "appList is null or empty");
                }
            } catch (Exception e) {
                Log.e(TAG, "获取前台应用失败: " + e.getMessage());
            }
        }
        return "N/A"; // 默认返回N/A而不是null
    }

    // 根据包名获取应用名称
    public String getAppNameFromPackage(String packageName) {
        PackageManager packageManager = getPackageManager();
        try {
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo(packageName, 0);
            return (String) packageManager.getApplicationLabel(applicationInfo);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * 判断是否是系统桌面（Launcher）
     */
    private boolean isSystemLauncher(String packageName) {
        if (packageName == null) {
            return false;
        }

        // 常见的系统桌面包名
        String[] launcherPackages = {
                "com.miui.home", // 小米桌面
                "com.huawei.android.launcher", // 华为桌面
                "com.android.launcher", // 原生桌面
                "com.android.launcher3", // 原生桌面3
                "com.oppo.launcher", // OPPO桌面
                "com.bbk.launcher2", // vivo桌面
                "com.sec.android.app.launcher", // 三星桌面
                "com.google.android.apps.nexuslauncher", // Google桌面
                "com.teslacoilsw.launcher", // Nova Launcher
                "com.actionlauncher.playstore" // Action Launcher
        };

        for (String launcher : launcherPackages) {
            if (packageName.equals(launcher) || packageName.contains("launcher")) {
                return true;
            }
        }

        return false;
    }

    /**
     * 重置前台时间跟踪（锁屏时调用）
     */
    private void resetForegroundTimeTracking() {
        lastForegroundTimeMap.clear();
        lastQueryTime = 0;
        Log.d(TAG, "重置前台时间跟踪（锁屏）");
    }

    // CSV字段转义：处理包含逗号、双引号、换行符的字段
    private String escapeCsvField(String field) {
        if (field == null) {
            return "";
        }
        // 如果包含逗号、双引号或换行符，需要用双引号包裹，并将内部双引号转义为两个双引号
        if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }

    // 获取配对的蓝牙设备名称
    public String getPairedDevicesNames() {
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Log.w(TAG, "蓝牙适配器不可用");
            return "N/A";
        }

        // Android 12+ 需要 BLUETOOTH_CONNECT 权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "缺少 BLUETOOTH_CONNECT 权限，无法获取配对设备");
                return "N/A";
            }
        }

        try {
            Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
            if (pairedDevices == null || pairedDevices.isEmpty()) {
                return "N/A";
            }

            List<String> deviceNames = new ArrayList<>();
            for (BluetoothDevice device : pairedDevices) {
                if (device != null) {
                    String name = device.getName();
                    if (!TextUtils.isEmpty(name)) {
                        deviceNames.add(name);
                    }
                }
            }

            return deviceNames.isEmpty() ? "N/A" : String.join(";", deviceNames);
        } catch (SecurityException e) {
            Log.e(TAG, "获取配对设备时权限异常: " + e.getMessage());
            return "N/A";
        } catch (Exception e) {
            Log.e(TAG, "获取配对设备时发生异常: " + e.getMessage());
            return "N/A";
        }
    }

    /**
     * Split CSV content into chunks of specified row count
     * @param csvContent Full CSV content
     * @param chunkSize Number of rows per chunk
     * @return List of CSV content chunks
     */
    private List<String> splitCSVIntoChunks(String csvContent, int chunkSize) {
        List<String> chunks = new ArrayList<>();
        String[] lines = csvContent.split("\n");

        StringBuilder currentChunk = new StringBuilder();
        int lineCount = 0;

        for (String line : lines) {
            if (line.trim().isEmpty()) continue;

            currentChunk.append(line).append("\n");
            lineCount++;

            if (lineCount >= chunkSize) {
                chunks.add(currentChunk.toString());
                currentChunk = new StringBuilder();
                lineCount = 0;
            }
        }

        // Add remaining lines as last chunk
        if (lineCount > 0) {
            chunks.add(currentChunk.toString());
        }

        return chunks;
    }

    private static class CsvUploadResult {
        final boolean success;
        final String error;

        CsvUploadResult(boolean success, String error) {
            this.success = success;
            this.error = error;
        }
    }

    private void uploadActiveCsvSegment(boolean imu) {
        AtomicBoolean gate = imu ? imuUploadInProgress : sensorUploadInProgress;
        if (!gate.compareAndSet(false, true)) {
            Log.d(TAG, (imu ? "IMU" : "Sensor") + " upload already running, skip this tick");
            return;
        }

        new Thread(() -> {
            File segment = null;
            try {
                segment = sealActiveCsvFile(imu);
                if (segment == null) {
                    return;
                }

                String content = readTextFile(segment);
                if (TextUtils.isEmpty(content)) {
                    Log.w(TAG, "Sealed upload segment is empty: " + segment.getAbsolutePath());
                    deleteFileQuietly(segment);
                    return;
                }

                boolean success;
                if (imu) {
                    success = uploadCsvChunksBlocking(content, Constants.getImuUploadUrl());
                } else {
                    CsvUploadResult result = uploadCSVBlocking(userId, content, Constants.getSensorUploadUrl());
                    success = result.success;
                    if (!success) {
                        Log.e(TAG, "Sensor segment upload failed: " + result.error);
                    }
                }

                if (success) {
                    Log.i(TAG, (imu ? "IMU" : "Sensor") + " segment uploaded: " + segment.getName());
                    deleteFileQuietly(segment);
                } else {
                    queueSegmentForRetry(imu, segment);
                }
            } catch (Exception e) {
                Log.e(TAG, "Segment upload failed", e);
                if (segment != null) {
                    queueSegmentForRetry(imu, segment);
                }
            } finally {
                gate.set(false);
            }
        }, imu ? "MobiBox-ImuUpload" : "MobiBox-SensorUpload").start();
    }

    private synchronized File sealActiveCsvFile(boolean imu) {
        Object lock = imu ? imuFileLock : sensorFileLock;
        synchronized (lock) {
            try {
                File dir = new File(Environment.getExternalStorageDirectory(), Constants.DATA_DIR);
                if (!dir.exists() && !dir.mkdirs()) {
                    Log.e(TAG, "Cannot create data directory: " + dir.getAbsolutePath());
                    return null;
                }

                String fileName = imu ? Constants.FILE_IMU_CSV : Constants.FILE_SENSOR_CSV;
                File activeFile = new File(dir, fileName);
                if (!activeFile.exists() || activeFile.length() == 0) {
                    return null;
                }

                if (imu) {
                    if (fileRepository == null) {
                        fileRepository = FileRepository.getInstance(getApplicationContext());
                    }
                    fileRepository.flushImuWriter();
                    fileRepository.closeFileWriter(Constants.getImuFilePath());
                    closeFileWriter(Constants.getImuFilePath());
                } else {
                    if (sensorWriter != null) {
                        sensorWriter.flush();
                    }
                    closeFileWriter(Constants.getSensorFilePath());
                }

                String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
                String ext = fileName.substring(fileName.lastIndexOf('.'));
                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(new Date());
                File sealed = new File(dir, baseName + "_upload_" + timestamp + ext);

                if (!activeFile.renameTo(sealed)) {
                    Log.e(TAG, "Failed to seal active file: " + activeFile.getAbsolutePath());
                    if (imu) {
                        fileRepository.initializeFileWriter(Constants.FILE_IMU_CSV);
                        initializeFileWriter(Constants.getImuFilePath());
                    } else {
                        initializeFileWriter(Constants.getSensorFilePath());
                    }
                    return null;
                }

                if (imu) {
                    fileRepository.initializeFileWriter(Constants.FILE_IMU_CSV);
                    initializeFileWriter(Constants.getImuFilePath());
                } else {
                    initializeFileWriter(Constants.getSensorFilePath());
                }

                Log.i(TAG, "Sealed " + fileName + " for upload: " + sealed.getName());
                return sealed;
            } catch (Exception e) {
                Log.e(TAG, "Failed to seal CSV file", e);
                if (imu) {
                    if (fileRepository != null) {
                        fileRepository.initializeFileWriter(Constants.FILE_IMU_CSV);
                    }
                    initializeFileWriter(Constants.getImuFilePath());
                } else {
                    initializeFileWriter(Constants.getSensorFilePath());
                }
                return null;
            }
        }
    }

    private boolean uploadCsvChunksBlocking(String csvContent, String url) {
        List<String> chunks = splitCSVIntoChunks(csvContent, Constants.IMU_UPLOAD_CHUNK_SIZE);
        if (chunks.isEmpty()) {
            return true;
        }

        for (int i = 0; i < chunks.size(); i++) {
            CsvUploadResult result = uploadCSVBlocking(userId, chunks.get(i), url);
            if (!result.success) {
                Log.e(TAG, "IMU chunk " + (i + 1) + "/" + chunks.size() + " failed: " + result.error);
                return false;
            }
        }
        return true;
    }

    private void queueSegmentForRetry(boolean imu, File segment) {
        if (segment == null || !segment.exists() || uploadQueueManager == null) {
            return;
        }
        UploadQueueManager.UploadType type = imu
                ? UploadQueueManager.UploadType.IMU_BACKUP
                : UploadQueueManager.UploadType.SENSOR_BACKUP;
        uploadQueueManager.addBackupFileToQueue(type, segment.getAbsolutePath());
        notifyUploadFailure(imu ? "IMU upload failed; segment saved for retry"
                : "Sensor upload failed; segment saved for retry");
    }

    private void notifyUploadFailure(String message) {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUploadErrorToastTime > Constants.TOAST_COOLDOWN_MS) {
            lastUploadErrorToastTime = currentTime;
            handler.post(() -> Toast.makeText(DataService.this, message, Toast.LENGTH_LONG).show());
            sendCustomNotification(DataService.this, "Upload Failed", message);
        }
    }

    private String readTextFile(File file) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to read file: " + file.getAbsolutePath(), e);
        }
        return sb.toString();
    }

    private void deleteFileQuietly(File file) {
        if (file != null && file.exists() && !file.delete()) {
            Log.w(TAG, "Failed to delete file: " + file.getAbsolutePath());
        }
    }

    /**
     * Upload IMU data in chunks sequentially
     * @param userId User ID
     * @param URL Upload endpoint
     * @param filePath File path for logging
     * @param chunks List of all chunk contents
     * @param currentChunk Current chunk index (0-based)
     * @param successCount Number of successfully uploaded chunks
     * @param finalCallback Final callback when all chunks complete
     */
    private void uploadIMUInChunks(String userId, String URL,
            String filePath, List<String> chunks, int currentChunk,
            int successCount, UploadCallback finalCallback) {

        if (currentChunk >= chunks.size()) {
            // All chunks uploaded successfully
            Log.i(TAG, "✅ 所有 IMU 数据块上传成功，共 " + successCount + " 块");
            finalCallback.onSuccess();
            return;
        }

        String chunkContent = chunks.get(currentChunk);
        int totalChunks = chunks.size();

        Log.d(TAG, "上传 IMU 数据块 " + (currentChunk + 1) + "/" + totalChunks +
                "，行数约: " + chunkContent.split("\n").length);

        uploadCSV(userId, chunkContent, URL, new UploadCallback() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "✅ IMU 数据块 " + (currentChunk + 1) + "/" + totalChunks + " 上传成功");
                // Upload next chunk
                uploadIMUInChunks(userId, URL, filePath, chunks, currentChunk + 1,
                        successCount + 1, finalCallback);
            }

            @Override
            public void onFailure(String error) {
                Log.e(TAG, "❌ IMU 数据块 " + (currentChunk + 1) + "/" + totalChunks +
                        " 上传失败: " + error);
                // Stop uploading, keep all data for retry
                finalCallback.onFailure(error);
            }
        });
    }

    // 上传 CSV 文件到服务器
    private CsvUploadResult uploadCSVBlocking(String userId, String csvContent, String URL) {
        if (TextUtils.isEmpty(userId)) {
            return new CsvUploadResult(false, "Missing userId");
        }
        if (TextUtils.isEmpty(csvContent)) {
            return new CsvUploadResult(true, null);
        }

        String urlType = URL.contains("imu") ? "IMU" : "Sensor";
        JSONObject payload = new JSONObject();
        try {
            JSONArray items = parseCSVToJSON(csvContent, urlType, userId);
            if (items.length() == 0) {
                Log.w(TAG, urlType + " CSV produced no uploadable rows");
                return new CsvUploadResult(true, null);
            }
            payload.put("items", items);
        } catch (JSONException e) {
            return new CsvUploadResult(false, "Failed to create JSON payload: " + e.getMessage());
        }

        RequestBody requestBody = RequestBody.create(
                payload.toString(),
                MediaType.get("application/json; charset=utf-8"));

        Request request = new Request.Builder()
                .url(URL)
                .post(requestBody)
                .build();

        try (Response response = getUploadHttpClient().newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                return new CsvUploadResult(false, "HTTP " + response.code() + ": " + responseBody);
            }
            if (TextUtils.isEmpty(responseBody)) {
                return new CsvUploadResult(false, "Empty server response");
            }

            JSONObject jsonResponse = new JSONObject(responseBody);
            if ("success".equals(jsonResponse.optString("status"))) {
                return new CsvUploadResult(true, null);
            }
            return new CsvUploadResult(false, jsonResponse.optString("message", "Unknown server error"));
        } catch (JSONException e) {
            return new CsvUploadResult(false, "Failed to parse server response: " + e.getMessage());
        } catch (IOException e) {
            return new CsvUploadResult(false, e.getClass().getSimpleName() + ": " + e.getMessage());
        } catch (Exception e) {
            return new CsvUploadResult(false, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private OkHttpClient getUploadHttpClient() {
        if (uploadHttpClient == null) {
            uploadHttpClient = new OkHttpClient.Builder()
                    .connectTimeout(Constants.NETWORK_CONNECT_TIMEOUT_SEC, TimeUnit.SECONDS)
                    .writeTimeout(Constants.NETWORK_WRITE_TIMEOUT_SEC, TimeUnit.SECONDS)
                    .readTimeout(Constants.NETWORK_READ_TIMEOUT_SEC, TimeUnit.SECONDS)
                    .callTimeout(Constants.NETWORK_CALL_TIMEOUT_SEC, TimeUnit.SECONDS)
                    .retryOnConnectionFailure(true)
                    .build();
        }
        return uploadHttpClient;
    }

    private void uploadCSV(String userId, String csvContent, String URL, UploadCallback callback) {
        CsvUploadResult result = uploadCSVBlocking(userId, csvContent, URL);
        if (result.success) {
            callback.onSuccess();
        } else {
            callback.onFailure(result.error);
        }
    }

    /**
     * 将CSV内容解析为JSON数组格式
     * @param csvContent CSV内容字符串
     * @param urlType 数据类型 ("IMU" 或 "Sensor")
     * @param userId 用户ID
     * @return JSON数组
     */
    private JSONArray parseCSVToJSON(String csvContent, String urlType, String userId) {
        JSONArray items = new JSONArray();
        String[] lines = csvContent.split("\n");

        for (String line : lines) {
            if (line.trim().isEmpty()) continue;

            String[] columns = splitCsvLine(line);
            
            try {
                if ("IMU".equals(urlType)) {
                    // IMU数据格式: timestamp,accel_x,accel_y,accel_z,gyro_x,gyro_y,gyro_z,mag_x,mag_y,mag_z
                    if (columns.length >= 10) {
                        JSONObject item = new JSONObject();
                        // Add user field to each item (required by backend)
                        item.put("user", userId);
                        item.put("timestamp", columns[0].trim());
                        item.put("acc_X", parseDouble(columns[1]));
                        item.put("acc_Y", parseDouble(columns[2]));
                        item.put("acc_Z", parseDouble(columns[3]));
                        item.put("gyro_X", parseDouble(columns[4]));
                        item.put("gyro_Y", parseDouble(columns[5]));
                        item.put("gyro_Z", parseDouble(columns[6]));
                        item.put("mag_X", parseDouble(columns[7]));
                        item.put("mag_Y", parseDouble(columns[8]));
                        item.put("mag_Z", parseDouble(columns[9]));
                        items.put(item);
                    } else {
                        Log.w(TAG, "IMU CSV行列数不足，跳过: " + line + " (期望10列，实际" + columns.length + "列)");
                    }
                } else {
                    // Sensor数据格式: timestamp,volumePercentage,screenOnCounter,wifiStatus,
                    // connect_wifi_name,networkTrafficInMB,rx_traffic,tx_traffic,stepcount_sensor,
                    // latStr,lonStr,battery_level,appNameForCsv,bluetoothDevices,currentAddress,
                    // currentPoi,nearbyBluetoothCount,topBluetoothDevices
                    if (columns.length >= 18) {
                        JSONObject item = new JSONObject();
                        // Add user field to each item (required by backend)
                        item.put("user", userId);
                        item.put("timestamp", columns[0].trim());
                        item.put("volume", parseDouble(columns[1]));
                        item.put("screen_on_ratio", parseDouble(columns[2]));
                        item.put("wifi_connected", "1".equals(columns[3].trim()));
                        item.put("wifi_ssid", columns[4].trim());
                        item.put("network_traffic", parseDouble(columns[5]));
                        item.put("Rx_traffic", parseDouble(columns[6]));  // Added missing field
                        item.put("Tx_traffic", parseDouble(columns[7]));  // Added missing field
                        item.put("stepcount_sensor", parseInt(columns[8]));
                        item.put("gpsLat", parseDouble(columns[9]));
                        item.put("gpsLon", parseDouble(columns[10]));
                        item.put("battery", parseDouble(columns[11]));
                        item.put("current_app", columns[12].trim());
                        item.put("bluetooth_devices", parseBluetoothDevices(columns[13]));
                        item.put("address", columns[14].trim());
                        item.put("poi", parsePoiList(columns[15]));
                        item.put("nearbyBluetoothCount", parseInt(columns[16]));
                        item.put("topBluetoothDevices", parseBluetoothDevices(columns[17]));
                        items.put(item);
                    } else {
                        Log.w(TAG, "Sensor CSV行列数不足，跳过: " + line + " (期望18列，实际" + columns.length + "列)");
                    }
                }
            } catch (JSONException e) {
                Log.e(TAG, "解析CSV行失败: " + line + ", 错误: " + e.getMessage());
            }
        }

        return items;
    }

    /**
     * 解析双精度浮点数，处理空值和异常
     */
    private String[] splitCsvLine(String line) {
        List<String> columns = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                columns.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        columns.add(current.toString());
        return columns.toArray(new String[0]);
    }

    private double parseDouble(String value) {
        if (value == null || value.trim().isEmpty() || "N/A".equals(value.trim())) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /**
     * 解析整数，处理空值和异常
     */
    private int parseInt(String value) {
        if (value == null || value.trim().isEmpty() || "N/A".equals(value.trim())) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 解析蓝牙设备列表（分号分隔的字符串转为JSON数组）
     */
    private JSONArray parseBluetoothDevices(String value) {
        JSONArray array = new JSONArray();
        if (value == null || value.trim().isEmpty() || "N/A".equals(value.trim())) {
            return array;
        }
        String[] devices = value.split(";");
        for (String device : devices) {
            if (!device.trim().isEmpty()) {
                array.put(device.trim());
            }
        }
        return array;
    }

    /**
     * 解析POI列表（分号或逗号分隔的字符串转为JSON数组）
     */
    private JSONArray parsePoiList(String value) {
        JSONArray array = new JSONArray();
        if (value == null || value.trim().isEmpty() || "N/A".equals(value.trim())) {
            return array;
        }
        String[] pois = value.split("[;,]");
        for (String poi : pois) {
            if (!poi.trim().isEmpty()) {
                array.put(poi.trim());
            }
        }
        return array;
    }

    // 读取 CSV 文件
    private String readCSVFile(String fileDir) {
        String csvContent = "";
        File file = new File(Environment.getExternalStorageDirectory(), fileDir);
        Set<String> timestampSet = new HashSet<>(); // 用于记录已经读取过的时间戳

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                String[] columns = splitCsvLine(line);
                if (columns.length > 0) {
                    String timestamp = columns[0]; // 第一列是时间戳
                    if (!timestampSet.contains(timestamp)) {
                        timestampSet.add(timestamp); // 将时间戳添加到 Set 中
                        sb.append(line).append("\n"); // 将该行添加到 StringBuilder 中
                    }
                }
            }
            csvContent = sb.toString();
            Log.i("csvFile", "Read csv file successful! ");
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to read CSV file: " + e.getMessage(), Toast.LENGTH_LONG).show();
            Log.e("csvFile", "Failed to read CSV file: " + e.getMessage());
        }
        return csvContent;
    }

    // 清空 CSV 文件
    private void clearCSVFile(String fileDir) {
        File file = new File(Environment.getExternalStorageDirectory(), fileDir);

        // 1. 先关闭现有的FileWriter
        closeFileWriter(fileDir);

        // 2. 清空文件内容
        try (FileWriter writer = new FileWriter(file, false)) {
            writer.write("");
            Log.i("csvFile", "CSV file cleared successfully");
        } catch (IOException e) {
            e.printStackTrace();
            Log.e("csvFile", "Failed to clear CSV file: " + e.getMessage());
        }

        // 3. 重新初始化FileWriter
        initializeFileWriter(fileDir);
    }

    private void closeFileWriter(String filePath) {
        try {
            if (filePath.contains("sensor.csv") && sensorWriter != null) {
                sensorWriter.close();
                sensorWriter = null;
            } else if (filePath.contains("IMU.csv") && imuWriter != null) {
                imuWriter.close();
                imuWriter = null;
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void initializeFileWriter(String filePath) {
        File dir = new File(Environment.getExternalStorageDirectory() + "/0.Mobibox");
        if (!dir.exists())
            dir.mkdirs(); //

        try {
            if (filePath.contains("sensor.csv")) {
                sensorFile = new File(dir, "sensor.csv");
                sensorWriter = new FileWriter(sensorFile, true); // 追加模式
            } else if (filePath.contains("IMU.csv")) {
                imuFile = new File(dir, "IMU.csv");
                imuWriter = new FileWriter(imuFile, true); // 追加模式
            }
        } catch (IOException e) {
            e.printStackTrace();
            Log.e(TAG, "File initialization failure " + filePath, e);
        }
    }

    private void initBluetooth() {
        if (bluetoothAdapter == null) {
            Log.w(TAG, "蓝牙适配器不可用");
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Log.w(TAG, "蓝牙未启用");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
            if (bluetoothLeScanner != null) {
                Log.i(TAG, "蓝牙LE扫描器初始化成功");
            } else {
                Log.e(TAG, "蓝牙LE扫描器初始化失败");
            }
        }

        // 初始化配对设备名称映射（用于名称回退）
        // Android 12+ 需要 BLUETOOTH_CONNECT 权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "缺少 BLUETOOTH_CONNECT 权限，跳过读取配对设备");
                return;
            }
        }

        try {
            Set<BluetoothDevice> paired = bluetoothAdapter.getBondedDevices();
            if (paired != null && !paired.isEmpty()) {
                int count = 0;
                for (BluetoothDevice d : paired) {
                    if (d != null && d.getAddress() != null) {
                        try {
                            String n = d.getName();
                            if (!TextUtils.isEmpty(n)) {
                                bondedNameByAddress.put(d.getAddress(), n);
                                count++;
                            }
                        } catch (SecurityException se) {
                            Log.w(TAG, "获取设备名称权限异常: " + d.getAddress());
                        }
                    }
                }
                Log.i(TAG, "成功读取 " + count + " 个配对设备名称");
            } else {
                Log.i(TAG, "没有已配对的蓝牙设备");
            }
        } catch (SecurityException e) {
            Log.w(TAG, "读取已配对设备时权限异常: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "读取已配对设备名称失败: " + e.getMessage());
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);

            BluetoothDevice device = result.getDevice();
            int rssi = result.getRssi();
            String name = resolveBluetoothName(device, result);
            String address = device.getAddress();

            // 过滤掉信号太弱的设备（RSSI < -80dBm）
            if (rssi > -80) {
                BluetoothScanResult scanResult = new BluetoothScanResult(name, address, rssi);

                synchronized (nearbyBluetoothDevices) {
                    // 检查是否已存在相同设备，如果存在则更新RSSI
                    boolean updated = false;
                    for (int i = 0; i < nearbyBluetoothDevices.size(); i++) {
                        BluetoothScanResult existing = nearbyBluetoothDevices.get(i);
                        if (existing.address.equals(address)) {
                            // 如果新的信号强度更好，则更新
                            if (rssi > existing.rssi) {
                                nearbyBluetoothDevices.set(i, scanResult);
                            }
                            updated = true;
                            break;
                        }
                    }

                    if (!updated) {
                        nearbyBluetoothDevices.add(scanResult);
                    }
                }

                Log.d(TAG, "发现蓝牙设备: " + name + " (" + address + ") RSSI: " + rssi);
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            for (ScanResult result : results) {
                onScanResult(ScanSettings.CALLBACK_TYPE_ALL_MATCHES, result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.e(TAG, "蓝牙扫描失败，错误码: " + errorCode);
            isScanning = false;
        }
    };

    // 4. 蓝牙扫描控制方法
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void startBluetoothScan() {
        if (bluetoothLeScanner == null || isScanning) {
            return;
        }

        // 检查权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ 需要 BLUETOOTH_SCAN 和 BLUETOOTH_CONNECT
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "蓝牙扫描权限不足 (需要 LOCATION, BLUETOOTH_SCAN, BLUETOOTH_CONNECT)");
                return;
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6-11 需要位置权限
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "蓝牙扫描权限不足 (需要 LOCATION)");
                return;
            }
        }

        try {
            // 优化：使用低功耗模式以支持后台和锁屏扫描
            // 注意：低延迟模式在后台/锁屏时可能被系统限制，改用低功耗模式
            ScanSettings.Builder settingsBuilder = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER) // 使用低功耗模式，后台友好
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    .setReportDelay(0); // 立即报告

            // Android 6.0+ 添加额外设置
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                settingsBuilder
                        .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE) // 激进匹配模式
                        .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT); // 最大匹配数
            }

            ScanSettings settings = settingsBuilder.build();

            nearbyBluetoothDevices.clear(); // 清空之前的扫描结果
            bluetoothLeScanner.startScan(null, settings, scanCallback);
            isScanning = true;

            Log.i(TAG, "开始蓝牙扫描（后台/锁屏优化模式：低功耗）");

            // 设置扫描持续时间
            bluetoothHandler.postDelayed(() -> {
                stopBluetoothScan();
                // 扫描结束后，对未解析成功的设备进行重试
                retryUnknownDevices();
            }, Constants.BLUETOOTH_SCAN_DURATION_MS);

        } catch (SecurityException e) {
            Log.e(TAG, "蓝牙扫描权限异常: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "蓝牙扫描启动失败: " + e.getMessage());
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void stopBluetoothScan() {
        if (bluetoothLeScanner != null && isScanning) {
            try {
                bluetoothLeScanner.stopScan(scanCallback);
                isScanning = false;
                Log.i(TAG, "停止蓝牙扫描，发现 " + nearbyBluetoothDevices.size() + " 个设备");
            } catch (SecurityException e) {
                Log.e(TAG, "停止蓝牙扫描权限异常: " + e.getMessage());
            }
        }
    }

    // 5. 获取扫描到的蓝牙设备数量
    public int getNearbyBluetoothDevicesCount() {
        synchronized (nearbyBluetoothDevices) {
            return nearbyBluetoothDevices.size();
        }
    }

    // 6. 获取最强的三个蓝牙设备（包含详细信息：名称|RSSI|地址）
    public String getTopThreeBluetoothDevices() {
        synchronized (nearbyBluetoothDevices) {
            if (nearbyBluetoothDevices.isEmpty()) {
                return "N/A";
            }

            // 按RSSI降序排列（信号强度从强到弱）
            Collections.sort(nearbyBluetoothDevices, (a, b) -> Integer.compare(b.rssi, a.rssi));

            StringBuilder result = new StringBuilder();
            int count = Math.min(3, nearbyBluetoothDevices.size());

            for (int i = 0; i < count; i++) {
                BluetoothScanResult device = nearbyBluetoothDevices.get(i);
                if (i > 0) {
                    result.append(";");
                }

                // 格式：设备名称|RSSI值|MAC地址
                String deviceName = getBestDeviceName(device);
                String deviceInfo = String.format("%s|%d|%s",
                        deviceName,
                        device.rssi,
                        device.address);
                result.append(deviceInfo);

                // 调试日志：查看实际获取的设备信息
                Log.d(TAG, String.format("Top蓝牙设备[%d]: 名称='%s', 原始名称='%s', RSSI=%d, MAC=%s",
                        i + 1, deviceName, device.name, device.rssi, device.address));
            }

            String finalResult = result.toString();
            Log.d(TAG, "Top3蓝牙设备最终字符串: " + finalResult);
            return finalResult;
        }
    }

    // 获取最佳设备名称显示
    private String getBestDeviceName(BluetoothScanResult device) {
        // 1. 优先使用已解析的名称（如果有效）
        if (!TextUtils.isEmpty(device.name) && !"Unknown".equals(device.name)) {
            return device.name;
        }

        // 2. 尝试从已配对设备中查找名称
        String bondedName = bondedNameByAddress.get(device.address);
        if (!TextUtils.isEmpty(bondedName)) {
            return bondedName;
        }

        // 3. 尝试通过蓝牙适配器重新获取设备名称
        if (bluetoothAdapter != null) {
            try {
                BluetoothDevice btDevice = bluetoothAdapter.getRemoteDevice(device.address);
                if (btDevice != null) {
                    String deviceName = btDevice.getName();
                    if (!TextUtils.isEmpty(deviceName)) {
                        return deviceName;
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "无法获取设备名称: " + device.address + ", " + e.getMessage());
            }
        }

        // 4. 如果所有方法都失败，生成一个友好的名称而不是显示MAC地址
        return generateFriendlyName(device.address);
    }

    // 生成友好的设备名称（基于MAC地址后4位）
    private String generateFriendlyName(String address) {
        if (TextUtils.isEmpty(address)) {
            return "Unknown Device";
        }

        // 提取MAC地址的后4位作为设备标识
        String[] parts = address.split(":");
        if (parts.length >= 6) {
            String lastTwo = parts[4] + parts[5];
            return "Device-" + lastTwo.toUpperCase();
        }

        return "Unknown Device";
    }

    // 从 ScanRecord 原始数据中解析设备名称
    private String parseDeviceNameFromScanRecord(byte[] scanRecord) {
        if (scanRecord == null || scanRecord.length == 0) {
            return null;
        }

        try {
            int pos = 0;
            while (pos < scanRecord.length - 1) {
                int length = scanRecord[pos] & 0xFF;
                if (length == 0)
                    break;

                int type = scanRecord[pos + 1] & 0xFF;

                // 0x08 = 短设备名称, 0x09 = 完整设备名称
                if (type == 0x09 || type == 0x08) {
                    byte[] nameBytes = new byte[length - 1];
                    System.arraycopy(scanRecord, pos + 2, nameBytes, 0, length - 1);
                    String name = new String(nameBytes, "UTF-8").trim();
                    if (!TextUtils.isEmpty(name)) {
                        Log.d(TAG, "从ScanRecord解析到设备名称: " + name);
                        return name;
                    }
                }

                pos += length + 1;
            }
        } catch (Exception e) {
            Log.w(TAG, "解析ScanRecord失败: " + e.getMessage());
        }
        return null;
    }

    // 检查是否有 BLUETOOTH_CONNECT 权限（Android 12+）
    private boolean hasBluetoothConnectPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
        }
        return true; // Android 12 以下不需要此权限
    }

    // 解析蓝牙设备显示名称：优先广播名，其次设备名，再次配对名，最后从持久化存储获取
    private String resolveBluetoothName(BluetoothDevice device, ScanResult result) {
        String address = device != null ? device.getAddress() : null;

        // 0. 先检查缓存
        if (!TextUtils.isEmpty(address) && deviceNameCache.containsKey(address)) {
            String cachedName = deviceNameCache.get(address);
            if (!TextUtils.isEmpty(cachedName) && !cachedName.equals("Unknown")) {
                return cachedName;
            }
        }

        String resolvedName = null;

        try {
            // 1) 从 ScanRecord 的 getDeviceName() 获取
            if (result != null && result.getScanRecord() != null) {
                resolvedName = result.getScanRecord().getDeviceName();
                if (!TextUtils.isEmpty(resolvedName)) {
                    Log.d(TAG, "从ScanRecord.getDeviceName()获取: " + resolvedName + " [" + address + "]");
                    cacheDeviceName(address, resolvedName);
                    saveDeviceNameToPersistentStorage(address, resolvedName); // 持久化保存
                    return resolvedName;
                }

                // 2) 从 ScanRecord 原始字节数据解析
                byte[] scanRecordBytes = result.getScanRecord().getBytes();
                resolvedName = parseDeviceNameFromScanRecord(scanRecordBytes);
                if (!TextUtils.isEmpty(resolvedName)) {
                    cacheDeviceName(address, resolvedName);
                    saveDeviceNameToPersistentStorage(address, resolvedName); // 持久化保存
                    return resolvedName;
                }
            }

            // 3) 使用 device.getName()（需要 BLUETOOTH_CONNECT 权限）
            if (device != null && hasBluetoothConnectPermission()) {
                try {
                    resolvedName = device.getName();
                    if (!TextUtils.isEmpty(resolvedName)) {
                        Log.d(TAG, "从device.getName()获取: " + resolvedName + " [" + address + "]");
                        cacheDeviceName(address, resolvedName);
                        saveDeviceNameToPersistentStorage(address, resolvedName); // 持久化保存
                        return resolvedName;
                    }
                } catch (SecurityException e) {
                    Log.w(TAG, "缺少BLUETOOTH_CONNECT权限: " + e.getMessage());
                }
            }

            // 4) 从已配对设备缓存中查找
            if (!TextUtils.isEmpty(address)) {
                String bondedName = bondedNameByAddress.get(address);
                if (!TextUtils.isEmpty(bondedName)) {
                    Log.d(TAG, "从已配对设备获取: " + bondedName + " [" + address + "]");
                    cacheDeviceName(address, bondedName);
                    saveDeviceNameToPersistentStorage(address, bondedName); // 持久化保存
                    return bondedName;
                }
            }

            // 5) 从持久化存储中获取（历史记录）
            String persistentName = loadDeviceNameFromPersistentStorage(address);
            if (!TextUtils.isEmpty(persistentName)) {
                Log.d(TAG, "从历史记录获取: " + persistentName + " [" + address + "]");
                cacheDeviceName(address, persistentName);
                return persistentName;
            }

            // 6) 尝试从厂商OUI识别设备品牌
            String vendorName = getVendorNameFromMac(address);
            if (!TextUtils.isEmpty(vendorName)) {
                Log.d(TAG, "从MAC地址识别厂商: " + vendorName + " [" + address + "]");
                return vendorName;
            }

        } catch (Exception e) {
            Log.w(TAG, "解析设备名称异常: " + e.getMessage());
        }

        // 如果所有方法都失败，加入重试队列
        if (!TextUtils.isEmpty(address)) {
            addToUnknownDeviceQueue(address);
        }

        Log.w(TAG, "无法获取设备名称，地址: " + address + "，已加入重试队列");
        return "Unknown";
    }

    // 缓存设备名称
    private void cacheDeviceName(String address, String name) {
        if (!TextUtils.isEmpty(address) && !TextUtils.isEmpty(name)) {
            deviceNameCache.put(address, name);
        }
    }

    // 7. 启动定时蓝牙扫描
    private void startPeriodicBluetoothScan() {
        bluetoothHandler.post(new Runnable() {
            @Override
            public void run() {
                if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                    startBluetoothScan();
                }
                bluetoothHandler.postDelayed(this, Constants.BLUETOOTH_SCAN_INTERVAL_MS);
            }
        });
    }

    // 8. 持久化保存设备名称到SharedPreferences
    private void saveDeviceNameToPersistentStorage(String address, String name) {
        if (TextUtils.isEmpty(address) || TextUtils.isEmpty(name)) {
            return;
        }
        try {
            SharedPreferences prefs = getSharedPreferences("BluetoothDeviceNames", MODE_PRIVATE);
            prefs.edit().putString(address, name).apply();
            Log.d(TAG, "设备名称已保存到持久化存储: " + name + " [" + address + "]");
        } catch (Exception e) {
            Log.e(TAG, "保存设备名称失败: " + e.getMessage());
        }
    }

    // 9. 从SharedPreferences加载设备名称
    private String loadDeviceNameFromPersistentStorage(String address) {
        if (TextUtils.isEmpty(address)) {
            return null;
        }
        try {
            SharedPreferences prefs = getSharedPreferences("BluetoothDeviceNames", MODE_PRIVATE);
            return prefs.getString(address, null);
        } catch (Exception e) {
            Log.e(TAG, "加载设备名称失败: " + e.getMessage());
            return null;
        }
    }

    // 10. 从MAC地址识别设备厂商（基于OUI - Organizationally Unique Identifier）
    private String getVendorNameFromMac(String address) {
        if (TextUtils.isEmpty(address) || address.length() < 8) {
            return null;
        }

        try {
            // 提取OUI（前3个字节）
            String oui = address.substring(0, 8).replace(":", "").toUpperCase();

            // 常见蓝牙设备厂商OUI映射（部分示例）
            switch (oui) {
                // Apple
                case "00036":
                case "001D4F":
                case "0025BC":
                case "28E14C":
                case "9C208E":
                case "E0ACCB":
                case "98FE94":
                case "A0999B":
                case "64A5C3":
                case "E8040B":
                case "7C0191":
                case "F0DCE2":
                    return "Apple Device";

                // Samsung
                case "005056":
                case "001632":
                case "0018AF":
                case "1C232C":
                case "E840F2":
                case "0C1420":
                case "342387":
                case "6C2F2C":
                    return "Samsung Device";

                // Xiaomi
                case "1034B6":
                case "342476":
                case "640980":
                case "787B8A":
                case "7CE9D3":
                case "C460F6":
                case "F0B429":
                case "F8A45F":
                    return "Xiaomi Device";

                // Huawei
                case "005A13":
                case "0CF839":
                case "2CE4F7":
                case "48DB50":
                case "5CF8A1":
                case "68DB54":
                case "C8F230":
                case "E06995":
                    return "Huawei Device";

                // Google
                case "7C1DD9":
                case "002586":
                case "5CF370":
                case "F4F5E8":
                    return "Google Device";

                // Sony
                case "000C9B":
                case "001323":
                case "0016CF":
                case "0019C5":
                    return "Sony Device";

                // JBL
                case "5C6968":
                case "0C728E":
                    return "JBL Speaker";

                // Bose
                case "2C411F":
                case "B8D5CB":
                    return "Bose Device";

                // Beats
                case "8CF5A3":
                case "C8690A":
                    return "Beats Device";

                default:
                    return null; // 未识别的厂商
            }
        } catch (Exception e) {
            Log.e(TAG, "识别厂商失败: " + e.getMessage());
            return null;
        }
    }

    // 11. 添加设备到未知设备队列
    private void addToUnknownDeviceQueue(String address) {
        if (TextUtils.isEmpty(address)) {
            return;
        }

        synchronized (unknownDeviceQueue) {
            // 检查是否已经在队列中
            if (!unknownDeviceQueue.contains(address)) {
                unknownDeviceQueue.add(address);
                // 增加重试计数
                Integer count = deviceNameRetryCount.get(address);
                deviceNameRetryCount.put(address, count == null ? 1 : count + 1);
                Log.d(TAG, "设备已加入重试队列: " + address + " (重试次数: " + deviceNameRetryCount.get(address) + ")");
            }
        }
    }

    // 12. 重试解析未知设备名称
    private void retryUnknownDevices() {
        synchronized (unknownDeviceQueue) {
            if (unknownDeviceQueue.isEmpty()) {
                Log.d(TAG, "未知设备队列为空，无需重试");
                return;
            }

            Log.i(TAG, "开始重试解析 " + unknownDeviceQueue.size() + " 个未知设备");

            List<String> toRetry = new ArrayList<>(unknownDeviceQueue);
            unknownDeviceQueue.clear();

            // 使用后台线程延迟重试，避免阻塞主线程
            new Thread(() -> {
                try {
                    Thread.sleep(2000); // 延迟2秒后重试
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                for (String address : toRetry) {
                    // 检查重试次数，最多重试3次
                    Integer retryCount = deviceNameRetryCount.get(address);
                    if (retryCount != null && retryCount > 3) {
                        Log.w(TAG, "设备重试次数超限，放弃: " + address);
                        continue;
                    }

                    try {
                        if (bluetoothAdapter != null && hasBluetoothConnectPermission()) {
                            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
                            if (device != null) {
                                String name = device.getName();
                                if (!TextUtils.isEmpty(name)) {
                                    Log.i(TAG, "重试成功，获取到设备名称: " + name + " [" + address + "]");
                                    cacheDeviceName(address, name);
                                    saveDeviceNameToPersistentStorage(address, name);

                                    // 更新已扫描到的设备列表
                                    synchronized (nearbyBluetoothDevices) {
                                        for (BluetoothScanResult scanResult : nearbyBluetoothDevices) {
                                            if (scanResult.address.equals(address)) {
                                                scanResult.name = name;
                                                break;
                                            }
                                        }
                                    }
                                } else {
                                    // 仍然无法获取，再次加入队列
                                    Log.d(TAG, "重试失败，设备名称仍为空: " + address);
                                }
                            }
                        }
                    } catch (SecurityException e) {
                        Log.w(TAG, "重试时权限不足: " + address);
                    } catch (Exception e) {
                        Log.e(TAG, "重试设备名称解析失败: " + address + ", " + e.getMessage());
                    }

                    // 避免请求过快
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                Log.i(TAG, "未知设备重试完成");
            }).start();
        }
    }

    /**
     * 获取UTC+8格式的时间戳字符串
     * 格式：yyyy-MM-dd HH:mm:ss (精确到秒)
     * 
     * @return UTC+8时间字符串
     */

    /**
     * 获取当前UTC ISO 8601格式的时间戳字符串
     * 格式：yyyy-MM-dd'T'HH:mm:ss.SSS'Z' (例如: 2026-03-03T11:34:30.470Z)
     * 
     * @return UTC ISO 8601时间字符串
     */
    private String getUTCISO8601Timestamp() {
        return getUTCISO8601Timestamp(System.currentTimeMillis());
    }

    /**
     * 获取UTC ISO 8601格式的时间戳字符串（指定时间）
     * 格式：yyyy-MM-dd'T'HH:mm:ss.SSS'Z' (例如: 2026-03-03T11:34:30.470Z)
     * 
     * @param timeMillis 毫秒时间戳
     * @return UTC ISO 8601时间字符串
     */
    private String getUTCISO8601Timestamp(long timeMillis) {
        // 使用ISO 8601格式，UTC时间（Z表示Zulu/UTC时间）
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC")); // UTC时间
        return sdf.format(new Date(timeMillis));
    }

    /**
     * 获取中国时区（Asia/Shanghai, UTC+8）ISO 8601格式的时间戳字符串（指定时间）
     * 格式：yyyy-MM-dd'T'HH:mm:ss.SSSXXX (例如: 2026-03-03T19:34:30.470+08:00)
     *
     * @param timeMillis 毫秒时间戳
     * @return 中国时区 ISO 8601 时间字符串
     */
    private String getChinaISO8601Timestamp(long timeMillis) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        return sdf.format(new Date(timeMillis));
    }

    // 上传回调接口
    interface UploadCallback {
        void onSuccess();

        void onFailure(String error);
    }

    /**
     * 启动步数传感器定期刷新任务
     * 每60秒重新注册一次步数传感器监听器，确保权限和传感器保持活跃状态
     */
    private void startStepSensorRefresh() {
        stepSensorRefreshHandler.post(refreshStepSensorRunnable);
        Log.d(TAG, "步数传感器定期刷新任务已启动");
    }

    /**
     * 定期刷新步数传感器的 Runnable
     */
    private final Runnable refreshStepSensorRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                // 检查权限状态
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    if (checkSelfPermission(
                            Manifest.permission.ACTIVITY_RECOGNITION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        Log.w(TAG, "⚠️ ACTIVITY_RECOGNITION 权限已丢失！");
                        // 这里可以发送通知提醒用户重新授权
                        stepSensorRefreshHandler.postDelayed(this, Constants.STEP_SENSOR_REFRESH_INTERVAL_MS);
                        return;
                    }
                }

                // 重新注册步数传感器监听器
                if (stepCounter != null && sensorManager != null) {
                    // 先取消注册
                    sensorManager.unregisterListener(DataService.this, stepCounter);
                    // 重新注册
                    boolean registered = sensorManager.registerListener(
                            DataService.this,
                            stepCounter,
                            SensorManager.SENSOR_DELAY_NORMAL);

                    if (registered) {
                        Log.d(TAG, "✅ 步数传感器监听器已刷新（重新注册成功）");
                    } else {
                        Log.w(TAG, "⚠️ 步数传感器监听器刷新失败");
                    }
                } else {
                    Log.w(TAG, "⚠️ 步数传感器不可用，无法刷新");
                }
            } catch (Exception e) {
                Log.e(TAG, "刷新步数传感器时出错: " + e.getMessage());
            }

            // 60秒后再次执行
            stepSensorRefreshHandler.postDelayed(this, Constants.STEP_SENSOR_REFRESH_INTERVAL_MS);
        }
    };
}
