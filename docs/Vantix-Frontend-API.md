# Vantix 前端接口文档

> 基线：`master @ bc62b9fe0dd91cb9fdc8685ea1b0cf982a9d16f1`  
> 用途：前端页面联调。仅整理当前已实现、前端可能直接调用的 `/api/**` 接口；`/internal/**` 不提供 Web 前端直接调用。

## 0. 通用约定

### 0.1 统一返回

Controller 统一通过 `sino-cloud-base` 的 `CommonResult<T>` 返回。Vantix 仓库本身没有定义 `CommonResult` 的 JSON 字段名，因此本文只展示 **业务 data 内容**；前端按现有平台统一响应拦截器解包，不要根据本文自行定义外层 `code/message/data`。

### 0.2 登录与权限

Vantix 不新增登录接口或 Token Header，继续使用现有 `sino-cloud-base / UserHolder` 登录态。

| UserScope | 数据范围 |
|---|---|
| `GLOBAL` | 全局 |
| `COMPANY` | 当前公司 |
| `PERSONAL` | 当前公司；账号/续期额外限制当前用户 |
| `UNSUPPORTED` | 相关业务接口拒绝 |

关键规则：服务码、兑换、转赠属于公司资产；`PERSONAL` 查询服务账号和续期时必须同时满足当前公司 + `assignedUserId=当前用户`。`/api/config/**`、`/api/companies/page` 为平台管理能力。

### 0.3 分页

分页响应 data：

```json
{
  "records": [],
  "current": 1,
  "size": 20,
  "total": 0,
  "pages": 0
}
```

### 0.4 时间

查询参数使用 ISO LocalDateTime：

```text
2026-09-16T10:30:00
```

### 0.5 常用枚举

| 名称 | 值 |
|---|---|
| GenerationSource | `B2B`, `OFFLINE` |
| ServiceCodeStatus | `PENDING`, `PROCESSING`, `CONSUMED` |
| 服务码 displayStatus | `WAITING`, `EXPIRING`, `EXPIRED`, `PROCESSING`, `CONSUMED` |
| 服务账号展示状态 | `WAITING_ACTIVATION`, `ACTIVE`, `EXPIRED`, `DISABLED` |
| ExchangeStatus | `PROCESSING`, `COMPLETED`, `FAILED`, `MANUAL_REVIEW` |
| 续期状态 | `PROCESSING`, `COMPLETED`, `FAILED`, `MANUAL_REVIEW` |
| TransferType | `PARENT_CHILD`, `TO_SYSTEM`, `FROM_SYSTEM` |
| CompanyStatus | `ACTIVE`, `INACTIVE` |
| CompanyLevel | `FIRST_LEVEL`, `SECOND_LEVEL` |

## Controller URL 权限兼容改造

由于权限中心按 URL path 分配权限、不区分 HTTP Method，且不支持路径参数，本次对部分 API URL 进行了调整。以下接口使用静态 URL，原路径中的业务标识改为 Query 参数；同路径不同 Method 的接口通过动作后缀拆分。

| 功能 | Method | 原接口 | 新接口 | 参数变化 |
|---|---|---|---|---|
| 账号详情 | GET | `/api/service-accounts/{id}` | `/api/service-accounts/detail` | `id` 从 Path 改为 Query |
| 服务码详情 | GET | `/api/service-codes/{id}` | `/api/service-codes/detail` | `id` 从 Path 改为 Query |
| 单码转赠历史 | GET | `/api/service-codes/{id}/transfers` | `/api/service-codes/transfers/history` | `id` 从 Path 改为 Query |
| 兑换配置写入 | POST | `/api/service-code-exchange-config` | `/api/service-code-exchange-config/configure` | 无，增加动作后缀 |
| 发起兑换 | POST | `/api/service-code-exchanges` | `/api/service-code-exchanges/create` | 无，增加动作后缀 |
| 兑换结果 | GET | `/api/service-code-exchanges/{requestId}` | `/api/service-code-exchanges/result` | `requestId` 从 Path 改为 Query |
| 兑换日志详情 | GET | `/api/service-code-exchanges/{requestId}/detail` | `/api/service-code-exchanges/detail` | `requestId` 从 Path 改为 Query |
| 转赠批次详情 | GET | `/api/service-code-transfers/{transferNo}` | `/api/service-code-transfers/detail` | `transferNo` 从 Path 改为 Query |
| 来源订单详情 | GET | `/api/service-code-generations/orders/{orderNo}` | `/api/service-code-generations/orders/detail` | `orderNo` 从 Path 改为 Query |
| 生成批次详情 | GET | `/api/service-code-generation-batches/{batchNo}` | `/api/service-code-generation-batches/detail` | `batchNo` 从 Path 改为 Query |
| 公司详情 | GET | `/api/companies/{companyId}` | `/api/companies/detail` | `companyId` 从 Path 改为 Query |
| 公司直属下级 | GET | `/api/companies/{companyId}/children` | `/api/companies/children` | `companyId` 从 Path 改为 Query |
| 公司上下级维护 | PUT | `/api/companies/{companyId}/parent` | `/api/companies/parent/update` | `companyId` 从 Path 改为 Query |
| 服务规格创建 | POST | `/api/config/service-durations` | `/api/config/service-durations/create` | 增加动作后缀 |
| 服务规格更新 | PUT | `/api/config/service-durations/{id}` | `/api/config/service-durations/update` | `id` 从 Path 改为 Query |
| 服务规格详情 | GET | `/api/config/service-durations/{id}` | `/api/config/service-durations/detail` | `id` 从 Path 改为 Query |
| 系统公司更新 | PUT | `/api/config/system-company` | `/api/config/system-company/update` | 增加动作后缀 |
| 发起续期 | POST | `/api/account-renewals` | `/api/account-renewals/create` | 增加动作后缀 |
| 续期详情 | GET | `/api/account-renewals/{requestId}` | `/api/account-renewals/detail` | `requestId` 从 Path 改为 Query |
| 密码重置 | POST | `/api/service-accounts/{serviceAccountId}/password/reset` | `/api/service-accounts/password/reset` | `serviceAccountId` 从 Path 改为 Query |
| 密码重置结果 | GET | `/api/account-password-resets/{requestId}` | `/api/account-password-resets/result` | `requestId` 从 Path 改为 Query |
| 自定义密码 | POST | `/api/service-accounts/{serviceAccountId}/password` | `/api/service-accounts/password/custom` | `serviceAccountId` 从 Path 改为 Query，Body 只含 password |
| 临时查看密码 | POST | `/api/service-accounts/{serviceAccountId}/password/reveal` | `/api/service-accounts/password/reveal` | `serviceAccountId` 从 Path 改为 Query |

