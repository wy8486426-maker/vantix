# 服务码查询 API

本页描述服务码运营页面使用的三个查询接口。所有接口继续使用项目统一的 `CommonResultAdapter.success(...)` 响应包装。

## `GET /api/service-codes`

分页参数：

| 参数 | 必填 | 说明 |
| --- | --- | --- |
| `current` | 否 | 页码，默认 `1`，必须大于等于 `1` |
| `size` | 否 | 页大小，默认 `20`，范围 `1..500` |
| `keyword` | 否 | trim 后为空视为未传，最多 100 个字符 |
| `status` | 否 | 原始状态：`PENDING`、`PROCESSING`、`CONSUMED` |
| `displayStatus` | 否 | `WAITING`、`EXPIRING`、`EXPIRED`、`PROCESSING`、`CONSUMED` |
| `specCode` | 否 | 精确匹配服务码快照 `spec_code` |
| `durationDays` | 否 | 精确匹配服务码快照天数，必须大于 0 |
| `sourceOrderNo` | 否 | 精确匹配来源订单号 |
| `ownerCompanyId` | 否 | 精确匹配归属公司；同时受 UserScope 权限约束 |

`status` 与 `displayStatus` 同时传入时必须对应同一原始状态。例如 `status=PENDING&displayStatus=EXPIRING` 合法，`status=CONSUMED&displayStatus=WAITING` 会返回 `INVALID_ARGUMENT`。

默认排序为 `created_at DESC, id DESC`，即最新生成的服务码优先。响应分页字段保持项目现有结构：`records`、`current`、`size`、`total`、`pages`。

keyword 搜索以下字段：服务码 `code`、`service_code.source_order_no`、生成批次冻结的 `display_name`、归属公司的 `company_name`。Vantix 不返回 B2B `productName`；前端原型中的“商品名称”应改用 `displayName`（规格名称）。

示例：

```json
{
  "records": [
    {
      "id": 101,
      "code": "VX260915ABCDEFG",
      "specCode": "SC001",
      "displayName": "90天活动版",
      "serviceType": "NTRIP",
      "durationDays": 90,
      "codeSilenceDays": 30,
      "sourceOrderId": 10001,
      "sourceOrderNo": "B2B202609150001",
      "generateBatchId": 5001,
      "batchNo": "BG202609150001",
      "ownerCompanyId": 2001,
      "ownerCompanyName": "示例客户",
      "expireAt": "2026-10-15T10:00:00",
      "status": "PENDING",
      "displayStatus": "WAITING",
      "processingType": null,
      "processingRequestId": null,
      "consumeType": null,
      "consumedAt": null,
      "version": 0,
      "createdAt": "2026-09-15T10:00:00",
      "updatedAt": "2026-09-15T10:00:00"
    }
  ],
  "current": 1,
  "size": 20,
  "total": 1,
  "pages": 1
}
```

## `displayStatus`

展示状态由同一次请求的业务 `Clock`（`Asia/Shanghai`）动态计算：

| 展示状态 | 规则 |
| --- | --- |
| `WAITING` | `status=PENDING` 且 `expireAt > now + upcomingDays` |
| `EXPIRING` | `status=PENDING` 且 `now < expireAt <= now + upcomingDays` |
| `EXPIRED` | `status=PENDING` 且 `expireAt <= now` |
| `PROCESSING` | `status=PROCESSING` |
| `CONSUMED` | `status=CONSUMED` |

列表 SQL 筛选和返回 DTO 使用同一组边界规则。`displayName` 来自生成批次的历史冻结快照；`specCode`、`serviceType`、`durationDays`、`codeSilenceDays` 来自服务码自身快照，不会被当前 `service_duration_config` 改名或修改覆盖。

## `GET /api/service-codes/statistics`

支持 `keyword`、`specCode`、`durationDays`、`sourceOrderNo`、`ownerCompanyId`，不接受 `status` 或 `displayStatus` 作为统计过滤条件。一次数据库条件聚合返回：

```json
{
  "total": 100,
  "waiting": 60,
  "expiring": 10,
  "expired": 5,
  "processing": 3,
  "consumed": 22
}
```

五种展示状态互斥且覆盖 `total`，因此 `total = waiting + expiring + expired + processing + consumed`。统计使用同一次请求的 `now` 和 `upcomingAt`，不会加载全部服务码到 Java 内存计数。

## `GET /api/service-codes/{id}`

详情返回与列表相同的服务码字段：`id`、`code`、`specCode`、`displayName`、`serviceType`、`durationDays`、`codeSilenceDays`、`sourceOrderId`、`sourceOrderNo`、`generateBatchId`、`batchNo`、`ownerCompanyId`、`ownerCompanyName`、`expireAt`、`status`、`displayStatus`、`processingType`、`processingRequestId`、`consumeType`、`consumedAt`、`version`、`createdAt`、`updatedAt`。

详情执行与列表相同的 UserScope 权限检查，不嵌入转赠历史，也不查询 Exchange、CORS 或 `service_account`。转赠历史继续通过 `GET /api/service-codes/{id}/transfers` 独立查询。

## UserScope

- `GLOBAL`：可查询全部服务码；传入 `ownerCompanyId` 时作为普通精确筛选。
- `COMPANY`：仅查询当前公司；不传 `ownerCompanyId` 自动使用当前公司，传入其它公司会明确拒绝。
- `PERSONAL`：服务码仍属于公司池，行为与当前公司范围一致，不按 assigned user 缩小范围。
- `UNSUPPORTED` 或缺少用户上下文：拒绝请求，不降级为 `GLOBAL`。
