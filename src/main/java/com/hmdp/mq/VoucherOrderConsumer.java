package com.hmdp.mq;

import com.hmdp.entity.VoucherOrder;
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
 *   <li>手动 ACK：处理成功 basicAck；处理失败 basicNack + requeue 策略</li>
 *   <li>幂等性：落库前先按订单 ID 查询，已存在则直接 ACK，避免重复落库</li>
 *   <li>异常兜底：捕获所有异常，避免消息进入死循环</li>
 * </ul>
 *
 * <p>监听配置：queues 指定监听哪个队列；concurrency 指定消费者线程数范围（min-max）。
 * 这里 concurrency=1-3 表示最少 1 个消费线程、最多 3 个，根据消息堆积情况自动扩容。
 * 这比 Redis Stream 的 while(true) 单线程模型更弹性，也是真正利用了线程池。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VoucherOrderConsumer {

    private final IVoucherOrderService voucherOrderService;

    /**
     * 消费秒杀订单消息。
     *
     * @param order       消息体（由 Jackson2JsonMessageConverter 自动反序列化）
     * @param channel     AMQP Channel，用于手动 ACK/NACK
     * @param deliveryTag 消息的投递标签，ACK 时需要
     * @param redelivered 是否是重发消息（Broker 没收到上一次 ACK 时会重发）
     */
    @RabbitListener(queues = RabbitMqConstants.SECKILL_ORDER_QUEUE, concurrency = "1-3")
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
            try {
                // 处理失败：basicNack(deliveryTag, multiple=false, requeue=false)
                // requeue=false：不重新入队，避免同一条"毒消息"反复消费导致雪崩
                // 生产环境建议把失败消息发到死信队列（DLX），由人工或补偿任务处理
                channel.basicNack(deliveryTag, false, false);
            } catch (Exception ackEx) {
                log.error("发送 nack 失败, orderId={}", order.getId(), ackEx);
            }
        }
    }
}
