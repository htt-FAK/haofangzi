package com.zhq.haofangzi.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 锁房/意向记录（表 {@code hf_house_lock}，spec 003；house_id 唯一索引是并发兜底） */
@Data
@TableName("hf_house_lock")
public class HouseLock {

    @TableId
    private Long id;
    private Long houseId;
    private Long userId;
    private String intentionNo;
    /** ACTIVE / EXPIRED / CONVERTED / CANCELED */
    private String status;
    private LocalDateTime expireAt;
    private Integer renewCount;
    /** 锁定时房源快照 JSON（确认页展示，避免房源改价后文案漂移，FR-34） */
    private String createSnapshot;
    private LocalDateTime createdAt;
    @TableLogic
    private Integer deleted;
}
