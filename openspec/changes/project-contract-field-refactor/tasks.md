## 1. 数据库迁移

- [x] 1.1 创建 V15__field_refactor.sql：project.owner_name→manager_name、+site_leader_name NOT NULL DEFAULT ''；contract DROP client_name、owner_name→keeper_name，验证启动迁移成功
## 2. 后端实现

- [x] 2.1 Project 实体/DTO/Service：ownerName → managerName + siteLeaderName（均必填），验证编译通过
- [x] 2.2 Contract 实体/DTO/Service：删 clientName、ownerName→keeperName（必填）；创建校验不变；列表客户筛选走 EXISTS 子查询；ContractVO.clientName 从项目填充，验证编译通过
- [x] 2.3 CollectionMapper 收款联表客户改取 pr.client_name，验证编译通过
- [x] 2.4 mvn test 全部通过

## 3. 前端实现

- [x] 3.1 类型定义同步（ProjectItem/Request 双负责人；ContractItem/Request keeperName、去 clientName 输入）
- [x] 3.2 项目页面：项目负责人拆为项目经理 + 现场负责人两个必填输入
- [x] 3.3 合同页面：移除客户名称/负责人输入，新增合同保管人；列表展示客户（只读）与保管人列，类型检查通过

## 4. 集成验证

- [x] 4.1 端到端：登记项目（双负责人）→ 挂合同（填保管人，不填客户）→ 列表客户随项目展示 → 按客户关键字筛选命中
- [x] 4.2 校验：缺保管人被拒、缺现场负责人被拒
