# Vantix 对接 CORS 账号服务接口说明

本文记录 Vantix 当前调用的 CORS 账号协议。`vantix.cors.base-url` 只配置
scheme + host + port，以下路径由适配器统一拼接；除 `customPass` 的正式 body 外，
请求不附加密码、secret 或其他认证头。

## 新增账号

```http
POST /BaseUser/userInfo/add
Content-Type: application/json
Accept: application/json
```

请求体由 exchange batch 的不可变快照构建：

```json
{
  "requestId": "EXCHANGE_<uuid>",
  "addNum": 10,
  "accountType": 0,
  "durationType": 365,
  "accountName": "AB12",
  "nameType": 0,
  "silenceType": 30,
  "activeType": 1,
  "remark": "",
  "dealerId": 123,
  "normalType": 0
}
```

| 字段 | 来源或固定值 |
|---|---|
| `requestId` | Vantix 为该 CORS side effect 生成并持久化的稳定 ID。 |
| `addNum` | exchange batch `quantity` 快照。 |
| `accountType` | `0`。 |
| `durationType` | exchange batch `durationDays` 快照。 |
| `accountName` | 首次 reservation 冻结到 exchange batch 的 4 位账号前缀。 |
| `nameType` | `0`。 |
| `silenceType` | exchange batch `accountSilenceDays` 快照。 |
| `activeType` | `1`。 |
| `remark` | 当前业务约定的可选备注；未提供时传空字符串。 |
| `dealerId` | exchange batch/company 的 `companyId`。 |
| `normalType` | `0`。 |

## 响应

外层响应是 `CommonResult<Object>`：

```json
{
  "code": 0,
  "message": "操作成功",
  "data": {
    "accounts": [
      {"id": 10001, "name": "AB12000001"},
      {"id": 10002, "name": "AB12000002"}
    ]
  }
}
```

`code = 0` 表示 CORS 请求成功接受，`data` 可以暂时为 `null`。当 `data` 有值时，
Vantix 使用 `accounts` 入账；列表必须非 null、数量恰好等于 `addNum`，每个 `id` 为正数且
批内不重复，每个 `name` 非 blank、长度不超过本地字段限制且批内不重复。校验不通过时
不会标记 COMPLETE，也不会伪造账号。

`code != 0` 表示业务失败。例如：

| code | 含义 | Vantix 处理 |
|---|---|---|
| `5302` | 用户名称重复 | 明确失败，按现有失败路径释放本地预留。 |
| `5301` | 用户添加失败 | 明确失败，按现有失败路径释放本地预留。 |

未知业务码、HTTP 5xx、超时、连接异常、非 JSON 或无法解析的响应均视为结果未知，保留
本地预留并重试；日志只记录 operation/batch、requestId、code、message 和 retryCount，
不记录认证信息。

## 幂等与重试

同一个 exchange batch 当前严格对应一个 CORS 新增账号 operation。Vantix 在 reservation
事务中生成一次 `requestId`，写入 `cors_operation.request_id`，事务提交后才调用 CORS。
retry、应用重启补偿、HTTP timeout、网络异常以及 `code = 0, data = null` 都从数据库读取
并复用该值，不重新生成 UUID，也不创建第二个 operation。

CORS Redis 回传结果窗口从 operation 的第一次调用时间
`cors_operation.first_attempt_at` 计算，默认约 2 分钟；retry 不会重新延长窗口。窗口内
允许短期重试，窗口到期后停止自动重试并将现有 operation 与 exchange batch 转为
`MANUAL_REVIEW`。当前不新增查询协议、Redis 直查或自动换 requestId 重建。

只有拿到完整有效的 `accounts` 并完成 Vantix 本地账号、兑换明细、服务码消费和批次状态
持久化后，才会将 operation/batch 标记完成。

## 批量续期

```http
POST /BaseUser/userInfo/batch/renewal
Content-Type: application/json
Accept: application/json
```

请求体：

```json
{
  "ids": [101, 102, 103],
  "dayType": 365,
  "requestId": "req-20260916-0003"
}
```

| 字段 | 规则 |
|---|---|
| `ids` | 必填 `integer[]`；来自 Vantix `service_account.cors_account_id`，校验为大于 0 的 Long，不使用本地账号、兑换明细、服务码或续期记录 ID。 |
| `dayType` | 必填整数；按 CORS 正式定义表示续期天数，取 Vantix 已冻结的 `durationDays`。不传规格码、单位或前端 label。 |
| `requestId` | CORS Redis 结果关联键。Vantix 在本地 `account_renewal.request_id` 和 `cors_operation.request_id` 稳定保存，重试复用，不重新生成。 |

