# Upload 函数上传数据报告

## 概述

本报告详细说明了 `upload` 和 `uploadimu` 两个函数分别上传的数据内容和格式。

---

## 1. upload 函数（sensor.csv 上传）

### 基本信息
- **函数调用位置**: `DataService.java` 第333行
- **上传URL**: `http://120.25.178.24:5000/upload`
- **数据源文件**: `0.MobiBox/sensor.csv`
- **上传频率**: 每3次写入周期（约30秒）后尝试上传
- **计数器**: 使用 `counter` 变量（与IMU共享计数器）
- **文件大小限制**: 超过5MB时执行文件轮转

### 上传数据格式

通过 `uploadCSV` 函数上传，数据以JSON格式发送：
```json
{
  "user_id": "用户ID",
  "csv_content": "CSV文件内容"
}
```

### CSV文件数据结构

`sensor.csv` 文件包含以下字段（按顺序）：

| 序号 | 字段名称 | 数据类型 | 说明 |
|------|---------|---------|------|
| 1 | timestamp | String | UTC+8时间戳 |
| 2 | volumePercentage | Number | 音量百分比 |
| 3 | screenOnCounter | Number | 屏幕开启计数 |
| 4 | wifiStatus | Number/String | WiFi连接状态 |
| 5 | connect_wifi_name | String | 连接的WiFi名称（CSV转义） |
| 6 | networkTrafficInMB | Number | 网络总流量（MB，保留2位小数） |
| 7 | rx_traffic | Number | 接收流量（KB） |
| 8 | tx_traffic | Number | 发送流量（KB） |
| 9 | stepcount_sensor | Number | 步数增量 |
| 10 | latStr | String | 纬度坐标 |
| 11 | lonStr | String | 经度坐标 |
| 12 | battery_level | Number | 电池电量百分比 |
| 13 | appNameForCsv | String | 前台应用名称（CSV转义，ScreenOff或N/A时为空） |
| 14 | bluetoothDevices | String | 蓝牙设备信息（CSV转义） |
| 15 | currentAddress | String | 当前地址（CSV转义） |
| 16 | currentPoi | String | 当前POI（兴趣点，CSV转义） |
| 17 | nearbyBluetoothCount | Number | 扫描到的附近蓝牙设备总数 |
| 18 | topBluetoothDevices | String | 前3个信号最强的蓝牙设备（格式：名称\|RSSI\|MAC地址;名称\|RSSI\|MAC地址;...） |

### 数据收集说明

- **时间戳**: 使用UTC+8时区格式
- **网络流量**: 计算时间段内的增量（接收+发送）
- **步数**: 计算时间段内的增量
- **应用名称**: 当应用名称为"ScreenOff"或"N/A"时，该字段为空字符串
- **蓝牙设备**: 包含详细的蓝牙扫描数据，包括设备名称、RSSI值和MAC地址

### 上传逻辑

1. 检查文件大小，超过5MB则先执行文件轮转
2. 读取CSV文件内容
3. 如果内容不为空，调用 `uploadCSV` 函数上传
4. 上传成功后：
   - 重置 `counter = 0`
   - 清空文件内容
5. 上传失败后：
   - 设置 `counter = 10`（约20秒后重试）
   - 显示Toast通知用户
   - 如果文件超过5MB，执行轮转

---

## 2. uploadimu 函数（IMU.csv 上传）

### 基本信息
- **函数调用位置**: `DataService.java` 第409行
- **上传URL**: `http://120.25.178.24:5000/upload_imu`
- **数据源文件**: `0.MobiBox/IMU.csv`
- **上传频率**: 每3次写入周期（约30秒）后尝试上传
- **计数器**: 使用独立的 `imuCounter` 变量（与sensor.csv分离）
- **文件大小限制**: 超过5MB时执行文件轮转

### 上传数据格式

通过 `uploadCSV` 函数上传，数据以JSON格式发送：
```json
{
  "user_id": "用户ID",
  "csv_content": "CSV文件内容"
}
```

### CSV文件数据结构

