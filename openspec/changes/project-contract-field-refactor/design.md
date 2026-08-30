## Context

字段级调整，涉及 project/contract 两实体。当前库中数据为测试数据（ABC 项目一份），迁移需兼容已有行。

## Goals / Non-Goals

**Goals:** 消除客户名重复录入；负责人语义精确化（项目经理/现场负责人/合同保管人）。
**Non-Goals:** 不做客户主数据；不改收款/成本等其他模块口径（收款 VO 客户改取自项目属顺带修正）。

## Decisions

**1. V15 迁移兼容存量行**
`project.owner_name` RENAME 为 `manager_name`（保留原值）；新增 `site_leader_name VARCHAR(50) NOT NULL DEFAULT ''`（存量行走默认值，应用层对新数据强制必填）。`contract` DROP `client_name`、`owner_name` RENAME 为 `keeper_name`。
替代方案：清库重来——用户可能已录入自己的数据，不做破坏性清理。

**2. 合同客户筛选走 EXISTS 子查询**
MyBatis-Plus Wrapper `.apply("EXISTS (SELECT 1 FROM project p WHERE p.id = contract.project_id AND p.client_name LIKE {0})", kw)`，参数绑定安全。

**3. 收款联表客户改取项目**
PaymentVO.clientName 数据源从 `c.client_name` 改为 `pr.client_name`（该查询已 LEFT JOIN project）。

## Risks / Trade-offs

- 存量项目 site_leader 为空串 → 前端编辑时必填校验会要求补填，符合预期
- ContractVO.clientName 保留（来自项目），前端只读展示

## Migration Plan

V15 随启动自动执行；回滚即回退代码。

## Open Questions
（无）
