package com.zhq.haofangzi.controller;

import com.zhq.haofangzi.common.ApiResponse;
import com.zhq.haofangzi.common.BizException;
import com.zhq.haofangzi.common.ErrorCode;
import com.zhq.haofangzi.domain.entity.Appointment;
import com.zhq.haofangzi.service.AppointmentService;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 预约看房接口（M6，spec 006）。前端契约见 openapi.yaml tag appointment；
 * 页面：{@code views/AppointmentsView.vue}（我的预约 + 新建向导 + 时段占用）。
 */
@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

    private final AppointmentService service;

    /** 我的预约（客户看到自己的；顾问 additionally 看到名下客户的，FR-98） */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> mine(Principal me) {
        return ApiResponse.ok(service.listFor(uid(me)));
    }

    /** 可选时段与余量（FR-95；无配置时按默认容量 6 计算） */
    @GetMapping("/slots")
    public ApiResponse<List<Map<String, Object>>> slots(@RequestParam long projectId,
                                                        @RequestParam(defaultValue = "7") int days) {
        return ApiResponse.ok(service.slots(projectId, days));
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> create(@RequestBody Appointment body, Principal me) {
        Appointment saved = service.create(uid(me), body);
        return ApiResponse.ok(Map.of("id", saved.getId() == null ? 0L : saved.getId(),
                "status", saved.getStatus(), "visitDate", String.valueOf(saved.getVisitDate()),
                "visitSlot", saved.getVisitSlot()));
    }

    /** 状态变更（顾问：CONFIRMED/ARRIVED/COMPLETED；客户：CANCELED），非法流转 40931 */
    @PostMapping("/{id}/status")
    public ApiResponse<Void> status(@PathVariable long id, @RequestBody Map<String, String> body, Principal me) {
        service.changeStatus(id, body.get("target"), body.get("remark"), uid(me));
        return ApiResponse.ok();
    }

    private static long uid(Principal me) {
        if (me == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");                 // FR-90
        }
        return Long.parseLong(me.getName());
    }

    /** 便于本地自测：仅允许未来 14 天内的日期（服务层同样校验，这里挡明显错误入参） */
    static boolean dateInRange(LocalDate d) {
        LocalDate today = LocalDate.now();
        return d != null && !d.isBefore(today) && d.isBefore(today.plusDays(15));
    }
}
