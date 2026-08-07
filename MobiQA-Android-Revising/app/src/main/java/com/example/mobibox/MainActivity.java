package com.example.mobibox;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothProfile;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.example.mobibox.service.DataService;
import com.example.mobibox.service.DataService;
import com.example.mobibox.workers.DailyLogWorker;
import com.example.mobibox.workers.HourlyUpdateWorker;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import com.android.volley.Request;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.RequestBody;
import okhttp3.Response;

@RequiresApi(api = Build.VERSION_CODES.S)
public class MainActivity extends AppCompatActivity {

    private DataService dataService;
    private DataServiceConnection dataServiceConnection;
    private boolean dataServiceBound = false;

    private Button startButton;
    private Button stopButton;
    private Button interventionButton;
    private Button summaryButton;
    private Button surveyButton;
    private Button clearDataButton;
    private int currentPermissionIndex = 0;

    // 界面数据更新
    private TextView imuTextView;
    private TextView sensorDataTextView;

    // 干预轮询统一由 DataService 负责（POST）。MainActivity 不再直接请求。

    private String userId;
    private int counter = 0;
    private int ifGet = 0;

    private static final int REQUEST_BATTERY_OPTIMIZATION = 1001;
    private static final int REQUEST_AUTO_START_SETTINGS = 1002;

    private Handler handler = new Handler(Looper.getMainLooper());

    private Runnable runnable = new Runnable() {
        @Override
        public void run() {
            updateImuViews();
            updateSensorDataView();
            counter++;

            // 干预检查逻辑已迁移至 DataService 定时任务

            handler.postDelayed(this, 1000);
        }
    };

    // 权限获取和申请
    private String[] permissions = { Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            Manifest.permission.ACTIVITY_RECOGNITION, Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.FOREGROUND_SERVICE, Manifest.permission.BLUETOOTH_SCAN, };

    private List<String> permissionlist = Arrays.asList(Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_BACKGROUND_LOCATION,
            Manifest.permission.ACTIVITY_RECOGNITION, Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_NETWORK_STATE, Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.FOREGROUND_SERVICE);

