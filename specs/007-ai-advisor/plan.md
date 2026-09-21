# PLAN 007 · AI 网关（HOW）

**改动面**: `integration/`（`LlmClient`、`OpenAiCompatibleClient`、`MockLlmClient`、`PromptTemplateStore`、
`JsonSchemaGuard`、`AiCallLogMapper`）、`controller/AiController`、`service/AdvisorService`、`service/RuleDraftService`、
前端 `views/AiAdvisorView.vue`、`views/admin/AiRuleDraftView.vue`、`stores/ai.ts`

## 1. 调用管线（一次请求 = 固定 5 步，任何能力都走它）

```
promptKey + payload
  1) PayloadSanitizer   去 PII、裁剪候选到 30 条、数值保留 2 位（FR-122）
  2) PromptTemplateStore  render("compare-conclusion@v3", payload)
  3) CacheGate          key = sha256(userId + promptKey + version + digest(payload))，60s（FR-113）
  4) LlmClient.chat()   4a. temperature=0.2、response_format=json_object、timeout=8s、retry≤1
  5) JsonSchemaGuard    schema 校验 + 引用完整性（metricCode 必须存在于入参矩阵）→ 不过则 Fallback
     └ Fallback: MockLlmClient（确定性模板，产出同一 schema）→ code=50310, aiGenerated=false
  写 ai_call_log（延迟、tokens、是否降级、剔除幻觉句数）
```

## 2. 提示词工程约定（详见 `contracts/prompt-templates.md`）
- 模板放仓库（可评审、可 diff），**不写进 Java 字符串**；版本号 `@vN` 进日志。
- 系统提示固定三段：角色 / 输出 JSON Schema（内联最小描述）/ 硬约束（"必须引用给定的 metricCode，禁止编造数值，禁止给投资建议"）。
- 少样本只给 1 个（课程演示上下文预算 < 3k tokens）。

## 3. 各能力落地要点

| 能力 | 关键实现 |
| --- | --- |
| I1 规则草案 | 以 ACTIVE 规则集为基线 → 只允许 AI 改 `weight` 与 `tiers`（禁止新增 `metricCode`，防止调用不存在的计算器）→ `RuleDraftDiff` 输出变更清单 → 落 `eval_rule_set(status=DRAFT, source=AI)`；发布需 `confirm=true` |
| I2 对比结论 | 输入 005 精简矩阵（不含绝对价格明细，避免模型做财务建议）；解析时按句扫描 `LIGHT_|VENT_|...` 引用，剔除无引用句（FR-116） |
| I3 顾问 | **两段式**：先规则召回（硬条件：预算区间、最少居室、`saleStatus!=SOLD`、面积段，SQL 过滤 → ≤30 候选 + 各自 7 维分）→ 再让 LLM 在**候选集合内**排序并写理由；`houseTypeId` 白名单校验，越界 ID 直接丢弃。排序差异 >1 位时前后端各记一次，便于观察模型稳定性 |
| I4 3D 代码 | 输入 geometry JSON，输出函数体；后端只返回字符串，前端 `<pre>` + 复制按钮，不做 `eval`；另有 `scene-codegen` 的纯规则模板版（无需网络即可演示） |

## 4. MockLlmClient（答辩保命）
确定性模板 + 真实数据计算：例如 I2 模板按"总分差、最优维度、最低维度"生成中文结论；I3 按加权分排序后套 3 条模板理由；
I1 输出"把 VENT 权重 0.18→0.24 并收紧 `VENT_cross` 阈值"这类可读草案 —— 让降级不变成空壳。

## 5. 配置
```yaml
haofangzi.ai:
  enabled: ${LLM_ENABLED:true}
  base-url: ${LLM_BASE_URL:https://dashscope.aliyuncs.com/compatible-mode/v1}
  model: ${LLM_MODEL:qwen-plus}
  api-key: ${LLM_API_KEY:}        # 只在环境变量，禁止入库
  timeout-ms: 8000
  max-retry: 1
  cache-seconds: 60
```
启动时 `enabled=false` 或 `api-key` 为空 → 直接注册 `MockLlmClient`（`@ConditionalOnMissingBean`风格），不抛异常。

## 6. 前端
`stores/ai.ts` 统一 `withFallback()`：`code=50310` 不弹错误 toast，改为黄色提示条 + `aiGenerated=false` 徽标；
理由中的 `metricCode` 渲染为可点击标签（跳评分明细）；推荐卡片显示"为什么推荐给你（3）/需要注意（2）"。

## 7. 安全与合规
禁 PII 出网（Sanitizer 白名单字段 + 兜底正则手机号/身份证/姓名）；结果区块固定免责（FR-121）；
调用量与延迟看板（FR-120）；密钥只在环境变量 —— `.env.example` 提供占位；日志脱敏 `digest` 不落完整 prompt。

## 8. 测试
- 单元：`JsonSchemaGuardTest`（合法/缺字段/越界 metricCode）、`PayloadSanitizerTest`（手机号必须被清除）、`RuleDraftDiffTest`；
- 集成：`@TestConfiguration` 注入 `StubLlmClient`（返回固定 JSON / 超时 / 坏 JSON / 幻觉句）→ 断言降级与剔除计数（AC-62/64/66）；
- 契约：草案过 `scoring-rule.schema.json`；
- 手工：演示脚本"断网点顾问"录制到答辩视频，防止现场网络依赖。
