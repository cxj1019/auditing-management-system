# 部署指南

## 架构说明

Vercel 仅承载**前端**静态站点。后端(Spring Boot,含 Playwright 依赖)无法运行在 Vercel 上,需部署到支持 Java 长运行进程的平台:

| 组件 | 平台 | 说明 |
|---|---|---|
| 前端 | **Vercel** | 本仓库 `frontend/` 目录 |
| 后端 | Railway / Render / 云服务器 | `backend/` 目录,`mvn spring-boot:run` 或打包 jar |
| 数据库 | Supabase PostgreSQL | 已在用,连接串走环境变量 |

## 一、前端部署到 Vercel

1. Vercel 控制台 → Add New Project → 导入 `cxj1019/auditing-management-system`
2. 配置:
   - **Root Directory**: `frontend`
   - **Framework Preset**: Vite(自动识别)
   - **Build Command**: `npm run build`
   - **Output Directory**: `dist`
3. 环境变量(Production + Preview):

| 变量 | 值 | 说明 |
|---|---|---|
| `VITE_API_BASE_URL` | `https://<你的后端域名>/api` | 后端地址;不设置则走同域 `/api` |

4. Deploy。`vercel.json` 已配置 SPA 路由回退(刷新不 404),并放行 `/api/` 前缀不回退。

## 二、后端部署(Railway 为例)

1. Railway → New Project → Deploy from GitHub → 选择本仓库
2. **Root Directory**: `backend`
3. 构建命令:`mvn -q -DskipTests package`;启动命令:`java -jar target/accounting-firm-0.0.1-SNAPSHOT.jar --spring.profiles.active=prod`
4. 环境变量:

| 变量 | 说明 |
|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | Supabase Session Pooler 连接参数 |
| `DB_SSLMODE` | `require` |
| `JWT_SECRET` | 生产必须设置随机长字符串 |
| `SUPABASE_URL` / `SUPABASE_SERVICE_KEY` / `SUPABASE_BUCKET` | 附件存储 |
| `SPRING_PROFILES_ACTIVE` | `prod` |

5. 首次启动 Flyway 自动迁移建表(V1–V45),默认管理员 `admin / admin123`(登录后立即改密码)。

## 三、收尾检查

- [ ] 前端 `VITE_API_BASE_URL` 指向后端域名(或配置反向代理同域转发 `/api`)
- [ ] 后端 CORS 如需收紧,修改 `CorsConfig.java` 为具体前端域名
- [ ] 默认 admin 密码已修改
- [ ] Supabase Storage 桶 `contract-attachments` 可访问

## 备注

- Playwright(函证物流截图)需要浏览器内核,Serverless 平台(Railway/Render 容器)需安装 Chromium;若不需要物流截图功能,可忽略该报错,其余功能不受影响
- 数据库迁移自动执行,多实例部署时 Flyway 自带锁,无需人工干预
