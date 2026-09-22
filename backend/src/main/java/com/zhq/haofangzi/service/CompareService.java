package com.zhq.haofangzi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhq.haofangzi.common.BizException;
import com.zhq.haofangzi.common.ErrorCode;
import com.zhq.haofangzi.domain.dto.EvaluationDto;
import com.zhq.haofangzi.domain.entity.CompareReport;
import com.zhq.haofangzi.mapper.CompareMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 对比报告快照与分享（spec 005，演示口径：不做 PDF） */
@Service
@RequiredArgsConstructor
public class CompareService {

    private final EvaluationService evaluation;
    private final CompareMapper reports;
    private final ObjectMapper mapper;

    @Transactional
    @SuppressWarnings("unchecked")
    public Map<String, Object> create(List<Long> houseTypeIds, Long userId, String conclusion) {
        if (houseTypeIds == null || houseTypeIds.size() < 2 || houseTypeIds.size() > 4) {
            throw new BizException(ErrorCode.COMPARE_SIZE, "对比户型数量须为 2~4 个");
        }
        List<EvaluationDto.Result> cols = new ArrayList<>();
        for (Long id : houseTypeIds) {
            EvaluationDto.Cmd cmd = new EvaluationDto.Cmd();
            cmd.setHouseTypeId(id);
            cmd.setPreview(true);
            cols.add(evaluation.evaluate(cmd, userId));
        }
        Map<String, Object> matrix = matrix(cols);
        CompareReport r = new CompareReport();
        r.setUserId(userId == null ? 0L : userId);
        r.setTitle("选房对比");
        r.setHouseTypeIds(write(houseTypeIds));
        r.setSetVersion(cols.get(0).getSetVersion());
        r.setTemplateCode("GENERAL");
        r.setMatrixJson(write(matrix));
        r.setConclusion(conclusion);
        r.setAiGenerated(0);
        reports.insertCompareReport(r);
        Map<String, Object> vo = shareVo(r, matrix);
        vo.put("id", r.getId());
        return vo;
    }

