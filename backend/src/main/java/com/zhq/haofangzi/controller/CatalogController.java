package com.zhq.haofangzi.controller;

import com.zhq.haofangzi.common.ApiResponse;
import com.zhq.haofangzi.common.BizException;
import com.zhq.haofangzi.common.ErrorCode;
import com.zhq.haofangzi.common.PageResult;
import com.zhq.haofangzi.domain.entity.Evaluation;
import com.zhq.haofangzi.domain.entity.HouseType;
import com.zhq.haofangzi.domain.entity.Room;
import com.zhq.haofangzi.mapper.CatalogMapper;
import com.zhq.haofangzi.mapper.EvalMapper;
import com.zhq.haofangzi.service.EvaluationService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 目录检索接口（M2 户型 + M3 房源查询侧）。游客可访问（FR-09），列表强制分页（NFR-06）。
 * 详情页返回体即 2D/3D 的几何数据源，须通过 {@code specs/002/.../geometry.schema.json} 校验。
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CatalogController {

    private static final int MAX_SIZE = 100;

    private final CatalogMapper catalog;
    private final EvalMapper evals;
    private final EvaluationService evaluationService;

    @GetMapping("/house-types")
    public ApiResponse<PageResult<Map<String, Object>>> houseTypes(
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Integer rooms,
            @RequestParam(required = false) BigDecimal minArea,
            @RequestParam(required = false) BigDecimal maxArea,
            @RequestParam(required = false) String orientation,
            @RequestParam(required = false) BigDecimal maxTotalPrice,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (size < 1 || size > MAX_SIZE) {
            throw new BizException(ErrorCode.PARAM_INVALID, "size 必须在 1~" + MAX_SIZE + "（默认 20）");
        }
        List<HouseType> list = catalog.houseTypes(projectId, rooms, minArea, maxArea, orientation, maxTotalPrice,
                (page - 1) * size, size);
        long total = catalog.houseTypeCount(projectId, rooms, minArea, maxArea, orientation, maxTotalPrice);
        List<Map<String, Object>> records = list.stream().map(this::card).toList();
        return ApiResponse.ok(PageResult.of(records, total, page, size));
    }

    @GetMapping("/house-types/{id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable long id) {
        HouseType ht = catalog.houseType(id);
        if (ht == null) {
            throw new BizException(ErrorCode.HOUSE_TYPE_NOT_FOUND, "户型不存在");
        }
        List<Room> rooms = catalog.rooms(id);
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();   // 允许 null 值（Map.of 不接受 null）
        m.put("id", ht.getId());
        m.put("projectId", ht.getProjectId());
        m.put("code", ht.getCode());
        m.put("name", ht.getName());
        m.put("orientation", ht.getOrientation());
        m.put("bay", ht.getBay());
        m.put("depth", ht.getDepth());
        m.put("ceiling", ht.getCeilingHeight());
        m.put("gfa", ht.getGfa());
        m.put("privateArea", ht.getPrivateArea());
        m.put("roomCount", ht.getRooms());
        m.put("priceRef", ht.getPriceRef());
        m.put("outline", ht.getOutlineJson() == null ? "[]" : ht.getOutlineJson());
        m.put("rooms", rooms);
        m.put("planSvgUrl", ht.getPlanSvgUrl());
        m.put("modelGlbUrl", ht.getModelGlbUrl());
        m.put("panoUrl", ht.getPanoUrl());
        m.put("ext", ht.getExtJson());
        // rooms 面积和与 privateArea 的偏差供前端警告（AC-10 ≤3%，DD-F05 校验同源）
        java.math.BigDecimal sum = rooms.stream().map(Room::getArea)
                .filter(java.util.Objects::nonNull).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        m.put("roomsAreaSum", sum);
        return ApiResponse.ok(m);
    }

    @GetMapping("/houses")
    public ApiResponse<PageResult<Map<String, Object>>> houses(
            @RequestParam(required = false) Long houseTypeId,
            @RequestParam(required = false) Long buildingId,
            @RequestParam(required = false) String saleStatus,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (size < 1 || size > MAX_SIZE) {
            throw new BizException(ErrorCode.PARAM_INVALID, "size 必须在 1~" + MAX_SIZE + "（默认 20）");
        }
        List<Map<String, Object>> records = catalog.houses(houseTypeId, buildingId, saleStatus, (page - 1) * size, size);
        long total = catalog.houseCount(houseTypeId, buildingId, saleStatus);
        return ApiResponse.ok(PageResult.of(records, total, page, size));
    }

    @GetMapping("/house-types/{id}/evaluation/history")
    public ApiResponse<List<Evaluation>> history(@PathVariable long id,
                                                 @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(evaluationService.history(id, limit));
    }

    private Map<String, Object> card(HouseType t) {
        Evaluation latest = evals.latestEvaluation(t.getId(), "GENERAL");
        java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", t.getId());
        m.put("code", t.getCode());
        m.put("name", t.getName());
        m.put("gfa", t.getGfa());
        m.put("rooms", t.getRooms());
        m.put("orientation", t.getOrientation());
        m.put("priceRef", t.getPriceRef());
        m.put("latestScore", latest == null ? null : latest.getTotalScore());
        m.put("latestLevel", latest == null ? "未评估" : latest.getLevel());
        m.put("plan", planOf(catalog.rooms(t.getId())));
        return m;
    }

    /** 列表封面只用房间外接矩形，和详情 2D 图纸同源。 */
    private List<Map<String, Object>> planOf(List<Room> rooms) {
        List<Map<String, Object>> plan = new java.util.ArrayList<>();
        for (Room r : rooms) {
            if (r.getW() == null || r.getH() == null) continue;
            java.util.Map<String, Object> box = new java.util.LinkedHashMap<>();
            box.put("x", r.getX());
            box.put("y", r.getY());
            box.put("w", r.getW());
            box.put("h", r.getH());
            box.put("category", r.getCategory());
            plan.add(box);
        }
        return plan;
    }
}