当前 Vantix 本地续期仍是“一个账号 + 一个服务码”事务边界，因此单个本地 operation
发送一个 singleton `ids`；若未来本地请求出现不同 `durationDays`，必须按天数拆成独立
请求和独立 requestId，不能混用一个 `dayType`。只有 `account_source=EXCHANGE` 可以
使用服务码续期，`TEST` 与 `HISTORY_IMPORT` 仍在调用 CORS 前拒绝。

成功响应示例：

```json
{
  "code": 0,
  "message": "操作成功",
  "data": {
    "interface_name": "corsRenewal",
    "corsNameList": ["AB12000001", "AB12000002"]
  }
}
```

`code=0,data=null` 表示请求成功接受但 Redis 结果暂不可用，不是完成或失败；在约 120
秒窗口内使用原 requestId 重试。窗口起点只取第一次真实 CORS 尝试写入的
`cors_operation.first_attempt_at`，不以 `created_at` 回退；窗口结束后转为
`MANUAL_REVIEW`，不换 requestId 无限重放。拿到 `interface_name=corsRenewal` 且
`corsNameList` 非 null、数量/名称合法并与本地账号集合一致后，才允许本地 finalize。

续期确定性失败码（以及任何其他可解析、可信的非 0 code）：

| CORS code | Vantix 处理 | 处理 |
|---|---|---|
| `5314` | 保留 CORS code/message | 明确失败，释放本地服务码预留，不重试。 |
| `5345` | 保留 CORS code/message | 明确失败，释放本地服务码预留，不重试。 |
| `5316` | 保留 CORS code/message | 明确失败，释放本地服务码预留，不重试。 |
| 其他非 `0` code | 保留 CORS code/message | 同样视为明确失败，不按 UNKNOWN 无限重试。 |

5xx、超时、连接异常、非 JSON、格式错误和缺失 code 仍按 UNKNOWN/retry/
`MANUAL_REVIEW` 处理；CORS 已成功但本地 finalize 失败时继续使用原 requestId 恢复，
不创建第二次续期语义。

## 重置密码

```http
POST /BaseUser/userInfo/resetPass
Content-Type: application/json
Accept: application/json
```

请求体：

```json
{
  "id": 10001
}
```

`id` 是从 Vantix `service_account.cors_account_id` 严格解析得到的 CORS 真实账号主键，
不是 Vantix `service_account.id`。当前未确认 CORS 的 password 返回 data 契约，Vantix
只按通用 `code/message/data` 做兼容：`code=0` 成功，非 0 为业务失败，5xx/超时/格式错误
为结果未知；不臆造密码、token 或其他返回字段，也不进入 CORS operation retry。

## 自定义密码

```http
POST /BaseUser/userInfo/customPass
Content-Type: application/json
Accept: application/json
```

请求体示例（文档不展示真实密码）：

```json
{
  "id": 10001,
  "password": "***"
}
```

`password` 只在本次请求内存中用于调用 CORS，不写数据库、日志、audit detail、MDC、
`cors_operation` payload/result 或异常消息；请求对象和 HTTP 日志均不打印完整 body。
TEST/HISTORY_IMPORT 只禁止服务码续期，不因来源被额外禁止密码操作；仍须通过现有
GLOBAL/COMPANY/PERSONAL 账号访问权限和合法 CORS ID 校验。密码调用没有已确认的
requestId/Redis 契约，因此 timeout/连接未知只进入人工确认，不自动重放明文密码。

## Vantix 配置

| 配置项 | 默认值 | 作用 |
|---|---:|---|
| `vantix.cors.base-url` | `http://localhost:8081` | CORS 服务根地址。 |
| `vantix.cors.connect-timeout` | `2s` | 建立连接超时。 |
| `vantix.cors.read-timeout` | `5s` | 读取响应超时。 |
| `vantix.cors-operation.poll-interval` | `5s` | operation worker 轮询间隔。 |
| `vantix.cors-operation.retry-base-delay` | `30s` | retry 指数退避基数。 |
| `vantix.cors-operation.result-window` | `2m` | Redis 回传结果窗口。 |
| `vantix.cors-operation.claim-timeout` | `2m` | worker claim 租约超时。 |

## 代码参考

- [CORS HTTP 适配器](src/main/java/com/sinognss/cloud/vantix/infrastructure/cors/RestCorsAccountGateway.java)
- [请求和响应模型](src/main/java/com/sinognss/cloud/vantix/integration/cors/)
- [批次处理器](src/main/java/com/sinognss/cloud/vantix/application/cors/CorsOperationProcessor.java)
- [CORS 操作状态服务](src/main/java/com/sinognss/cloud/vantix/application/cors/CorsOperationStateService.java)
- [CORS 客户端配置](src/main/java/com/sinognss/cloud/vantix/config/CorsClientConfig.java)
- [HTTP 契约测试](src/test/java/com/sinognss/cloud/vantix/infrastructure/cors/RestCorsAccountGatewayTest.java)
