# MobiQA Android Revising 后端对接说明

本文档说明 `MobiQA-Android-Revising` 版本相对原始 Android 版本的主要改动，以及这些改动对后端接口、数据格式和联调工作的影响。

## 结论

这个版本可以作为后端联调版本使用。接口 URL 基本没有变化，主要上传 payload 仍然是 JSON `items` 数组。此次修改主要集中在 Android 客户端内部的后台保活、文件封存、上传节奏、网络复用、失败重试和提醒逻辑。

对后端来说，通常不需要重写接口，但需要重点确认：

- 批量 `items` 插入性能是否足够。
- 同一用户、同一 timestamp 的重复数据是否能幂等处理。
- 补传导致的数据乱序是否能正确入库。
- IMU 每分钟集中上传多个 chunk 时是否会超时。
- 成功/失败响应格式是否和客户端判断逻辑一致。

## 本次 Android 端主要改动

### 1. 后台服务启动方式

原始版本主要通过 `bindService()` 启动 `DataService`，Activity 生命周期会影响后台采集稳定性。

当前版本改为：

- `MainActivity` 先调用 `ContextCompat.startForegroundService()` 显式启动前台服务。
- 然后再 `bindService()` 绑定控制服务。
- `DataService.onStartCommand()` 中如果发现尚未采集，会自动调用 `startDataCollection()`。

影响：

- 用户退出 Activity 后，后台采集继续运行的概率更高。
- 服务被系统重建后，会尝试自动恢复采集。
- 用户操作逻辑基本不变，仍然是打开 app、授权、点击开始采集。

### 2. Foreground service type 调整

原始版本：

```xml
android:foregroundServiceType="location|dataSync|specialUse"
```

当前版本：

```xml
android:foregroundServiceType="location|specialUse"
```

原因：

- `DataService` 的核心职责是长期后台采集定位、传感器和上下文数据，不是短生命周期的数据同步任务。
- Android 15 对 `dataSync` foreground service 有累计运行超时限制。长期常驻服务声明 `dataSync` 会增加被系统 timeout 的风险。
- 上传仍然保留，只是不再把整个长期采集服务声明为 `dataSync` 类型。

对后端无直接接口影响。

### 3. 文件上传策略重构

原始版本的问题：

- 上传线程直接读取正在写入的 `sensor.csv` / `IMU.csv`。
- 上传成功后清空活跃文件。
- 容易发生边写边读、边写边清空导致的数据丢失或错乱。

当前版本：

- 到上传时间后，先把活跃 CSV 文件封存为独立 segment 文件。
- 文件名形如：

```text
sensor_upload_yyyyMMdd_HHmmss_SSS.csv
IMU_upload_yyyyMMdd_HHmmss_SSS.csv
```

- 活跃文件立即重新创建并继续写入。
- 上传封存后的 segment 文件。
- 上传成功后删除 segment。
- 上传失败后加入 pending queue，等待网络恢复后重试。

影响：

- 数据丢失风险明显下降。
- 后端会看到延迟补传的数据。
- 后端不要假设数据严格按到达顺序排列。
- 后端最好按 `user + timestamp` 做幂等或去重。

### 4. IMU 上传频率调整

原始版本：

- IMU 采样目标约 50Hz。
- 每 5 秒读取活跃 `IMU.csv` 并上传。

当前版本：

- IMU 采样目标仍约 50Hz。
- IMU 写入间隔仍为 20ms。
- IMU 上传改为每 60 秒封存一次 segment。
- 每个 IMU 上传 chunk 最大 1000 行。

粗略估算：

- 50Hz * 60s = 约 3000 行/分钟。
- 所以常见情况是一分钟产生约 3 个 `/upload/imu` 请求，每个请求最多约 1000 条 `items`。

影响：

- Android 端网络请求频率降低，超时概率下降。
- 后端单次请求处理的数据量变大。
- 后端需要确认 `/upload/imu` 支持 1000 条左右的批量插入。

### 5. 上传网络逻辑调整

后台上传现在使用复用的 OkHttpClient，并采用阻塞式上传流程：

- 上传当前 segment 时，同一类上传有并发门控，避免重复上传。
- pending queue 处理时逐个上传。
- 后台上传主链路不再每次新建 OkHttpClient。
- 后台上传主链路不再强制 `Connection: close`。

影响：

- 连接复用更好。
- 移动网络下握手开销更小。
- 长时间运行时网络超时概率应下降。

注意：

- `InterventionActivity`、`DailyLogActivity`、`DailyLogWorker` 中仍有部分页面请求单独 new OkHttpClient，并仍有 `Connection: close`。这不影响后台数据上传主链路，但页面侧接口仍建议后续统一。

### 6. 提醒/summary 拉取逻辑调整

原始版本：

