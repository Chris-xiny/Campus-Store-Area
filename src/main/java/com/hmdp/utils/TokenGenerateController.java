package com.hmdp.utils;

import com.hmdp.dto.Result;
import com.hmdp.utils.TokenGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * Token生成控制器 - 用于JMeter测试准备
 */
@Slf4j
@RestController
@RequestMapping("/test")
public class TokenGenerateController {

    @Resource
    private TokenGenerator tokenGenerator;

    /**
     * 批量生成用户token
     * @param count 用户数量（默认100，最大1000）
     * @param fileName 文件名（可选，默认tokens.txt）
     * @return 生成结果
     */
    @PostMapping("/generate-tokens")
    public Result generateTokens(
            @RequestParam(value = "count", defaultValue = "100") int count,
            @RequestParam(value = "fileName", defaultValue = "tokens.txt") String fileName) {
        
        if (count <= 0 || count > 1000) {
            return Result.fail("用户数量必须在1-1000之间");
        }
        
        try {
            log.info("收到token生成请求，数量: {}, 文件名: {}", count, fileName);
            
            int generatedCount = tokenGenerator.generateTokensManually(count, fileName);
            
            return Result.ok("成功生成 " + generatedCount + " 个token，已保存到 " + fileName);
        } catch (Exception e) {
            log.error("生成token失败", e);
            return Result.fail("生成token失败: " + e.getMessage());
        }
    }

    /**
     * 获取当前tokens.txt文件信息
     * @return 文件信息
     */
    @GetMapping("/token-info")
    public Result getTokenInfo() {
        try {
            java.nio.file.Path path = java.nio.file.Paths.get("tokens.txt");
            
            if (!java.nio.file.Files.exists(path)) {
                return Result.fail("tokens.txt文件不存在");
            }
            
            long lineCount = java.nio.file.Files.lines(path).count();
            long fileSize = java.nio.file.Files.size(path);
            
            java.util.Map<String, Object> info = new java.util.HashMap<>();
            info.put("fileName", "tokens.txt");
            info.put("lineCount", lineCount);
            info.put("fileSize", fileSize + " bytes");
            info.put("filePath", path.toAbsolutePath().toString());
            
            return Result.ok(info);
        } catch (Exception e) {
            log.error("获取token文件信息失败", e);
            return Result.fail("获取文件信息失败: " + e.getMessage());
        }
    }
}
