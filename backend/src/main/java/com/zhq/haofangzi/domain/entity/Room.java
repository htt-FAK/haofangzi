package com.zhq.haofangzi.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.math.RoundingMode;
import lombok.Data;

/**
 * 房间构件（表 {@code hf_room}）：2D/3D 绘制与采光/通风/动线指标的共同数据源。
 * 坐标单位米，原点为包围盒西北角（与 geometry.schema.json 一致）。
 */
@Data
@TableName("hf_room")
public class Room {

    @TableId
    private Long id;
    private Long houseTypeId;
    private String name;
    /** LIVING/DINING/MASTER/SECOND/STUDY/KITCHEN/BATH/BALCONY/CORRIDOR/UTILITY/ENTRY */
    private String category;
    private BigDecimal x;
    private BigDecimal y;
    private BigDecimal w;
    private BigDecimal h;
    /** 受控冗余：与 w×h 偏差必须 ≤5%（AC-15 在此校验） */
    private BigDecimal area;
    /** 该空间主要采光面朝向；NONE 表示无采光面 */
    private String orientation;
    /** 外窗洞口面积 ㎡；0 → 暗房间 */
    private BigDecimal windowArea;
    private BigDecimal windowOpenableRatio;
    private String doorsJson;
    private String windowsJson;
    @TableLogic
    private Integer deleted;

    public boolean hasWindow() {
        return windowArea != null && windowArea.signum() > 0;
    }

    /** 窗地比（采光核心指标 LIGHT_wfa 的单空间输入） */
    public BigDecimal windowFloorRatio() {
        if (area == null || area.signum() <= 0 || windowArea == null) {
            return null;
        }
        return windowArea.divide(area, 3, RoundingMode.HALF_UP);
    }

    public boolean livingSpace() {
        return "LIVING".equals(category) || "DINING".equals(category)
                || "MASTER".equals(category) || "SECOND".equals(category) || "STUDY".equals(category);
    }

    public boolean wet() {
        return "KITCHEN".equals(category) || "BATH".equals(category);
    }

    /** 几何投影区间 [x, x+w]（南北通透 / 动线连通判定使用） */
    public double[] xRange() {
        return new double[]{x.doubleValue(), x.add(w).doubleValue()};
    }

    public double[] yRange() {
        return new double[]{y.doubleValue(), y.add(h).doubleValue()};
    }
}
