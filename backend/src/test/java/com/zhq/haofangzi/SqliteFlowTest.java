package com.zhq.haofangzi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zhq.haofangzi.common.BizException;
import com.zhq.haofangzi.domain.dto.SelectionDto;
import com.zhq.haofangzi.service.SelectionService;
import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SqliteFlowTest {

    @Autowired
    MockMvc mvc;
    @Autowired
    DataSource dataSource;
    @Autowired
    SelectionService selection;

    @Test
    void houseTypeListAndUnauthorizedLock() throws Exception {
        mvc.perform(get("/api/house-types").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.data.records").isArray());
        mvc.perform(post("/api/selection/locks")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"houseId\":1,\"confirm\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void oneHouseLocksOnceOnSqlite() throws Exception {
        long houseId;
        String room = String.valueOf(System.nanoTime() % 100000);
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("""
                    INSERT INTO hf_house (building_id, house_type_id, unit_code, floor_no, room_no, area, unit_price, total_price, sale_status)
                    VALUES (1, 1, '9单元', 8, '%s', 90, 9000, 810000, 'AVAILABLE')
                    """.formatted(room));
            try (var rs = st.executeQuery("SELECT last_insert_rowid()")) {
                rs.next();
                houseId = rs.getLong(1);
            }
        }
        int n = 20;
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> tasks = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            long user = 10_000L + i;
            tasks.add(pool.submit(() -> {
                start.await();
                SelectionDto.LockCmd cmd = new SelectionDto.LockCmd();
                cmd.setHouseId(houseId);
                cmd.setConfirm(true);
                try {
                    selection.lock(cmd, user);
                    return true;
                } catch (BizException e) {
                    return false;
                }
            }));
        }
        start.countDown();
        int wins = 0;
        for (Future<Boolean> task : tasks) {
            if (task.get()) {
                wins++;
            }
        }
        pool.shutdown();
        assertThat(wins).isEqualTo(1);
    }
}
