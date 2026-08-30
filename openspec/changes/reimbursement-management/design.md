## Context

合同、收款模块已上线（Flyway 至 v6）。报销单需要申请人身份——从 SecurityContext 取当前登录用户（SecurityUser 提供 userId/username/nickname），无需额外用户选择器。审批人同样取自上下文。数据库 Supabase PostgreSQL，新迁移从 V7 开始。

## Goals / Non-Goals

**Goals:**
- 报销单提交 → 审批（批准/驳回）的最小闭环，含受控编辑/删除
- 编号规则复用合同模块的生成器模式（BX 前缀）
- 权限点 + 数据范围规则（本人 or 审批人）双层控制

**Non-Goals:**
- 不做多级审批/会签（单级审批即可满足当前规模）
- 不做附件票据上传（后续单独评估）
- 不做报销打款与财务支付对接
- 不做金额预算控制

## Decisions

**1. 单表 `reimbursement`，状态机 PENDING→APPROVED/REJECTED**
状态仅 3 个，Service 层显式校验流转；终态锁定编辑/删除。
替代方案：工作流引擎——设计阶段已明确排除。

**2. 数据范围规则在 Service 层实现**
编辑/删除：`申请人本人 OR 具备 business:reimbursement:approve 权限`，且状态必须为待审批。不引入数据权限中间件，规则简单直接。
替代方案：@PostFilter 按行过滤——可读性差且当前无此规模需求。

**3. 禁止自审在 Service 层校验**
审批接口比较当前 username 与单据 applicant_username，相同即拒绝。这是财务合规的基本要求，不可配置关闭。

**4. 申请人/审批人冗余存储 username 与 nickname**
列表展示需要姓名，避免联表 sys_user；username 用于身份判定，nickname 用于展示。

**5. 迁移 V7（建表）/ V8（菜单种子），菜单 ID 120 段**
120=报销管理菜单，121–124=按钮（add/edit/delete/approve）；admin/manager 全量授权，employee 授予 list/add/edit/delete（无 approve）；setval 推进序列。

**6. 合同关联可选**
contract_id 可空；成本分析模块聚合时按 contract_id 归集项目成本，未关联的计入公共费用。

## Risks / Trade-offs

- 单级审批可能不满足大额报销管控 → 后续可加金额阈值二级审批，本期不做
- employee 可见他人报销单 → 与既有模块口径一致（内网全员可见），后续可加"仅看本人"开关
- 驳回后需重新提交新单 → 本期不支持驳回后再编辑，保持终态语义简单

## Migration Plan

V7/V8 随启动自动执行；回滚即回退代码。无存量数据迁移。

## Open Questions

- 大额报销是否需要多级审批阈值——留待实际使用后评估