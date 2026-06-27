package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.hmdp.entity.MqFailedOrder;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.MqFailedOrderMapper;
import com.hmdp.service.IMqFailedOrderService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * MQ 消费失败补偿服务。
 * <p>
 * 消费失败时将订单写入 mq_failed_order 表，
 * 定时任务每 30 秒扫描 pending 状态且 next_retry_time 已到的记录进行重试。
 * 重试成功则删除记录，超过 max_retry 次则标记 dead 等待人工介入。
 */
@Slf4j
@Service
public class MqFailedOrderServiceImpl
        extends ServiceImpl<MqFailedOrderMapper, MqFailedOrder>
        implements IMqFailedOrderService {

    @Resource
    private IVoucherOrderService voucherOrderService;

    /** 默认最大重试次数 */
    private static final int DEFAULT_MAX_RETRY = 5;

    /** 基础退避时间（秒），实际退避 = BASE * 2^retryCount */
    private static final long BASE_BACKOFF_SECONDS = 30L;

    /**
     * 消费失败时写入补偿表。
     * 同一 orderId 不重复插入（利用唯一索引 uk_order_id 去重）。
     */
    @Override
    public void saveFailedOrder(VoucherOrder order, String reason) {
        // 去重：同 orderId 已存在则跳过
        long existCount = query().eq("order_id", order.getId()).count();
        if (existCount > 0) {
            log.debug("补偿记录已存在，跳过, orderId={}", order.getId());
            return;
        }

        MqFailedOrder record = new MqFailedOrder();
        record.setOrderId(order.getId());
        record.setUserId(order.getUserId());
        record.setVoucherId(order.getVoucherId());
        record.setFailReason(truncate(reason, 500));
        record.setRetryCount(0);
        record.setMaxRetry(DEFAULT_MAX_RETRY);
        // 首次重试在 30 秒后
        record.setNextRetryTime(LocalDateTime.now().plusSeconds(BASE_BACKOFF_SECONDS));
        record.setStatus("pending");
        save(record);
        log.info("补偿记录已写入, orderId={}, reason={}", order.getId(), truncate(reason, 80));
    }

    /**
     * 定时任务：每 30 秒扫描待重试的补偿记录。
     * <p>
     * 直接调用 handleVoucherOrder 重试（不经过 MQ），
     * 成功则删除补偿记录，失败则递增重试次数并指数退避。
     * 超过 max_retry 次则标记 status=dead。
     */
    @Scheduled(fixedDelay = 30000)
    @Override
    public void compensate() {
        List<MqFailedOrder> tasks = query()
                .eq("status", "pending")
                .le("next_retry_time", LocalDateTime.now())
                .list();

        if (tasks.isEmpty()) return;
        log.info("补偿任务开始，待处理 {} 条记录", tasks.size());

        for (MqFailedOrder task : tasks) {
            try {
                // 重建 VoucherOrder 对象，直接调用业务方法重试
                VoucherOrder order = new VoucherOrder();
                order.setId(task.getOrderId());
                order.setUserId(task.getUserId());
                order.setVoucherId(task.getVoucherId());

                voucherOrderService.handleVoucherOrder(order);

                // 重试成功 → 删除补偿记录
                removeById(task.getId());
                log.info("补偿重试成功，删除记录, orderId={}", task.getOrderId());

            } catch (Exception e) {
                task.setRetryCount(task.getRetryCount() + 1);

                if (task.getRetryCount() >= task.getMaxRetry()) {
                    // 超过最大重试次数 → 标记死信，等待人工处理
                    task.setStatus("dead");
                    log.error("补偿重试 {} 次仍失败，标记为 dead, orderId={}, reason={}",
                            task.getRetryCount(), task.getOrderId(), truncate(e.getMessage(), 200));
                } else {
                    // 指数退避：30s → 60s → 120s → 240s → 480s
                    long delaySeconds = BASE_BACKOFF_SECONDS * (1L << task.getRetryCount());
                    task.setNextRetryTime(LocalDateTime.now().plusSeconds(delaySeconds));
                    log.warn("补偿重试第 {} 次失败，{}秒后重试, orderId={}, reason={}",
                            task.getRetryCount(), delaySeconds, task.getOrderId(),
                            truncate(e.getMessage(), 200));
                }

                task.setFailReason(truncate(e.getMessage(), 500));
                updateById(task);
            }
        }
    }

    /** 截断字符串至指定长度 */
    private String truncate(String s, int maxLen) {
        if (StrUtil.isBlank(s)) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
