package com.hmdp.mq;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.ErrorLogService;
import com.hmdp.service.IMqFailedOrderService;
import com.hmdp.service.IVoucherOrderService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * 秒杀订单消息消费者：从 MQ 拿到订单后执行 DB 落库。
 *
 * <p>可靠性保障（面试重点）：
 * <ul>
 *   <li>手动 ACK：处理成功 basicAck；处理失败通过两层兜底保证消息出队</li>
 *   <li>幂等性：落库前先按订单 ID 查询，已存在则直接 ACK，避免重复落库</li>
 *   <li>降级链：补偿表（自动重试）→ ErrorLogService（DB + 本地文件自动降级），保证消息不循环</li>
 * </ul>
 *
 * <p>监听配置：queues 指定监听哪个队列；concurrency 指定消费者线程数范围（min-max）。
 * 这里 concurrency=5-10 表示最少 5 个消费线程、最多 10 个，根据消息堆积情况自动扩容。
 * 这比 Redis Stream 的 while(true) 单线程模型更弹性，也是真正利用了线程池。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VoucherOrderConsumer {

    private final IVoucherOrderService voucherOrderService;
    private final IMqFailedOrderService mqFailedOrderService;
    private final ErrorLogService errorLogService;

    /**
     * 消费秒杀订单消息。
     *
     * @param order       消息体（由 Jackson2JsonMessageConverter 自动反序列化）
     * @param channel     AMQP Channel，用于手动 ACK/NACK
     * @param deliveryTag 消息的投递标签，ACK 时需要
     * @param redelivered 是否是重发消息（Broker 没收到上一次 ACK 时会重发）
     */
    @RabbitListener(queues = RabbitMqConstants.SECKILL_ORDER_QUEUE, concurrency = "5-10")
    public void onMessage(VoucherOrder order,
                          Channel channel,
                          @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag,
                          @Header(AmqpHeaders.REDELIVERED) boolean redelivered) {
        try {
            if (redelivered) {
                log.warn("收到重发消息, orderId={}, deliveryTag={}", order.getId(), deliveryTag);
            }
            // 幂等 + 落库（内部已包含"重复订单直接返回"的逻辑）
            voucherOrderService.handleVoucherOrder(order);

            // 处理成功：basicAck(deliveryTag, multiple=false)
            channel.basicAck(deliveryTag, false);
            log.debug("订单处理成功并已 ACK, orderId={}", order.getId());

        } catch (Exception e) {
            log.error("订单处理异常, orderId={}, cause={}", order.getId(), e.getMessage(), e);
            handleConsumerFailure(order, channel, deliveryTag, e);
        }
    }

    /**
     * 消费失败处理（两层降级 + 保证 ACK 出队）：
     * <pre>
     * 第一层：mq_failed_order 补偿表 → 定时任务自动重试，成功后删除
     * 第二层：ErrorLogService → 写 error_log 表留痕（DB 失败自动降级到本地文件）
     * 无论哪层成功，最终都 ACK 出队，防止消息在队列中无限循环。
     * </pre>
     */
    private void handleConsumerFailure(VoucherOrder order, Channel channel,
                                       long deliveryTag, Exception bizEx) {
        String context = String.format(
                "{\"orderId\":%d,\"userId\":%d,\"voucherId\":%d}",
                order.getId(), order.getUserId(), order.getVoucherId());

        // —— 第一层：补偿表（可自动重试） ——
        try {
            mqFailedOrderService.saveFailedOrder(order, bizEx.getMessage());
            log.warn("补偿表写入成功, orderId={}", order.getId());
        } catch (Exception compEx) {
            // —— 第二层：ErrorLogService（DB → 本地文件自动降级） ——
            log.error("补偿表写入失败，降级到 error_log, orderId={}", order.getId(), compEx);
            errorLogService.log("秒杀订单",
                    "VoucherOrderConsumer.onMessage", bizEx, context);
        }

        // 无论哪层，最终 ACK 出队，防止消息死循环
        try {
            channel.basicAck(deliveryTag, false);
        } catch (Exception ackEx) {
            log.error("ACK 发送失败, orderId={}", order.getId(), ackEx);
        }
    }
}
