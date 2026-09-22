package com.zhq.haofangzi.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RuleMapper {

@Select("""
            SELECT id, name, version, status, source, confirmed, active,
                   template_code AS templateCode
            FROM eval_rule_set
            WHERE deleted = 0
            ORDER BY id DESC
            """)
    java.util.List<java.util.Map<String, Object>> ruleSets();

@Select("""
            SELECT id, name, version, status, source, confirmed, active,
                   template_code AS templateCode
            FROM eval_rule_set
            WHERE active = 1 AND status = 'PUBLISHED' AND deleted = 0
            ORDER BY id DESC LIMIT 1
            """)
    java.util.Map<String, Object> activeRuleSet();

@Select("""
            SELECT id, name, version, status, source, confirmed, active,
                   template_code AS templateCode
            FROM eval_rule_set WHERE id = #{id} AND deleted = 0
            """)
    java.util.Map<String, Object> ruleSet(@Param("id") long id);

@Select("""
            SELECT d.code AS dimCode, d.name AS dimName, rsd.weight AS dimWeight,
                   r.metric_code AS metricCode, r.metric_name AS metricName, r.unit AS unit,
                   r.operator AS operator, r.internal_weight AS internalWeight,
                   r.higher_is_better AS higherIsBetter, r.tier_json AS tierJson,
                   r.basis AS basis, r.suggestion AS suggestion, r.source_fields AS sourceFields
            FROM eval_rule r
            JOIN eval_dimension d ON d.id = r.dimension_id
            LEFT JOIN eval_rule_set_dim rsd
              ON rsd.set_id = r.set_id AND rsd.dimension_id = d.id AND rsd.deleted = 0
            WHERE r.set_id = #{setId} AND r.deleted = 0
            ORDER BY d.order_idx, r.order_idx
            """)
    java.util.List<java.util.Map<String, Object>> rulesOf(@Param("setId") long setId);

@Select("""
            SELECT d.code AS code, rsd.weight AS weight
            FROM eval_rule_set_dim rsd
            JOIN eval_dimension d ON d.id = rsd.dimension_id
            WHERE rsd.set_id = #{setId} AND rsd.deleted = 0
            ORDER BY d.order_idx
            """)
    java.util.List<java.util.Map<String, Object>> ruleSetWeights(@Param("setId") long setId);

@Select("SELECT id, code FROM eval_dimension WHERE code = #{code} AND deleted = 0 LIMIT 1")
    java.util.Map<String, Object> dimensionByCode(@Param("code") String code);

@Update("UPDATE eval_rule_set SET active = 0 WHERE deleted = 0 AND active = 1")
    int clearActiveRuleSets();

@Update("""
            UPDATE eval_rule_set
            SET status = 'PUBLISHED', active = 1, confirmed = 1, published_at = datetime('now','localtime')
            WHERE id = #{id} AND deleted = 0
            """)
    int publishRuleSet(@Param("id") long id);

@Update("""
            UPDATE eval_rule_set_dim
            SET weight = #{weight}
            WHERE set_id = #{setId} AND deleted = 0
              AND dimension_id = (SELECT id FROM eval_dimension WHERE code = #{code} AND deleted = 0)
            """)
    int updateDimWeight(@Param("setId") long setId, @Param("code") String code, @Param("weight") double weight);

@Insert("""
            INSERT INTO eval_rule_set (name, version, status, source, confirmed, active, template_code, created_at)
            VALUES (#{name}, #{version}, 'DRAFT', #{source}, #{confirmed}, 0, #{templateCode}, datetime('now','localtime'))
            """)
    @org.apache.ibatis.annotations.Options(useGeneratedKeys = true, keyProperty = "id")
    int insertRuleSet(com.zhq.haofangzi.domain.entity.RuleSetRow row);

@Insert("""
            INSERT INTO eval_rule_set_dim (set_id, dimension_id, weight, created_at)
            VALUES (#{setId}, #{dimensionId}, #{weight}, datetime('now','localtime'))
            """)
    int insertRuleSetDim(@Param("setId") long setId, @Param("dimensionId") long dimensionId,
                         @Param("weight") double weight);

@Insert("""
            INSERT INTO eval_rule (set_id, dimension_id, metric_code, metric_name, source_fields, operator, unit,
                                   internal_weight, higher_is_better, tier_json, basis, suggestion, order_idx, created_at)
            VALUES (#{setId}, #{dimensionId}, #{metricCode}, #{metricName}, #{sourceFields}, #{operator}, #{unit},
                    #{internalWeight}, #{higherIsBetter}, #{tierJson}, #{basis}, #{suggestion}, #{orderIdx}, datetime('now','localtime'))
            """)
    int insertRule(@Param("setId") long setId, @Param("dimensionId") long dimensionId,
                   @Param("metricCode") String metricCode, @Param("metricName") String metricName,
                   @Param("sourceFields") String sourceFields, @Param("operator") String operator,
                   @Param("unit") String unit, @Param("internalWeight") double internalWeight,
                   @Param("higherIsBetter") int higherIsBetter, @Param("tierJson") String tierJson,
                   @Param("basis") String basis, @Param("suggestion") String suggestion,
                   @Param("orderIdx") int orderIdx);
}