    @Transactional
    public Map<String, Object> share(long id, Long userId) {
        CompareReport r = reports.compareReport(id);
        if (r == null || (userId != null && r.getUserId() != null && !r.getUserId().equals(userId) && r.getUserId() != 0L)) {
            throw new BizException(ErrorCode.FORBIDDEN, "报告不存在或无权分享");
        }
        if (r.getShareToken() == null || r.getShareToken().isBlank()
                || r.getExpireAt() == null || r.getExpireAt().isBefore(LocalDateTime.now())) {
            r.setShareToken(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            r.setExpireAt(LocalDateTime.now().plusDays(7));
            reports.updateCompareShare(r);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("token", r.getShareToken());
        m.put("url", "/share/reports/" + r.getShareToken());
        m.put("expireAt", r.getExpireAt().toString());
        return m;
    }

    /** 浏览器打印用的 HTML。不做服务端 PDF，打印对话框即可另存为 PDF。 */
    public String toHtml(String token) {
        Map<String, Object> vo;
        try {
            vo = byToken(token);
        } catch (BizException e) {
            return "<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>分享已失效</title></head><body><p>"
                    + esc(e.getMessage()) + "</p></body></html>";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\"><title>选房对比报告</title>");
        sb.append("<style>body{font-family:sans-serif;padding:24px;color:#141414}table{border-collapse:collapse;width:100%}");
        sb.append("td,th{border:1px solid #ccc;padding:6px 8px;font-size:13px} .note{color:#666;font-size:12px}</style></head><body>");
        sb.append("<h1>肇庆好房子 · 选房对比报告</h1>");
        sb.append("<p class=\"note\">只读快照 · 分享有效期 7 天 · 分数由规则引擎计算，仅供参考，不构成购房建议。</p>");
        sb.append("<p>").append(esc(String.valueOf(vo.getOrDefault("summary", "")))).append("</p><table><thead><tr><th>维度</th>");
        Object cols = vo.get("columns");
        if (cols instanceof List<?> list) {
            for (Object c : list) {
                if (c instanceof Map<?, ?> m) {
                    sb.append("<th>").append(esc(String.valueOf(m.get("name")))).append("</th>");
                }
            }
            sb.append("</tr></thead><tbody>");
            Object rows = vo.get("rows");
            if (rows instanceof List<?> rs) {
                for (Object row : rs) {
                    if (!(row instanceof Map<?, ?> rm)) {
                        continue;
                    }
                    sb.append("<tr><td>").append(esc(String.valueOf(rm.get("dim")))).append("</td>");
                    for (Object c : list) {
                        if (c instanceof Map<?, ?> m) {
                            sb.append("<td>").append(esc(String.valueOf(rm.get(String.valueOf(m.get("key")))))).append("</td>");
                        }
                    }
                    sb.append("</tr>");
                }
            }
        }
        sb.append("</tbody></table><script>window.addEventListener('load',()=>window.print())</script></body></html>");
        return sb.toString();
    }

    private static String esc(String s) {
        if (s == null || "null".equals(s)) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public Map<String, Object> byToken(String token) {
        CompareReport r = reports.compareReportByToken(token);
        if (r == null || r.getExpireAt() == null || r.getExpireAt().isBefore(LocalDateTime.now())) {
            throw new BizException(ErrorCode.SHARE_EXPIRED, "分享链接已过期或无效");
        }
        reports.bumpView(r.getId());
        r.setViewCount((r.getViewCount() == null ? 0 : r.getViewCount()) + 1);
        return shareVo(r, readMatrix(r));
    }

    public void markVisit(long id, String note, boolean shown) {
        if (reports.compareReport(id) == null) {
            throw new BizException(ErrorCode.HOUSE_TYPE_NOT_FOUND, "对比报告不存在");
        }
        String text = note == null ? "" : note.strip();
        if (text.length() > 255) {
            text = text.substring(0, 255);
        }
        reports.markVisit(id, text, shown ? 1 : 0);
    }

    public List<Map<String, Object>> recentShares() {
        return reports.recentShares();
    }

    public CompareReport require(long id) {
        CompareReport r = reports.compareReport(id);
        if (r == null) {
            throw new BizException(ErrorCode.HOUSE_TYPE_NOT_FOUND, "对比报告不存在");
        }
        return r;
    }

    public void saveConclusion(CompareReport r, String text, boolean ai) {
        r.setConclusion(text);
        r.setAiGenerated(ai ? 1 : 0);
        reports.updateCompareShare(r);
    }

    private Map<String, Object> shareVo(CompareReport r, Map<String, Object> matrix) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("summary", r.getConclusion());
        m.put("source", r.getAiGenerated() != null && r.getAiGenerated() == 1 ? "ai" : "fallback");
        m.put("consultantNote", r.getConsultantNote() == null ? "" : r.getConsultantNote());
        m.put("shown", r.getShown() != null && r.getShown() == 1);
        m.put("viewCount", r.getViewCount() == null ? 0 : r.getViewCount());
        m.put("createTime", r.getCreatedAt() == null ? LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                : r.getCreatedAt().toString());
        m.putAll(matrix);
        return m;
    }

    private Map<String, Object> matrix(List<EvaluationDto.Result> cols) {
        List<Map<String, Object>> columns = new ArrayList<>();
        EvaluationDto.Result best = cols.stream().max(java.util.Comparator.comparingDouble(EvaluationDto.Result::getTotal)).orElse(cols.get(0));
        for (EvaluationDto.Result c : cols) {
            Map<String, Object> col = new LinkedHashMap<>();
            col.put("id", c.getHouseTypeId());
            col.put("name", c.getHouseTypeName());
            col.put("key", "ht" + c.getHouseTypeId());
            col.put("best", c.getHouseTypeId().equals(best.getHouseTypeId()));
            col.put("total", c.getTotal());
            col.put("level", c.getLevel());
            columns.add(col);
        }
        String[][] dimRows = {
                {"总分", "total", "total"},
                {"采光与日照", "LIGHT", "dim"},
                {"通风与对流", "VENT", "dim"},
                {"动线与分区", "CIRC", "dim"},
                {"实用与得房", "UTIL", "dim"},
                {"静谧与干扰", "QUIET", "dim"},
                {"绿色与舒适", "GREEN", "dim"},
                {"经济适配", "COST", "dim"},
        };
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String[] def : dimRows) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("dim", def[0]);
            row.put("metric", def[0]);
            row.put("note", "");
            for (EvaluationDto.Result c : cols) {
                double v = "total".equals(def[2]) ? c.getTotal() : dimScore(c, def[1]);
                row.put("ht" + c.getHouseTypeId(), String.format("%.1f", v));
            }
            rows.add(row);
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("columns", columns);
        m.put("rows", rows);
        return m;
    }

    private static double dimScore(EvaluationDto.Result c, String code) {
        if (c.getDimensions() == null) {
            return 0;
        }
        return c.getDimensions().stream().filter(d -> code.equals(d.getCode()))
                .map(d -> d.getScore() == null ? 0.0 : d.getScore()).findFirst().orElse(0.0);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMatrix(CompareReport r) {
        try {
            return mapper.readValue(r.getMatrixJson(), Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private String write(Object o) {
        try {
            return mapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL, "对比快照序列化失败");
        }
    }
}
