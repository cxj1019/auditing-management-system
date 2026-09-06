# 系统迁移教程:从 Render + Supabase 迁移到自托管 VPS

> 适用对象:会计师事务所管理系统(前后端分离:Spring Boot 3.2.5 / Java 17 + PostgreSQL + Vue 3)
> 本教程假设当前部署形态与线上一致:后端 Render(Docker)、数据库 Supabase(PostgreSQL)、文件存储 Supabase Storage、前端 Vercel。

---

## 一、当前架构与迁移目标

### 1.1 当前架构

| 组件 | 现状 | 说明 |
|---|---|---|
| 后端 | Render(Docker,端口由 PORT 注入) | Spring Boot 3.2.5,Java 17,MyBatis-Plus,Flyway |
| 数据库 | Supabase PostgreSQL(Session Pooler,限 15 连接) | 表结构由 Flyway 管理(当前 V1~V59) |
| 文件存储 | Supabase Storage(bucket:`contract-attachments`) | 合同/发票/报销/函证附件 |
| 前端 | Vercel(VITE_API_BASE_URL 指向 Render) | Vue 3 + Vite,history 路由 |
| 定时任务 | 后端内置(@Scheduled 每天 08:00 提醒扫描) | 随后端运行 |

### 1.2 迁移目标架构(单台 VPS)

```
互联网 ──► Nginx(443/HTTPS, 前端静态文件, /api 反代) ──► Spring Boot(127.0.0.1:8080)
                                                        └─► PostgreSQL(127.0.0.1:5432)
                                                        └─► 本地磁盘或 MinIO(附件)
```

全部组件可跑在一台 VPS 上(推荐 2 核 4G 起,系统盘 40G+,Debian 12 / Ubuntu 22.04+)。

### 1.3 迁移总步骤

1. VPS 环境准备(安装 JDK/PG/Nginx)
2. 数据库迁移(Supabase → 自托管 PG)
3. 附件迁移(Supabase Storage → 本地/MinIO)
4. 后端部署(jar + systemd)
5. 前端构建 + Nginx 托管
6. 域名切换与验证
7. 回收旧平台资源

> **推荐做法:并行运行 1~2 周**(新旧同时可访问,新数据只在 VPS 录入),确认无误后再切域名、停旧服务。

---

## 二、VPS 环境准备

### 2.1 基础配置

```bash
# 以 root 或 sudo 用户执行;假设系统为 Debian 12 / Ubuntu 22.04
apt update && apt upgrade -y
apt install -y curl wget git unzip gnupg

# 时区(提醒扫描按本地时间 08:00 执行)
timedatectl set-timezone Asia/Shanghai

# 防火墙:只放行 SSH/HTTP/HTTPS
apt install -y ufw
ufw allow 22/tcp && ufw allow 80/tcp && ufw allow 443/tcp
ufw enable
```

### 2.2 安装 PostgreSQL 16

```bash
apt install -y postgresql-16
systemctl enable --now postgresql

# 创建数据库与账号(记下密码,后端要用)
sudo -u postgres psql <<'SQL'
CREATE USER firm WITH PASSWORD '改成强密码';
CREATE DATABASE firm OWNER firm;
SQL
```

### 2.3 安装 JDK 17

```bash
apt install -y openjdk-17-jre-headless
java -version   # 确认 17.x
```

### 2.4 安装 Nginx 与 HTTPS 证书

```bash
apt install -y nginx certbot python3-certbot-nginx
```

### 2.5 域名解析

将域名(如 `mytscpa.19851019.xyz`)的 A 记录指向 VPS 公网 IP。
**注意:此刻先不要改线上正在使用的解析**,可以先用一个临时子域(如 `vps.19851019.xyz`)验证,验证通过后再把正式域名切过来。

---

## 三、数据库迁移(Supabase → 自托管 PG)

### 3.1 从 Supabase 导出

在任一装了 `postgresql-client` 的机器上执行(也可在 VPS 上执行)。Supabase 控制台 → Project Settings → Database 可以查到连接串;用 **Session Pooler** 地址(端口 5432)或直连地址(5432,需白名单)。

```bash
# 只导业务 schema(public),Supabase 的 auth/storage 等系统 schema 不要导
pg_dump "host=aws-0-ap-southeast-1.pooler.supabase.com port=5432 \
         user=postgres.lzdhsdlnrkwapspaogwm dbname=postgres sslmode=require" \
        --schema=public --no-owner --no-privileges \
        -F c -f firm_backup.dump
```

