# ServiceAccount 查询 API

## 分页列表

`GET /api/service-accounts`

参数：`current`、`size`、`keyword`、`status`、`specCode`、`durationDays`、`ownerCompanyId`、`assignedUserId`、可选 `accountSource`。`accountSource` 取 `EXCHANGE`、`TEST`、`HISTORY_IMPORT`。分页和过滤在数据库执行，排序为 `created_at DESC, id DESC`。

`status` 是基于当前真实 CORS 快照的展示分类，支持 `WAITING_ACTIVATION`、`ACTIVE`、`EXPIRED`、`DISABLED`。原始 `accountStatus`（`cors_status`）和 `activationStatus`（`cors_activation_status`）同时返回。

列表返回账号、CORS 账号 ID、展示分类、账号来源、服务规格、公司、分配用户、来源服务码、兑换批次/requestId、`exchangeAt`、激活/到期时间、CORS 创建/更新时间、最近同步时间和本地创建/更新时间。没有 password、forceActivateTime、Device 或 Diagnostics 字段。`TEST` 和 `HISTORY_IMPORT` 的 `exchangeAt`、`exchangeBatchNo`、来源服务码均为 `null`。

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

## 测试账号下发

`POST /api/service-accounts/test-issues` 仅允许 `GLOBAL`。请求字段为 `requestId`、`companyId`、`quantity`、`specCode`、可选四位 `accountPrefix`（缺省为 `TEST`）。规格的 `serviceType`、`durationDays` 和 `accountSilenceDays` 由后端按当前 enabled 规格读取并冻结到测试下发批次；不会读取或修改 `company_exchange_config`，也不会创建兑换批次、兑换明细或消耗服务码。

`GET /api/service-accounts/test-issues/query?requestId={requestId}` 查询测试下发批次和成功后的 `{id, name}` CORS identity，不返回密码。查询使用独立静态路径以遵循项目现有权限 URL 约定。TEST 创建复用 CORS `POST /BaseUser/userInfo/add`（当前 gateway 的相对路径为 `/userInfo/add`），批次 API `requestId` 与 CORS requestId 分离；重试始终复用同一个 CORS requestId。CORS `code=0,data=null` 会进入结果窗口内重试，超过窗口转 `MANUAL_REVIEW`。

## 历史账号导入

`POST /api/service-accounts/history-imports` 仅允许 `GLOBAL`。请求字段为 `requestId`、`companyId`、`specCode` 和完整的 `accounts: [{id, name}]`。`specCode` 必须存在，允许选择 disabled 的历史规格；规格快照由后端读取。导入不调用 CORS create、不创建 `cors_operation`、不消耗服务码，并在同一事务内完成批次和全部 `service_account` 入账。

批内 `id`/`name` 必须分别唯一且有效；数据库已存在的 `cors_account_id` 或 `account`、以及任何 identity 映射冲突都会拒绝，绝不覆盖。相同 `requestId` 加相同规范化 payload 幂等返回原批次；相同 requestId 的不同 payload 返回幂等冲突。

账号来源统一为 `EXCHANGE`、`TEST`、`HISTORY_IMPORT`。目前只有 `EXCHANGE` 账号允许服务码续期；TEST/HISTORY_IMPORT 在锁定账号后、预留或消费服务码前以 `ACCOUNT_SOURCE_NOT_RENEWABLE` 拒绝。三种来源都继续进入现有 CORS authoritative 状态同步。
