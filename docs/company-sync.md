# 公司目录同步

## 职责边界

user-center 是公司真实性和公司身份的权威来源，只提供 `companyId` 和 `companyName`。Vantix 的 `dealer_company` 是本地公司目录，并独立维护公司上下级关系：`parent_company_id IS NULL` 表示 `FIRST_LEVEL`，非空表示 `SECOND_LEVEL`。

同步不会使用 user-center 的公司类型推断层级，也不会覆盖 `parent_company_id` 或 `company_status`。新公司插入时 `parent_company_id` 为 `NULL`，`company_status` 使用 Vantix 表结构的默认值 `ACTIVE`。后续运营人员仍可通过现有公司层级接口维护上下级关系。

## 同步字段和行为

同步只写入：

- `company_id` / user-center `companyId`
- `company_name` / user-center `companyName`
- `company_synced_at`

同步采用 MySQL 5.7 兼容的 upsert。已有本地公司的名称和同步时间可以更新，但不会删除 user-center 当前分页中缺失的公司，不会自动禁用公司，不会修改本地层级或状态。

## 触发机制

1. 兑换配置首次写入时的 sync-on-miss：本地公司缺失时，根据当前 `UserScope.companyId` 精确查询 user-center；已有兑换配置的幂等重试不会调用 user-center。
2. 后台定时全量同步：默认每 6 小时执行一次，时间为 `Asia/Shanghai` 的 00:00、06:00、12:00、18:00，每页默认 200 条。

相关配置：

```yaml
vantix:
  company-sync:
    enabled: true
    cron: 0 0 */6 * * ?
    zone: Asia/Shanghai
    page-size: 200
```

全量同步按 user-center 返回的 `totalPage` 逐页读取；空列表会记录警告并结束本次运行，避免异常分页导致死循环。远程失败或分页结构异常会记录错误并结束本次运行，已经成功写入的本地公司数据保留，等待下一个周期重试。

应用启动不执行阻塞或额外异步全量同步，也不引入分布式调度锁。多实例同时运行时依赖 `UNIQUE(company_id)` 和幂等 upsert 保持数据安全。
