package com.zhq.haofangzi.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AppointmentMapper {

@Insert("""
            INSERT INTO appointment (user_id, consultant_id, project_id, house_type_id, house_id, intention_no,
                                     visit_date, visit_slot, party_size, contact_phone, remark, status, created_at)
            VALUES (#{userId}, #{consultantId}, #{projectId}, #{houseTypeId}, #{houseId}, #{intentionNo},
                    #{visitDate}, #{visitSlot}, #{partySize}, #{contactPhone}, #{remark}, #{status}, datetime('now','localtime'))
            """)
    @org.apache.ibatis.annotations.Options(useGeneratedKeys = true, keyProperty = "id")
    int insertAppointment(com.zhq.haofangzi.domain.entity.Appointment a);

@Select("""
            SELECT a.*, p.name AS projectName, t.name AS houseTypeName, u.nickname AS customerName
            FROM appointment a
            LEFT JOIN hf_project p ON p.id = a.project_id
            LEFT JOIN hf_house_type t ON t.id = a.house_type_id
            LEFT JOIN sys_user u ON u.id = a.user_id
            WHERE a.deleted = 0
              AND (#{userId} IS NULL OR a.user_id = #{userId} OR a.consultant_id = #{userId})
            ORDER BY a.visit_date DESC, a.visit_slot ASC LIMIT #{limit}
            """)
    java.util.List<com.zhq.haofangzi.domain.entity.Appointment> appointments(@Param("userId") Long userId,
                                                                             @Param("limit") int limit);

@Select("SELECT * FROM appointment WHERE id = #{id} AND deleted = 0")
    com.zhq.haofangzi.domain.entity.Appointment appointment(@Param("id") long id);

@Select("""
            SELECT COUNT(*) FROM appointment
            WHERE project_id = #{projectId} AND visit_date = #{visitDate} AND visit_slot = #{visitSlot}
              AND status IN ('PENDING', 'CONFIRMED') AND deleted = 0
            """)
    int slotUsed(@Param("projectId") long projectId, @Param("visitDate") java.time.LocalDate visitDate,
                 @Param("visitSlot") String visitSlot);

@Select("""
            <script>
            SELECT s.visit_date AS visit_date, s.slot AS slot, s.capacity AS capacity,
                   (SELECT COUNT(*) FROM appointment a WHERE a.project_id = s.project_id
                     AND a.visit_date = s.visit_date AND a.visit_slot = s.slot
                     AND a.status IN ('PENDING','CONFIRMED') AND a.deleted = 0) AS used
            FROM hf_slot_config s
            WHERE s.deleted = 0 AND s.enabled = 1
              AND s.project_id = #{projectId} AND s.visit_date BETWEEN #{from} AND #{to}
            ORDER BY s.visit_date, s.slot
            </script>
            """)
    java.util.List<java.util.Map<String, Object>> slotView(@Param("projectId") long projectId,
                                                            @Param("from") java.time.LocalDate from,
                                                            @Param("to") java.time.LocalDate to);

@Update("""
            UPDATE appointment SET status = #{to}, audit_remark = #{remark}, audit_by = #{auditBy}
            WHERE id = #{id} AND status = #{from} AND deleted = 0
            """)
    int updateAppointmentStatus(@Param("id") long id, @Param("from") String from, @Param("to") String to,
                                @Param("remark") String remark, @Param("auditBy") Long auditBy);

@Update("UPDATE appointment SET notified_at = datetime('now','localtime') WHERE id = #{id} AND notified_at IS NULL")
    int markNotified(@Param("id") long id);

@Update("UPDATE appointment SET reminded_2h_at = datetime('now','localtime') WHERE id = #{id} AND reminded_2h_at IS NULL")
    int markReminded2h(@Param("id") long id);

@Select("""
            SELECT * FROM appointment
            WHERE deleted = 0 AND status IN ('PENDING','CONFIRMED')
              AND ((visit_date = date('now','localtime','+1 day') AND notified_at IS NULL)
                OR (visit_date = date('now','localtime') AND reminded_2h_at IS NULL
                     AND (visit_date || ' ' || substr(visit_slot, 1, 5))
                         BETWEEN datetime('now','localtime') AND datetime('now','localtime','+2 hours')))
            LIMIT 200
            """)
    java.util.List<com.zhq.haofangzi.domain.entity.Appointment> toRemind();

@Insert("INSERT INTO sys_message (user_id, type, title, content, biz_id, created_at) "
            + "VALUES (#{userId}, #{type}, #{title}, #{content}, #{bizId}, datetime('now','localtime'))")
    int insertMessage(@Param("userId") long userId, @Param("type") String type, @Param("title") String title,
                     @Param("content") String content, @Param("bizId") String bizId);
}
