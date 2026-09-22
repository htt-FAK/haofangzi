package com.zhq.haofangzi.service;

import com.alibaba.excel.EasyExcel;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhq.haofangzi.common.BizException;
import com.zhq.haofangzi.common.ErrorCode;
import com.zhq.haofangzi.common.MaskUtil;
import com.zhq.haofangzi.domain.dto.EvaluationDto;
import com.zhq.haofangzi.domain.dto.HouseImportRow;
import com.zhq.haofangzi.domain.entity.House;
import com.zhq.haofangzi.domain.entity.RuleSetRow;
import com.zhq.haofangzi.domain.enums.SaleStatus;
import com.zhq.haofangzi.engine.RuleRepository;
import com.zhq.haofangzi.engine.RuleSetView;
import com.zhq.haofangzi.mapper.AiLogMapper;
import com.zhq.haofangzi.mapper.AuditMapper;
import com.zhq.haofangzi.mapper.CatalogMapper;
import com.zhq.haofangzi.mapper.CompareMapper;
import com.zhq.haofangzi.mapper.RuleMapper;
import com.zhq.haofangzi.mapper.SelectionMapper;
import com.zhq.haofangzi.mapper.UserMapper;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 管理端：规则版本、销控、账号。顾问只能改销控，规则和用户仅管理员。 */
@Service
@RequiredArgsConstructor
public class AdminService {

