## Why

事务所日常管理依赖合同、收款、报销、函证、成本等多个分散的 Excel 和线下流程，缺乏统一平台，数据割裂、追溯困难。本变更启动会计师事务所管理系统的建设，先搭建整体架构骨架与多用户权限基础，为后续各业务模块的独立演进提供稳定底座。

## What Changes

- 搭建前后端分离的项目脚手架：前端 Vue3 + Element Plus + TypeScript，后端 Spring Boot (Java)，数据库 PostgreSQL（先使用 Supabase 云服务，后续可迁移自托管 Supabase），代码规范与统一工程结构
- 建立模块化架构骨架：后端按业务模块分包、前端按模块组织路由与页面目录，定义清晰的模块注册与扩展方式，为合同管理、收款、报销、函证、成本分析等模块预留接入点
- 实现用户认证与基于角色的访问控制（RBAC）：账号登录、Token 鉴权、角色权限管理、菜单/按钮级权限控制，满足事务所内网多用户场景
- 建立共享基础设施：统一响应封装、全局异常处理、操作审计日志、配置管理、分页与查询规范
- 业务模块（合同管理、收款情况、报销管理、函证管理、成本分析、钉钉工资数据导入）不在本变更范围内，作为后续独立变更逐个实现

## Capabilities

### New Capabilities
- `system-foundation`: 系统整体骨架与共享基础设施，包括项目脚手架、模块化架构、统一响应/异常/日志/审计/配置规范
- `user-auth`: 用户认证与基于角色的访问控制（RBAC），包括登录鉴权、角色权限模型、菜单与按钮权限控制

### Modified Capabilities
（无——全新项目，尚无既有能力规格）

## Impact

- 新建前端工程（Vue3 + Element Plus + TypeScript + Vite）、后端工程（Spring Boot 3 + MyBatis-Plus + PostgreSQL，数据库先使用 Supabase 云服务、后续可平滑迁移自托管 Supabase）、以及公共开发规范文档
- 新增依赖：前端 Element Plus、Vue Router、Pinia、Axios；后端 Spring Security、JWT、MyBatis-Plus、PostgreSQL 驱动
- 建立 `openspec/specs/` 下首批能力规格，后续业务模块变更将在此基础上增量扩展
- 部署形态：事务所内网多用户使用，初期为单体后端 + 单页前端架构