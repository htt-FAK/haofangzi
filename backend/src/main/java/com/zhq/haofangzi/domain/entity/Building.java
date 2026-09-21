package com.zhq.haofangzi.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Data;

/** 楼栋（表 {@code hf_building}）：提供梯户比与南向遮挡，用于 QUIET/GREEN/LIGHT 指标 */
@Data
@TableName("hf_building")
public class Building {

    @TableId
    private Long id;
    private Long projectId;
    private String code;
    private Integer totalFloor;
    private Integer unitsPerFloor;
    private Integer elevatorCount;
    /** 1 = 无遮挡；用于低层采光修正 LIGHT_floor_occlusion */
    private BigDecimal southOcclusion;
    private BigDecimal greenRate;
    @TableLogic
    private Integer deleted;

    /** 梯户比（spec 004 QUIET_elevator_ratio 输入） */
    public double elevatorRatio() {
        int elev = elevatorCount == null || elevatorCount < 1 ? 1 : elevatorCount;
        int units = unitsPerFloor == null || unitsPerFloor < 1 ? 2 : unitsPerFloor;
        return units * 1.0 / elev;
    }
}
