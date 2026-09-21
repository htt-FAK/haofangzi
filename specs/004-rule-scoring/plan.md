# PLAN 004 · 评估引擎（HOW）

**改动面**: `engine/*`（本系统技术核心）、`controller/EvaluationController`、`controller/AdminRuleController`、
`service/EvaluationService`、`domain/entity/` 4 张评估表、`resources/rules/default-rules.json`、
`resources/seed-data.sql`（分位样本）。前端 `views/EvaluateView.vue`、`views/admin/RulesView.vue`。

## 1. 引擎结构（策略 + 规则外置）

```mermaid
flowchart LR
  RUL[("eval_rule_set / eval_rule<br/>(JSON 外置)")] -->|启动加载+发布失效| REPO[RuleRepository<br/>Caffeine]
  IN[HouseTypeDetail + House 修正 + 用户模板] --> FAC[MetricContext]
  FAC --> CALC["MetricCalculator × 21<br/>（@Component，code 自注册）"]
  REPO --> SCORE[ScoreAssembler]
  CALC -->|MetricResult{code,value,ok,source}| SCORE
  SCORE --> SNAP[EvaluationSnapshot<br/>detail_json]
  SNAP --> OUT[EvaluationResultVO<br/>total/level/dims/metrics/suggestions]
```

- `MetricCalculator` 接口：`String code(); MetricResult calc(MetricContext ctx);` Spring 启动收集为 `Map<String,MetricCalculator>`；
  规则里的 `metric_code` 找不到实现 → 记日志并降级为"数据不足"，**不阻塞整体评分**（稳健性）。
- `MetricContext = {HouseType ht, List<Room> rooms, House house, Building b, Project p, UserPref pref}`，
  每个 calculator 只读自己需要的字段，天然线程安全 → 可并行计算（`parallelStream` 视实测，21 指标串行已 <5ms）。
- **打分管线**（`ScoreAssembler`，纯函数，单测主对象）：
  1. 逐规则求值 → 档位（`tier_json` 按序取第一个命中区间）→ 指标分；
  2. `evidence` 模板渲染：`窗地比 {value}=0.16，满足 {cond}（≥0.14），判为优；依据：{basis}`；
  3. 维度分 = Σ(指标分 × 指标内权重) / Σ(有效指标权重)（FR-57 归一化）；
  4. 总分 = Σ(维度分 × 维度权重)，保留 1 位小数，`level` 按阈值映射；
  5. 建议：命中"中/差"档 → 取 `rule.suggestion` 模板；再按"分数提升潜力 = 权重×(满分-得分)"排序取前 3。

## 2. 指标公式登记表（`docs/04-详细设计` 逐条画流程图/伪代码）

| code | 公式（简化实现，答辩可讲清） | 数据源字段 |
| --- | --- | --- |
| `LIGHT_wfa` | 各起居空间 `windowArea / area` 的加权均值 | `hf_room` |
| `LIGHT_orientation` | 主朝向映射分：南 100 / 东南·西南 85 / 东·西 65 / 北 45 | `house_type.orientation` |
| `LIGHT_bay_depth` | `bay / depth`，理想区间 1.2~1.8 | `bay`,`depth` |
| `LIGHT_floor_occlusion` | `1 - max(0,(8-floorNo)/8) × (1-southOcclusion)` | `house.floor_no`,`building.south_occlusion` |
| `LIGHT_dark_bath` | 是否存在无外窗卫生间（bool） | `hf_room` category=BATH,windowArea=0 |
| `VENT_cross` | 存在南向与北向均有窗且中间为起居/走道连通（简化：`orientation=NS` 或房间投影 x 区间重叠判定） | `hf_room` |
| `VENT_open_window_ratio` | 可开启窗面积 / 外墙面积 | `window_area` |
| `VENT_kitch_bath_exhaust` | 厨卫均有外窗或外墙 | `hf_room` |
| `CIRC_corridor_ratio` | `Σ(走道类房间面积) / private_area` | `hf_room` |
| `CIRC_public_private` | 客厅与卧室集合边界是否需穿越（简化：主卧与客厅是否共墙重叠段 <1.2m） | `hf_room` |
| `CIRC_wet_separation` | `bath_area ≥ 4 && 存在独立洗手区` | `bath_area` |
| `CIRC_meal_kitchen` | 餐厅中心 ↔ 厨房中心距离 ≤ 3.0m | `hf_room` |
| `UTIL_usable_ratio` | `private_area / gfa` | |
| `UTIL_irregular` | `irratio` | |
| `UTIL_room_min_size` | 最小开间 < 2.4m 记罚 | `hf_room.width` |
| `UTIL_storage` | `storage_wall_len / gfa` | |
| `QUIET_road` | 主次卧朝向是否与临 road 侧同向（bool→罚分） | `near_road`,`orientation` |
| `QUIET_shaft` | 主卧是否贴电梯井/管井 | `adjacent_elevator` |
| `QUIET_elevator_ratio` | `units_per_floor / elevator_count` | `hf_building` |
| `GREEN_*` | 净高、阳台进深 ≥1.5m、绿化率 | |
| `COST_unit_price_gap` | `(unitPrice - project.avgPrice)/avgPrice` | `hf_house`,`hf_project` |
| `COST_total_fit` | `totalPrice` 是否落入 `[budgetMin,budgetMax]`（无画像则"数据不足"） | `sys_user` |
| `COST_area_waste` | `无效面积(corridor+irr) × unitPrice` 元/㎡ 占比 | |

