package com.hmdp.mq;

/**
 * RabbitMQ 相关常量：队列名、交换机名、路由键
 *
 * <p>路由模型：Direct Exchange + 固定 routingKey，点对点精确投递。
 */
public final class RabbitMqConstants {

    private RabbitMqConstants() {}

    /** 秒杀订单队列（持久化队列） */
    public static final String SECKILL_ORDER_QUEUE = "seckill.order.queue";

    /** 秒杀订单交换机（direct 类型） */
    public static final String SECKILL_ORDER_EXCHANGE = "seckill.order.exchange";

    /** 路由键：和交换机绑定时使用，消息发送时也使用相同的 routingKey */
    public static final String SECKILL_ORDER_ROUTING_KEY = "seckill.order";
}
