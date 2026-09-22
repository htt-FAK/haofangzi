package com.zhq.haofangzi.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.File;
import javax.sql.DataSource;
import org.sqlite.SQLiteConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** SQLite 连接：外键、写锁等待、日期按 yyyy-MM-dd HH:mm:ss 与 SQL 里的 datetime() 对齐。 */
@Configuration
public class SqliteDataSourceConfig {

    static {
        File nativeDir = new File("target/sqlite-native");
        if (!nativeDir.exists()) {
            nativeDir.mkdirs();
        }
        System.setProperty("org.sqlite.tmpdir", nativeDir.getAbsolutePath());
    }

    @Bean
    public DataSource dataSource(@Value("${spring.datasource.url}") String url) {
        ensureParent(url);
        SQLiteConfig sqlite = new SQLiteConfig();
        sqlite.enforceForeignKeys(true);
        sqlite.setBusyTimeout(10000);
        sqlite.setJournalMode(SQLiteConfig.JournalMode.WAL);
        sqlite.setTransactionMode(SQLiteConfig.TransactionMode.IMMEDIATE);
        sqlite.setDateStringFormat("yyyy-MM-dd HH:mm:ss");
        HikariConfig pool = new HikariConfig();
        pool.setJdbcUrl(url);
        pool.setDriverClassName("org.sqlite.JDBC");
        pool.setPoolName("hf-sqlite");
        pool.setMaximumPoolSize(8);
        pool.setConnectionInitSql("PRAGMA busy_timeout=10000");
        sqlite.toProperties().forEach((k, v) -> pool.addDataSourceProperty(String.valueOf(k), String.valueOf(v)));
        return new HikariDataSource(pool);
    }

    /** SQLite 不会创建父目录，首次启动 ./data/haofangzi.db 会直接失败。 */
    private static void ensureParent(String url) {
        String path = url;
        int q = path.indexOf('?');
        if (q >= 0) {
            path = path.substring(0, q);
        }
        String prefix = "jdbc:sqlite:";
        if (path.startsWith(prefix)) {
            path = path.substring(prefix.length());
        }
        if (path.isBlank() || ":memory:".equals(path) || path.startsWith("file:") && path.contains("mode=memory")) {
            return;
        }
        File parent = new File(path).getAbsoluteFile().getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
    }
}
