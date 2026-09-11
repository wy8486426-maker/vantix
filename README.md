# Vantix 账号运营平台

第一阶段是单 Spring Boot 服务，负责公司上下级关系、服务时长/账号沉默配置、服务码资产和批量转赠。数据库由 Flyway 从空库执行 `V1__init_schema.sql` 初始化。

## 本阶段接口

- `GET /api/companies`、`GET /api/companies/{companyId}`、`GET /api/companies/{companyId}/children`
- `PUT /api/companies/{companyId}/parent`
- `GET/POST/PUT /api/config/service-durations`
- `GET/PUT /api/config/account`
- `GET/PUT /api/config/system-company`
- `GET /api/service-codes`、`GET /api/service-codes/{id}`
- `POST /api/service-codes/transfers`
- `GET /api/service-codes/{id}/transfers`

服务码转赠在一个 MySQL 本地事务内按服务码 ID 升序 `SELECT ... FOR UPDATE`，完成全部校验后使用 `version` CAS 更新并记录流水；任意一张失败都会回滚批次。服务码过期和即将到期是 DTO 展示状态，不是数据库状态。

公司基础资料的 `CompanyClient` 和 CORS 的 `CorsAccountClient` 仅保留边界，未猜测远程 URL，也未实现真实账号创建、续期、激活、密码查看或密码重置。

统一用户中心由 `sino-cloud-base` 提供。`UserHolderBridge` 调用 `com.sinognss.cloud.base.filter.UserHolder` 的 `getUserAndCompanyId()` 作为数据范围、`getUser()` 作为真实操作人；本项目不创建用户、角色、权限或密码字段。

## 启动

需要 Java 17、Maven 3.9+ 和 MySQL 5.7/8。使用环境变量 `VANTIX_DB_URL`、`VANTIX_DB_USERNAME`、`VANTIX_DB_PASSWORD` 配置数据库。数据库连接驱动固定为 MySQL Connector/J 8.0.33。

```powershell
$env:JAVA_HOME='E:\Java\jdk17.0.7'
$env:MAVEN_HOME='E:\apache-maven-3.9.16'
$env:Path="$env:JAVA_HOME\bin;$env:MAVEN_HOME\bin;$env:Path"
mvn clean test
```

第二阶段 TODO：实现 `exchange_*` 业务、CORS `CREATE_ACCOUNT/RENEW_ACCOUNT/FORCE_ACTIVATE` 补偿流程、账号续期/激活/密码敏感接口和公司服务实际同步适配器。