> 提示:`-F c` 自定义格式便于选择性恢复。导出前建议在系统里停止录入几分钟,保证一致性。

### 3.2 导入 VPS PostgreSQL

```bash
# 把 firm_backup.dump 上传到 VPS 后:
pg_restore -h 127.0.0.1 -U firm -d firm --no-owner --no-privileges firm_backup.dump
# 如有 Supabase 残留的扩展依赖报错(如 pg_cron),可加 --exit-on-error 去掉重试或忽略非致命错误
```

### 3.3 验证

```bash
psql -h 127.0.0.1 -U firm -d firm -c "
SELECT (SELECT count(*) FROM sys_user)  AS users,
       (SELECT count(*) FROM client)    AS clients,
       (SELECT count(*) FROM project)   AS projects,
       (SELECT count(*) FROM contract)  AS contracts,
       (SELECT count(*) FROM invoice)   AS invoices,
       (SELECT count(*) FROM reimbursement) AS bills;"
# 与 Supabase 上相同查询的数字对比一致即可

# Flyway 历史:后端启动时会自动校验并继续,无需手工处理
```

> **注意**:自托管 PG 没有 15 连接上限,后端连接池可以调大(见 5.2 `DB_POOL_SIZE`,建议 10)。

---

## 四、附件存储迁移(Supabase Storage → 本地磁盘)

### 4.1 现状说明

代码通过 `SupabaseStorageService` 以 HTTP API + Service Key 读写 Supabase Storage(bucket `contract-attachments`)。附件对象路径形如:
`contracts/{合同id}/xxx.pdf`、`reimbursements/{单id}/xxx.pdf`、`confirmations/{id}/...`、`invoices/...`。

### 4.2 迁移方案二选一

**方案 A(推荐,改动最小):自建 MinIO,S3 兼容**
1. VPS 安装 MinIO(docker 单容器即可),创建 bucket `contract-attachments`。
2. 用迁移脚本把 Supabase Storage 全量对象搬到 MinIO(见 4.3)。
3. 改造 `SupabaseStorageService` 为按配置选择端点(Supabase Storage API 与 S3 API 不同,需把 upload/download/delete 换成 AWS SDK for S3,或使用 MinIO 的 S3 兼容签名)。工作量约半天。

**方案 B(最简单):本地磁盘存储**
1. 新增 `LocalStorageService`,实现与 `SupabaseStorageService` 相同的接口方法(upload/download/delete/preview-url),文件写到 `/data/firm/attachments/`,预览 URL 走 Nginx 静态路径(加鉴权头或使用临时签名可后置简化)。
2. `application-prod.yml` 增加 `storage.type: local`(通过 `@ConditionalOnProperty` 切换两个 Bean)。
3. 历史附件用脚本从 Supabase 下载后按原对象路径落盘。
4. 参考代码骨架:

```java
@Service
@ConditionalOnProperty(name = "storage.type", havingValue = "local")
public class LocalStorageService implements StorageService {
    @Value("${storage.local-dir:/data/firm/attachments}")
    private String baseDir;
    // upload: Files.write(Path.of(baseDir, objectPath), bytes)
    // download: Files.readAllBytes(...)
    // delete: Files.deleteIfExists(...)
}
```

### 4.3 附件搬运脚本(示例,Python)

```python
# migrate_storage.py —— 从 Supabase Storage 全量下载再上传到目标端
import os, requests

SUPA_URL = 'https://lzdhsdlnrkwapspaogwm.supabase.co'
SERVICE_KEY = '你的 service key'
BUCKET = 'contract-attachments'

headers = {'Authorization': f'Bearer {SERVICE_KEY}'}
# 1. 列出全部对象(分页)
objects = []
offset = 0
while True:
    r = requests.post(f'{SUPA_URL}/storage/v1/object/list/{BUCKET}',
                      json={'prefix': '', 'limit': 1000, 'offset': offset, 'sortBy': {'column': 'name'}},
                      headers=headers)
    batch = r.json()
    if not batch: break
    objects += [o for o in batch if not o.get('id') is None]
    offset += 1000
# 2. 逐个下载 → 落到 ./storage_backup/<path>
for o in objects:
    path = o['name']
    data = requests.get(f'{SUPA_URL}/storage/v1/object/authenticated/{BUCKET}/{path}', headers=headers).content
    os.makedirs(os.path.dirname(f'storage_backup/{path}'), exist_ok=True)
    open(f'storage_backup/{path}', 'wb').write(data)
    print('saved', path)
# 3. 将 storage_backup 目录按相同路径结构上传/拷贝到 MinIO 或 /data/firm/attachments/
```

