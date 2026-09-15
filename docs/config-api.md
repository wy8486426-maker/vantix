# 配置中心 API

配置中心用于运营维护服务规格和系统公司。`/api/config/**` 所有接口只允许明确的 `GLOBAL` UserScope；缺少用户上下文、`COMPANY`、`PERSONAL` 或 `UNSUPPORTED` 均拒绝访问。

## 服务规格

### `GET /api/config/service-durations`

返回全部服务规格，包含 `specCode`、`displayName`、`serviceType`、`durationDays`、`codeSilenceDays`、`accountSilenceDays`、`enabled`、`remark`、`createdBy`、`updatedBy` 以及审计时间。服务规格按类型、时长和名称稳定排序。

### `GET /api/config/service-durations/{id}`

返回单个服务规格详情；不存在时返回 `NOT_FOUND`。

### `POST /api/config/service-durations`

创建请求字段：

```json
{
  "displayName": "90天标准版",
  "serviceType": "CORS",
  "durationDays": 90,
  "codeSilenceDays": 30,
  "accountSilenceDays": 60,
  "enabled": true,
  "remark": ""
}
```

`durationDays` 必须大于 0；两个沉默天数必须大于等于 0。系统生成稳定唯一的 `specCode`，`displayName` 全局唯一。

### `PUT /api/config/service-durations/{id}`

更新请求只接受：

```json
{
  "displayName": "90天标准版（新版名称）",
  "codeSilenceDays": 30,
  "accountSilenceDays": 60,
  "enabled": false,
  "remark": "停用旧规格"
}
```

这是 PUT 全量更新；请求中的字段均必填，省略字段不会被视为“不修改”。更新请求不包含 `serviceType` 或 `durationDays`。创建后的 `specCode`、`serviceType`、`durationDays` 永久不可修改；规格通过 `enabled=false` 停用，不提供删除接口。

## 系统公司

### `GET /api/config/system-company`

返回当前 `systemCompanyId`。

### `PUT /api/config/system-company`

请求：

```json
{"systemCompanyId": 10001}
```

写入前只验证目标 `companyId` 在 `dealer_company` 中真实存在，不额外要求公司处于某个状态。不存在时返回 `NOT_FOUND`。
