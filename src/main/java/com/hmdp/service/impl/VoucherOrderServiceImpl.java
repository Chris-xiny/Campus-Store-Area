package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.mq.VoucherOrderProducer;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

/**
 * 秒杀订单服务实现。
 *
 * <p>流程：
 * <ol>
 *   <li>用户请求 → Lua 脚本在 Redis 中原子完成"判库存 + 判一人一单 + 扣库存 + 记购买"</li>
 *   <li>预扣成功 → 生成订单号，把订单信息投递到 RabbitMQ</li>
 *   <li>立刻给用户返回订单号（不等待 DB 落库，用户体感极快）</li>
 *   <li>VoucherOrderConsumer 异步消费消息，调用 handleVoucherOrder 落库</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder>
        implements IVoucherOrderService {

    private final ISeckillVoucherService seckillVoucherService;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisIdWorker redisIdWorker;
    private final VoucherOrderProducer voucherOrderProducer;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    /**
     * 秒杀入口：Redis Lua 预扣 + RabbitMQ 异步落库。
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();

        // 1. 执行 Lua 脚本：原子判库存 + 判一人一单 + 预扣
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                userId.toString()
        );

        int r = result.intValue();
        if (r != 0) {
            return Result.fail(r == 1 ? "库存不足!" : "不能重复购买!");
        }

        // 2. 预扣成功，生成订单号并构造订单对象（此时未落库）
        Long orderId = redisIdWorker.nextId("order");
        VoucherOrder order = new VoucherOrder();
        order.setId(orderId);
        order.setVoucherId(voucherId);
        order.setUserId(userId);

        // 3. 投递到 RabbitMQ（消息持久化 + publisher confirm + returns 兜底）
        try {
            voucherOrderProducer.send(order);
        } catch (Exception e) {
            // MQ 发送失败：回滚 Redis 中的库存和用户购买记录，保证数据一致
            log.error("MQ 发送失败，回滚 Redis 预扣, orderId={}", orderId, e);
            rollbackRedisPreDeduct(voucherId, userId);
            return Result.fail("系统繁忙，请稍后再试");
        }

        // 4. 立刻返回订单号给用户（异步落库由消费者完成）
        return Result.ok(orderId);
    }

    /**
     * 回滚 Lua 脚本中已做的 Redis 预扣（库存 + 用户购买记录）。
     * 仅在 MQ 发送失败时调用，避免"Redis 扣了但 DB 没扣"的不一致。
     */
    private void rollbackRedisPreDeduct(Long voucherId, Long userId) {
        try {
            String stockKey = "seckill:stock:" + voucherId;
            String orderKey = "seckill:order:" + voucherId;
            stringRedisTemplate.opsForValue().increment(stockKey);
            stringRedisTemplate.opsForSet().remove(orderKey, userId.toString());
        } catch (Exception e) {
            log.error("Redis 预扣回滚失败, voucherId={}, userId={}", voucherId, userId, e);
        }
    }

    /**
     * 消费者调用：幂等校验 + DB 扣减库存 + 订单落库。
     * 使用 @Transactional 保证原子性。
     */
    @Transactional
    @Override
    public void handleVoucherOrder(VoucherOrder order) {
        // 1. 幂等性校验：已落库的订单直接返回（应对 MQ 消息重发）
        Long existCount = query().eq("id", order.getId()).count();
        if (existCount > 0) {
            log.info("订单已存在，跳过重复处理, orderId={}", order.getId());
            return;
        }

        // 2. DB 层再次校验一人一单（防御性编程，Lua 已经判过一次）
        Long userId = order.getUserId();
        Long count = query().eq("user_id", userId).eq("voucher_id", order.getVoucherId()).count();
        if (count > 0) {
            log.warn("一人一单校验失败（DB层）, userId={}, voucherId={}", userId, order.getVoucherId());
            return;
        }

        // 3. DB 扣减库存（乐观锁：stock > 0）
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", order.getVoucherId())
                .gt("stock", 0)
                .update();
        if (!success) {
            log.error("DB 库存不足, voucherId={}", order.getVoucherId());
            throw new RuntimeException("库存不足，订单处理失败");
        }

        // 4. 订单落库
        save(order);
        log.info("订单落库成功, orderId={}, userId={}, voucherId={}",
                order.getId(), userId, order.getVoucherId());
    }
}
