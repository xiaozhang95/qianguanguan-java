package com.qianguanguan.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*") // 前端地址
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(false) // 注意：true 时不能用 *
                .maxAge(3600);
    }
}