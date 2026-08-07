package com.example.mobibox;

import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.SharedPreferences;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.util.Log;
import android.content.pm.PackageManager;

// 这个目前也用不上，辅助功能虽然能非常准确的get到正在使用的应用，然而每次都会强行跳转前台

public class MyAccessibilityService extends AccessibilityService {

//    @Override
//    public void onServiceConnected() {
//        // 保持服务存活的关键设置
//        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
//        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
//        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
//        info.flags = AccessibilityServiceInfo.DEFAULT
//                | AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
//
//        // 重要：设置超时时间为0表示永不超时
//        info.notificationTimeout = 0;
//
//        setServiceInfo(info);
//    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 处理无障碍事件
    }

    @Override
    public void onInterrupt() {
    }


}


