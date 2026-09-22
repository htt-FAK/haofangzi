package com.zhq.haofangzi.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AuditMapper {

@Insert("""
            INSERT INTO sys_audit_log (user_id, action, target_type, target_id, detail_json, created_at)
            VALUES (#{userId}, #{action}, #{targetType}, #{targetId}, #{detail}, datetime('now','localtime'))
            """)
    int insertAudit(@Param("userId") Long userId, @Param("action") String action,
                    @Param("targetType") String targetType, @Param("targetId") String targetId,
                    @Param("detail") String detail);
}
