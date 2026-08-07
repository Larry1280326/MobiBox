# 后端API修改说明

## 概述
本次修改涉及两个反馈提交功能：
1. **Log Feedback** - 新增的日志反馈功能
2. **Feedback for Suggestion** - 原有的干预建议反馈功能（已修改）

## API端点
两个功能都使用同一个端点：`POST /send_intervention_feedback`

后端需要根据请求体中的字段来区分是哪种类型的反馈：
- 如果包含 `log_mc1`, `log_mc2`, `log_mc3`, `log_mc4`, `log_ground_truth` 等字段，则为 **Log Feedback**
- 如果包含 `mc1`, `mc2`, `mc3`, `mc4`, `mc5`, `mc6`, `intervention` 等字段，则为 **Feedback for Suggestion**

---

## 1. Log Feedback 数据结构

### 请求体格式（JSON）

```json
{
  "user_id": "string (必填)",
  "hourly_log": "string (必填)",
  "log_mc1": 1-5 (必填, 整数),
  "log_mc2": 1-3 (必填, 整数),
  "log_mc3": "too_little|appropriate|too_much" (必填, 字符串),
  "log_mc4": "too_short|appropriate|too_long" (必填, 字符串),
  "log_ground_truth": "string (必填, 英文)",
  "log_suggestions": "string (选填, 英文)"
}
```

### 字段说明

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `user_id` | string | 是 | 用户ID |
| `hourly_log` | string | 是 | 小时行为总结内容 |
| `log_mc1` | integer | 是 | 问题1：日志准确度/正确性评分 (1=最不正确, 5=最正确) |
| `log_mc2` | integer | 是 | 问题2：日志清晰度评分 (1-3) |
| `log_mc3` | string | 是 | 问题3：细节是否足够 ("too_little"|"appropriate"|"too_much") |
| `log_mc4` | string | 是 | 问题4：长度评价 ("too_short"|"appropriate"|"too_long") |
| `log_ground_truth` | string | 是 | 标准答案（Ground Truth），英文 |
| `log_suggestions` | string | 否 | 优化建议，英文 |

### 响应格式

成功响应：
```json
{
  "status": "success",
  "message": "Log feedback submitted successfully"
}
```

错误响应：
```json
{
  "status": "error",
  "message": "错误信息",
  "error_code": 431  // 431: 用户ID缺失, 432: 未知用户, 500: 服务器错误
}
```

---

## 2. Feedback for Suggestion 数据结构（已修改）

### 请求体格式（JSON）

```json
{
  "user_id": "string (必填)",
  "intervention": "string (必填)",
  "hourly_log": "string (选填)",
  "mc1": 0|1 (必填, 整数, yes=1, no=0),
  "mc2": 1-5 (必填, 整数),
  "mc3": 1-5 (必填, 整数),
  "mc4": 1-5 (必填, 整数),
  "mc5": 1-5 (必填, 整数),
  "mc6": 1-5 (必填, 整数),
  "lq": "string (选填, 之前是必填)",
  "atomic_activities": {
    "har": ["string"],
    "phone_usages": ["string"],
    "app_usage": ["string"],
    "location": ["string"],
    "movement": ["string"],
    "step_counts": ["string"]
  }
}
```

### 字段说明

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `user_id` | string | 是 | 用户ID |
| `intervention` | string | 是 | 干预建议内容 |
| `hourly_log` | string | 否 | 小时行为总结内容（可选） |
| `mc1` | integer | 是 | 问题1：干预内容是否相关 (0=no, 1=yes) |
| `mc2` | integer | 是 | 问题2：干预及时性评分 (1-5) |
| `mc3` | integer | 是 | 问题3：干预措施正确性评分 (1-5) |
| `mc4` | integer | 是 | 问题4：干预有效性评分 (1-5) |
| `mc5` | integer | 是 | 问题5：干预清晰度评分 (1-5) |
| `mc6` | integer | 是 | 问题6：总体满意度评分 (1-5) |
| `lq` | string | **否** | 优质干预提醒内容（**已改为选填**） |
| `atomic_activities` | object | 是 | 原子活动数据 |

### 重要变更

1. **`lq` 字段改为选填**：之前是必填字段，现在改为选填。如果用户没有填写，该字段可能不存在于请求体中。

### 响应格式

成功响应：
```json
{
  "status": "success",
  "message": "Feedback submitted successfully"
}
```

错误响应：
```json
{
  "status": "error",
  "message": "错误信息",
  "error_code": 431  // 431: 用户ID缺失, 432: 未知用户, 433: 干预内容缺失, 434: 反馈信息不完整, 500: 服务器错误
}
```

---

## 后端实现建议

### Python Flask 示例代码

