package com.hmdp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启用 Spring 定时任务调度。
 * 用于 MQ 消费失败补偿定时任务（MqFailedOrderServiceImpl.compensate）。
 */
@Configuration
@EnableScheduling
public class ScheduleConfig {
}
