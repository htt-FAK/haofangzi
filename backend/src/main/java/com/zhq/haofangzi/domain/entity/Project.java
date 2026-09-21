package com.zhq.haofangzi.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Data;

/** 楼盘项目（表 {@code hf_project}）；avgPrice 为 COST_unit_price_gap 的基准 */
@Data
@TableName("hf_project")
public class Project {

    @TableId
    private Long id;
    private String name;
    private String district;
    private String address;
    private BigDecimal lng;
    private BigDecimal lat;
    private String developer;
    private BigDecimal avgPrice;
    private Integer deliveryYear;
    private String tag;
    private String coverUrl;
    private String summary;
    @TableLogic
    private Integer deleted;
}
