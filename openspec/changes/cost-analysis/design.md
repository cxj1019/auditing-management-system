## Context

收款（contract_payment）、报销（reimbursement，含 status 与 contract_id）表已存在。成本分析是只读聚合 + 一张新表（labor_cost）。Flyway 当前 v10，新迁移 V11/V12。

## Goals / Non-Goals

**Goals:**
- 一条聚合 SQL 出项目利润表（合同 LEFT JOIN 三类金额子查询）
- 概览统计复用同一聚合结果在服务层求和，避免多次全表扫描
- 人工成本 CRUD 复用既有模块模式

**Non-Goals:**
- 不做预算管理与偏差分析
- 不做按人员/按月份的多维交叉报表（本期仅项目维度 + 月份字段留档）
- 不做图表可视化（统计卡片 + 表格已满足，图表后续增强）
- 不做薪酬数据导入（钉钉导入后置，人工成本先手工登记）

## Decisions

**1. 聚合口径固定并写入规格**
收入 = contract_payment 全部记录合计（不区分合同状态）；直接成本 = 已批准报销（status=APPROVED 且 contract_id 非空）+ labor_cost 全部；毛利 = 已收 − 直接成本；毛利率 = 毛利 / 已收（已收 0 → null）。口径一旦实现即成为系统行为契约。
替代方案：按合同状态过滤收入——已终止合同的历史收款仍是真实收入，不排除。

**2. 毛利率后端计算返回，已收为 0 返回 null**
前端对 null 显示「—」，避免除零与误导性的 0%。
替代方案：前端计算——口径散落两处，后端统一。

**3. labor_cost 独立表而非复用报销**
人工成本（月度工时成本）与员工报销（票据报销）业务语义、审批流不同，混表会增加状态机复杂度。
替代方案：复用 reimbursement 加类别——审批流对人工成本无意义，拒绝。

**4. 迁移 V11（建表）/ V12（菜单种子），菜单 ID 140 段**
140=成本分析菜单（perm business:cost:list），141–143=人工成本按钮（labor-add/labor-edit/labor-delete）；admin/manager 全量、employee 仅 list。

**5. 唯一性约束：同一合同同一人员同一月份仅一条人工成本**
防止重复登记，唯一索引 (contract_id, person_name, cost_month)。

## Risks / Trade-offs

- 聚合查询随数据量增长变慢 → 当前量级（千级合同）无压力；后续可加汇总表
- 报销驳回/删除后利润变化 → 实时聚合天然一致
- 人工成本无审批流 → 登记即生效，依赖权限约束（仅 manager/admin 可维护）

## Migration Plan

V11/V12 随启动自动执行；回滚即回退代码。无存量数据迁移。

## Open Questions

- 是否需要按月份的成本趋势图——留待前端增强变更