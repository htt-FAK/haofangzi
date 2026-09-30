package com.zhq.haofangzi.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhq.haofangzi.common.ApiResponse;
import com.zhq.haofangzi.common.BizException;
import com.zhq.haofangzi.common.ErrorCode;
import com.zhq.haofangzi.common.MaskUtil;
import com.zhq.haofangzi.domain.dto.EvaluationDto;
import com.zhq.haofangzi.domain.entity.CompareReport;
import com.zhq.haofangzi.domain.entity.Evaluation;
import com.zhq.haofangzi.domain.entity.HouseType;
import com.zhq.haofangzi.domain.entity.UserBrief;
import com.zhq.haofangzi.integration.LlmClient;
import com.zhq.haofangzi.mapper.AiLogMapper;
import com.zhq.haofangzi.mapper.CatalogMapper;
import com.zhq.haofangzi.mapper.ChatMapper;
import com.zhq.haofangzi.mapper.EvalMapper;
import com.zhq.haofangzi.mapper.UserMapper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * AI 网关编排：分数永远来自规则引擎，模型只写文字（宪法第三条）。
 */
@Service
@RequiredArgsConstructor
public class AiService {

    private final LlmClient.Router llm;
    private final EvaluationService evaluation;
    private final CompareService compare;
    private final CatalogMapper catalog;
    private final UserMapper users;
    private final ChatMapper chats;
    private final EvalMapper evals;
    private final AiLogMapper callLogs;

    // Qwen3.5-4B 原生支持 256K 上下文 (可扩展至 100 万 tokens)，深度保留多轮对话历史
    private static final int MODEL_CONTEXT = 40;
    private static final int PAGE_HISTORY = 200;

    public List<Map<String, Object>> history(long userId) {
        List<Map<String, Object>> rows = new ArrayList<>(chats.latest(userId, PAGE_HISTORY));
        Collections.reverse(rows);
        return rows;
    }

    public void clear(long userId) {
        chats.clear(userId);
    }

    /**
     * 多轮顾问。默认回调。
     */
    public String chat(long userId, String question, Consumer<String> onToken) {
        return chat(userId, question, onToken, null);
    }

    /**
     * 多轮顾问。支持 Qwen3.5-4B 思考模式（Thinking Mode）透传。
     * @return {@code ai} 或 {@code fallback}
     */
    public String chat(long userId, String question, Consumer<String> onToken, Consumer<String> onThinking) {
        if (question == null || question.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请输入问题");
        }
        String q = question.strip();
        if (q.length() > 4000) {
            q = q.substring(0, 4000);
        }
        List<Map<String, Object>> prior = new ArrayList<>(chats.latest(userId, MODEL_CONTEXT));
        Collections.reverse(prior);
        chats.insert(userId, "user", q);
        String card = scoreCard();
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", chatSystem(profileLine(userId), card)));
        for (Map<String, Object> row : prior) {
            String role = String.valueOf(row.get("role"));
            if (!"user".equals(role) && !"assistant".equals(role)) {
                continue;
            }
            Object content = row.get("content");
            if (content == null) {
                continue;
            }
            messages.add(Map.of("role", role, "content", String.valueOf(content)));
        }
        messages.add(Map.of("role", "user", "content", q));
        StringBuilder acc = new StringBuilder();
        long t0 = System.currentTimeMillis();
        llm.streamText(messages, piece -> {
            acc.append(piece);
            if (onToken != null) {
                onToken.accept(piece);
            }
        }, thinkingPiece -> {
            if (onThinking != null) {
                onThinking.accept(thinkingPiece);
            }
        });
        long latency = System.currentTimeMillis() - t0;
        String source;
        String answer;
        if (acc.isEmpty()) {
            answer = templateChat(q, card);
            if (onToken != null) {
                onToken.accept(answer);
            }
            source = "fallback";
        } else {
            answer = acc.toString();
            source = "ai";
        }
        chats.insert(userId, "assistant", answer);
        recordCall(userId, "advisor-chat", latency, "fallback".equals(source), q);
        return source;
    }

    private String chatSystem(String profile, String card) {
        return "你是肇庆好房子的专属智能选房顾问，由通义千问 Qwen3.5-4B（原生多模态、256K 上下文、门控 Delta 混合架构）驱动。"
                + "请用亲切、专业、客观的简体中文回答，语气自然得体，像面对面沟通。"
                + "守则：严禁编造购房资格、贷款利率、税费和投资升值承诺。分数只能严格引用本次附带的规则引擎评估数据，没有分数就说明暂未评估。"
                + "排版要求：结构清晰，善用加粗与列表突出户型采光日照、通风、动线及得房率等关键指标。"
                + "追问时以上方上下文为准。画像代码 FAMILY_3 这类为家庭结构枚举（如三口之家），不要当成人数。"
                + "买家偏好画像：" + profile
                + "。在售重点户型评分依据：" + (card.isBlank() ? "暂无" : card);
    }

