package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    // 查询所有商铺类型
    @Override
    public List<ShopType> queryTypeList() {
        //从redis查询
        List<String> shopType = stringRedisTemplate.opsForList().range(CACHE_SHOP_TYPE_KEY, 0, -1);
        //存在则返回结果
        if(!shopType.isEmpty()){
            return shopType.stream().map(typeString-> JSONUtil.toBean(typeString, ShopType.class)).toList();
        }
        //不存在，去数据库查询
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();
        //数据库中不存在，返回空
        if (shopTypeList.isEmpty()){
            return null;
        }
        //存在，写入redis
        stringRedisTemplate.opsForList().rightPushAll(CACHE_SHOP_TYPE_KEY,
                shopTypeList.stream().map(JSONUtil::toJsonStr).toList());
        //返回结果
        return shopTypeList;
    }
}
