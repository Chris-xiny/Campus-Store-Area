-- MQ 消费失败补偿表
CREATE TABLE IF NOT EXISTS `mq_failed_order` (
  `id`              bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键',
  `order_id`        bigint(20) NOT NULL COMMENT '订单ID',
  `user_id`         bigint(20) NOT NULL COMMENT '用户ID',
  `voucher_id`      bigint(20) NOT NULL COMMENT '优惠券ID',
  `fail_reason`     varchar(500) DEFAULT NULL COMMENT '失败原因',
  `retry_count`     int(11) NOT NULL DEFAULT 0 COMMENT '已重试次数',
  `max_retry`       int(11) NOT NULL DEFAULT 5 COMMENT '最大重试次数',
  `next_retry_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '下次重试时间',
  `status`          varchar(10) NOT NULL DEFAULT 'pending' COMMENT '状态：pending-待重试 / dead-死信',
  `create_time`     timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_order_id` (`order_id`) COMMENT '同一订单不重复补偿'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'MQ消费失败补偿表';
