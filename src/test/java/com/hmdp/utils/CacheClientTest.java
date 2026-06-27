package com.hmdp.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.util.ReflectionTestUtils.setField;

/**
 * CacheClient 单元测试 — 覆盖 Redis 缓存核心机制：
 * <ul>
 *   <li>缓存雪崩防护：randomTtl 在基准 TTL ±20% 范围内随机偏移</li>
 *   <li>互斥锁：getLock / unLock 基于 Redis SETNX + DELETE</li>
 *   <li>缓存穿透防护：queryWithMutex 空值缓存短 TTL</li>
 * </ul>
 *
 * <p>面试价值：展示对 Redis 缓存三大问题（穿透/击穿/雪崩）的理解及测试方法。
 */
@ExtendWith(MockitoExtension.class)
class CacheClientTest {

    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOps;
    @Mock private AsyncTaskUtils asyncTaskUtils;

    private CacheClient cacheClient;

    @BeforeEach
    void setUp() {
        cacheClient = new CacheClient();
        setField(cacheClient, "stringRedisTemplate", stringRedisTemplate);
        setField(cacheClient, "asyncTaskUtils", asyncTaskUtils);
    }

    // ==================== randomTtl 缓存雪崩防护 ====================

    @Test
    @DisplayName("randomTtl 结果在基准 TTL 的 ±20% 范围内")
    void randomTtl_withinBounds() throws Exception {
        Method m = CacheClient.class.getDeclaredMethod("randomTtl", long.class);
        m.setAccessible(true);

        long baseTtl = 100L;
        for (int i = 0; i < 500; i++) {
            long result = (long) m.invoke(cacheClient, baseTtl);
            assertTrue(result >= 80 && result <= 120,
                    "randomTtl=" + result + " 超出 [80, 120] 范围");
        }
    }

    @Test
    @DisplayName("randomTtl 多次调用产生不同值（验证随机性）")
    void randomTtl_producesVariation() throws Exception {
        Method m = CacheClient.class.getDeclaredMethod("randomTtl", long.class);
        m.setAccessible(true);

        long baseTtl = 1000L;
        long first = (long) m.invoke(cacheClient, baseTtl);
        boolean hasDifferent = false;
        for (int i = 0; i < 50; i++) {
            if ((long) m.invoke(cacheClient, baseTtl) != first) {
                hasDifferent = true;
                break;
            }
        }
        assertTrue(hasDifferent, "50 次调用结果全部相同，随机性存疑");
    }

    // ==================== getLock / unLock 互斥锁 ====================

    @Test
    @DisplayName("getLock 成功 — setIfAbsent 返回 true")
    void getLock_success() throws Exception {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(eq("lock:test:1"), eq("1"), eq(10L), eq(TimeUnit.SECONDS)))
                .thenReturn(true);

        Method m = CacheClient.class.getDeclaredMethod("getLock", String.class, Long.class);
        m.setAccessible(true);
        boolean result = (boolean) m.invoke(cacheClient, "lock:test:1", 10L);

        assertTrue(result);
    }

    @Test
    @DisplayName("getLock 失败 — setIfAbsent 返回 false（已被其他线程持锁）")
    void getLock_fails() throws Exception {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(any(), any(), anyLong(), any(TimeUnit.class)))
                .thenReturn(false);

        Method m = CacheClient.class.getDeclaredMethod("getLock", String.class, Long.class);
        m.setAccessible(true);
        boolean result = (boolean) m.invoke(cacheClient, "lock:test:1", 10L);

        assertFalse(result);
    }

    @Test
    @DisplayName("unLock — 调用 Redis delete 释放锁")
    void unLock_callsDelete() throws Exception {
        Method m = CacheClient.class.getDeclaredMethod("unLock", String.class);
        m.setAccessible(true);
        m.invoke(cacheClient, "lock:test:1");

        verify(stringRedisTemplate).delete("lock:test:1");
    }

    // ==================== queryWithMutex 缓存穿透防护 ====================

    @Test
    @DisplayName("queryWithMutex — Redis 命中缓存直接返回，不查 DB")
    void queryWithMutex_cacheHit() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("cache:shop:1")).thenReturn("{\"id\":1,\"name\":\"测试商铺\"}");

        String result = cacheClient.queryWithMutex(
                "cache:shop:", "lock:shop:", 1L, String.class,
                id -> "should-not-be-called", 30L, 10L);

        assertNotNull(result);
        assertTrue(result.contains("测试商铺"));
    }

    @Test
    @DisplayName("queryWithMutex — 空值缓存（防穿透）：Redis 返回空串 → 返回 null，不查 DB")
    void queryWithMutex_nullValueCached_returnsNull() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("cache:shop:999")).thenReturn("");

        String result = cacheClient.queryWithMutex(
                "cache:shop:", "lock:shop:", 999L, String.class,
                id -> "should-not-be-called", 30L, 10L);

        assertNull(result, "空值缓存应返回 null");
    }
}
