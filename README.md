# 会计师事务所管理系统

前后端分离的会计师事务所一体化管理系统,覆盖「客户 → 项目 → 合同 → 发票 → 收款核销」完整业务链,附带函证跟踪、报销审批、日程工时、成本利润分析等模块。

## 技术栈

| 端 | 技术 |
|---|---|
| 后端 | Spring Boot 3.2.5 · Java 17 · MyBatis-Plus · PostgreSQL(Supabase 兼容) · Flyway · JWT |
| 前端 | Vue 3 · TypeScript · Vite · Element Plus · Pinia · vue-router |
| 规范 | openspec 规范驱动开发(13 个变更提案) |

## 功能总览

- **客户管理**:客户档案、境内/境外、开票信息(抬头/税号/开户行/账号/地址/电话)
- **项目管理**:项目性质→项目类型→业务类型三级字典(84 条业务配置),级联选择
- **合同管理**:按业务类型自动编号(如 迈伊兹审约(2026)第0001 号)、状态流转、扫描件归档
- **发票管理**:登记→开票→作废状态机,自动带出客户开票信息与发票品名/税收编码,支持外币(自动抓取中国银行牌价折算),待开票清单导出给财务
- **收款管理**:收款核销到发票,支持客户预收款(先收款后开票),合同/发票双维度汇总
- **日程管理**:周/日/月三视图,跨天色带、节假日(休/班/节日名)标记,工时规则推算(全天 7h、跨午休扣减、加班每 4h 强制休息 1h)
- **成本分析**:项目利润表、经营概览、项目年份筛选、收入成本及人员工时明细导出,部门数据隔离
- **工作台**:待办卡片(待审批报销/待开发票/逾期应收/逾期函证/到期合同)、开票回款总览、今日日程、项目 Top5
- **提醒中心**:每日定时扫描逾期风险生成站内通知
- **系统管理**:RBAC 用户/角色/菜单/部门、业务类型字典维护、审计日志查询

## 快速开始

### 后端(端口 8080)

```bash
cd backend
# 配置环境变量(或使用默认本地 PostgreSQL)
#   DB_HOST / DB_PORT / DB_NAME / DB_USER / DB_PASSWORD
#   JWT_SECRET(生产必须设置)
#   SUPABASE_URL / SUPABASE_SERVICE_KEY / SUPABASE_BUCKET
mvn spring-boot:run
```

数据库结构由 Flyway 自动迁移(`src/main/resources/db/migration`,V1–V45),首次启动自动建表并写入种子数据(默认管理员 `admin / admin123`)。

### 前端(端口 5173)

```bash
cd frontend
npm install
npm run dev
```

访问 http://localhost:5173 ,默认账号 `admin / admin123`。

## 目录结构

```
backend/    Spring Boot 后端(按业务模块分包:client/project/contract/invoice/collection/
            confirmation/reimbursement/schedule/cost/dashboard/notify/system/auth/common)
frontend/   Vue 3 前端(views 按业务页面组织,api 按模块封装,stores 状态管理)
openspec/   规范驱动开发的变更提案(proposal/design/tasks/spec)
```

## 部署说明

- 前端:Vercel(构建命令 `npm run build`,输出目录 `dist`)
- 后端:建议部署到支持 Java 的平台(Railway/Render/云服务器),数据库使用 Supabase PostgreSQL
- 所有敏感配置(DB_PASSWORD/JWT_SECRET/SUPABASE_SERVICE_KEY)均通过环境变量注入,仓库内不含密钥
