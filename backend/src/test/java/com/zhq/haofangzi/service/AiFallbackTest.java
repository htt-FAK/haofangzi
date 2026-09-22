package com.zhq.haofangzi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhq.haofangzi.common.ErrorCode;
import com.zhq.haofangzi.config.HfProperties;
import com.zhq.haofangzi.domain.dto.EvaluationDto;
import com.zhq.haofangzi.integration.LlmClient;
import com.zhq.haofangzi.mapper.AiLogMapper;
import com.zhq.haofangzi.mapper.CatalogMapper;
import com.zhq.haofangzi.mapper.ChatMapper;
import com.zhq.haofangzi.mapper.EvalMapper;
import com.zhq.haofangzi.mapper.UserMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiFallbackTest {

    @Mock LlmClient.Router llm;
    @Mock EvaluationService evaluation;
    @Mock CompareService compare;
    @Mock CatalogMapper catalog;
    @Mock UserMapper users;
    @Mock ChatMapper chats;
    @Mock EvalMapper evals;
    @Mock AiLogMapper callLogs;
    ObjectMapper mapper = new ObjectMapper();
    AiService ai;

    @BeforeEach
    void setUp() {
        ai = new AiService(llm, evaluation, compare, catalog, users, chats, evals, callLogs);
    }

    @Test
    @DisplayName("模型文案再夸张，推荐分仍是引擎总分")
    void modelCannotOverwriteScore() throws Exception {
        when(evaluation.evaluate(any(), any())).thenReturn(scored());
        when(llm.generate(any(), any(), any())).thenReturn(LlmClient.Outcome.of(
                mapper.readTree("{\"text\":\"优先看通风更好的一套，分数以规则引擎为准\"}"), "advisor-ranking", "v1", 12));
        var resp = ai.advice(Map.of("candidateHouseTypeIds", List.of(1), "question", "通风怎么样"), 3L);
        assertThat(resp.getCode()).isEqualTo(0);
        assertThat(resp.getData().get("answer")).isEqualTo("优先看通风更好的一套，分数以规则引擎为准");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rec = (List<Map<String, Object>>) resp.getData().get("recommend");
        assertThat(((Number) rec.get(0).get("total")).doubleValue()).isEqualTo(88d);
        assertThat(resp.getData().get("source")).isEqualTo("ai");
    }

    @Test
    @DisplayName("超时降级仍带回引擎分数，并标记模板")
    void timeoutKeepsEngineScore() throws Exception {
        when(evaluation.evaluate(any(), any())).thenReturn(scored());
        when(llm.generate(any(), any(), any())).thenReturn(
                new LlmClient.Outcome(mapper.readTree("{\"text\":\"模板不该被采用\"}"), true, "advisor-ranking", "v1", 20000, 0));
        var resp = ai.advice(Map.of("candidateHouseTypeIds", List.of(1), "question", "安静吗"), 3L);
        assertThat(resp.getCode()).isEqualTo(ErrorCode.AI_FALLBACK);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rec = (List<Map<String, Object>>) resp.getData().get("recommend");
        assertThat(((Number) rec.get(0).get("total")).doubleValue()).isEqualTo(88d);
        assertThat(resp.getData().get("source")).isEqualTo("fallback");
    }

    @Test
    @DisplayName("只有 note 字段时不采用模型原文")
    void rejectsUnstructuredModelJson() throws Exception {
        when(evaluation.evaluate(any(), any())).thenReturn(scored());
        when(llm.generate(any(), any(), any())).thenReturn(LlmClient.Outcome.of(
                mapper.readTree("{\"note\":\"这套房值 99 分\"}"), "advisor-ranking", "v1", 5));
        var resp = ai.advice(Map.of("candidateHouseTypeIds", List.of(1), "question", "买吗"), 3L);
        assertThat(resp.getCode()).isEqualTo(ErrorCode.AI_FALLBACK);
        assertThat(String.valueOf(resp.getData().get("answer"))).doesNotContain("99");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rec = (List<Map<String, Object>>) resp.getData().get("recommend");
        assertThat(((Number) rec.get(0).get("total")).doubleValue()).isEqualTo(88d);
    }

    @Test
    @DisplayName("坏 JSON 解析失败；无密钥时网关直接降级")
    void badJsonAndDisabledModel() throws Exception {
        assertThat(LlmClient.OpenAiCompatible.readModelJson(mapper, "不是 JSON")).isNull();
        var extracted = LlmClient.OpenAiCompatible.readModelJson(mapper, "说明 {\"note\":\"ok\"} 结束");
        assertThat(extracted.path("note").asText()).isEqualTo("ok");

        HfProperties props = new HfProperties();
        props.getAi().setEnabled(false);
        props.getAi().setTimeoutMs(20000);
        LlmClient.Outcome out = new LlmClient.Router(props, mapper).generate("advisor-ranking", "sys", "user");
        assertThat(out.fallback()).isTrue();
        assertThat(out.payload().path("note").asText()).contains("确定性排序");
    }

    @Test
    @DisplayName("流式失败时回模板，并带上该用户上一轮")
    void chatKeepsPriorTurnsWhenModelFails() {
        when(chats.latest(1L, 12)).thenReturn(List.of(
                Map.of("role", "assistant", "content", "先看朝南的三居"),
                Map.of("role", "user", "content", "我想要三居")));
        when(catalog.houseTypes(any(), any(), any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());
        when(llm.streamText(any(), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            List<Map<String, String>> messages = inv.getArgument(0);
            assertThat(messages).anyMatch(m -> "我想要三居".equals(m.get("content")));
            assertThat(messages).anyMatch(m -> "先看朝南的三居".equals(m.get("content")));
            assertThat(messages.get(messages.size() - 1).get("content")).isEqualTo("那朝向呢");
            return false;
        });
        StringBuilder shown = new StringBuilder();
        String source = ai.chat(1L, "那朝向呢", shown::append);
        assertThat(source).isEqualTo("fallback");
        assertThat(shown.toString()).contains("那朝向呢");
        verify(chats).insert(eq(1L), eq("user"), eq("那朝向呢"));
        verify(chats).insert(eq(1L), eq("assistant"), contains("那朝向呢"));
    }

    private static EvaluationDto.Result scored() {
        EvaluationDto.Result r = new EvaluationDto.Result();
        r.setHouseTypeId(1L);
        r.setHouseTypeName("星湖 A1");
        r.setTotal(88);
        r.setLevel("优");
        r.setSuggestions(List.of());
        return r;
    }
}
