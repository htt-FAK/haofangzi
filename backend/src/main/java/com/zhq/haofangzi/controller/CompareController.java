package com.zhq.haofangzi.controller;

import com.zhq.haofangzi.common.ApiResponse;
import com.zhq.haofangzi.common.BizException;
import com.zhq.haofangzi.common.ErrorCode;
import com.zhq.haofangzi.service.CompareService;
import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 对比报告与只读分享（spec 005；openapi tag report） */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CompareController {

    private final CompareService compare;

    @PostMapping("/compare-reports")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body, Principal me) {
        return ApiResponse.ok(compare.create(idsOf(body), uid(me),
                body.get("summary") instanceof Map<?, ?> s ? String.valueOf(s.get("text"))
                        : body.get("summary") == null ? null : String.valueOf(body.get("summary"))));
    }

    @PostMapping("/compare-reports/{id}/share")
    public ApiResponse<Map<String, Object>> share(@PathVariable long id, Principal me) {
        return ApiResponse.ok(compare.share(id, uid(me)));
    }

    @GetMapping("/share/reports/{token}")
    public ApiResponse<Map<String, Object>> open(@PathVariable String token) {
        return ApiResponse.ok(compare.byToken(token));
    }

    @GetMapping(value = "/share/reports/{token}/print", produces = MediaType.TEXT_HTML_VALUE)
    public String print(@PathVariable String token) {
        return compare.toHtml(token);
    }

    @SuppressWarnings("unchecked")
    private static List<Long> idsOf(Map<String, Object> body) {
        Object raw = body.get("houseTypeIds");
        if (raw == null) {
            raw = body.get("ids");
        }
        List<Long> ids = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Number n) {
                    ids.add(n.longValue());
                }
            }
        }
        return ids;
    }

    private static Long uid(Principal me) {
        if (me == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        return Long.parseLong(me.getName());
    }
}
