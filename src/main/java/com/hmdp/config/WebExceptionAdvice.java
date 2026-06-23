package com.hmdp.config;

import com.hmdp.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.servlet.http.HttpServletRequest;
import java.util.stream.Collectors;

/**
 * 全局异常处理：区分业务异常、参数校验异常、系统异常三类
 */
@Slf4j
@RestControllerAdvice
public class WebExceptionAdvice {

    /**
     * 业务异常：可预期的业务失败（如库存不足、未登录等），不需要打印堆栈
     */
    @ExceptionHandler(BusinessException.class)
    public Result handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return Result.fail(e.getMessage());
    }

    /**
     * @RequestBody 参数校验失败（配合 @Valid 使用）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result handleValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ":" + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);
        return Result.fail(message);
    }

    /**
     * @ModelAttribute / @RequestParam 参数绑定校验失败
     */
    @ExceptionHandler(BindException.class)
    public Result handleBindException(BindException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ":" + f.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数绑定失败: {}", message);
        return Result.fail(message);
    }

    /**
     * 系统异常：兜底处理所有未捕获的 RuntimeException
     */
    @ExceptionHandler(RuntimeException.class)
    public Result handleRuntimeException(RuntimeException e, HttpServletRequest request) {
        String traceId = MDC.get("traceId");
        log.error("系统异常 [uri={}, traceId={}]: {}", request.getRequestURI(), traceId, e.toString(), e);
        return Result.fail("服务器异常，请稍后重试");
    }
}
