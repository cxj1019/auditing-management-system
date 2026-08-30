## Why

合同是事务所业务的核心载体：项目承接、收款计划、成本归集都围绕合同展开。系统骨架已就绪（认证、RBAC、模块扩展点均已验证），需要按既定路线落地第一个业务模块——合同管理，为后续收款、报销、函证、成本分析提供业务锚点。

## What Changes

- 新增合同管理能力：合同登记（自动编号）、分页查询与多条件筛选、详情查看、编辑、状态流转（草稿 → 执行中 → 已完成/已终止）、删除（仅草稿）
- 新增数据库表 `contract`（Flyway V3）与菜单权限种子数据（Flyway V4，含角色授权）
- 填充既有 `contract` 占位模块的 Controller/Service/Mapper/Entity
- 前端新增「业务管理 → 合同管理」页面（列表 + 筛选 + 新增/编辑弹窗 + 状态流转操作），注册模块路由
- 权限点：`business:contract:list/add/edit/delete/status`；admin 全部，manager 增删改查+流转，employee 仅查询

## Capabilities

### New Capabilities
- `contract-management`: 合同登记、查询、编辑、状态流转与删除的全生命周期管理能力

### Modified Capabilities
（无——本变更不改变既有能力的规格行为）

## Impact

- 后端：填充 `com.accounting.firm.contract` 包（已预留模板）；新增 Flyway 迁移 V3（建表）、V4（菜单与角色授权种子数据）；无破坏性变更
- 数据库：新增 `contract` 表；`sys_menu` 新增业务管理目录与合同管理菜单/按钮；`sys_role_menu` 为 admin/manager/employee 增加授权行
- 前端：`src/api/contract.ts`、`src/views/business/contract/index.vue`、路由模块注册
- 依赖关系：收款情况模块将引用合同的编号与名称（后续变更处理），本变更不引入对其他业务模块的依赖