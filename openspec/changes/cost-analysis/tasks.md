## 1. 数据库迁移

- [x] 1.1 创建 V11__labor_cost_schema.sql：labor_cost 表（id、contract_id、person_name VARCHAR(50)、cost_month VARCHAR(7) 如 2026-08、amount NUMERIC(14,2)、remark、审计字段，(contract_id,person_name,cost_month) 唯一索引），验证启动迁移成功
- [x] 1.2 创建 V12__cost_menu_seed.sql：成本分析菜单(id=140, perm=business:cost:list) + 按钮 141–143（labor-add/labor-edit/labor-delete），admin/manager 全量、employee 仅 list，setval 推进序列，验证角色权限正确

## 2. 后端实现

- [x] 2.1 实现 LaborCost 实体/Mapper、LaborCostRequest、ProjectProfitVO/OverviewVO，验证编译通过
- [x] 2.2 实现 CostAnalysisService：项目利润聚合查询（收入=收款合计、成本=已批准报销+人工成本、毛利与毛利率计算）、经营概览统计、人工成本 CRUD（唯一约束校验、金额>0、合同存在性校验），验证业务规则正确
- [x] 2.3 实现 CostAnalysisController：GET /api/cost/profit、GET /api/cost/overview、GET/POST/PUT/DELETE /api/cost/labor，全部加 @PreAuthorize 与 @AuditLog（人工成本写操作），验证统一响应
- [x] 2.4 单元测试：毛利率计算（正常/零收入 null/超支），验证 mvn test 通过

## 3. 前端实现

- [x] 3.1 新增 src/api/cost.ts 与类型定义
- [x] 3.2 新增 views/business/cost/index.vue：概览统计卡片（总收入/总成本/总毛利/回款率）+ 项目利润表（毛利率进度条）+ 人工成本维护弹窗
- [x] 3.3 注册 /business/cost 路由，验证有权限角色可见菜单

## 4. 集成验证

- [x] 4.1 端到端：登记人工成本 → 项目利润表成本与毛利正确 → 概览统计与明细一致 → 编辑/删除人工成本后数据更新
- [x] 4.2 业务规则：重复登记同合同同人同月被拒、金额 0 被拒、无收款合同毛利率显示为空
- [x] 4.3 权限验证：employee 可查看利润表但人工成本新增返回 403；审计日志记录人工成本操作
