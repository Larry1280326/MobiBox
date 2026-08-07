package com.example.mobibox;

import android.Manifest;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.content.Intent;
import android.os.Environment;
import android.provider.Settings;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.content.SharedPreferences;

import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import android.util.Log;

import com.example.mobibox.network.HttpApiClient;

import java.util.Arrays;
import java.util.List;

public class AuthorizationActivity extends AppCompatActivity {

    // 定义视图组件作为类成员变量
    private EditText inputText;
    private Button intomainButton;
    private SharedPreferences prefs;
    public String userId;

    private int currentPermissionIndex = 0;
    private ActivityResultLauncher<String> standardPermissionLauncher;
    private ActivityResultLauncher<Intent> manageStorageLauncher;

    // 修改：分离普通权限和特殊权限
    private final List<String> normalPermissions = Arrays.asList(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACTIVITY_RECOGNITION,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.BLUETOOTH_SCAN
    );

    // 特殊权限单独处理
    private boolean needsBackgroundLocation = true;
    private boolean needsManageStorage = true;
    private boolean backgroundLocationRequested = false;
    private boolean manageStorageRequested = false;

    private void checkAndRequestPermissions() {
        // 先检查普通权限
        if (currentPermissionIndex < normalPermissions.size()) {
            String currentPermission = normalPermissions.get(currentPermissionIndex);
            if (isPermissionGranted(currentPermission)) {
                currentPermissionIndex++;
                checkAndRequestPermissions();
            } else {
                requestSpecificPermission(currentPermission);
            }
        }
        // 普通权限完成后，检查Background Location Permission
        else if (needsBackgroundLocation && !backgroundLocationRequested) {
            requestBackgroundLocationIfNeeded();
        }
        // 最后检查存储管理权限
        else if (needsManageStorage && !manageStorageRequested) {
            requestManageStorageIfNeeded();
        }
        // 所有权限处理完成
        else {
            Log.i("AuthorizationActivity", "所有权限检查完成");
        }
    }

    private boolean isPermissionGranted(String permission) {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED;
    }

    // 修改：添加Background Location Permission的特殊处理
    private void requestBackgroundLocationIfNeeded() {
        backgroundLocationRequested = true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 需要先有前台位置权限才能请求Background Location Permission
            if (isPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
                    isPermissionGranted(Manifest.permission.ACCESS_COARSE_LOCATION)) {

                if (!isPermissionGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                    // 显示解释对话框
                    new AlertDialog.Builder(this)
                            .setTitle("Background Location Permission")
                            .setMessage("To provide location services in background, please allow app to always access location")
                            .setPositiveButton("Settings", (dialog, which) -> {
                                standardPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
                            })
                            .setNegativeButton("Skip", (dialog, which) -> {
                                Log.w("AuthorizationActivity", "用户SkipBackground Location Permission");
                                checkAndRequestPermissions();
                            })
                            .show();
                    return;
                }
            } else {
                Log.w("AuthorizationActivity", "前台位置权限未获取，SkipBackground Location Permission");
            }
        }

