package com.zhq.haofangzi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhq.haofangzi.common.ApiResponse;
import com.zhq.haofangzi.common.BizException;
import com.zhq.haofangzi.common.ErrorCode;
import com.zhq.haofangzi.service.AiService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.Principal;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** AI 能力网关（spec 007 I2/I3）。分数不由模型产生。顾问对话按用户记忆并流式返回。 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService ai;
    private final ObjectMapper json;

    @PostMapping("/advice")
    public ApiResponse<Map<String, Object>> advice(@RequestBody Map<String, Object> body, Principal me) {
        return ai.advice(body, uid(me));
    }

    @PostMapping("/report-conclusion")
    public ApiResponse<Map<String, Object>> conclusion(@RequestBody Map<String, Object> body, Principal me) {
        return ai.reportConclusion(body, uid(me));
    }

    @GetMapping("/chat")
    public ApiResponse<List<Map<String, Object>>> history(Principal me) {
        return ApiResponse.ok(ai.history(uid(me)));
    }

    @DeleteMapping("/chat")
    public ApiResponse<Void> clear(Principal me) {
        ai.clear(uid(me));
        return ApiResponse.ok();
    }

    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public void stream(@RequestBody Map<String, String> body, Principal me, HttpServletResponse response) throws IOException {
        long userId = uid(me);
        String question = body == null ? null : body.get("question");
        if (question == null || question.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请输入问题");
        }
        response.setCharacterEncoding("UTF-8");
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("X-Accel-Buffering", "no");
        var out = response.getWriter();
        String source = ai.chat(userId, question, text -> {
            try {
                out.write("data: " + json.writeValueAsString(Map.of("type", "token", "text", text)) + "\n\n");
                out.flush();
                response.flushBuffer();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });
        out.write("data: " + json.writeValueAsString(Map.of("type", "done", "source", source)) + "\n\n");
        out.flush();
        response.flushBuffer();
    }

    private static long uid(Principal me) {
        if (me == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        return Long.parseLong(me.getName());
    }
}
