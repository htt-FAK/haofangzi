package com.zhq.haofangzi.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CompareMapper {

@Insert("""
            INSERT INTO compare_report (user_id, title, house_type_ids, set_version, template_code,
                                        matrix_json, conclusion, ai_generated, share_token, expire_at, created_at)
            VALUES (#{userId}, #{title}, #{houseTypeIds}, #{setVersion}, #{templateCode},
                    #{matrixJson}, #{conclusion}, #{aiGenerated}, #{shareToken}, #{expireAt}, datetime('now','localtime'))
            """)
    @org.apache.ibatis.annotations.Options(useGeneratedKeys = true, keyProperty = "id")
    int insertCompareReport(com.zhq.haofangzi.domain.entity.CompareReport r);

@Update("""
            UPDATE compare_report SET share_token = #{shareToken}, expire_at = #{expireAt}, conclusion = #{conclusion}
            WHERE id = #{id} AND deleted = 0
            """)
    int updateCompareShare(com.zhq.haofangzi.domain.entity.CompareReport r);

@Select("SELECT * FROM compare_report WHERE id = #{id} AND deleted = 0")
    com.zhq.haofangzi.domain.entity.CompareReport compareReport(@Param("id") long id);

@Select("SELECT * FROM compare_report WHERE share_token = #{token} AND deleted = 0 LIMIT 1")
    com.zhq.haofangzi.domain.entity.CompareReport compareReportByToken(@Param("token") String token);

@Update("UPDATE compare_report SET view_count = view_count + 1 WHERE id = #{id} AND deleted = 0")
    int bumpView(@Param("id") long id);

@Update("""
            UPDATE compare_report SET consultant_note = #{note}, shown = #{shown}
            WHERE id = #{id} AND deleted = 0
            """)
    int markVisit(@Param("id") long id, @Param("note") String note, @Param("shown") int shown);

@Select("""
            SELECT id, title, share_token AS shareToken, view_count AS viewCount,
                   consultant_note AS consultantNote, shown, created_at AS createdAt
            FROM compare_report
            WHERE deleted = 0 AND share_token IS NOT NULL
            ORDER BY id DESC
            LIMIT 20
            """)
    java.util.List<java.util.Map<String, Object>> recentShares();
}
