# PLAN 006 · 预约看房（HOW）

**改动面**: `AppointmentController`、`AppointmentService`、`SlotService`、`job/AppointmentNotifyJob`、
`domain/enums/AppointmentStatus`、前端 `views/AppointmentNewView.vue`、`views/AppointmentListView.vue`、`views/admin/AppointmentsView.vue`

## 1. 容量与占位（不冗余计数）
`hf_slot_config(project_id, visit_date, slot, capacity)`；已占 =
`SELECT visit_slot, COUNT(*) FROM appointment WHERE project_id=? AND visit_date=? AND status IN ('PENDING','CONFIRMED') GROUP BY visit_slot`。
提交前在事务内 `SELECT ... FOR UPDATE` 锁住该 `slot_config` 行 → 计算 used → 满了返回 `40018` + 推荐空段（同项目日期升序取 3）→ AC-50 与并发超卖防护。
释放靠状态变化（取消/超时/完成）自然生效，无需回滚补偿。

## 2. 状态机实现（`docs/diagrams/appointment-state.mmd`）

| from＼to | PENDING | CONFIRMED | ARRIVED | COMPLETED | CANCELED |
| --- | --- | --- | --- | --- | --- |
| PENDING | — | 顾问确认 | — | — | 用户/后台取消 |
| CONFIRMED | 改期回退 | — | 到访登记 | — | 取消（须原因） |
| ARRIVED | — | — | — | 填跟进结果 | 不允许（`40931`） |
| COMPLETED | — | — | — | — | 不允许 |

`AppointmentStatus.canNext(to)` 枚举静态方法 + `updateByIdAndStatus(id, from, to)`（乐观条件，影响行数 0 → `40931`）。

## 3. 提醒任务
```
@Scheduled(cron = "0 */10 * * * ?") 每 10 分钟
 → select * from appointment
   where status='CONFIRMED'
     and ( (visit_date = tomorrow and notified_at is null and now() >= visit_datetime - 1 day + 8h窗口)
        or (visit_datetime between now() and now()+2h and reminded_2h_at is null) )
 → 客户：站内信 + MockSms；顾问：站内信（"明日 09:00 张XX 138****0001 看 A1-98"）
 → update ... set notified_at=now() where id=? and notified_at is null   // 幂等，多实例也安全
```
提前 1 天与提前 2 小时两个字段分开（`notified_at` / `reminded_2h_at`），保证 AC-53 不重复。

## 4. 与 003 意向单联动
`appointment.intention_no` 非空时：确认预约 → 意向转 `RESERVED→`（保持房源锁定不释放，若已过 24h 则重新占位、失败提示）；
成交登记 → 同一事务 `hf_house.sold`（AC-56，写审计）。

## 5. 权限与脱敏
`@PreAuthorize("hasAnyRole('CONSULTANT','ADMIN')")` + service 内 `if (role==CONSULTANT && ap.consultantId!=me) throw 40302`；
`AppointmentVO.contactPhone` 统一 `MaskUtil.mobile()`（含导出）→ FR-102、AC-57。

## 6. 导出与看板
明日清单：EasyExcel 写流（`visit_slot` 升序、同段并列展示）；
看板（FR-100）：一条 SQL 分组统计 + ECharts 柱/环图，口径写进报告附录（分母 = 提交量，爽约 = CONFIRMED 未到店且过期）。

## 7. 测试
单测：`canNext` 全矩阵 25 组（含非法）、`SlotService.recommend` 排序；
MockMvc：AC-51/52/54/55；提醒幂等：连跑两次任务，通知记录数不变（AC-53）；
并发：10 线程抢同一时段（capacity=6）→ 恰 6 成功（NFR-05 类比）。