`IMU.csv` 文件包含以下字段（按顺序）：

| 序号 | 字段名称 | 数据类型 | 说明 |
|------|---------|---------|------|
| 1 | timestamp | String | UTC+8时间戳 |
| 2 | accel_x | Number | 加速度计X轴数据（保留5位小数） |
| 3 | accel_y | Number | 加速度计Y轴数据（保留5位小数） |
| 4 | accel_z | Number | 加速度计Z轴数据（保留5位小数） |
| 5 | gyro_x | Number | 陀螺仪X轴数据（保留5位小数） |
| 6 | gyro_y | Number | 陀螺仪Y轴数据（保留5位小数） |
| 7 | gyro_z | Number | 陀螺仪Z轴数据（保留5位小数） |
| 8 | mag_x | Number | 磁力计X轴数据（保留5位小数） |
| 9 | mag_y | Number | 磁力计Y轴数据（保留5位小数） |
| 10 | mag_z | Number | 磁力计Z轴数据（保留5位小数） |

### 数据收集说明

- **时间戳**: 使用UTC+8时区格式
- **传感器数据**:
  - **改进前**: 需要加速度计、陀螺仪和磁力计三种传感器数据都更新时才写入一条记录
  - **改进后**: 只要有一个传感器更新就立即记录一条数据，其他传感器使用上次的值
  - **优势**: 最大化数据收集频率，接近传感器的实际更新频率（25Hz）
- **数据精度**: 所有传感器数据保留5位小数
- **数据收集频率**:
  - **理论设置**: 传感器注册频率为40,000微秒（40毫秒），理论上应为25Hz
  - **改进前实际频率**: 约每秒1条数据（1Hz）- 受三种传感器同步更新的限制
  - **改进后期望频率**: 接近25Hz（接近传感器注册频率）- 每个传感器更新都会记录
  - **改进说明**:
    1. **最大化频率**: 只要有一个传感器更新就立即记录，不等待其他传感器同步
    2. **数据策略**: 当前更新的传感器使用新值，其他传感器使用上次的值
    3. **写入周期**: `writeData()` 函数每10秒执行一次，将 `sensorDataList` 中累积的数据批量写入文件
    4. **系统限制**: Android系统在后台运行时会对传感器更新频率进行节流，但改进后频率应能接近理论值
    5. **预期效果**: 每10秒写入周期内，`sensorDataList` 中应能累积约250条数据（25Hz × 10秒），频率提升约25倍

### 数据格式示例

```
2024-01-01 12:00:00.000,0.12345,0.23456,9.81000,0.00123,0.00234,0.00345,20.12345,30.23456,40.34567
```

### 上传逻辑（独立重试机制）

#### 触发条件
- `imuCounter >= 3`（硬编码阈值）
- 每个写入周期约10秒，即约30秒触发一次上传

#### 重试机制（独立于sensor.csv）
- **成功时**: 重置 `imuCounter = 0`
- **失败时**: 设置 `imuCounter = 10`
  - 下次上传将在约20秒后触发
  - 显示Toast通知用户
  - 如果文件超过5MB，执行文件轮转

