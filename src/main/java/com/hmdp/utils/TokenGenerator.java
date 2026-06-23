package com.hmdp.utils;

import cn.hutool.core.util.RandomUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_CODE_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_CODE_TTL;

/**
 * Token批量生成工具 - 用于JMeter压力测试
 * 使用方法：
 * 1. 修改USER_COUNT设置需要生成的用户数量
 * 2. 运行项目，会自动生成tokens.txt文件
 * 3. tokens.txt文件位于项目根目录
 */
@Slf4j
@Component
public class TokenGenerator implements CommandLineRunner {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // ==================== 配置区域 ====================
    
    /**
     * 需要生成的用户数量
     */
    private static final int USER_COUNT = 100;
    
    /**
     * 手机号前缀（建议根据实际业务调整）
     */
    private static final String PHONE_PREFIX = "138";
    
    /**
     * 生成的token文件路径
     */
    private static final String TOKEN_FILE_PATH = "tokens.txt";
    
    /**
     * 是否启用自动生成（生产环境建议设为false）
     */
    private static final boolean ENABLE_AUTO_GENERATE = false;

    // ==================================================

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public void run(String... args) throws Exception {
        if (!ENABLE_AUTO_GENERATE) {
            log.info("Token自动生成已禁用，如需生成请修改ENABLE_AUTO_GENERATE为true");
            return;
        }

        log.info("开始生成 {} 个用户token...", USER_COUNT);
        
        List<String> tokens = generateTokens(USER_COUNT);
        
        saveTokensToFile(tokens, TOKEN_FILE_PATH);
        
        log.info("成功生成 {} 个token，已保存到 {}", tokens.size(), TOKEN_FILE_PATH);
    }

    /**
     * 批量生成用户token
     * @param count 用户数量
     * @return token列表
     */
    public List<String> generateTokens(int count) {
        List<String> tokens = new ArrayList<>();
        
        for (int i = 0; i < count; i++) {
            try {
                String phone = generatePhoneNumber(i);
                String token = loginOrRegisterUser(phone);
                
                if (token != null) {
                    tokens.add(token);
                    log.info("[{}/{}] 用户 {} 登录成功，token: {}", 
                        i + 1, count, phone, token);
                } else {
                    log.warn("[{}/{}] 用户 {} 登录失败", i + 1, count, phone);
                }
                
                Thread.sleep(10);
            } catch (Exception e) {
                log.error("生成第{}个用户token时出错", i + 1, e);
            }
        }
        
        return tokens;
    }

    /**
     * 生成手机号
     * @param index 序号
     * @return 手机号
     */
    private String generatePhoneNumber(int index) {
        return PHONE_PREFIX + String.format("%08d", index);
    }

    /**
     * 登录或注册用户
     * @param phone 手机号
     * @return token
     */
    private String loginOrRegisterUser(String phone) {
        try {
            String code = sendVerificationCode(phone);
            
            if (code == null) {
                log.warn("发送验证码失败，手机号: {}", phone);
                return null;
            }
            
            return doLogin(phone, code);
        } catch (Exception e) {
            log.error("登录用户失败，手机号: {}", phone, e);
            return null;
        }
    }

    /**
     * 发送验证码（直接写入Redis）
     * @param phone 手机号
     * @return 验证码
     */
    private String sendVerificationCode(String phone) {
        String code = RandomUtil.randomNumbers(6);
        
        try {
            stringRedisTemplate.opsForValue().set(
                LOGIN_CODE_KEY + phone, 
                code, 
                LOGIN_CODE_TTL, 
                TimeUnit.MINUTES
            );
            return code;
        } catch (Exception e) {
            log.error("保存验证码到Redis失败", e);
            return null;
        }
    }

    /**
     * 执行登录
     * @param phone 手机号
     * @param code 验证码
     * @return token
     */
    private String doLogin(String phone, String code) {
        String url = "http://localhost:8081/user/login";
        
        Map<String, String> loginData = new HashMap<>();
        loginData.put("phone", phone);
        loginData.put("code", code);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<Map<String, String>> request = new HttpEntity<>(loginData, headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                Map<String, Object> body = response.getBody();
                if (body != null && Boolean.TRUE.equals(body.get("success"))) {
                    return (String) body.get("data");
                }
            }
        } catch (Exception e) {
            log.error("调用登录接口失败", e);
        }
        
        return null;
    }

    /**
     * 保存token到文件
     * @param tokens token列表
     * @param filePath 文件路径
     */
    private void saveTokensToFile(List<String> tokens, String filePath) {
        try (FileWriter writer = new FileWriter(filePath)) {
            for (String token : tokens) {
                writer.write(token + "\n");
            }
            writer.flush();
            log.info("成功保存 {} 个token到文件: {}", tokens.size(), filePath);
        } catch (IOException e) {
            log.error("保存token到文件失败", e);
            throw new RuntimeException("保存token文件失败", e);
        }
    }

    /**
     * 手动触发token生成（可通过Controller调用）
     * @param count 用户数量
     * @param outputPath 输出路径
     * @return 生成的token数量
     */
    public int generateTokensManually(int count, String outputPath) {
        log.info("手动触发token生成，数量: {}", count);
        
        List<String> tokens = generateTokens(count);
        saveTokensToFile(tokens, outputPath);
        
        return tokens.size();
    }
}
