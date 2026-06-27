-- 秒杀订单表添加一人一单唯一约束
-- 防止并发消费时同一用户重复购买同一优惠券
ALTER TABLE `tb_voucher_order`
    ADD UNIQUE INDEX `uk_user_voucher` (`user_id`, `voucher_id`) COMMENT '一人一单唯一约束';
