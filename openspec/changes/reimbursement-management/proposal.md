## Why

事务所员工垫付的差旅、办公等费用目前靠纸质单据线下签字，流程慢、难追溯。系统骨架与业务模块规范已就绪，落地报销管理实现线上化提交与审批。

## What Changes

- 新增报销管理能力：报销单提交（自动编号）、分页查询、受控编辑/删除（仅待审批 + 本人或审批人）、审批流（批准/驳回 + 意见，禁止自审，终态锁定）
- 新增数据库表 `reimbursement`（Flyway V7）与菜单权限种子数据（Flyway V8，菜单 ID 120 段）
- 前端新增「业务管理 → 报销管理」页面（列表 + 提交/编辑弹窗 + 审批操作）

## Capabilities

### New Capabilities
- `reimbursement-management`: 费用报销单的提交、检索、受控修改与审批能力

### Modified Capabilities
（无）

## Impact

- 后端：新增 `com.accounting.firm.reimbursement` 包；Flyway V7/V8；无破坏性变更
- 数据库：新增 `reimbursement` 表；`sys_menu` 增加 120–124；角色授权 admin/manager 全量（含审批），employee 无审批权限
- 前端：`src/api/reimbursement.ts`、`src/views/business/reimbursement/index.vue`、路由注册
- 关联：cost-analysis 模块后续将聚合已批准报销作为成本来源