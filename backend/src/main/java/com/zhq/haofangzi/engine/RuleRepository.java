package com.zhq.haofangzi.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zhq.haofangzi.common.BizException;
import com.zhq.haofangzi.common.ErrorCode;
import com.zhq.haofangzi.mapper.RuleMapper;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Repository;

/**
 * 规则装载与热更新（spec 004 FR-55/58、AC-35；plan §3）。
 *
 * <p>运行时优先读库中 {@code active=1 且 PUBLISHED} 的规则集；库不可用或没有发布版本时回退
 * {@code default-rules.json}。发布后调用 {@link #invalidate()}，下一次评估即读到新版本（FR-58）。
 */
@Slf4j
@Repository
public class RuleRepository {

    private static final String CACHE_KEY = "active";

    private final ObjectMapper mapper;
    private final ResourceLoader resourceLoader;
    private final RuleMapper rules;
    private final RuleSetView fallback;
    private final Cache<String, RuleSetView> cache = Caffeine.newBuilder()
            .maximumSize(8).expireAfterWrite(Duration.ofMinutes(5)).build();

    public RuleRepository(ObjectMapper mapper, ResourceLoader resourceLoader, RuleMapper rules,
                          com.zhq.haofangzi.config.HfProperties props) {
        this.mapper = mapper;
        this.resourceLoader = resourceLoader;
        this.rules = rules;
        this.fallback = loadFromJson(props.getEval().getRuleSource());
    }

    public RuleSetView jsonSeed() {
        return fallback;
    }

    public RuleSetView active() {
        RuleSetView v = cache.get(CACHE_KEY, k -> loadActiveOrFallback());
        if (v != null && !v.weightsValid()) {
            log.error("ACTIVE 规则集权重之和≠1（version={}），仍按现值计算，请检查发布流程", v.version());
        }
        return v;
    }

