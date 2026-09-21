package com.zhq.haofangzi.domain.entity;

import java.math.BigDecimal;
import lombok.Data;

/** 用户最小视图（评估与经济维度、AI 顾问输入用；<b>不含口令</b>，手机号仅内部使用、出口一律脱敏） */
@Data
public class UserBrief {
    private Long id;
    private String phone;
    private String role;
    private BigDecimal budgetMin;
    private BigDecimal budgetMax;
    private String familyStructure;
    private Integer mustRooms;
}
