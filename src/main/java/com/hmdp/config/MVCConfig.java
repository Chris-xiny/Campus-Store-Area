package com.hmdp.config;

import com.hmdp.interceptor.LoginInterceptor;
import com.hmdp.interceptor.RefreshTokenInterceptor;
import com.hmdp.interceptor.TraceIdInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MVCConfig implements WebMvcConfigurer {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Value("${hmdp.upload.image-dir}")
    private String imageUploadDir;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 链路追踪拦截器（最先执行，保证后续拦截器和业务都能拿到 traceId）
        registry.addInterceptor(new TraceIdInterceptor())
                .addPathPatterns("/**")
                .order(-2);

        // 刷新token拦截器
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                .addPathPatterns("/**")
                .excludePathPatterns("/test/**")
                .order(0);

        // 登录拦截器
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/shop/**",
                        "/voucher/**",
                        "/shop-type/**",
                        "/upload/**",
                        "/imgs/**",
                        "/blog/hot",
                        "/user/code",
                        "/user/login",
                        "/test/**"
                ).order(1);
    }

    /**
     * 静态资源映射：本地开发时通过 /imgs/** 访问上传的图片。
     * 生产环境由 Nginx 直接 serve，不走 Spring Boot。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/imgs/**")
                .addResourceLocations("file:" + imageUploadDir);
    }
}
