## Context

三个独立增强：人员下拉（数据源 sys_user）、邮箱登录（sys_user.email 已有列）、合同附件（新表）。当前库中存量用户 admin 无邮箱，需种子补填；测试项目 ABC 的现场负责人为空串，编辑补填即可。

## Goals / Non-Goals

**Goals:** 人员字段与系统账号打通（下拉选择）；登录凭证支持邮箱；扫描件归档闭环（上传/清单/下载/删除）。
**Non-Goals:** 不做人员字段改外键（仍存姓名文本）；不做在线预览 Office 文件；不做附件版本管理；不接对象存储。

## Decisions

**0. 附件存储后端调整为 Supabase Storage（实施期决策）**
原设计的本地磁盘存储改为 Supabase Storage 私有桶 `contract-attachments`：上传/下载/删除走 Storage REST API（service_role 密钥仅服务端环境变量），对象路径 `contracts/{contractId}/{uuid}.{ext}`，桶缺失自动创建。对外接口与行为规格不变，仅存储后端替换；自托管 Supabase 时天然沿用。
替代方案：本地磁盘——多机部署与备份不便，用户明确要求上 Supabase。

**1. 人员字段存姓名文本 + 下拉选人**
保持既有 VARCHAR 口径，下拉数据源为启用用户昵称。避免三处表结构迁移与展示联表；重名风险内网规模可接受。
替代方案：改 user_id 外键——迁移与展示成本高，收益低。

**2. 邮箱登录 = username OR email 单查询**
`loadUserByUsername` 改为 `WHERE username = ? OR email = ?`；V16 为 email 加唯一约束并给 admin 种子邮箱 admin@firm.cn。DTO 层 email 必填 + @Email + 服务层唯一校验。
替代方案：仅允许邮箱登录——老账号无邮箱会被锁死，兼容性差。

**3. 附件本地磁盘存储 + UUID 文件名**
目录 `${UPLOAD_DIR:./uploads}`；入库原始文件名/大小/类型；下载按记录流式返回并带原始文件名。白名单后缀 pdf/jpg/png/doc/docx + 20MB 上限（yml multipart 配置）。路径穿越天然规避（存储名不含用户输入）。
替代方案：数据库 bytea 存文件——备份膨胀；MinIO——引入外部依赖。

**4. 附件权限复用合同权限点**
上传/删除要求 business:contract:edit；清单/下载要求 business:contract:list。不新增权限点。

**5. V16 迁移**
email 唯一约束（PG 允许多个 NULL，存量无冲突）+ admin 邮箱种子 + contract_attachment 表（contract_id 索引）。

## Risks / Trade-offs

- 用户昵称重名 → 下拉显示「姓名 (账号)」辅助区分，存储仍为姓名
- 本地磁盘单点 → 内网单体部署可接受；自托管阶段可换挂载卷
- 删除合同未级联删附件 → 合同删除仅限草稿且草稿一般无附件；后续可在删除逻辑中顺带清理

## Migration Plan

V16 随启动自动执行；回滚即回退代码。

## Open Questions
（无）
