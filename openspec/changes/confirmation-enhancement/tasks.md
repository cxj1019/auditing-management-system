## 1. 数据库迁移

- [x] 1.1 V19：ALTER TABLE confirmation ADD 6 列 + 建 confirmation_attachment 表

## 2. 后端

- [x] 2.1 Confirmation 实体加 6 字段；ConfirmationRequest 加 confirmationNo(@NotBlank) + 新字段
- [x] 2.2 ConfirmationServiceImpl：createConfirmation 用 request 中的 confirmationNo（移除自动生成）；编号唯一校验；回函不相符必填不符原因
- [x] 2.3 ConfirmationAttachmentService + Controller（复用 SupabaseStorageService，attachment_type 分类）
- [x] 2.4 mvn test 39/39 通过

## 3. 前端

- [x] 3.1 类型与 API 同步（ConfirmationAttachmentItem + 附件 CRUD API）
- [x] 3.2 函证表单加新字段（编号文本输入、方式下拉、快递单号、回函相符性）
- [x] 3.3 附件弹窗（原始+回函分区上传/下载/删除）
- [x] 3.4 类型检查通过

## 4. 验证

- [x] 4.1 人工编号登记成功；编号重复被拒(500)
- [x] 4.2 原始函证扫描件上传成功；附件按 attachmentType 分类
- [x] 4.3 回函不相符+不符原因字段保存正确

## 5. 物流截图（Playwright）

- [x] 5.1 添加 Playwright Java 依赖（com.microsoft.playwright:playwright:1.44.0）
- [x] 5.2 创建 LogisticsScreenshotService：打开快递100 查询页面 → 截图物流状态 → 返回 PNG
- [x] 5.3 后端 API：POST /{id}/track-logistics?action=send|reply（根据快递单号自动截图并上传 Supabase）
- [x] 5.4 前端附件弹窗展示物流截图（可预览/下载）
- [x] 5.5 E2E 验证：SF1234567890 物流截图成功上传（158KB PNG）