> 迁移前后用对象数量与总字节数核对一遍。Supabase 控制台 Storage 页也能看到总量。

---

## 五、后端部署

### 5.1 打包

本地或 CI 上执行:

```bash
cd backend
mvn clean package -DskipTests
# 产物: target/accounting-firm-*.jar
scp target/accounting-firm-*.jar root@vps:/opt/firm/app.jar
```

### 5.2 环境变量(systemd)

`/etc/systemd/system/firm.service`:

```ini
[Unit]
Description=Accounting Firm Backend
After=network.target postgresql.service

[Service]
User=firm
WorkingDirectory=/opt/firm
ExecStart=/usr/bin/java -Xms512m -Xmx1024m -jar /opt/firm/app.jar
Environment=SPRING_PROFILES_ACTIVE=prod
Environment=DB_HOST=127.0.0.1
Environment=DB_PORT=5432
Environment=DB_NAME=firm
Environment=DB_USER=firm
Environment=DB_PASSWORD=数据库密码
# 自托管无 15 连接限制,可以调大
Environment=DB_POOL_SIZE=10
# 重要:沿用线上同一 JWT_SECRET,否则所有用户 token 失效需重新登录
Environment=JWT_SECRET=与 deploy-env.txt 中一致的值
Environment=SERVER_PORT=8080
# 附件本地存储(若采用方案 B)
Environment=STORAGE_TYPE=local
Environment=STORAGE_LOCAL_DIR=/data/firm/attachments
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

```bash
useradd -r -s /usr/sbin/nologin firm
mkdir -p /opt/firm /data/firm/attachments && chown -R firm:firm /opt/firm /data/firm
systemctl daemon-reload && systemctl enable --now firm
journalctl -u firm -f    # 看启动日志,Flyway 会自动校验并执行增量迁移
```

> **Flyway 说明**:自托管库已通过 3.2 导入了 flyway_schema_history,启动时只执行缺失的增量迁移,是安全的。首次启动务必看日志确认 `Successfully validated N migrations`。

---

## 六、前端构建与 Nginx

### 6.1 构建前端

```bash
cd frontend
npm ci
# 自托管后 API 与页面同域,直接用 /api 相对路径
VITE_API_BASE_URL=/api npm run build
# 产物: dist/
scp -r dist/* root@vps:/opt/firm/web/
```

### 6.2 Nginx 站点配置

`/etc/nginx/sites-available/firm`:

```nginx
server {
    listen 80;
    server_name mytscpa.19851019.xyz;

    # 附件上传上限(发票/合同扫描件最大 20MB,留余量)
    client_max_body_size 25m;
    gzip on;
    gzip_types text/css application/javascript application/json image/svg+xml;

    # 前端静态文件
    root /opt/firm/web;
    index index.html;

    # history 路由回退:非 /api 的路径全部回 index.html
    location / {
        try_files $uri $uri/ /index.html;
    }

    # 后端接口反代
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_read_timeout 120s;
    }

    # 附件本地存储(方案 B 时的只读下载;生产建议加鉴权或使用后端签名 URL)
    # location /attachments/ {
    #     alias /data/firm/attachments/;
    # }
}

# HTTPS(certbot 自动补 443 配置与跳转)
# certbot --nginx -d mytscpa.19851019.xyz
```

```bash
ln -s /etc/nginx/sites-available/firm /etc/nginx/sites-enabled/
nginx -t && systemctl reload nginx
certbot --nginx -d mytscpa.19851019.xyz   # 自动配置 HTTPS + 自动续期
```

> **同域优势**:前端 `VITE_API_BASE_URL=/api`,不再有跨域问题;Vercel/Render 的 CORS 配置也不再需要。

---

## 七、域名切换与验证清单

确认临时域名下系统完全正常后:

1. 把正式域名的 A 记录从 Vercel/Render 切到 VPS IP(TTL 调短到 300s 可加速切换)。
2. 等待解析生效(`ping mytscpa.19851019.xyz` 指向新 IP)。
3. 按**验证清单**逐项过一遍:

| 检查项 | 预期 |
|---|---|
| 登录 | 各角色均可正常登录,菜单与迁移前一致 |
| 客户/项目/合同列表 | 数据完整,条数与旧系统一致 |
| 附件预览/下载 | 合同、发票、报销、函证附件均可打开 |
| 附件上传 | 新上传一个合同扫描件,存储目录出现新文件 |
| 报销全流程 | 提交 → 审批 → 财务付款,通知正常产生 |
| 应收账龄/垫付台账 | 数字与旧系统一致 |
| 导出 | 费用明细/对账单 Excel 可下载 |
| HTTPS | 证书有效,HTTP 自动跳转 HTTPS |
| 定时提醒 | 次日 08:00 检查通知是否生成(或手动触发) |

4. 观察 1~2 周后:停用 Render 服务、Vercel 项目;Supabase 项目可导出最终备份后暂停(建议先保留一份最终 pg_dump 存档)。

---

## 八、回滚方案

切换域名后如发现严重问题:

1. 把域名 A 记录切回原 Vercel/Render 体系(旧系统未动,仍在运行)。
2. 如果切换期间 VPS 已产生新数据:在新系统停录后,把 VPS 增量数据(`pg_dump` 新增表行)合并回 Supabase,或直接以 VPS 为准反向切换。
3. 因此**强烈建议并行运行期只在一套系统录入数据**。

---

## 九、备选方案:Docker Compose 一键部署

如果不想到处装环境,可在 VPS 上用 Docker Compose:

```yaml
# docker-compose.yml
services:
  db:
    image: postgres:16
    environment:
      POSTGRES_DB: firm
      POSTGRES_USER: firm
      POSTGRES_PASSWORD: 数据库密码
    volumes:
      - pgdata:/var/lib/postgresql/data
      - ./firm_backup.dump:/docker-entrypoint-initdb.d/firm_backup.dump
    restart: always
  app:
    image: eclipse-temurin:17-jre
    working_dir: /app
    command: java -jar app.jar
    volumes:
      - ./app.jar:/app/app.jar
      - ./attachments:/data/firm/attachments
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DB_HOST: db
      DB_PORT: 5432
      DB_NAME: firm
      DB_USER: firm
      DB_PASSWORD: 数据库密码
      DB_POOL_SIZE: "10"
      JWT_SECRET: 与线上一致的值
      SERVER_PORT: 8080
    depends_on:
      - db
    restart: always
  nginx:
    image: nginx:alpine
    ports: ["80:80", "443:443"]
    volumes:
      - ./web:/usr/share/nginx/html:ro
      - ./nginx.conf:/etc/nginx/conf.d/default.conf:ro
      - ./certs:/etc/nginx/certs:ro
    depends_on:
      - app
    restart: always
volumes:
  pgdata:
```

> 注意:`db` 服务的初始化 dump 需为纯 SQL 格式(`pg_dump --schema=public --no-owner` 不带 `-F c`),或手动 `pg_restore` 后再起 app。

---

## 十、环境变量对照表(迁移核对用)

| 变量 | Render 现值 | VPS 建议值 |
|---|---|---|
| SPRING_PROFILES_ACTIVE | prod | prod |
| DB_HOST | aws-0-ap-southeast-1.pooler.supabase.com | 127.0.0.1(或 docker 服务名 db) |
| DB_PORT | 5432 | 5432 |
| DB_NAME | postgres | firm |
| DB_USER | postgres.lzdhsdlnrkwapspaogwm | firm |
| DB_PASSWORD | (Supabase 密码) | 新强密码 |
| DB_SSLMODE | require | disable(本机内网)或按需 |
| DB_POOL_SIZE | 3(Supabase 限制) | 10(无限制) |
| JWT_SECRET | deploy-env.txt 中的值 | **保持不变** |
| SUPABASE_URL / SUPABASE_SERVICE_KEY / SUPABASE_BUCKET | Supabase 存储 | 迁移后可移除(改用本地/MinIO 存储) |

有任何一步卡住,先看 `journalctl -u firm -n 200` 与 Nginx error log,绝大多数问题(连接串、Flyway、权限)都能在日志里直接定位。
