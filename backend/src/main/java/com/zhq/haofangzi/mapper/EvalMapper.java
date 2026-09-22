package com.zhq.haofangzi.mapper;

import com.zhq.haofangzi.domain.entity.Evaluation;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EvalMapper {

@Insert("""
            INSERT INTO evaluation (user_id, house_type_id, house_id, set_id, set_version, template_code,
                                    total_score, level, missing_count, detail_json, ai_note, created_at)
            VALUES (#{userId}, #{houseTypeId}, #{houseId}, #{setId}, #{setVersion}, #{templateCode},
                    #{totalScore}, #{level}, #{missingCount}, #{detailJson}, #{aiNote}, datetime('now','localtime'))
            """)
    int insertEvaluation(Evaluation e);

@Select("SELECT * FROM evaluation WHERE id = #{id} AND deleted = 0")
    Evaluation evaluation(@Param("id") long id);

@Select("""
            SELECT * FROM evaluation WHERE house_type_id = #{htId} AND deleted = 0
            ORDER BY id DESC LIMIT #{limit}
            """)
    List<Evaluation> evaluations(@Param("htId") long htId, @Param("limit") int limit);

@Select("""
            SELECT e.* FROM evaluation e
            JOIN (SELECT house_type_id, MAX(id) mid FROM evaluation
                  WHERE deleted = 0 AND template_code = #{tpl} GROUP BY house_type_id) x ON x.mid = e.id
            WHERE e.house_type_id = #{htId}
            """)
    Evaluation latestEvaluation(@Param("htId") long htId, @Param("tpl") String tpl);
}
