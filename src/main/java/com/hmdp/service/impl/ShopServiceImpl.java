package com.hmdp.service.impl;


import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;


import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private CacheClient cacheClient;


    /**
     * 根据id查询商铺信息
     *
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @Override
    public Result QueryById(Long id) {
        //用逻辑过期解决缓存穿透
        Shop shop = cacheClient.queryWithLogicExpire(CACHE_SHOP_KEY, LOCK_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, LOCK_SHOP_TTL);
        if (shop == null) {
            return Result.fail("商户信息不存在");
        }
        return Result.ok(shop);
    }

    /**
     * 新增商铺并同步 GEO 数据。
     */
    @Override
    public Result saveShop(Shop shop) {
        save(shop);
        if (shop.getX() != null && shop.getY() != null && shop.getTypeId() != null) {
            geoAdd(shop);
        }
        return Result.ok(shop.getId());
    }


    /**
     * 根据id更改商铺信息
     *
     * @param shop 商铺信息
     */
    @Transactional
    @Override
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("商铺id不能为空");
        }
        //1.先更新数据库
        updateById(shop);
        //2.删除 L1 本地缓存 + L2 Redis 缓存（Canal binlog 方案待官方兼容 MySQL 8.x 后启用）
        cacheClient.invalidateLocalCache(CACHE_SHOP_KEY, id);
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        //3.同步GEO数据（如果坐标变更）
        if (shop.getX() != null && shop.getY() != null && shop.getTypeId() != null) {
            geoAdd(shop);
        }
        return Result.ok();

    }

    /**
     * 根据商铺类型分页查询，支持按距离/人气/评分排序。
     */
    @Override
    public Result queryShopByType(Integer typeId, Integer current, String sortBy, Double x, Double y) {
        if (StrUtil.isBlank(sortBy)) {
            // 按距离排序 —— 走 Redis GEORADIUS
            return Result.ok(queryByDistance(typeId, current, x, y));
        } else {
            // 按人气/评分排序 —— 走 DB ORDER BY
            return Result.ok(queryByField(typeId, current, sortBy, x, y));
        }
    }

    // ==================== GEO 数据初始化 ====================

    /**
     * 应用启动时将所有商铺坐标加载到 Redis GEO，按 typeId 分组。
     * key 格式：shop:geo:{typeId}，member 为 shopId。
     */
    @PostConstruct
    public void loadShopGeoData() {
        List<Shop> shops = list();
        if (CollUtil.isEmpty(shops)) return;
        Map<Long, List<Shop>> grouped = shops.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        for (Map.Entry<Long, List<Shop>> entry : grouped.entrySet()) {
            String key = SHOP_GEO_KEY + entry.getKey();
            // 幂等：先清空再写入
            stringRedisTemplate.delete(key);
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>();
            for (Shop shop : entry.getValue()) {
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                ));
            }
            stringRedisTemplate.opsForGeo().add(key, locations);
        }
        log.info("GEO 数据加载完成，共 {} 个商铺", shops.size());
    }

    /**
     * 新增商铺时同步写入 GEO 数据。
     */
    private void geoAdd(Shop shop) {
        String key = SHOP_GEO_KEY + shop.getTypeId();
        stringRedisTemplate.opsForGeo().add(key,
                new Point(shop.getX(), shop.getY()),
                shop.getId().toString());
    }

    // ==================== 按距离排序（Redis GEORADIUS） ====================

    private List<Shop> queryByDistance(Integer typeId, Integer current, Double x, Double y) {
        int offset = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        String key = SHOP_GEO_KEY + typeId;

        // GEORADIUS：按距离升序，limit 取 offset + pageSize 条
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo()
                .radius(key,
                        new Circle(new Point(x, y),
                                new Distance(5000, RedisGeoCommands.DistanceUnit.METERS)),
                        RedisGeoCommands.GeoRadiusCommandArgs
                                .newGeoRadiusArgs()
                                .includeDistance()
                                .sortAscending()
                                .limit(offset + SystemConstants.DEFAULT_PAGE_SIZE)
                );

        if (results == null || results.getContent().isEmpty()) {
            return Collections.emptyList();
        }

        // 提取 shopId 和距离，保持 GEORADIUS 返回顺序
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = results.getContent();
        List<Long> ids = new ArrayList<>();
        Map<Long, Double> distanceMap = new LinkedHashMap<>();
        for (GeoResult<RedisGeoCommands.GeoLocation<String>> r : content) {
            Long id = Long.valueOf(r.getContent().getName());
            ids.add(id);
            distanceMap.put(id, r.getDistance().getValue());
        }

        // 批量查 DB，按 GEORADIUS 顺序组装
        Map<Long, Shop> shopMap = listByIds(ids).stream()
                .collect(Collectors.toMap(Shop::getId, s -> s));
        List<Shop> list = new ArrayList<>();
        for (Long id : ids) {
            Shop shop = shopMap.get(id);
            if (shop != null) {
                shop.setDistance(distanceMap.get(id));
                list.add(shop);
            }
        }

        // 截取当前页
        return list.size() > offset
                ? list.subList(offset, Math.min(list.size(), offset + SystemConstants.DEFAULT_PAGE_SIZE))
                : Collections.emptyList();
    }

    // ==================== 按人气/评分排序（DB ORDER BY） ====================

    private List<Shop> queryByField(Integer typeId, Integer current, String sortBy, Double x, Double y) {
        String orderBy = "comments".equals(sortBy) ? "comments" : "score";
        Page<Shop> page = query()
                .eq("type_id", typeId)
                .orderByDesc(orderBy)
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
        List<Shop> records = page.getRecords();
        // 计算每个商铺到用户的距离
        fillDistance(records, x, y);
        return records;
    }

    // ==================== 距离计算（Haversine 公式） ====================

    /**
     * 批量填充商铺到用户的距离（米），避免 N 次 GEODIST 网络往返。
     */
    private void fillDistance(List<Shop> shops, Double x, Double y) {
        if (CollUtil.isEmpty(shops) || x == null || y == null) return;
        Point userPoint = new Point(x, y);
        for (Shop shop : shops) {
            if (shop.getX() != null && shop.getY() != null) {
                shop.setDistance(haversine(userPoint, shop.getX(), shop.getY()));
            }
        }
    }

    /**
     * Haversine 公式：根据两点经纬度计算球面距离（米）。
     */
    private double haversine(Point p, double shopX, double shopY) {
        double R = 6371000; // 地球半径（米）
        double dLat = Math.toRadians(shopY - p.getY());
        double dLon = Math.toRadians(shopX - p.getX());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(p.getY())) * Math.cos(Math.toRadians(shopY))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
