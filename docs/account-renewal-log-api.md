# Renewal Log API

## 分页列表

`GET /api/account-renewals`

参数：`current`、`size`、`keyword`、`status`、`ownerCompanyId`、`createdFrom`、`createdTo`。`status` 使用现有续期状态：`PROCESSING`、`COMPLETED`、`FAILED`、`MANUAL_REVIEW`。结果按 `created_at DESC, id DESC` 排序，并在数据库中完成过滤和分页。

返回续期 ID/requestId、服务账号及账号名、CORS account ID、公司、分配用户、服务码 ID/快照值、specCode、生成批次快照 displayName、serviceType、durationDays、状态、当前账号到期时间、错误字段和创建/完成时间。

GLOBAL 查询全局；COMPANY 强制当前公司；PERSONAL 强制当前公司且只返回 `assigned_user_id` 为当前用户的账号续期。非 GLOBAL 的 `ownerCompanyId` 不能扩大范围。

当前 `account_renewal` schema 没有保存续期前后账号到期时间，也没有把这两个值写入续期 snapshot。因此本接口不虚构 `oldExpireAt` 或 `newExpireAt`；`currentAccountExpireAt` 明确表示查询时 `service_account.expire_at` 的当前快照，不是历史续期前后值。

## 详情

`GET /api/account-renewals/{requestId}` 保留，并与列表一样始终可用，不依赖 `vantix.cors.renewal.enabled`。它继续返回原有 `AccountRenewalView` 契约，并扩展返回账号、公司、来源服务码和生成批次 displayName 等可可靠关联字段。查询权限仍遵守既有续期账号权限，响应不包含 password，也不改变 reserve/process/finalize 状态机；只有 POST 创建续期仍受续期开关和 CORS 能力条件控制。
