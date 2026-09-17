# 服务码转赠日志 API

转赠流水表按每张服务码保存一行；前端列表按 `transferNo` 聚合为一条转赠批次记录。

## `GET /api/service-code-transfers`

分页参数：`current`、`size`、`keyword`、`transferType`、`fromCompanyId`、`toCompanyId`、`createdFrom`、`createdTo`、`specCode`、`durationDays`。

`keyword` 在数据库中过滤 `transferNo`、`serviceCode`、转出公司名、转入公司名和操作人姓名。分页前完成过滤，批次数量仍统计该 `transferNo` 的全部服务码行。列表记录包含：

`transferNo`、`fromCompanyId`、`fromCompanyName`、`toCompanyId`、`toCompanyName`、`transferType`、`quantity`、`specCode`、`serviceType`、`durationDays`、`reason`、`operatorUserId`、`operatorUserName`、`createdAt`。

`quantity` 来源于该批次的 `COUNT(*)`，不是 Java 内存计数。SQL 使用聚合列和稳定排序，兼容 MySQL 5.7 `ONLY_FULL_GROUP_BY`。

## `GET /api/service-code-transfers/detail`

> **接口地址已变更**
>
> 原接口：`GET /api/service-code-transfers/{transferNo}`
>
> 新接口：`GET /api/service-code-transfers/detail?transferNo={transferNo}`
>
> 变更：`transferNo` 从 Path 参数调整为 Query 参数。

Query 参数：`transferNo`，必填，转赠批次号。

返回：

```json
{
  "batch": {
    "transferNo": "TR...",
    "fromCompanyId": 10001,
    "fromCompanyName": "上级公司",
    "toCompanyId": 10002,
    "toCompanyName": "下级公司",
    "transferType": "PARENT_CHILD",
    "quantity": 2,
    "specCode": "SC...",
    "serviceType": "CORS",
    "durationDays": 90,
    "reason": "",
    "operatorUserId": 123,
    "operatorUserName": "operator",
    "createdAt": "2026-09-16T10:00:00"
  },
  "items": []
}
```

明细项包含 `serviceCodeId`、`serviceCode`、`specCode`、历史冻结 `displayName`、`serviceType`、`durationDays`、`expireAt`。`displayName` 从服务码关联的生成批次快照读取，不读取当前规格名称。

GLOBAL 可查看全部；COMPANY 和 PERSONAL 仅可查看当前公司作为转出方或转入方的批次，PERSONAL 不按用户过滤；UNSUPPORTED 或缺少用户上下文拒绝。单码转赠历史现使用 `GET /api/service-codes/transfers/history?id={id}`，原接口为 `GET /api/service-codes/{id}/transfers`；转赠写接口保持不变。
