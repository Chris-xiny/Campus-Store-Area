package com.hmdp;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis HyperLogLog 百万级基数统计集成测试。
 * <p>
 * HyperLogLog 是 Redis 提供的概率型基数统计数据结构：
 * <ul>
 *   <li>仅需 ~12KB 内存即可统计最多 2^64 个不同元素</li>
 *   <li>标准误差 0.81%，实际通常 < 2%</li>
 *   <li>PFADD 添加元素、PFCOUNT 获取基数、PFMERGE 合并多个 HLL</li>
 * </ul>
 *
 * <p>典型应用场景：网站 UV 统计、直播间在线人数、消息已读人数等"不需要精确值，但数据量巨大"的场景。
 */
@Slf4j
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HyperLogLogTest {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final String HLL_KEY = "test:hll:uv";
    private static final String HLL_DAY1 = "test:hll:day1";
    private static final String HLL_DAY2 = "test:hll:day2";
    private static final String HLL_MERGED = "test:hll:merged";

    /** 百万级元素数量 */
    private static final long TOTAL_ELEMENTS = 1_000_000L;

    /** 允许的最大误差（HyperLogLog 理论误差 0.81%，实际放宽到 3%） */
    private static final double MAX_ERROR_RATE = 0.03;

    // ==================== 生命周期：一次性插入百万数据 ====================

    @BeforeAll
    void insertMillionElements() {
        stringRedisTemplate.delete(HLL_KEY);
        log.info("开始插入 {} 条 HyperLogLog 数据...", TOTAL_ELEMENTS);
        long start = System.currentTimeMillis();

        stringRedisTemplate.executePipelined(
                (org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                    byte[] key = HLL_KEY.getBytes();
                    for (long i = 0; i < TOTAL_ELEMENTS; i++) {
                        connection.hyperLogLogCommands().pfAdd(key, ("user:" + i).getBytes());
                    }
                    return null;
                });

        long elapsed = System.currentTimeMillis() - start;
        log.info("百万级 PFADD Pipeline 插入完成, 耗时: {}ms", elapsed);
    }

    @AfterAll
    void cleanupAll() {
        stringRedisTemplate.delete(HLL_KEY);
        stringRedisTemplate.delete(HLL_DAY1);
        stringRedisTemplate.delete(HLL_DAY2);
        stringRedisTemplate.delete(HLL_MERGED);
        log.info("测试清理完成，所有 HyperLogLog key 已删除");
    }

    // ==================== 百万级基数统计 ====================

    @Test
    @Order(1)
    @DisplayName("百万级 PFCOUNT — 基数估算误差在 3% 以内")
    void millionCount_accuracy() {
        Long estimated = stringRedisTemplate.opsForHyperLogLog().size(HLL_KEY);
        assertNotNull(estimated);

        double error = Math.abs(estimated - TOTAL_ELEMENTS) / (double) TOTAL_ELEMENTS;
        log.info("HyperLogLog 基数估算: {} (实际: {}, 误差率: {}%)",
                estimated, TOTAL_ELEMENTS, String.format("%.2f", error * 100));

        // 误差应在 3% 以内（HyperLogLog 理论误差 0.81%）
        assertTrue(error < MAX_ERROR_RATE,
                String.format("误差率 %.2f%% 超过阈值 %.2f%%", error * 100, MAX_ERROR_RATE * 100));

        // 至少应该在 95 万 ~ 105 万之间
        assertTrue(estimated >= 950_000 && estimated <= 1_050_000,
                "估算值 " + estimated + " 不在合理范围 [950000, 1050000]");
    }

}
