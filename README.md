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

公司基础资料的 `CompanyClient` 和 CORS 的 `CorsAccountClient` 仅保留边界，未猜测远程 URL，也未实现真实账号创建、续期、激活、密码查看或密码重置。

统一用户中心由 `sino-cloud-base` 提供。`UserHolderBridge` 调用 `com.sinognss.cloud.base.filter.UserHolder` 的 `getUserAndCompanyId()` 作为数据范围、`getUser()` 作为真实操作人；本项目不创建用户、角色、权限或密码字段。

本阶段不引入 Redis、分布式事务或 CORS 密码存储。无用户上下文或不支持的数据范围会拒绝请求；定时任务/MQ/system 身份需在后续阶段显式建模。

## Phase 1.5

- GET /internal/v1/service-code-specs 只返回当前启用的服务时长规格，不暴露数据库 ID 或沉默规则。
- POST /internal/v1/service-code-generations 接收 B2B 请求号、订单号、公司 ID、规格码和数量；服务码由 Vantix 生成，并按生成时配置保存快照。
- GET /api/service-codes/offline-import/template 下载 .xlsx 模板；上传时通过 POST /api/service-codes/offline-import?companyId=... 单独传入页面选择的公司。
- GET /api/service-code-generation-batches/{batchNo} 和 orderNo 查询支持批次追溯。
- 线下导入会先校验整个文件，再在同一个 MySQL 本地事务中创建所有批次和服务码。Excel 不含公司、商品、部件号、金额或配置 ID 列。
- 内部 B2B 接口需要由部署网关配置服务认证；应用未另建认证机制。
- 已知部署风险继续保留：sino-cloud-base 的 UserInterceptor 使用 javax.servlet，而 Spring Boot 3 使用 jakarta.servlet；本阶段不调整该风险或认证架构。
- 线下导入配置项为 vantix.offline-import.max-file-size-bytes、max-rows、max-total-codes 和 max-errors；默认分别为 10 MiB、500 行、5,000 个服务码和 100 条错误。
- V3 为 service_duration_config 增加稳定且不可变的 spec_code，新增生成批次幂等约束和 service_code.generate_batch_id。V1/V2 保持不变；Phase 1.5 migration 在正式上线前仍处于开发冻结阶段。
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

后续阶段 TODO：实现 `exchange_*` 业务、CORS `CREATE_ACCOUNT/RENEW_ACCOUNT/FORCE_ACTIVATE` 补偿流程、账号续期/激活/密码敏感接口和公司服务实际同步适配器。
