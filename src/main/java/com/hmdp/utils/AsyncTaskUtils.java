package com.hmdp.utils;

import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TTL;
import static java.lang.Thread.sleep;

/**
 * 异步任务工具类：集中管理需要异步执行的任务，复用 AsyncConfig 中声明的线程池。
 */
@Slf4j
@Component
public class AsyncTaskUtils {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 异步重建缓存（配合"逻辑过期"方案使用）。
     * 由 cacheExecutor 线程池执行。
     */
    @Async("cacheExecutor")
    public <R, Id> void rebuildCacheAsync(Id id, Class<R> type, Function<Id, R> dbFallBack, String lockKey) {
        try {
            // 模拟重建耗时（实际是 DB 查询 + 业务处理）
            sleep(200);
            R r = dbFallBack.apply(id);
            RedisData redisData = new RedisData(LocalDateTime.now().plusMinutes(CACHE_SHOP_TTL), r);
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
            log.debug("缓存重建完成, key={}", CACHE_SHOP_KEY + id);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("缓存重建被中断, key={}", CACHE_SHOP_KEY + id);
        } catch (Exception e) {
            log.error("缓存重建异常, key={}", CACHE_SHOP_KEY + id, e);
        } finally {
            // 无论重建是否成功，保证锁一定会释放，否则其他线程永远拿不到锁
            stringRedisTemplate.delete(lockKey);
        }
    }
}