---

# 1. Dashboard

## GET `/api/dashboard`

**作用**：控制台核心统计。

**入参**：无。

**响应 data**：

```json
{
  "serviceCodeTotal": 100,
  "serviceCodeWaiting": 60,
  "serviceCodeExpiring": 10,
  "serviceCodeExpired": 5,
  "serviceCodeProcessing": 5,
  "serviceCodeConsumed": 20,
  "accountTotal": 30,
  "accountWaiting": 5,
  "accountActive": 20,
  "accountExpired": 3,
  "accountDisabled": 2,
  "generationOrderTotal": 12,
  "exchangeTotal": 8,
  "renewalTotal": 6,
  "transferTotal": 4
}
```

**使用**：GLOBAL 看全局；COMPANY 看当前公司；PERSONAL 的服务码/生成/兑换/转赠按公司，账号/续期按公司 + 当前用户。

---

# 2. 服务账号

## 2.1 GET `/api/service-accounts`

**作用**：账号管理/我的账号分页。

**Query**：

| 参数 | 必填 | 说明 |
|---|---:|---|
| current | 否 | 默认 1 |
| size | 否 | 默认 20 |
| keyword | 否 | 最长 100 |
| status | 否 | `WAITING_ACTIVATION/ACTIVE/EXPIRED/DISABLED` |
| specCode | 否 | 最长 32 |
| durationDays | 否 | >0，单位天 |
| ownerCompanyId | 否 | 公司 ID |
| assignedUserId | 否 | 分配用户 ID |

示例：

```http
GET /api/service-accounts?current=1&size=20&status=ACTIVE&keyword=AB12
```

**响应 data**：

```json
{
  "records": [
    {
      "id": 1001,
      "corsAccountId": "20001",
      "accountName": "AB12000001",
      "accountStatus": "NORMAL",
      "activationStatus": "ACTIVE",
      "status": "ACTIVE",
      "specCode": "SC123456789ABC",
      "displayName": "一年服务",
      "serviceType": "RTK",
      "durationDays": 365,
      "ownerCompanyId": 10001,
      "ownerCompanyName": "经销商A",
      "assignedUserId": 90001,
      "sourceServiceCodeId": 501,
      "sourceServiceCode": "SERVICE-CODE-XXXX",
      "exchangeBatchId": 301,
      "exchangeBatchNo": "EXB202609160001",
      "exchangeRequestId": "EXCHANGE_202609160001",
      "activatedAt": "2026-09-16T10:00:00",
      "expireAt": "2027-09-16T10:00:00",
      "corsCreatedAt": "2026-09-15T09:00:00",
      "corsUpdatedAt": "2026-09-16T10:00:00",
      "lastSyncAt": "2026-09-16T10:01:00",
      "createdAt": "2026-09-15T09:00:00",
      "updatedAt": "2026-09-16T10:01:00"
    }
  ],
  "current": 1,
  "size": 20,
  "total": 1,
  "pages": 1
}
```

**注意**：PERSONAL 只能看到当前公司且分配给当前用户的账号；列表不返回密码。

## 2.2 GET `/api/service-accounts/statistics`

**作用**：账号状态统计卡片。

**Query**：`keyword, specCode, durationDays, ownerCompanyId, assignedUserId`。

**响应 data**：

```json
{
  "total": 30,
  "waiting": 5,
  "active": 20,
  "expired": 3,
  "disabled": 2
}
```

## 2.3 GET `/api/service-accounts/detail`

> **接口地址已变更**
>
> 原接口：`GET /api/service-accounts/{id}`
>
> 新接口：`GET /api/service-accounts/detail?id={id}`
>
> 变更：`id` 从 Path 参数调整为 Query 参数。

**作用**：账号详情。

**Query**：`id`，必填，服务账号 ID。

示例：`GET /api/service-accounts/detail?id=1001`

**响应**：单个账号对象，字段与分页记录一致。

---

# 3. 服务码

## 3.1 GET `/api/service-codes`

**作用**：服务码分页。

**Query**：

```text
current, size, keyword,
status=PENDING|PROCESSING|CONSUMED,
displayStatus=WAITING|EXPIRING|EXPIRED|PROCESSING|CONSUMED,
specCode, durationDays, sourceOrderNo, ownerCompanyId
```

**响应 data**：

```json
{
  "records": [
    {
      "id": 501,
      "code": "SERVICE-CODE-XXXX",
      "sourceOrderId": 101,
      "sourceOrderNo": "ORDER202609160001",
      "ownerCompanyId": 10001,
      "ownerCompanyName": "经销商A",
      "specCode": "SC123456789ABC",
      "serviceType": "RTK",
      "durationDays": 365,
      "codeSilenceDays": 30,
      "expireAt": "2026-10-16T10:00:00",
      "status": "PENDING",
      "displayStatus": "WAITING",
      "processingType": null,
      "processingRequestId": null,
      "consumeType": null,
      "consumedAt": null,
      "version": 0,
      "createdAt": "2026-09-16T10:00:00",
      "updatedAt": "2026-09-16T10:00:00",
      "generateBatchId": 201,
      "batchNo": "BATCH202609160001",
      "displayName": "一年服务"
    }
  ],
  "current": 1,
  "size": 20,
  "total": 1,
  "pages": 1
}
```

