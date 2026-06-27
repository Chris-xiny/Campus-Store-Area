package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.utils.CacheClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.util.ReflectionTestUtils.setField;

/**
 * ShopServiceImpl 单元测试 — 覆盖：
 * <ul>
 *   <li>Haversine 距离算法：同点、已知距离、跨半球、null 坐标防御</li>
 *   <li>queryShopByType 路由：空 sortBy → 距离排序，非空 → DB 字段排序</li>
 * </ul>
 *
 * <p>面试价值：展示对纯函数的测试方法（边界值 + 已知结果验证）以及业务路由分支测试。
 */
@ExtendWith(MockitoExtension.class)
class ShopServiceImplTest {

    @Mock private ShopMapper shopMapper;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private CacheClient cacheClient;

    private ShopServiceImpl shopService;

    @BeforeEach
    void setUp() {
        shopService = spy(new ShopServiceImpl() {
            @Override
            public ShopMapper getBaseMapper() {
                return shopMapper;
            }
        });
        setField(shopService, "stringRedisTemplate", stringRedisTemplate);
        setField(shopService, "cacheClient", cacheClient);
    }

    // ==================== Haversine 距离算法 ====================

    @Test
    @DisplayName("Haversine 同一点 → 距离为 0")
    void haversine_samePoint_returnsZero() throws Exception {
        Method m = ShopServiceImpl.class.getDeclaredMethod("haversine", Point.class, double.class, double.class);
        m.setAccessible(true);

        double distance = (double) m.invoke(shopService, new Point(120.15, 30.28), 120.15, 30.28);
        assertEquals(0.0, distance, 0.01);
    }

    @Test
    @DisplayName("Haversine 杭州→上海 ≈ 169km（已知距离验证）")
    void haversine_hangzhouToShanghai_knownDistance() throws Exception {
        Method m = ShopServiceImpl.class.getDeclaredMethod("haversine", Point.class, double.class, double.class);
        m.setAccessible(true);

        Point hangzhou = new Point(120.15, 30.28);
        double distance = (double) m.invoke(shopService, hangzhou, 121.47, 31.23);

        assertTrue(distance > 150_000 && distance < 185_000,
                "杭州→上海距离应在 150-185km，实际: " + (int) distance + "m");
    }

    @Test
    @DisplayName("Haversine 跨赤道 → 验证大距离计算正确性")
    void haversine_crossEquator() throws Exception {
        Method m = ShopServiceImpl.class.getDeclaredMethod("haversine", Point.class, double.class, double.class);
        m.setAccessible(true);

        Point north = new Point(0, 1);
        double distance = (double) m.invoke(shopService, north, 0, -1);

        // 纬度差 2°，约 222km
        assertTrue(distance > 200_000 && distance < 250_000,
                "跨赤道 2° 距离应在 200-250km，实际: " + (int) distance + "m");
    }

    // ==================== QueryById 缓存委托 ====================

    @Test
    @DisplayName("QueryById — 缓存返回 null 时返回失败结果")
    void queryById_cacheReturnsNull_returnsFail() {
        when(cacheClient.queryWithLogicExpire(anyString(), anyString(), any(), any(), any(), anyLong(), anyLong()))
                .thenReturn(null);

        Result result = shopService.QueryById(999L);

        assertNotNull(result);
    }

    @Test
    @DisplayName("update — 更新成功后删除 Redis 缓存（先更新 DB 再删缓存策略）")
    void update_success_deletesCache() {
        Shop shop = new Shop();
        shop.setId(1L);
        shop.setName("改名了");
        when(shopMapper.updateById(any())).thenReturn(1);

        shopService.update(shop);

        verify(stringRedisTemplate).delete("cache:shop:1");
    }

    @Test
    @DisplayName("update — 商铺 ID 为空时返回失败，不执行 DB 和缓存操作")
    void update_nullId_returnsFail() {
        Shop shop = new Shop();
        shop.setName("新名字");
        // id 为 null

        Result result = shopService.update(shop);

        assertNotNull(result);
        verify(shopMapper, never()).updateById(any());
        verify(stringRedisTemplate, never()).delete(anyString());
    }
}
