package com.hmdp.config;

import com.hmdp.dto.Result;
import lombok.Getter;

/**
 * 自定义业务异常：用于可预期的业务失败场景（如库存不足、未登录、无权操作等）
 */
@Getter
public class BusinessException extends RuntimeException {

    private final Integer code;

    public BusinessException(String message) {
        super(message);
        this.code = 400;
    }

    public BusinessException(Integer code, String message) {
        super(message);
        this.code = code;
    }
}
