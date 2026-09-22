package com.zhq.haofangzi.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 对比报告快照（表 {@code compare_report}，spec 005） */
@Data
@TableName("compare_report")
public class CompareReport {

    @TableId
    private Long id;
    private Long userId;
    private String title;
    private String houseTypeIds;
    private String setVersion;
    private String templateCode;
    private String matrixJson;
    private String conclusion;
    private Integer aiGenerated;
    private String shareToken;
    private LocalDateTime expireAt;
    private Integer viewCount;
    private String consultantNote;
    private Integer shown;
    private LocalDateTime createdAt;
    @TableLogic
    private Integer deleted;
}
