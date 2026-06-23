package com.hmdp.service;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherOrderService extends IService<VoucherOrder> {

    Result seckillVoucher(Long voucherId);

    /**
     * 处理 MQ 投递过来的秒杀订单：幂等校验 + 扣减 DB 库存 + 落库。
     * 由 VoucherOrderConsumer 调用，运行在事务内。
     */
    void handleVoucherOrder(VoucherOrder order);
}