## 3.2 GET `/api/service-codes/statistics`

**Query**：`keyword, specCode, durationDays, sourceOrderNo, ownerCompanyId`。

**响应 data**：

```json
{
  "total": 100,
  "waiting": 60,
  "expiring": 10,
  "expired": 5,
  "processing": 5,
  "consumed": 20
}
```

统计接口不接收状态筛选；前端点状态卡片时只把状态加到列表接口。

## 3.3 GET `/api/service-codes/detail`

> **接口地址已变更**
>
> 原接口：`GET /api/service-codes/{id}`
>
> 新接口：`GET /api/service-codes/detail?id={id}`
>
> 变更：`id` 从 Path 参数调整为 Query 参数。

**作用**：服务码详情。响应字段同列表单条记录。

**Query**：`id`，必填，服务码 ID。

示例：`GET /api/service-codes/detail?id=501`

---

# 4. 兑换配置

## 4.1 GET `/api/service-code-exchange-config`

**作用**：查询当前公司是否已经设置 CORS 账号前缀。

**响应 data**：

```json
{
  "configured": true,
  "companyId": 10001,
  "accountPrefix": "AB12",
  "configuredByUserId": 90001,
  "configuredByUserName": "张三",
  "configuredAt": "2026-09-16T09:00:00"
}
```

未配置时 `configured=false`，配置字段可为 null。

## 4.2 POST `/api/service-code-exchange-config/configure`

> **接口地址已变更**
>
> 原接口：`POST /api/service-code-exchange-config`
>
> 新接口：`POST /api/service-code-exchange-config/configure`
>
> 变更：与 GET 查询接口使用不同静态 URL，请求 Body 不变。

**Body**：

```json
{
  "accountPrefix": "AB12"
}
```

**规则**：trim 后必须匹配 `^[A-Za-z0-9]{4}$`；一个公司只能配置一次；相同 prefix 重复提交幂等成功，不同 prefix 会被拒绝。

**响应**：同查询接口。

---

# 5. 可兑换服务码分组

## GET `/api/service-code-exchange-groups`

**作用**：兑换弹窗展示当前可兑换库存。

**Query**：`companyId` 可选；普通公司最终只能访问自身范围。

**响应 data**：

```json
[
  {
    "specCode": "SC123456789ABC",
    "displayName": "一年服务",
    "serviceType": "RTK",
    "generationSource": "B2B",
    "availableCount": 100,
    "earliestExpireAt": "2026-10-01T00:00:00"
  }
]
```

前端选择 `specCode + generationSource + quantity` 后发起兑换。

---

# 6. 服务码兑换 / 兑换日志

## 6.1 POST `/api/service-code-exchanges/create`

> **接口地址已变更**
>
> 原接口：`POST /api/service-code-exchanges`
>
> 新接口：`POST /api/service-code-exchanges/create`
>
> 变更：与 GET 日志列表使用不同静态 URL，请求 Body 不变。

**作用**：发起兑换。

**Body**：

```json
{
  "requestId": "EXCHANGE_202609160001",
  "companyId": 10001,
  "specCode": "SC123456789ABC",
  "generationSource": "B2B",
  "quantity": 10
}
```

**注意**：不传 `accountPrefix`，后端从公司兑换配置读取并冻结。

**响应 data**：

```json
{
  "batchId": 301,
  "operationId": 401,
  "created": true
}
```

`created=false` 表示同一 requestId 已存在并复用。网络超时必须复用同一个 `requestId` 重试。

## 6.2 GET `/api/service-code-exchanges/result`

> **接口地址已变更**
>
> 原接口：`GET /api/service-code-exchanges/{requestId}`
>
> 新接口：`GET /api/service-code-exchanges/result?requestId={requestId}`
>
> 变更：`requestId` 从 Path 参数调整为 Query 参数。

**作用**：查询一次兑换处理结果。

**Query**：`requestId`，必填。

**响应 data**：

```json
{
  "requestId": "EXCHANGE_202609160001",
  "exchangeBatchNo": "EXB202609160001",
  "companyId": 10001,
  "specCode": "SC123456789ABC",
  "generationSource": "B2B",
  "quantity": 2,
  "status": "COMPLETED",
  "accountPrefix": "AB12",
  "createdAt": "2026-09-16T10:00:00",
  "completedAt": "2026-09-16T10:00:10",
  "accounts": [
    {
      "accountId": "20001",
      "account": "AB12000001",
      "accountStatus": "NORMAL",
      "activationStatus": "WAITING_ACTIVATION",
      "activatedAt": null,
      "expireAt": null
    }
  ]
}
```

## 6.3 GET `/api/service-code-exchanges`

**作用**：兑换日志分页。

**Query**：

```text
current, size, keyword,
status=PROCESSING|COMPLETED|FAILED|MANUAL_REVIEW,
specCode, ownerCompanyId, createdFrom, createdTo
```

**响应 data**：

```json
{
  "records": [
    {
      "requestId": "EXCHANGE_202609160001",
      "batchId": 301,
      "batchNo": "EXB202609160001",
      "ownerCompanyId": 10001,
      "ownerCompanyName": "经销商A",
      "specCode": "SC123456789ABC",
      "displayName": "一年服务",
      "serviceType": "RTK",
      "durationDays": 365,
      "quantity": 10,
      "successQuantity": 10,
      "status": "COMPLETED",
      "accountPrefix": "AB12",
      "generationSource": "B2B",
      "createdAt": "2026-09-16T10:00:00",
      "completedAt": "2026-09-16T10:00:10",
      "lastErrorCode": null,
      "lastErrorMessage": null
    }
  ],
  "current": 1,
  "size": 20,
  "total": 1,
  "pages": 1
}
```

