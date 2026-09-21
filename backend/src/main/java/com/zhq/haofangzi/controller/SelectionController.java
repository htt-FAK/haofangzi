package com.zhq.haofangzi.controller;

import com.zhq.haofangzi.common.ApiResponse;
import com.zhq.haofangzi.domain.dto.SelectionDto;
import com.zhq.haofangzi.service.SelectionService;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 模拟选房接口（M3，spec 003）。全部需登录（FR-09）， Principal 由 JwtAuthFilter 注入 */
@RestController
@RequestMapping("/api/selection")
@RequiredArgsConstructor
public class SelectionController {

    private final SelectionService selectionService;

    /** 锁定房源（返回倒计时与快照；冲突时 code=40910 且 data.alternatives 非空） */
    @PostMapping("/locks")
    public ApiResponse<SelectionDto.LockResult> lock(@Valid @RequestBody SelectionDto.LockCmd cmd, Principal me) {
        return ApiResponse.ok(selectionService.lock(cmd, uid(me)));
    }

    @GetMapping("/locks/current")
    public ApiResponse<SelectionDto.LockResult> current(Principal me) {
        return ApiResponse.ok(selectionService.currentLock(uid(me)));                    // null → 前端隐藏倒计时
    }

    @PostMapping("/locks/{intentionNo}/cancel")
    public ApiResponse<Void> cancel(@PathVariable String intentionNo, Principal me) {
        selectionService.cancel(intentionNo, uid(me));
        return ApiResponse.ok();
    }

    @PostMapping("/locks/{intentionNo}/convert")
    public ApiResponse<SelectionDto.LockResult> convert(@PathVariable String intentionNo, Principal me) {
        return ApiResponse.ok(selectionService.convert(intentionNo, uid(me)));           // FR-37
    }

    @PostMapping("/favorites")
    public ApiResponse<Void> favorite(@RequestBody java.util.Map<String, Object> body, Principal me) {
        selectionService.addFavorite(uid(me), String.valueOf(body.get("targetType")),
                Long.parseLong(String.valueOf(body.get("targetId"))));
        return ApiResponse.ok();
    }

    private static long uid(Principal me) {
        if (me == null) {
            throw new com.zhq.haofangzi.common.BizException(
                    com.zhq.haofangzi.common.ErrorCode.UNAUTHORIZED, "请先登录");
        }
        return Long.parseLong(me.getName());
    }
}
