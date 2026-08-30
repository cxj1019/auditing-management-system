## Why

事务所需要回答「每个项目赚了多少钱」：合同收入与项目成本（已批准报销、人工成本）分散在各模块中，缺乏统一的利润视图。各业务模块数据已就绪，落地成本分析提供按项目的收入-成本-利润汇总看板。

## What Changes

- 新增成本分析能力：按合同维度聚合收入（收款合计）、直接成本（关联该合同的已批准报销合计）、毛利与毛利率；提供全局经营概览（总收入/总成本/总毛利/回款率）
- 新增人工成本登记：`labor_cost` 表支持录入项目人工投入（人员、月份、金额），作为报销之外的成本来源（Flyway V11）
- 菜单权限种子数据（Flyway V12，菜单 ID 140 段）：业务管理 → 成本分析
- 前端新增「业务管理 → 成本分析」页面：经营概览统计卡片 + 项目利润表（含毛利率进度条）+ 人工成本登记维护

## Capabilities

### New Capabilities
- `cost-analysis`: 项目收入成本利润聚合分析与人工成本登记能力

### Modified Capabilities
（无——只读聚合既有模块数据 + 新增人工成本表）

## Impact

- 后端：新增 `com.accounting.firm.cost` 包；Flyway V11/V12；聚合查询复用 collection/reimbursement 既有表，无破坏性变更
- 数据库：新增 `labor_cost` 表；`sys_menu` 增加 140–143；角色授权 admin/manager 全量、employee 仅查看
- 前端：`src/api/cost.ts`、`src/views/business/cost/index.vue`、路由注册
- 口径说明：收入=contract_payment 合计；直接成本=reimbursement(已批准, contract_id 非空) 合计 + labor_cost 合计；未关联合同的已批准报销计入公共费用不参与项目毛利