`displayName` 为首次 reservation 时冻结在 exchange batch 的历史快照。

## 6.4 GET `/api/service-code-exchanges/detail`

> **接口地址已变更**
>
> 原接口：`GET /api/service-code-exchanges/{requestId}/detail`
>
> 新接口：`GET /api/service-code-exchanges/detail?requestId={requestId}`
>
> 变更：`requestId` 从 Path 参数调整为 Query 参数。

**作用**：兑换日志详情。

**Query**：`requestId`，必填。

**响应 data**：

```json
{
  "batch": {
    "requestId": "EXCHANGE_202609160001",
    "batchId": 301,
    "batchNo": "EXB202609160001",
    "ownerCompanyId": 10001,
    "ownerCompanyName": "经销商A",
    "specCode": "SC123456789ABC",
    "displayName": "一年服务",
    "serviceType": "RTK",
    "durationDays": 365,
    "quantity": 2,
    "successQuantity": 2,
    "status": "COMPLETED",
    "accountPrefix": "AB12",
    "generationSource": "B2B",
    "createdAt": "2026-09-16T10:00:00",
    "completedAt": "2026-09-16T10:00:10",
    "lastErrorCode": null,
    "lastErrorMessage": null
  },
  "items": [
    {
      "serviceCodeId": 501,
      "serviceCode": "SERVICE-CODE-XXXX",
      "serviceAccountId": 1001,
      "corsAccountId": "20001",
      "accountName": "AB12000001",
      "status": "COMPLETED"
    }
  ]
}
```

---

# 7. 服务码转赠 / 转赠日志

## 7.1 POST `/api/service-codes/transfers`

**Body**：

```json
{
  "fromCompanyId": 10001,
  "toCompanyId": 10002,
  "serviceCodeIds": [501, 502],
  "reason": "渠道调拨"
}
```

`serviceCodeIds` 非空、最多 500。后端按公司关系、系统公司、服务码状态和有效期做校验，整批成功或整批失败。

**响应 data**：

```json
{
  "transferNo": "TR202609160001",
  "transferredCount": 2
}
```

## 7.2 GET `/api/service-codes/transfers/history`

> **接口地址已变更**
>
> 原接口：`GET /api/service-codes/{id}/transfers`
>
> 新接口：`GET /api/service-codes/transfers/history?id={id}`
>
> 变更：`id` 从 Path 参数调整为 Query 参数。

**作用**：查看单个服务码的转赠历史。

**Query**：`id`，必填，服务码 ID。

**响应 data**：

```json
[
  {
    "id": 1,
    "transferNo": "TR202609160001",
    "serviceCodeId": 501,
    "serviceCode": "SERVICE-CODE-XXXX",
    "fromCompanyId": 10001,
    "toCompanyId": 10002,
    "transferType": "PARENT_CHILD",
    "reason": "渠道调拨",
    "operatorUserId": 90001,
    "operatorUserName": "张三",
    "createdAt": "2026-09-16T10:00:00"
  }
]
```

## 7.3 GET `/api/service-code-transfers`

**Query**：

```text
current, size, keyword, transferType,
fromCompanyId, toCompanyId, createdFrom, createdTo,
specCode, durationDays
```

**响应 data（单条核心字段）**：

```json
{
  "transferNo": "TR202609160001",
  "fromCompanyId": 10001,
  "fromCompanyName": "经销商A",
  "toCompanyId": 10002,
  "toCompanyName": "经销商B",
  "transferType": "PARENT_CHILD",
  "quantity": 2,
  "specCode": "SC123456789ABC",
  "serviceType": "RTK",
  "durationDays": 365,
  "reason": "渠道调拨",
  "operatorUserId": 90001,
  "operatorUserName": "张三",
  "createdAt": "2026-09-16T10:00:00"
}
```

完整响应仍是分页结构。

## 7.4 GET `/api/service-code-transfers/detail`

> **接口地址已变更**
>
> 原接口：`GET /api/service-code-transfers/{transferNo}`
>
> 新接口：`GET /api/service-code-transfers/detail?transferNo={transferNo}`
>
> 变更：`transferNo` 从 Path 参数调整为 Query 参数。

**Query**：`transferNo`，必填，转赠批次号。

**响应 data**：

```json
{
  "batch": {
    "transferNo": "TR202609160001",
    "fromCompanyId": 10001,
    "fromCompanyName": "经销商A",
    "toCompanyId": 10002,
    "toCompanyName": "经销商B",
    "transferType": "PARENT_CHILD",
    "quantity": 2,
    "specCode": "SC123456789ABC",
    "serviceType": "RTK",
    "durationDays": 365,
    "reason": "渠道调拨",
    "operatorUserId": 90001,
    "operatorUserName": "张三",
    "createdAt": "2026-09-16T10:00:00"
  },
  "items": [
    {
      "serviceCodeId": 501,
      "serviceCode": "SERVICE-CODE-XXXX",
      "specCode": "SC123456789ABC",
      "displayName": "一年服务",
      "serviceType": "RTK",
      "durationDays": 365,
      "expireAt": "2026-10-16T10:00:00"
    }
  ]
}
```

---

# 8. 来源订单 / 生成批次

> 这里是 Vantix 的服务码来源/生成记录，不是 B2B 商品支付订单。

## 8.1 GET `/api/service-code-generations/orders`

**Query**：

```text
current, size, keyword, generationSource,
status, ownerCompanyId, createdFrom, createdTo
```

**响应 data（记录字段）**：

```json
{
  "id": 101,
  "requestId": "GEN202609160001",
  "generationSource": "B2B",
  "sourceOrderNo": "ORDER202609160001",
  "sourceOrderTime": "2026-09-16T09:00:00",
  "ownerCompanyId": 10001,
  "ownerCompanyName": "经销商A",
  "itemCount": 2,
  "totalQuantity": 100,
  "status": "COMPLETED",
  "operatorUserId": 90001,
  "operatorUserName": "张三",
  "createdAt": "2026-09-16T09:01:00",
  "updatedAt": "2026-09-16T09:01:05"
}
```

