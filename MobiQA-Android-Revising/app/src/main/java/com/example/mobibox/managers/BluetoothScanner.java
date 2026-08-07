package com.example.mobibox.managers;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.example.mobibox.Constants;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages Bluetooth LE scanning for nearby devices.
 * Handles device discovery, name resolution, and periodic scanning.
 */
public class BluetoothScanner {
    private static final String TAG = "BluetoothScanner";
    private static final String PREFS_DEVICE_NAMES = "BluetoothDeviceNames";

    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;

    private boolean isScanning = false;
    private final Handler scanHandler = new Handler(Looper.getMainLooper());

    // Device data storage
    private final List<BluetoothScanResult> nearbyDevices =
            Collections.synchronizedList(new java.util.ArrayList<>());
    private final Map<String, String> bondedNameByAddress = new ConcurrentHashMap<>();
    private final Map<String, String> deviceNameCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> deviceNameRetryCount = new ConcurrentHashMap<>();
    private final List<String> unknownDeviceQueue =
            Collections.synchronizedList(new java.util.ArrayList<>());

    // Singleton instance
    private static volatile BluetoothScanner instance;

    /**
     * Result class for Bluetooth scan results.
     */
    public static class BluetoothScanResult {
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

    /**
     * Get the singleton instance of BluetoothScanner.
     * @param context Application context
     * @return The singleton instance
     */
    public static BluetoothScanner getInstance(Context context) {
        if (instance == null) {
            synchronized (BluetoothScanner.class) {
                if (instance == null) {
                    instance = new BluetoothScanner(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private BluetoothScanner(Context context) {
        this.context = context;
        this.bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        init();
    }

    private void init() {
        if (bluetoothAdapter == null) {
            Log.w(TAG, "Bluetooth adapter not available");
            return;
        }

        if (!bluetoothAdapter.isEnabled()) {
            Log.w(TAG, "Bluetooth is not enabled");
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
            if (bluetoothLeScanner != null) {
                Log.i(TAG, "Bluetooth LE scanner initialized successfully");
            } else {
                Log.e(TAG, "Bluetooth LE scanner initialization failed");
            }
        }

        loadBondedDevices();
    }

    /**
     * Load names of bonded (paired) devices for fallback name resolution.
     */
    private void loadBondedDevices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.w(TAG, "Missing BLUETOOTH_CONNECT permission, skipping bonded devices");
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
                            Log.w(TAG, "Security exception getting device name: " + d.getAddress());
                        }
                    }
                }
                Log.i(TAG, "Loaded " + count + " bonded device names");
            }
        } catch (SecurityException e) {
            Log.w(TAG, "Security exception reading bonded devices: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Error reading bonded devices: " + e.getMessage());
        }
    }

    // =====================
    // Scanning Methods
    // =====================

    /**
     * Start a periodic Bluetooth scan.
     */
    public void startPeriodicScan() {
        scanHandler.post(new Runnable() {
            @Override
            public void run() {
                if (bluetoothAdapter != null && bluetoothAdapter.isEnabled()) {
                    startScan();
                }
                scanHandler.postDelayed(this, Constants.BLUETOOTH_SCAN_INTERVAL_MS);
            }
        });
    }

    /**
     * Stop periodic Bluetooth scanning.
     */
    public void stopPeriodicScan() {
        scanHandler.removeCallbacksAndMessages(null);
        stopScan();
    }

    /**
     * Start a single Bluetooth scan.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void startScan() {
        if (bluetoothLeScanner == null || isScanning) {
            return;
        }

        if (!checkScanPermissions()) {
            return;
        }

        try {
            ScanSettings.Builder settingsBuilder = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                    .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                    .setReportDelay(0);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                settingsBuilder
                        .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                        .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT);
            }

            ScanSettings settings = settingsBuilder.build();
            nearbyDevices.clear();
            bluetoothLeScanner.startScan(null, settings, scanCallback);
            isScanning = true;

            Log.i(TAG, "Started Bluetooth scan (low power mode)");

            scanHandler.postDelayed(() -> {
                stopScan();
                retryUnknownDevices();
            }, Constants.BLUETOOTH_SCAN_DURATION_MS);

        } catch (SecurityException e) {
            Log.e(TAG, "Security exception starting scan: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Failed to start scan: " + e.getMessage());
        }
    }

    /**
     * Stop the current Bluetooth scan.
     */
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public void stopScan() {
        if (bluetoothLeScanner != null && isScanning) {
            try {
                bluetoothLeScanner.stopScan(scanCallback);
                isScanning = false;
                Log.i(TAG, "Stopped scan, found " + nearbyDevices.size() + " devices");
            } catch (SecurityException e) {
                Log.e(TAG, "Security exception stopping scan: " + e.getMessage());
            }
        }
    }