        checkAndRequestPermissions();
    }

    // 修改：添加存储管理权限的特殊处理
    private void requestManageStorageIfNeeded() {
        manageStorageRequested = true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                // 显示解释对话框
                new AlertDialog.Builder(this)
                        .setTitle("File Management Permission")
                        .setMessage("App needs file management permission to work properly, please allow in settings")
                        .setPositiveButton("Settings", (dialog, which) -> {
                            requestManageExternalStorage();
                        })
                        .setNegativeButton("Skip", (dialog, which) -> {
                            Log.w("AuthorizationActivity", "用户SkipFile Management Permission");
                            checkAndRequestPermissions();
                        })
                        .show();
                return;
            }
        }

        checkAndRequestPermissions();
    }

    @RequiresApi(api = Build.VERSION_CODES.R)
    private void requestManageExternalStorage() {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    .setData(Uri.parse("package:" + getPackageName()));
            manageStorageLauncher.launch(intent);
        } catch (Exception e) {
            Log.e("AuthorizationActivity", "无法打开File Management PermissionSettings", e);
            Toast.makeText(this, "Unable to open settings page", Toast.LENGTH_SHORT).show();
            checkAndRequestPermissions();
        }
    }

    private void requestSpecificPermission(String permission) {
        // 修改：添加权限解释
        String explanation = getPermissionExplanation(permission);
        if (!explanation.isEmpty()) {
            new AlertDialog.Builder(this)
                    .setTitle("Permission Explanation")
                    .setMessage(explanation)
                    .setPositiveButton("Allow", (dialog, which) -> {
                        standardPermissionLauncher.launch(permission);
                    })
                    .setNegativeButton("Deny", (dialog, which) -> {
                        Log.w("AuthorizationActivity", "用户Deny权限: " + permission);
                        handlePermissionResult(false);
                    })
                    .show();
        } else {
            standardPermissionLauncher.launch(permission);
        }
    }

    // 修改：添加权限解释方法
    private String getPermissionExplanation(String permission) {
        switch (permission) {
            case Manifest.permission.ACCESS_FINE_LOCATION:
            case Manifest.permission.ACCESS_COARSE_LOCATION:
                return "App needs location permission to provide location services";
            case Manifest.permission.POST_NOTIFICATIONS:
                return "App needs notification permission to notify you of important information";
            case Manifest.permission.ACTIVITY_RECOGNITION:
                return "App needs activity recognition permission to detect your movement status";
            case Manifest.permission.BLUETOOTH_CONNECT:
                return "App needs Bluetooth permission to connect to Bluetooth devices";
            case Manifest.permission.BLUETOOTH_SCAN:
                return "App needs Bluetooth permission to scan for Bluetooth devices";
            default:
                return ""; // 不显示对话框，直接请求
        }
    }

    // 修改：改进权限结果处理
    private void handlePermissionResult(boolean granted) {
        if (!granted) {
            String currentPermission = normalPermissions.get(currentPermissionIndex);
            Log.w("AuthorizationActivity", "权限被Deny: " + currentPermission);

            // 检查是否是关键权限
            if (isCriticalPermission(currentPermission)) {
                showCriticalPermissionDialog(currentPermission);
                return;
            }
        }

        currentPermissionIndex++;
        checkAndRequestPermissions();
    }

    // 修改：添加关键权限检查
    private boolean isCriticalPermission(String permission) {
        return permission.equals(Manifest.permission.ACCESS_NETWORK_STATE) ||
                permission.equals(Manifest.permission.ACCESS_WIFI_STATE)||permission.equals(Manifest.permission.BLUETOOTH_CONNECT)
                ||permission.equals(Manifest.permission.POST_NOTIFICATIONS)||permission.equals(Manifest.permission.ACCESS_FINE_LOCATION)||
                permission.equals(Manifest.permission.ACCESS_COARSE_LOCATION);
    }

    // 修改：添加关键权限对话框
    private void showCriticalPermissionDialog(String permission) {
        new AlertDialog.Builder(this)
                .setTitle("Important Permission")
                .setMessage("This permission is essential for the app, please enable it manually in settings")
                .setPositiveButton("Go to Settings", (dialog, which) -> {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                })
                .setNegativeButton("Continue", (dialog, which) -> {
                    currentPermissionIndex++;
                    checkAndRequestPermissions();
                })
                .show();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_authorization);

        // 初始化SharedPreferences
        prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        boolean isFirstTime = prefs.getBoolean("isFirstTime", true);
        String savedUserId = prefs.getString("userId", "");
        userId = prefs.getString("userId", "");

        if (!isFirstTime && !savedUserId.isEmpty()) {
            startMainActivity();
            return; // 修改：添加return避免重复初始化
        }

        // 初始化视图组件
        inputText = findViewById(R.id.input_text);
        intomainButton = findViewById(R.id.intomain_button);

        // Settings接受按钮的点击事件
        intomainButton.setOnClickListener(v -> {
            String userIdInput = inputText.getText().toString().trim();
            if (!userIdInput.isEmpty()) {
                // 存储用户ID并标记为非首次启动
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean("isFirstTime", false);
                editor.putString("userId", userIdInput);
                editor.apply();

                // 初始化HttpApiClient和注册用户
                HttpApiClient.init(userIdInput);

                new Thread(() -> {
                    try {
                        boolean success = HttpApiClient.getInstance().registerUser(userIdInput);
                        if (success) {
                            Log.i("AuthorizationActivity", "User registered successfully: " + userIdInput);
                        } else {
                            Log.e("AuthorizationActivity", "Failed to register user: " + userIdInput);
                        }
                    } catch (Exception e) {
                        Log.e("AuthorizationActivity", "Failed to register user", e);
                    }
                }).start();

                // 跳转到主界面
                startActivity(new Intent(this, MainActivity.class));
                finish();
            } else {
                Toast.makeText(this, "Please enter user ID！", Toast.LENGTH_SHORT).show();
            }
        });

        // 修改：改进权限请求回调处理
        standardPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                result -> handlePermissionResult(result)
        );

        // 文件管理特殊权限回调
        manageStorageLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    // 检查权限是否已授予
                    boolean granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.R ||
                            Environment.isExternalStorageManager();
                    Log.i("AuthorizationActivity", "File Management Permission结果: " + granted);
                    checkAndRequestPermissions();
                }
        );

        // 开始权限检查流程
        checkAndRequestPermissions();
    }

    /**
     * 启动主界面并关闭当前界面
     */
    private void startMainActivity() {
        String savedUserId = prefs.getString("userId", "unknown");
        HttpApiClient.init(savedUserId);
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}