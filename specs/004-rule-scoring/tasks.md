# TASKS 004 · 规则打分引擎

## Layer 1 契约与数据（先冻结，前后端/AI 域可并行）
- [x] T-061 `contracts/scoring-rule.schema.json` 定稿（tier、operator、evidence、suggestion、weight 约束）
- [ ] T-062 4 张评估表 + 21 指标字典初始化（`eval_dimension`、`eval_rule` seed）
- [x] T-063 `resources/rules/default-rules.json`（7 维 21 指标完整分档 + 依据条文）→ FR-50

## Layer 2 引擎骨架
- [x] T-064 `MetricCalculator` 接口 + `MetricRegistry`（Spring 收集为 code→bean）→ FR-60
- [x] T-065 `MetricContext` 装载器（户型 + 房间 + 房源 + 楼栋 + 楼盘 + 画像，一次查询组装载）
- [x] T-066 `RuleRepository`：加载 ACTIVE 集 + Caffeine + `invalidateOnPublish` → FR-55/58
- [x] T-067 `TierMatcher`：有序区间匹配 + 边界规则（左闭右开）+ 单测 → FR-50
- [x] T-068 `ScoreAssembler`：维度归一化 + 总分 + 等级 + 证据渲染 + 建议排序 → FR-50/52/57，AC-30/31

## Layer 3 指标计算器（按维度分组，可并行 [P]）
- [x] T-069 [P] LIGHT ×5（`wfa`、`orientation`、`bay_depth`、`floor_occlusion`、`dark_bath`）
- [x] T-070 [P] VENT ×3（`cross`、`open_window_ratio`、`kitch_bath_exhaust`）
- [x] T-071 [P] CIRC ×4（几何连通类，需 `geometry.ts` 同规则的后端实现）
- [x] T-072 [P] UTIL ×4（含最小开间罚分、收纳墙长度）
- [x] T-073 [P] QUIET ×3 + GREEN ×3
- [x] T-074 [P] COST ×3（读画像预算，缺失→"数据不足"）

## Layer 4 服务与接口
- [x] T-075 `EvaluationService.evaluate()`：缓存 → 取规则 → 计算 → 落快照；`GET /evaluations/{id}` 读快照 → FR-51，AC-32
- [ ] T-076 人群模板（4 套）权重覆写 + `template` 入参 → FR-53，AC-33
- [ ] T-077 指标分位对比（样本分桶）→ FR-54
- [ ] T-078 结果缓存（key 含 setVersion+template，TTL 10min）→ FR-61
- [x] T-079 管理端：规则集 CRUD、权重校验、试算接口、PUBLISHED 只读、AI 草案需确认才可发布 → FR-55/56/60，AC-34/36

## Layer 5 前端
- [ ] T-080 `EvaluateView`：等级大字 + 总分环 + 雷达 + 维度卡 + 明细表（依据气泡）→ FR-50/52
- [ ] T-081 改进建议区 + 分位条 + 模板切换重算 + 历史评测抽屉 → FR-53/54
- [x] T-082 `admin/RulesView`：版本列表、权重滑块（和值实时校验）、分档表格、试算弹窗 → FR-56/60

## Layer 6 验证与文档
- [ ] T-083 单测：`ScoreAssemblerTest`（≥12 例含除零/全缺/边界）+ 每计算器 ≥2 例 → 引擎行覆盖 ≥80%
- [ ] T-084 契约测试：schema 校验 `default-rules.json`；集成测试 AC-32/35/36
- [ ] T-085 性能：`EvaluationPerfTest` p95 <200ms；权重灵敏度分析（±20% 矩阵，FR-59）出图
- [ ] T-086 `docs/02` DFD-1.4（评估加工逻辑）+ DD（规则集/评测记录/数据流"户型参数包"）
- [x] T-087 `docs/03` 引擎在结构图中的位置 + 中层模块分解；`docs/04` §4 打分流程图 + 判定表（窗地比分档）+ 证据伪代码
- [x] T-088 `docs/05` §5 评估解读 + 免责文案；`docs/06` TC-E-01~12；`docs/03` 附录引用 `default-rules.json`

## 追溯
FR-50~61 → T-061~085；AC-30~37 → TC-E-01~12；NFR-01/08/11 → T-078/066/068。
