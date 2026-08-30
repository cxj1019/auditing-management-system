## Why

项目与合同各自填写客户名和负责人，数据重复且可能不一致。合同挂项目后客户应随项目带出；负责人语义需要更精确：项目负责人拆分为项目经理与现场负责人，合同侧改为合同保管人。

## What Changes

- **BREAKING** 项目实体：`owner_name` 拆分为 `manager_name`（项目经理）与 `site_leader_name`（项目现场负责人），均为必填
- **BREAKING** 合同实体：删除 `client_name`（客户随所属项目带出，只读展示）；`owner_name` 重命名为 `keeper_name`（合同保管人，必填）
- 合同列表的客户筛选改为按项目客户匹配；收款记录的客户展示改为取自项目
- Flyway V15 执行列调整

## Capabilities

### New Capabilities
（无）

### Modified Capabilities
- `project-management`: 项目登记字段调整——项目经理、项目现场负责人替代原项目负责人
- `contract-management`: 合同登记字段调整——移除客户名称与项目负责人，新增合同保管人；客户信息随项目只读展示；查询支持按项目客户筛选

## Impact

- 后端：project/contract 实体与 DTO 字段调整；合同客户筛选改走 EXISTS 子查询；collection 联表客户取自 project
- 数据库：V15 迁移（RENAME/ADD/DROP COLUMN）
- 前端：项目表单两个负责人字段；合同表单移除客户与负责人输入、新增保管人；列表列同步