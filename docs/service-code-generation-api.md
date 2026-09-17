# 服务码来源订单 API

这些接口描述 Vantix 的服务码生成来源订单，不是完整的 B2B 商业订单；不包含商品、金额、支付或商品名称字段。

## `GET /api/service-code-generations/orders`

分页参数：`current`、`size`、`keyword`、`generationSource`、`status`、`ownerCompanyId`、`createdFrom`、`createdTo`。

`generationSource` 当前只有 `B2B` 和 `OFFLINE`。`keyword` 搜索 `requestId`、`sourceOrderNo`、`ownerCompanyName`，日期参数使用 `LocalDateTime`，同时传入时必须 `createdFrom <= createdTo`。

响应记录包含：`id`、`requestId`、`generationSource`、`sourceOrderNo`、`sourceOrderTime`、`ownerCompanyId`、`ownerCompanyName`、`itemCount`、`totalQuantity`、`status`、`operatorUserId`、`operatorUserName`、`createdAt`、`updatedAt`。

GLOBAL 可查询全部并可选按公司筛选；COMPANY 和 PERSONAL 只查询当前公司，PERSONAL 不按用户缩小范围；UNSUPPORTED 或缺少用户上下文拒绝。分页和过滤均在数据库执行，默认按 `created_at DESC, id DESC` 排序。

## `GET /api/service-code-generations/orders/statistics`

支持 `keyword`、`status`、`ownerCompanyId`、`createdFrom`、`createdTo`，返回数据库条件聚合：

```json
{"total": 10, "b2b": 7, "offline": 3}
```

当前只有两种来源，因此 `total = b2b + offline`。

## 订单详情

> **接口地址已变更**
>
> 原接口：`GET /api/service-code-generations/orders/{orderNo}`
>
> 新接口：`GET /api/service-code-generations/orders/detail?orderNo={orderNo}`
>
> 变更：`orderNo` 从 Path 参数调整为 Query 参数，`companyId` 仍为可选 Query 参数。

`GET /api/service-code-generations/orders/detail?orderNo={orderNo}` 用于按来源订单号读取订单及规格批次详情；服务码查询接口及其现有筛选契约不变。

生成批次详情同样已调整：原接口为 `GET /api/service-code-generation-batches/{batchNo}`，新接口为 `GET /api/service-code-generation-batches/detail?batchNo={batchNo}`，`batchNo` 从 Path 参数调整为 Query 参数。
