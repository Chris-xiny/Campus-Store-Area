package com.hmdp;

import com.hmdp.utils.AsyncTaskUtils;
import com.hmdp.utils.RedisIdWorker;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
@SpringBootTest
class HmDianPingApplicationTests {


    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private AsyncTaskUtils asyncTaskUtils;
    @Resource
    private RedissonClient redissonClient;
    @Resource
    private RedissonClient redissonClient2;
    @Resource
    private RedissonClient redissonClient3;

    private RLock lock;

    @BeforeEach
    void set(){
        RLock lock1 = redissonClient.getLock("order");
        RLock lock2 = redissonClient2.getLock("order");
        RLock lock3 = redissonClient3.getLock("order");
        lock = redissonClient.getMultiLock(lock1, lock2, lock3);
    }
    @Test
    void method01() throws InterruptedException {
        boolean isLock=lock.tryLock(1,-1, TimeUnit.SECONDS);
        if(!isLock){
            log.error("获取锁失败...1");
            return;
        }
        try{
            log.info("获取锁成功...1");
            method02();
            log.info("执行业务...1");
        }finally {
            log.info("释放锁...1");
            lock.unlock();
        }
    }
    @Test
    void method02() throws InterruptedException {
        boolean isLock=lock.tryLock(1,-1, TimeUnit.SECONDS);
        if(!isLock){
            log.error("获取锁失败...2");
            return;
        }
        try{
            log.info("获取锁成功...2");
            log.info("执行业务...2");
        }finally {
            log.info("释放锁...2");
            lock.unlock();
        }
    }

    @Test
    void testIdWorker() throws InterruptedException {
        String keyPrefix="order";
        Long start =System.currentTimeMillis();
        CountDownLatch latch = new CountDownLatch(300);
        for (int i = 0; i < 300; i++) {
            asyncTaskUtils.Task(key->{
                for (int j = 0; j < 100; j++) {
                    System.out.println(redisIdWorker.nextId(key));
                }
                latch.countDown();
                return 0;
            },keyPrefix);
        }
        latch.await();
        Long end =System.currentTimeMillis();
        System.out.println(end-start);
    }

}
