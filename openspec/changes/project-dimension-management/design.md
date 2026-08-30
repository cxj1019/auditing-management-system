## Context

用户确认架构调整：项目（Project）为顶层业务实体，合同挂项目下（一个审计项目对应一份约定书），收款经合同间接归集到项目，报销/人工成本/函证直接挂项目。存量测试数据清空重来。Flyway 当前 v12，新迁移 V13/V14。

## Goals / Non-Goals

**Goals:**
- 建立项目台账（CRUD + 状态机：进行中→已完成→归档，支持重开）
- 全部业务对象的项目归属关系落地，盈亏分析按项目聚合
- 存量测试数据一次性清空（TRUNCATE + 序列重置），权限体系数据保留

**Non-Goals:**
- 不做客户主数据管理（客户名称仍为文本字段）
- 不做项目预算管理
- 不做项目级权限隔离（数据范围仍为全员可见）
- 收款不改为直接挂项目（保持挂合同，行业惯例按约定书回款）

## Decisions

**1. 项目编号 PRJ+yyyyMMdd+4 位流水，状态机三态**
IN_PROGRESS(0) → FINISHED(1) → ARCHIVED(2)；FINISHED 可重开回 IN_PROGRESS；ARCHIVED 终态。复用既有编号生成器模式。
替代方案：项目编号手工录入——保持全系统自动编号一致性。

**2. 归档即冻结：归档项目不可编辑、不可挂新合同、不可再登记报销/人工成本/函证**
「归档」语义 = 该项目完全结案。已完成但未归档的项目仍可继续收款与归集成本（真实业务中尾款和费用常在完成后才发生）。
替代方案：仅完成即可挂新合同——归档失去意义。

**3. 数据清理放在 V13 迁移内按序执行**
先 TRUNCATE 业务表（contract_payment、reimbursement、labor_cost、confirmation、contract，RESTART IDENTITY 重置序列），再执行 DDL（建 project 表、contract 加 project_id NOT NULL、三表 RENAME contract_id→project_id）。PostgreSQL DDL 可事务化，迁移原子生效。sys_menu/sys_role_menu/flyway 历史不动。
替代方案：DROP 全库重建——会丢失菜单权限数据，拒绝。

**4. 报销/函证的 project_id 可空（公共费用/无项目函证），人工成本 project_id 必填**
保留原口径：未关联的已批准报销计入公共费用不参与项目毛利。人工成本必然属于项目，必填。

**5. 成本分析聚合 SQL 以 project 为主表，三个子查询分别汇总收入/报销/人工**
收入子查询经 contract 表按 project_id 分组（JOIN contract_payment）；报销子查询过滤 status=1（已批准）且 project_id 非空；概览统计服务层对聚合结果求和，回款率分母为各项目合同总额之和。

**6. 菜单 ID 150 段：150=项目管理菜单，151–155=按钮（add/edit/delete/status）**
角色授权 admin/manager 全量、employee 仅 list。前端路由 /business/project 注册进 moduleRoutes。

## Risks / Trade-offs

- TRUNCATE 清空测试数据不可恢复 → 用户已明确选择清库重来
- 合同列表展示项目信息需联表 → 冗余 project_no/project_name 到查询 VO，避免 N+1
- 已完成项目可继续收款但不可挂新合同 → 符合「尾款照收、不再签约」的业务现实

## Migration Plan

V13/V14 随启动自动执行（含清数据）；应用重启后系统即为项目维度结构。回滚需回退代码并手工恢复数据（测试环境可接受）。

## Open Questions

- 项目是否需要关联团队成员（多负责人）——本期单负责人字段，后续按需扩展