## 1. 数据库迁移

- [x] 1.1 创建 V13__project_dimension.sql：建 project 表（project_no 唯一、name、type、client_name、owner_name、start_date/end_date、status SMALLINT 默认 0 进行中、remark、审计字段）；TRUNCATE 五张业务表并重置序列；contract 加 project_id BIGINT NOT NULL + 索引；reimbursement/labor_cost/confirmation 的 contract_id RENAME 为 project_id（labor_cost 的 project_id 改 NOT NULL），验证启动迁移成功
- [x] 1.2 创建 V14__project_menu_seed.sql：项目管理菜单(id=150, perm=business:project:list) + 按钮 151–154（add/edit/delete/status），admin/manager 全量、employee 仅 list，setval 推进序列，验证角色权限正确

## 2. 后端实现

- [x] 2.1 实现 project 包：Project 实体/状态枚举（IN_PROGRESS/FINISHED/ARCHIVED，支持重开）/编号生成器/Mapper/Service（CRUD+流转+删除校验无关联合同）/Controller（@PreAuthorize+@AuditLog），验证编译与单测通过
- [x] 2.2 调整 contract 包：实体加 projectId，Request 必填校验，创建时校验项目存在且进行中，列表 VO 带项目编号名称，验证编译通过
- [x] 2.3 调整 reimbursement/cost(labor)/confirmation 包：关联从 contractId 改为 projectId，提交/登记时校验项目存在且未归档，VO 展示项目信息，验证编译通过
- [x] 2.4 重写 cost 聚合：CostAnalysisMapper 按 project 维度聚合（收入经合同分组、已批准报销、人工成本），概览回款率分母改为各项目合同总额之和，验证编译通过
- [x] 2.5 单元测试：项目状态机与编号生成，验证 mvn test 全部通过

## 3. 前端实现

- [x] 3.1 新增 src/api/project.ts 与类型定义（ProjectItem/ProjectRequest）
- [x] 3.2 新增 views/business/project/index.vue：列表+筛选+登记/编辑弹窗+状态流转按钮（完成/重开/归档）
- [x] 3.3 调整合同页面：弹窗加「所属项目」必选下拉（仅进行中项目），列表加项目列
- [x] 3.4 调整报销/函证页面：弹窗关联选择器从合同换为项目（可空）；收款页面列表加项目列
- [x] 3.5 调整成本分析页面：利润表按项目展示（项目编号/名称列），人工成本弹窗选项目
- [x] 3.6 注册 /business/project 路由，类型检查通过

## 4. 集成验证

- [x] 4.1 端到端：admin 登记项目 → 项目下挂合同 → 合同收款 → 报销挂项目并批准 → 登记人工成本 → 成本分析页项目毛利正确 → 归档项目
- [x] 4.2 业务规则：归档项目不可编辑/不可挂新合同/不可登记报销人工函证；有关联合同的项目删除被拒；已完成项目可重开
- [x] 4.3 权限验证：employee 可见项目菜单但无维护按钮，接口返回 403；审计日志记录项目操作
