# ServiceAccount 查询 API

## 分页列表

`GET /api/service-accounts`

参数：`current`、`size`、`keyword`、`status`、`specCode`、`durationDays`、`ownerCompanyId`、`assignedUserId`。分页和过滤在数据库执行，排序为 `created_at DESC, id DESC`。

`status` 是基于当前真实 CORS 快照的展示分类，支持 `WAITING_ACTIVATION`、`ACTIVE`、`EXPIRED`、`DISABLED`。原始 `accountStatus`（`cors_status`）和 `activationStatus`（`cors_activation_status`）同时返回。

列表返回账号、CORS 账号 ID、展示分类、服务规格、公司、分配用户、来源服务码、兑换批次/requestId、激活/到期时间、CORS 创建/更新时间、最近同步时间和本地创建/更新时间。没有 password、forceActivateTime、Device 或 Diagnostics 字段。

规格展示名通过来源服务码关联的生成批次快照取得，不读取当前 `service_duration_config` 覆盖历史含义。

## 统计

`GET /api/service-accounts/statistics`

参数为 `keyword`、`specCode`、`durationDays`、`ownerCompanyId`、`assignedUserId`。返回 `total`、`waiting`、`active`、`expired`、`disabled`。统计使用与列表相同的业务筛选条件，但不接受列表的 `status` 分页分类，因此不会错误地只统计某一个状态。

## 详情

`GET /api/service-accounts/detail?id={id}`

> **接口地址已变更**
>
> 原接口：`GET /api/service-accounts/{id}`
>
> 新接口：`GET /api/service-accounts/detail?id={id}`
>
> 变更：`id` 从 Path 参数调整为 Query 参数。

返回列表字段及可靠的 CORS 快照字段。GLOBAL 可访问全局；COMPANY 强制当前公司；PERSONAL 强制当前公司和当前 `assigned_user_id`。越权或不存在统一按项目查询模式返回 NOT_FOUND，不暴露 password。

UNSUPPORTED UserScope fail closed。GLOBAL 可选公司/用户筛选；非 GLOBAL 不能通过筛选参数扩大范围。
