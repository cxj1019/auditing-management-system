## Why

事务所的业务核心是「项目」（如 XX 公司 2025 年度审计），合同、收款、成本、函证都围绕项目发生。当前系统以合同为中心，缺少项目载体，无法直接回答「这个项目赚了多少钱」。调整为按项目维度管理，使盈亏分析直接落在项目上。

## What Changes

- 新增项目管理能力：项目台账（自动编号 PRJ+日期+流水）、标准字段（名称/类型/客户/负责人/期间/状态）、状态流转（进行中→已完成→已归档，支持重开）
- **BREAKING** 合同必须归属项目：`contract` 新增 `project_id` 必填列，登记合同时选择进行中的项目
- **BREAKING** 报销、人工成本、函证的关联从合同改为项目（`contract_id` → `project_id`）；收款保持挂合同、经合同间接归集到项目
- 成本分析聚合维度从合同改为项目：项目收入 = 项目下全部合同收款合计；成本 = 挂项目的已批准报销 + 人工成本；输出项目毛利与毛利率
- 存量测试数据清空（TRUNCATE 业务表并重置序列），权限体系数据保留

## Capabilities

### New Capabilities
- `project-management`: 项目台账管理与状态流转能力

### Modified Capabilities
- `contract-management`: 合同登记新增必填的项目归属；列表与详情展示所属项目
- `collection-management`: 收款记录展示所属项目信息（关联结构不变，仍挂合同）
- `reimbursement-management`: 报销单关联从合同改为项目（可空，未关联计入公共费用）
- `confirmation-management`: 函证关联从合同改为项目（可空）
- `cost-analysis`: 聚合维度从合同改为项目，人工成本挂项目；概览口径同步调整

## Impact

- 后端：新增 `com.accounting.firm.project` 包；contract/reimbursement/cost/confirmation 四个包的实体与校验调整；Flyway V13（建表+改列+清数据）/V14（菜单种子）
- 数据库：新增 `project` 表；`contract` 加 `project_id NOT NULL`；三张业务表 `contract_id` 重命名为 `project_id`；业务表数据清空
- 前端：新增项目管理页面；合同/报销/人工成本/函证弹窗的关联选择器从合同换为项目；成本分析页面按项目展示
- 权限：新增 `business:project:*` 权限点（菜单 ID 150 段），admin/manager 全量、employee 仅查询