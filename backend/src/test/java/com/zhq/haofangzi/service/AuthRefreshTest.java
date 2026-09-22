package com.zhq.haofangzi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zhq.haofangzi.common.BizException;
import com.zhq.haofangzi.common.ErrorCode;
import com.zhq.haofangzi.config.HfProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuthRefreshTest {

    @Test
    @DisplayName("刷新令牌只能使用一次")
    void refreshOnce() {
        HfProperties props = new HfProperties();
        props.getJwt().setSecret("demo-only-please-change-me-32bytes!!");
        AuthLookup lookup = new AuthLookup(props);
        String refresh = lookup.issueRefresh(8L, "BUYER");
        String access = lookup.refreshOnce(refresh);
        assertThat(access).isNotBlank();
        assertThat(lookup.parseOrEmpty(access).get("typ")).isEqualTo("access");
        assertThatThrownBy(() -> lookup.refreshOnce(refresh))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).getCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }
}
