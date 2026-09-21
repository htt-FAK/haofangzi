# 肇庆市"好房子"项目在线选房与户型智能评估系统

> 软件工程课程设计 · 课题十一 | 组内 3-4 人 | 采用 **Spec-Driven Development（规格驱动开发）** 组织架构

本仓库以"规格（spec）先行、代码与文档同源"的方式组织：`constitution.md` 是不可违背的项目宪法，
`specs/NNN-*` 是每个功能域的唯一事实来源（需求 → 设计 → 任务 → 契约），
`docs/` 是把 specs 按课程设计评分表（系统分析 / 概要设计 / 详细设计 / 报告文档）重新编排成的交付物，
`backend/` `frontend/` `database/` 是按 spec 生成的可运行骨架。

## 一、目录结构

```
haofangzi/
├── README.md                     本文件：项目入口 + spec 工作流
├── constitution.md               项目宪法（原则、约束、评审门禁，spec/代码冲突时以此为准）
├── plan.md                       全局技术方案（技术选型、分层、跨域架构、里程碑）
├── tasks.md                      全局任务清单（按人分工、按 spec 追溯、可勾选）
├── specs/                        ── 规格层：唯一事实来源 ──
│   ├── 000-program/              0 号全局规格：非功能需求、术语表、验收总则
│   │   └── glossary.md
│   ├── 001-user-auth/            用户注册登录、画像与偏好
│   ├── 002-layout-display/       户型 2D/3D 与全景展示
│   ├── 003-house-selection/      房源筛选、收藏、模拟选房与锁房
│   ├── 004-rule-scoring/         规则打分引擎（采光/通风/动线…可解释）
│   ├── 005-compare-report/       多户型对比报告（导出/分享）
│   ├── 006-appointment/          预约看房与后台管理、通知提醒
│   └── 007-ai-advisor/           AI 创新：规则生成、报告生成、智能选房顾问
│       ├── spec.md               需求规格（用户故事 + 验收标准，不含实现细节）
│       ├── plan.md               该域技术方案（模块、算法、改动面）
│       ├── tasks.md              该域可执行任务（T-001 形式，带归属层级）
│       └── contracts/            接口契约（API 片段 / JSON Schema，与代码同 PR 演进）
├── docs/                         ── 交付层：课程设计报告（按评分表组织）──
│   ├── 01-可行性分析/可行性分析报告.md
│   ├── 02-需求分析/需求分析说明书.md        系统流程图 + 分层 DFD(0/1 层) + 数据字典
│   ├── 03-概要设计/概要设计说明书.md        软件结构图(上层/中层/下层) + 数据结构 + 数据库设计
│   ├── 04-详细设计/详细设计说明书.md        模块过程设计（流程图/伪代码/判定表）+ 界面设计
│   ├── 05-用户手册/用户手册.md
│   ├── 06-测试计划/测试计划与用例.md
│   └── diagrams/                 Mermaid 图源（用例图/类图/时序图/ER 图/状态图）
├── database/                     schema.sql（物理设计）、seed-data.sql、ER 说明
├── backend/                      Spring Boot 3 + MyBatis-Plus 骨架（评分引擎已实现）
└── frontend/                     Vue 3 + Vite + TS + Element Plus 骨架
```

## 二、Spec 工作流（团队协作约定）

| 阶段 | 命令式动作 | 产出 | 门禁（DoD） |
| --- | --- | --- | --- |
| ① constitution | 组内评审项目原则 | `constitution.md` | 版本号 + 生效日期，组长签署 |
| ② specify | 写/改 `specs/NNN/spec.md` | 用户故事、FR-xxx、验收标准 | **禁止**出现表名、框架名、HTTP 状态码以外的实现细节 |
| ③ plan | 写 `specs/NNN/plan.md` + 更新根 `plan.md` | 技术选型映射、模块拆分 | 覆盖 spec 全部 FR；不违背宪法 |
| ④ tasks | 拆 `specs/NNN/tasks.md` | 1~4 小时粒度任务，含 [P] 并行标记 | 每任务可追溯到 FR 编号 |
| ⑤ implement | 按 tasks 编码，先 `contracts/` 后代码 | `backend/` `frontend/` | `mvn test` + `pnpm build` 通过；契约测试通过 |
| ⑥ converge | 回写 `docs/` 对应章节 | 课程设计报告章节 | 图表与 specs 一致，标注 spec 版本号 |

**追溯链**：`FR/US 编号 → tasks 编号 → 代码包&类名 → 测试用例编号 → 报告章节`。
任何不一致以 `constitution.md` → `spec.md` → `plan.md` → 代码 的顺序仲裁。

## 三、功能全景（对应课题十一五大模块 + 四个创新点）

