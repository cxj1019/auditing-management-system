## Why

函证模块需完善实务流程：编号改为人工填写、增加函证方式与快递单号跟踪、原始函证与回函扫描件上传至 Supabase Storage、回函相符性判断与不符原因记录。

## What Changes

- **BREAKING** 函证编号改为人工填写（不再自动生成 HZ 前缀）
- 新增字段：函证方式（邮寄/电子/现场）、发出快递单号、回函快递单号、是否回函、回函是否相符、不符原因
- 新增附件表 `confirmation_attachment`，支持原始函证扫描件与回函扫描件分类上传（Supabase Storage）
- 回函状态流转时自动设置「是否回函」标记

## Capabilities

### Modified Capabilities
- `confirmation-management`: 编号人工填写、增加函证方式/快递单号/回函相符性/扫描件附件

## Impact

- 数据库：V19 迁移（ALTER TABLE 加 6 列 + 建 confirmation_attachment 表）
- 后端：Confirmation 实体/DTO/Service 调整；新增 ConfirmationAttachmentService/Controller
- 前端：函证表单加新字段；附件管理弹窗（原始+回函分区）
