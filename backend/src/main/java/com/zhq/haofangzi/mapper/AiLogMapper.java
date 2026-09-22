package com.zhq.haofangzi.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** AI 调用日志。只记摘要，不记完整提示词。 */
@Mapper
public interface AiLogMapper {

    @Insert("""
            INSERT INTO ai_call_log (user_id, prompt_key, prompt_version, latency_ms, ok, fallback, payload_digest, created_at)
            VALUES (#{userId}, #{promptKey}, 'v1', #{latencyMs}, #{ok}, #{fallback}, #{digest}, datetime('now','localtime'))
            """)
    int insert(@Param("userId") Long userId, @Param("promptKey") String promptKey,
               @Param("latencyMs") long latencyMs, @Param("ok") int ok,
               @Param("fallback") int fallback, @Param("digest") String digest);

    @Select("""
            SELECT prompt_key AS promptKey, COUNT(*) AS calls, SUM(fallback) AS fallbacks,
                   CAST(AVG(latency_ms) AS INTEGER) AS avgLatency
            FROM ai_call_log
            WHERE deleted = 0
            GROUP BY prompt_key
            ORDER BY calls DESC
            """)
    List<Map<String, Object>> summary();

    @Select("""
            SELECT prompt_key AS promptKey, latency_ms AS latencyMs, fallback, created_at AS createdAt
            FROM ai_call_log
            WHERE deleted = 0
            ORDER BY id DESC
            LIMIT 20
            """)
    List<Map<String, Object>> recent();
}