| 编号 | 功能域 | 对应题目要求 | 创新点 |
| --- | --- | --- | --- |
| 001 | 用户与画像 | 角色基础 | I3 智能选房顾问的输入源 |
| 002 | 户型展示 | 模块 1 户型 2D/3D 展示、全景 | I4 AI 生成 3D 初始化代码 |
| 003 | 在线选房 | 模块 2 筛选/收藏/楼栋楼层选择 | — |
| 004 | 规则打分 | 模块 3 采光通风动线打分、评分明细 | I1 AI 生成评估规则 + 动态权重 |
| 005 | 对比报告 | 模块 4 多户型对比、导出分享 | I2 AI 生成对比结论与选房建议 |
| 006 | 预约看房 | 模块 5 预约、后台管理、通知 | — |
| 007 | AI 服务 | 创新点 I1-I4 统一网关 | 可解释性输出 |

## 四、本地运行骨架

```bash
# 1. 数据库（MySQL 8）
mysql -uroot -p < database/schema.sql && mysql -uroot -p haofangzi < database/seed-data.sql

# 2. 后端（JDK 17 + Maven 3.9）
cd backend && mvn spring-boot:run            # http://localhost:8080/api   Swagger: /doc.html

# 3. 前端（Node 20 + pnpm）
cd frontend && pnpm i && pnpm dev            # http://localhost:5173
```

演示账号：`13800000001 / Test@123`（购房者）、`admin / Test@123`（顾问/管理员）。
无外网环境下 AI 能力自动降级为 `MockLlmClient`（规则模板生成），保证答辩可演示。

## 五、当前实现进度（框架已就位，按 tasks.md 推进）

| 域 | 已完成（可直接演示） | 待办（对应任务号） |
| --- | --- | --- |
| 基础设施 | 分层与包结构、`ApiResponse`/错误码/异常出口、脱敏工具、JWT 过滤器、`docker-compose` 一键起、`schema.sql`+`seed-data.sql`（3 楼盘/20 户型/600 房源/规则 v1.0） | Testcontainers 集成环境 T-003 |
| 001 用户 | JWT 签发与解析、角色权限骨架 | `AuthController`（注册/登录/锁定/refresh）、画像向导页 T-004~T-009 |
| 002 展示 | `CatalogController` 列表与详情（几何载荷）、`FloorPlan2D.vue`（Canvas 参数化绘制 + 标注 + 命中联动）、`House3DViewer.vue`（GLB 优先 + 参数化盒体回落 + dispose）、`geometry.ts` 工具集 | 全景查看器、楼栋剖面组件、素材上传与签名 T-028~T-034 |
| 003 选房 | `SelectionService` 锁房三重并发保护、续期/取消/转意向、超时回收任务、备选房源；`SelectionConfirmView`（倒计时 + 冲突备选） | 楼层矩阵视图、销控 Excel 导入 T-042~T-058 |
| 004 评估 | **引擎完整可用**：`ScoringEngine`+`ScoreAssembler`+`TierMatcher`+7 维规则种子（`default-rules.json`，权重和=1）+7 个指标计算器 +人群模板 + 结果缓存 + `evaluation` 快照；前端 `EvaluateView`（总分/雷达/维度卡/明细证据/改进建议）；`ScoreAssemblerTest` 覆盖边界与缺数据归一 | 其余 14 个指标 T-069~T-074；规则管理后台的 DRAFT/发布/试算全链 T-079；DB 规则真源切换 T-066 |
| 005 对比 | `CompareView`（客户端矩阵、best/worst 与差值、AI 结论 + 模板回落、分享链接入口） | 服务端矩阵冻结与 `compare_report` 落库、PDF 导出、分享只读页 T-089~T-102 |
| 006 预约 | `Appointment` 实体 + 状态机 `AppointmentStatus`（含拒绝矩阵）、容量查询与占用校验、创建/变更接口、`AppointmentService#remind` 幂等提醒、`AppointmentsView`（列表 + 新建向导 + 时段余量） | 明日清单导出、看板、时段容量维护页 T-113~T-118 |
| 007 AI | `OpenAiCompatibleClient`（超时 8s + 重试 1 次 + JSON mode + 思维链清洗）、`MockLlmClient`、`AiGateway` 缓存与调用日志、`AiController`（规则草案 / 对比结论 / 顾问三端点）、`JsonSchemaGuard` 引用白名单 | 提示词模板外置文件化 T-122；草案 diff 与人工发布门禁 UI（004-T-079）；3D 场景代码生成端点 T-126 |
| 文档 | 六份课程设计文档（可行性 / 需求分析含 DFD+数据字典 / 概要 / 详细 / 用户手册 / 测试）、7 域 spec+plan+tasks、契约（openapi + 2 个 JSON Schema + 规则种子）、Mermaid 图源 | 学校模板合稿与截图 T-127 |

**已知偏差（诚实记录，答辩需说明）**
1. 规则以 `default-rules.json` 为种子而非 DB 真源（T-066 待切换），因此"发布即生效"当前表现为缓存失效路径；
2. 评估域实现了 7/21 指标（每维至少 1 条，缺失维度按有效指标归一，不影响 100 分制），其余 14 条按 T-069~T-074 补；
3. 对比报告在演示环境可由前端矩阵 + 后端结论生成，服务端快照/PDF 属未完成任务；
4. 前端登录页仍为骨架（001 后端未落地时可用 `AuthLookup#issue` 手工签 token 演示）。

