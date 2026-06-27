package com.hmdp.service;

import com.hmdp.entity.ErrorLog;
import com.hmdp.mapper.ErrorLogMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ErrorLogService 单元测试 — 覆盖通用业务异常日志的两级降级：
 * <ul>
 *   <li>正常路径：写入 error_log 表成功</li>
 *   <li>降级路径：DB 写入失败 → 自动写入本地文件</li>
 *   <li>安全保证：任何场景下 log() 都不会向外抛出异常</li>
 * </ul>
 *
 * <p>面试价值：展示对"最后一道防线"服务的设计——永不抛异常 + 自动降级。
 */
@ExtendWith(MockitoExtension.class)
class ErrorLogServiceTest {

    private ErrorLogService errorLogService;
    private ErrorLogMapper mockMapper;
    private static final Path FALLBACK_FILE = Paths.get("error_log_fallback.log");

    @BeforeEach
    void setUp() {
        mockMapper = mock(ErrorLogMapper.class);
        errorLogService = spy(new ErrorLogService() {
            @Override
            public ErrorLogMapper getBaseMapper() {
                return mockMapper;
            }
        });
    }

    @AfterEach
    void cleanUp() throws IOException {
        Files.deleteIfExists(FALLBACK_FILE);
    }

    @Test
    @DisplayName("正常路径 — DB 写入成功")
    void log_dbWriteSuccess() {
        when(mockMapper.insert(any(ErrorLog.class))).thenReturn(1);

        errorLogService.log("商铺缓存", "ShopServiceImpl.rebuild", new RuntimeException("Redis超时"), null);

        verify(mockMapper).insert(any(ErrorLog.class));
    }

    @Test
    @DisplayName("降级路径 — DB 写入失败，自动写入本地文件")
    void log_dbFails_fallbackToFile() throws IOException {
        when(mockMapper.insert(any(ErrorLog.class))).thenThrow(new RuntimeException("DB连接断开"));

        errorLogService.log("秒杀订单", "Consumer.onMessage", new RuntimeException("MQ超时"), "{}");

        assertTrue(Files.exists(FALLBACK_FILE), "降级文件应被创建");
        String content = new String(Files.readAllBytes(FALLBACK_FILE), StandardCharsets.UTF_8);
        assertTrue(content.contains("秒杀订单"), "文件应包含业务模块名");
        assertTrue(content.contains("Consumer.onMessage"), "文件应包含方法名");
    }

    @Test
    @DisplayName("安全保证 — log() 永远不会抛出异常")
    void log_neverThrows() {
        when(mockMapper.insert(any(ErrorLog.class))).thenThrow(new RuntimeException("DB挂了"));

        assertDoesNotThrow(() ->
                errorLogService.log("测试模块", "testMethod",
                        new RuntimeException("测试异常"), null));
    }

    @Test
    @DisplayName("null 异常消息 — 不会 NPE")
    void log_nullMessage() {
        when(mockMapper.insert(any(ErrorLog.class))).thenReturn(1);

        assertDoesNotThrow(() ->
                errorLogService.log("模块", "method", new RuntimeException((String) null), null));
    }

    @Test
    @DisplayName("null context — 正常记录，不会 NPE")
    void log_nullContext() {
        when(mockMapper.insert(any(ErrorLog.class))).thenReturn(1);

        assertDoesNotThrow(() ->
                errorLogService.log("模块", "method", new RuntimeException("err"), null));

        verify(mockMapper).insert(any(ErrorLog.class));
    }

    @Test
    @DisplayName("长异常消息 — 自动截断，不会溢出数据库字段")
    void log_longMessage() {
        when(mockMapper.insert(any(ErrorLog.class))).thenReturn(1);

        String longMsg = "x".repeat(1000);
        assertDoesNotThrow(() ->
                errorLogService.log("模块", "method", new RuntimeException(longMsg), null));
    }
}
