package com.zhq.haofangzi.domain.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

/**
 * 评估域传输对象（一个文件承载"一条用例的所有入出参"，便于契约对照；
 * 对应 openapi 的 EvaluateCmd / EvaluationResultVO）。
 */
public final class EvaluationDto {

    private EvaluationDto() {}

    /** 入参：POST /api/evaluations（FR-50） */
    @Data
    public static class Cmd {
        @NotNull(message = "户型必填")
        private Long houseTypeId;
        /** 传入则纳入楼层/遮挡/单价修正（AC-33 语义） */
        private Long houseId;
        /** GENERAL/FAMILY_3/FAMILY_4/MULTI_GEN/SENIOR_COUPLE/INVESTOR */
        private String templateCode = "GENERAL";
        /** true 时不落库（游客预览，FR-09） */
        private boolean preview;
    }

    /** 出参：评分结果（含 7 维 + 明细 + 建议，FR-52；同时作为 detail_json 快照结构） */
    @Data
    public static class Result {
        private Long evaluationId;
        private Long houseTypeId;
        private String houseTypeName;
        private Double gfa;
        private Long houseId;
        private String setVersion;
        private double total;
        /** 优/良/中/差 */
        private String level;
        private List<Dim> dimensions;
        private List<Metric> metrics;
        private List<Suggestion> suggestions;
        private int missingCount;
        /** 固定免责文案（FR-121），前端与 PDF 共用 */
        private String disclaimer = "评分基于规则引擎与样例参数，仅供选房参考，不构成合规或投资建议。";
    }

    @Data
    public static class Dim {
        private String code;
        private String name;
        private double weight;
        /** null 表示该维度指标全部数据不足（AC-31） */
        private Double score;
        private Double contribution;
    }

    @Data
    public static class Metric {
        private String dimensionCode;
        private String metricCode;
        private String metricName;
        private String value;
        private String grade;
        private double score;
        private double internalWeight;
        /** 证据：指标值 + 判定表达式 + 输入快照（NFR-11） */
        private String evidence;
        /** 依据来源（规范条文/设计常识） */
        private String basis;
        private boolean missing;
        private List<String> sourceFields;
    }

    @Data
    public static class Suggestion {
        private String dimension;
        private String metric;
        private String text;
        /** 提升潜力 = 维度权重 × 维度内权重 × (100 − 指标分) */
        private double potential;
    }
}
