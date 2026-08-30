## Why

人员字段（项目经理/现场负责人/合同保管人）目前为自由文本，拼写不一致且无法与系统账号关联；登录凭证仅支持账号；合同扫描件仍线下保管。本次一并解决：人员从系统在册账号下拉选择、邮箱可作为登录用户名、合同支持扫描件上传归档。

## What Changes

- 人员下拉选择：新增「在册人员选项」接口（启用状态用户的 ID/姓名），项目经理、现场负责人、合同保管人字段改为下拉选择（存储姓名文本，口径不变）
- 邮箱登录：登录接口同时接受「账号或邮箱」；用户邮箱变为必填且全局唯一；登录页提示语更新
- **BREAKING** 用户管理：新建/编辑用户时邮箱必填且唯一（存量用户需补填）
- 合同附件：新增 `contract_attachment` 表与上传/列表/下载/删除接口；文件存储本地磁盘（可配置目录，UUID 重命名防路径穿越）；限制 20MB 与常见文档/图片格式
- Flyway V16：sys_user.email 唯一约束 + admin 补邮箱种子 + 附件表

## Capabilities

### New Capabilities
- `contract-attachment`: 合同扫描件的上传、查阅、下载与删除能力

### Modified Capabilities
- `user-auth`: 登录认证支持账号或邮箱；用户邮箱必填且唯一
- `project-management`: 项目经理与现场负责人改为从系统在册人员中下拉选择
- `contract-management`: 合同保管人改为从系统在册人员中下拉选择；合同支持关联扫描件（能力详见 contract-attachment）

## Impact

- 后端：UserDetailsServiceImpl 支持 OR 邮箱查询；SysUser 新增 options 接口与邮箱唯一校验；新增 attachment 包；V16 迁移
- 数据库：sys_user.email 唯一约束；新增 contract_attachment 表
- 前端：登录页提示语；项目/合同表单人员字段换 el-select；合同列表新增附件管理弹窗（上传/下载/删除）
- 配置：multipart 上限 20MB；上传目录环境变量 UPLOAD_DIR（默认 ./uploads）