package com.zhq.haofangzi.engine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Repository;

/**
 * 规则装载与热更新（spec 004 FR-55/58、AC-35；plan §3）。
 *
 * <p>骨架实现策略：首次启动把 {@code resources/rules/default-rules.json} 解析为视图并缓存；
 * 发布新规则集时调用 {@link #invalidate()}，下一次评估即读到新版本，无需重启（NFR-08）。
 *
 * <p>任务 T-062/T-066 待接入数据库版：从 {@code eval_rule_set(active=1) + eval_rule} 组装同一
 * {@link RuleSetView}（tier_json 用 Jackson 解析并预排序）。视图与来源解耦，因此上层无感。
 */
@Slf4j
@Repository
public class RuleRepository {

    private static final String CACHE_KEY = "active";

    private final ObjectMapper mapper;
    private final ResourceLoader resourceLoader;
    private final RuleSetView fallback;
    private final Cache<String, RuleSetView> cache = Caffeine.newBuilder()
            .maximumSize(8).expireAfterWrite(Duration.ofMinutes(5)).build();

    public RuleRepository(ObjectMapper mapper, ResourceLoader resourceLoader,
                          com.zhq.haofangzi.config.HfProperties props) {
        this.mapper = mapper;
        this.resourceLoader = resourceLoader;
        this.fallback = loadFromJson(props.getEval().getRuleSource());
    }

    public RuleSetView active() {
        RuleSetView v = cache.get(CACHE_KEY, k -> fallback);
        if (!v.weightsValid()) {
            log.error("ACTIVE 规则集权重之和≠1（version={}），仍按现值计算，请检查发布流程", v.version());
        }
        return v;
    }

    /** DRAFT 试算用（FR-60 / docs/04 §4.5） */
    public RuleSetView load(long setId) {
        // TODO(T-066): 按 setId 从库中组装 RuleSetView；当前骨架回退 ACTIVE
        return active();
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
