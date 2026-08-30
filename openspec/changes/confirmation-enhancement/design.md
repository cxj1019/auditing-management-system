## Context

函证模块已有基础 CRUD + 状态机 + 逾期标记。本次扩展实务字段与附件能力。Flyway v18，新迁移 V19。

## Decisions

1. 编号人工填写 + DB 唯一约束兜底（已有 uk_confirmation_no）
2. 附件分类（original/reply）用 attachment_type 列区分，复用 SupabaseStorageService
3. 回函流转时 changeStatus(action=confirm) 额外接收 replyTrackingNo/replyMatched/discreason 参数
4. has_reply 由回函流转自动置 true；reply_matched/discrepancy_reason 可后续编辑
5. 菜单/权限不变（已有 business:confirmation:* 权限点）

## Migration Plan

V19 随启动自动执行。