    private boolean checkScanPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED ||
                    context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)
                            != PackageManager.PERMISSION_GRANTED ||
                    context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                            != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Missing required Bluetooth permissions");
                return false;
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "Missing location permission for Bluetooth scan");
                return false;
            }
        }
        return true;
    }

    // =====================
    // Scan Callback
    // =====================

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);

            BluetoothDevice device = result.getDevice();
            int rssi = result.getRssi();
            String name = resolveBluetoothName(device, result);
            String address = device.getAddress();

            // Filter out weak signals (RSSI < -80dBm)
            if (rssi > -80) {
                BluetoothScanResult scanResult = new BluetoothScanResult(name, address, rssi);

                synchronized (nearbyDevices) {
                    boolean updated = false;
                    for (int i = 0; i < nearbyDevices.size(); i++) {
                        BluetoothScanResult existing = nearbyDevices.get(i);
                        if (existing.address.equals(address)) {
                            if (rssi > existing.rssi) {
                                nearbyDevices.set(i, scanResult);
                            }
                            updated = true;
                            break;
                        }
                    }

                    if (!updated) {
                        nearbyDevices.add(scanResult);
                    }
                }

                Log.d(TAG, "Found device: " + name + " (" + address + ") RSSI: " + rssi);
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
            Log.e(TAG, "Scan failed with error code: " + errorCode);
            isScanning = false;
        }
    };

    // =====================
    // Name Resolution
    // =====================

    /**
     * Resolve the display name for a Bluetooth device.
     */
    private String resolveBluetoothName(BluetoothDevice device, ScanResult result) {
        String address = device != null ? device.getAddress() : null;

        // Check cache first
        if (!TextUtils.isEmpty(address) && deviceNameCache.containsKey(address)) {
            String cachedName = deviceNameCache.get(address);
            if (!TextUtils.isEmpty(cachedName) && !cachedName.equals("Unknown")) {
                return cachedName;
            }
        }

        String resolvedName = null;

        try {
            // 1. Try ScanRecord getDeviceName
            if (result != null && result.getScanRecord() != null) {
                resolvedName = result.getScanRecord().getDeviceName();
                if (!TextUtils.isEmpty(resolvedName)) {
                    cacheAndSaveDeviceName(address, resolvedName);
                    return resolvedName;
                }

                // 2. Try parsing from raw scan record
                byte[] scanRecordBytes = result.getScanRecord().getBytes();
                resolvedName = parseDeviceNameFromScanRecord(scanRecordBytes);
                if (!TextUtils.isEmpty(resolvedName)) {
                    cacheAndSaveDeviceName(address, resolvedName);
                    return resolvedName;
                }
            }

            // 3. Try device.getName()
            if (device != null && hasBluetoothConnectPermission()) {
                try {
                    resolvedName = device.getName();
                    if (!TextUtils.isEmpty(resolvedName)) {
                        cacheAndSaveDeviceName(address, resolvedName);
                        return resolvedName;
                    }
                } catch (SecurityException e) {
                    Log.w(TAG, "Security exception getting device name");
                }
            }

            // 4. Try bonded devices
            if (!TextUtils.isEmpty(address)) {
                String bondedName = bondedNameByAddress.get(address);
                if (!TextUtils.isEmpty(bondedName)) {
                    cacheAndSaveDeviceName(address, bondedName);
                    return bondedName;
                }
            }

            // 5. Try persistent storage
            String persistentName = loadDeviceNameFromStorage(address);
            if (!TextUtils.isEmpty(persistentName)) {
                deviceNameCache.put(address, persistentName);
                return persistentName;
            }

            // 6. Try vendor identification from MAC
            String vendorName = getVendorNameFromMac(address);
            if (!TextUtils.isEmpty(vendorName)) {
                return vendorName;
            }

        } catch (Exception e) {
            Log.w(TAG, "Error resolving device name: " + e.getMessage());
        }

        // Add to retry queue
        if (!TextUtils.isEmpty(address)) {
            addToUnknownDeviceQueue(address);
        }

        return "Unknown";
    }

    private void cacheAndSaveDeviceName(String address, String name) {
        if (!TextUtils.isEmpty(address) && !TextUtils.isEmpty(name)) {
            deviceNameCache.put(address, name);
            saveDeviceNameToStorage(address, name);
        }
    }

    private String parseDeviceNameFromScanRecord(byte[] scanRecord) {
        if (scanRecord == null || scanRecord.length == 0) {
            return null;
        }

        try {
            int pos = 0;
            while (pos < scanRecord.length - 1) {
                int length = scanRecord[pos] & 0xFF;
                if (length == 0) break;

                int type = scanRecord[pos + 1] & 0xFF;

                // 0x08 = short name, 0x09 = complete name
                if (type == 0x09 || type == 0x08) {
                    byte[] nameBytes = new byte[length - 1];
                    System.arraycopy(scanRecord, pos + 2, nameBytes, 0, length - 1);
                    String name = new String(nameBytes, "UTF-8").trim();
                    if (!TextUtils.isEmpty(name)) {
                        return name;
                    }
                }

                pos += length + 1;
            }
        } catch (Exception e) {
            Log.w(TAG, "Error parsing scan record: " + e.getMessage());
        }
        return null;
    }

    private boolean hasBluetoothConnectPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                    == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    // =====================
    // Storage Methods
    // =====================

    private void saveDeviceNameToStorage(String address, String name) {
        if (TextUtils.isEmpty(address) || TextUtils.isEmpty(name)) {
            return;
        }
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_DEVICE_NAMES, Context.MODE_PRIVATE);
            prefs.edit().putString(address, name).apply();
        } catch (Exception e) {
            Log.e(TAG, "Error saving device name: " + e.getMessage());
        }
    }

    private String loadDeviceNameFromStorage(String address) {
        if (TextUtils.isEmpty(address)) {
            return null;
        }
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_DEVICE_NAMES, Context.MODE_PRIVATE);
            return prefs.getString(address, null);
        } catch (Exception e) {
            Log.e(TAG, "Error loading device name: " + e.getMessage());
            return null;
        }
    }

    // =====================
    // Vendor Identification
    // =====================

    private String getVendorNameFromMac(String address) {
        if (TextUtils.isEmpty(address) || address.length() < 8) {
            return null;
        }

        try {
            String oui = address.substring(0, 8).replace(":", "").toUpperCase();

            switch (oui) {
                // Apple
                case "00036": case "001D4F": case "0025BC": case "28E14C":
                case "9C208E": case "E0ACCB": case "98FE94": case "A0999B":
                case "64A5C3": case "E8040B": case "7C0191": case "F0DCE2":
                    return "Apple Device";

                // Samsung
                case "005056": case "001632": case "0018AF": case "1C232C":
                case "E840F2": case "0C1420": case "342387": case "6C2F2C":
                    return "Samsung Device";

                // Xiaomi
                case "1034B6": case "342476": case "640980": case "787B8A":
                case "7CE9D3": case "C460F6": case "F0B429": case "F8A45F":
                    return "Xiaomi Device";

                // Huawei
                case "005A13": case "0CF839": case "2CE4F7": case "48DB50":
                case "5CF8A1": case "68DB54": case "C8F230": case "E06995":
                    return "Huawei Device";

                // Google
                case "7C1DD9": case "002586": case "5CF370": case "F4F5E8":
                    return "Google Device";

                // Sony
                case "000C9B": case "001323": case "0016CF": case "0019C5":
                    return "Sony Device";

                // JBL
                case "5C6968": case "0C728E":
                    return "JBL Speaker";

                // Bose
                case "2C411F": case "B8D5CB":
                    return "Bose Device";

                // Beats
                case "8CF5A3": case "C8690A":
                    return "Beats Device";

                default:
                    return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error identifying vendor: " + e.getMessage());
            return null;
        }
    }

    // =====================
    // Unknown Device Queue
    // =====================

    private void addToUnknownDeviceQueue(String address) {
        if (!TextUtils.isEmpty(address) && !unknownDeviceQueue.contains(address)) {
            unknownDeviceQueue.add(address);
        }
    }

    private void retryUnknownDevices() {
        if (unknownDeviceQueue.isEmpty()) {
            return;
        }

        Log.d(TAG, "Retrying " + unknownDeviceQueue.size() + " unknown devices");
        // Could implement additional retry logic here
    }

    // =====================
    // Public Getters
    // =====================

    /**
     * Get the number of nearby devices found.
     */
    public int getNearbyDeviceCount() {
        synchronized (nearbyDevices) {
            return nearbyDevices.size();
        }
    }

    /**
     * Get the top three Bluetooth devices by signal strength.
     * Format: "Name|RSSI|Address;Name|RSSI|Address;..."
     */
    public String getTopThreeBluetoothDevices() {
        synchronized (nearbyDevices) {
            if (nearbyDevices.isEmpty()) {
                return "N/A";
            }

            // Sort by RSSI descending
            Collections.sort(nearbyDevices, (a, b) -> Integer.compare(b.rssi, a.rssi));

            StringBuilder result = new StringBuilder();
            int count = Math.min(3, nearbyDevices.size());

            for (int i = 0; i < count; i++) {
                BluetoothScanResult device = nearbyDevices.get(i);
                if (i > 0) {
                    result.append(";");
                }

                String deviceName = getBestDeviceName(device);
                result.append(String.format("%s|%d|%s", deviceName, device.rssi, device.address));
            }

            return result.toString();
        }
    }

    private String getBestDeviceName(BluetoothScanResult device) {
        // Use resolved name if valid
        if (!TextUtils.isEmpty(device.name) && !"Unknown".equals(device.name)) {
            return device.name;
        }

        // Try bonded devices
        String bondedName = bondedNameByAddress.get(device.address);
        if (!TextUtils.isEmpty(bondedName)) {
            return bondedName;
        }

        // Generate friendly name from MAC address
        return generateFriendlyName(device.address);
    }

    private String generateFriendlyName(String address) {
        if (TextUtils.isEmpty(address)) {
            return "Unknown Device";
        }

        String[] parts = address.split(":");
        if (parts.length >= 6) {
            String lastTwo = parts[4] + parts[5];
            return "Device-" + lastTwo.toUpperCase();
        }

        return "Unknown Device";
    }

    /**
     * Get all nearby devices.
     */
    public List<BluetoothScanResult> getNearbyDevices() {
        return nearbyDevices;
    }

    /**
     * Check if currently scanning.
     */
    public boolean isScanning() {
        return isScanning;
    }
}