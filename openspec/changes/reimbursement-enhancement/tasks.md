## 1. 数据库迁移

- [x] 1.1 创建 V17__reimbursement_enhancement.sql（清数据+头表改造+明细/附件两新表+财务权限点）
- [x] 1.2 创建 V18__attachment_item.sql（reimbursement_attachment 加 item_id 列关联明细行）

## 2. 后端实现

- [x] 2.1 状态机重构：DRAFT/PENDING/APPROVED/REJECTED/PENDING_FINAL 五态（含撤回流转）
- [x] 2.2 明细行：ReimbursementItem 实体/Mapper；ReimbursementRequest 改为 title+projectId+items[]；Service 同事务重算 total_amount；提交校验明细非空
- [x] 2.3 生命周期接口：创建/更新/提交/撤回/审批二级阈值/删除；明细增量同步（保留行 ID 维持附件关联）
- [x] 2.4 财务接口：receive-invoice/mark-paid（finance 权限点，硬约束未收票不可付款）
- [x] 2.5 发票附件：Supabase Storage 存储；支持 itemId 关联明细行；上传限本人草稿态
- [x] 2.6 导出数据接口：GET /api/reimbursements/export-items（日期范围扁平明细 JSON）
- [x] 2.7 创建接口返回草稿 ID（供前端行级上传）；GET /{id}/items 明细查询端点
- [x] 2.8 超时调优：后端 Supabase 请求超时 30s→60s；前端上传请求超时 15s→60s
- [x] 2.9 mvn test 全部通过（39/39）

## 3. 前端实现

- [x] 3.1 安装 xlsx/jspdf/html2canvas 依赖；类型定义同步
- [x] 3.2 报销页面重构：列表加草稿/待终审状态与财务标记列；明细行编辑器（增删行自动合计）
- [x] 3.3 保存草稿后弹窗保持打开、切换编辑模式、明细行获取 ID 后每行可上传发票
- [x] 3.4 详情抽屉：明细清单+发票附件+审批操作+财务面板+PDF 导出
- [x] 3.5 工具栏 Excel 导出（xlsx 生成）；详情 PDF 导出（html2canvas+jspdf）
- [x] 3.6 类型检查通过

## 4. 集成验证

- [x] 4.1 草稿创建自动合计 → 发票上传 → 提交 → 撤回 → 再提交 → 经理审批 → 财务收票付款
- [x] 4.2 二级审批：大额转待终审 → admin 终审；自审禁止
- [x] 4.3 业务规则：无明细提交被拒、未收票付款被拒、终态编辑被拒
- [x] 4.4 行级附件：创建返回 ID → 明细带 ID → 按明细行上传发票 → 附件 itemId 匹配 → 更新保留行 ID
- [x] 4.5 权限：employee 财务/审批返回 403；Excel 导出 3 行含项目/状态/审批人
