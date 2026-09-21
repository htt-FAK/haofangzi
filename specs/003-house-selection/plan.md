# PLAN 003 · 在线选房（HOW）

**改动面**: `HouseController`、`SelectionController`、`FavoriteController`、`HouseService`、`SelectionService`、
`mapper/HouseMapper`（含悲观锁 SQL）、`job/LockExpireJob`、前端 `views/{HousesView,HouseDetailView,SelectionConfirmView,FavoritesView}.vue`、`api/house.ts`

## 1. 契约（`contracts/openapi.yaml` tag `house` / `selection`）
`GET /api/houses`（多条件 + 排序 + 分页）、`GET /api/houses/{id}`、`GET /api/buildings/{id}/floors`（楼层矩阵）、
`GET/POST/DELETE /api/favorites`、`POST /api/selection/locks`（body `{houseId, confirm:true}`）、
`GET /api/selection/locks/current`（我的有效锁）、`POST /api/selection/locks/{no}/renew|cancel|convert`。

## 2. 筛选查询
动态 SQL（MyBatis 文本块 + `<if>`），条件命中索引 `(sale_status)`、`(building_id, floor_no)`；
`ORDER BY total_price|area|score` 时 `score` 走 `evaluation` 关联子查询（`MAX(id)` 派生表，避免一对多放大），
演示数据量下可接受；如压测不过则改为 `hf_house` 冗余列 `last_score`（写入时更新，记录在 ROADMAP）。

## 3. 锁房并发方案（核心，课程答辩重点）

```
POST /selection/locks
 → 参数校验 + 登录 + confirm=true（未确认 40013）
 → 事务 begin
    1) House house = houseMapper.selectForUpdate(houseId)      // SELECT ... FOR UPDATE 行锁
    2) if (saleStatus != AVAILABLE) throw Biz(40911/40910)     // 含他人锁定的判断
    3) existing = lockMapper.findByHouseActive(houseId)
       - 我的锁 且 已续期<2  → 续期 expire_at = min(now+10min, firstAt+20min), renew++
       - 他人的锁            → 40910 + 备选（同户型 AVAILABLE 按 |floorNo-diff| 升序 3 条）
    4) insert hf_house_lock(status=ACTIVE, intention_no=YX...) // 唯一索引 uk_house_id 兜底
    5) update hf_house set sale_status='LOCKED', lock_expire_at=? where id=? and sale_status='AVAILABLE'
 → commit
```
**三重保护**：行锁（互斥）+ 乐观条件更新（`where sale_status='AVAILABLE'` 影响行数≠1 即回滚）+ 唯一索引（脏数据兜底）→ AC-21。

**过期回收**：`LockExpireJob` 每 30s 扫 `status=ACTIVE and expire_at<now()`（`LIMIT 200`，多实例用
`ShedLock` 风格行锁表或直接接受幂等重复），置 `EXPIRED`、房源回 `AVAILABLE`、写站内通知 → AC-22。
（宪法禁止引入 MQ，定时任务是刻意选择，答辩说明取舍。）

**超时/一致性说明**：锁释放与转预约之间存在竞态，转预约在同一事务里 `selectForUpdate` 复核，失败则返回 40912 让用户重选。

## 4. 状态机（`docs/diagrams/house-state.mmd`）
`AVAILABLE →(lock) LOCKED →(convert) RESERVED →(admin/预约成交) SOLD`；
`LOCKED →(cancel/expire) AVAILABLE`；`RESERVED →(24h 超时) AVAILABLE`；非法流转在 `HouseStatus.next()` 枚举方法里拒绝（抛 `40911`）。

## 5. 收藏
`insert ignore`-语义（先查后插 + 唯一索引捕获 `DuplicateKey` 转幂等成功）；上限 50 在 service 计数校验；
`favoriteType` 区分 `HOUSE_TYPE/HOUSE`；收藏项查询用一次 join 带出评分徽标。

## 6. 前端
- 列表页：筛选状态写入 `query` + Pinia，返回时保留；`useDebounce(300ms)` 触发查询。
- 确认页：`setInterval` 倒计时以服务端 `expireAt` 为基准（防改本地时间），页面 `visibilitychange` 回来重新校准。
- 冲突处理：`40910/40911` 弹窗内直接展示"备选楼层"按钮（AC-20）。
- 未登录 CTA：`router.push({name:'login', query:{redirect}})`（AC-28）。

## 7. Excel 导入（FR-40）
`hutool-poi`/EasyExcel 逐行校验，错误行收集 → 生成错误明细 xlsx 流下载，成功行分批 `saveBatch(200)`（AC-26）。

## 8. 审计与通知
锁/释放/改状态三类动作写 `sys_audit_log`；站内信表演示期复用 `sys_message`（简化：仅"我的通知"列表，不推送）。

## 9. 测试
单测：状态机非法流转、续期上限、倒计时基准；并发集成测试 `SelectionConcurrencyTest`
（`ExecutorService` 20 线程 → 断言成功数=1、锁表 1 行，对应 AC-21）；
前端 vitest：`formatExpire`、备选推荐排序。JMeter 脚本 `docs/06-测试计划/jmeter/selection.jmx`。