    private String profileLine(Long userId) {
        if (userId == null) {
            return "未登录";
        }
        UserBrief me = users.userBrief(userId);
        if (me == null) {
            return "未填写";
        }
        return "预算 " + (me.getBudgetMin() == null ? "不限" : me.getBudgetMin() + "元")
                + "–" + (me.getBudgetMax() == null ? "不限" : me.getBudgetMax() + "元")
                + "，家庭结构 " + (me.getFamilyStructure() == null ? "未填" : me.getFamilyStructure())
                + "，期望居室 " + (me.getMustRooms() == null ? "未填" : me.getMustRooms() + "房");
    }

    private String scoreCard() {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (HouseType t : catalog.houseTypes(null, null, null, null, null, null, 0, 15)) {
            if (n >= 12) {
                break;
            }
            Evaluation e = evals.latestEvaluation(t.getId(), "GENERAL");
            sb.append(t.getName() == null ? ("户型" + t.getId()) : t.getName());
            if (t.getGfa() != null) {
                sb.append(' ').append(t.getGfa().stripTrailingZeros().toPlainString()).append("㎡");
            }
            if (t.getRooms() != null) {
                sb.append(' ').append(t.getRooms()).append("居");
            }
            if (t.getOrientation() != null) {
                sb.append(' ').append(t.getOrientation()).append("向");
            }
            if (e != null && e.getTotalScore() != null) {
                sb.append("（得分:").append(e.getTotalScore().stripTrailingZeros().toPlainString()).append("分");
                if (e.getLevel() != null) {
                    sb.append(",").append(e.getLevel());
                }
                sb.append("）");
            } else {
                sb.append("（未评）");
            }
            sb.append("；");
            n++;
        }
        return sb.toString();
    }

    private static String templateChat(String question, String card) {
        String scores = card.isBlank() ? "目前没有可引用的规则分数。" : "目前规则分数：" + card;
        return "模型暂不可用，这是模板回复。我记下了你的问题：「" + question + "」。"
                + scores + "分数由规则引擎计算，仅供参考，不构成购房建议。";
    }

    public ApiResponse<Map<String, Object>> advice(Map<String, Object> body, Long userId) {
        int topN = intVal(body.get("topN"), 3);
        @SuppressWarnings("unchecked")
        List<Number> rawIds = body.get("candidateHouseTypeIds") instanceof List<?> l
                ? (List<Number>) l : List.of();
        List<Long> ids = rawIds.stream().map(Number::longValue).toList();
        if (ids.isEmpty()) {
            ids = catalog.houseTypes(null, null, null, null, null, null, 0, 20).stream()
                    .map(HouseType::getId).toList();
        }
        UserBrief profile = userId == null ? null : users.userBrief(userId);
        List<Map<String, Object>> ranked = new ArrayList<>();
        for (Long id : ids) {
            EvaluationDto.Cmd cmd = new EvaluationDto.Cmd();
            cmd.setHouseTypeId(id);
            cmd.setPreview(true);
            EvaluationDto.Result r = evaluation.evaluate(cmd, userId);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("houseTypeId", r.getHouseTypeId());
            item.put("id", r.getHouseTypeId());
            item.put("name", r.getHouseTypeName());
            item.put("total", r.getTotal());
            item.put("score", r.getTotal());
            item.put("level", r.getLevel());
            item.put("reasons", r.getSuggestions() == null ? List.of()
                    : r.getSuggestions().stream().limit(2).map(EvaluationDto.Suggestion::getText).toList());
            ranked.add(item);
        }
        ranked.sort(Comparator.comparingDouble((Map<String, Object> m) -> ((Number) m.get("total")).doubleValue()).reversed());
        if (ranked.size() > topN) {
            ranked = new ArrayList<>(ranked.subList(0, topN));
        }
        String question = String.valueOf(body.getOrDefault("question", ""));
        String prompt = MaskUtil.scrub("用户问题：" + question + "；画像：" + (profile == null ? "无" : profile.getFamilyStructure())
                + "；候选：" + ranked);
        LlmClient.Outcome out = llm.generate("advisor-ranking",
                "你是选房顾问。禁止给出购房资格、贷款、税费建议。只用中文解释已给出的规则分数。"
                        + "只输出 JSON 对象 {\"text\":\"...\"}，不要其他字段。",
                prompt);
        String modelText = textOf(out);
        boolean usedModel = modelText != null;
        String answer = usedModel ? modelText : templateAdvice(ranked, question);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("answer", answer);
        data.put("recommend", ranked);
        data.put("source", usedModel ? "ai" : "fallback");
        if (!usedModel) {
            return ApiResponse.fallback(data, out.fallback() ? "模型超时或未配置密钥" : "模型输出不符合 {text} 结构");
        }
        return ApiResponse.ok(data);
    }

