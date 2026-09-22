package com.zhq.haofangzi.service;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zhq.haofangzi.common.BizException;
import com.zhq.haofangzi.common.ErrorCode;
import com.zhq.haofangzi.common.MaskUtil;
import com.zhq.haofangzi.config.HfProperties;
import com.zhq.haofangzi.domain.entity.SysUser;
import com.zhq.haofangzi.mapper.UserMapper;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 注册 / 登录 / 画像完整度（spec 001 T-004~T-006）。
 * 失败锁定走进程内 Caffeine（宪法：不引入 Redis）。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");
    private static final Pattern STRONG = Pattern.compile("^(?=.*[A-Za-z])(?=.*\\d).{8,}$");
    public static final String DEMO_SMS = "123456";

    private final UserMapper users;
    private final AuthLookup tokens;
    private final PasswordEncoder passwords;
    private final HfProperties props;

    private final Cache<String, String> captchas = Caffeine.newBuilder()
            .maximumSize(2000).expireAfterWrite(Duration.ofMinutes(5)).build();
    private final Cache<String, String> smsCodes = Caffeine.newBuilder()
            .maximumSize(2000).expireAfterWrite(Duration.ofMinutes(5)).build();
    private final ConcurrentHashMap<String, FailState> fails = new ConcurrentHashMap<>();

    public Map<String, Object> captcha() {
        LineCaptcha cap = CaptchaUtil.createLineCaptcha(120, 40, 4, 20);
        String id = UUID.randomUUID().toString().replace("-", "");
        captchas.put(id, cap.getCode());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("captchaId", id);
        m.put("imageBase64", "data:image/png;base64," + cap.getImageBase64());
        return m;
    }

    public void sendSms(String phone) {
        requireAccount(phone);
        smsCodes.put(phone, DEMO_SMS);
    }

    @Transactional
    public void register(Map<String, String> body) {
        String phone = trim(body.get("phone"));
        if (!PHONE.matcher(phone).matches()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写 11 位手机号");
        }
        consumeCaptcha(body.get("captchaId"), body.get("captcha"));
        String password = body.get("password") == null ? "" : body.get("password");
        if (!STRONG.matcher(password).matches()) {
            throw new BizException(ErrorCode.PASSWORD_WEAK, "口令至少 8 位，且同时包含字母和数字");
        }
        if (users.userByPhone(phone) != null) {
            throw new BizException(ErrorCode.PHONE_REGISTERED, "该手机号已注册");
        }
        SysUser u = new SysUser();
        u.setPhone(phone);
        u.setPassword(passwords.encode(password));
        u.setNickname(blankToNull(body.get("nickname")));
        u.setRole("BUYER");
        u.setStatus(1);
        users.insertUser(u);
    }

    public Map<String, Object> login(Map<String, String> body) {
        String phone = trim(body.get("phone"));
        requireAccount(phone);
        assertNotLocked(phone);

        boolean sms = body.get("smsCode") != null && !body.get("smsCode").isBlank();
        SysUser user = users.userByPhone(phone);
        boolean ok;
        if (sms) {
            ok = DEMO_SMS.equals(body.get("smsCode")) && DEMO_SMS.equals(smsCodes.getIfPresent(phone));
            if (ok) {
                smsCodes.invalidate(phone);
            }
        } else {
            consumeCaptcha(body.get("captchaId"), body.get("captcha"));
            ok = user != null && passwords.matches(body.get("password") == null ? "" : body.get("password"),
                    user.getPassword());
        }
        if (!ok || user == null || user.getStatus() != null && user.getStatus() == 0) {
            onFail(phone);
            throw new BizException(ErrorCode.BAD_CREDENTIAL, "账号或口令不正确");
        }
        fails.remove(phone);
        String role = user.getRole() == null ? "BUYER" : user.getRole();
        String token = tokens.issue(user.getId(), role);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("refreshToken", tokens.issueRefresh(user.getId(), role));
        data.put("expiresIn", props.getJwt().getTtlSeconds());
        data.put("user", toVo(user));
        return data;
    }

    public Map<String, Object> refresh(String refreshToken) {
        String token = tokens.refreshOnce(refreshToken);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("expiresIn", props.getJwt().getTtlSeconds());
        return data;
    }

    @Transactional
    public Map<String, Object> updateProfile(long userId, Map<String, Object> body) {
        SysUser user = users.userById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        BigDecimal min = decimal(body.get("budgetMin"));
        BigDecimal max = decimal(body.get("budgetMax"));
        if (min != null && max != null && max.compareTo(min) < 0) {
            throw new BizException(ErrorCode.BUDGET_RANGE, "预算上限不能小于下限");
        }
        Integer rooms = body.get("mustRooms") instanceof Number n ? n.intValue() : null;
        if (rooms != null && (rooms < 1 || rooms > 6)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "居室数为 1~6");
        }
        String family = blankToNull(body.get("familyStructure") == null ? null : String.valueOf(body.get("familyStructure")));
        String orient = blankToNull(body.get("preferOrientation") == null ? null : String.valueOf(body.get("preferOrientation")));
        String tags = blankToNull(body.get("preferTags") == null ? null : String.valueOf(body.get("preferTags")));
        users.updateProfile(userId, min, max, family, rooms, orient, tags);
        user.setBudgetMin(min);
        user.setBudgetMax(max);
        user.setFamilyStructure(family);
        user.setMustRooms(rooms);
        user.setPreferOrientation(orient);
        user.setPreferTags(tags);
        return toVo(user);
    }

    public Map<String, Object> me(long userId) {
        SysUser user = users.userById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.UNAUTHORIZED, "请先登录");
        }
        return toVo(user);
    }

    /** 单测读取尚未消费的验证码明文 */
    String peekCaptcha(String captchaId) {
        return captchas.getIfPresent(captchaId);
    }

    public static int completeness(SysUser u) {
        int n = 0;
        if (u.getBudgetMin() != null && u.getBudgetMax() != null) {
            n += 25;
        }
        if (u.getFamilyStructure() != null && !u.getFamilyStructure().isBlank()) {
            n += 25;
        }
        if (u.getMustRooms() != null) {
            n += 25;
        }
        if (u.getPreferOrientation() != null && !u.getPreferOrientation().isBlank()) {
            n += 25;
        }
        return n;
    }

    private Map<String, Object> toVo(SysUser u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", u.getId());
        m.put("phone", MaskUtil.mobile(u.getPhone()));
        m.put("nickname", u.getNickname() == null ? "用户" : u.getNickname());
        m.put("role", u.getRole());
        m.put("profileCompleteness", completeness(u));
        m.put("budgetMin", u.getBudgetMin());
        m.put("budgetMax", u.getBudgetMax());
        m.put("familyStructure", u.getFamilyStructure());
        m.put("mustRooms", u.getMustRooms());
        return m;
    }

    private void consumeCaptcha(String id, String answer) {
        if (id == null || id.isBlank()) {
            throw new BizException(ErrorCode.CAPTCHA_ERROR, "请先获取图形验证码");
        }
        String expect = captchas.getIfPresent(id);
        captchas.invalidate(id);
        if (expect == null || answer == null || !expect.equalsIgnoreCase(answer.strip())) {
            throw new BizException(ErrorCode.CAPTCHA_ERROR, "图形验证码错误或已过期");
        }
    }

    private void requireAccount(String phone) {
        if (phone == null || phone.isBlank()) {
            throw new BizException(ErrorCode.PARAM_INVALID, "请填写账号");
        }
    }

    private void assertNotLocked(String phone) {
        FailState st = fails.get(phone);
        if (st == null || st.lockUntil == 0) {
            return;
        }
        long remain = (st.lockUntil - System.currentTimeMillis()) / 1000;
        if (remain > 0) {
            throw new BizException(ErrorCode.LOGIN_LOCKED, "失败次数过多，请稍后再试",
                    Map.of("lockSeconds", remain));
        }
        fails.remove(phone);
    }

    private void onFail(String phone) {
        int limit = props.getSecurity().getLoginFailLimit();
        int lockSec = props.getSecurity().getLoginLockSeconds();
        FailState st = fails.computeIfAbsent(phone, k -> new FailState());
        st.count++;
        if (st.count >= limit) {
            st.lockUntil = System.currentTimeMillis() + lockSec * 1000L;
            throw new BizException(ErrorCode.LOGIN_LOCKED, "失败次数过多，账号已锁定",
                    Map.of("lockSeconds", (long) lockSec));
        }
    }

    private static BigDecimal decimal(Object o) {
        if (o == null || String.valueOf(o).isBlank()) {
            return null;
        }
        if (o instanceof BigDecimal b) {
            return b;
        }
        if (o instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return new BigDecimal(String.valueOf(o).strip());
    }

    private static String trim(String s) {
        return s == null ? "" : s.strip();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    static final class FailState {
        int count;
        long lockUntil;
    }
}
