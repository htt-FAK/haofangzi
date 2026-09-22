package com.zhq.haofangzi.mapper;

import com.zhq.haofangzi.domain.entity.Building;
import com.zhq.haofangzi.domain.entity.House;
import com.zhq.haofangzi.domain.entity.HouseType;
import com.zhq.haofangzi.domain.entity.Project;
import com.zhq.haofangzi.domain.entity.Room;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CatalogMapper {

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
            ORDER BY t.gfa ASC LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    List<HouseType> houseTypes(@Param("projectId") Long projectId, @Param("rooms") Integer rooms,
                               @Param("minArea") java.math.BigDecimal minArea, @Param("maxArea") java.math.BigDecimal maxArea,
                               @Param("orientation") String orientation, @Param("maxTotalPrice") java.math.BigDecimal maxTotalPrice,
                               @Param("offset") int offset, @Param("size") int size);

@Select("""
            <script>
            SELECT COUNT(*) FROM hf_house_type t
            WHERE t.deleted = 0
            <if test="projectId != null"> AND t.project_id = #{projectId}</if>
            <if test="rooms != null"> AND t.rooms &gt;= #{rooms}</if>
            <if test="minArea != null"> AND t.gfa &gt;= #{minArea}</if>
            <if test="maxArea != null"> AND t.gfa &lt;= #{maxArea}</if>
            <if test="orientation != null"> AND (t.orientation = #{orientation} OR t.orientation = 'NS')</if>
            <if test="maxTotalPrice != null"> AND t.price_ref &lt;= #{maxTotalPrice}</if>
            </script>
            """)
    long houseTypeCount(@Param("projectId") Long projectId, @Param("rooms") Integer rooms,
                        @Param("minArea") java.math.BigDecimal minArea, @Param("maxArea") java.math.BigDecimal maxArea,
                        @Param("orientation") String orientation, @Param("maxTotalPrice") java.math.BigDecimal maxTotalPrice);

@Select("""
            <script>
            SELECT h.*, b.code AS buildingCode FROM hf_house h
            LEFT JOIN hf_building b ON b.id = h.building_id
            WHERE h.deleted = 0
            <if test="houseTypeId != null"> AND h.house_type_id = #{houseTypeId}</if>
            <if test="buildingId != null"> AND h.building_id = #{buildingId}</if>
            <if test="saleStatus != null"> AND h.sale_status = #{saleStatus}</if>
            ORDER BY h.floor_no ASC, h.room_no ASC
            LIMIT #{size} OFFSET #{offset}
            </script>
            """)
    java.util.List<java.util.Map<String, Object>> houses(@Param("houseTypeId") Long houseTypeId,
                                                         @Param("buildingId") Long buildingId,
                                                         @Param("saleStatus") String saleStatus,
                                                         @Param("offset") int offset, @Param("size") int size);

@Select("""
            <script>
            SELECT COUNT(*) FROM hf_house h WHERE h.deleted = 0
            <if test="houseTypeId != null"> AND h.house_type_id = #{houseTypeId}</if>
            <if test="buildingId != null"> AND h.building_id = #{buildingId}</if>
            <if test="saleStatus != null"> AND h.sale_status = #{saleStatus}</if>
            </script>
            """)
    long houseCount(@Param("houseTypeId") Long houseTypeId, @Param("buildingId") Long buildingId,
                    @Param("saleStatus") String saleStatus);

@Insert("""
            INSERT INTO hf_house (building_id, house_type_id, unit_code, floor_no, room_no, area, unit_price,
                                  total_price, sale_status, created_at)
            VALUES (#{buildingId}, #{houseTypeId}, '1单元', #{floorNo}, #{roomNo}, #{area}, #{unitPrice},
                    #{totalPrice}, 'AVAILABLE', datetime('now','localtime'))
            """)
    int insertHouse(@Param("buildingId") long buildingId, @Param("houseTypeId") long houseTypeId,
                    @Param("floorNo") int floorNo, @Param("roomNo") String roomNo,
                    @Param("area") java.math.BigDecimal area, @Param("unitPrice") java.math.BigDecimal unitPrice,
                    @Param("totalPrice") java.math.BigDecimal totalPrice);
}