> 分位对比（FR-54）：启动时把 20 个样本户型的指标值装入内存做 3 分位分桶，避免查询期统计。

## 3. 规则外置与热更新
- `resources/rules/default-rules.json`（结构 = `specs/004/contracts/scoring-rule.schema.json`）在 v1 迁移时导入 `eval_rule_set(version=v1,status=PUBLISHED,active=1)`；
  之后**数据库为唯一真源**，JSON 仅用于重建演示环境与给报告附录看规则原文。
- `RuleRepository` Caffeine（key=`activeSetVersionHash`），规则发布 → `publish()` 内主动 `invalidate` → 无重启生效（FR-58/AC-35）。
- 校验：`Σweight=1±0.001`、operator 与 tier 类型匹配（LT/LE 只允许单层）、`metric_code` 必须已注册 → 否则 `40920`。

## 4. 缓存与性能
- 结果缓存 key = `sha256(houseTypeId|houseId|setVersion|template|budgetBand)`，TTL 10min，最大值 2000（Caffeine）。
- 明细缓存与落库分开：缓存只加速返回；`evaluation` 仍写入（保证"我的评估历史"）。
- 21 指标 + JSON 序列化实测目标：单户型 < 30ms，50 并发 p95 < 200ms（NFR-01）。

## 5. 可复现性（关键设计）
`detail_json` 冻结指标值、命中档位、权重快照、`basis` 文本、`setVersion`；
`GET /api/evaluations/{id}` 直接读快照而非重算 → AC-32 与报告"历史成绩可追溯"。
（规则改版影响未来的评估，不影响历史 —— 宪法第五条 3。）

## 6. API
`POST /api/evaluations {houseTypeId,houseId?,template?}`、`GET /api/evaluations/{id}`、
`GET /api/house-types/{id}/evaluation/latest`、`GET /api/evaluations?houseTypeId=`（历史）、
`GET/POST /api/admin/rule-sets`、`PUT /api/admin/rule-sets/{id}/tiers`、`POST .../publish`、`POST /api/admin/rule-sets/try`（试算）。

## 7. 前端
雷达图 ECharts `radar`（7 维）、维度卡 + 明细表（命中档位色标 + `?` 弹依据）、人群模板下拉（切换即重算，带 loading 骨架）、
"改进建议"手风琴、分位条（该指标在样本中的位置）。管理端：权重滑块（实时显示和值，非 1 禁用发布）+ 分档表格。

## 8. 测试（宪法第四条：本域覆盖率 ≥80%）
- 纯单测 `ScoreAssemblerTest`：满分/0 分/全缺数据/归一化/等级边界（85/75/60）；
- 指标单测：`LightWfaCalculatorTest`（含面积 0 的除零保护）、`VentCrossCalculatorTest`（L 形布局）等，每计算器 ≥2 例；
- 集成：`EvaluationFlowTest` 评估→快照→规则改版→历史复现（AC-32）、发布权限（AC-36）、热更新（AC-35）；
- 契约：`scoring-rule.schema.json` 对 `default-rules.json` 做 `json-schema-validator` 测试（防止规则文件坏掉导致启动失败）；
- 性能：`EvaluationPerfTest`（1000 次计算断言 p95）。
