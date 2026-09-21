package com.zhq.haofangzi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zhq.haofangzi.common.BizException;
import com.zhq.haofangzi.common.ErrorCode;
import com.zhq.haofangzi.config.HfProperties;
import com.zhq.haofangzi.domain.dto.EvaluationDto;
import com.zhq.haofangzi.domain.entity.Building;
import com.zhq.haofangzi.domain.entity.Evaluation;
import com.zhq.haofangzi.domain.entity.House;
import com.zhq.haofangzi.domain.entity.HouseType;
import com.zhq.haofangzi.domain.entity.Project;
import com.zhq.haofangzi.domain.entity.Room;
import com.zhq.haofangzi.domain.entity.UserBrief;
import com.zhq.haofangzi.engine.MetricContext;
import com.zhq.haofangzi.engine.RuleRepository;
import com.zhq.haofangzi.engine.RuleSetView;
import com.zhq.haofangzi.engine.ScoreAssembler;
import com.zhq.haofangzi.engine.ScoringEngine;
import com.zhq.haofangzi.mapper.HfMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 评估用例编排（M4 外层）：装载上下文 → 调引擎 → 写快照 / 读快照。
 *
 * <p>关键设计：
 * <ul>
 *   <li>缓存键包含 {@code setVersion + template}，发布新版本自然失效（FR-61 / AC-35）；</li>
 *   <li>结果对象与 {@code detail_json} 同一来源，保证重开历史与当时一致（AC-32）；</li>
 *   <li>预览态（游客/试算）不写库（FR-09）。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationService {

    private final HfMapper hf;
    private final ScoringEngine engine;
    private final RuleRepository rules;
    private final HfProperties props;
    private final ObjectMapper objectMapper;

    private final Cache<String, EvaluationDto.Result> cache = Caffeine.newBuilder()
            .maximumSize(2000).expireAfterWrite(Duration.ofMinutes(10)).build();

    @Transactional
    public EvaluationDto.Result evaluate(EvaluationDto.Cmd cmd, Long userId) {
        HouseType ht = hf.houseType(cmd.getHouseTypeId());
        if (ht == null) {
            throw new BizException(ErrorCode.HOUSE_TYPE_NOT_FOUND, "户型不存在或已下架");
        }
        House house = cmd.getHouseId() == null ? null : hf.house(cmd.getHouseId());
        if (cmd.getHouseId() != null && house == null) {
            throw new BizException(ErrorCode.HOUSE_NOT_FOUND, "房源不存在");
        }
        RuleSetView set = rules.active();
        ScoringEngine.CrowdTemplate template = ScoringEngine.CrowdTemplate.of(cmd.getTemplateCode());
        String key = cacheKey(cmd, set.version(), template);
        EvaluationDto.Result cached = cache.get(key, k -> compute(ht, house, set, template, userId));

        EvaluationDto.Result result = copyWithMeta(cached);
        if (!cmd.isPreview() && userId != null) {
            Evaluation e = persist(ht, house, set, template, result, userId);
            result.setEvaluationId(e.getId() == null ? 0L : e.getId());
        }
        return result;
    }

    /** 读快照（不重算）：AC-32 历史复现 */
    public EvaluationDto.Result readSnapshot(long evaluationId, Long requester) {
        Evaluation e = hf.evaluation(evaluationId);
        if (e == null) {
            throw new BizException(ErrorCode.HOUSE_TYPE_NOT_FOUND, "评测记录不存在");
        }
        if (e.getUserId() != null && !e.getUserId().equals(requester)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权查看他人的评测记录");
        }
        try {
            return objectMapper.readValue(e.getDetailJson(), EvaluationDto.Result.class);
        } catch (Exception ex) {
            log.error("快照解析失败 evaluationId={}", evaluationId, ex);
            throw new BizException(ErrorCode.INTERNAL, "评测快照解析失败，请联系管理员");
        }
    }

    private EvaluationDto.Result compute(HouseType ht, House house, RuleSetView set,
                                         ScoringEngine.CrowdTemplate template, Long userId) {
        MetricContext ctx = loadContext(ht, house, userId);
        ScoreAssembler.Outcome outcome = engine.score(ctx, set, template.weights());
        return toDto(ht, house, set, template, outcome);
    }

    private MetricContext loadContext(HouseType ht, House house, Long userId) {
        List<Room> rooms = hf.rooms(ht.getId());
        Building b = house == null || house.getBuildingId() == null ? null : hf.building(house.getBuildingId());
        Project p = hf.project(ht.getProjectId());
        MetricContext.Profile profile = null;
        if (userId != null) {
            UserBrief u = hf.userBrief(userId);
            if (u != null && u.getBudgetMin() != null && u.getBudgetMax() != null) {
                profile = new MetricContext.Profile(u.getBudgetMin().longValue(), u.getBudgetMax().longValue(),
                        u.getFamilyStructure(), u.getMustRooms());
            }
        }
        return new MetricContext(ht, rooms, house, b, p, profile);
    }

    private EvaluationDto.Result toDto(HouseType ht, House house, RuleSetView set,
                                       ScoringEngine.CrowdTemplate template, ScoreAssembler.Outcome o) {
        EvaluationDto.Result r = new EvaluationDto.Result();
        r.setHouseTypeId(ht.getId());
        r.setHouseTypeName(ht.getName());
        r.setHouseId(house == null ? null : house.getId());
        r.setSetVersion(set.version() + (template == ScoringEngine.CrowdTemplate.GENERAL ? "" : "/" + template));
        r.setTotal(o.total());
        r.setLevel(o.level());
        r.setMissingCount(o.missingCount());
        r.setDimensions(o.dimensions().stream().map(d -> {
            EvaluationDto.Dim x = new EvaluationDto.Dim();
            x.setCode(d.code());
            x.setName(d.name());
            x.setWeight(d.weight());
            x.setScore(d.score());
            x.setContribution(d.contrib());
            return x;
        }).toList());
        r.setMetrics(o.metrics().stream().map(m -> {
            EvaluationDto.Metric x = new EvaluationDto.Metric();
            x.setMetricCode(m.metricCode());
            x.setMetricName(m.metricName());
            x.setValue(m.value());
            x.setGrade(m.grade());
            x.setScore(m.score());
            x.setInternalWeight(m.internalWeight());
            x.setEvidence(m.evidence());
            x.setBasis(m.basis());
            x.setMissing(m.missing());
            x.setSourceFields(m.sourceFields());
            return x;
        }).toList());
        r.setSuggestions(o.suggestions().stream().map(s -> {
            EvaluationDto.Suggestion x = new EvaluationDto.Suggestion();
            x.setDimension(s.dimension());
            x.setMetric(s.metric());
            x.setText(s.text());
            x.setPotential(s.potential());
            return x;
        }).toList());
        return r;
    }

    private Evaluation persist(HouseType ht, House house, RuleSetView set, ScoringEngine.CrowdTemplate template,
                               EvaluationDto.Result result, Long userId) {
        Evaluation e = new Evaluation();
        e.setUserId(userId);
        e.setHouseTypeId(ht.getId());
        e.setHouseId(house == null ? null : house.getId());
        e.setSetId(set.setId());
        e.setSetVersion(set.version());
        e.setTemplateCode(template.name());
        e.setTotalScore(BigDecimal.valueOf(result.getTotal()));
        e.setLevel(result.getLevel());
        e.setMissingCount(result.getMissingCount());
        try {
            e.setDetailJson(objectMapper.writeValueAsString(result));
        } catch (Exception ex) {
            throw new BizException(ErrorCode.INTERNAL, "评分快照序列化失败");
        }
        hf.insertEvaluation(e);
        return e;
    }

    private String cacheKey(EvaluationDto.Cmd cmd, String version, ScoringEngine.CrowdTemplate t) {
        try {
            String raw = cmd.getHouseTypeId() + "|" + cmd.getHouseId() + "|" + version + "|" + t;
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8)))
                    .substring(0, 32);
        } catch (Exception e) {
            return rawKey(cmd, version, t);
        }
    }

    private String rawKey(EvaluationDto.Cmd cmd, String version, ScoringEngine.CrowdTemplate t) {
        return cmd.getHouseTypeId() + version + t.name();
    }

    /** 缓存对象必须"每次返回副本"，否则调用方修改会污染缓存 */
    private EvaluationDto.Result copyWithMeta(EvaluationDto.Result src) {
        try {
            return objectMapper.readValue(objectMapper.writeValueAsBytes(src), EvaluationDto.Result.class);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL, "结果复制失败");
        }
    }

    public List<Evaluation> history(long houseTypeId, int limit) {
        return hf.evaluations(houseTypeId, Math.min(limit, 50));
    }

    public String activeRuleVersion() {
        return rules.active().version();
    }
}
