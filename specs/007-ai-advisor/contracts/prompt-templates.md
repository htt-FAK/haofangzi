# CONTRACT 007 · 提示词模板与输出契约（仓库内可评审）

统一约定：`role: system` 固定三段（角色 / 输出模式 / 硬约束）；`temperature=0.2`；`response_format=json_object`；
禁止在 payload 放手机号/姓名（由 `PayloadSanitizer` 白名单保证）。所有 `metricCode` 必须来自入参列表，
否则 `JsonSchemaGuard` 丢弃该句并 `hallucinationDropped++`。

---
## `rule-draft@v3`（I1 规则草案）
**用户消息模板**
```text
基线规则集（ACTIVE）：{{baseline.dimensions}}   // [{code,weight,rules:[{metricCode,operator,tiers}]}]
家庭诉求：{{requirement}}                       // 例：三代同堂、重视通风与静谧、预算 150 万内
请输出一个 vNext 草案规则集，要求：
1. 只允许修改 dimensions[].weight 与 rules[].tiers，禁止新增/删除 metricCode；
2. 7 维权重之和必须 = 1.000（±0.001）；VENT ∈ [0.10,0.28]、QUIET ∈ [0.05,0.20]；
3. 每处变更必须给 changeNote（≤40 字，说明理由）与 basis（规范条文号或"行业设计常识"）；
4. 不给任何投资建议；不输出 JSON 之外的文本。
```
**输出契约**：符合 `specs/004-rule-scoring/contracts/scoring-rule.schema.json`，另加
```json
{ "changes": [{ "path": "dimensions[VENT].weight", "from": 0.18, "to": 0.24, "changeNote": "三代同堂共居，通风优先级提升", "basis": "《住宅项目规范》采光通风条款" }],
  "summary": "……", "caution": "草案需人工审阅后发布" }
```

---
## `compare-conclusion@v2`（I2 对比结论）
```text
你是住宅户型评估助手。以下是 {{count}} 个户型同一规则版本({{setVersion}})的评分（数值来自规则引擎，不要重新计算或改动）：
{{matrixLite}}   // [{houseTypeId,name,total,level,dims:{LIGHT:..},topMetrics:[{code,value,grade}],missing:[code]}]
用户画像：{{profileLite}}  // familyStructure, budgetBand, mustRooms, preferTags
输出：
{ "recommendation": houseTypeId, "confidence": "高|中|低",
  "reasons": [{"text": "≤60字，必须包含至少一个 metricCode", "refs": ["VENT_cross"]}],
  "perItem": [{"houseTypeId":1, "pros":["…refs"], "cons":["…"], "risks":["…"]}],
  "fitAudience": "…", "nextCheck": ["现场核验要点：…"] }
硬约束：只引用 refs 给出的 code；无依据则不写；不出现金额建议/投资建议；缺失数据项（missing）不得作为理由依据；
若两个户型总分差 <3，confidence 必须为"中"或"低"并说明取舍取决于偏好权重。
```
**模板降级（Mock）**：`总分差 ≥3 → 推荐 X`；`COST 维差 ≥8 → 预算敏感提示`；`QUIET 最低 → 临路/井道提醒`，
文案含 `refs` 真实 code，结构完全一致（保证前端单一路径）。

---
## `advisor-ranking@v2`（I3 智能选房顾问，**白名单内排序**）
```text
候选（已按硬性条件过滤：预算 {{budgetBand}}、需 {{mustRooms}} 房、在售）：
{{candidates}}  // [{houseTypeId,name,gfa,totalPrice,orientation,useableRatio,dims:{...}}]  (≤30 条，id 白名单 = candidateHouseTypeIds)
请：先按家庭画像给 7 维个性化权重重排（不得超出模板上限），输出 Top{{topN}}；
每项 {"houseTypeId": 必须在白名单, "rankReasons": [3 条，各含 refs metricCode], "riskPoints": [2 条], "askUser": "一句追问（用于进一步收敛偏好）"}
禁止：新增候选、修改任何分数、给出价格预测或投资建议。
```
**服务端二次校验**：越界 `houseTypeId` 丢弃；`rankReasons` 无 `refs` 的条目剔除；若结果数 ≠ topN → 走模板排序（按 004 分 + 画像权重重算的确定性结果）。

---
## `scene-code@v1`（I4 Three.js 场景初始化代码）
```text
输入 geometry（单位 m，原点=包围盒西北角，遵循 specs/002/contracts/geometry.schema.json）：{{geometry}}
请生成 TypeScript 函数：
export function buildHouseScene(geom: HouseTypeGeometry): THREE.Group
要求：① 外墙轮廓用 ExtrudeGeometry/Lines；② 每个 room → BoxGeometry(w, ceiling, h) 半透明 + EdgesGeometry 描边；
③ 门窗： openings 用贴图遮罩（禁止 CSG 布尔）；④ 材质与色板按 category 常量集中定义；
⑤ 顶部注释含生成时间与 promptKey@version；⑥ ≥ 60fps 目标（复用几何、限制 drawcall，给出 dispose())；
⑦ 不得读取外部资源、不得使用未安装的依赖（只允许 three / OrbitControls）。
输出：仅代码块 + 一段 notes（假设与可调参数）。
```
**安全边界**：返回字符串仅供复制与人工审阅（宪法第三条），系统内不 `eval`/不 `new Function()`；
纯模板版（无网络）用同一 geometry 直接生成等价函数体：`buildHouseScene` 的 for-loop 模板，答辩时可对比展示"AI 版/模板版"。
