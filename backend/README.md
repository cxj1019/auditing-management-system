# 会计师事务所管理系统 — 后端

Spring Boot 3 + Java 17 + MyBatis-Plus + PostgreSQL（Supabase）+ Spring Security (JWT) + Flyway

## 环境要求

- JDK 17+
- Maven 3.8+
- PostgreSQL 数据库（Supabase 云服务或本地 PostgreSQL）

## 快速开始

### 方式一：连接 Supabase（推荐起步方案）

1. 在 [supabase.com](https://supabase.com) 创建项目（免费版即可起步）
2. 在项目 Dashboard → Connect 中获取连接参数，选择 **Session Pooler**（IPv4 可直连）
3. 配置环境变量后启动：

| 环境变量 | 说明 | 示例 |
|---------|------|------|
| `DB_HOST` | Session Pooler 地址 | `aws-0-ap-southeast-1.pooler.supabase.com` |
| `DB_PORT` | 端口 | `5432` |
| `DB_NAME` | 数据库名 | `postgres` |
| `DB_USER` | 用户名（注意带项目前缀） | `postgres.abcdefghijklm` |
| `DB_PASSWORD` | 数据库密码（Dashboard 中设置） | —— |
| `DB_SSLMODE` | SSL 模式 | `require` |
| `JWT_SECRET` | JWT 签名密钥（生产必须修改） | —— |

```bash
mvn spring-boot:run
```

Flyway 会在首次启动时自动建表并写入初始化数据。

### 方式二：本地 PostgreSQL

```bash
# 创建数据库后
set DB_HOST=localhost
set DB_PORT=5432
set DB_NAME=postgres
set DB_USER=postgres
set DB_PASSWORD=postgres
set DB_SSLMODE=disable
mvn spring-boot:run
```

默认管理员账号：`admin / admin123`

## 迁移到自托管 Supabase

自托管 Supabase 同样是 PostgreSQL，迁移只需更换连接参数（DB_HOST/DB_PORT/DB_USER/DB_PASSWORD 指向自托管实例），schema 由 Flyway 管理、代码零改动。

## 模块结构

```
com.accounting.firm
├── common        # 共享基础设施（统一响应/异常/安全/审计/配置）
├── auth          # 认证模块（登录/登出/用户信息）
├── system        # 系统管理（用户/角色/菜单 RBAC）
└── contract      # 业务模块模板（占位示例）
```

## 新增业务模块

按 `contract` 模板创建包结构即可，无需修改框架代码：

```
com.accounting.firm.<module>
├── controller    # REST 接口，返回 ApiResult
├── service       # 业务接口 + impl
├── mapper        # 继承 BaseMapper（自动被 @MapperScan 扫描）
└── entity        # 实体
```

同时在前端 `frontend/src/router/modules/` 下注册模块路由、在菜单管理中配置菜单项并分配权限。

## 测试

```bash
mvn test
```
