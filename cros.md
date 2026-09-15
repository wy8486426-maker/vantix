# Vantix 对接 CORS 账号服务接口说明

本文记录 Vantix 服务码兑换流程实际调用的 CORS 账号服务 HTTP 契约，供 CORS 服务端实现和联调使用。接口地址、字段校验和状态处理依据当前 Vantix 代码整理；真实 CORS 服务尚未进行端到端联调。

## 目录

- [接口概览](#接口概览)
- [创建账号批次](#创建账号批次)
- [查询账号批次](#查询账号批次)
- [响应字段与时间格式](#响应字段与时间格式)
- [错误码和处理语义](#错误码和处理语义)
- [幂等与重试约定](#幂等与重试约定)
- [Vantix 配置](#vantix-配置)
- [代码参考](#代码参考)

## 接口概览

所有路径相对于 `vantix.cors.base-url`。当前默认地址为 `http://localhost:8081`。

| 用途 | 方法 | 路径 |
|---|---|---|
| 创建账号批次 | `POST` | `/internal/v1/accounts/batch-create` |
| 按请求号查询批次 | `GET` | `/internal/v1/accounts/batch-create/{requestId}` |

POST 请求发送 `Content-Type: application/json` 和 `Accept: application/json`；GET 请求发送 `Accept: application/json`。当前 Vantix 适配器不附加认证令牌或其他自定义请求头，也不使用独立的幂等请求头；幂等标识通过 JSON 和查询路径中的 `requestId` 传递。

## 创建账号批次

```http
POST /internal/v1/accounts/batch-create
Content-Type: application/json
Accept: application/json
```

请求体：

```json
{
  "requestId": "EX-20260913-000001",
  "durationDays": 1,
  "silenceDays": 30,
  "quantity": 2,
  "accountPrefix": "sino"
}
```

| 字段 | 类型 | 要求 | 说明 |
|---|---|---|---|
| `requestId` | string | 必填 | Vantix 批次请求号；按大小写敏感的不透明字符串处理，重试必须沿用原值。 |
| `durationDays` | integer | 必填 | 账号有效时长天数，必须为正数。 |
| `silenceDays` | integer | 必填 | CORS 自动激活前的沉默天数，必须为非负数。 |
| `quantity` | integer | 必填 | 本批账号数量，必须为正数；Vantix 当前单次上限为 5000。 |
| `accountPrefix` | string 或 null | 可空 | 账号前缀；Vantix 未设置时传 `null`，前缀最长 64 个字符。 |

成功响应使用 HTTP 2xx，`code` 为数字 `0` 或字符串 `"0"`。`data.requestId` 必须与请求中的请求号完全一致，`data.accounts` 必须包含恰好 `quantity` 个账号：

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "requestId": "EX-20260913-000001",
    "accounts": [
      {
        "index": 1,
        "accountId": "cors-10001",
        "account": "sino-10001",
        "accountStatus": "ENABLED",
        "activationStatus": "WAITING_ACTIVATION",
        "activatedAt": null,
        "expireAt": "2026-10-13T16:30:00+08:00",
        "createdAt": "2026-09-13T16:30:00+08:00",
        "updatedAt": "2026-09-13T16:30:00+08:00"
      },
      {
        "index": 2,
        "accountId": "cors-10002",
        "account": "sino-10002",
        "accountStatus": "ENABLED",
        "activationStatus": "WAITING_ACTIVATION",
        "activatedAt": null,
        "expireAt": "2026-10-13T16:30:00+08:00",
        "createdAt": "2026-09-13T16:30:00+08:00",
        "updatedAt": "2026-09-13T16:30:00+08:00"
      }
    ]
  }
}
```

## 查询账号批次

```http
GET /internal/v1/accounts/batch-create/EX-20260913-000001
Accept: application/json
```

查询成功时返回与创建成功相同的响应 envelope 和账号字段。查询结果中的 `data.requestId` 必须匹配路径请求号；`data.accounts` 必须非空。Vantix 使用 `index` 将远端账号与本地服务码对应，因此序号必须是从 `1` 开始、无重复且连续的整数。数组可以不按序号排序，Vantix 会按 `index` 排序后入账。

请求号尚不存在时，可返回 HTTP 404（响应体可为空），也可返回带有 `code: "NOT_FOUND"` 的 JSON。该语义仅用于查询接口。

## 响应字段与时间格式

成功响应的 envelope 至少包含 `code` 和 `data`；`message` 可省略。`data` 是对象，字段如下：

| 字段 | 类型 | 要求 | 说明 |
|---|---|---|---|
| `requestId` | string | 必填、非空 | 必须与原请求号完全相同。 |
| `accounts` | array | 必填、非空 | 创建接口数组长度必须等于请求 `quantity`；查询接口必须至少含一项。 |
| `accounts[].index` | integer | 必填 | 从 1 开始连续且不重复。 |
| `accounts[].accountId` | string | 必填、非空且批内唯一 | CORS 账号 ID。 |
| `accounts[].account` | string | 必填、非空且批内唯一 | 账号内容。 |
| `accounts[].accountStatus` | string | 必填、非空 | CORS 账号状态；Vantix 保留未知状态字符串以兼容扩展。 |
| `accounts[].activationStatus` | string | 必填、非空 | 激活状态；Vantix 保留未知状态字符串以兼容扩展。 |
| `accounts[].activatedAt` | string 或 null | 必须出现，可空 | 激活时间。 |
| `accounts[].expireAt` | string 或 null | 必须出现，可空 | 到期时间；成功入账时该值作为账号到期时间。 |
| `accounts[].createdAt` | string | 必填、非空 | CORS 账号创建时间。 |
| `accounts[].updatedAt` | string | 必填、非空 | CORS 账号更新时间。 |

时间字符串必须是可解析的 ISO 8601/RFC 3339 日期时间，并带时区偏移，例如 `2026-09-13T16:30:00+08:00` 或 `2026-09-13T08:30:00Z`。Vantix 将时间转换为 `Asia/Shanghai` 后保存。

## 错误码和处理语义

错误响应可包含 `code`、`message` 和 `sideEffect`。`sideEffect` 必须使用 JSON 布尔值，不能用字符串 `"false"`。

| 条件 | Vantix 识别结果 | 处理方式 |
|---|---|---|
| HTTP 2xx 且 `code` 为 `0`，响应数据通过校验 | 成功 | 按 `index` 将账号写入 Vantix。 |
| 查询接口 HTTP 404，或查询响应 `code` 为 `NOT_FOUND` | 未找到 | 查询重试时可使用原 `requestId` 再次提交创建请求。 |
| `code` 为 `INVALID_ARGUMENT` 且 `sideEffect` 为布尔 `false` | 明确拒绝 | Vantix 判定远端未产生副作用，并释放本地预留服务码。 |
| `code` 为 `IDEMPOTENCY_CONFLICT` | 幂等冲突 | 转人工复核；不得改用新请求号自动重建。 |
| HTTP 5xx、其他 4xx、超时/连接错误、非 JSON 或结构不完整 | 结果未知 | 保留本地预留；后续先查询原 `requestId`，避免把不确定结果当作失败。 |

只有 `INVALID_ARGUMENT` 且明确声明 `sideEffect: false` 才会被认定为安全的明确拒绝。其他错误即使返回 4xx，也按结果未知处理。请勿在错误 envelope 中省略这项无副作用保证。

错误响应示例：

```json
{
  "code": "INVALID_ARGUMENT",
  "message": "durationDays must be positive",
  "sideEffect": false
}
```

## 幂等与重试约定

远端必须将 `requestId` 作为幂等键，并以大小写敏感方式匹配。相同 `requestId` 和相同请求参数重复提交，不得重复创建账号；应返回原批次结果或当前批次状态。相同 `requestId` 搭配不同请求参数时，返回 `IDEMPOTENCY_CONFLICT`。

当创建调用超时或响应无法确认时，Vantix 会先调用查询接口。查询返回成功时直接完成本地入账；查询返回未找到时，Vantix 使用原 `requestId` 和原参数再次提交。因而查询接口必须可靠反映该请求号对应的批次状态，幂等创建也必须防止查询暂未可见时的重复创建。

Vantix 当前默认每 5 秒轮询待处理操作，最多自动重试 10 次；重试延迟以 30 秒为基数指数增长，最长 1 小时。超过重试次数、发生幂等冲突或远端已成功但 Vantix 本地入账失败时，会转人工复核。

## Vantix 配置

| 配置项 | 环境变量 | 默认值 | 作用 |
|---|---|---:|---|
| `vantix.cors.base-url` | `VANTIX_CORS_BASE_URL` | `http://localhost:8081` | CORS 服务根地址。 |
| `vantix.cors.connect-timeout` | `VANTIX_CORS_CONNECT_TIMEOUT` | `2s` | 建立连接的超时。 |
| `vantix.cors.read-timeout` | `VANTIX_CORS_READ_TIMEOUT` | `5s` | 读取响应的超时。 |
| `vantix.cors-operation.enabled` | `VANTIX_CORS_OPERATION_ENABLED` | `true` | 是否启用后台操作 worker。 |
| `vantix.cors-operation.poll-interval` | `VANTIX_CORS_OPERATION_POLL_INTERVAL` | `5s` | worker 轮询间隔。 |
| `vantix.cors-operation.max-retries` | `VANTIX_CORS_OPERATION_MAX_RETRIES` | `10` | 自动重试上限。 |
| `vantix.cors-operation.retry-base-delay` | `VANTIX_CORS_OPERATION_RETRY_BASE_DELAY` | `30s` | 指数退避初始延迟。 |
| `vantix.cors-operation.claim-timeout` | `VANTIX_CORS_OPERATION_CLAIM_TIMEOUT` | `2m` | worker 操作租约超时。 |
| `vantix.cors-operation.worker-batch-size` | `VANTIX_CORS_OPERATION_WORKER_BATCH_SIZE` | `20` | 每轮领取的操作数上限。 |

## 代码参考

- [CORS HTTP 适配器](src/main/java/com/sinognss/cloud/vantix/infrastructure/cors/RestCorsAccountGateway.java)
- [请求和响应模型](src/main/java/com/sinognss/cloud/vantix/integration/cors/)
- [批次处理器](src/main/java/com/sinognss/cloud/vantix/application/cors/CorsOperationProcessor.java)
- [CORS 客户端配置](src/main/java/com/sinognss/cloud/vantix/config/CorsClientConfig.java)
- [应用配置](src/main/resources/application.yml)
- [HTTP 契约测试](src/test/java/com/sinognss/cloud/vantix/infrastructure/cors/RestCorsAccountGatewayTest.java)
