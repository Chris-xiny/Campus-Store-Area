package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;
import static java.lang.Thread.sleep;

@Slf4j
@Component
public class CacheClient {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private AsyncTaskUtils asyncTaskUtils;

    //用互斥锁解决缓存穿透
    public <R,Id> R queryWithMutex(String keyPrefix, String lockPrefix,Id id,Class<R> type,Function<Id,R> dbFallBack,Long ExpireSeconds,Long lockTTLSeconds ){
        while (true) {
            //1.从redis中查询
            String key = keyPrefix + id;
            String json = stringRedisTemplate.opsForValue().get(key);
            R r;
            //2.存在，返回
            if (StrUtil.isNotBlank(json)) {//isNotBlank()判断的是不为空且不为空串""
                r = JSONUtil.toBean(json, type);
                return r;
            }
            //3.防止缓存穿透可能带来的问题
            if (json != null) {//如果不为空，只能是空串，说明数据库没有该商铺，直接返回即可，不再查询数据库
                return null;
            }
            //4.实现缓存重建
            //4.1获取互斥锁
            String lockKey = lockPrefix + id;
            boolean isLock = false;
            try {
                isLock = getLock(lockKey,lockTTLSeconds);
                //4.2判断获取是否成功
                if (!isLock) {
                    //4.3失败，等待并重新尝试获取
                    sleep(50);
                    continue;
                }
                //4.4成功获取锁后DoubleCheck缓存中是否存在数据(重复123步骤)，避免额外重建缓存
                json = stringRedisTemplate.opsForValue().get(key);
                if (StrUtil.isNotBlank(json)) {
                    r = JSONUtil.toBean(json,type);
                    return r;
                }
                if (json != null) {
                    return null;
                }
                //模拟数据库业务时间比较长
                sleep(200);
                //4.5查询数据库
                r = dbFallBack.apply(id);

                //5.数据库中不存在，写入空值防止缓存穿透并返回错误
                if (r == null) {
                    stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                    return null;
                }
                //数据库中存在，返回结果并写入redis
                stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(r), ExpireSeconds, TimeUnit.MINUTES);
                //返回结果
                return r;
            } catch (InterruptedException e) {
                throw new RuntimeException();
            } finally {
                if (isLock) {
                    unLock(lockKey);
                }
            }
        }
    }

    //用逻辑过期解决缓存穿透
    public  <R,Id> R queryWithLogicExpire(String keyPrefix, String lockPrefix, Id id, Class<R> type, Function<Id,R> dbFallBack,Long logicExpireSeconds,Long lockTTLSeconds){
        //1.从redis中查询
        String key = keyPrefix + id;
        String redisDataJson = stringRedisTemplate.opsForValue().get(key);
        String lockKey = lockPrefix + id;
        //2.存在，返回
        if (StrUtil.isNotBlank(redisDataJson)) {//isNotBlank()判断的是不为空且不为空串""
            RedisData redisData = JSONUtil.toBean(redisDataJson, RedisData.class);
            R r = JSONUtil.toBean(JSONUtil.toJsonStr(redisData.getData()), type);
            if (redisData.getExpireTime().isAfter(LocalDateTime.now())) {
                //如果逻辑过期时间未过期，直接返回信息
                return r;
            } else {
                //如果过期了，通过判断获取锁是否成功来决定是否需要异步重建缓存，无论怎样最后都返回旧数据
                if (!getLock(lockKey,lockTTLSeconds)) {
                    //失败,说明已经有线程处理，直接返回旧数据
                    return r;
                }
                //成功就异步刷新,并将锁交给新线程
                asyncTaskUtils.rebuildCacheAsync(id,type,dbFallBack,lockKey);
                return r;
            }
        }
        //3.1不存在,循环等待获取结果或自己查询数据库
        while (true) {
            //没有就尝试加锁自己处理
            boolean flag = getLock(lockKey,lockTTLSeconds);
            try {
                if (!flag) {
                    //4.3获取锁失败,说明已经有线程处理，睡一会再二次检查缓存
                    sleep(50);
                    //如果已经存在，说明已经有线程刚刷新了缓存,二次缓存检查到数据就直接返回数据
                    R r = isRedisDataExistInRedis(key,type);
                    if (r == null) {
                        continue;
                    }
                    //直接返回
                    return r;
                } else {
                    //成功获取锁,二次检查如果已经存在，说明已经有线程刚刷新了缓存,直接返回数据
                    R r = isRedisDataExistInRedis(key,type);
                    //没有就查询数据库并重建缓存,然后返回新数据
                    if (r == null) {
                        return rebuildCacheWithExpireSeconds(keyPrefix,id,type,dbFallBack,logicExpireSeconds);
                    }
                    //直接返回
                    return r;
                }
            } catch (InterruptedException e) {
                throw new RuntimeException();
            } finally {
                //如果成功获得锁则释放
                if (flag) {
                    unLock(lockKey);
                }
            }
        }
    }

    private <R> R isRedisDataExistInRedis(String key,Class<R> type) {
        String redisDataJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(redisDataJson)) {
            RedisData redisData = JSONUtil.toBean(redisDataJson, RedisData.class);
            return JSONUtil.toBean(JSONUtil.toJsonStr(redisData.getData()), type);
        }
        return null;
    }

    private <R,Id> R rebuildCacheWithExpireSeconds(String keyPrefix, Id id,Class<R> type, Function<Id,R> dbFallBack,Long logicExpireSeconds) {
        R r=dbFallBack.apply(id);
        String json = JSONUtil.toJsonStr(new RedisData(LocalDateTime.now().plusSeconds(logicExpireSeconds), r));
        stringRedisTemplate.opsForValue().set(keyPrefix + id, json);
        if(r==null){//如果是空数据，设置ttl防止堆积无效数据占用空间
            stringRedisTemplate.expire(keyPrefix + id,CACHE_NULL_TTL,TimeUnit.MINUTES);
        }
        return r;
    }

    private boolean getLock(String key,Long lockTTLSeconds) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", lockTTLSeconds,TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }
}
