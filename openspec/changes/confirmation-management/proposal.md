## Why

函证（银行函证、往来款函证）是审计业务的核心程序，事务所目前用 Excel 台账跟踪函证的发出与回函情况，容易遗漏逾期未回函的函证。系统已有合同与项目载体，落地函证管理实现发函到回函的全过程跟踪。

## What Changes

- 新增函证管理能力：函证登记（自动编号 HZ+yyyyMMdd+流水）、分页查询（按状态/类型/关键字筛选）、编辑、删除（仅未发出）、状态流转（未发出 → 已发出 → 已回函 / 已作废）
- 逾期提醒字段：发出后 N 天未回函在列表中标记逾期（阈值默认 30 天，可配置）
- 新增数据库表 `confirmation`（Flyway V9）与菜单权限种子数据（Flyway V10，菜单 ID 130 段）
- 前端新增「业务管理 → 函证管理」页面（列表 + 登记/编辑弹窗 + 流转操作 + 逾期高亮）

## Capabilities

### New Capabilities
- `confirmation-management`: 函证登记、跟踪、状态流转与逾期标记能力

### Modified Capabilities
（无）

## Impact

- 后端：新增 `com.accounting.firm.confirmation` 包；Flyway V9/V10；无破坏性变更
- 数据库：新增 `confirmation` 表；`sys_menu` 增加 130–134；角色授权 admin/manager 全量、employee 仅查询
- 前端：`src/api/confirmation.ts`、`src/views/business/confirmation/index.vue`、路由注册
- 关联：函证可关联合同（项目），成本分析模块不直接依赖本模块