package com.zhq.haofangzi.config;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * 空库时执行 SQLite 建表和种子。放在 Bean 初始化阶段，避免定时任务先于建表执行。
 * 房源批量数据由 {@link SqliteHouseSeed} 生成。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqliteBootstrap implements org.springframework.beans.factory.InitializingBean {

    private final DataSource dataSource;
    private final SqliteHouseSeed houses;

    @Override
    public void afterPropertiesSet() throws Exception {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.execute("""
                    CREATE TABLE IF NOT EXISTS ai_chat_message (
                      id INTEGER PRIMARY KEY,
                      user_id INTEGER NOT NULL,
                      role TEXT NOT NULL,
                      content TEXT NOT NULL,
                      created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            st.execute("CREATE INDEX IF NOT EXISTS idx_chat_user ON ai_chat_message(user_id, id)");
            if (tableExists(st, "sys_user")) {
                for (String sql : statements("sqlite/extra-types.sql")) {
                    st.execute(sql);
                }
                houses.fillIfEmpty(c);
                return;
            }
            for (String sql : statements("sqlite/schema.sql")) {
                st.execute(sql);
            }
            for (String sql : statements("sqlite/seed.sql")) {
                st.execute(sql);
            }
            houses.fillIfEmpty(c);
            log.info("SQLite 已初始化：schema + 种子 + 房源");
        }
    }

    private static boolean tableExists(Statement st, String name) throws Exception {
        try (ResultSet rs = st.executeQuery(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = '" + name + "'")) {
            return rs.next();
        }
    }

    private static List<String> statements(String classpath) throws Exception {
        StringBuilder cur = new StringBuilder();
        List<String> out = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource(classpath).getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().startsWith("--")) {
                    continue;
                }
                cur.append(line).append('\n');
                if (line.trim().endsWith(";")) {
                    String sql = cur.toString().trim();
                    if (!sql.isEmpty()) {
                        out.add(sql);
                    }
                    cur.setLength(0);
                }
            }
        }
        return out;
    }
}
