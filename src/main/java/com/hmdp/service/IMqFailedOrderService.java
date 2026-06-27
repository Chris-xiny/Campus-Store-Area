package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.hmdp.entity.MqFailedOrder;
import com.hmdp.entity.VoucherOrder;

public interface IMqFailedOrderService extends IService<MqFailedOrder> {

    /**
     * 消费失败时写入补偿表（同一 orderId 不重复插入）。
     *
     * @param order  失败的秒杀订单
     * @param reason 失败原因
     */
    void saveFailedOrder(VoucherOrder order, String reason);

    /**
     * 定时任务调用：扫描待重试的补偿记录，逐条重新处理。
     * 成功则删除记录，失败则递增重试次数并指数退避。
     */
    void compensate();
}
