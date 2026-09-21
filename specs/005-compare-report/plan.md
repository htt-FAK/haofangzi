# PLAN 005 · 对比报告（HOW）

**改动面**: `ReportController`、`CompareService`、`PdfRendererService`、`domain/entity/CompareReport`、
前端 `views/{CompareView,ReportsView,ReportShareView}.vue`、`components/CompareMatrix.vue`

## 1. 矩阵构建（纯函数，`CompareService#buildMatrix`）
```
输入: List<houseTypeId>, templateCode, ruleSetVersion
1. 逐个取 latest evaluation（同 template+version），缺失则同步调用 004 生成（FR-71）
2. rows = 维度（7）+ 指标（21，按维度分组可折叠）+ 绝对量条目（gfa, totalPrice, usableRatio, orientation, floor）
3. 每行：normalize(值) → 判定 best/worst；diffText = 比率类→绝对差(保留2位)、金额类→百分比、等级类→差档数
4. 输出 matrix_json（含 cell:{value, score, grade, highlight, missing, diff}）供前端与 PDF 共用同一结构
```
缺失数据单元格 `missing=true`，不参与 best/worst，也不作为 AI 证据（AC-47）。

## 2. 结论生成（AI 主 + 模板兜底）
- AI：`LlmClient.generate("compare-conclusion", payload)`，payload = 精简矩阵（维度分 + 最优/最差指标 + 用户画像），
  要求返回 `{recommendation, reasons[], prosCons{[houseTypeId]:{pros[],cons[],risks[]}}, fitAudience}`，
  并由后端**校验每条 reason 至少引用一个 `metricCode`**（正则匹配 `LIGHT_|VENT_|…`），不通过则视为模板降级 → NFR-11。
- 模板兜底（`MockLlmClient`）：按 `总分差 ≥3 → 推荐 X；COST 维度差 ≥8 → 提示预算敏感；QUIET 差 ≥6 → 临路提醒`，
  输出结构与 AI 相同，保证前端单一渲染路径。
- `aiGenerated` 布尔入 `compare_report.ai_generated`，页面与 PDF 均显示角标。

## 3. PDF 导出
`openhtmltopdf`（HTML 模板 + Thymeleaf 渲染 → PDF，中文字体嵌入 `SourceHanSansSC-Regular.otf`）；
失败 `try-catch` 降级返回自包含 HTML（错误码 `50002`，`Content-Disposition` 仍给文件）→ 演示不崩。
两页排版：P1 头部 + 雷达图（SVG，由 ECharts 服务端渲染/或手写 SVG 折线）+ 矩阵摘要；P2 指标明细 + 结论 + footer 免责。

## 4. 分享令牌
`share_token = Base64URL(SHA1PRNG 16 字节)`；唯一索引；`expire_at = now + days`（≤30）；撤销 = 置 token 为 NULL 并记审计。
`/share/reports/{token}` 走白名单（免鉴权）→ service 内只读查询并**剥除 PII 字段**（AC-46）；`viewCount` 更新用 `update ... set view_count=view_count+1`。

## 5. 存储与快照
`compare_report(house_type_ids JSON, matrix_json, conclusion, ai_generated, share_token, expire_at, set_version, view_count)`；
重开只读快照（AC-44），矩阵不重算 → 规则改版不影响历史；列表页显示"基于 vN"。

## 6. 前端
候选池来源三处统一（收藏 / 列表多选 / 详情"加入对比"），Pinia `compare` store 存 id 列表（<2 禁用按钮，>4 阻止勾选并 toast）。
矩阵表：`CompareMatrix.vue`，`position:sticky` 锁首列，行内 best/worst 用色板（绿 `#0a8a4a` / 红 `#c62828`，满足色弱：同时给图标 ↑↓）。
结论卡：推荐徽标 + 理由列表（每条 metric 引用可点击 → 跳矩阵行并高亮）+ 风险提示 + "AI 生成"角标 + 灵敏度提示（FR-79）。
打印样式：`@media print` 与 PDF 模板同源类名，减少两套排版漂移。

## 7. 测试
单测：`buildMatrix` best/worst/diff/缺失、token 生成唯一性与过期、结论证据引用校验（AI 幻觉防线）；
MockMvc：AC-40/41/44/45；PDF 快照测试（前 2 页文本包含关键字，避免渲染断图）；
手测清单 TC-R-01~09 写入 `docs/06`。
