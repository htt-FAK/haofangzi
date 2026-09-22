package com.zhq.haofangzi;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 好房子在线选房与户型智能评估系统 · 启动类（课程设计 课题十一）
 *
 * <p>包结构与设计文档 1:1（宪法第八条）：controller / service / engine / integration / mapper / domain。
 * 域划分见根 {@code plan.md} 第 2 节，规格见 {@code specs/00X-*} 目录。
 *
 * <p>启动顺序见 {@code docs/03-概要设计} §5：迁移校验 → 导入默认规则集 → 注册 21 个指标计算器 →
 * 装载规则缓存与分位样本 → 启动调度池（锁回收 / 意向过期 / 预约提醒）。
 */
@SpringBootApplication
@MapperScan("com.zhq.haofangzi.mapper")
@ConfigurationPropertiesScan("com.zhq.haofangzi.config")
@EnableScheduling
@EnableAsync
public class HaofangziApplication {

    public static void main(String[] args) {
        SpringApplication.run(HaofangziApplication.class, args);
    }
}
