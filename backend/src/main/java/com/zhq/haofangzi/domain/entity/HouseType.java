package com.zhq.haofangzi.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 户型（表 {@code hf_house_type}）—— 评估主体（spec 002/004；数据字典 DD-F05）。
 * 这些列即"指标输入"，与 {@code specs/002/.../geometry.schema.json} 字段一一映射，
 * 新增指标列需同步：schema.sql → 本实体 → geometry 契约 → 004 指标登记（宪法第五条）。
 */
@Data
@TableName("hf_house_type")
public class HouseType {

    @TableId
    private Long id;
    private Long projectId;
    private String code;
    private String name;

    /** 建筑面积 ㎡ */
    private BigDecimal gfa;
    /** 套内面积 ㎡ */
    private BigDecimal privateArea;
    private Integer rooms;
    private Integer halls;
    private Integer baths;
    /** S/SE/SW/E/W/N/NS */
    private String orientation;
    private BigDecimal bay;
    private BigDecimal depth;
    private BigDecimal ceilingHeight;
    private Integer balconyCount;
    private BigDecimal windowArea;
    private BigDecimal kitchenArea;
    private BigDecimal bathArea;
    private BigDecimal masterBedroomArea;
    private BigDecimal circulationLen;
    private BigDecimal corridorRatio;
    private BigDecimal irrRatio;
    private BigDecimal storageWallLen;
    private Integer nearRoad;
    private Integer adjacentElevator;
    private String style;
    private String planSvgUrl;
    private String modelGlbUrl;
    private String panoUrl;
    private String outlineJson;
    private String extJson;
    private BigDecimal priceRef;
    private String remark;

    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;

    /** 得房率：spec 004 的 UTIL_usable_ratio 输入 */
    public BigDecimal usableRatio() {
        if (gfa == null || privateArea == null || gfa.signum() <= 0) {
            return null;
        }
        return privateArea.divide(gfa, 3, java.math.RoundingMode.HALF_UP);
    }

    public boolean southFacing() {
        return "S".equals(orientation) || "NS".equals(orientation) || "SE".equals(orientation) || "SW".equals(orientation);
    }
}
