package com.zhq.haofangzi.controller;

import com.zhq.haofangzi.common.ApiResponse;
import com.zhq.haofangzi.common.BizException;
import com.zhq.haofangzi.common.ErrorCode;
import com.zhq.haofangzi.domain.dto.EvaluationDto;
import com.zhq.haofangzi.service.AdminService;
import com.zhq.haofangzi.service.CompareService;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 管理端。规则与用户仅 ADMIN；销控顾问也可操作。 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService admin;
    private final CompareService compare;

    @GetMapping("/ai-usage")
    public ApiResponse<Map<String, Object>> aiUsage() {
        requireAdmin();
        return ApiResponse.ok(admin.aiUsage());
    }

    @GetMapping("/shares")
    public ApiResponse<List<Map<String, Object>>> shares() {
        return ApiResponse.ok(admin.recentShares());
    }

    @PostMapping("/shares/{id}/visit")
    public ApiResponse<Void> visit(@PathVariable long id, @RequestBody Map<String, Object> body) {
        String note = body.get("note") == null ? "" : String.valueOf(body.get("note"));
        boolean shown = Boolean.TRUE.equals(body.get("shown")) || "true".equals(String.valueOf(body.get("shown")));
        compare.markVisit(id, note, shown);
        return ApiResponse.ok();
    }

    @GetMapping("/rule-sets")
    public ApiResponse<Map<String, Object>> ruleSets() {
        requireAdmin();
        return ApiResponse.ok(admin.ruleSets());
    }

    @PostMapping("/rule-sets/draft")
    public ApiResponse<Map<String, Object>> draft(@RequestBody(required = false) Map<String, String> body) {
        requireAdmin();
        String source = body == null ? "MANUAL" : body.get("source");
        return ApiResponse.ok(admin.copyDraft(source));
    }

    @PutMapping("/rule-sets/{id}/weights")
    public ApiResponse<Void> weights(@PathVariable long id, @RequestBody Map<String, Object> body) {
        requireAdmin();
        admin.updateWeights(id, doubles(body.get("weights")));
        return ApiResponse.ok();
    }

    @PostMapping("/rule-sets/{id}/publish")
    public ApiResponse<Void> publish(@PathVariable long id, @RequestBody(required = false) Map<String, Object> body,
                                    Principal me) {
        requireAdmin();
        boolean confirm = body != null && Boolean.TRUE.equals(body.get("confirm"));
        admin.publish(id, confirm, uid(me));
        return ApiResponse.ok();
    }

    @PostMapping("/rule-sets/trial")
    public ApiResponse<EvaluationDto.Result> trial(@RequestBody Map<String, Object> body, Principal me) {
        requireAdmin();
        Object id = body.get("houseTypeId");
        if (!(id instanceof Number n)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请指定户型");
        }
        return ApiResponse.ok(admin.trial(n.longValue(), uid(me)));
    }

    @GetMapping("/houses")
    public ApiResponse<Map<String, Object>> houses(@RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size,
                                                   @RequestParam(required = false) String status) {
        return ApiResponse.ok(admin.houses(page, size, status));
    }

    @PostMapping("/houses/{id}/status")
    public ApiResponse<Void> houseStatus(@PathVariable long id, @RequestBody Map<String, String> body, Principal me) {
        admin.changeHouseStatus(id, body.get("status"), body.get("reason"), uid(me));
        return ApiResponse.ok();
    }

    @GetMapping("/houses/template")
    public ResponseEntity<byte[]> template() {
        byte[] bytes = admin.houseTemplate();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=house-import.xlsx")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @PostMapping("/houses/import")
    public ApiResponse<Map<String, Object>> importHouses(@RequestParam("file") MultipartFile file, Principal me)
            throws java.io.IOException {
        return ApiResponse.ok(admin.importHouses(file.getInputStream(), uid(me)));
    }

    @GetMapping("/users")
    public ApiResponse<Map<String, Object>> users(@RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        requireAdmin();
        return ApiResponse.ok(admin.users(page, size));
    }

    @PostMapping("/users/{id}/status")
    public ApiResponse<Void> userStatus(@PathVariable long id, @RequestBody Map<String, Object> body, Principal me) {
        requireAdmin();
        int status = body.get("status") instanceof Number n ? n.intValue() : -1;
        admin.userStatus(id, status, uid(me));
        return ApiResponse.ok();
    }

    @PostMapping("/users/{id}/reset-password")
    public ApiResponse<Void> reset(@PathVariable long id, @RequestBody Map<String, String> body, Principal me) {
        requireAdmin();
        admin.resetPassword(id, body.get("password"), uid(me));
        return ApiResponse.ok();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Double> doubles(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "weights 必须是对象");
        }
        Map<String, Double> out = new java.util.LinkedHashMap<>();
        map.forEach((k, v) -> {
            if (v instanceof Number n) {
                out.put(String.valueOf(k), n.doubleValue());
            }
        });
        return out;
    }

    private static void requireAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean admin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!admin) {
            throw new BizException(ErrorCode.FORBIDDEN, "仅管理员可操作");
        }
    }

    private static long uid(Principal me) {
        if (me == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        return Long.parseLong(me.getName());
    }
}
