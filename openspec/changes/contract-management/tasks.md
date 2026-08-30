## 1. 数据库迁移

- [x] 1.1 创建 V3__contract_schema.sql：contract 表（id、contract_no 唯一、name、client_name、contract_type、amount NUMERIC(14,2)、sign_date、service_start、service_end、owner_name、status SMALLINT 默认 0 草稿、remark、审计字段），验证应用启动迁移成功
- [x] 1.2 创建 V4__contract_menu_seed.sql：业务管理目录(id=100) + 合同管理菜单(id=101, perm=business:contract:list, component=business/contract/index) + 按钮 102–106（add/edit/delete/status），sys_role_menu 授权 admin 全量 / manager 全量 / employee 仅 list，验证三角色登录菜单与权限正确

## 2. 后端实现

- [x] 2.1 实现 Contract 实体与 ContractMapper（替换占位实体），状态枚举 ContractStatus（DRAFT/RUNNING/FINISHED/TERMINATED）含合法流转表，验证编译通过
- [x] 2.2 实现 ContractService：分页筛选查询（名称/客户模糊、负责人、状态）、创建（编号生成 HT+yyyyMMdd+流水 + 唯一约束重试）、编辑（DTO 不含 status/contractNo）、状态流转（非法流转抛 BusinessException）、删除（仅草稿），验证单元逻辑正确
- [x] 2.3 实现 ContractController：GET/POST/PUT /api/contracts、PUT /api/contracts/{id}/status、DELETE /api/contracts/{id}，全部加 @PreAuthorize 权限点与 @AuditLog 注解，验证接口返回统一结构
- [x] 2.4 编写合同状态机与编号生成的单元测试（合法流转通过、非法流转拒绝、编号格式正确），验证 mvn test 通过

## 3. 前端实现

- [x] 3.1 新增 src/api/contract.ts（分页查询/新增/编辑/流转/删除），类型定义补充 ContractItem/ContractRequest
- [x] 3.2 新增 views/business/contract/index.vue：列表（合同编号/名称/客户/类型/金额/负责人/状态/签约日期）+ 筛选栏 + 新增/编辑弹窗 + 状态流转按钮（按当前状态显示：草稿→开始执行；执行中→完成/终止）+ 删除（仅草稿），按钮用 v-permission 控制
- [x] 3.3 在 router/modules/index.ts 注册 /business/contract 路由（meta.perm=business:contract:list），验证有权限角色可见菜单并可进入页面

## 4. 集成验证

- [x] 4.1 端到端验证：admin 登录 → 创建合同（自动编号格式 HT+日期+流水）→ 编辑 → 开始执行 → 完成，全流程走通且审计日志有记录
- [x] 4.2 状态机验证：草稿直接完成被拒绝、已完成再流转被拒绝、非草稿删除被拒绝
- [x] 4.3 权限验证：employee 登录可见合同菜单但无操作按钮，直接调接口返回 403；manager 可增改删但不可见系统管理菜单
