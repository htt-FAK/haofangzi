# 术语表 Glossary（specs/000-program）

> 术语唯一定义处（宪法治理条款）。任何 spec、文档、代码注释引入新词须先登记本表。

| 术语 | 英文/字段 | 定义 |
| --- | --- | --- |
| 楼盘项目 | project / `hf_project` | 肇庆市一个"好房子"示范住宅小区，含位置、开发商、均价 |
| 楼栋 | building / `hf_building` | 楼盘内单栋建筑，含总层数、每层户数、梯户比 |
| 户型 | houseType / `hf_house_type` | 一种房间组合方案（如"建面 98㎡ 三房两厅两卫 南北通透"），是**评估的主体** |
| 房源 | house / `hf_house` | 具体可卖的一套房子 = 楼栋 + 楼层 + 房号 + 户型，含建筑面积、单价、销控状态 |
| 房间构件 | room / `hf_room` | 户型内单个功能空间（客厅/主卧/厨房/卫生间/阳台），含尺寸、门窗、朝向 |
| 套内面积 | privateArea | 各使用空间净面积之和（不含墙体与公摊） |
| 建筑面积 | gfa | 套内面积 + 公摊；界面默认展示建筑面积 |
| 得房率 | usableRatio | 套内面积 / 建筑面积，% |
| 面宽 / 进深 | bay / depth | 户型朝南方向的宽度 / 垂直于面宽的深度 |
| 窗地比 | wfa | 某空间窗面积 / 该空间地面面积，采光核心指标 |
| 南北通透 | crossVentilation | 南向与北向均有可开启外窗且通风路径无明显遮挡 |
| 动线 | circulation | 居住/家务/访客三条流线在户型内的路径长度与交叉情况 |
| 功能分区 | zoning | 动静分区、干湿分离、公私分区三项合规检查 |
| 评估指标 | metric | 可从户型几何/属性数据算出的一个量化值，如 `daylight_wfa` |
| 评估维度 | dimension | 采光/通风/动线/实用/静谧/绿色/经济 7 类，各有权重 |
| 规则集 | ruleSet / `eval_rule_set` | 一套带版本的维 + 指标阈值分档配置，是打分依据；有 `DRAFT/PUBLISHED/OFFLINE` 状态 |
| 规则 | rule / `eval_rule` | `metric + operator + threshold + score + evidence` 的最小判定单元 |
| 分档评分 | tiered scoring | 指标值落入某区间得该区间分（非 0/1），见判定表 |
| 评测记录 | evaluation / `evaluation` | 某用户对某户型在某规则集版本下的一次打分结果 + JSON 明细快照 |
| 证据 | evidence | 一条得分的解释：指标值、命中规则、阈值、规范依据（如《住宅项目规范》条文） |
| 对比报告 | compareReport | 2-4 个户型的得分矩阵 + 差异高亮 + AI 结论文本，可导出 PDF/分享 |
| 锁房 | houseLock | 模拟选房时对房源的**临时占用**（非认购），默认 TTL 10 分钟 |
| 销控状态 | saleStatus | `AVAILABLE 可选 / LOCKED 锁定中 / RESERVED 已预留 / SOLD 已售` |
| 意向单 | intention | 锁房成功后生成的 24h 有效意向记录，可转预约看房 |
| 预约看房 | appointment | 用户预约到售楼部实地看房的时间段 + 顾问 + 状态机 |
| 全景 | panorama | 2:1 等距柱状投影图，前端以 Three.js 球面贴图实现 360° 查看 |
| LLM 网关 | LlmClient | 大模型调用的统一接口，含超时/重试/降级；`promptKey` 标识提示词模板 |
| MockLlmClient | — | 离线兜底实现，用模板 + 规则产出结构化结果，返回码 50310 标记 |
| AI 生成标注 | aiGenerated | 表示该内容由模型生成，需展示依据与"仅供参考"提示 |
| 权重灵敏度分析 | weightSensitivity | 微调维度权重观察排名变化，用于验证评分稳健性 |
| 分享令牌 | shareToken | 对比报告只读访问的随机串，默认 7 天有效 |
| 软删除 | deleted | `deleted=0/1`，逻辑删除标记（全表通用，NFR-12） |

## 编号前缀约定

| 前缀 | 含义 | 出现于 |
| --- | --- | --- |
| `FR-nnn` / `NFR-nn` | 功能/非功能需求 | 各 spec.md |
| `US-nn` | 用户故事 | spec.md |
| `AC-nn` | 验收标准 | spec.md |
| `T-nnn` | 任务 | tasks.md |
| `DFD-nn` / `DD-xxx` / `TC-xxx` | 数据流图 / 数据字典条目 / 测试用例 | docs/02、docs/06 |
| `METRIC-xx` | 评估指标 | docs/04、specs/004 |
