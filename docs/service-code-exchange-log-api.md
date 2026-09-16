# Exchange Log API

## 分页列表

`GET /api/service-code-exchanges`

参数：`current`、`size`、`keyword`、`status`、`specCode`、`ownerCompanyId`、`createdFrom`、`createdTo`。`status` 使用现有 `ExchangeStatus`：`PROCESSING`、`COMPLETED`、`FAILED`、`MANUAL_REVIEW`。结果按兑换 batch 一行返回，稳定排序为 `created_at DESC, id DESC`。

列表返回 `requestId`、batch ID/编号、公司、规格快照、服务类型、天数、数量、成功数量、状态、账号前缀快照、generationSource、创建/完成时间和既有错误字段。`displayName` 直接来自 `exchange_batch.display_name`，在首次 reservation 时冻结；不以当前服务规格配置或生成批次名称覆盖历史。

GLOBAL 查询全局。COMPANY/PERSONAL 均限定当前公司；兑换属于公司服务码资产，本轮列表不按 PERSONAL 额外缩小到 assigned user。非 GLOBAL 传入其他 `ownerCompanyId` 会拒绝。

## 详情

既有 `GET /api/service-code-exchanges/{requestId}` 保持原响应和权限语义。新增 `GET /api/service-code-exchanges/{requestId}/detail` 返回 batch 主体及 items。每个 item 返回 `serviceCodeId`、服务码快照、`serviceAccountId`、CORS account ID、账号名和 exchange detail 状态；处理中的明细可能尚无账号 ID。详情也执行公司 scope 校验，其他公司记录按 NOT_FOUND 处理。

任何响应都不返回 password。
