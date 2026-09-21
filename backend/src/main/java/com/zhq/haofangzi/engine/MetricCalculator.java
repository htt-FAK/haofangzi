package com.zhq.haofangzi.engine;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 评估指标计算器 SPI（spec 004 plan §1；策略模式，新增指标不改主流程 —— OCP）。
 *
 * <p>实现约定（评审红线，详见 docs/04 §4.2）：
 * <ol>
 *   <li>{@link #code()} 必须与规则集里的 {@code metric_code} 一致，且全小写下划线后缀，如 {@code LIGHT_wfa}；</li>
 *   <li>输入缺失或异常时返回 {@link MetricResult#insufficient}，<b>禁止抛异常</b>，禁止返回 NaN/Infinity；</li>
 *   <li>同一 ctx 多次调用结果必须相同（纯函数，便于复现历史分数 AC-32）；</li>
 *   <li>实现类必须写 Javadoc 说明公式与依据条文（NFR-11）。</li>
 * </ol>
 *
 * <p>21 个指标的实现分派见 {@code specs/004-rule-scoring/tasks.md} T-069~T-074；
 * 本骨架内附 4 个代表实现（采光窗地比、通风南北通透、实用得房率、经济预算匹配）。
 */
public interface MetricCalculator {

    String code();

    MetricResult calc(MetricContext ctx);

    /**
     * 注册表：Spring 收集全部实现，供 {@link ScoringEngine} 按 {@code metric_code} 查找。
     * 规则里出现未注册的 code → 该指标记为"数据不足"并告警（稳健性，plan 004 §3）。
     */
    @Component
    class Registry {

        private final Map<String, MetricCalculator> byCode;

        public Registry(List<MetricCalculator> calculators) {
            this.byCode = calculators.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                    c -> c.code().toUpperCase(), c -> c, (a, b) -> {
                        throw new IllegalStateException("指标编码重复：" + a.code());
                    }));
        }

        public Optional<MetricCalculator> find(String code) {
            return code == null ? Optional.empty() : Optional.ofNullable(byCode.get(code.toUpperCase()));
        }

        public java.util.Set<String> codes() {
            return byCode.keySet();
        }
    }
}