    /** 按编号装载。编号无效或库中没有该版本时直接失败，不用当前生效规则顶上。 */
    public RuleSetView load(long setId) {
        if (setId <= 0) {
            return active();
        }
        Map<String, Object> row = rules.ruleSet(setId);
        if (row == null || row.get("id") == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "规则集不存在");
        }
        RuleSetView built = assemble(row, rules.rulesOf(setId));
        if (built == null) {
            throw new BizException(ErrorCode.INTERNAL, "规则集无法组装");
        }
        return built;
    }

    private RuleSetView loadActiveOrFallback() {
        try {
            Map<String, Object> row = rules.activeRuleSet();
            if (row != null && row.get("id") != null) {
                long id = ((Number) row.get("id")).longValue();
                RuleSetView built = assemble(row, rules.rulesOf(id));
                if (built != null && built.dims() != null && !built.dims().isEmpty()) {
                    log.info("已装载数据库规则集 id={} version={}", built.setId(), built.version());
                    return built;
                }
            }
        } catch (Exception e) {
            log.warn("库中 ACTIVE 规则不可用，回退 JSON 种子：{}", e.toString());
        }
        return fallback;
    }

    /** 把规则行组装成与 JSON 种子相同的视图。行按维度分组，分档预排序。 */
    RuleSetView assemble(Map<String, Object> header, List<Map<String, Object>> rows) {
        if (header == null || rows == null || rows.isEmpty()) {
            return null;
        }
        Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        Map<String, Double> weights = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String code = str(row.get("dimCode"));
            if (code.isBlank()) {
                continue;
            }
            grouped.computeIfAbsent(code, k -> new ArrayList<>()).add(row);
            names.putIfAbsent(code, str(row.get("dimName")));
            if (row.get("dimWeight") != null) {
                weights.put(code, num(row.get("dimWeight")));
            }
        }
        List<RuleSetView.DimView> dims = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> e : grouped.entrySet()) {
            List<RuleSetView.RuleView> rules = new ArrayList<>();
            for (Map<String, Object> r : e.getValue()) {
                rules.add(new RuleSetView.RuleView(
                        str(r.get("metricCode")), str(r.get("metricName")), str(r.get("unit")),
                        str(r.get("operator")).isBlank() ? "BETWEEN" : str(r.get("operator")),
                        num(r.get("internalWeight")), truthy(r.get("higherIsBetter")),
                        Raw.orderTiers(parseTiers(str(r.get("tierJson")))),
                        str(r.get("basis")), str(r.get("suggestion")), parseFields(str(r.get("sourceFields")))));
            }
            dims.add(new RuleSetView.DimView(e.getKey(), names.getOrDefault(e.getKey(), e.getKey()),
                    weights.getOrDefault(e.getKey(), 0d), rules));
        }
        return new RuleSetView(((Number) header.get("id")).longValue(), str(header.get("name")),
                str(header.get("version")),
                str(header.get("templateCode")).isBlank() ? "GENERAL" : str(header.get("templateCode")),
                dims, RuleSetView.LevelThresholds.defaults());
    }

    private List<RuleSetView.Tier> parseTiers(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return mapper.readValue(json, new TypeReference<List<RuleSetView.Tier>>() {});
        } catch (Exception e) {
            log.warn("分档 JSON 无法解析：{}", e.toString());
            return List.of();
        }
    }

    private List<String> parseFields(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        if (raw.strip().startsWith("[")) {
            try {
                return mapper.readValue(raw, new TypeReference<List<String>>() {});
            } catch (Exception e) {
                return List.of(raw);
            }
        }
        return List.of(raw);
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static double num(Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        if (o == null) {
            return 0d;
        }
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return 0d;
        }
    }

    private static boolean truthy(Object o) {
        if (o instanceof Boolean b) {
            return b;
        }
        if (o instanceof Number n) {
            return n.intValue() != 0;
        }
        return o == null || !"0".equals(String.valueOf(o)) && !"false".equalsIgnoreCase(String.valueOf(o));
    }

    /** 规则发布后调用：下一个请求即读到新版本（AC-35） */
    public void invalidate() {
        cache.invalidateAll();
        log.info("规则缓存已失效，下一次评估将装载最新 PUBLISHED 版本");
    }

    private RuleSetView loadFromJson(String location) {
        try (InputStream in = open(location)) {
            Raw raw = mapper.readValue(in, Raw.class);
            return raw.toView();
        } catch (IOException e) {
            throw new IllegalStateException("默认规则集加载失败：" + location + "（禁止以空规则启动，避免全 0 分）", e);
        }
    }

    private InputStream open(String location) throws IOException {
        Resource r = resourceLoader.getResource(location);
        return r.getInputStream();
    }

    /** JSON 结构 = specs/004-rule-scoring/contracts/scoring-rule.schema.json（CI 中双向校验） */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    record Raw(String name, String version, String status, String templateCode,
               Levels levelThresholds, List<RawDim> dimensions) {

        @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
        record Levels(Double excellent, Double good, Double fair) {
        }

        @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
        record RawDim(String code, String name, Double weight, List<RawRule> rules) {
        }

        @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
        record RawRule(String metricCode, String metricName, String unit, String operator,
                       Double internalWeight, Boolean higherIsBetter, String basis, String suggestion,
                       List<String> sourceFields, List<RuleSetView.Tier> tiers) {
        }

        RuleSetView toView() {
            List<RuleSetView.DimView> dims = dimensions.stream().map(d -> new RuleSetView.DimView(
                    d.code(), d.name(), d.weight() == null ? 0 : d.weight(),
                    d.rules().stream().map(r -> new RuleSetView.RuleView(
                            r.metricCode(), r.metricName(), r.unit(),
                            r.operator() == null ? "BETWEEN" : r.operator(),
                            r.internalWeight() == null ? 0 : r.internalWeight(),
                            r.higherIsBetter() == null || r.higherIsBetter(),
                            orderTiers(r.tiers()),
                            r.basis(), r.suggestion(),
                            r.sourceFields() == null ? List.of() : r.sourceFields())).toList()))
                    .toList();
            RuleSetView.LevelThresholds th = levelThresholds == null ? RuleSetView.LevelThresholds.defaults()
                    : new RuleSetView.LevelThresholds(
                    nz(levelThresholds.excellent(), 85), nz(levelThresholds.good(), 75), nz(levelThresholds.fair(), 60));
            return new RuleSetView(0, name, version, templateCode == null ? "GENERAL" : templateCode, dims, th);
        }

        /** 数值档按 min 降序（高档在前）保证"第一个命中即最优档"；布尔档（min=null）排在其后 */
        private static List<RuleSetView.Tier> orderTiers(List<RuleSetView.Tier> tiers) {
            if (tiers == null) {
                return List.of();
            }
            return tiers.stream()
                    .sorted(java.util.Comparator.comparingDouble(
                            (RuleSetView.Tier t) -> t.min() == null ? -Double.MAX_VALUE : -t.min()))
                    .toList();
        }

        private static double nz(Double v, double def) {
            return v == null ? def : v;
        }
    }
}
