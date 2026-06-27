package com.hmdp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * MQ 消费失败补偿记录。
 * 定时任务扫描 pending 状态且 next_retry_time 已到的记录进行重试，
 * 成功则删除记录，超过 max_retry 次则标记为 dead 等待人工介入。
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("mq_failed_order")
public class MqFailedOrder implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 订单 ID（与 VoucherOrder.id 一致，用于去重和幂等） */
    private Long orderId;

    /** 用户 ID */
    private Long userId;

    /** 优惠券 ID */
    private Long voucherId;

    /** 失败原因（截断至 500 字符） */
    private String failReason;

    /** 已重试次数 */
    private Integer retryCount;

    /** 最大重试次数（默认 5） */
    private Integer maxRetry;

    /** 下次重试时间（指数退避） */
    private LocalDateTime nextRetryTime;

    /** 状态：pending=待重试，dead=超过最大重试次数 */
    private String status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
