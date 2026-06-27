package com.hmdp.mq;

import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.ErrorLogService;
import com.hmdp.service.IMqFailedOrderService;
import com.hmdp.service.IVoucherOrderService;
import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

/**
 * MQ 消费者单元测试 — 覆盖秒杀订单消费的完整路径：
 * <ul>
 *   <li>成功路径：处理成功 → ACK</li>
 *   <li>补偿路径：处理失败 → 写补偿表 → ACK</li>
 *   <li>降级路径：补偿表也失败 → ErrorLogService（内部自动降级到文件）→ ACK</li>
 *   <li>边界场景：重发消息、ACK 本身失败</li>
 * </ul>
 *
 * <p>面试价值：展示对 MQ 消费端可靠性保障的理解，包括幂等、补偿、降级三层防御。
 */
@ExtendWith(MockitoExtension.class)
class VoucherOrderConsumerTest {

    @Mock private IVoucherOrderService voucherOrderService;
    @Mock private IMqFailedOrderService mqFailedOrderService;
    @Mock private ErrorLogService errorLogService;
    @Mock private Channel channel;

    @InjectMocks
    private VoucherOrderConsumer consumer;

    private VoucherOrder order;

    @BeforeEach
    void setUp() {
        order = new VoucherOrder();
        order.setId(1001L);
        order.setUserId(2001L);
        order.setVoucherId(3001L);
    }

    @Test
    @DisplayName("消费成功 → handleVoucherOrder 正常执行 → basicAck")
    void onMessage_success_ackCalled() throws Exception {
        consumer.onMessage(order, channel, 1L, false);

        verify(voucherOrderService).handleVoucherOrder(order);
        verify(channel).basicAck(1L, false);
        verifyNoInteractions(mqFailedOrderService, errorLogService);
    }

    @Test
    @DisplayName("消费失败 → 补偿表写入成功 → basicAck（定时任务接管重试）")
    void onMessage_bizFails_compensationSaved_ackCalled() throws Exception {
        doThrow(new RuntimeException("DB连接超时"))
                .when(voucherOrderService).handleVoucherOrder(order);

        consumer.onMessage(order, channel, 2L, false);

        verify(mqFailedOrderService).saveFailedOrder(eq(order), eq("DB连接超时"));
        verify(channel).basicAck(2L, false);
        verifyNoInteractions(errorLogService);
    }

    @Test
    @DisplayName("补偿表也写失败 → ErrorLogService 降级留痕 → 仍然 ACK 出队")
    void onMessage_compensationFails_errorLogFallback_ackCalled() throws Exception {
        doThrow(new RuntimeException("业务异常"))
                .when(voucherOrderService).handleVoucherOrder(order);
        doThrow(new RuntimeException("补偿表也挂了"))
                .when(mqFailedOrderService).saveFailedOrder(any(), any());

        consumer.onMessage(order, channel, 3L, false);

        verify(errorLogService).log(
                eq("秒杀订单"),
                eq("VoucherOrderConsumer.onMessage"),
                any(RuntimeException.class),
                anyString());
        verify(channel).basicAck(3L, false);
    }

    @Test
    @DisplayName("重发消息 → 正常处理并 ACK")
    void onMessage_redelivered_stillProcesses() throws Exception {
        consumer.onMessage(order, channel, 4L, true);

        verify(voucherOrderService).handleVoucherOrder(order);
        verify(channel).basicAck(4L, false);
    }

    @Test
    @DisplayName("ACK 本身抛异常 → 不会向上传播，消息保留在队列等 RabbitMQ 重发")
    void onMessage_ackFails_noExceptionPropagated() throws Exception {
        doThrow(new RuntimeException("DB异常"))
                .when(voucherOrderService).handleVoucherOrder(order);
        doThrow(new RuntimeException("补偿异常"))
                .when(mqFailedOrderService).saveFailedOrder(any(), any());
        doThrow(new RuntimeException("ACK IO异常"))
                .when(channel).basicAck(anyLong(), anyBoolean());

        consumer.onMessage(order, channel, 5L, false);

        // 验证所有降级路径都被尝试，且测试不抛异常
        verify(errorLogService).log(anyString(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("消费成功时补偿服务和错误日志不被调用（零副作用）")
    void onMessage_success_noCompensationNoErrorLog() throws Exception {
        consumer.onMessage(order, channel, 6L, false);

        verify(voucherOrderService).handleVoucherOrder(order);
        verify(mqFailedOrderService, never()).saveFailedOrder(any(), any());
        verify(errorLogService, never()).log(any(), any(), any(), any());
    }
}
