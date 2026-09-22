package com.zhq.haofangzi.domain.entity;

import lombok.Data;

/** 写入 eval_rule_set 时的参数对象（管理端复制草案）。 */
@Data
public class RuleSetRow {
    private Long id;
    private String name;
    private String version;
    private String source;
    private Integer confirmed;
    private String templateCode;
}
