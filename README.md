# Vantix 账号运营平台

第一阶段是单 Spring Boot 服务，负责公司上下级关系、服务时长/账号沉默配置、服务码资产和批量转赠。数据库由 Flyway 从空库执行当前完整的 `V1__init_schema.sql` baseline 初始化。

## 本阶段接口

- `GET /api/companies`、`GET /api/companies/{companyId}`、`GET /api/companies/{companyId}/children`
- `PUT /api/companies/{companyId}/parent`
- `GET/POST/PUT /api/config/service-durations`
- `GET/PUT /api/config/system-company`
- `GET /api/config/service-durations/{id}`（配置中心详情）
- `GET/POST /api/service-code-exchange-config`（经销商一次性兑换前缀配置）
- `GET /api/service-codes`、`GET /api/service-codes/{id}`、`GET /api/service-codes/statistics`（列表支持分页、筛选与动态 `displayStatus=WAITING|EXPIRING|EXPIRED|PROCESSING|CONSUMED`）
- `POST /api/service-codes/transfers`
- `GET /api/service-codes/{id}/transfers`
- `GET /api/service-code-generations/orders`、`GET /api/service-code-generations/orders/statistics`
- `GET /api/companies/page`、`GET /api/companies/partners`
- `GET /api/service-code-transfers`、`GET /api/service-code-transfers/{transferNo}`

服务码转赠在一个 MySQL 本地事务内按服务码 ID 升序 `SELECT ... FOR UPDATE`，完成全部校验后使用 `version` CAS 更新并记录流水；任意一张失败都会回滚批次。批量转赠要求所有服务码的 `service_type`、`spec_code`、`duration_days` 完全一致。服务码过期和即将到期是 DTO 展示状态，不是数据库状态。

公司真实性由 user-center 提供，Vantix 通过 `UserCenterFeignService` 同步公司 ID/名称到本地 `dealer_company`；公司上下级关系和状态仍由 Vantix 维护。CORS 的 `CorsAccountClient` 未猜测远程 URL。密码查看与重置流程已实现本地审计、权限和补偿边界，并通过 `CorsAccountPasswordGateway` 留待 CORS 接口契约确认后接入真实适配器；默认 `vantix.cors.password.enabled=false`，缺少 Gateway 时密码路由不会注册。

密码路由为 `POST /api/service-accounts/{serviceAccountId}/password/reveal`、`POST /api/service-accounts/{serviceAccountId}/password/reset` 和本地状态查询 `GET /api/account-password-resets/{requestId}`。查看密码先写审计、成功后仅放入当前 HTTP 响应，并设置 `no-store`；重置使用原 requestId 持久化跟踪，超时恢复先查 CORS 状态，只有确认请求不存在后才会再次 POST。

统一用户中心由 `sino-cloud-base` 提供。`UserHolderBridge` 调用 `com.sinognss.cloud.base.filter.UserHolder` 的 `getUserAndCompanyId()` 作为数据范围、`getUser()` 作为真实操作人；本项目不创建用户、角色、权限或密码字段。

无用户上下文或不支持的数据范围会拒绝请求；定时任务/MQ/system 身份需在后续阶段显式建模。

## Phase 1.5

- GET /internal/v1/service-code-specs 只返回当前启用的服务时长规格，不暴露数据库 ID 或沉默规则。
- POST /internal/v1/service-code-generations 接收订单级 requestId、订单号、公司 ID 和 items 多规格列表；每个 item 使用 specCode + quantity。Vantix 在同一事务中生成整单及各规格批次，响应仅返回订单/批次摘要。
  - 同 requestId、同 payload 重放返回原订单；同来源/公司/订单号但不同 requestId、同 payload 也幂等；payload 不同则冲突。订单上限为 50 个规格、总计 5,000 张，每规格数量上限 5,000。
- GET /api/service-codes/offline-import/template 下载 .xlsx 模板；上传时通过 POST /api/service-codes/offline-import?companyId=... 单独传入页面选择的公司。
- GET /api/service-code-generation-batches/{batchNo} 与 ?orderNo=... 查询支持批次追溯；GET /api/service-code-generations/orders/{orderNo} 返回订单头及规格批次摘要。
- 线下导入按订单号聚合多规格，会先校验整个文件，再在同一个 MySQL 本地事务中创建所有订单头、批次和服务码；同一订单的非空下单时间必须一致。Excel 不含公司、商品、部件号、金额或配置 ID 列。
- 内部 B2B 接口需要由部署网关配置服务认证；应用未另建认证机制。
- 已知部署风险继续保留：sino-cloud-base 的 UserInterceptor 使用 javax.servlet，而 Spring Boot 3 使用 jakarta.servlet；本阶段不调整该风险或认证架构。
- 线下导入配置项为 vantix.offline-import.max-file-size-bytes、max-rows、max-total-codes 和 max-errors；默认分别为 10 MiB、500 行、5,000 个服务码和 100 条错误。
- 服务时长规格使用全局唯一的 `spec_code` 和 `display_name`，业务时长统一使用 `duration_days`；创建后不可修改 `spec_code`、`service_type` 和 `duration_days`，只能调整展示名称、沉默天数、启用状态和备注。
- 当前 Vantix CORS adapter 使用 `requestId`、`durationDays`、`silenceDays`、`quantity`、`accountPrefix`；兑换前缀由 Vantix 的公司级不可变配置冻结到 `exchange_batch` 后传入。最终真实 CORS HTTP wire contract 的字段名仍需与 CORS 团队独立联调确认。
- Redis 实时状态通知保留即时 authoritative read 和 500ms confirmation；CORS MySQL 状态同步保留 `0 0 2 * * ?`、`Asia/Shanghai` fallback。

## Pre-release database policy

Vantix 尚未正式生产发布，当前 schema 已 squash 为新的 V1 baseline。任何使用旧 V1~V9 migration 的开发/测试数据库都必须由开发人员手工删除并重新创建；不支持把 pre-release legacy business data 原地升级到新 baseline。正式第一次生产部署后，V1 将被冻结，后续 schema 变化才使用 V2/V3/...。

开发环境重建示例：

```sql
DROP DATABASE vantix;
CREATE DATABASE vantix CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

应用启动和 Flyway migration 不会自动执行 `DROP DATABASE`。
## 启动

需要 Java 17、Maven 3.9+ 和 MySQL 5.7/8。使用环境变量 `VANTIX_DB_URL`、`VANTIX_DB_USERNAME`、`VANTIX_DB_PASSWORD` 配置数据库。数据库连接驱动固定为 MySQL Connector/J 8.0.33。

```powershell
$env:JAVA_HOME='E:\Java\jdk17.0.7'
$env:MAVEN_HOME='E:\apache-maven-3.9.16'
$env:Path="$env:JAVA_HOME\bin;$env:MAVEN_HOME\bin;$env:Path"
mvn clean test
```

后续阶段 TODO：密码 Gateway 的真实适配器需等 CORS 确认接口契约后实现。
