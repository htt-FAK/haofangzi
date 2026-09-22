# TASKS 007 · AI 能力

## Layer 1 网关与治理
- [x] T-119 `contracts/prompt-templates.md` 定稿 4 个模板（I1/I2/I3/I4）与输出 schema
- [x] T-120 `LlmClient` 接口 + `OpenAiCompatibleClient`（timeout 8s、retry 1、`response_format=json_object`）→ FR-110
- [x] T-121 `MockLlmClient`（确定性模板产出，四能力全覆盖）→ FR-112，AC-66
- [ ] T-122 `PromptTemplateStore`（仓库内 md/yaml 模板 + `@vN`）→ FR-110/119
- [ ] T-123 `PayloadSanitizer`（白名单 + PII 正则兜底 + 候选裁剪到 30）→ FR-122
- [ ] T-124 `JsonSchemaGuard`（schema 校验 + `metricCode` 引用白名单）→ FR-111/116，AC-62/64
- [ ] T-125 `ai_call_log` 表 + AOP 记录（延迟/tokens/降级/剔除数）+ 管理端统计 → FR-120

## Layer 2 能力实现
- [ ] T-126 I1 `RuleDraftService.generate()`：基线装配 → LLM → 仅允许改 weight/tiers → DRAFT 入库 → diff 视图数据 → FR-114/115，AC-60/61
- [x] T-127 I1 发布门禁（`source=AI` 必须 `confirm=true`）→ AC-61
- [ ] T-128 I2 `CompareService` 接入（结论解析 + 无引用句剔除 + `ai_generated` 落库）→ FR-116
- [ ] T-129 I3 `AdvisorService`：硬条件召回（SQL）→ LLM 白名单内排序 → 理由/风险结构校验 → FR-117，AC-63
- [ ] T-130 I4 `SceneCodeService`：geometry → Three.js 函数体（纯模板版兜底）→ AC-67
- [ ] T-131 I5 看房要点清单（低分维度 → 现场核验项）
- [ ] T-132 缓存门（`CacheGate` 60s，`X-AI-Cache` 头）→ AC-65

## Layer 3 前端
- [ ] T-133 `stores/ai.ts` 的 `withFallback`（50310 → 提示条，不报错）
- [ ] T-134 `AiAdvisorView`（推荐卡：理由可点跳明细 / 风险 / 追问 / 免责条）
- [ ] T-135 `admin/AiRuleDraftView`（诉求输入 + diff 表 + 逐条采纳/忽略 + 试算）
- [ ] T-136 3D 调试抽屉（代码 `<pre>` + 复制，明确"不执行"）→ FR-118
- [ ] T-137 报告结论卡 `AI/模板` 角标与"仅供参考"文案 → FR-121

## Layer 4 验证与文档
- [ ] T-138 `StubLlmClient` 四种返回（正常/超时/坏 JSON/幻觉句）驱动集成测试 → AC-62/64
- [ ] T-139 断网降级演示脚本（`LLM_ENABLED=false` 全流程走查）→ AC-66
- [ ] T-140 草案契约测试：`RuleDraftService` 输出必过 `scoring-rule.schema.json`
- [ ] T-141 `docs/03` AI 网关位置 + 时序图、`docs/04` §7 提示词与降级流程图、`docs/06` TC-AI-01~08、`docs/05` §9 顾问使用
- [ ] T-142 报告"AI 幻觉与风险"控制说明（引用剔除、白名单排序、人工审阅门禁）——答辩必问

**追溯**：FR-110~122 → T-119~140；AC-60~67 → TC-AI-xx；宪法第三条（可解释）→ T-124/127/129。
