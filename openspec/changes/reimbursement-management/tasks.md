## 1. 数据库迁移

- [x] 1.1 创建 V7__reimbursement_schema.sql：reimbursement 表（id、reimbursement_no 唯一、applicant_username、applicant_name、contract_id 可空、category VARCHAR(30)、amount NUMERIC(14,2)、expense_date DATE、title VARCHAR(200) 事由、status SMALLINT 默认 0 待审批、approver_username、approver_name、approve_time TIMESTAMP、approve_comment VARCHAR(300)、审计字段），验证启动迁移成功
- [x] 1.2 创建 V8__reimbursement_menu_seed.sql：报销管理菜单(id=120, perm=business:reimbursement:list) + 按钮 121–124（add/edit/delete/approve），admin/manager 全量、employee 无 approve，setval 推进序列，验证角色权限正确

## 2. 后端实现

- [x] 2.1 实现 Reimbursement 实体/Mapper、ReimbursementRequest/ReimbursementVO、状态枚举（PENDING/APPROVED/REJECTED）与编号生成（BX 前缀），验证编译通过
- [x] 2.2 实现 ReimbursementService：提交（申请人取当前用户）、分页筛选、编辑/删除（待审批 + 本人或具审批权限）、审批（批准/驳回 + 意见必填 + 禁止自审 + 终态锁定），验证业务规则正确
- [x] 2.3 实现 ReimbursementController：GET/POST /api/reimbursements、PUT/DELETE /api/reimbursements/{id}、PUT /api/reimbursements/{id}/approve，全部加 @PreAuthorize 与 @AuditLog，验证统一响应
- [x] 2.4 单元测试：状态机流转与编号生成，验证 mvn test 通过

## 3. 前端实现

- [x] 3.1 新增 src/api/reimbursement.ts 与类型定义
- [x] 3.2 新增 views/business/reimbursement/index.vue：列表（状态标签+审批意见列）+ 提交/编辑弹窗 + 审批操作（批准/驳回+意见输入，v-permission 控制）
- [x] 3.3 注册 /business/reimbursement 路由，验证有权限角色可见菜单

## 4. 集成验证

- [x] 4.1 端到端：employee 提交报销单 → manager 批准 → 状态与审批人正确 → 再提交一份 → manager 驳回附意见 → 终态不可编辑
- [x] 4.2 业务规则：自审被拒、非本人且无审批权限编辑被拒、金额 0 被拒、已审批删除被拒
- [x] 4.3 权限验证：employee 无 approve 权限调用审批返回 403；审计日志记录提交与审批操作
