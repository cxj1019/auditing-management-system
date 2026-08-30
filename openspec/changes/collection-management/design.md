## Context

合同管理已完成（contract 表含 contract_no/name/client_name/amount/status）。本模块在其上叠加回款记录与汇总。数据库 Supabase PostgreSQL，Flyway 当前版本 v4，新迁移从 V5 开始。前端模块路由规范与权限指令已就绪。

## Goals / Non-Goals

**Goals:**
- 收款记录 CRUD + 按合同汇总进度，一个页面两个页签交付
- 复用既有基础设施：ApiResult/@PreAuthorize/@AuditLog/Flyway/分页插件
- 列表与汇总均带出合同冗余信息（编号/名称/客户），避免前端二次请求

**Non-Goals:**
- 不做开票管理与发票关联
- 不做分期收款计划（计划 vs 实际的对比后续再评估）
- 不做银行流水自动对账
- 不做退款单独立流程（删除记录即可纠错）

## Decisions

**1. 单表 `contract_payment`，contract_id 外键逻辑关联**
不建物理外键约束（保持与既有表风格一致，应用层校验合同存在性与状态），索引建在 contract_id 上保证汇总聚合性能。
替代方案：物理 FK——Supabase 下可行但删合同级联语义复杂，本期不需要。

**2. 草稿合同禁止收款，其余状态允许**
执行中是主要场景；已完成合同可能有尾款到账，已终止可能收到前期欠款，一律放行，避免过度限制。
替代方案：仅执行中可收款——会挡住真实业务，拒绝。

**3. 编辑不允许更换所属合同**
换合同 = 删了重录，语义更清晰且避免汇总口径混乱；DTO 不暴露 contractId 于编辑场景。
替代方案：允许换绑——增加校验复杂度，收益低。

**4. 汇总用一条 GROUP BY 聚合 SQL（LEFT JOIN 保证零收款合同也出现）**
`SELECT c.id, c.contract_no, ..., COALESCE(SUM(p.amount),0) FROM contract c LEFT JOIN contract_payment p ... GROUP BY c.id ORDER BY c.create_time DESC`，关键字过滤放 WHERE。
替代方案：内存聚合——数据量大时低效。

**5. 迁移 V5（建表）/ V6（菜单种子），菜单 ID 使用 110 段**
110=收款管理菜单（parent=100 业务管理），111–114=按钮；角色授权与合同模块一致（admin/manager 全量、employee 仅 list）；setval 推进 sys_menu 序列。

**6. 前端单页面 el-tabs 双页签**
「收款记录」（表格+CRUD 弹窗）与「收款汇总」（表格+el-progress 进度条），共享筛选关键字，减少页面跳转。

## Risks / Trade-offs

- 并发登记收款导致汇总瞬时偏差 → 汇总实时计算（非冗余字段），天然一致；量级小无性能问题
- 删除合同后收款记录成孤儿 → 本期合同无删除场景（仅草稿可删且草稿不可收款），未来若开放需级联策略
- employee 可见全部合同收款 → 内网全员可见财务汇总符合当前事务所规模，后续可加数据权限

## Migration Plan

V5/V6 随启动自动执行；回滚即回退代码。无存量数据迁移。

## Open Questions

- 收款方式字典（转账/现金/支票/其他）是否需要可配置——本期硬编码，后续按需做字典模块