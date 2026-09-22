package com.zhq.haofangzi.mapper;

import com.zhq.haofangzi.domain.entity.House;
import com.zhq.haofangzi.domain.entity.HouseLock;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SelectionMapper {

@Select("SELECT * FROM hf_house WHERE id = #{id} AND deleted = 0")
    House houseForUpdate(@Param("id") long id);

@Select("SELECT * FROM hf_house_lock WHERE house_id = #{houseId} AND status = 'ACTIVE' AND deleted = 0")
    HouseLock activeLock(@Param("houseId") long houseId);

@Select("SELECT * FROM hf_house_lock WHERE intention_no = #{no} AND user_id = #{userId} AND status = 'ACTIVE' AND deleted = 0")
    HouseLock activeLockByIntention(@Param("no") String no, @Param("userId") long userId);

@Select("SELECT * FROM hf_house_lock WHERE user_id = #{userId} AND status = 'ACTIVE' AND deleted = 0 ORDER BY id DESC LIMIT 1")
    HouseLock currentActiveLock(@Param("userId") long userId);

@Insert("""
            INSERT INTO hf_house_lock (house_id, user_id, intention_no, status, expire_at, renew_count,
                                        create_snapshot, created_at)
            VALUES (#{houseId}, #{userId}, #{intentionNo}, #{status}, #{expireAt}, #{renewCount},
                    #{createSnapshot}, datetime('now','localtime'))
            """)
    int insertLock(HouseLock lock);

@Update("UPDATE hf_house_lock SET expire_at = #{expireAt}, renew_count = renew_count + 1 WHERE id = #{id}")
    int renewLock(@Param("id") long id, @Param("expireAt") LocalDateTime expireAt);

@Update("UPDATE hf_house_lock SET status = #{to} WHERE id = #{id} AND status = #{from}")
    int updateLockStatus(@Param("id") long id, @Param("from") String from, @Param("to") String to);

@Update("""
            UPDATE hf_house SET sale_status = #{to}, lock_expire_at = #{lockExpireAt}
            WHERE id = #{houseId} AND sale_status = #{from} AND deleted = 0
            """)
    int updateHouseStatusIf(@Param("houseId") long houseId, @Param("from") String from,
                            @Param("to") String to, @Param("lockExpireAt") LocalDateTime lockExpireAt);

@Select("""
            SELECT * FROM hf_house_lock
            WHERE status = 'ACTIVE' AND expire_at < datetime('now','localtime') AND deleted = 0 ORDER BY expire_at LIMIT #{limit}
            """)
    List<HouseLock> expiredLocks(@Param("limit") int limit);

@Select("""
            SELECT * FROM hf_house WHERE house_type_id = #{htId} AND sale_status = 'AVAILABLE' AND deleted = 0
              AND id <> #{excludeId}
            ORDER BY ABS(floor_no - #{floorNo}) ASC, total_price ASC LIMIT 3
            """)
    List<House> alternatives(@Param("htId") long htId, @Param("floorNo") int floorNo, @Param("excludeId") long excludeId);

@Select("SELECT COUNT(*) FROM hf_favorite WHERE user_id = #{userId} AND deleted = 0")
    int favoriteCount(@Param("userId") long userId);

@Insert("""
            INSERT INTO hf_favorite (user_id, target_type, target_id, created_at)
            SELECT #{userId}, #{targetType}, #{targetId}, datetime('now','localtime')
            WHERE NOT EXISTS (SELECT 1 FROM hf_favorite WHERE user_id = #{userId}
                              AND target_type = #{targetType} AND target_id = #{targetId} AND deleted = 0)
            """)
    int insertFavoriteIfAbsent(@Param("userId") long userId, @Param("targetType") String targetType,
                               @Param("targetId") long targetId);
}
