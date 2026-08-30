## 1. 项目脚手架搭建

- [x] 1.1 初始化后端 Spring Boot 3 + Java 17 项目（Maven 工程，含 spring-boot-starter-web、spring-boot-starter-security、mybatis-plus-spring-boot3-starter、postgresql、jjwt、flyway-core），验证 `mvn compile` 通过
- [x] 1.2 初始化前端 Vue 3 + Vite + TypeScript 项目（含 Element Plus、Pinia、Vue Router、Axios 依赖），验证 `npm run dev` 正常启动
- [x] 1.3 配置后端按环境加载（application-dev.yml / application-prod.yml），数据库连接、JWT 密钥等敏感项通过环境变量注入，验证不同 profile 加载不同配置

## 2. 数据库初始化与 Flyway 基线

- [x] 2.1 创建 Flyway 基线脚本 V1__init_schema.sql，包含 sys_user、sys_role、sys_menu、sys_user_role、sys_role_menu 五张基础表的结构定义，验证 `mvn flyway:migrate` 执行成功
- [x] 2.2 创建 Flyway 数据初始化脚本 V2__seed_data.sql，插入默认管理员账号、三种角色模板（系统管理员、项目经理、普通员工）及对应菜单权限数据，验证脚本执行后数据库有正确的初始化数据

## 3. 共享基础设施

- [x] 3.1 实现统一响应封装（ApiResult 类，含 code/message/data 字段，静态工厂方法 success/error），验证任意接口返回格式均为统一结构
- [x] 3.2 实现统一业务异常类（BusinessException，含业务码枚举）与全局异常处理器（@RestControllerAdvice），验证未预期异常返回统一错误响应且不包含堆栈信息
- [x] 3.3 实现 MyBatis-Plus 基础配置（分页插件、自动填充处理器），验证分页查询返回正确结果

## 4. 操作审计日志

- [x] 4.1 实现 @AuditLog 注解与 AOP 切面，记录操作人、操作时间、操作内容与结果，验证对登录与数据变更接口添加注解后审计表有对应记录

## 5. 用户认证

- [x] 5.1 实现用户登录接口（POST /api/auth/login），使用 BCrypt 加密密码，登录成功签发 JWT 并返回用户基本信息与菜单权限，验证正确密码登录成功、错误密码返回明确提示
- [x] 5.2 实现 JWT Token 鉴权过滤器（JwtAuthenticationFilter），校验请求头中的 Token 有效性，验证携带有效 Token 可正常访问、缺失/过期 Token 返回 401
- [x] 5.3 实现令牌黑名单机制（登出/改密时记录 jti），验证登出后同一 Token 无法再访问受保护接口

## 6. RBAC 权限模型

- [x] 6.1 实现用户管理 CRUD 接口（GET/POST/PUT /api/users），验证管理员可创建、编辑、查询用户
- [x] 6.2 实现角色管理 CRUD 接口（GET/POST/PUT /api/roles）与角色-菜单权限分配接口，验证管理员可维护角色及权限集合
- [x] 6.3 实现菜单管理 CRUD 接口（GET/POST/PUT /api/menus），支持树形结构，验证管理员可维护菜单与按钮权限点
- [x] 6.4 实现后端权限校验（@PreAuthorize 或自定义注解），验证无权限操作返回 403 错误

## 7. 前端认证与权限集成

- [x] 7.1 实现登录页面，调用登录接口后存储 Token 与用户信息到 Pinia store，验证登录成功后跳转到首页
- [x] 7.2 实现 Axios 拦截器（请求头注入 Token + 401 自动跳转登录页），验证 Token 过期时自动重定向到登录页
- [x] 7.3 实现动态路由与权限指令（根据用户权限过滤可访问菜单与操作按钮），验证不同角色登录后看到不同的菜单与按钮
- [x] 7.4 实现系统整体布局页面（左侧菜单树，顶部导航栏，内容区路由视图），验证用户登录后能看到完整布局

## 8. 模块扩展点与菜单注册

- [x] 8.1 定义后端模块包规范与示例模块模板（contract 占位模块，含 Controller、Service、Mapper 空壳），验证新增模块只需按规范创建包即可被框架扫描
- [x] 8.2 定义前端模块目录规范与菜单注册方式（路由配置文件中追加模块路由），验证新增模块的路由后按权限正常展示菜单
- [x] 8.3 实现系统首页仪表盘（显示当前用户信息、系统名称），验证首页正常加载

## 9. 集成验证

- [x] 9.1 编写完整登录→菜单展示→用户管理→角色权限分配的端到端测试，验证一个完整流程走通
- [x] 9.2 验证审计日志记录正常，关键操作均可追溯
- [x] 9.3 验证不同角色（管理员、项目经理、普通员工）登录后菜单与权限隔离正确
