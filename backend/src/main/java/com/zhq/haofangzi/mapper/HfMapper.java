package com.zhq.haofangzi.mapper;

import com.zhq.haofangzi.domain.entity.Building;
import com.zhq.haofangzi.domain.entity.Evaluation;
import com.zhq.haofangzi.domain.entity.House;
import com.zhq.haofangzi.domain.entity.HouseLock;
import com.zhq.haofangzi.domain.entity.HouseType;
import com.zhq.haofangzi.domain.entity.Project;
import com.zhq.haofangzi.domain.entity.Room;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 数据访问集中层（骨架用注解 SQL 便于阅读；生产可按域拆 BaseMapper + XML，见 T-002/T-022/T-062）。
 *
 * <p>SQL 一律参数化（{@code #{}}），动态排序字段在 service 层用枚举映射，禁止拼接（TC-SEC 安全用例）。
 */
@Mapper
public interface HfMapper {

    // ── 评估上下文装载（002 FR-19 / 004 plan §4：一次装载，避免 21 次查库）──────────────
    @Select("SELECT * FROM hf_house_type WHERE id = #{id} AND deleted = 0")
    HouseType houseType(@Param("id") long id);

    @Select("SELECT * FROM hf_room WHERE house_type_id = #{htId} AND deleted = 0 ORDER BY id")
    List<Room> rooms(@Param("htId") long htId);

    @Select("SELECT * FROM hf_house WHERE id = #{id} AND deleted = 0")
    House house(@Param("id") long id);

    @Select("SELECT * FROM hf_building WHERE id = #{id} AND deleted = 0")
    Building building(@Param("id") long id);

    @Select("SELECT * FROM hf_project WHERE id = #{id} AND deleted = 0")
    Project project(@Param("id") long id);

    @Select("""
            SELECT p.avg_price FROM hf_project p JOIN hf_house_type t ON t.project_id = p.id
            WHERE t.id = #{htId} AND p.deleted = 0
            """)
    java.math.BigDecimal projectAvgPrice(@Param("htId") long htId);

    // ── 列表与筛选（002 FR-11 / 003 FR-31；分页由 PageHelper-free 手写 LIMIT，size ≤100 由 service 保证）──
    @Select("""
            <script>
            SELECT t.* FROM hf_house_type t
            WHERE t.deleted = 0
            <if test="projectId != null"> AND t.project_id = #{projectId}</if>
            <if test="rooms != null"> AND t.rooms &gt;= #{rooms}</if>
            <if test="minArea != null"> AND t.gfa &gt;= #{minArea}</if>
            <if test="maxArea != null"> AND t.gfa &lt;= #{maxArea}</if>
            <if test="orientation != null"> AND (t.orientation = #{orientation} OR t.orientation = 'NS')</if>
            <if test="maxTotalPrice != null"> AND t.price_ref &lt;= #{maxTotalPrice}</if>
            ORDER BY t.gfa ASC LIMIT #{offset}, #{size}
            </script>
            """)
    List<HouseType> houseTypes(@Param("projectId") Long projectId, @Param("rooms") Integer rooms,
                               @Param("minArea") java.math.BigDecimal minArea, @Param("maxArea") java.math.BigDecimal maxArea,
                               @Param("orientation") String orientation, @Param("maxTotalPrice") java.math.BigDecimal maxTotalPrice,
                               @Param("offset") int offset, @Param("size") int size);

    // ── 评测记录（004 FR-51）───────────────────────────────────────────────
    @Insert("""
            INSERT INTO evaluation (user_id, house_type_id, house_id, set_id, set_version, template_code,
                                    total_score, level, missing_count, detail_json, ai_note, created_at)
            VALUES (#{userId}, #{houseTypeId}, #{houseId}, #{setId}, #{setVersion}, #{templateCode},
                    #{totalScore}, #{level}, #{missingCount}, #{detailJson}, #{aiNote}, NOW())
            """)
    int insertEvaluation(Evaluation e);

    @Select("SELECT * FROM evaluation WHERE id = #{id} AND deleted = 0")
    Evaluation evaluation(@Param("id") long id);

    @Select("""
            SELECT * FROM evaluation WHERE house_type_id = #{htId} AND deleted = 0
            ORDER BY id DESC LIMIT #{limit}
            """)
    List<Evaluation> evaluations(@Param("htId") long htId, @Param("limit") int limit);

    /** 列表徽标用：某户型在指定模板下的最新分数（避免一对多放大：MAX(id) 子查询） */
    @Select("""
            SELECT e.* FROM evaluation e
            JOIN (SELECT house_type_id, MAX(id) mid FROM evaluation
                  WHERE deleted = 0 AND template_code = #{tpl} GROUP BY house_type_id) x ON x.mid = e.id
            WHERE e.house_type_id = #{htId}
            """)
    Evaluation latestEvaluation(@Param("htId") long htId, @Param("tpl") String tpl);

    // ── 锁房与并发（003 FR-34~41 / AC-21）───────────────────────────────────
    /** 悲观锁：与 plan 003 §3 步骤 1 对应，必须在事务内调用 */
    @Select("SELECT * FROM hf_house WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    House houseForUpdate(@Param("id") long id);

    @Select("SELECT * FROM hf_house_lock WHERE house_id = #{houseId} AND status = 'ACTIVE' AND deleted = 0")
    HouseLock activeLock(@Param("houseId") long houseId);

    @Select("SELECT * FROM hf_house_lock WHERE intention_no = #{no} AND user_id = #{userId} AND status = 'ACTIVE' AND deleted = 0")
    HouseLock activeLockByIntention(@Param("no") String no, @Param("userId") long userId);

    /** 我的有效锁（AC-22 倒计时基准；一用户同时最多一条 ACTIVE，由业务保证） */
    @Select("SELECT * FROM hf_house_lock WHERE user_id = #{userId} AND status = 'ACTIVE' AND deleted = 0 ORDER BY id DESC LIMIT 1")
    HouseLock currentActiveLock(@Param("userId") long userId);

    @Insert("""
            INSERT INTO hf_house_lock (house_id, user_id, intention_no, status, expire_at, renew_count,
                                        create_snapshot, created_at)
            VALUES (#{houseId}, #{userId}, #{intentionNo}, #{status}, #{expireAt}, #{renewCount},
                    #{createSnapshot}, NOW())
            """)
    int insertLock(HouseLock lock);

    @Update("UPDATE hf_house_lock SET expire_at = #{expireAt}, renew_count = renew_count + 1 WHERE id = #{id}")
    int renewLock(@Param("id") long id, @Param("expireAt") LocalDateTime expireAt);

    @Update("UPDATE hf_house_lock SET status = #{to} WHERE id = #{id} AND status = #{from}")
    int updateLockStatus(@Param("id") long id, @Param("from") String from, @Param("to") String to);

    /** 乐观条件更新：影响行数 != 1 即说明状态被他人改变 → 回滚（AC-21 的第二重保护） */
    @Update("""
            UPDATE hf_house SET sale_status = #{to}, lock_expire_at = #{lockExpireAt}
            WHERE id = #{houseId} AND sale_status = #{from} AND deleted = 0
            """)
    int updateHouseStatusIf(@Param("houseId") long houseId, @Param("from") String from,
                            @Param("to") String to, @Param("lockExpireAt") LocalDateTime lockExpireAt);

    @Select("""
            SELECT * FROM hf_house_lock
            WHERE status = 'ACTIVE' AND expire_at < NOW() AND deleted = 0 ORDER BY expire_at LIMIT #{limit}
            """)
    List<HouseLock> expiredLocks(@Param("limit") int limit);

    /** 冲突时的同户型备选（AC-20/FR-41）：按与本层距离升序 */
    @Select("""
            SELECT * FROM hf_house WHERE house_type_id = #{htId} AND sale_status = 'AVAILABLE' AND deleted = 0
              AND id <> #{excludeId}
            ORDER BY ABS(floor_no - #{floorNo}) ASC, total_price ASC LIMIT 3
            """)
    List<House> alternatives(@Param("htId") long htId, @Param("floorNo") int floorNo, @Param("excludeId") long excludeId);

    // ── 收藏（003 FR-33）─────────────────────────────────────────────────
    @Select("SELECT COUNT(*) FROM hf_favorite WHERE user_id = #{userId} AND deleted = 0")
    int favoriteCount(@Param("userId") long userId);

    @Insert("""
            INSERT INTO hf_favorite (user_id, target_type, target_id, created_at)
            SELECT #{userId}, #{targetType}, #{targetId}, NOW()
            WHERE NOT EXISTS (SELECT 1 FROM hf_favorite WHERE user_id = #{userId}
                              AND target_type = #{targetType} AND target_id = #{targetId} AND deleted = 0)
            """)
    int insertFavoriteIfAbsent(@Param("userId") long userId, @Param("targetType") String targetType,
                               @Param("targetId") long targetId);

    // ── 用户画像（001 FR-04 / 007 I3 输入）──────────────────────────────────
    @Select("SELECT id, phone, role, budget_min, budget_max, family_structure, must_rooms "
            + "FROM sys_user WHERE id = #{id} AND deleted = 0")
    com.zhq.haofangzi.domain.entity.UserBrief userBrief(@Param("id") long id);
    // ── 预约看房（006）─────────────────────────────────────────────────────
    @Insert("""
            INSERT INTO appointment (user_id, consultant_id, project_id, house_type_id, house_id, intention_no,
                                     visit_date, visit_slot, party_size, contact_phone, remark, status, created_at)
            VALUES (#{userId}, #{consultantId}, #{projectId}, #{houseTypeId}, #{houseId}, #{intentionNo},
                    #{visitDate}, #{visitSlot}, #{partySize}, #{contactPhone}, #{remark}, #{status}, NOW())
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

    /** 同项目同日同时段已占用的预约数（含 PENDING/CONFIRMED）*/
    @Select("""
            SELECT COUNT(*) FROM appointment
            WHERE project_id = #{projectId} AND visit_date = #{visitDate} AND visit_slot = #{visitSlot}
              AND status IN ('PENDING', 'CONFIRMED') AND deleted = 0
            """)
    int slotUsed(@Param("projectId") long projectId, @Param("visitDate") java.time.LocalDate visitDate,
                 @Param("visitSlot") String visitSlot);

    /** 时段容量视图：未配置的项目用默认 6 位（FR-95）*/
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

    /** 状态流转（乐观：带 from 条件，影响行数 0 = 并发被别人改走 → 40931） */
    @Update("""
            UPDATE appointment SET status = #{to}, audit_remark = #{remark}, audit_by = #{auditBy}
            WHERE id = #{id} AND status = #{from} AND deleted = 0
            """)
    int updateAppointmentStatus(@Param("id") long id, @Param("from") String from, @Param("to") String to,
                                @Param("remark") String remark, @Param("auditBy") Long auditBy);

    @Update("UPDATE appointment SET notified_at = NOW() WHERE id = #{id} AND notified_at IS NULL")
    int markNotified(@Param("id") long id);

    @Update("UPDATE appointment SET reminded_2h_at = NOW() WHERE id = #{id} AND reminded_2h_at IS NULL")
    int markReminded2h(@Param("id") long id);

    /** 待提醒清单：提前 1 天（次日整段）与提前 2 小时（AC-53，幂等靠两个标记列） */
    @Select("""
            SELECT * FROM appointment
            WHERE deleted = 0 AND status IN ('PENDING','CONFIRMED')
              AND ((visit_date = DATE_ADD(CURDATE(), INTERVAL 1 DAY) AND notified_at IS NULL)
                OR (visit_date = CURDATE() AND reminded_2h_at IS NULL
                     AND TIMESTAMP(visit_date, LEFT(visit_slot, 5)) BETWEEN NOW() AND DATE_ADD(NOW(), INTERVAL 2 HOUR)))
            LIMIT 200
            """)
    java.util.List<com.zhq.haofangzi.domain.entity.Appointment> toRemind();

    @Insert("INSERT INTO sys_message (user_id, type, title, content, biz_id, created_at) "
            + "VALUES (#{userId}, #{type}, #{title}, #{content}, #{bizId}, NOW())")
    int insertMessage(@Param("userId") long userId, @Param("type") String type, @Param("title") String title,
                     @Param("content") String content, @Param("bizId") String bizId);

    // TODO(T-066)：activeRuleSet() + rulesOf(setId)，把 DB 规则集组装成 RuleSetView（当前由 RuleRepository 读 JSON 种子）
}