完整响应为分页结构。

## 8.2 GET `/api/service-code-generations/orders/statistics`

**Query**：`keyword, status, ownerCompanyId, createdFrom, createdTo`。

**响应 data**：

```json
{
  "total": 12,
  "b2b": 10,
  "offline": 2
}
```

## 8.3 GET `/api/service-code-generations/orders/detail`

> **接口地址已变更**
>
> 原接口：`GET /api/service-code-generations/orders/{orderNo}`
>
> 新接口：`GET /api/service-code-generations/orders/detail?orderNo={orderNo}`
>
> 变更：`orderNo` 从 Path 参数调整为 Query 参数。

**Query**：`orderNo` 必填，`companyId` 可选。

**响应 data**：数组。

```json
[
  {
    "requestId": "GEN202609160001",
    "generationSource": "B2B",
    "orderNo": "ORDER202609160001",
    "companyId": 10001,
    "orderTime": "2026-09-16T09:00:00",
    "status": "COMPLETED",
    "itemCount": 2,
    "totalQuantity": 100,
    "items": [
      {
        "specCode": "SC123456789ABC",
        "displayName": "一年服务",
        "quantity": 50,
        "generatedCount": 50,
        "batchNo": "BATCH202609160001",
        "status": "COMPLETED"
      }
    ],
    "idempotent": false,
    "createdAt": "2026-09-16T09:01:00"
  }
]
```

## 8.4 GET `/api/service-code-generation-batches/detail`

> **接口地址已变更**
>
> 原接口：`GET /api/service-code-generation-batches/{batchNo}`
>
> 新接口：`GET /api/service-code-generation-batches/detail?batchNo={batchNo}`
>
> 变更：`batchNo` 从 Path 参数调整为 Query 参数。

**Query**：`batchNo`，必填，生成批次号。

**响应 data**：

```json
{
  "batchNo": "BATCH202609160001",
  "generationSource": "B2B",
  "sourceOrderNo": "ORDER202609160001",
  "companyId": 10001,
  "specCode": "SC123456789ABC",
  "displayName": "一年服务",
  "quantity": 50,
  "generatedCount": 50,
  "status": "COMPLETED",
  "createdAt": "2026-09-16T09:01:00"
}
```

## 8.5 GET `/api/service-code-generation-batches`

**Query**：`orderNo`，`companyId` 可选。

**作用**：按来源订单号查询该订单的生成批次数组，单条格式同 8.4。

---

# 9. 线下服务码导入

## 9.1 GET `/api/service-codes/offline-import/template`

**作用**：下载 Excel 模板。

响应：

```text
Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
filename: 线下订单服务码导入模板.xlsx
```

前端按 Blob 文件下载。

## 9.2 POST `/api/service-codes/offline-import?companyId=10001`

**Content-Type**：`multipart/form-data`

**FormData**：

```text
file=<xlsx文件>
```

Part 名必须为 `file`。

**响应 data**：

```json
{
  "batchCount": 2,
  "generatedCount": 100,
  "batches": [
    {
      "batchNo": "BATCH202609160001",
      "generationSource": "OFFLINE",
      "sourceOrderNo": "OFFLINE202609160001",
      "companyId": 10001,
      "specCode": "SC123456789ABC",
      "displayName": "一年服务",
      "quantity": 50,
      "generatedCount": 50,
      "status": "COMPLETED",
      "createdAt": "2026-09-16T09:01:00"
    }
  ]
}
```

---

# 10. 公司 / 经销商

## 10.1 GET `/api/companies`

**Query**：`name`，`status=ACTIVE|INACTIVE`。

**作用**：轻量公司列表；GLOBAL 可查全部，非 GLOBAL 按当前公司范围。

**响应 data**：

```json
[
  {
    "companyId": 10001,
    "companyName": "经销商A",
    "parentCompanyId": null,
    "companyStatus": "ACTIVE",
    "companySyncedAt": "2026-09-16T06:00:00",
    "createdAt": "2026-09-01T10:00:00",
    "updatedAt": "2026-09-16T06:00:00"
  }
]
```

## 10.2 GET `/api/companies/page`

**仅 GLOBAL。**

**Query**：

```text
current, size, keyword,
status=ACTIVE|INACTIVE,
parentCompanyId,
level=FIRST_LEVEL|SECOND_LEVEL
```

**响应 data（记录字段）**：

```json
{
  "companyId": 10002,
  "companyName": "二级经销商B",
  "parentCompanyId": 10001,
  "parentCompanyName": "一级经销商A",
  "level": "SECOND_LEVEL",
  "companyStatus": "ACTIVE",
  "companySyncedAt": "2026-09-16T06:00:00",
  "createdAt": "2026-09-01T10:00:00",
  "updatedAt": "2026-09-16T06:00:00"
}
```

完整响应为分页结构。

## 10.3 GET `/api/companies/detail`

> **接口地址已变更**
>
> 原接口：`GET /api/companies/{companyId}`
>
> 新接口：`GET /api/companies/detail?companyId={companyId}`
>
> 变更：`companyId` 从 Path 参数调整为 Query 参数。

**作用**：公司详情。响应字段同 10.1 单条。

**Query**：`companyId`，必填。

## 10.4 GET `/api/companies/children`

> **接口地址已变更**
>
> 原接口：`GET /api/companies/{companyId}/children`
>
> 新接口：`GET /api/companies/children?companyId={companyId}`
>
> 变更：`companyId` 从 Path 参数调整为 Query 参数。

**作用**：查询直属下级，响应为公司对象数组。

**Query**：`companyId`，必填。

## 10.5 GET `/api/companies/partners`

