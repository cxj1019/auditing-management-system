## 1. 数据库迁移

- [x] 1.1 创建 V5__payment_schema.sql：contract_payment 表（id、contract_id、amount NUMERIC(14,2)、payment_date DATE、payment_method VARCHAR(30)、payer_name VARCHAR(100)、remark VARCHAR(500)、审计字段，contract_id 索引），验证启动迁移成功
- [x] 1.2 创建 V6__payment_menu_seed.sql：收款管理菜单(id=110, perm=business:collection:list) + 按钮 111–114（add/edit/delete），admin/manager 全量授权、employee 仅 list，setval 推进序列，验证三角色权限正确

## 2. 后端实现

- [x] 2.1 实现 ContractPayment 实体/Mapper（含 JOIN contract 的动态列表查询与 GROUP BY 汇总查询）、PaymentRequest/PaymentVO/CollectionSummaryVO，验证编译通过
- [x] 2.2 实现 CollectionService：登记（校验合同存在且非草稿、金额>0）、编辑（不可换合同）、删除、分页筛选查询、按合同汇总（含进度计算），验证业务规则正确
- [x] 2.3 实现 CollectionController：GET/POST/PUT /api/payments、DELETE /api/payments/{id}、GET /api/payments/summary，全部加 @PreAuthorize 与 @AuditLog，验证统一响应结构
- [x] 2.4 单元测试：金额校验与汇总进度计算的纯函数逻辑，验证 mvn test 通过

## 3. 前端实现

- [x] 3.1 新增 src/api/collection.ts 与类型定义（PaymentItem/PaymentRequest/CollectionSummaryItem）
- [x] 3.2 新增 views/business/collection/index.vue：el-tabs 双页签——「收款记录」（表格+筛选+新增/编辑弹窗+删除）与「收款汇总」（合同维度表格+el-progress 进度条）
- [x] 3.3 注册 /business/collection 路由（perm=business:collection:list），验证有权限角色可见菜单

## 4. 集成验证

- [x] 4.1 端到端：admin 登记两笔收款 → 列表可见且带合同信息 → 汇总页已收合计与进度正确 → 编辑一笔金额 → 汇总更新 → 删除一笔 → 汇总更新
- [x] 4.2 业务规则：对草稿合同登记被拒、金额 0 被拒、编辑换合同被忽略
- [x] 4.3 权限验证：employee 仅可查看记录与汇总，新增/编辑/删除接口返回 403；审计日志记录操作人与内容
