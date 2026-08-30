## 1. 数据库迁移

- [x] 1.1 创建 V9__confirmation_schema.sql：confirmation 表（id、confirmation_no 唯一、type VARCHAR(30)、target_unit VARCHAR(200)、summary VARCHAR(500)、contract_id 可空、status SMALLINT 默认 0、sent_date DATE 可空、confirmed_date DATE 可空、审计字段），验证启动迁移成功
- [x] 1.2 创建 V10__confirmation_menu_seed.sql：函证管理菜单(id=130, perm=business:confirmation:list) + 按钮 131–134（add/edit/delete/status），admin/manager 全量、employee 仅 list，setval 推进序列，验证角色权限正确

## 2. 后端实现

- [x] 2.1 实现 Confirmation 实体/Mapper、ConfirmationRequest、状态枚举（NOT_SENT/SENT/CONFIRMED/VOIDED）与编号生成（HZ 前缀），验证编译通过
- [x] 2.2 实现 ConfirmationService：登记、分页筛选（含 overdue 动态计算）、编辑（不改编号状态）、删除（仅未发出）、流转（send/confirm/void 动作 + 日期校验 + 状态机校验），验证业务规则正确
- [x] 2.3 实现 ConfirmationController：GET/POST /api/confirmations、PUT/DELETE /api/confirmations/{id}、PUT /api/confirmations/{id}/status，全部加 @PreAuthorize 与 @AuditLog，验证统一响应
- [x] 2.4 单元测试：状态机流转与编号生成，验证 mvn test 通过

## 3. 前端实现

- [x] 3.1 新增 src/api/confirmation.ts 与类型定义
- [x] 3.2 新增 views/business/confirmation/index.vue：列表（逾期行高亮）+ 登记/编辑弹窗 + 流转操作（发出/回函/作废，带日期输入）
- [x] 3.3 注册 /business/confirmation 路由，验证有权限角色可见菜单

## 4. 集成验证

- [x] 4.1 端到端：登记函证 → 发出（记录日期）→ 回函（记录日期）→ 状态与日期正确；审计日志有记录
- [x] 4.2 业务规则：未发出直接回函被拒、已回函再流转被拒、已发出删除被拒、编辑不改编号状态
- [x] 4.3 逾期验证：构造发出日期超过 30 天的函证，列表返回 overdue=true；employee 无流转权限调用返回 403
