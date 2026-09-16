# Dashboard API

`GET /api/dashboard`

使用统一响应包装。查询从现有 Vantix 表实时聚合，单次 SQL 返回以下字段：

```json
{
  "serviceCodeTotal": 0,
  "serviceCodeWaiting": 0,
  "serviceCodeExpiring": 0,
  "serviceCodeExpired": 0,
  "serviceCodeProcessing": 0,
  "serviceCodeConsumed": 0,
  "accountTotal": 0,
  "accountWaiting": 0,
  "accountActive": 0,
  "accountExpired": 0,
  "accountDisabled": 0,
  "generationOrderTotal": 0,
  "exchangeTotal": 0,
  "renewalTotal": 0,
  "transferTotal": 0
}
```

服务码 waiting/expiring 使用现有 `vantix.service-code.upcoming-days` 和 `expire_at` 计算，状态仍遵循服务码查询 API。账号四个分类使用当前已落库的 CORS 状态：`cors_status=DISABLED` 为 disabled；只有 `cors_status=ENABLED` 时，才按 `cors_activation_status=WAITING_ACTIVATION/ACTIVE/EXPIRED` 分类。未知或不完整的 CORS 状态计入 total，但不伪造到这四个分类。

GLOBAL 查询全局服务码、账号、生成订单、兑换批次、续期记录和转赠批次数。COMPANY 查询当前公司资产。PERSONAL 查询当前公司的服务码、生成订单、兑换批次和转赠批次；账号及续期记录额外限定 `assigned_user_id` 为当前用户。

本轮未实现公告、销售额、支付额、B2B 商品、Device、Diagnostics 或混合最近操作时间线。
