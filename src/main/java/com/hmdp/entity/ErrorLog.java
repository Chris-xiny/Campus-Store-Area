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
 * 通用业务异常日志记录。
 * <p>
 * 全局共用，任何模块的业务异常都可以写入此表。
 * 定位为只读审计——只记录，不自动重试，需人工排查。
 * <p>
 * 与 mq_failed_order 的区别：
 * - mq_failed_order：MQ 消费失败专用，定时任务自动重试，成功后删除
 * - error_log：通用异常记录，只读留痕，覆盖所有不可自动重试的业务异常
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("error_log")
public class ErrorLog implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 异常发生时间 */
    private LocalDateTime createTime;

    /** 业务模块，如"秒杀订单"、"商铺缓存"、"支付通知" */
    private String bizModule;

    /** 出错的类名.方法名，如 VoucherOrderConsumer.onMessage */
    private String errorMethod;

    /** 错误摘要 */
    private String errorMessage;

    /** 异常上下文（JSON 格式，记录 orderId、userId 等关键参数） */
    private String context;

    /** 异常堆栈（截取前 2000 字符） */
    private String stackTrace;
}
