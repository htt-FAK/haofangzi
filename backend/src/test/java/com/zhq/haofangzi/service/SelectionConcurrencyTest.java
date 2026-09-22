package com.zhq.haofangzi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhq.haofangzi.common.BizException;
import com.zhq.haofangzi.config.HfProperties;
import com.zhq.haofangzi.domain.dto.SelectionDto;
import com.zhq.haofangzi.domain.entity.House;
import com.zhq.haofangzi.mapper.CatalogMapper;
import com.zhq.haofangzi.mapper.SelectionMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 锁房并发（NFR-05）。这里伪造 Mapper 的条件更新，不连 MySQL：
 * 多线程可以同时读到 AVAILABLE，但 updateHouseStatusIf 只允许一次从 AVAILABLE 改为 LOCKED。
 */
@ExtendWith(MockitoExtension.class)
class SelectionConcurrencyTest {

    @Mock
    SelectionMapper locks;
    @Mock
    CatalogMapper catalog;

    @Test
    @DisplayName("20 个并发锁定同一房源，条件更新只成功 1 次")
    void onlyOneLockWins() throws Exception {
        AtomicReference<String> status = new AtomicReference<>("AVAILABLE");
        Object gate = new Object();
        when(locks.houseForUpdate(1L)).thenAnswer(inv -> {
            synchronized (gate) {
                House h = new House();
                h.setId(1L);
                h.setHouseTypeId(2L);
                h.setFloorNo(6);
                h.setSaleStatus(status.get());
                h.setArea(new BigDecimal("90"));
                h.setTotalPrice(new BigDecimal("1000000"));
                h.setUnitPrice(new BigDecimal("11000"));
                return h;
            }
        });
        when(locks.activeLock(1L)).thenReturn(null);
        when(locks.insertLock(any())).thenReturn(1);
        when(locks.updateHouseStatusIf(eq(1L), eq("AVAILABLE"), eq("LOCKED"), any())).thenAnswer(inv -> {
            synchronized (gate) {
                if ("AVAILABLE".equals(status.get())) {
                    status.set("LOCKED");
                    return 1;
                }
                return 0;
            }
        });
        when(locks.alternatives(anyLong(), anyInt(), anyLong())).thenReturn(List.of());

        SelectionService svc = new SelectionService(locks, catalog, new HfProperties(), new ObjectMapper());
        int n = 20;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> tasks = new java.util.ArrayList<>();
        for (int i = 0; i < n; i++) {
            long user = 100L + i;
            tasks.add(pool.submit(() -> {
                start.await();
                SelectionDto.LockCmd cmd = new SelectionDto.LockCmd();
                cmd.setHouseId(1L);
                cmd.setConfirm(true);
                try {
                    svc.lock(cmd, user);
                    return true;
                } catch (BizException e) {
                    return false;
                }
            }));
        }
        start.countDown();
        int wins = 0;
        for (Future<Boolean> f : tasks) {
            if (f.get()) {
                wins++;
            }
        }
        pool.shutdown();
        assertThat(wins).isEqualTo(1);
    }
}
