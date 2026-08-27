package com.wen.thumbsystembackend.configuration;

import com.wen.thumbsystembackend.interceptors.UserLoginInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfigure implements WebMvcConfigurer {
    @Autowired
    private UserLoginInterceptor userLoginInterceptor;
    @Autowired
    private ResourcePatternResolver resourcePatternResolver;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(userLoginInterceptor)
                .excludePathPatterns(
                        "/user/login",            // 业务公开接口（拦截器匹配 servlet 相对路径，无 /api 前缀）
                        "/doc.html",              // knife4j 页面
                        "/webjars/**",            // knife4j 的 js/css
                        "/v3/api-docs/**",        // springdoc 文档数据
                        "/swagger-ui*/**",        // swagger 静态资源
                        "/favicon.ico",
                        "/error")                 // 错误转发，否则 400/404 都会被拦成空响应
                .addPathPatterns("/**");
    }
}
