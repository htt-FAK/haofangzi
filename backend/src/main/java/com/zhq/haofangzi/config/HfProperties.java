package com.zhq.haofangzi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code haofangzi.*} 配置绑定（业务参数外置，见 application.yml；宪法第六条"配置优于代码"）。
 * 锁房时长、收藏上限、权重缓存、AI 超时等都是演示时可调参数。
 */
@Data
@ConfigurationProperties(prefix = "haofangzi")
public class HfProperties {

    private Jwt jwt = new Jwt();
    private Lock lock = new Lock();
    private Intention intention = new Intention();
    private Security security = new Security();
    private Eval eval = new Eval();
    private Ai ai = new Ai();
    private Media media = new Media();
    private int favoriteLimit = 50;
    /** 演示环境未指派顾问时，预约默认派给的顾问账号 id（seed 数据里的 13800000003） */
    private Long demoConsultantId = 3L;

    @Data
    public static class Jwt {
        private String secret;
        private long ttlSeconds = 7200;
    }

    @Data
    public static class Lock {
        private int ttlMinutes = 10;
        private int renewMax = 2;
        private int totalMaxMinutes = 20;
        private int scanSeconds = 30;
    }

    @Data
    public static class Intention {
        private int ttlHours = 24;
    }

    @Data
    public static class Security {
        private int loginFailLimit = 5;
        private int loginLockSeconds = 900;
    }

    @Data
    public static class Eval {
        private int cacheMax = 2000;
        private int cacheTtlMinutes = 10;
        private String ruleSource = "classpath:rules/default-rules.json";
    }

    @Data
    public static class Ai {
        private boolean enabled = true;
        private String baseUrl;
        private String model;
        private String apiKey;
        private long timeoutMs = 60000;
        private int maxRetry = 1;
        private long cacheSeconds = 60;
        /** Qwen3.5-4B 默认启用思考模式 (Thinking Mode) */
        private boolean enableThinking = true;
        /** 原生 256K 上下文 (262,144 tokens) */
        private int contextLength = 262144;
        private int maxTokens = 4096;
        private double temperature = 0.7;
        private double topP = 0.8;
        private double repetitionPenalty = 1.05;
        /** 原生多模态统一视觉语言基础能力 */
        private boolean multimodal = true;
    }

    @Data
    public static class Media {
        private String root = "./upload";
        private long signTtlSeconds = 600;
    }
}
