# TASKS 003 · 在线选房

## Layer 1 契约与数据
- [x] T-039 冻结 openapi `house` / `selection` / `favorites` 端点与错误码（40910/40911/40912/40013）
- [ ] T-040 `hf_house`、`hf_house_lock`、`hf_favorite` 建表 + 唯一索引（`uk_lock_house`）+ 600 套房源 mock 数据
- [ ] T-041 [P] `HouseStatus` 枚举与 `next()` 流转校验（含单测）→ FR-39

## Layer 2 列表与筛选
- [ ] T-042 房源分页查询 DTO/Mapper（组合条件 + 排序），补索引 → FR-30/31
- [ ] T-043 评分徽标 join 派生表（同户型最新分）→ FR-31
- [ ] T-044 楼层矩阵接口 `GET /buildings/{id}/floors` → FR-32
- [ ] T-045 前端 `HousesView`（筛选栏/列表/排序/分页/骨架/条件回填画像）+ `HousesView` 条件保存 → FR-30/31
- [ ] T-046 前端 `HouseDetailView`（房源信息 + 内嵌 2D 户型 + 同户型异层切换）→ FR-32
- [ ] T-047 收藏 CRUD（幂等 + 上限 + 加入对比入口）与 `FavoritesView` → FR-33/25, AC-25

## Layer 3 锁房链路
- [ ] T-048 `selectForUpdate` Mapper + `SelectionService.lock()` 五步事务 → FR-34/35, AC-20/21
- [ ] T-049 续期（≤2 次、总 20 分钟）+ 取消 + 备选房源推荐查询 → FR-36, AC-20/23
- [ ] T-050 转意向（`RESERVED` + `intention_no` + 事务内复核）→ FR-37, AC-24
- [ ] T-051 `LockExpireJob`（30s 扫描、批量 200、写通知与审计）→ FR-38, AC-22/24
- [ ] T-052 `SelectionConfirmView`：快照 + 规则折叠 + 复选确认 + 服务端时间倒计时 + 过期态 → FR-34/36, AC-22/28
- [ ] T-053 冲突弹窗组件 `LockConflictDialog`（40910/40911 → 备选一键切换）→ FR-41

## Layer 4 管理端
- [ ] T-054 销控表格 + 改状态（原因必填）+ 审计 → FR-40
- [ ] T-055 Excel 模板下载 / 批量导入 + 错误明细下载 → FR-40, AC-26
- [ ] T-056 顾问"名下意向清单"（跟进状态标记）→ US-25

## Layer 5 验证与文档
- [ ] T-057 `SelectionConcurrencyTest`（20 线程断言 1 成功）+ 状态机/续期单测 ≥10 → NFR-05, AC-21
- [ ] T-058 JMeter `selection.jmx`（p95 与并发结果写入报告）
- [ ] T-059 `docs/02` DFD-1.3 + DD（房源/锁记录）；`docs/04` §3.4 锁房判定表 + 流程图；`docs/03` 结构图中"选房服务"叶子模块
- [ ] T-060 用户手册 §4 模拟选房与规则 FAQ；测试用例 TC-S-01~05、TC-P-01

## 追溯
FR-30~42 → T-039~058；AC-20~28 → TC-P-01 / TC-S-xx；NFR-05 → T-057。
