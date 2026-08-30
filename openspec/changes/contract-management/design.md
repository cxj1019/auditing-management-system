## Context

系统骨架已完成并通过集成验证：认证/RBAC/统一响应/审计/模块扩展点均可用（见 system-foundation 变更）。数据库为 Supabase PostgreSQL，Flyway 已有 V1/V2 基线。后端 `com.accounting.firm.contract` 占位模块与前端模块路由规范已就位。本设计只覆盖合同管理本身；收款、报销等模块在后续变更中实现。

## Goals / Non-Goals

**Goals:**
- 合同 CRUD + 受控状态机的完整闭环（后端接口 + 前端页面）
- 复用既有基础设施：ApiResult、@PreAuthorize 权限点、@AuditLog 审计、Flyway 迁移、分页插件
- 菜单与权限种子数据随迁移脚本落库，三个内置角色开箱即用

**Non-Goals:**
- 不做合同附件上传（后续按需单独评估）
- 不做回款计划/回款登记（属于收款情况模块）
- 不做合同到期提醒、消息通知
- 不做客户主数据管理（本期客户名称为文本字段）

## Decisions

**1. 单表建模 `contract`，客户暂为文本字段**
合同核心字段一次到位（编号、名称、客户、类型、金额、签约/起止日期、负责人、状态、备注），不引入客户表——当前无客户主数据诉求，YAGNI。
替代方案：客户外键关联——需要先建客户管理模块，超出本变更范围。

**2. 编号规则 HT+yyyyMMdd+4 位流水，数据库唯一约束兜底**
应用层按「当日最大流水 + 1」生成，`contract_no` 唯一索引防并发重复；极端并发下插入失败由唯一约束拦截并重试一次。
替代方案：数据库序列——跨天重置逻辑复杂，收益低。

**3. 状态机在 Service 层显式校验**
状态枚举 DRAFT/RUNNING/FINISHED/TERMINATED，合法流转表硬编码在服务层（DRAFT→RUNNING、RUNNING→FINISHED、RUNNING→TERMINATED），非法流转抛 BusinessException。前端按钮可用性同步此规则。
替代方案：状态模式类族——状态仅 4 个且流转简单，过度设计。

**4. 状态流转走独立接口 PUT /api/contracts/{id}/status**
编辑接口不接收 status 字段（DTO 层面隔离），避免「顺带改状态」绕过状态机。
替代方案：编辑接口内联改状态——校验逻辑分散，易出漏洞。

**5. 迁移编号 V3（建表）/ V4（菜单种子），菜单挂在新目录「业务管理」下**
V4 向 sys_menu 插入业务管理目录(id=100)、合同管理菜单(id=101)与 5 个按钮(102–106)，并为 admin(全量)/manager(list/add/edit/delete/status)/employee(list) 写入 sys_role_menu。ID 使用 100 段避开系统管理段（1–43）。
替代方案：应用启动时编程式注册——权限数据应与结构数据一样版本化可追溯。

**6. 前端页面放 views/business/contract/，路由注册进 modules/index.ts 的 moduleRoutes**
遵循既定模块规范；菜单 component 字段填 business/contract/index。

## Risks / Trade-offs

- 当日流水号并发冲突 → 唯一约束 + 单次重试；量级极小（事务所日签约个位数）
- employee 角色仅查询是否符合预期 → 遵循最小权限默认值，管理员可在角色管理中自行调整授权
- contract 表未来扩展附件/回款关联 → 预留 id 主键与 contract_no 业务键，后续模块以 contract_id 关联

## Migration Plan

Flyway V3/V4 随应用启动自动执行；回滚即回退代码（表与菜单数据保留不影响既有功能）。无存量数据迁移。

## Open Questions

- 合同类型字典（审计/税务/咨询/评估等）是否需要可配置——本期硬编码枚举，若需自定义字典在后续变更中处理