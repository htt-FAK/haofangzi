# PLAN 001 · 用户认证与画像（HOW）

**对应 spec**: v1.0 | **改动面**: `controller/AuthController`、`controller/AdminUserController`、`service/AuthService`、
`service/UserService`、`domain/entity/SysUser`、`config/SecurityConfig`、前端 `views/LoginView.vue`、`views/ProfileWizardView.vue`、`stores/user.ts`

## 1. 契约
见 `specs/000-program/contracts/openapi.yaml` tag `auth` / `admin-user`：
`POST /api/auth/register|login|code|refresh|logout`、`GET/PUT /api/users/me`、`PUT /api/users/me/profile`、
`POST /api/users/me/password`、`DELETE /api/users/me`、`GET /api/admin/users`、`PATCH /api/admin/users/{id}/status`。

## 2. 关键实现决策

| 议题 | 决策 |
| --- | --- |
| 会话 | 无状态 JWT（HS256，`sub=userId`，`role`，`jti`），密钥走环境变量；Redis 黑名单不引入，用 `iat` + 短 TTL 替代 |
| 口令 | `BCryptPasswordEncoder(10)` |
| 图形码 | 后端生成算术题图片（`hutool-captcha` 或自实现 BufferedImage，20 行内），答案存 Caffeine 5 分钟 |
| 短信码 | 演示：`SmsClient` 接口 + `MockSmsClient`（固定 `123456`，日志打印），便于替换真实通道 |
| 失败锁定 | Caffeine `loginFail:phone` 计数 → 计数 ≥5 写 `lock:phone` TTL 900s |
| 画像完整度 | 5 个字段各 20%，`UserService#completeness(user)` 计算，不入库（避免脏数据） |
| 注销 | 事务内：`deleted=1`、`phone=sha256(phone+"#"+id)`、`nickname="用户"+id%1000`、角色置 `NULL_ROLE` |
| 审计 | AOP `@Audit(action="login")` 切面写 `sys_audit_log`，异步 `@Async` |

## 3. 数据映射
`sys_user` ↔ `SysUser`（MyBatis-Plus `@TableLogic` 处理 `deleted`）；
`family_structure`、`prefer_tags` 用 `TypeHandler` 做 JSON ⇄ `List<String>`；出参统一 `UserVO`（不含 password、phone 脱敏）。

## 4. 安全与前端
路由守卫（`meta.requiresAuth`）+ 401 拦截器统一跳登录；Pinia `user` store 持久化 `token/profileCompleteness`；
管理端页面额外 `meta.roles=['ADMIN']`。

## 5. 测试策略
单测：口令散列校验、锁定计数、完整度计算、JSON TypeHandler；
集成（`@SpringBootTest` + MockMvc）：注册→登录→改画像→停用→重登录失败 全链路；
契约：`POST /api/auth/login` 响应结构与 openapi 一致性由 `openapi-typescript` 生成前端类型保证。