**Query**：`companyId` 可选。

**规则**：GLOBAL 需要指定目标公司；COMPANY/PERSONAL 通常不传，后端使用当前公司，不能借参数越权。

**响应 data**：

```json
[
  {
    "companyId": 10002,
    "companyName": "二级经销商B",
    "companyStatus": "ACTIVE",
    "relationshipType": "CHILD"
  }
]
```

## 10.6 PUT `/api/companies/parent/update`

> **接口地址已变更**
>
> 原接口：`PUT /api/companies/{companyId}/parent`
>
> 新接口：`PUT /api/companies/parent/update?companyId={companyId}`
>
> 变更：`companyId` 从 Path 参数调整为 Query 参数。

**Query**：`companyId`，必填。

设为二级：

```json
{
  "parentCompanyId": 10001,
  "reason": "调整经销商归属"
}
```

恢复一级：

```json
{
  "parentCompanyId": null,
  "reason": "解除上级关系"
}
```

**响应**：更新后的公司对象。

**规则**：`parentCompanyId=null` 即一级；非 null 即二级；不允许自关联和三级结构。

---

# 11. 配置中心

> `/api/config/**` 仅 GLOBAL。

## 11.1 GET `/api/config/service-durations`

**响应 data**：

```json
[
  {
    "id": 1,
    "specCode": "SC123456789ABC",
    "displayName": "一年服务",
    "serviceType": "RTK",
    "durationDays": 365,
    "codeSilenceDays": 30,
    "accountSilenceDays": 30,
    "enabled": true,
    "remark": "标准一年",
    "createdBy": "admin",
    "updatedBy": "admin",
    "createdAt": "2026-09-01T10:00:00",
    "updatedAt": "2026-09-01T10:00:00"
  }
]
```

## 11.2 GET `/api/config/service-durations/detail`

> **接口地址已变更**
>
> 原接口：`GET /api/config/service-durations/{id}`
>
> 新接口：`GET /api/config/service-durations/detail?id={id}`
>
> 变更：`id` 从 Path 参数调整为 Query 参数。

**Query**：`id`，必填，服务规格 ID。

响应为单个服务规格对象。

## 11.3 POST `/api/config/service-durations/create`

> **接口地址已变更**
>
> 原接口：`POST /api/config/service-durations`
>
> 新接口：`POST /api/config/service-durations/create`
>
> 变更：与 GET 列表接口使用不同静态 URL，请求 Body 不变。

**Body**：

```json
{
  "displayName": "一年服务",
  "serviceType": "RTK",
  "durationDays": 365,
  "codeSilenceDays": 30,
  "accountSilenceDays": 30,
  "enabled": true,
  "remark": "标准一年"
}
```

`specCode` 由后端生成。

## 11.4 PUT `/api/config/service-durations/update`

> **接口地址已变更**
>
> 原接口：`PUT /api/config/service-durations/{id}`
>
> 新接口：`PUT /api/config/service-durations/update?id={id}`
> 变更：`id` 从 Path 参数调整为 Query 参数。

**Query**：`id`，必填，服务规格 ID。

**Body**：

```json
{
  "displayName": "一年标准服务",
  "codeSilenceDays": 45,
  "accountSilenceDays": 30,
  "enabled": true,
  "remark": "调整展示名"
}
```

只允许改 `displayName/codeSilenceDays/accountSilenceDays/enabled/remark`。`specCode/serviceType/durationDays` 不可修改。

## 11.5 GET `/api/config/system-company`

**响应 data**：直接返回公司 ID，例如：

```json
10001
```

## 11.6 PUT `/api/config/system-company/update`

> **接口地址已变更**
>
> 原接口：`PUT /api/config/system-company`
>
> 新接口：`PUT /api/config/system-company/update`
>
> 变更：与 GET 查询接口使用不同静态 URL，请求 Body 不变。

**Body**：

```json
{
  "systemCompanyId": 10001
}
```

响应 data 为更新后的系统公司 ID。

---

# 12. 账号续期 / 续期日志

## 12.1 GET `/api/account-renewals`

**Query**：

```text
current, size, keyword,
status=PROCESSING|COMPLETED|FAILED|MANUAL_REVIEW,
ownerCompanyId, createdFrom, createdTo
```

**响应 data（记录字段）**：

```json
{
  "renewalId": 701,
  "requestId": "RENEW_202609160001",
  "serviceAccountId": 1001,
  "accountName": "AB12000001",
  "corsAccountId": "20001",
  "ownerCompanyId": 10001,
  "ownerCompanyName": "经销商A",
  "assignedUserId": 90001,
  "serviceCodeId": 502,
  "serviceCode": "SERVICE-CODE-RENEW",
  "specCode": "SC123456789ABC",
  "displayName": "一年服务",
  "serviceType": "RTK",
  "durationDays": 365,
  "status": "COMPLETED",
  "currentAccountExpireAt": "2028-09-16T10:00:00",
  "lastErrorCode": null,
  "lastErrorMessage": null,
  "createdAt": "2026-09-16T10:00:00",
  "updatedAt": "2026-09-16T10:00:10",
  "completedAt": "2026-09-16T10:00:10"
}
```

完整响应为分页结构。PERSONAL 只能看到分配给当前用户的账号续期记录。

## 12.2 GET `/api/account-renewals/detail`

> **接口地址已变更**
>
> 原接口：`GET /api/account-renewals/{requestId}`
>
> 新接口：`GET /api/account-renewals/detail?requestId={requestId}`
>
> 变更：`requestId` 从 Path 参数调整为 Query 参数。

**作用**：续期详情；此 GET 是本地查询，不依赖 CORS renewal 开关。

**Query**：`requestId`，必填。

**响应 data**：

