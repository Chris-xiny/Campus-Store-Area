package com.hmdp.mq;

import com.hmdp.entity.VoucherOrder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 秒杀订单消息生产者：把预扣成功的订单信息发到 RabbitMQ。
 *
 * <p>可靠性保障：
 * <ul>
 *   <li>消息持久化：由 RabbitMQConfig 声明队列和消息默认持久化</li>
 *   <li>Publisher Confirm：通过 RabbitTemplate.setConfirmCallback 实现</li>
 *   <li>消息唯一标识：通过 CorrelationData 携带 UUID，便于日志追踪和补偿</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VoucherOrderProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * 发送秒杀订单消息到 MQ。
     *
     * @param order 预扣库存成功后构造的订单对象（只含 id/voucherId/userId，不含 DB 时间戳）
     */
    public void send(VoucherOrder order) {
        String messageId = UUID.randomUUID().toString();

        // 使用自定义 CorrelationData 子类，便于在 confirm 回调里拿到业务上下文
        OrderCorrelationData correlationData = new OrderCorrelationData();
        correlationData.setId(messageId);
        correlationData.setOrderId(order.getId());
        correlationData.setVoucherId(order.getVoucherId());
        correlationData.setUserId(order.getUserId());

        try {
            rabbitTemplate.convertAndSend(
                    RabbitMqConstants.SECKILL_ORDER_EXCHANGE,
                    RabbitMqConstants.SECKILL_ORDER_ROUTING_KEY,
                    order,
                    correlationData
            );
            log.debug("秒杀订单消息已发送, messageId={}, orderId={}", messageId, order.getId());
        } catch (Exception e) {
            // 发送失败（网络异常、Broker 不可达等），需要补偿：回滚 Redis 库存 + 记录失败日志
            log.error("秒杀订单消息发送异常, orderId={}, cause={}", order.getId(), e.getMessage(), e);
            throw new RuntimeException("秒杀消息发送失败", e);
        }
    }

    /**
     * 自定义 CorrelationData 子类，携带业务上下文（orderId/voucherId/userId）。
     * 当 confirm 回调 ack=false 时，可基于这些字段执行补偿（回滚 Redis 库存、落失败表）。
     */
    public static class OrderCorrelationData extends CorrelationData {
        private Long orderId;
        private Long voucherId;
        private Long userId;

        public Long getOrderId() { return orderId; }
        public void setOrderId(Long orderId) { this.orderId = orderId; }
        public Long getVoucherId() { return voucherId; }
        public void setVoucherId(Long voucherId) { this.voucherId = voucherId; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
    }
}