#### 流程图
```
┌─────────────────────────────────────────────────────────────┐
│                   IMU Upload Flow                           │
├─────────────────────────────────────────────────────────────┤
│  1. imuCounter increments every 10s                        │
│  2. When imuCounter >= 3 → trigger upload                  │
│  3. Check file size (5MB max)                              │
│     - If > 5MB → rotate file, skip this upload            │
│  4. Read CSV content and upload                            │
│  5. On success: clear file, reset counter                  │
│  6. On failure:                                             │
│     - Set imuCounter = 10 → retry in ~20 seconds          │
│     - Show Toast + notification                             │
│     - Rotate file if > 5MB                                  │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. 共同的上传机制（uploadCSV函数）

### 函数签名
```java
private void uploadCSV(String userId, String csvContent, String URL, UploadCallback callback)
```

### 技术实现

- **HTTP客户端**: 使用OkHttp库
- **超时设置**:
  - 连接超时：15秒
  - 写入超时：15秒
  - 读取超时：15秒
- **请求方法**: POST
- **Content-Type**: `application/json; charset=utf-8`
- **请求体格式**: JSON对象，包含 `user_id` 和 `csv_content` 字段

### 响应处理

- **成功响应**: 服务器返回 `{"status": "success"}` 时调用 `onSuccess()` 回调
- **失败响应**:
  - 网络错误：记录错误信息并调用 `onFailure()` 回调
  - 服务器错误：解析错误消息并调用 `onFailure()` 回调
  - HTTP错误：记录HTTP状态码和响应体

---

## 4. 数据收集时间线

### 数据写入周期
- 数据每10秒写入一次（通过 `runnable` 定时执行）
- `counter` 和 `imuCounter` 同时递增
- sensor.csv触发条件: `counter >= 3`（约30秒）
- IMU.csv触发条件: `imuCounter >= 3`（约30秒）

### 文件管理
- **文件位置**: `/0.MobiBox/` 目录下
- **文件轮转**: 当文件大小超过5MB时，自动创建新文件
- **文件清空**: 上传成功后清空文件内容，准备下次数据收集

---

## 5. 配置常量

### 变量说明（DataService.java）

| 变量名 | 类型 | 说明 |
|--------|------|------|
| `counter` | int | sensor.csv上传计数器 |
| `imuCounter` | int | IMU.csv上传计数器（独立于counter） |

### 上传阈值（硬编码）

| 阈值 | 值 | 说明 |
|--------|-----|------|
| 正常触发阈值 | 3 | 约每30秒触发一次上传 |
| 重试阈值 | 10 | 失败后约20秒重试 |

---

## 6. 总结对比

| 特性 | upload (sensor.csv) | uploadimu (IMU.csv) |
|------|---------------------|---------------------|
| **数据类别** | 环境传感器和系统信息 | 运动传感器数据 |
| **数据频率** | 每10秒一条记录 | 改进后：接近25Hz（改进前：约1Hz） |
| **数据字段数** | 18个字段 | 10个字段 |
| **主要用途** | 用户行为和环境监测 | 运动姿态和位置追踪 |
| **数据量** | 中等（每10秒一条） | 改进后：较大（高频数据，接近25Hz） |
| **文件大小限制** | 5MB | 5MB |
| **上传频率** | 每30秒 | 每30秒 |
| **计数器** | `counter`（共享） | `imuCounter`（独立） |
| **重试机制** | 独立 | 独立 |

---

## 7. 注意事项

1. **独立计数器**: sensor.csv和IMU.csv使用独立的计数器（`counter`和`imuCounter`），互不影响
2. **独立重试**: 两个上传函数的重试机制完全独立，一个失败不影响另一个
3. **错误处理**: 上传失败后会延迟重试，避免频繁请求
4. **文件安全**: 使用CSV转义函数处理特殊字符，确保数据格式正确
5. **时区处理**: 所有时间戳统一使用UTC+8时区
6. **权限要求**: 需要网络权限、存储权限、位置权限、传感器权限等
7. **IMU数据频率说明**:
   - **改进前**: 虽然传感器注册频率设置为25Hz（40ms间隔），但实际收集频率约为每秒1条
   - **原因**: 需要三种传感器（加速度计、陀螺仪、磁力计）同时更新才记录一条，磁力计更新频率低导致整体频率受限
   - **改进方案**: 已优化代码，现在只要有一个传感器更新就立即记录，其他传感器使用上次的值
   - **改进效果**: 预期频率提升至接近25Hz，数据收集频率提升约25倍
   - **数据质量**: 每个传感器更新时都会记录，已更新的传感器使用最新值，未更新的使用上次的值，确保数据连续性

---

**报告生成时间**: 2024年
**最后更新时间**: 2026年3月
**代码文件**: `app/src/main/java/com/example/mobibox/service/DataService.java`
**常量文件**: `app/src/main/java/com/example/mobibox/Constants.java`