    public ApiResponse<Map<String, Object>> reportConclusion(Map<String, Object> body, Long userId) {
        String text;
        boolean fromEngine = true;
        Long reportId = body.get("reportId") instanceof Number n ? n.longValue() : null;
        CompareReport report = reportId == null ? null : compare.require(reportId);
        List<Map<String, Object>> scores = scoresOf(body);
        if (scores.isEmpty() && report != null && report.getMatrixJson() != null) {
            text = report.getConclusion() == null ? "请结合矩阵中的最优列做决策。" : report.getConclusion();
        } else {
            text = engineSummary(scores);
        }
        String brief = scores.stream()
                .map(m -> String.valueOf(m.getOrDefault("name", m.get("id"))) + " " + m.get("total") + "分")
                .reduce((a, b) -> a + "；" + b)
                .orElse(text);
        LlmClient.Outcome out = llm.generateBrief("report-conclusion",
                "根据已给出的规则分数写一段中文对比结论。不要编造分数。只输出 JSON 对象 {\"text\":\"...\"}。",
                MaskUtil.scrub(brief), 8_000);
        recordCall(userId, "report-conclusion", out.latencyMs(), out.fallback() || textOf(out) == null, brief);
        String modelText = textOf(out);
        String finalText = modelText == null ? text : modelText;
        fromEngine = modelText == null;
        if (report != null) {
            compare.saveConclusion(report, finalText, modelText != null);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("text", finalText);
        data.put("conclusion", finalText);
        data.put("source", fromEngine ? "fallback" : "ai");
        if (fromEngine) {
            return ApiResponse.fallback(data, out.fallback() ? "模型超时或未配置密钥" : "模型输出不符合 {text} 结构");
        }
        return ApiResponse.ok(data);
    }

    private void recordCall(Long userId, String key, long latencyMs, boolean fallback, String digestSrc) {
        String digest = digestSrc == null ? "" : digestSrc.strip();
        if (digest.length() > 80) {
            digest = digest.substring(0, 80);
        }
        callLogs.insert(userId, key, latencyMs, fallback ? 0 : 1, fallback ? 1 : 0, digest);
    }

    /** 只接受非空字符串字段 text。其它字段一律视为未通过结构校验。 */
    private static String textOf(LlmClient.Outcome out) {
        if (out.fallback() || out.payload() == null || !out.payload().isObject() || !out.payload().has("text")) {
            return null;
        }
        String text = out.payload().path("text").asText("").strip();
        return text.isEmpty() ? null : text;
    }

    private static String templateAdvice(List<Map<String, Object>> ranked, String question) {
        if (ranked.isEmpty()) {
            return "当前没有可推荐的户型。请先到选房页浏览并评估后再问我。";
        }
        Map<String, Object> top = ranked.get(0);
        return "根据规则引擎分数，优先考虑「" + top.get("name") + "」（" + String.format("%.1f", ((Number) top.get("total")).doubleValue())
                + " 分）。分数由采光/通风等规则计算，不是模型打的。"
                + (question == null || question.isBlank() ? "" : "已结合你的描述做排序建议。")
                + "评估结果仅供参考，不构成购房或投资建议。";
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> scoresOf(Map<String, Object> body) {
        if (body.get("scores") instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    out.add((Map<String, Object>) m);
                }
            }
            return out;
        }
        return List.of();
    }

    private static String engineSummary(List<Map<String, Object>> scores) {
        if (scores.isEmpty()) {
            return "请至少选择两个已评估户型再生成结论。分数由规则引擎计算。";
        }
        Map<String, Object> top = scores.stream()
                .max(Comparator.comparingDouble(m -> num(m.get("total"))))
                .orElse(scores.get(0));
        return "综合表现推荐 " + top.getOrDefault("name", "#" + top.get("id"))
                + "（" + String.format("%.1f", num(top.get("total"))) + " 分）。分数由规则引擎确定性计算，仅供参考。";
    }

    private static double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }

    private static int intVal(Object o, int dft) {
        return o instanceof Number n ? n.intValue() : dft;
    }
}
