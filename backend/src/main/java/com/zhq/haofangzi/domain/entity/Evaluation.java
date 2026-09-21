package com.zhq.haofangzi.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 评测记录（表 {@code evaluation}）。
 * <b>快照不可变</b>：{@code detailJson} 冻结指标值、命中的档、权重、依据与规则版本，
 * 规则改版不影响历史分数（FR-51 / AC-32 / 宪法第五条 3）。
 */
@Data
@TableName("evaluation")
public class Evaluation {

    @TableId
    private Long id;
    private Long userId;
    private Long houseTypeId;
    private Long houseId;
    private Long setId;
    private String setVersion;
    private String templateCode;
    private BigDecimal totalScore;
    private String level;
    private Integer missingCount;
    private String detailJson;
    private String aiNote;
    private LocalDateTime createdAt;
    @TableLogic
    private Integer deleted;
}
