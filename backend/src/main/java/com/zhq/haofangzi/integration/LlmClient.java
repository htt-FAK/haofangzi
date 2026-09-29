package com.zhq.haofangzi.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhq.haofangzi.config.HfProperties;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 大模型网关（M7；spec 007 plan §1）。四项能力共用这一条五步管线：
 * 脱敏 → 模板渲染 → 缓存 → 调用（超时+重试）→ 输出契约校验，任一步失败落 {@link Fallback}。
 *
 * <p>禁止事项（宪法第三条）：不得用 LLM 直接产生户型<b>分数</b>；不得把未校验的模型文本入库；
 * 不得把手机号等 PII 放入 payload（见 {@code MaskUtil.scrub}）。
 */
public interface LlmClient {

    /** @return 模型返回的结构化 JSON；失败返回 empty 由上层降级 */
    Optional<JsonNode> chat(String promptKey, String systemPrompt, String userPrompt);

    default boolean realModel() {
        return true;
    }

    /** 统一出口：把降级语义（50310）与业务结果一起返回，前端只需判断 fallback 标记（AC-62/66） */
    record Outcome(JsonNode payload, boolean fallback, String promptKey, String promptVersion,
                   long latencyMs, int hallucinationDropped) {

        public static Outcome of(JsonNode n, String key, String ver, long ms) {
            return new Outcome(n, false, key, ver, ms, 0);
        }
    }

    // ───────────────────────── 厂商实现（OpenAI 兼容） ─────────────────────────
    @Slf4j
    class OpenAiCompatible implements LlmClient {

