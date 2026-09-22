package com.zhq.haofangzi.controller;

import com.zhq.haofangzi.common.ApiResponse;
import com.zhq.haofangzi.service.AppointmentService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** OpenAPI {@code GET /appointment-slots} 与预约控制器分路径，避免与 /appointments/{id} 冲突 */
@RestController
@RequiredArgsConstructor
public class AppointmentSlotController {

    private final AppointmentService service;

    @GetMapping("/api/appointment-slots")
    public ApiResponse<List<Map<String, Object>>> slots(@RequestParam(defaultValue = "1") long projectId,
                                                        @RequestParam(defaultValue = "7") int days) {
        return ApiResponse.ok(service.slots(projectId, days));
    }
}