- 后台每 5 分钟同时拉取 hourly log、intervention、atomic activities。
- 只有三者都出现新内容时，才缓存并通知。

当前版本：

- 仍然每 5 分钟拉取三类内容。
- 但不再要求三者同时有新内容。
- 哪一类有新内容，就处理哪一类。

影响：

- 用户更容易及时收到 intervention/hourly update。
- 后端不需要保证三类内容同步生成。
- 如果某一个接口临时失败，不会阻止其他已成功接口的提醒。

### 7. CSV 解析修复

原始版本使用简单 `split(",")`，字段中有逗号时容易错列。

当前版本增加了 CSV 行解析逻辑，支持：

- 引号包裹字段。
- 字段中包含逗号。
- 双引号转义。

影响：

- Wi-Fi 名称、地址、POI、蓝牙名称中包含逗号时，更不容易导致上传 JSON 字段错位。
- 但这仍然不是完整第三方 CSV 库实现，极端格式仍需谨慎。

## 后端接口与 payload

### 1. 用户注册

Endpoint:

```text
POST /register
```

请求示例：

```json
{
  "name": "user_id_or_name"
}
```

客户端只判断请求是否成功返回，不强依赖复杂字段。

### 2. Sensor 数据上传

Endpoint:

```text
POST /upload/documents
```

请求格式：

```json
{
  "items": [
    {
      "user": "user_id",
      "timestamp": "2026-07-14T12:00:00+08:00",
      "volume": 50.0,
      "screen_on_ratio": 1.0,
      "wifi_connected": true,
      "wifi_ssid": "WiFi Name",
      "network_traffic": 0.12,
      "Rx_traffic": 123,
      "Tx_traffic": 45,
      "stepcount_sensor": 20,
      "gpsLat": 22.3193,
      "gpsLon": 114.1694,
      "battery": 80.0,
      "current_app": "ExampleApp(com.example)",
      "bluetooth_devices": ["Device A", "Device B"],
      "address": "Address string",
      "poi": ["POI A", "POI B"],
      "nearbyBluetoothCount": 2,
      "topBluetoothDevices": ["Device A", "Device B"]
    }
  ]
}
```

字段说明：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `user` | string | 用户 ID |
| `timestamp` | string | 客户端采集时间 |
| `volume` | number | 当前音量百分比 |
| `screen_on_ratio` | number | 屏幕点亮比例/状态指标 |
| `wifi_connected` | boolean | 是否连接 Wi-Fi |
| `wifi_ssid` | string | Wi-Fi 名称 |
| `network_traffic` | number | 网络流量统计 |
| `Rx_traffic` | number | 接收流量 |
| `Tx_traffic` | number | 发送流量 |
| `stepcount_sensor` | integer | 步数增量 |
| `gpsLat` | number | 纬度 |
| `gpsLon` | number | 经度 |
| `battery` | number | 电量百分比 |
| `current_app` | string | 当前/最近前台应用 |
| `bluetooth_devices` | array | 蓝牙设备列表 |
| `address` | string | 地址 |
| `poi` | array | POI 列表 |
| `nearbyBluetoothCount` | integer | 附近蓝牙设备数量 |
| `topBluetoothDevices` | array | 排名前列的蓝牙设备 |

后端注意：

- `items` 可能一次包含多条 sensor 数据。
- 失败补传时，旧 timestamp 的数据可能稍后到达。
- 建议以 `user + timestamp`，必要时加数据类型，做去重或幂等处理。
- 客户端会把部分空值、`N/A`、解析失败的数值转为 `0`，后端分析时应区分“真实 0”和“客户端缺失值被转 0”的可能性。

### 3. IMU 数据上传

Endpoint:

```text
POST /upload/imu
```

请求格式：

```json
{
  "items": [
    {
      "user": "user_id",
      "timestamp": "2026-07-14T12:00:00+08:00",
      "acc_X": 0.01,
      "acc_Y": 0.02,
      "acc_Z": 9.81,
      "gyro_X": 0.001,
      "gyro_Y": 0.002,
      "gyro_Z": 0.003,
      "mag_X": 1.0,
      "mag_Y": 2.0,
      "mag_Z": 3.0
    }
  ]
}
```

字段说明：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `user` | string | 用户 ID |
| `timestamp` | string | 客户端采样时间 |
| `acc_X` | number | 加速度 X |
| `acc_Y` | number | 加速度 Y |
| `acc_Z` | number | 加速度 Z |
| `gyro_X` | number | 陀螺仪 X |
| `gyro_Y` | number | 陀螺仪 Y |
| `gyro_Z` | number | 陀螺仪 Z |
| `mag_X` | number | 磁力计 X |
| `mag_Y` | number | 磁力计 Y |
| `mag_Z` | number | 磁力计 Z |

后端注意：