```json
{
  "renewalId": 701,
  "requestId": "RENEW_202609160001",
  "serviceAccountId": 1001,
  "serviceCodeId": 502,
  "specCode": "SC123456789ABC",
  "serviceType": "RTK",
  "durationDays": 365,
  "codeSilenceDays": 30,
  "status": "COMPLETED",
  "lastErrorCode": null,
  "lastErrorMessage": null,
  "accountExpireAt": "2028-09-16T10:00:00",
  "createdAt": "2026-09-16T10:00:00",
  "updatedAt": "2026-09-16T10:00:10",
  "completedAt": "2026-09-16T10:00:10",
  "accountName": "AB12000001",
  "corsAccountId": "20001",
  "ownerCompanyId": 10001,
  "ownerCompanyName": "经销商A",
  "assignedUserId": 90001,
  "serviceCode": "SERVICE-CODE-RENEW",
  "displayName": "一年服务"
}
```

## 12.3 POST `/api/account-renewals/create`

> **接口地址已变更**
>
> 原接口：`POST /api/account-renewals`
>
> 新接口：`POST /api/account-renewals/create`
>
> 变更：与 GET 日志列表使用不同静态 URL，请求 Body 不变。

> 条件接口：仅当 `vantix.cors.renewal.enabled=true` 且 CORS renewal/status Gateway 可用时注册。

**Body**：

```json
{
  "requestId": "RENEW_202609160001",
  "serviceAccountId": 1001,
  "serviceCodeId": 502
}
```

**响应 data**：

```json
{
  "renewalId": 701,
  "operationId": 801,
  "requestId": "RENEW_202609160001",
  "created": true
}
```

一次请求为“一个账号 + 一个服务码”。网络超时要复用相同 requestId。当前没有批量续期写接口。
底层 CORS 使用 `POST /BaseUser/userInfo/batch/renewal`，`ids` 来自
`service_account.cors_account_id`，`dayType` 使用冻结的 `durationDays`；TEST/HISTORY_IMPORT
仍不允许服务码续期。

---

# 13. 密码能力（CORS 条件接口）

> 以下接口只有 `vantix.cors.password.enabled=true` 且对应 CORS Gateway 存在时才注册。账号列表/详情不会返回密码。

## 13.1 POST `/api/service-accounts/password/reset`

> **接口地址已变更**
>
> 原接口：`POST /api/service-accounts/{serviceAccountId}/password/reset`
>
> 新接口：`POST /api/service-accounts/password/reset?serviceAccountId={serviceAccountId}`
>
> 变更：`serviceAccountId` 从 Path 参数调整为 Query 参数。

**Query**：`serviceAccountId`，必填。

**Body**：

```json
{
  "requestId": "PWD_RESET_202609160001"
}
```

这里的 requestId 仅用于 Vantix 本地结果查询和保持既有前端契约，不会发送给 CORS。
底层 CORS 固定调用 `POST /BaseUser/userInfo/resetPass`，Body 为 `{"id":10001}`，其中
`id` 来自 `service_account.cors_account_id`。

**响应 data**：

```json
{
  "requestId": "PWD_RESET_202609160001",
  "serviceAccountId": 1001,
  "status": "SUCCEEDED"
}
```

不返回密码、token 或未确认的 CORS response data。

## 13.2 GET `/api/account-password-resets/result`

> **接口地址已变更**
>
> 原接口：`GET /api/account-password-resets/{requestId}`
>
> 新接口：`GET /api/account-password-resets/result?requestId={requestId}`
>
> 变更：`requestId` 从 Path 参数调整为 Query 参数。

**Query**：`requestId`，必填。

**响应 data**：

```json
{
  "requestId": "PWD_RESET_202609160001",
  "serviceAccountId": 1001,
  "account": "AB12000001",
  "status": "COMPLETED",
  "lastErrorCode": null,
  "lastErrorMessage": null,
  "createdAt": "2026-09-16T10:00:00",
  "updatedAt": "2026-09-16T10:00:05",
  "completedAt": "2026-09-16T10:00:05"
}
```

## 13.3 POST `/api/service-accounts/password/reveal`

> **接口地址已变更**
>
> 原接口：`POST /api/service-accounts/{serviceAccountId}/password/reveal`
>
> 新接口：`POST /api/service-accounts/password/reveal?serviceAccountId={serviceAccountId}`
>
> 变更：`serviceAccountId` 从 Path 参数调整为 Query 参数。

**Query**：`serviceAccountId`，必填。

**Body**：

```json
{
  "requestId": "PWD_REVEAL_202609160001"
}
```

**响应 data**：

```json
{
  "serviceAccountId": 1001,
  "account": "AB12000001",
  "password": "一次性明文"
}
```

该接口带 `no-store/no-cache` 响应头。前端只能临时展示，不得写 localStorage/sessionStorage/IndexedDB、不得 console、不得埋点上传、不得长期放全局 store。

## 13.4 POST `/api/service-accounts/password/custom`

**Query**：`serviceAccountId`，必填。

**Body**：

```json
{
  "password": "***"
}
```

底层 CORS 固定调用 `POST /BaseUser/userInfo/customPass`，Body 为
`{"id":10001,"password":"***"}`；`id` 来自 `service_account.cors_account_id`。
TEST/HISTORY_IMPORT 只禁止服务码续期；具备当前账号访问权限且 CORS ID 合法时，不因来源
被额外禁止密码操作。密码不进入数据库、日志、audit detail、MDC 或 operation payload，
timeout/连接未知不自动重试。

---

# 14. 前端推荐页面调用顺序

### 我的服务码

```text
GET /api/service-codes/statistics
GET /api/service-codes
```

兑换：

```text
GET  /api/service-code-exchange-config
  ↓ 未配置时
POST /api/service-code-exchange-config/configure
  ↓
GET  /api/service-code-exchange-groups
  ↓
POST /api/service-code-exchanges/create
  ↓
GET  /api/service-code-exchanges/result?requestId={requestId}
```

### 账号管理 / 我的账号