    private boolean checkPermissions() {
        List<String> missingPermissions = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }
        // 打印未申请权限
        if (missingPermissions.size() != 0) {
            sensorDataTextView
                    .setText("Please grant permission and restart the APP:\n" + String.join(",\n", missingPermissions));
            sensorDataTextView.setTextColor(Color.RED);
            return false;
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            boolean allPermissionsGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allPermissionsGranted = false;
                    break;
                }
            }

            if (allPermissionsGranted) {
                // 所有权限都被授予，继续执行需要权限的操作
                updateSensorDataView();
            } else {
                // 某些权限被拒绝，向用户展示信息并提供跳转到设置页面的选项
                showPermissionDeniedDialog();
            }
        }
    }

    private void showPermissionDeniedDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Necessity of Permission")
                .setMessage(
                        "To ensure that the application can run, please grant permissions in \"Settings - All Permissions\" and !RESTART! the application.\n"
                                +
                                "The following permissions need to be enabled: Bluetooth/nearby devices, continuous precise location, management of all files, sensors, and physical activity.")
                .setPositiveButton("Go to Settings", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        Uri uri = Uri.fromParts("package", getPackageName(), null);
                        intent.setData(uri);
                        startActivityForResult(intent, 1);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                        finish(); // 或者采取其他措施，比如禁用某些功能
                    }
                })
                .show();
    }

    // 创建通知渠道（只需调用一次，建议放在应用启动时）
    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "INTERVENTION_CHANNEL",
                    "Intervention Notifications",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Channel for intervention alerts");
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 界面元素初始化
        startButton = findViewById(R.id.start_button);
        stopButton = findViewById(R.id.stop_button);
        imuTextView = findViewById(R.id.imu_textview);
        sensorDataTextView = findViewById(R.id.sensor_data_textview);
        interventionButton = findViewById(R.id.intervention_button);
        surveyButton = findViewById(R.id.survey_button);
        clearDataButton = findViewById(R.id.clear_data_button);

        // 设置按钮点击事件
        interventionButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, InterventionActivity.class);
                startActivity(intent);
            }
        });

        surveyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, DailyLogActivity.class);
                startActivity(intent);
            }
        });

        if (!checkPermissions()) {
            ActivityCompat.requestPermissions(this, permissions, 1);
        }

        if (!checkPermissions()) {
            this.requestPermissions(permissions, 0);
        }

        // 检查是否已授予"使用情况访问"权限
        if (!isUsageAccessPermissionGranted()) {
            Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
            startActivity(intent);
            sensorDataTextView.setText("!RESTART! the app after granting following permission:" + "APP_USAGE_ACCESS");
            sensorDataTextView.setTextColor(Color.RED);
        }

        // 检查并请求后台运行权限（电池优化和厂商特定设置）
        // 延迟2秒执行，等待其他权限请求完成
        handler.postDelayed(() -> {
            checkAndRequestBackgroundPermissions();
        }, 2000);

        updateSensorDataView();

        // 获取 SharedPreferences 实例
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        userId = prefs.getString("userId", "");

        // 初始化通知渠道
        createNotificationChannel(this);

        // 干预状态由 InterventionNotificationManager 的缓存驱动，无需预加载本地干预

        // 注册Daily Log定时查询任务（每天晚上8点）
        scheduleDailyLogWorker();

        // 注意：Hourly Log 和 Intervention 的定时查询已改为由 DataService 每5分钟轮询
        // 不再需要单独的 HourlyUpdateWorker

        // 开始按钮监听
        startButton.setOnClickListener(v -> {
            Log.e("MainActivity", "========================================");
            Log.e("MainActivity", "用户点击了 Start 按钮！");
            Log.e("MainActivity", "========================================");
            startDataCollection();
            startButton.setEnabled(false); // 禁用开始按钮
        });

        // 停止按钮监听
        stopButton.setOnClickListener(v -> {
            stopDataCollection();
            startButton.setEnabled(true); // 启用开始按钮
        });

        // 清除数据按钮监听
        clearDataButton.setOnClickListener(v -> {
            clearAllCachedData();
        });

        // 添加通知渠道创建代码（Android 8.0+ 必需）
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "Foreground_Channel",
                    "Data Collection Service",
                    NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Service running in foreground");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }

        Log.i("MainActivity", "started");
    }

    /**
     * 注册Daily Log定时查询任务
     * 每天晚上8点（中国时区 UTC+8）自动查询服务器，如果有新的daily log则发送通知
     * 
     * 时区说明：
     * - 服务器位于中国，使用UTC+8时区（Asia/Shanghai）
     * - Daily Log在服务器每天早上8点（UTC+8）生成
     * - 客户端在每天晚上8点（UTC+8）查询，确保有足够时间生成数据
     * - 无论用户设备在哪个时区，都统一按照中国时区调度
     */
    private void scheduleDailyLogWorker() {
        // 使用中国时区（UTC+8）
        TimeZone chinaTimeZone = TimeZone.getTimeZone("Asia/Shanghai");

        // 获取当前时间（中国时区）
        Calendar currentDate = Calendar.getInstance(chinaTimeZone);

        // 设置目标时间为今天晚上8点（中国时区）
        Calendar targetDate = Calendar.getInstance(chinaTimeZone);
        targetDate.set(Calendar.HOUR_OF_DAY, 20); // 晚上8点
        targetDate.set(Calendar.MINUTE, 0);
        targetDate.set(Calendar.SECOND, 0);
        targetDate.set(Calendar.MILLISECOND, 0);

        // 如果当前时间已经超过今天晚上8点，则设置为明天晚上8点
        if (targetDate.before(currentDate)) {
            targetDate.add(Calendar.DAY_OF_MONTH, 1);
        }

        long initialDelay = targetDate.getTimeInMillis() - currentDate.getTimeInMillis();

        // 创建每天执行一次的周期性任务
        PeriodicWorkRequest dailyLogWorkRequest = new PeriodicWorkRequest.Builder(
                DailyLogWorker.class,
                24, TimeUnit.HOURS, // 每24小时执行一次
                15, TimeUnit.MINUTES // 容错窗口15分钟
        )
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .build();

        // 使用REPLACE策略确保只有一个定时任务在运行
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
                "DailyLogWorker",
                ExistingPeriodicWorkPolicy.KEEP,
                dailyLogWorkRequest);

        // 记录详细的时区和时间信息
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss z");
        sdf.setTimeZone(chinaTimeZone);
        String targetTimeStr = sdf.format(targetDate.getTime());
        String currentTimeStr = sdf.format(currentDate.getTime());
        long delayMinutes = initialDelay / (60 * 1000);

        Log.d("MainActivity", "===== Daily Log Worker 时区配置 =====");
        Log.d("MainActivity", "时区: Asia/Shanghai (UTC+8)");
        Log.d("MainActivity", "当前时间: " + currentTimeStr);
        Log.d("MainActivity", "首次执行时间: " + targetTimeStr);
        Log.d("MainActivity", "延迟时间: " + delayMinutes + " 分钟");
        Log.d("MainActivity", "========================================");
    }

    // 绑定服务 监控生命周期
    class DataServiceConnection implements ServiceConnection {

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            Log.e("MainActivity", "🟢 onServiceConnected - Service 已连接！");
            dataService = ((DataService.Binder) binder).service;
            dataServiceBound = true;
            Log.e("MainActivity", "🟢 准备调用 dataService.startDataCollection()");
            dataService.startDataCollection();
            Log.e("MainActivity", "🟢 dataService.startDataCollection() 调用完成");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.e("MainActivity", "🔴 onServiceDisconnected - Service 断开连接");
            dataService = null;
            dataServiceBound = false;
        }

        @Override
        public void onBindingDied(ComponentName name) {
            Log.e("MainActivity", "🔴 onBindingDied - Service 绑定失败");
            dataService = null;
            dataServiceBound = false;
        }

        @Override
        public void onNullBinding(ComponentName name) {
            Log.i("DataServiceConnection", "onNullBinding");
            dataService = null;
            dataServiceBound = false;
        }
    }

    // 数据采集 bind参数保证数据监控服务是单例，不会重复创建！！
    private void startDataCollection() {
        Log.e("MainActivity", "🔵 startDataCollection 方法被调用");
        Intent serviceIntent = new Intent(this, DataService.class);
        ContextCompat.startForegroundService(this, serviceIntent);
        if (dataServiceConnection == null) {
            dataServiceConnection = new DataServiceConnection();
        }
        if (!dataServiceBound) {
            Log.e("MainActivity", "🔵 正在绑定 DataService...");
            boolean bound = bindService(serviceIntent, dataServiceConnection, Context.BIND_AUTO_CREATE);
            Log.e("MainActivity", "🔵 bindService 返回: " + bound);
        }
        handler.post(runnable);
    }

    private void stopDataCollection() {
        if (dataService != null) {
            dataService.stopDataCollection();
        }
        if (dataServiceBound && dataServiceConnection != null) {
            try {
                unbindService(dataServiceConnection);
            } catch (IllegalArgumentException e) {
                Log.w("MainActivity", "DataService already unbound");
            }
            dataServiceBound = false;
            dataService = null;
        }
        stopService(new Intent(this, DataService.class));
        handler.removeCallbacks(runnable);
        Toast.makeText(MainActivity.this, "Stop Data Collection", Toast.LENGTH_SHORT).show();
    }

    /**
     * 检查是否已经授予了"使用情况访问"权限
     */
    private boolean isUsageAccessPermissionGranted() {
        AppOpsManager appOps = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
        int mode = appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(),
                getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    /**
     * 检查电池优化是否已关闭
     * 
     * @return true表示已关闭电池优化，false表示还在优化列表中
     */
    private boolean isBatteryOptimizationDisabled() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (powerManager != null) {
                boolean isIgnoring = powerManager.isIgnoringBatteryOptimizations(getPackageName());
                Log.d("MainActivity", "isIgnoringBatteryOptimizations(" + getPackageName() + ") = " + isIgnoring);
                return isIgnoring;
            }
        }
        return true; // Android 6.0以下没有电池优化
    }

    /**
     * 请求用户关闭电池优化
     * 会显示一个对话框引导用户操作
     */
    @SuppressLint("BatteryLife")
    private void requestDisableBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!isBatteryOptimizationDisabled()) {
                new AlertDialog.Builder(this)
                        .setTitle("Battery Optimization Required")
                        .setMessage("To ensure the app runs properly in background and when screen is locked, Battery Optimization needs to be disabled.\n\n" +
                                "In the next page:\n" +
                                "1. Find and click \"" + getString(R.string.app_name) + "\"\n" +
                                "2. Select \"Don't optimize\" or \"Allow\"\n\n" +
                                "This is important for data collection!")
                        .setPositiveButton("Go to Settings", (dialog, which) -> {
                            try {
                                // 直接跳转到忽略电池优化设置页面
                                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                                intent.setData(Uri.parse("package:" + getPackageName()));
                                startActivityForResult(intent, REQUEST_BATTERY_OPTIMIZATION);
                            } catch (Exception e) {
                                Log.e("MainActivity", "无法打开电池优化设置: " + e.getMessage());
                                // 如果上面的方法失败，尝试打开通用的电池优化设置页面
                                try {
                                    Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                                    startActivityForResult(intent, REQUEST_BATTERY_OPTIMIZATION);
                                    Toast.makeText(this, "Please find " + getString(R.string.app_name) + " and turn off optimization",
                                            Toast.LENGTH_LONG).show();
                                } catch (Exception ex) {
                                    Toast.makeText(this, "Unable to open settings page", Toast.LENGTH_SHORT).show();
                                }
                            }
                        })
                        .setNegativeButton("Later", (dialog, which) -> {
                            Toast.makeText(this, "Warning: App may be restricted by system in background", Toast.LENGTH_LONG).show();
                        })
                        .setCancelable(false)
                        .show();
            }
        }
    }

    /**
     * 引导用户进行厂商特定的设置（自启动、后台运行等）
     * 不同厂商的设置路径不同，这里提供通用引导
     */
    private void showManufacturerSpecificSettings() {
        String manufacturer = Build.MANUFACTURER.toLowerCase();
        String message = "为确保应用在后台正常运行，还需要进行以下设置：\n\n";

        if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi")) {
            message += "【小米/红米手机】\n" +
                    "1. Find this app in \"Settings > App Settings > App Management\"\n" +
                    "2. Tap \"Battery Saver Strategy\" and select \"Unrestricted\"\n" +
                    "3. Tap \"Auto-start\" and enable auto-start permission\n" +
                    "4. Tap \"Background Pop-up\" and allow background pop-ups\n";
        } else if (manufacturer.contains("huawei") || manufacturer.contains("honor")) {
            message += "[Huawei/Honor]\n" +
                    "1. Find this app in \"Settings > Apps > App Launch Management\"\n" +
                    "2. Turn off \"Auto Manage\" and manually enable \"Auto Launch\", \"Secondary Launch\", and \"Run in Background\"\n" +
                    "3. In \"Settings > Battery\", add this app to \"Launch Management Whitelist\"\n";
        } else if (manufacturer.contains("oppo")) {
            message += "[OPPO]\n" +
                    "1. Find this app in \"Settings > Battery > App Power Management\"\n" +
                    "2. Enable \"Run in Background\" and \"Auto-start\"\n" +
                    "3. Find this app in \"Settings > App Management\"\n" +
                    "4. Disable \"App Freezer\" and \"Smart Power Saving\"\n";
        } else if (manufacturer.contains("vivo")) {
            message += "[vivo]\n" +
                    "1. Allow this app in \"iManager > App Management > Permission Management > Background Pop-up\"\n" +
                    "2. Allow this app in \"iManager > App Management > Auto-start\"\n" +
                    "3. Allow this app in \"Settings > Battery > Background Power Consumption\"\n";
        } else if (manufacturer.contains("samsung")) {
            message += "[Samsung]\n" +
                    "1. Find this app in \"Settings > Device Care > Battery > App Power Management\"\n" +
                    "2. Disable \"Put app to sleep\"\n" +
                    "3. Add this app to \"Apps that won't be put to sleep\" list\n";
        } else {
            message += "[Other Devices]\n" +
                    "1. Find \"Battery\" or \"Power Saving\" options in system settings\n" +
                    "2. Add this app to whitelist or allow background running\n" +
                    "3. Allow app auto-start (if available)\n";
        }

        message += "\nThese settings are important for continuous data collection!";

        new AlertDialog.Builder(this)
                .setTitle("Important: Manufacturer Specific Settings")
                .setMessage(message)
                .setPositiveButton("Got it", (dialog, which) -> {
                    try {
                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                        intent.setData(Uri.parse("package:" + getPackageName()));
                        startActivity(intent);
                    } catch (Exception e) {
                        Log.e("MainActivity", "Unable to open app details: " + e.getMessage());
                    }
                })
                .setNegativeButton("Later", null)
                .show();
    }

    /**
     * 全面检查后台运行权限
     * 包括电池优化和厂商特定设置
     */
    private void checkAndRequestBackgroundPermissions() {
        // 使用SharedPreferences记录用户是否已经手动设置过，避免重复提示
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean userHasSetBatteryOptimization = prefs.getBoolean("userHasSetBatteryOptimization", false);
        
        // 先检查电池优化
        boolean isDisabled = isBatteryOptimizationDisabled();
        
        if (!isDisabled && !userHasSetBatteryOptimization) {
            // 只有在未设置且用户未手动设置过的情况下才提示
            requestDisableBatteryOptimization();
        } else if (isDisabled) {
            // 如果电池优化已关闭，记录状态并显示厂商特定设置引导（仅一次）
            prefs.edit().putBoolean("userHasSetBatteryOptimization", true).apply();
            boolean hasShownManufacturerSettings = prefs.getBoolean("hasShownManufacturerSettings", false);

            if (!hasShownManufacturerSettings) {
                showManufacturerSpecificSettings();
                prefs.edit().putBoolean("hasShownManufacturerSettings", true).apply();
            }
        } else {
            // 如果检查显示未关闭，但用户已经设置过，可能是厂商ROM的特殊情况
            Log.d("MainActivity", "电池优化检查显示未关闭，但用户可能已在厂商设置中设置为无限制");
        }
    }

    public String getConnectedBluetoothDeviceName(BluetoothAdapter bluetoothAdapter) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            return "Bluetooth is not enabled";
        }

        final String[] connectedDeviceName = { null };

        bluetoothAdapter.getProfileProxy(null, new BluetoothProfile.ServiceListener() {
            @SuppressLint("MissingPermission")
            @Override
            public void onServiceConnected(int profile, BluetoothProfile proxy) {
                if (profile == BluetoothProfile.GATT) {
                    BluetoothGatt gattServer = (BluetoothGatt) proxy;
                    for (BluetoothDevice device : gattServer.getConnectedDevices()) {
                        if (device.getBondState() == BluetoothDevice.BOND_BONDED && isConnected(device)) {
                            connectedDeviceName[0] = device.getName();
                            break;
                        }
                    }
                    bluetoothAdapter.closeProfileProxy(profile, proxy);
                }
            }

            @Override
            public void onServiceDisconnected(int profile) {
                // Do nothing
            }
        }, BluetoothProfile.GATT);

        return connectedDeviceName[0];
    }

    private boolean isConnected(BluetoothDevice device) {
        try {
            Method method = device.getClass().getMethod("isConnected");
            return (Boolean) method.invoke(device);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private void updateImuViews() {
        if (dataService == null)
            return;

        StringBuilder sb = new StringBuilder();
        sb.append("\uD83C\uDFC3IMU Data : ").append("\n");
        sb.append("Accelerometer: ").append("\n");
        sb.append(Arrays.toString(dataService.accelData)).append("\n");
        sb.append("Gyroscope: ").append("\n");
        sb.append(Arrays.toString(dataService.gyroData)).append("\n");
        sb.append("Magnetometer: ").append("\n");
        sb.append(Arrays.toString(dataService.magData));
        imuTextView.setText(sb.toString());
    }

    private void updateSensorDataView() {
        if (dataService == null)
            return;

        StringBuilder sb = new StringBuilder();

        // Volume Percentage
        sb.append("🔊 Volume Percentage: ").append(
                dataService.volumePercentage >= 0 ? String.format("%.2f", dataService.volumePercentage) + "%" : "N/A")
                .append("\n");

        // Screen On Ratio
        if (dataService.screenOnCounter == 0) {
            sb.append("📱 Screen Status: ").append("OFF").append("\n");
        } else if (dataService.screenOnCounter == 1) {
            sb.append("📱 Screen Status: ").append("ON").append("\n");
        }

        // WiFi Status
        if (dataService.wifiStatus == 1) {
            sb.append("ᯤ WiFi Status: Connected").append("\n");
        } else if (dataService.wifiStatus == 0) {
            sb.append("ᯤ WiFi Status: Unconnected").append("\n");
        } else {
            sb.append("ᯤ WiFi Status: N/A").append("\n");
        }

        // WiFi SSID
        sb.append("ᯤ WiFi SSID: ")
                .append(dataService.connect_wifi_name.equals("N/A") ? "N/A" : dataService.connect_wifi_name)
                .append("\n");

        // Network Traffic in MB
        sb.append("\uD83C\uDF10 Network Traffic in MB: ")
                .append(dataService.networkTrafficInMB >= 0 ? dataService.networkTrafficInMB + " MB" : "N/A")
                .append("\n");

        // Rx Traffic in KB
        sb.append("⬇ Rx Traffic in KB: ").append(dataService.rx_traffic >= 0 ? dataService.rx_traffic + " KB" : "N/A")
                .append("\n");

        // Tx Traffic in KB
        sb.append("⬆ Tx Traffic in KB: ").append(dataService.tx_traffic >= 0 ? dataService.tx_traffic + " KB" : "N/A")
                .append("\n");

        // Step Count in 10s
        sb.append("👣 Step Count: ")
                .append(dataService.stepcount_sensor >= 0 ? dataService.stepcount_sensor : "N/A").append("\n");

        // GPS Latitude and Longitude
        sb.append("📍 GPS Latitude: ")
                .append(Double.isNaN(dataService.gpsLat1) ? "N/A" : String.format("%.6f", dataService.gpsLat1))
                .append("\n");
        sb.append("📍 GPS Longitude: ")
                .append(Double.isNaN(dataService.gpsLon1) ? "N/A" : String.format("%.6f", dataService.gpsLon1))
                .append("\n");

        // Battery Level
        sb.append("🔋 Battery Level: ").append(dataService.battery_level >= 0 ? dataService.battery_level + "%" : "N/A")
                .append("\n");

        // Current App Name
        sb.append("\uD83D\uDCF2 Current App Name: ").append(dataService.appName == null ? "N/A" : dataService.appName)
                .append("\n");

        // Connected Bluetooth Device
        sb.append("ᛒ Paired Bluetooth Device: ")
                .append(dataService.bluetoothDevices == null ? "N/A" : dataService.bluetoothDevices).append("\n");
        sb.append("📡 Nearby Bluetooth Device Count: ").append(dataService.getNearbyBluetoothDevicesCount())
                .append("\n");
        sb.append("🔵 Top 3 Bluetooth Devices: ").append(dataService.getTopThreeBluetoothDevices()).append("\n");
        sensorDataTextView.setTextColor(Color.BLACK);
        sensorDataTextView.setText(sb.toString());
    }

    private boolean isAccessibilityServiceEnabled() {
        ComponentName expectedComponentName = new ComponentName(this, MyAccessibilityService.class);

        String enabledServicesSetting = Settings.Secure.getString(
                getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabledServicesSetting == null)
            return false;

        TextUtils.SimpleStringSplitter colonSplitter = new TextUtils.SimpleStringSplitter(':');
        colonSplitter.setString(enabledServicesSetting);

        while (colonSplitter.hasNext()) {
            String componentNameString = colonSplitter.next();
            ComponentName enabledService = ComponentName.unflattenFromString(componentNameString);
            if (enabledService != null && enabledService.equals(expectedComponentName)) {
                return true;
            }
        }
        return false;
    }

    private void showAccessibilityPermissionDialog() {
        new AlertDialog.Builder(this)
                .setMessage("Please enable Accessibility Service to use the functions of this application normally.")
                .setPositiveButton("open it", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                })
                .setNegativeButton("cancel", null)
                .show();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // 在此处处理传递过来的新 Intent 数据
        if (intent != null) {
            String title = intent.getStringExtra("custom_notification_title");
            String content = intent.getStringExtra("custom_notification_content");
            // 更新 UI 或执行其他操作
            // 处理数据（例如更新 UI）
            if (title != null && content != null) {
                // 示例：显示一个 Toast 或更新界面
                Toast.makeText(this, title + ": " + content, Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Toast.makeText(this, "STOP Collecting.", Toast.LENGTH_LONG).show();
        startButton.setEnabled(true); // 启用开始按钮
        if (handler != null && runnable != null) {
            handler.removeCallbacks(runnable);
        }
        if (dataServiceBound && dataServiceConnection != null) {
            try {
                unbindService(dataServiceConnection);
            } catch (IllegalArgumentException e) {
                Log.w("MainActivity", "DataService already unbound on destroy");
            }
            dataServiceBound = false;
            dataService = null;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_BATTERY_OPTIMIZATION) {
            // 延迟检查，因为设置可能需要时间生效
            handler.postDelayed(() -> {
                // 检查电池优化是否已经关闭
                boolean isDisabled = isBatteryOptimizationDisabled();
                Log.d("MainActivity", "电池优化检查结果: " + (isDisabled ? "已关闭" : "未关闭"));
                
                if (isDisabled) {
                    Toast.makeText(this, "✅ Battery optimization disabled, thank you！", Toast.LENGTH_SHORT).show();
                    Log.i("MainActivity", "电池优化已关闭");
                    
                    // 记录用户已设置
                    SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                    prefs.edit().putBoolean("userHasSetBatteryOptimization", true).apply();

                    // 显示厂商特定设置引导
                    handler.postDelayed(() -> {
                        boolean hasShownManufacturerSettings = prefs.getBoolean("hasShownManufacturerSettings", false);

                        if (!hasShownManufacturerSettings) {
                            showManufacturerSpecificSettings();
                            prefs.edit().putBoolean("hasShownManufacturerSettings", true).apply();
                        }
                    }, 500);
                } else {
                    // 再次检查，因为某些厂商ROM可能需要更长时间
                    handler.postDelayed(() -> {
                        boolean isDisabledRetry = isBatteryOptimizationDisabled();
                        if (isDisabledRetry) {
                            Toast.makeText(this, "✅ Battery optimization disabled, thank you！", Toast.LENGTH_SHORT).show();
                            Log.i("MainActivity", "电池优化已关闭（延迟检查）");
                            
                            // 记录用户已设置
                            SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
                            prefs.edit().putBoolean("userHasSetBatteryOptimization", true).apply();
                        } else {
                            // 如果用户已经在厂商设置中设置为无限制，这个检查可能无法检测到
                            // 不再显示警告toast，只在日志中记录
                            Log.w("MainActivity", "标准Android电池优化检查显示未关闭，但如果已在厂商设置中设置为无限制，可以忽略此提示");
                        }
                    }, 2000); // 延迟2秒再次检查
                }
            }, 500); // 延迟500ms检查，等待设置生效
        }
    }

    protected void onResume() {
        super.onResume();

        // 每次恢复时检查电池优化状态
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            boolean isDisabled = isBatteryOptimizationDisabled();
            if (isDisabled) {
                Log.d("MainActivity", "电池优化状态: 已关闭 ✓");
            } else {
                Log.w("MainActivity", "电池优化状态: 开启中 (可能影响后台运行)");
                Log.w("MainActivity", "提示: 如果已在厂商设置中设置为无限制，此检查可能无法检测到，请忽略此警告");
            }
        }
    }

    /**
     * 清除所有缓存的CSV数据（包括sensor.csv、IMU.csv及其备份文件）
     */
    private void clearAllCachedData() {
        new AlertDialog.Builder(this)
                .setTitle("Confirm Clear")
                .setMessage("Are you sure you want to clear all cached sensor data?\nThis will delete all unuploaded CSV files and backup files。\nData collection will also be stopped。")
                .setPositiveButton("Confirm", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        // 先停止数据收集（相当于点击Stop按钮）
                        stopDataCollection();
                        startButton.setEnabled(true); // 启用开始按钮
                        // 然后删除文件
                        deleteAllCsvFiles();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /**
     * 删除所有CSV文件和备份文件
     */
    private void deleteAllCsvFiles() {
        new Thread(() -> {
            try {
                java.io.File dir = new java.io.File(android.os.Environment.getExternalStorageDirectory(), "0.Mobibox");
                if (!dir.exists()) {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Data directory does not exist", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                int deletedCount = 0;
                java.io.File[] files = dir.listFiles();
                if (files != null) {
                    for (java.io.File file : files) {
                        String fileName = file.getName();
                        // 删除sensor.csv、IMU.csv及其所有备份文件
                        if (fileName.startsWith("sensor") && fileName.endsWith(".csv")) {
                            if (file.delete()) {
                                deletedCount++;
                                Log.i("MainActivity", "已删除: " + fileName);
                            }
                        } else if (fileName.startsWith("IMU") && fileName.endsWith(".csv")) {
                            if (file.delete()) {
                                deletedCount++;
                                Log.i("MainActivity", "已删除: " + fileName);
                            }
                        }
                    }
                }

                final int finalCount = deletedCount;
                runOnUiThread(() -> {
                    if (finalCount > 0) {
                        Toast.makeText(MainActivity.this, "Cleared " + finalCount + "  files", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(MainActivity.this, "No files found to clear", Toast.LENGTH_SHORT).show();
                    }
                });
            } catch (Exception e) {
                Log.e("MainActivity", "清除数据失败: " + e.getMessage());
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Clear failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }
}