```python
@app.route('/send_intervention_feedback', methods=['POST'])
def send_intervention_feedback():
    try:
        data = request.get_json()
        
        if not data:
            return jsonify({
                "status": "error",
                "message": "Invalid request body",
                "error_code": 400
            }), 400
        
        user_id = data.get('user_id')
        if not user_id:
            return jsonify({
                "status": "error",
                "message": "User ID is required",
                "error_code": 431
            }), 431
        
        # 检查是否为Log Feedback
        if 'log_mc1' in data:
            return handle_log_feedback(data)
        # 否则为Feedback for Suggestion
        else:
            return handle_suggestion_feedback(data)
            
    except Exception as e:
        return jsonify({
            "status": "error",
            "message": str(e),
            "error_code": 500
        }), 500

def handle_log_feedback(data):
    """处理Log Feedback"""
    # 验证必填字段
    required_fields = ['hourly_log', 'log_mc1', 'log_mc2', 'log_mc3', 'log_mc4', 'log_ground_truth']
    for field in required_fields:
        if field not in data or not data[field]:
            return jsonify({
                "status": "error",
                "message": f"Missing required field: {field}",
                "error_code": 434
            }), 434
    
    # 验证字段值
    if not (1 <= data['log_mc1'] <= 5):
        return jsonify({
            "status": "error",
            "message": "log_mc1 must be between 1 and 5",
            "error_code": 434
        }), 434
    
    if not (1 <= data['log_mc2'] <= 3):
        return jsonify({
            "status": "error",
            "message": "log_mc2 must be between 1 and 3",
            "error_code": 434
        }), 434
    
    if data['log_mc3'] not in ['too_little', 'appropriate', 'too_much']:
        return jsonify({
            "status": "error",
            "message": "log_mc3 must be one of: too_little, appropriate, too_much",
            "error_code": 434
        }), 434
    
    if data['log_mc4'] not in ['too_short', 'appropriate', 'too_long']:
        return jsonify({
            "status": "error",
            "message": "log_mc4 must be one of: too_short, appropriate, too_long",
            "error_code": 434
        }), 434
    
    # 保存到数据库
    # TODO: 实现数据库保存逻辑
    
    return jsonify({
        "status": "success",
        "message": "Log feedback submitted successfully"
    }), 200

def handle_suggestion_feedback(data):
    """处理Feedback for Suggestion"""
    # 验证必填字段
    required_fields = ['intervention', 'mc1', 'mc2', 'mc3', 'mc4', 'mc5', 'mc6']
    for field in required_fields:
        if field not in data:
            return jsonify({
                "status": "error",
                "message": f"Missing required field: {field}",
                "error_code": 434
            }), 434
    
    # lq字段现在是选填的，不需要验证
    
    # 验证字段值
    if data['mc1'] not in [0, 1]:
        return jsonify({
            "status": "error",
            "message": "mc1 must be 0 or 1",
            "error_code": 434
        }), 434
    
    for i in range(2, 7):
        mc_field = f'mc{i}'
        if not (1 <= data[mc_field] <= 5):
            return jsonify({
                "status": "error",
                "message": f"{mc_field} must be between 1 and 5",
                "error_code": 434
            }), 434
    
    # 保存到数据库
    # TODO: 实现数据库保存逻辑
    
    return jsonify({
        "status": "success",
        "message": "Feedback submitted successfully"
    }), 200
```

---

## 数据库表结构建议

### Log Feedback 表

```sql
CREATE TABLE log_feedback (
    id SERIAL PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    hourly_log TEXT NOT NULL,
    log_mc1 INTEGER NOT NULL CHECK (log_mc1 >= 1 AND log_mc1 <= 5),
    log_mc2 INTEGER NOT NULL CHECK (log_mc2 >= 1 AND log_mc2 <= 3),
    log_mc3 VARCHAR(20) NOT NULL CHECK (log_mc3 IN ('too_little', 'appropriate', 'too_much')),
    log_mc4 VARCHAR(20) NOT NULL CHECK (log_mc4 IN ('too_short', 'appropriate', 'too_long')),
    log_ground_truth TEXT NOT NULL,
    log_suggestions TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Feedback for Suggestion 表（需要修改）

```sql
-- 如果lq字段之前是NOT NULL，需要改为NULLABLE
ALTER TABLE intervention_feedback 
ALTER COLUMN lq TEXT NULL;  -- 改为可选
```

---

## 测试用例

### Log Feedback 测试

```bash
curl -X POST http://120.25.178.24:5000/send_intervention_feedback \
  -H "Content-Type: application/json" \
  -d '{
    "user_id": "test_user",
    "hourly_log": "Test hourly log content",
    "log_mc1": 4,
    "log_mc2": 2,
    "log_mc3": "appropriate",
    "log_mc4": "appropriate",
    "log_ground_truth": "This is the ground truth answer in English",
    "log_suggestions": "Optional optimization suggestions"
  }'
```

### Feedback for Suggestion 测试（不带lq字段）

```bash
curl -X POST http://120.25.178.24:5000/send_intervention_feedback \
  -H "Content-Type: application/json" \
  -d '{
    "user_id": "test_user",
    "intervention": "Test intervention content",
    "mc1": 1,
    "mc2": 4,
    "mc3": 5,
    "mc4": 4,
    "mc5": 5,
    "mc6": 4
  }'
```

---

## 注意事项

1. **字段区分**：后端需要根据请求体中是否存在 `log_mc1` 字段来区分两种反馈类型
2. **向后兼容**：确保旧的Feedback for Suggestion请求仍然可以正常工作
3. **数据验证**：需要验证所有必填字段和字段值的有效性
4. **错误处理**：返回适当的错误码和错误信息
5. **数据库**：如果之前 `lq` 字段是NOT NULL，需要修改为NULLABLE以支持选填















