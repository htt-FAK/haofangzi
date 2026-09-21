package com.zhq.haofangzi.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.zhq.haofangzi.domain.enums.SaleStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/** 房源/一套房（表 {@code hf_house}，销控状态见 {@link SaleStatus}；spec 003） */
@Data
@TableName("hf_house")
public class House {

    @TableId
    private Long id;
    private Long buildingId;
    private Long houseTypeId;
    private String unitCode;
    private Integer floorNo;
    private String roomNo;
    private String direction;
    private BigDecimal area;
    private BigDecimal unitPrice;
    /** 受控冗余 = area × unitPrice（展示高频，写入即定；数据字典 DI-10） */
    private BigDecimal totalPrice;
    private String saleStatus;
    private String viewLevel;
    private Integer noiseLevel;
    private LocalDateTime lockExpireAt;
    private LocalDateTime soldAt;
    @TableLogic
    private Integer deleted;

    public SaleStatus status() {
        return saleStatus == null ? SaleStatus.AVAILABLE : SaleStatus.valueOf(saleStatus);
    }
}
