## Why

借鉴事务所已有的 ExpenseFlow 报销原型（Next.js + Supabase，经分析确认其数据模型与流程设计成熟），将现有单金额、单级审批的简易报销升级为标准报销闭环：费用明细行、发票附件、财务收票/付款环节、二级审批、撤回与导出。

## What Changes

- **BREAKING** 报销单模型重构：头表保留标题/项目/状态/审批字段，新增财务标记（已收发票/已付款）与一级审批人记录；金额/类别/日期下沉到新表 `reimbursement_item`（费用明细行，含发票号与是否增值税发票）
- **BREAKING** 状态机重构：草稿 → 待审批 →（金额超阈值或转交）待终审 → 已批准/已驳回；支持提交后撤回；终态锁定
- 新增发票附件：复用 Supabase Storage，附件挂报销单（图片/PDF）
- 新增财务环节：具备财务权限者对已批准单据标记「已收发票」→「已付款」（硬约束：未收发票不可付款）
- 新增导出：费用明细 Excel 导出（前端 xlsx）；报销单 PDF 导出（jspdf + html2canvas）
- 存量报销测试数据清空
- 二级审批阈值可配置（默认 5000 元）

## Capabilities

### New Capabilities
（无——均为 reimbursement-management 能力增强）

### Modified Capabilities
- `reimbursement-management`: 全面重构——明细行模型、草稿/提交/撤回生命周期、二级审批、财务收票付款环节、发票附件、Excel/PDF 导出

## Impact

- 后端：reimbursement 包大改；V17 迁移（清数据+改列+两张新表+菜单权限点 125 财务操作）
- 前端：报销页面重写（明细行编辑器、详情抽屉、财务面板、导出按钮）；新增依赖 xlsx/jspdf/html2canvas
- 数据库：TRUNCATE reimbursement；新增 reimbursement_item / reimbursement_attachment 表