# TASKS 001 · 用户认证与画像

**状态标记**: `[ ]` 待办 · `[~]` 进行中 · `[x]` 完成 · `[P]` 可与其他任务并行
每个任务 1~4 小时，完成定义 = 代码合并 + 测试通过 + 文档落点更新。

## Layer 1：契约与脚手架
- [x] T-001 在 `contracts/openapi.yaml` 冻结 auth/user/admin-user 端点（前后端并行前置）→ FR-01~06
- [x] T-002 [P] 建表 `sys_user`、`sys_audit_log`，写入 `database/schema.sql` 与 MyBatis-Plus 实体 → FR-04
- [x] T-003 [P] `SecurityConfig`：JWT 过滤器 + 角色表达式 + CORS 白名单 → FR-03/06

## Layer 2：后端实现
- [x] T-004 注册（手机号格式、图形码校验、口令强度、唯一性）与散列存储 → FR-01，AC-07
- [x] T-005 登录：口令 / 短信码两种方式 + Mock 短信通道 → FR-02
- [x] T-006 失败锁定计数器（Caffeine）+ `40305` 剩余时间 → FR-02，AC-01
- [x] T-007 `POST /auth/refresh` 静默续期（`iat` 校验，仅一次）→ FR-03，AC-06
- [x] T-008 画像读写：`PUT /users/me/profile` + `completeness` 计算 → FR-04/05，AC-02/03
- [ ] T-009 修改口令、退出登录 → FR-02
- [x] T-010 管理端：分页查询、停用/启用、重置口令 → FR-07，AC-04
- [ ] T-011 注销与匿名化（事务）→ FR-08，AC-05
- [ ] T-012 `@Audit` AOP 切面 + 异步落库（含 IP/UA）→ FR-10

## Layer 3：前端
- [ ] T-013 [P] 登录/注册页（分步校验、口令强度条、倒计时）
- [ ] T-014 [P] 画像向导 4 步 + Pinia store + 本地持久化 → AC-02
- [x] T-015 axios 拦截器：401 跳登录带回跳、错误码 toast 映射 → AC-06
- [x] T-016 管理端用户列表页（Element Plus Table + 操作确认框）

## Layer 4：验证与文档
- [ ] T-017 单测 ≥ 8 例（含锁定、完整度、匿名化）；MockMvc 全链路 1 例 → 宪法第四条 1
- [ ] T-018 更新 `docs/02-需求分析`（DFD-1.1 用户注册子图 + DD 条目）与 `docs/03-概要设计`（用户服务模块）
- [ ] T-019 `docs/05-用户手册` §2 注册登录 + §6 画像设置；`docs/06-测试计划` 补 TC-A-01~06

## 追溯检查
- [ ] T-020 自检：FR-01~10 均被至少一个 T-xxx 覆盖；AC-01~07 均有 TC 编号；完成后在 `tasks.md`(根) 勾选里程碑
