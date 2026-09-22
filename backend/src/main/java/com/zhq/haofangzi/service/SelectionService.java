package com.zhq.haofangzi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhq.haofangzi.common.BizException;
import com.zhq.haofangzi.common.ErrorCode;
import com.zhq.haofangzi.config.HfProperties;
import com.zhq.haofangzi.domain.dto.SelectionDto;
import com.zhq.haofangzi.domain.entity.House;
import com.zhq.haofangzi.domain.entity.HouseLock;
import com.zhq.haofangzi.domain.enums.SaleStatus;
import com.zhq.haofangzi.mapper.CatalogMapper;
import com.zhq.haofangzi.mapper.SelectionMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 模拟选房（M3）—— 全系统唯一有并发冲突的用例（NFR-05 / AC-21 / TC-P-01）。
 *
 * <p><b>三重保护</b>（答辩重点，见 docs/04 §6.1）：
 * <ol>
 *   <li>SQLite 写锁把并发事务串行化（没有 MySQL 的行锁）；</li>
 *   <li>条件更新 {@code WHERE sale_status='AVAILABLE'}：affected≠1 即回滚，防中间态；</li>
 *   <li>唯一索引 {@code uk_lock_house}：并发脏写的最后兜底。</li>
 * </ol>
 * 超时回收用定时任务（课程约束：不引入 MQ，见宪法第六条与 plan 003 §3）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SelectionService {

    private static final DateTimeFormatter INTENT_DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final SelectionMapper locks;
    private final CatalogMapper catalog;
    private final HfProperties props;
    private final ObjectMapper mapper;

    @Transactional(rollbackFor = Exception.class)
    public SelectionDto.LockResult lock(SelectionDto.LockCmd cmd, long userId) {
        if (!cmd.isConfirm()) {
            throw new BizException(ErrorCode.NO_CONFIRM, "请先阅读并勾选选房规则说明");   // FR-34
        }
        House house = locks.houseForUpdate(cmd.getHouseId());                              // 保护 1
        if (house == null) {
            throw new BizException(ErrorCode.HOUSE_NOT_FOUND, "房源不存在");
        }
        HouseLock active = locks.activeLock(house.getId());
        LocalDateTime now = LocalDateTime.now();

        if (active != null && active.getExpireAt().isAfter(now)) {
            if (!Long.valueOf(userId).equals(active.getUserId())) {
                throw new BizException(ErrorCode.LOCK_CONFLICT, "该房源已被其他用户锁定", alternatives(house));  // AC-20
            }
            return renew(house, active);                                                // R3 本人续期
        }
        if (!house.status().selectable()) {
            throw new BizException(ErrorCode.NOT_SELECTABLE, "该房源当前不可选（已售或已预留）"); // AC-27
        }

        LocalDateTime expireAt = now.plusMinutes(props.getLock().getTtlMinutes());
        HouseLock lock = newLock(house.getId(), userId, expireAt, snapshot(house));
        locks.insertLock(lock);                                                            // 保护 3（唯一索引）
        int updated = locks.updateHouseStatusIf(house.getId(), SaleStatus.AVAILABLE.name(),
                SaleStatus.LOCKED.name(), expireAt);                                    // 保护 2
        if (updated != 1) {
            throw new BizException(ErrorCode.LOCK_CONFLICT, "房源状态已变化，请重新选择", alternatives(house));
        }
        SelectionDto.LockResult r = SelectionDto.LockResult.of(lock.getIntentionNo(), lock.getIntentionNo(), expireAt, 0, house);
        r.setLockNo("LK" + lock.getIntentionNo().substring(2));
        return r;
    }

    @Transactional
    public SelectionDto.LockResult renewByIntention(String intentionNo, long userId) {
        HouseLock lock = locks.activeLockByIntention(intentionNo, userId);
        if (lock == null) {
            throw new BizException(ErrorCode.NOT_SELECTABLE, "锁定不存在或已过期");
        }
        House house = locks.houseForUpdate(lock.getHouseId());
        return renew(house, lock);
    }

    /** 本人重复提交 = 续期，最多 {@code renewMax} 次且总时长 ≤ totalMaxMinutes（FR-36 / AC-23） */
    @Transactional
    public SelectionDto.LockResult renew(House house, HouseLock lock) {
        if (lock.getRenewCount() >= props.getLock().getRenewMax()) {
            throw new BizException(ErrorCode.RENEW_LIMIT, "续期次数已用完，请尽快转意向或释放");
        }
        // 总占用不超过 totalMaxMinutes：首次 10min + 续期 2×5min 的上限语义
        long alreadyUsed = (long) props.getLock().getTtlMinutes() * (1 + lock.getRenewCount());
        long remaining = Math.max(0, props.getLock().getTotalMaxMinutes() - alreadyUsed);
        LocalDateTime expireAt = LocalDateTime.now().plusMinutes(Math.min(props.getLock().getTtlMinutes(), remaining));
        locks.renewLock(lock.getId(), expireAt);
        locks.updateHouseStatusIf(house.getId(), SaleStatus.LOCKED.name(), SaleStatus.LOCKED.name(), expireAt);
        return SelectionDto.LockResult.of(lock.getIntentionNo(), lock.getIntentionNo(), expireAt,
                lock.getRenewCount() + 1, house);
    }

    @Transactional
    public void cancel(String lockNo, long userId) {
        HouseLock lock = locks.activeLockByIntention(lockNo, userId);
        if (lock == null) {
            throw new BizException(ErrorCode.NOT_SELECTABLE, "锁定不存在或已过期");
        }
        locks.updateLockStatus(lock.getId(), "ACTIVE", "CANCELED");
        locks.updateHouseStatusIf(lock.getHouseId(), SaleStatus.LOCKED.name(), SaleStatus.AVAILABLE.name(), null);
    }

    /** 转意向：LOCKED → RESERVED，保留 24h，可被预约（006）引用（FR-37 / AC-24） */
    @Transactional
    public SelectionDto.LockResult convert(String intentionNo, long userId) {
        HouseLock lock = locks.activeLockByIntention(intentionNo, userId);
        if (lock == null) {
            throw new BizException(ErrorCode.NOT_SELECTABLE, "意向不存在或已失效");
        }
        House house = locks.houseForUpdate(lock.getHouseId());
        locks.updateLockStatus(lock.getId(), "ACTIVE", "CONVERTED");
        LocalDateTime expire = LocalDateTime.now().plusHours(props.getIntention().getTtlHours());
        locks.updateHouseStatusIf(house.getId(), SaleStatus.LOCKED.name(), SaleStatus.RESERVED.name(), expire);
        return SelectionDto.LockResult.of(intentionNo, intentionNo, expire, lock.getRenewCount(), house);
    }

    public SelectionDto.LockResult currentLock(long userId) {
        HouseLock l = locks.currentActiveLock(userId);
        if (l == null || l.getExpireAt().isBefore(LocalDateTime.now())) {
            return null;                                                               // 前端据此隐藏倒计时（AC-22）
        }
        return SelectionDto.LockResult.of(l.getIntentionNo(), l.getIntentionNo(), l.getExpireAt(),
                l.getRenewCount(), catalog.house(l.getHouseId()));
    }

    // ── 收藏（FR-33：幂等 + 上限 50）───────────────────────────────────────
    @Transactional
    public void addFavorite(long userId, String targetType, long targetId) {
        // 先尝试幂等插入（唯一索引 + WHERE NOT EXISTS 双保险），已存在时返回 0 行即视为成功
        if (locks.insertFavoriteIfAbsent(userId, targetType, targetId) == 0) {
            return;
        }
        int count = locks.favoriteCount(userId);
        if (count > props.getFavoriteLimit()) {
            // 越界则回滚本次收藏（事务内即回滚），给出明确提示（AC-25）
            throw new BizException(ErrorCode.FAVORITE_LIMIT, "收藏已达 " + props.getFavoriteLimit() + " 条上限，请先清理");
        }
    }

    /** 锁超时回收（FR-38 / AC-22）。条件更新保证重复执行与多实例下的幂等。 */
    @Scheduled(fixedDelayString = "${haofangzi.lock.scan-seconds:30}000")
    @Transactional
    public int releaseExpiredLocks() {
        List<HouseLock> expired = locks.expiredLocks(200);
        int released = 0;
        for (HouseLock l : expired) {
            if (locks.updateHouseStatusIf(l.getHouseId(), SaleStatus.LOCKED.name(), SaleStatus.AVAILABLE.name(), null) == 1
                    && locks.updateLockStatus(l.getId(), "ACTIVE", "EXPIRED") == 1) {
                released++;
                log.info("房源 {} 锁定超时已释放（user={}）", l.getHouseId(), l.getUserId());   // 通知：TODO(T-051) 写 sys_message
            }
        }
        return released;
    }

    private List<SelectionDto.HouseSnap> alternatives(House house) {
        return locks.alternatives(house.getHouseTypeId(), house.getFloorNo(), house.getId()).stream()
                .map(SelectionDto.HouseSnap::of).toList();
    }

    private HouseLock newLock(long houseId, long userId, LocalDateTime expireAt, String snapshot) {
        HouseLock l = new HouseLock();
        l.setHouseId(houseId);
        l.setUserId(userId);
        l.setIntentionNo("YX" + LocalDateTime.now().format(INTENT_DAY)
                + String.format("%06d", ThreadLocalRandom.current().nextInt(1_000_000)));
        l.setStatus("ACTIVE");
        l.setExpireAt(expireAt);
        l.setRenewCount(0);
        l.setCreateSnapshot(snapshot);
        return l;
    }

    private String snapshot(House h) {
        try {
            return mapper.writeValueAsString(SelectionDto.HouseSnap.of(h));            // 含户型名需 T-044 补 join
        } catch (Exception e) {
            throw new BizException(ErrorCode.INTERNAL, "锁房快照生成失败");
        }
    }
}
