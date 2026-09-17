# Vantix 对接 CORS 账号服务接口说明

本文记录 Vantix 服务码兑换流程当前调用的 CORS 新增账号契约。所有路径相对于
`vantix.cors.base-url`，请求不附加密码、secret 或其他认证头。

## 新增账号

```http
POST /userInfo/add
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
    "interface_name": "corsAdd",
    "corsNameList": ["AB12000001", "AB12000002"]
  }
}
```

`code = 0` 表示 CORS 请求成功接受，`data` 可以暂时为 `null`。当 `data` 有值时，
Vantix 只使用 `corsNameList` 入账；列表必须非 null、数量恰好等于 `addNum`、每个元素
非 blank 且批内不重复。校验不通过时不会标记 COMPLETE，也不会伪造账号名。

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

只有拿到完整有效的 `corsNameList` 并完成 Vantix 本地账号、兑换明细、服务码消费和批次
状态持久化后，才会将 operation/batch 标记完成。

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