- Android 端目标采样频率是 50Hz，即约每 20ms 一条。
- 系统调度和手机硬件会导致实际采样间隔波动，后端不要假设严格等间隔。
- 每个请求最多约 1000 条 IMU `items`。
- 网络恢复后可能连续补传多个 chunk。

### 4. Summary / Intervention 拉取

#### Hourly / Daily Log

Endpoint:

```text
POST /get_summary_log
```

Hourly 请求常见格式：

```json
{
  "user": "user_id",
  "log_type": "hourly"
}
```

Daily 请求常见格式：

```json
{
  "user": "user_id",
  "log_type": "daily"
}
```

部分路径可能带 `last_log_id`：

```json
{
  "user": "user_id",
  "log_type": "daily",
  "last_log_id": 123
}
```

#### Intervention

Endpoint:

```text
POST /get_intervention
```

请求格式：

```json
{
  "user": "user_id"
}
```

#### Atomic Activities

Endpoint:

```text
POST /get_compressed_atomic_activities
```

请求格式：

```json
{
  "user": "user_id",
  "duration": 300
}
```

说明：

- `duration` 是客户端根据上次拉取时间计算出的秒数。
- 首次拉取时可能是 `0`。

### 5. Feedback 相关接口

仍保留原有接口：

```text
POST /send_intervention_feedback
POST /send_log_feedback
POST /feedback
POST /send_weekly_survey
POST /send_summary_feedback
POST /submit_atomic_activities
```

这轮修改没有刻意改变这些接口的业务语义。

## 后端响应格式建议

上传成功时建议统一返回：

```json
{
  "status": "success"
}
```

失败时建议返回：

```json
{
  "status": "error",
  "message": "reason"
}
```

客户端当前主要通过 `status == "success"` 判断上传成功。如果响应体为空、非 JSON、没有 `status`，客户端会视为失败并进入重试逻辑。

## 与原始版本相比，后端最需要关注的变化

### 请求频率变化

- Sensor：仍约 30 秒上传一次，但改成上传封存 segment。
- IMU：从约 5 秒一次改成约 60 秒一次 segment，并按 1000 行 chunk 上传。

### 数据到达顺序变化

由于失败重试和 pending queue，后端会看到：

- 旧数据晚到。
- 同一时间段数据被分多个 chunk 到达。
- 网络恢复后短时间内补传多批数据。

### 重复数据可能性

如果客户端上传成功但响应丢失，客户端可能认为失败并重试。因此后端需要考虑重复写入。

建议去重键：

```text
user + timestamp + data_type
```

如果 IMU 同一 timestamp 可能存在多条，则可以扩展为：

```text
user + timestamp + acc_X + acc_Y + acc_Z + gyro_X + gyro_Y + gyro_Z + mag_X + mag_Y + mag_Z
```

实际去重策略由后端数据模型决定。

## 当前仍存在的 Android 端限制

这些不是后端必须解决的问题，但联调时需要知道：

- Android 端仍使用明文 HTTP。
- 权限策略仍然比较激进，包括定位、后台定位、蓝牙、使用情况、全文件访问等。
- 厂商 ROM 仍可能限制长期后台运行。
- 如果服务启动时无网络，部分采集初始化可能直接返回，后续还需要真机长时间测试。
- 页面侧部分网络请求还未完全统一到同一个 OkHttpClient。
- 代码中仍有一些旧逻辑被 `if (false)` 包住，当前不会执行，但后续维护时需要清理。

## 建议联调清单

后端同学可按下面顺序验证：

1. `/register` 是否能正常注册用户。
2. `/upload/documents` 是否能接收包含多条 `items` 的请求。
3. `/upload/imu` 是否能接收 1000 条左右 `items` 的请求。
4. 上传成功是否返回 `{"status":"success"}`。
5. 上传失败是否返回 JSON，并包含 `message`。
6. 同一批数据重复上传时，后端是否会重复入库或能幂等处理。
7. 旧 timestamp 数据延迟上传时，后端是否能按采集时间正确存储。
8. `/get_summary_log` 的 hourly/daily 返回是否能被客户端识别为新内容。
9. `/get_intervention` 是否能返回 intervention 内容。
10. `/get_compressed_atomic_activities` 是否支持 `duration=0` 和较大 duration。

## 给后端同学的简短说明

这版 Android 主要是重构后台采集和上传稳定性。接口路径基本不变，上传仍是 `POST /upload/documents` 和 `POST /upload/imu`，payload 是 `{ "items": [...] }`。变化是客户端现在会先封存 CSV segment 再上传，IMU 从 5 秒上传改成 60 秒上传并按 1000 行切块。失败会进入 pending queue，网络恢复后补传，因此后端需要支持批量、乱序、重复和延迟数据。成功响应请返回 `{"status":"success"}`，失败响应请返回 JSON `message`。
