package com.hmdp.config;

import com.hmdp.mq.RabbitMqConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 配置类：声明队列、交换机、绑定关系，并配置消息序列化器和可靠性回调。
 *
 * <p>注意：Spring AMQP 启动时会对比声明的结构和 Broker 中已存在的结构。
 * 若不一致会报错，因此首次部署或修改结构时需要清空旧队列（或删除后重建）。
 */
@Configuration
public class RabbitMQConfig {

    /**
     * 消息转换器：使用 Jackson JSON 替代 JDK 序列化。
     * 好处：消息内容是 JSON 字符串，便于调试和跨语言消费。
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * RabbitTemplate：生产者发消息的入口。
     * 配置 confirm / returns 回调，保证消息到达队列（可靠性第一环）。
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);

        // 开启 publisher confirm + returns（yaml 里 publisher-confirm-type=correlated 也可）
        template.setMandatory(true);

        // 消息到达 Exchange 后回调（需 yaml 开启 publisher-confirm-type=correlated）
        template.setConfirmCallback((correlation, ack, cause) -> {
            if (!ack) {
                // 消息没到 Exchange，需要补偿（记日志 + 告警 + 落失败表由运营补单）
                System.err.println("[RabbitMQ Confirm] 消息未到达 Exchange, cause=" + cause);
            }
        });

        // 消息从 Exchange 路由不到 Queue 时回调（路由失败）
        template.setReturnsCallback(returned -> {
            System.err.println("[RabbitMQ Return] 消息路由失败, replyCode="
                    + returned.getReplyCode()
                    + ", replyText=" + returned.getReplyText()
                    + ", exchange=" + returned.getExchange()
                    + ", routingKey=" + returned.getRoutingKey());
        });

        return template;
    }

    /** 秒杀订单队列：durable=true，Broker 重启后队列不丢 */
    @Bean
    public Queue seckillOrderQueue() {
        return QueueBuilder.durable(RabbitMqConstants.SECKILL_ORDER_QUEUE).build();
    }

    /** 秒杀订单交换机：direct 类型，按 routingKey 精确路由 */
    @Bean
    public DirectExchange seckillOrderExchange() {
        return new DirectExchange(RabbitMqConstants.SECKILL_ORDER_EXCHANGE, true, false);
    }

    /** 绑定：把队列绑到交换机上，并指定 routingKey */
    @Bean
    public Binding seckillOrderBinding(Queue seckillOrderQueue, DirectExchange seckillOrderExchange) {
        return BindingBuilder.bind(seckillOrderQueue)
                .to(seckillOrderExchange)
                .with(RabbitMqConstants.SECKILL_ORDER_ROUTING_KEY);
    }
}
