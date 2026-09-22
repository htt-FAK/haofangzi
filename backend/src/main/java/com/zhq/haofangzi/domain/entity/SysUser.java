package com.zhq.haofangzi.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import lombok.Data;

/** 用户（表 {@code sys_user}）。口令仅内部使用，出口 VO 不得带 password。 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId
    private Long id;
    private String phone;
    private String password;
    private String nickname;
    private String role;
    /** 1 正常 0 停用 */
    private Integer status;
    private BigDecimal budgetMin;
    private BigDecimal budgetMax;
    private String familyStructure;
    private Integer mustRooms;
    private String preferOrientation;
    private String preferTags;
    @TableLogic
    private Integer deleted;
}
