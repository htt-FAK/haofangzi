package com.zhq.haofangzi.domain.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 预约看房单（表 {@code appointment}，spec 006）。
 * 手机号库内明文（业务需要，顾问要打电话），但<b>所有出口 VO 必须脱敏</b>（FR-102 / TC-SEC-07）。
 */
@Data
@TableName("appointment")
public class Appointment {

    @TableId
    private Long id;
    private Long userId;
    private Long consultantId;
    private Long projectId;
    private Long houseTypeId;
    private Long houseId;
    /** 由模拟选房转来的意向号（FR-90），可为空 */
    private String intentionNo;
    private LocalDate visitDate;
    /** 09:00-10:00 …，一段一位 */
    private String visitSlot;
    private Integer partySize;
    private String contactPhone;
    private String remark;
    /** PENDING/CONFIRMED/ARRIVED/COMPLETED/CANCELED */
    private String status;
    private String cancelReason;
    private String auditRemark;
    private Long auditBy;
    /** 提前 1 天提醒幂等标记（FR-97） */
    private LocalDateTime notifiedAt;
    /** 提前 2 小时提醒幂等标记 */
    private LocalDateTime reminded2hAt;
    /** 到访后顾问回写的关注维度标签，供画像与 AI 顾问使用 */
    private String focusTags;
    @TableField("created_at")
    private LocalDateTime createdAt;
    @TableField("updated_at")
    private LocalDateTime updatedAt;
    @TableLogic
    private Integer deleted;

    // ── join 出来的展示字段（不参与写入）──
    @TableField(exist = false)
    private String projectName;
    @TableField(exist = false)
    private String houseTypeName;
    @TableField(exist = false)
    private String customerName;

    public com.zhq.haofangzi.domain.enums.AppointmentStatus status() {
        return com.zhq.haofangzi.domain.enums.AppointmentStatus.of(status);
    }

    /** 看房开始时刻（日期 + 时段起始），提醒任务与"是否已过期"判定用 */
    public LocalDateTime visitStart() {
        String start = visitSlot == null ? "09:00" : visitSlot.split("-")[0];
        return LocalDateTime.of(visitDate, java.sql.Time.valueOf(start + ":00").toLocalTime());
    }
}
