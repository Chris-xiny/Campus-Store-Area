package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Shop> {

    /**
     * 根据id查询商铺信息
     * @param id 商铺id
     * @return 商铺详情数据
     */
    Result QueryById(Long id);

    /**
     * 新增商铺并同步 GEO 数据。
     */
    Result saveShop(Shop shop);

    /**
     * 根据id更改商铺信息
     * @param shop 商铺信息
     */
    Result update(Shop shop);

    /**
     * 根据商铺类型分页查询，支持按距离/人气/评分排序。
     * @param typeId 商铺类型 ID
     * @param current 页码
     * @param sortBy 排序字段：空=按距离，comments=按人气，score=按评分
     * @param x 经度
     * @param y 纬度
     */
    Result queryShopByType(Integer typeId, Integer current, String sortBy, Double x, Double y);
}