    public Map<String, Object> aiUsage() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("summary", callLogs.summary());
        m.put("recent", callLogs.recent());
        return m;
    }

    public List<Map<String, Object>> recentShares() {
        return reports.recentShares();
    }

    private static final Pattern STRONG = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,}$");

    private final RuleMapper ruleSets;
    private final CatalogMapper catalog;
    private final SelectionMapper houses;
    private final UserMapper users;
    private final AuditMapper audit;
    private final AiLogMapper callLogs;
    private final CompareMapper reports;
    private final RuleRepository rules;
    private final EvaluationService evaluation;
    private final PasswordEncoder passwords;
    private final ObjectMapper mapper;

    public Map<String, Object> ruleSets() {
        List<Map<String, Object>> rows = ruleSets.ruleSets();
        List<Map<String, Object>> list = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            list.add(seedCard());
        } else {
            for (Map<String, Object> row : rows) {
                Map<String, Object> item = new LinkedHashMap<>(row);
                long id = ((Number) row.get("id")).longValue();
                item.put("weights", ruleSets.ruleSetWeights(id));
                item.put("weightSum", sumWeights(ruleSets.ruleSetWeights(id)));
                list.add(item);
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", list);
        data.put("activeVersion", rules.active().version());
        return data;
    }

    @Transactional
    public Map<String, Object> copyDraft(String source) {
        String src = source == null || source.isBlank() ? "MANUAL" : source.strip().toUpperCase();
        if (!"MANUAL".equals(src) && !"AI".equals(src)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "来源只能是 MANUAL 或 AI");
        }
        RuleSetView view = rules.active();
        RuleSetRow row = new RuleSetRow();
        row.setName(view.name() == null ? "评估规则" : view.name());
        row.setVersion(view.version() + "-d" + (System.currentTimeMillis() % 100000));
        row.setSource(src);
        row.setConfirmed("AI".equals(src) ? 0 : 1);
        row.setTemplateCode(view.templateCode());
        ruleSets.insertRuleSet(row);
        long setId = row.getId();
        int order = 0;
        for (RuleSetView.DimView dim : view.dims()) {
            long dimId = dimensionId(dim.code());
            ruleSets.insertRuleSetDim(setId, dimId, dim.weight());
            for (RuleSetView.RuleView rule : dim.rules()) {
                ruleSets.insertRule(setId, dimId, rule.metricCode(), rule.metricName(), write(rule.sourceFields()),
                        rule.operator(), rule.unit(), rule.internalWeight(), rule.higherIsBetter() ? 1 : 0,
                        write(rule.tiers()), rule.basis(), rule.suggestion(), order++);
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", setId);
        m.put("version", row.getVersion());
        m.put("source", src);
        m.put("status", "DRAFT");
        return m;
    }

    @Transactional
    public void updateWeights(long setId, Map<String, Double> weights) {
        Map<String, Object> set = requireSet(setId);
        if ("PUBLISHED".equals(String.valueOf(set.get("status")))) {
            throw new BizException(ErrorCode.RULE_PUBLISHED_READONLY, "已发布规则只读，请先复制为草案");
        }
        if (weights == null || weights.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请提交维度权重");
        }
        double sum = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (Math.abs(sum - 1.0d) > 0.001d) {
            throw new BizException(ErrorCode.RULE_WEIGHT, "维度权重之和必须为 1", Map.of("sum", sum));
        }
        weights.forEach((code, w) -> ruleSets.updateDimWeight(setId, code, w));
        rules.invalidate();
    }

    @Transactional
    public void publish(long setId, boolean confirm, long actor) {
        Map<String, Object> set = requireSet(setId);
        boolean ai = "AI".equals(String.valueOf(set.get("source")));
        int confirmed = set.get("confirmed") instanceof Number n ? n.intValue() : 0;
        if (ai && confirmed == 0 && !confirm) {
            throw new BizException(ErrorCode.AI_DRAFT_NOT_CONFIRMED, "AI 草案必须人工确认后才能发布");
        }
        double sum = sumWeights(ruleSets.ruleSetWeights(setId));
        if (Math.abs(sum - 1.0d) > 0.001d) {
            throw new BizException(ErrorCode.RULE_WEIGHT, "维度权重之和必须为 1", Map.of("sum", sum));
        }
        ruleSets.clearActiveRuleSets();
        ruleSets.publishRuleSet(setId);
        rules.invalidate();
        audit.insertAudit(actor, "PUBLISH_RULE", "RULE_SET", String.valueOf(setId),
                "{\"version\":\"" + set.get("version") + "\"}");
    }

    public EvaluationDto.Result trial(long houseTypeId, Long userId) {
        EvaluationDto.Cmd cmd = new EvaluationDto.Cmd();
        cmd.setHouseTypeId(houseTypeId);
        cmd.setPreview(true);
        return evaluation.evaluate(cmd, userId);
    }

    public Map<String, Object> houses(int page, int size, String status) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 50);
        String st = status == null || status.isBlank() ? null : status;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", catalog.houseCount(null, null, st));
        m.put("items", catalog.houses(null, null, st, (p - 1) * s, s));
        return m;
    }

    @Transactional
    public void changeHouseStatus(long houseId, String to, String reason, long actor) {
        if (reason == null || reason.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写变更原因");
        }
        House house = catalog.house(houseId);
        if (house == null) {
            throw new BizException(ErrorCode.HOUSE_NOT_FOUND, "房源不存在");
        }
        SaleStatus next;
        try {
            next = SaleStatus.valueOf(to);
        } catch (Exception e) {
            throw new BizException(ErrorCode.PARAM_INVALID, "状态不合法");
        }
        if (!house.status().canNext(next)) {
            throw new BizException(ErrorCode.NOT_SELECTABLE, "不允许从 " + house.getSaleStatus() + " 改为 " + to);
        }
        if (houses.updateHouseStatusIf(houseId, house.getSaleStatus(), next.name(), null) != 1) {
            throw new BizException(ErrorCode.NOT_SELECTABLE, "房源状态已变化，请刷新");
        }
        audit.insertAudit(actor, "HOUSE_STATUS", "HOUSE", String.valueOf(houseId),
                "{\"from\":\"" + house.getSaleStatus() + "\",\"to\":\"" + next.name() + "\",\"reason\":\""
                        + reason.replace("\"", "") + "\"}");
    }

    public byte[] houseTemplate() {
        HouseImportRow sample = new HouseImportRow();
        sample.setBuildingId(1L);
        sample.setHouseTypeId(1L);
        sample.setFloorNo(8);
        sample.setRoomNo("01");
        sample.setArea(new BigDecimal("98.00"));
        sample.setUnitPrice(new BigDecimal("9500"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out, HouseImportRow.class).sheet("销控").doWrite(List.of(sample));
        return out.toByteArray();
    }

    @Transactional
    public Map<String, Object> importHouses(InputStream in, long actor) {
        List<HouseImportRow> rows = EasyExcel.read(in).head(HouseImportRow.class).sheet().doReadSync();
        List<Map<String, Object>> errors = new ArrayList<>();
        int ok = 0;
        int line = 1;
        for (HouseImportRow row : rows) {
            line++;
            String err = validate(row);
            if (err != null) {
                errors.add(Map.of("line", line, "message", err));
                continue;
            }
            BigDecimal total = row.getArea().multiply(row.getUnitPrice()).setScale(2, RoundingMode.HALF_UP);
            try {
                catalog.insertHouse(row.getBuildingId(), row.getHouseTypeId(), row.getFloorNo(), row.getRoomNo().strip(),
                        row.getArea(), row.getUnitPrice(), total);
                ok++;
            } catch (Exception e) {
                errors.add(Map.of("line", line, "message", "写入失败，可能是房号重复或楼栋/户型不存在"));
            }
        }
        audit.insertAudit(actor, "IMPORT_HOUSE", "HOUSE", String.valueOf(ok),
                "{\"ok\":" + ok + ",\"errors\":" + errors.size() + "}");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("imported", ok);
        m.put("errors", errors);
        return m;
    }

    public Map<String, Object> users(int page, int size) {
        int p = Math.max(page, 1);
        int s = Math.min(Math.max(size, 1), 50);
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : users.users((p - 1) * s, s)) {
            Map<String, Object> item = new LinkedHashMap<>(row);
            item.put("phone", MaskUtil.mobile(String.valueOf(row.get("phone"))));
            items.add(item);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", users.userCount());
        m.put("items", items);
        return m;
    }

    @Transactional
    public void userStatus(long id, int status, long actor) {
        if (status != 0 && status != 1) {
            throw new BizException(ErrorCode.PARAM_INVALID, "状态只能是 0 或 1");
        }
        if (users.updateUserStatus(id, status) != 1) {
            throw new BizException(ErrorCode.HOUSE_NOT_FOUND, "用户不存在");
        }
        audit.insertAudit(actor, status == 1 ? "ENABLE_USER" : "DISABLE_USER", "USER", String.valueOf(id), null);
    }

    @Transactional
    public void resetPassword(long id, String password, long actor) {
        if (password == null || !STRONG.matcher(password).matches()) {
            throw new BizException(ErrorCode.PASSWORD_WEAK, "口令至少 8 位，且同时包含字母和数字");
        }
        if (users.updateUserPassword(id, passwords.encode(password)) != 1) {
            throw new BizException(ErrorCode.HOUSE_NOT_FOUND, "用户不存在");
        }
        audit.insertAudit(actor, "RESET_PASSWORD", "USER", String.valueOf(id), null);
    }

    private Map<String, Object> requireSet(long setId) {
        Map<String, Object> set = ruleSets.ruleSet(setId);
        if (set == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "规则集不存在，请先复制草案");
        }
        return set;
    }

    private long dimensionId(String code) {
        Map<String, Object> dim = ruleSets.dimensionByCode(code);
        if (dim == null || dim.get("id") == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "维度未入库：" + code + "。请先执行 seed-data.sql");
        }
        return ((Number) dim.get("id")).longValue();
    }

    private Map<String, Object> seedCard() {
        RuleSetView seed = rules.jsonSeed();
        List<Map<String, Object>> weights = new ArrayList<>();
        for (RuleSetView.DimView d : seed.dims()) {
            weights.add(Map.of("code", d.code(), "weight", d.weight()));
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", 0);
        item.put("name", seed.name());
        item.put("version", seed.version());
        item.put("status", "FILE");
        item.put("source", "FILE");
        item.put("confirmed", 1);
        item.put("active", 1);
        item.put("templateCode", seed.templateCode());
        item.put("weights", weights);
        item.put("weightSum", sumWeights(weights));
        item.put("note", "当前来自 default-rules.json。复制为草案后才能改权重并发布到数据库。");
        return item;
    }

    private static double sumWeights(List<Map<String, Object>> weights) {
        if (weights == null) {
            return 0d;
        }
        double sum = 0d;
        for (Map<String, Object> w : weights) {
            Object v = w.get("weight");
            if (v instanceof Number n) {
                sum += n.doubleValue();
            }
        }
        return BigDecimal.valueOf(sum).setScale(3, RoundingMode.HALF_UP).doubleValue();
    }

    private static String validate(HouseImportRow row) {
        if (row.getBuildingId() == null || row.getHouseTypeId() == null || row.getFloorNo() == null) {
            return "楼栋、户型、楼层必填";
        }
        if (row.getRoomNo() == null || row.getRoomNo().isBlank()) {
            return "房号必填";
        }
        if (row.getArea() == null || row.getArea().signum() <= 0 || row.getUnitPrice() == null || row.getUnitPrice().signum() <= 0) {
            return "面积和单价必须大于 0";
        }
        return null;
    }

    private String write(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL, "记录序列化失败");
        }
    }
}
