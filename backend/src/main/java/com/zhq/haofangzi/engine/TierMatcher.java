package com.zhq.haofangzi.engine;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 分档匹配：有序列表取<b>第一个</b>命中档（数值档左闭右开，边界值归入高档方向由 tier 顺序保证）。
 * 抽成独立组件的原因：它是打分可复现的关键（AC-05 等级边界 TC-E-05），需要被单独覆盖。
 */
@Component
public class TierMatcher {

    public Optional<RuleSetView.Tier> match(double value, List<RuleSetView.Tier> tiers) {
        if (tiers == null || tiers.isEmpty()) {
            return Optional.empty();
        }
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return Optional.empty();      // 绝不把 NaN 落进任何档（TC-E-04）
        }
        return tiers.stream().filter(t -> t.hit(value)).findFirst();
    }
}
