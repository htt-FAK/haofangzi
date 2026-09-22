package com.zhq.haofangzi.mapper;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper {

@Select("SELECT id, phone, role, budget_min, budget_max, family_structure, must_rooms "
            + "FROM sys_user WHERE id = #{id} AND deleted = 0")
    com.zhq.haofangzi.domain.entity.UserBrief userBrief(@Param("id") long id);

@Select("SELECT * FROM sys_user WHERE phone = #{phone} AND deleted = 0 LIMIT 1")
    com.zhq.haofangzi.domain.entity.SysUser userByPhone(@Param("phone") String phone);

@Select("SELECT * FROM sys_user WHERE id = #{id} AND deleted = 0")
    com.zhq.haofangzi.domain.entity.SysUser userById(@Param("id") long id);

@Insert("""
            INSERT INTO sys_user (phone, password, nickname, role, status, created_at, updated_at)
            VALUES (#{phone}, #{password}, #{nickname}, #{role}, #{status}, datetime('now','localtime'), datetime('now','localtime'))
            """)
    @org.apache.ibatis.annotations.Options(useGeneratedKeys = true, keyProperty = "id")
    int insertUser(com.zhq.haofangzi.domain.entity.SysUser user);

@Select("""
            SELECT id, phone, nickname, role, status
            FROM sys_user WHERE deleted = 0
            ORDER BY id LIMIT #{size} OFFSET #{offset}
            """)
    java.util.List<java.util.Map<String, Object>> users(@Param("offset") int offset, @Param("size") int size);

@Select("SELECT COUNT(*) FROM sys_user WHERE deleted = 0")
    long userCount();

@Update("UPDATE sys_user SET status = #{status} WHERE id = #{id} AND deleted = 0")
    int updateUserStatus(@Param("id") long id, @Param("status") int status);

@Update("UPDATE sys_user SET password = #{password} WHERE id = #{id} AND deleted = 0")
    int updateUserPassword(@Param("id") long id, @Param("password") String password);

@Update("""
            UPDATE sys_user
            SET budget_min = #{budgetMin}, budget_max = #{budgetMax}, family_structure = #{familyStructure},
                must_rooms = #{mustRooms}, prefer_orientation = #{preferOrientation}, prefer_tags = #{preferTags}
            WHERE id = #{id} AND deleted = 0
            """)
    int updateProfile(@Param("id") long id, @Param("budgetMin") java.math.BigDecimal budgetMin,
                      @Param("budgetMax") java.math.BigDecimal budgetMax, @Param("familyStructure") String familyStructure,
                      @Param("mustRooms") Integer mustRooms, @Param("preferOrientation") String preferOrientation,
                      @Param("preferTags") String preferTags);
}
