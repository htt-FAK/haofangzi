package com.zhq.haofangzi.service;

import com.zhq.haofangzi.common.BizException;
import com.zhq.haofangzi.common.ErrorCode;
import com.zhq.haofangzi.common.MaskUtil;
import com.zhq.haofangzi.config.HfProperties;
import com.zhq.haofangzi.domain.entity.Appointment;
import com.zhq.haofangzi.domain.enums.AppointmentStatus;
import com.zhq.haofangzi.mapper.AppointmentMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 预约看房用例（M6，spec 006）。三处要点：
 * <ol>
 *   <li>容量判定在事务内，用 {@code COUNT} 现算占用，不做冗余计数列（避免漂移，FR-95/AC-50）；</li>
 *   <li>状态流转只走 {@link AppointmentStatus#requireNext}，更新语句带 from 条件（并发下 40931）；</li>
 *   <li>提醒任务幂等：靠 {@code notified_at} / {@code reminded_2h_at} 两个标记列（AC-53）。</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AppointmentService {

    private static final int DEFAULT_CAPACITY = 6;
    private static final int MAX_AHEAD_DAYS = 14;

    private final AppointmentMapper appointments;
    private final HfProperties props;

    /** 可选时段视图：[{date, slots:[{slot, left, capacity}]}] */
    public List<Map<String, Object>> slots(long projectId, int days) {
        LocalDate from = LocalDate.now();
        LocalDate to = from.plusDays(Math.min(days, MAX_AHEAD_DAYS));
        Map<String, List<Map<String, Object>>> grouped = new TreeMap<>();
        for (Map<String, Object> row : appointments.slotView(projectId, from, to)) {
            int capacity = intValue(row.get("capacity"), DEFAULT_CAPACITY);
            int used = intValue(row.get("used"), 0);
            grouped.computeIfAbsent(String.valueOf(row.get("visit_date")), k -> new ArrayList<>())
                    .add(Map.of("slot", row.get("slot"), "capacity", capacity, "left", Math.max(0, capacity - used)));
        }
        String[] defaults = {"09:00-10:00", "10:00-11:00", "14:00-15:00", "15:00-16:00"};
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (grouped.containsKey(d.toString())) {
                continue;
            }
            List<Map<String, Object>> list = new ArrayList<>();
            for (String slot : defaults) {
                int used = appointments.slotUsed(projectId, d, slot);
                list.add(Map.of("slot", slot, "capacity", DEFAULT_CAPACITY, "left", Math.max(0, DEFAULT_CAPACITY - used)));
            }
            grouped.put(d.toString(), list);
        }
        List<Map<String, Object>> out = new ArrayList<>();
        grouped.forEach((date, list) -> out.add(Map.of("date", date, "list", list)));
        return out;
    }

    @Transactional
    public Appointment create(long userId, Appointment cmd) {
        LocalDate today = LocalDate.now();
        if (cmd.getVisitDate() == null || cmd.getVisitDate().isBefore(today) || cmd.getVisitDate().isAfter(today.plusDays(MAX_AHEAD_DAYS))) {
            throw new BizException(ErrorCode.PARAM_INVALID, "看房日期需在今天到 " + today.plusDays(MAX_AHEAD_DAYS) + " 之间");
        }
        if (cmd.getPartySize() == null || cmd.getPartySize() < 1 || cmd.getPartySize() > 6) {
            throw new BizException(ErrorCode.PARAM_INVALID, "随行人数需 1~6 人");
        }
        if (cmd.getContactPhone() == null || !cmd.getContactPhone().matches("^1[3-9]\\d{9}$")) {
            throw new BizException(ErrorCode.PARAM_INVALID, "联系电话格式不正确");
        }
        boolean duplicate = appointments.appointments(userId, 50).stream()
                .anyMatch(a -> a.getVisitDate().equals(cmd.getVisitDate()) && a.status().occupies());
        if (duplicate) {
            throw new BizException(ErrorCode.DATE_CONFLICT, "同一天已有未完成的预约，可先改期或取消");   // AC-51
        }
        int used = appointments.slotUsed(cmd.getProjectId(), cmd.getVisitDate(), normalizeSlot(cmd.getVisitSlot()));
        int capacity = capacityOf(cmd.getProjectId(), cmd.getVisitDate(), normalizeSlot(cmd.getVisitSlot()));
        if (used >= capacity) {
            throw new BizException(ErrorCode.SLOT_FULL, "该时段已约满（" + used + "/" + capacity + "），请换时段");
        }

        cmd.setUserId(userId);
        cmd.setStatus(AppointmentStatus.PENDING.name());
        cmd.setVisitSlot(normalizeSlot(cmd.getVisitSlot()));
        if (cmd.getPartySize() == null) {
            cmd.setPartySize(2);
        }
        appointments.insertAppointment(cmd);
        appointments.insertMessage(consultantOf(cmd), "APPOINT_NEW", "新的看房预约",
                MaskUtil.mobile(cmd.getContactPhone()) + " 申请 " + cmd.getVisitDate() + " " + cmd.getVisitSlot(), String.valueOf(cmd.getId()));
        log.info("预约已创建 id={} user={} {} {}", cmd.getId(), userId, cmd.getVisitDate(), cmd.getVisitSlot());   // 审计：T-110 统一切面
        return cmd;
    }

    /** 客户与顾问看到的不同视图：顾问能看到自己名下的，客户只看自己的；手机号一律脱敏 */
    public List<Map<String, Object>> listFor(long me) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Appointment a : appointments.appointments(me, 100)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("visitDate", String.valueOf(a.getVisitDate()));
            m.put("visitSlot", a.getVisitSlot());
            m.put("projectName", a.getProjectName());
            m.put("houseTypeName", a.getHouseTypeName());
            m.put("intentionNo", a.getIntentionNo());
            m.put("partySize", a.getPartySize());
            m.put("status", a.getStatus());
            m.put("statusName", a.status().label());
            m.put("contactPhone", MaskUtil.mobile(a.getContactPhone()));           // FR-102 / AC-57
            m.put("customerRole", Long.valueOf(me).equals(a.getUserId()) ? "customer" : "consultant");
            out.add(m);
        }
        return out;
    }

    @Transactional
    public void changeStatus(long id, String target, String remark, long actor) {
        Appointment a = appointments.appointment(id);
        if (a == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "预约不存在");
        }
        AppointmentStatus to = AppointmentStatus.of(target);
        if (!Long.valueOf(actor).equals(a.getUserId()) && !Long.valueOf(actor).equals(a.getConsultantId())) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能处理自己的预约");            // AC-54
        }
        boolean isCustomer = Long.valueOf(actor).equals(a.getUserId());
        // 客户自助只能取消；确认/到场/完成属顾问动作（权限即业务规则，不靠前端隐藏按钮）
        if (isCustomer && to != AppointmentStatus.CANCELED) {
            throw new BizException(ErrorCode.FORBIDDEN, "该操作需由顾问执行");
        }
        a.status().requireNext(to);                                                     // AC-52
        if (appointments.updateAppointmentStatus(id, a.getStatus(), to.name(), remark, actor) != 1) {
            throw new BizException(ErrorCode.APPOINT_STATUS, "状态已被他人变更，请刷新后重试");
        }
        appointments.insertMessage(a.getUserId(), "APPOINT_AUDIT", "预约状态更新",
                a.getVisitDate() + " " + a.getVisitSlot() + " → " + to.label(), String.valueOf(id));
    }

    /** 提醒任务（FR-97 / AC-53）。每 10 分钟；多实例下靠条件更新幂等。 */
    @Scheduled(fixedDelay = 600_000)
    @Transactional
    public int remind() {
        int n = 0;
        for (Appointment a : appointments.toRemind()) {
            boolean oneDayAhead = a.getVisitDate().isEqual(LocalDate.now().plusDays(1));
            String title = oneDayAhead ? "明日看房提醒" : "看房时间临近";
            String content = String.format("%s %s · %s，请提前到达", oneDayAhead ? a.getVisitDate().plusDays(-1) : LocalDate.now(),
                    a.getVisitSlot(), a.getProjectName() == null ? "楼盘" : a.getProjectName());
            appointments.insertMessage(a.getUserId(), oneDayAhead ? "APPOINT_1D" : "APPOINT_2H", title, content, String.valueOf(a.getId()));
            Long consultant = a.getConsultantId();
            if (consultant != null) {
                appointments.insertMessage(consultant, "APPOINT_REMIND", "带看准备",
                        MaskUtil.mobile(a.getContactPhone()) + " " + a.getVisitSlot() + " " + a.getHouseTypeName(), String.valueOf(a.getId()));
            }
            n += oneDayAhead ? appointments.markNotified(a.getId()) : appointments.markReminded2h(a.getId());
        }
        if (n > 0) {
            log.info("预约提醒已发送 {} 条", n);
        }
        return n;
    }

    private int capacityOf(long projectId, LocalDate date, String slot) {
        return appointments.slotView(projectId, date, date).stream()
                .filter(r -> slot.equals(String.valueOf(r.get("slot"))))
                .map(r -> intValue(r.get("capacity"), DEFAULT_CAPACITY))
                .findFirst().orElse(DEFAULT_CAPACITY);
    }

    private Long consultantOf(Appointment cmd) {
        return cmd.getConsultantId() == null ? props.getDemoConsultantId() : cmd.getConsultantId();
    }

    private static String normalizeSlot(String slot) {
        return slot == null || slot.isBlank() ? "10:00-11:00" : slot.trim();
    }

    private static int intValue(Object o, int def) {
        return o instanceof Number num ? num.intValue() : def;
    }
}
