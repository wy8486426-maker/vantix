# 前端 API 清单

本轮实现的是前端基础查询和配置能力；所有接口使用项目统一响应包装。

| 模块 | 状态 | 接口 |
| --- | --- | --- |
| 服务码 | IMPLEMENTED | 现有列表、统计、详情及单码转赠历史 |
| 配置中心 | IMPLEMENTED | 服务规格列表/详情/创建/更新，系统公司读取/更新；GLOBAL only |
| 兑换配置 | IMPLEMENTED | `GET/POST /api/service-code-exchange-config` |
| 服务码来源订单 | IMPLEMENTED | 订单分页、统计、既有订单号详情 |
| 客户/合作伙伴 | IMPLEMENTED | `GET /api/companies/page`、`GET /api/companies/partners` |
| 转赠日志 | IMPLEMENTED | 批次分页、批次详情；按 `transferNo` 聚合 |
| Dashboard | IMPLEMENTED | `GET /api/dashboard`，实时聚合服务码、账号和操作数量 |
| Account management | IMPLEMENTED | `GET /api/service-accounts`、`/statistics`、`/{id}` |
| Exchange logs | IMPLEMENTED | `GET /api/service-code-exchanges`、`/{requestId}`、`/{requestId}/detail` |
| Renewal logs | IMPLEMENTED | `GET /api/account-renewals`，并保留既有 `/{requestId}` |
| SDK | DEFERRED | 本轮不实现 SDK 中心 |
| Free trial | DEFERRED | 本轮不实现免费试用 |
| Device | BLOCKED_EXTERNAL | 依赖外部设备能力 |
| Diagnostics | BLOCKED_EXTERNAL | 依赖外部故障诊断能力 |

## 统一约束

- `/api/config/**` 只允许 `GLOBAL` UserScope。
- COMPANY/PERSONAL 查询按公司范围执行；服务码、生成订单、转赠日志不按个人用户进一步缩小资产范围。
- 文本筛选先 trim，空字符串按未传处理；列表分页和过滤在数据库执行。
- 新增查询使用 JOIN/聚合，避免逐行读取公司、生成批次或服务码造成 N+1。
- 所有新增 SQL 按 MySQL 5.7 语法编写，不使用 CTE、窗口函数或 MySQL 8 专属语法。
- Dashboard 使用现有表实时聚合，不新增业务事实表；本轮不提供公告、销售额、支付额、B2B 商品、Device 或 Diagnostics 数据。