```text
GET /api/service-accounts/statistics
GET /api/service-accounts
GET /api/service-accounts/detail?id={id}
```

续期：

```text
POST /api/account-renewals/create
GET  /api/account-renewals/detail?requestId={requestId}
```

### 操作日志

```text
兑换：GET /api/service-code-exchanges
      GET /api/service-code-exchanges/detail?requestId={requestId}

续期：GET /api/account-renewals
      GET /api/account-renewals/detail?requestId={requestId}

转赠：GET /api/service-code-transfers
      GET /api/service-code-transfers/detail?transferNo={transferNo}
```

---

# 15. 快速接口索引

| 模块 | Method | URL | 作用 |
|---|---|---|---|
| Dashboard | GET | `/api/dashboard` | 控制台统计 |
| 账号 | GET | `/api/service-accounts` | 账号分页 |
| 账号 | GET | `/api/service-accounts/statistics` | 账号统计 |
| 账号 | GET | `/api/service-accounts/detail`（`id` Query） | 账号详情 |
| 服务码 | GET | `/api/service-codes` | 服务码分页 |
| 服务码 | GET | `/api/service-codes/statistics` | 服务码统计 |
| 服务码 | GET | `/api/service-codes/detail`（`id` Query） | 服务码详情 |
| 转赠 | POST | `/api/service-codes/transfers` | 发起转赠 |
| 转赠 | GET | `/api/service-codes/transfers/history`（`id` Query） | 单码转赠历史 |
| 兑换配置 | GET | `/api/service-code-exchange-config` | 查询 prefix |
| 兑换配置 | POST | `/api/service-code-exchange-config/configure` | 首次设置 prefix |
| 兑换 | GET | `/api/service-code-exchange-groups` | 可兑换库存分组 |
| 兑换 | POST | `/api/service-code-exchanges/create` | 发起兑换 |
| 兑换 | GET | `/api/service-code-exchanges/result`（`requestId` Query） | 查询兑换结果 |
| 兑换日志 | GET | `/api/service-code-exchanges` | 日志分页 |
| 兑换日志 | GET | `/api/service-code-exchanges/detail`（`requestId` Query） | 日志详情 |
| 转赠日志 | GET | `/api/service-code-transfers` | 日志分页 |
| 转赠日志 | GET | `/api/service-code-transfers/detail`（`transferNo` Query） | 日志详情 |
| 来源订单 | GET | `/api/service-code-generations/orders` | 分页 |
| 来源订单 | GET | `/api/service-code-generations/orders/statistics` | 统计 |
| 来源订单 | GET | `/api/service-code-generations/orders/detail`（`orderNo` Query） | 详情 |
| 生成批次 | GET | `/api/service-code-generation-batches/detail`（`batchNo` Query） | 批次详情 |
| 生成批次 | GET | `/api/service-code-generation-batches` | 按订单查批次 |
| 线下导入 | GET | `/api/service-codes/offline-import/template` | 下载模板 |
| 线下导入 | POST | `/api/service-codes/offline-import` | 上传 Excel |
| 公司 | GET | `/api/companies` | 轻量列表 |
| 公司 | GET | `/api/companies/page` | 全局分页 |
| 公司 | GET | `/api/companies/detail`（`companyId` Query） | 详情 |
| 公司 | GET | `/api/companies/children`（`companyId` Query） | 直属下级 |
| 公司 | GET | `/api/companies/partners` | 合作伙伴 |
| 公司 | PUT | `/api/companies/parent/update`（`companyId` Query） | 维护上下级 |
| 配置 | GET | `/api/config/service-durations` | 规格列表 |
| 配置 | GET | `/api/config/service-durations/detail`（`id` Query） | 规格详情 |
| 配置 | POST | `/api/config/service-durations/create` | 新建规格 |
| 配置 | PUT | `/api/config/service-durations/update`（`id` Query） | 修改规格 |
| 配置 | GET | `/api/config/system-company` | 系统公司 |
| 配置 | PUT | `/api/config/system-company/update` | 修改系统公司 |
| 续期 | GET | `/api/account-renewals` | 日志分页 |
| 续期 | GET | `/api/account-renewals/detail`（`requestId` Query） | 详情 |
| 续期 | POST | `/api/account-renewals/create` | 发起续期（CORS 条件） |
| 密码 | POST | `/api/service-accounts/password/reset`（`serviceAccountId` Query） | 重置（CORS 条件） |
| 密码 | GET | `/api/account-password-resets/result`（`requestId` Query） | 重置结果（CORS 条件） |
| 密码 | POST | `/api/service-accounts/password/custom`（`serviceAccountId` Query） | 自定义密码（CORS 条件） |
| 密码 | POST | `/api/service-accounts/password/reveal`（`serviceAccountId` Query） | 临时查看（CORS 条件） |

---

# 16. 当前不要调用/不存在的前端能力

```text
/internal/**                 内部集成接口，不给 Web 前端
SDK Center / 我的 SDK       暂未实现
免费试用                    暂未实现
Device / 自有设备           暂未实现/外部依赖
Diagnostics / 故障排查      暂未实现/外部依赖
公告中心                    暂未实现
账号 CSV 导出               暂未实现
批量续期写接口              暂未实现
Force Activation            不做，由 CORS silenceDays 自动激活
B2B 商品/价格/支付 API       不属于 Vantix
```

## 17. 联调重点

- 不要自行扩大 `companyId/ownerCompanyId/assignedUserId` 权限，后端会按登录身份再次约束。
- `requestId` 型写操作超时后必须复用原 `requestId`。
- 兑换请求不传 prefix；prefix 先通过兑换配置接口设置一次。
- 服务展示名使用 `displayName`，不要使用 `productName`。
- `durationDays` 单位统一为天。
- 账号列表/详情不返回密码。
- 错误判断应使用平台统一 `CommonResult` / 全局异常约定，不要解析中文 message 决定业务流程。
