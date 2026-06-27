package com.hmdp.canal;

import com.hmdp.entity.Shop;
import com.hmdp.utils.CacheClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.StringRedisTemplate;
import top.javatool.canal.client.annotation.CanalTable;
import top.javatool.canal.client.handler.EntryHandler;

import javax.annotation.Resource;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;

/**
 * Canal 监听 tb_shop 表的 binlog 变更事件，自动失效缓存。
 * <p>
 * 业务代码只需更新 DB，无需手动删缓存；Canal 捕获 ROW 格式 binlog 后推送变更事件，
 * Handler 自动清除对应的 Caffeine L1 + Redis L2 缓存，实现缓存一致性与业务解耦。
 */
@Slf4j
@Component
@CanalTable("tb_shop")
public class ShopCanalHandler implements EntryHandler<Shop> {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;

    @Override
    public void insert(Shop shop) {
        // 新增店铺无需清缓存（还没有缓存）
        log.debug("Canal 检测到店铺新增: shopId={}", shop.getId());
    }

    @Override
    public void update(Shop before, Shop after) {
        Long id = after.getId();
        log.info("Canal 检测到店铺更新，自动失效缓存: shopId={}", id);
        // 清除 L1 Caffeine 本地缓存
        cacheClient.invalidateLocalCache(CACHE_SHOP_KEY, id);
        // 清除 L2 Redis 缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
    }

    @Override
    public void delete(Shop shop) {
        Long id = shop.getId();
        log.info("Canal 检测到店铺删除，自动失效缓存: shopId={}", id);
        cacheClient.invalidateLocalCache(CACHE_SHOP_KEY, id);
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
    }
}
