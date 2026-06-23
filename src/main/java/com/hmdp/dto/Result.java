package com.hmdp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.MDC;

import java.util.List;

/**
 * 统一响应体：封装所有接口的返回结构，包含 traceId 和 timestamp 便于链路追踪与日志排查
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result {
    private Boolean success;
    private String errorMsg;
    private Object data;
    private Long total;
    private String traceId;
    private Long timestamp;

    public static Result ok() {
        return build(true, null, null, null);
    }

    public static Result ok(Object data) {
        return build(true, null, data, null);
    }

    public static Result ok(List<?> data, Long total) {
        return build(true, null, data, total);
    }

    public static Result fail(String errorMsg) {
        return build(false, errorMsg, null, null);
    }

    private static Result build(Boolean success, String errorMsg, Object data, Long total) {
        Result r = new Result();
        r.setSuccess(success);
        r.setErrorMsg(errorMsg);
        r.setData(data);
        r.setTotal(total);
        r.setTraceId(MDC.get("traceId"));
        r.setTimestamp(System.currentTimeMillis());
        return r;
    }
}
