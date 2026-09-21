package com.zhq.haofangzi.controller;

import com.zhq.haofangzi.common.ApiResponse;
import com.zhq.haofangzi.domain.dto.EvaluationDto;
import com.zhq.haofangzi.engine.ScoringEngine;
import com.zhq.haofangzi.service.EvaluationService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 评估接口（M4）。游客可预览（preview=true 不落库，FR-09）；登录用户默认落快照（FR-51）。
 * 规则维护接口在 {@code AdminRuleController}（T-079）。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationService evaluationService;

    @PostMapping("/evaluations")
    public ApiResponse<EvaluationDto.Result> evaluate(@Valid @RequestBody EvaluationDto.Cmd cmd, Principal me) {
        Long userId = me == null ? null : Long.valueOf(me.getName());
        return ApiResponse.ok(evaluationService.evaluate(cmd, userId));
    }

    @GetMapping("/evaluations/{id}")
    public ApiResponse<EvaluationDto.Result> snapshot(@PathVariable long id, Principal me) {
        return ApiResponse.ok(evaluationService.readSnapshot(id, me == null ? -1 : Long.valueOf(me.getName())));
    }

    @GetMapping("/house-types/{id}/evaluation/latest")
    public ApiResponse<EvaluationDto.Result> latest(@PathVariable long id,
                                                   @RequestParam(defaultValue = "false") boolean preview) {
        EvaluationDto.Cmd cmd = new EvaluationDto.Cmd();
        cmd.setHouseTypeId(id);
        cmd.setPreview(true);                                       // 徽标用只读预览，不写入历史
        return ApiResponse.ok(evaluationService.evaluate(cmd, null));
    }

    /** 人群模板清单（FR-53）；前端下拉数据源 */
    @GetMapping("/evaluation-templates")
    public ApiResponse<List<Map<String, String>>> templates() {
        return ApiResponse.ok(Arrays.stream(ScoringEngine.CrowdTemplate.values())
                .map(t -> Map.of("code", t.name(), "customized",
                        String.valueOf(!t.weights().isEmpty())))
                .toList());
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/admin/rule-sets/version")
    public ApiResponse<String> activeVersion() {
        return ApiResponse.ok(evaluationService.activeRuleVersion());
    }
}
