package com.zhq.haofangzi.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 顾问对话记忆。页面可回看较多记录，送进模型的只取最近若干条。 */
@Mapper
public interface ChatMapper {

    @Select("""
            SELECT id, role, content, created_at AS createdAt
            FROM ai_chat_message
            WHERE user_id = #{userId}
            ORDER BY id DESC
            LIMIT #{limit}
            """)
    List<Map<String, Object>> latest(@Param("userId") long userId, @Param("limit") int limit);

    @Insert("""
            INSERT INTO ai_chat_message (user_id, role, content, created_at)
            VALUES (#{userId}, #{role}, #{content}, datetime('now','localtime'))
            """)
    int insert(@Param("userId") long userId, @Param("role") String role, @Param("content") String content);

    @Delete("DELETE FROM ai_chat_message WHERE user_id = #{userId}")
    int clear(@Param("userId") long userId);
}