        private final HfProperties props;
        private final ObjectMapper mapper;
        private final HttpClient http;
        private final ScheduledExecutorService streamTimeouts = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "llm-stream-timeout");
            t.setDaemon(true);
            return t;
        });

        public OpenAiCompatible(HfProperties props, ObjectMapper mapper) {
            this.props = props;
            this.mapper = mapper;
            HttpClient.Builder b = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(props.getAi().getTimeoutMs()))
                    .proxy(java.net.ProxySelector.getDefault());
            String envProxy = System.getenv("https_proxy");
            if (envProxy == null || envProxy.isBlank()) {
                envProxy = System.getenv("all_proxy");
            }
            if (envProxy == null || envProxy.isBlank()) {
                envProxy = System.getenv("http_proxy");
            }
            if (envProxy != null && !envProxy.isBlank()) {
                try {
                    URI u = URI.create(envProxy.startsWith("http") ? envProxy : ("http://" + envProxy));
                    if (u.getHost() != null && u.getPort() > 0) {
                        b.proxy(java.net.ProxySelector.of(new java.net.InetSocketAddress(u.getHost(), u.getPort())));
                    }
                } catch (Exception ignored) {
                }
            }
            this.http = b.build();
        }

        @Override
        public Optional<JsonNode> chat(String promptKey, String systemPrompt, String userPrompt) {
            HfProperties.Ai ai = props.getAi();
            return chat(promptKey, systemPrompt, userPrompt, ai.getTimeoutMs(), ai.getMaxRetry());
        }

        public Optional<JsonNode> chat(String promptKey, String systemPrompt, String userPrompt,
                                        long timeoutMs, int maxRetry) {
            HfProperties.Ai ai = props.getAi();
            if (!ai.isEnabled() || ai.getApiKey() == null || ai.getApiKey().isBlank()) {
                return Optional.empty();                                     // 直接走 Mock（AC-66）
            }
            for (int attempt = 0; attempt <= maxRetry; attempt++) {
                long t0 = System.currentTimeMillis();
                boolean jsonMode = attempt == 0;
                try {
                    Map<String, Object> payload = new java.util.LinkedHashMap<>();
                    payload.put("model", ai.getModel());
                    payload.put("temperature", 0.2);
                    payload.put("enable_thinking", false);
                    if (jsonMode) {
                        payload.put("response_format", Map.of("type", "json_object"));
                    }
                    payload.put("messages", List.of(Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)));
                    String body = mapper.writeValueAsString(payload);
                    HttpRequest req = HttpRequest.newBuilder(URI.create(ai.getBaseUrl() + "/chat/completions"))
                            .timeout(Duration.ofMillis(timeoutMs))
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + ai.getApiKey())
                            .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
                    HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
                    if (res.statusCode() / 100 != 2) {
                        log.warn("LLM HTTP {} promptKey={} attempt={} jsonMode={}",
                                res.statusCode(), promptKey, attempt, jsonMode);
                        continue;
                    }
                    String content = mapper.readTree(res.body())
                            .path("choices").path(0).path("message").path("content").asText("");
                    JsonNode parsed = readModelJson(mapper, content);
                    if (parsed != null && !parsed.isMissingNode() && !parsed.isNull()) {
                        return Optional.of(parsed);
                    }
                    log.warn("LLM 返回无法解析为 JSON promptKey={} attempt={}", promptKey, attempt);
                } catch (Exception e) {
                    log.warn("LLM 调用失败 promptKey={} attempt={} 耗时={}ms 原因={}",
                            promptKey, attempt, System.currentTimeMillis() - t0, e.toString());
                }
            }
            return Optional.empty();
        }

        /**
         * 流式对话。只把 {@code delta.content} 交给调用方，忽略 {@code reasoning_content}。
         * @return 是否至少收到一个正文字符；失败或无密钥返回 false
         */
        public boolean streamText(List<Map<String, String>> messages, Consumer<String> onToken) {
            HfProperties.Ai ai = props.getAi();
            if (!ai.isEnabled() || ai.getApiKey() == null || ai.getApiKey().isBlank()) {
                return false;
            }
            boolean[] any = {false};
            InputStream raw = null;
            ScheduledFuture<?> kill = null;
            ScheduledFuture<?> quiet = null;
            try {
                Map<String, Object> payload = new java.util.LinkedHashMap<>();
                payload.put("model", ai.getModel());
                payload.put("temperature", 0.4);
                payload.put("enable_thinking", false);
                payload.put("stream", true);
                payload.put("messages", messages);
                String body = mapper.writeValueAsString(payload);
                HttpRequest req = HttpRequest.newBuilder(URI.create(ai.getBaseUrl() + "/chat/completions"))
                        .timeout(Duration.ofMillis(90_000))
                        .header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + ai.getApiKey())
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
                HttpResponse<InputStream> res = http.send(req, HttpResponse.BodyHandlers.ofInputStream());
                raw = res.body();
                if (res.statusCode() / 100 != 2) {
                    log.warn("LLM 流式 HTTP {}", res.statusCode());
                    return false;
                }
                InputStream in = raw;
                kill = streamTimeouts.schedule(() -> closeQuietly(in), 90, TimeUnit.SECONDS);
                quiet = streamTimeouts.schedule(() -> {
                    if (!any[0]) {
                        closeQuietly(in);
                    }
                }, 20, TimeUnit.SECONDS);
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.startsWith("data:")) {
                            continue;
                        }
                        String data = line.substring(5).trim();
                        if (data.isEmpty() || "[DONE]".equals(data)) {
                            continue;
                        }
                        String piece = deltaContent(data);
                        if (!piece.isEmpty()) {
                            any[0] = true;
                            onToken.accept(piece);
                        }
                    }
                }
                return any[0];
            } catch (Exception e) {
                log.warn("LLM 流式失败：{}", e.toString());
                return any[0];
            } finally {
                if (quiet != null) {
                    quiet.cancel(false);
                }
                if (kill != null) {
                    kill.cancel(false);
                }
                if (raw != null) {
                    try {
                        raw.close();
                    } catch (Exception ignored) {
                        // 已关闭
                    }
                }
            }
        }

        private static void closeQuietly(InputStream in) {
            try {
                in.close();
            } catch (Exception ignored) {
                // 关闭流以打断阻塞的 readLine
            }
        }

        private String deltaContent(String data) {
            try {
                JsonNode content = mapper.readTree(data).path("choices").path(0).path("delta").path("content");
                if (content.isMissingNode() || content.isNull() || !content.isTextual()) {
                    return "";
                }
                return content.asText("");
            } catch (Exception e) {
                return "";
            }
        }

        /**
         * 解析模型正文。先去代码围栏，再整段解析；失败则截取第一个 '{' 到最后一个 '}'。
         * 硅基流动部分模型会拒绝 response_format，或在 JSON 前后加说明。解析失败返回 null，由上层降级。
         */
        public static JsonNode readModelJson(ObjectMapper mapper, String content) {
            String t = stripFence(content == null ? "" : content);
            JsonNode direct = tryTree(mapper, t);
            if (direct != null) {
                return direct;
            }
            int a = t.indexOf('{');
            int b = t.lastIndexOf('}');
            if (a >= 0 && b > a) {
                return tryTree(mapper, t.substring(a, b + 1));
            }
            return null;
        }

        private static JsonNode tryTree(ObjectMapper mapper, String t) {
            if (t == null || t.isBlank()) {
                return null;
            }
            String s = t.strip();
            if (!s.startsWith("{") && !s.startsWith("[")) {
                return null;
            }
            try {
                return mapper.readTree(t);
            } catch (Exception e) {
                return null;
            }
        }

        /** 模型偶尔无视 response_format 仍包 ```json ```，做一次性清洗（防御式，不算"智能"） */
        private static String stripFence(String s) {
            String t = s.strip();
            if (t.startsWith("```")) {
                int first = t.indexOf('\n');
                int last = t.lastIndexOf("```");
                if (first > 0 && last > first) {
                    return t.substring(first + 1, last).strip();
                }
            }
            return t;
        }
    }

    // ───────────────────────── 离线降级实现（确定性，可单测） ─────────────────────────
    /**
     * Mock：用规则模板产出<b>与真实模型同 schema</b> 的结果，让断网演示仍然完整（FR-112）。
     * 关键差异：{@code fallback=true}，界面显示"模板生成"角标。
     */
    @Slf4j
    class Mock implements LlmClient {

        private final ObjectMapper mapper;

        public Mock(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public boolean realModel() {
            return false;
        }

        @Override
        public Optional<JsonNode> chat(String promptKey, String systemPrompt, String userPrompt) {
            try {
                JsonNode n = switch (promptKey) {
                    case "rule-draft" -> mapper.readTree("""
                            {"summary":"沿用基线，VENT 权重 0.18→0.22，QUIET 0.10→0.14","changes":[
                             {"path":"dimensions[VENT].weight","from":0.18,"to":0.22,
                              "changeNote":"三代同堂共居，通风优先级提升","basis":"《住宅项目规范》通风相关条文"},
                             {"path":"dimensions[QUIET].weight","from":0.10,"to":0.14,
                              "changeNote":"多代同住对噪声更敏感","basis":"行业设计常识"}],
                             "caution":"草案需人工审阅后发布"}
                            """);
                    case "advisor-ranking" -> mapper.readTree(
                            "{\"items\":[],\"note\":\"使用引擎分数 + 画像权重的确定性排序\"}");
                    case "scene-code" -> mapper.readTree(
                            "{\"code\":\"// 见 scene-codegen 模板版\",\"notes\":\"模板输出\"}");
                    default -> mapper.readTree("""
                            {"recommendation":null,"confidence":"中","reasons":[],"perItem":[],
                             "fitAudience":"模板未命中，请检查入参","nextCheck":[]}
                            """);
                };
                return Optional.of(n);
            } catch (Exception e) {
                log.error("Mock 模板解析异常（应不可能发生：常量 JSON）", e);
                return Optional.empty();
            }
        }
    }

    /** 选择实现：enabled=false 或无密钥 → Mock（启动即可演示，不抛异常） */
    @Component
    class Router {

        private final LlmClient primary;
        private final LlmClient fallback;
        private final ObjectMapper mapper;
        private final HfProperties props;

        public Router(HfProperties props, ObjectMapper mapper) {
            this.mapper = mapper;
            this.props = props;
            this.primary = new OpenAiCompatible(props, mapper);
            this.fallback = new Mock(mapper);
        }

        public boolean streamText(List<Map<String, String>> messages, Consumer<String> onToken) {
            return ((OpenAiCompatible) primary).streamText(messages, onToken);
        }

        public Outcome generateBrief(String promptKey, String systemPrompt, String userPrompt, long timeoutMs) {
            long t0 = System.currentTimeMillis();
            Optional<JsonNode> n = ((OpenAiCompatible) primary).chat(promptKey, systemPrompt, userPrompt, timeoutMs, 0);
            if (n.isPresent()) {
                return Outcome.of(n.get(), promptKey, "v1", System.currentTimeMillis() - t0);
            }
            JsonNode fb = fallback.chat(promptKey, systemPrompt, userPrompt)
                    .orElseGet(() -> mapper.createObjectNode());
            return new Outcome(fb, true, promptKey, "v1", System.currentTimeMillis() - t0, 0);
        }

        public Outcome generate(String promptKey, String systemPrompt, String userPrompt) {
            long t0 = System.currentTimeMillis();
            Optional<JsonNode> n = primary.chat(promptKey, systemPrompt, userPrompt);
            if (n.isPresent()) {
                return Outcome.of(n.get(), promptKey, "v1", System.currentTimeMillis() - t0);
            }
            JsonNode fb = fallback.chat(promptKey, systemPrompt, userPrompt)
                    .orElseGet(() -> mapper.createObjectNode());
            return new Outcome(fb, true, promptKey, "v1", System.currentTimeMillis() - t0, 0);
        }

        /** 缓存键：promptKey + 版本 + 入参摘要（FR-113；不落完整 prompt，仅摘要，日志脱敏） */
        public String cacheKey(String promptKey, Object payload) {
            try {
                String raw = promptKey + "|" + mapper.writeValueAsString(payload);
                return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(raw.getBytes(StandardCharsets.UTF_8))).substring(0, 32);
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
