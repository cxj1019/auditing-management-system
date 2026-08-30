## Context

借鉴 ExpenseFlow 原型（数据模型：reports 头 + expenses 明细 + receipt_urls 发票数组 + is_invoice_received/is_paid 财务标记；状态机含 draft/submitted/pending_partner_approval）。我们已有 Supabase Storage 附件机制（contract_attachment 模式）与 RBAC。Flyway 当前 v16，新迁移 V17。

## Goals / Non-Goals

**Goals:** 明细行模型、草稿-提交-撤回生命周期、二级审批（阈值可配）、财务收票/付款硬约束、发票附件、Excel/PDF 导出。
**Non-Goals:** 不做多部门组织架构；不做 R2/预签名直传（走后端中转，内网带宽足够）；不做在线预览 Office；不引入消息通知。

## Decisions

**1. 头表冗余 total_amount，保存明细时重算**
列表页高频展示总额，实时 SUM 每次联查明细代价高；明细保存与头更新放同一事务保证一致。
替代方案：查询时聚合——列表 N+1 聚合，拒绝。

**2. 状态机五态：DRAFT(0)/PENDING(1)/APPROVED(2)/REJECTED(3)/PENDING_FINAL(4)**
财务用两个布尔标记（is_invoice_received/is_paid）而非状态——原型验证该设计可让「已批准+已收票+已付款」三维独立可见。
替代方案：把收票/付款做成状态——组合爆炸，拒绝。

**3. 二级审批规则简化映射**
原型的「经理→合伙人」映射为「manager→admin」：一级审批人（非 admin）批准时若 total_amount > 阈值 → PENDING_FINAL，仅 admin 可终审；admin 自己的一级批准即终审。阈值 `reimbursement.second-approval-threshold:5000` 配置化。

**4. 附件挂单不挂明细**
发票附件统一挂报销单（UI 在详情处管理），避免明细行级附件的交互复杂度；规格已按此表述。
替代方案：明细行内嵌上传——首版 UI 复杂度高，后续按需细化。

**5. 导出分工：Excel 前端生成、PDF 前端渲染**
xlsx（SheetJS）在浏览器端生成 xlsx；jspdf+html2canvas 将报销单模板组件转 PDF。后端只提供数据查询接口（扁平明细 JSON）。
替代方案：后端 POI/iText——增加依赖与服务端负载，前端方案原型已验证。

**6. V17 迁移**
TRUNCATE reimbursement RESTART IDENTITY；DROP COLUMN category/amount/expense_date；ADD is_invoice_received/is_paid BOOLEAN DEFAULT FALSE、primary_approver_name VARCHAR(50)；建 reimbursement_item 与 reimbursement_attachment 两表；sys_menu 加 125 财务权限点（admin/manager 授权）。

## Risks / Trade-offs

- total_amount 冗余一致性 → 同事务重算兜底
- 阈值审批对 admin 自身提交的单据跳过二级 → admin 提交由 manager 审批即为终审，符合「最高角色无需自审升级」
- html2canvas 对复杂样式渲染偏差 → 报销单模板保持简洁表格风格
- 清空报销测试数据 → 用户已确认

## Migration Plan

V17 随启动自动执行；回滚即回退代码（测试数据不恢复）。

## Open Questions
（无）
