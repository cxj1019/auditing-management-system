## Context

全新项目，无既有代码与历史约束（动机见 proposal.md 的 Why）。系统面向事务所内网多用户、单事务所部署，业务规模为中小型。用户已确定技术栈：Vue3 + Element Plus + Spring Boot + PostgreSQL（数据库先使用 Supabase 云服务，后续迁移至自托管 Supabase，两者均为 PostgreSQL，切换仅需更换连接参数）。本设计只覆盖系统骨架与认证权限，业务模块在后续变更中逐个实现。

## Goals / Non-Goals

**Goals:**
- 搭建可运行的前后端工程骨架与数据库初始化基线
- 建立模块化架构，使合同、收款、报销、函证、成本、钉钉导入等后续模块按统一约定接入
- 落地认证与 RBAC，满足内网多用户、角色权限隔离场景
- 沉淀共享基础设施：统一响应、全局异常、审计日志、环境化配置

**Non-Goals:**
- 不实现任何业务模块的具体功能（合同管理、收款情况、报销管理、函证管理、成本分析、钉钉工资导入）
- 不做微服务化、多租户或云端部署适配
- 不引入工作流引擎（后续模块若有审批需求再单独评估）

## Decisions

**1. 单体后端 + 模块化包结构，而非微服务**
事务所内网规模，单体部署成本低、运维简单。后端按业务模块分包（auth、contract、finance、reimbursement、confirmation、cost 等），模块间通过明确的 Service 接口交互；模块边界即未来可能的拆分边界。
替代方案：微服务（Spring Cloud + K8s）——对当前规模过度设计，引入运维复杂度，拒绝。

**2. Spring Boot 3 + Java 17 + MyBatis-Plus + PostgreSQL（Supabase）**
Spring Boot 3 为当前主流生态；MyBatis-Plus 提供通用 CRUD、分页与代码生成，减少样板代码；PostgreSQL 支持事务与财务数据的一致性要求。
数据库托管选 Supabase（本质为托管 PostgreSQL，平台开源 Apache 2.0）：起步阶段直接使用 Supabase 云服务免运维；后续迁移自托管 Supabase 时仍是 PostgreSQL，应用侧只需更换 JDBC 连接参数（DB_HOST/DB_USER/DB_SSLMODE 等环境变量），schema 与代码零改动。连接优先走 Session Pooler（IPv4 可直连）并启用 sslmode=require。
替代方案：MySQL 8 社区版——GPLv2 开源可用但归属 Oracle，用户明确要求脱离该生态；Spring Data JPA——复杂报表查询与动态 SQL 不如 MyBatis 直观，国内团队维护成本较高。

**3. Spring Security + JWT 无状态认证**
前后端分离场景下 JWT 无状态、易扩展；登录成功后签发 Token，前端通过 Axios 拦截器在请求头注入。登出与改密通过令牌黑名单（记录 jti）实现即时失效。
替代方案：Session + Cookie——需会话存储且与前后端分离的体验相悖。

**4. RBAC 数据模型（5 张基础表）**
`sys_user`、`sys_role`、`sys_menu`（菜单与按钮权限点一体建模）、`sys_user_role`、`sys_role_menu`。前端按当前用户权限动态渲染菜单，后端对按钮级操作做权限校验，权限调整即时生效。
替代方案：在代码中用 `@PreAuthorize` 硬编码权限——权限变更需改代码重新发布，不灵活。

**5. 前端 Vue3 + Vite + TypeScript + Element Plus + Pinia + Vue Router + Axios**
Vite 提供快速的开发体验；Pinia 管理登录态、用户信息与权限集合；Vue Router 按模块组织路由，登录后根据权限过滤可访问路由；Axios 拦截器统一处理 Token 注入、401 跳转与统一响应解包。
替代方案：Vue2 生态——已停止主流维护，不选用。

**6. 统一响应 + 全局异常 + AOP 审计**
统一响应体 `code / message / data`，成功与失败格式一致；`@RestControllerAdvice` 统一兜底异常；自定义 `@AuditLog` 注解 + AOP 切面记录关键操作（登录、数据增删改）。
替代方案：逐接口手工封装——重复代码多且容易遗漏。

**7. Flyway 管理数据库结构**
schema 变更全部通过 Flyway 版本化脚本管理，保证各环境结构一致、变更可追溯。
替代方案：手工 SQL 脚本——多环境容易漂移。

## Risks / Trade-offs

- JWT 无法服务端主动失效 → 引入令牌黑名单（登出/改密记录 jti 并拦截校验），并设置合理有效期
- RBAC 权限配置上手成本 → 提供初始化数据：内置角色模板（系统管理员、项目经理、普通员工）与对应菜单权限
- 审计日志数据量增长 → 仅记录关键操作；分表与归档策略在业务模块阶段再评估
- Supabase 云服务从境内访问延迟与连通性不稳定 → 后端对数据库的访问为集中式连接池（非每个客户端直连），影响可控；若不可接受可提前切换自托管 Supabase 或境内 PostgreSQL 托管，应用零改动
- Supabase 免费版有容量限制且长期不活跃会被暂停 → 业务数据量增长前升级付费版或提前自托管；生产环境启用定期逻辑备份（pg_dump）
- 单实例数据库无高可用 → 起步阶段可接受，以定期备份兜底；自托管阶段再评估主备方案
- 单体应用随模块增多趋于庞大 → 以模块包边界约束耦合，超限时按模块边界拆分

## Migration Plan

全新项目，无存量数据迁移。实施阶段直接建立 Flyway 基线脚本（含 RBAC 基础表与初始化数据）；回滚即回退代码与库脚本，无破坏性变更。

## Open Questions

- 钉钉工资数据导入的对接方式（API 或文件导入）——用户已决定后置，届时单独变更设计
- 各业务模块的实体字段、单据状态与审批流程细节——各模块变更时再定义，不影响本骨架