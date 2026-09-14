# Vantix 账号运营平台

第一阶段是单 Spring Boot 服务，负责公司上下级关系、服务时长/账号沉默配置、服务码资产和批量转赠。数据库由 Flyway 从空库执行 `V1__init_schema.sql` 和后续版本迁移初始化。

## 本阶段接口

- `GET /api/companies`、`GET /api/companies/{companyId}`、`GET /api/companies/{companyId}/children`
- `PUT /api/companies/{companyId}/parent`
- `GET/POST/PUT /api/config/service-durations`
- `GET/PUT /api/config/account`
- `GET/PUT /api/config/system-company`
- `GET /api/service-codes`、`GET /api/service-codes/{id}`（列表支持 `status` 与动态 `displayStatus=WAITING|EXPIRING|EXPIRED|PROCESSING|CONSUMED`）
- `POST /api/service-codes/transfers`
- `GET /api/service-codes/{id}/transfers`

服务码转赠在一个 MySQL 本地事务内按服务码 ID 升序 `SELECT ... FOR UPDATE`，完成全部校验后使用 `version` CAS 更新并记录流水；任意一张失败都会回滚批次。批量转赠要求所有服务码的 `service_type`、`duration_value`、`duration_unit` 完全一致。服务码过期和即将到期是 DTO 展示状态，不是数据库状态。

公司基础资料的 `CompanyClient` 和 CORS 的 `CorsAccountClient` 未猜测远程 URL。密码查看与重置流程已实现本地审计、权限和补偿边界，并通过 `CorsAccountPasswordGateway` 留待 CORS 接口契约确认后接入真实适配器；默认 `vantix.cors.password.enabled=false`，缺少 Gateway 时密码路由不会注册。

密码路由为 `POST /api/service-accounts/{serviceAccountId}/password/reveal`、`POST /api/service-accounts/{serviceAccountId}/password/reset` 和本地状态查询 `GET /api/account-password-resets/{requestId}`。查看密码先写审计、成功后仅放入当前 HTTP 响应，并设置 `no-store`；重置使用原 requestId 持久化跟踪，超时恢复先查 CORS 状态，只有确认请求不存在后才会再次 POST。

统一用户中心由 `sino-cloud-base` 提供。`UserHolderBridge` 调用 `com.sinognss.cloud.base.filter.UserHolder` 的 `getUserAndCompanyId()` 作为数据范围、`getUser()` 作为真实操作人；本项目不创建用户、角色、权限或密码字段。

本阶段不引入 Redis、分布式事务或 CORS 密码存储。无用户上下文或不支持的数据范围会拒绝请求；定时任务/MQ/system 身份需在后续阶段显式建模。

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
- V3 为 service_duration_config 增加稳定且不可变的 spec_code，新增 service_code_generate_order 订单头、订单 payloadHash 幂等约束、批次 generate_order_id 关系和 service_code.generate_batch_id。V1/V2 保持不变；Phase 1.5 migration 在正式上线前仍处于开发冻结阶段。
- 服务时长规格按 duration_value + duration_unit 全局唯一，specCode 固定为 D/W/M/Y + 时长值；创建后不可修改规格身份，只能调整启用状态、沉默月数和备注。
- V3 不创建数据库外键。修改尚未上线的 V3 后，开发环境应重建空 schema 并从 V1/V2/V3 重新执行 Flyway。
## 启动

需要 Java 17、Maven 3.9+ 和 MySQL 5.7/8。使用环境变量 `VANTIX_DB_URL`、`VANTIX_DB_USERNAME`、`VANTIX_DB_PASSWORD` 配置数据库。数据库连接驱动固定为 MySQL Connector/J 8.0.33。

```powershell
$env:JAVA_HOME='E:\Java\jdk17.0.7'
$env:MAVEN_HOME='E:\apache-maven-3.9.16'
$env:Path="$env:JAVA_HOME\bin;$env:MAVEN_HOME\bin;$env:Path"
mvn clean test
```

后续阶段 TODO：实现 `exchange_*` 业务、CORS `CREATE_ACCOUNT/RENEW_ACCOUNT/FORCE_ACTIVATE` 补偿流程和公司服务实际同步适配器；密码 Gateway 的真实适配器需等 CORS 确认接口契约后实现。
