package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.entity.ErrorLog;
import com.hmdp.mapper.ErrorLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;

/**
 * 通用业务异常日志服务。
 * <p>
 * 任何模块出错后调用 {@link #log(String, String, Throwable, String)} 记录异常。
 * 内部自动处理降级：DB 写入失败时自动写入本地文件，调用方无需关心。
 * <p>
 * <b>本方法不会抛出异常</b>，保证不会影响主业务流程。
 * <p>
 * 使用示例：
 * <pre>
 * try {
 *     // 业务逻辑
 * } catch (Exception e) {
 *     errorLogService.log("商铺缓存", "ShopServiceImpl.rebuildCache", e,
 *             "{\"shopId\":" + shopId + "}");
 * }
 * </pre>
 */
@Slf4j
@Service
public class ErrorLogService extends ServiceImpl<ErrorLogMapper, ErrorLog> {

    private static final String FALLBACK_FILE = "error_log_fallback.log";

    /**
     * 记录业务异常。先尝试写入 DB，失败则自动降级写入本地文件。
     *
     * @param bizModule    业务模块名，如"秒杀订单"、"商铺缓存"
     * @param errorMethod  出错的类名.方法名
     * @param e            异常对象
     * @param context      上下文信息（JSON 格式），可为 null
     */
    public void log(String bizModule, String errorMethod, Throwable e, String context) {
        // 第一选择：写入 DB
        try {
            ErrorLog record = new ErrorLog()
                    .setCreateTime(LocalDateTime.now())
                    .setBizModule(bizModule)
                    .setErrorMethod(errorMethod)
                    .setErrorMessage(truncate(e.getMessage(), 500))
                    .setContext(truncate(context, 2000))
                    .setStackTrace(truncate(getStackTrace(e), 2000));
            save(record);
            log.info("错误日志已记录: [{}] {} - {}", bizModule, errorMethod, truncate(e.getMessage(), 80));
        } catch (Exception dbEx) {
            log.warn("error_log DB 写入失败，降级到本地文件: {}", dbEx.getMessage());
            // 自动降级：写入本地文件
            try {
                String line = String.format(
                        "%s|bizModule=%s|method=%s|error=%s|context=%s%n",
                        LocalDateTime.now(), bizModule, errorMethod,
                        truncate(e.getMessage(), 200), context);
                Files.write(Paths.get(FALLBACK_FILE),
                        line.getBytes(StandardCharsets.UTF_8),
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                log.warn("错误日志已写入本地文件: {}", FALLBACK_FILE);
            } catch (Exception fileEx) {
                log.error("错误日志 DB 和文件均写入失败, bizModule={}, errorMethod={}",
                        bizModule, errorMethod, fileEx);
            }
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }

    private String getStackTrace(Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        return sw.toString();
    }
}
