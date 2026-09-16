# 服务码兑换配置 API

兑换配置属于经销商“我的服务码”，不是 `/api/config/**` 运营配置。每个公司只能成功配置一次账号前缀，成功后永久锁定。

## UserScope

- `COMPANY`：使用当前 `companyId`。
- `PERSONAL`：仍使用当前 `companyId`，同一公司的用户共享前缀。
- `GLOBAL`：本接口拒绝，因为没有“我的公司”语义。
- `UNSUPPORTED` 或缺少用户上下文：拒绝。

## `GET /api/service-code-exchange-config`

已配置：

```json
{
  "configured": true,
  "companyId": 10001,
  "accountPrefix": "AB12",
  "configuredByUserId": 123,
  "configuredByUserName": "operator",
  "configuredAt": "2026-09-16T10:00:00"
}
```

未配置时不返回 404，而是返回：

```json
{
  "configured": false,
  "companyId": 10001,
  "accountPrefix": null,
  "configuredByUserId": null,
  "configuredByUserName": null,
  "configuredAt": null
}
```

## `POST /api/service-code-exchange-config`

请求只接受账号前缀，`companyId` 必须从当前 UserScope 获取：

```json
{"accountPrefix": "AB12"}
```

前缀必须匹配 `^[A-Za-z0-9]{4}$`，即恰好 4 位数字或英文字母；系统会 trim 首尾空白，保存时保留大小写且大小写敏感，不会补零、截断、随机生成或强制转大写。

首次配置时，Vantix 从当前 `UserScope.companyId` 读取公司范围。如果本地 `dealer_company` 尚不存在，会调用 user-center 的精确公司查询接口确认公司存在，并补齐公司 ID/名称目录数据；新公司默认是 `FIRST_LEVEL`（`parent_company_id IS NULL`），然后才创建 `company_exchange_config`。user-center 不负责公司上下级关系或公司状态。

同一前缀的重复提交返回已存在配置，适合网络重试；如果本地已有配置，重试不会调用 user-center；不同前缀的再次提交返回 `EXCHANGE_CONFIG_LOCKED`。数据库通过 `UNIQUE(company_id)` 处理并发首次提交：竞争插入后重新读取，前缀相同按幂等成功处理，不同则锁定冲突。

## 兑换接口变化

`POST /api/service-code-exchanges` 的请求只保留 `requestId`、`companyId`、`specCode`、`generationSource`、`quantity`，不再接受 `accountPrefix`。创建新兑换批次前必须存在公司兑换配置，否则返回 `EXCHANGE_CONFIG_REQUIRED`。

已有 `requestId` 时先执行原有幂等检查，直接复用 `exchange_batch.account_prefix` 快照，不重新读取当前配置。第一次预留时读取的配置前缀会冻结到 `exchange_batch.account_prefix`，后续重试和 CORS 请求都使用该快照。客户端 payload hash 不包含服务端兑换配置前缀。
