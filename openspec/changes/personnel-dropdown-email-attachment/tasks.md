## 1. 数据库迁移

- [x] 1.1 创建 V16__email_attachment.sql：sys_user.email 唯一约束、admin 补邮箱 admin@firm.cn、建 contract_attachment 表（contract_id 索引），验证启动迁移成功

## 2. 后端实现

- [x] 2.1 邮箱登录：UserDetailsServiceImpl 按 username OR email 查询；SysUserService 创建/编辑时 email 必填+格式+唯一校验；新增 GET /api/users/options（启用用户 id/nickname/email，任意已认证用户可访问），验证编译通过
- [x] 2.2 合同附件：ContractAttachment 实体/Mapper/Service（UUID 落盘、白名单校验、20MB 限制）/Controller（POST/GET/GET download/DELETE /api/contracts/{id}/attachments），权限 edit/list，@AuditLog 标注上传删除，application.yml 配置 multipart 与上传目录，验证编译通过
- [x] 2.3 mvn test 全部通过

## 3. 前端实现

- [x] 3.1 类型与 API：UserOption、attachment 接口函数（含 blob 下载）
- [x] 3.2 登录页提示语改为「用户名/邮箱」；用户管理页邮箱必填标记
- [x] 3.3 项目页面：项目经理/现场负责人换 el-select 人员下拉；合同页面：保管人换 el-select
- [x] 3.4 合同页面：操作列加「附件」按钮 + 弹窗（el-upload 手动上传、清单、下载、删除），类型检查通过

## 4. 集成验证

- [x] 4.1 邮箱登录：admin 用 admin@firm.cn 登录成功；错误密码拒绝；创建新用户（唯一邮箱）并用其邮箱登录成功
- [x] 4.2 人员下拉：options 接口返回启用用户；项目/合同表单可选到人员
- [x] 4.3 附件：上传 PDF 成功并列出；超限/非法格式被拒；下载内容一致；删除后清单与磁盘清理；employee 上传被拒 403

## 5. 实施期调整

- [x] 5.1 附件存储切换为 Supabase Storage（私有桶 contract-attachments，service_role 环境变量注入，桶缺失自动创建），上传/下载/删除全链路验证通过
- [x] 5.2 修复登录后动态路由未注册导致的菜单 404：登录仅建立会话，用户信息与路由注册统一由守卫完成
