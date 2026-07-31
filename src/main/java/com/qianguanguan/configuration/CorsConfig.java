package com.qianguanguan.configuration;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 跨域配置 & 静态资源映射
 * <p>
 * 允许所有来源的跨域请求，并将 /upload/** 路径映射到本地 upload 目录
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    /**
     * 配置跨域映射
     * 允许所有来源、所有方法、所有请求头，预检请求缓存 1 小时
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins("*")
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }

    /**
     * 配置静态资源映射
     * 将 /upload/** URL 路径映射到项目根目录下的 upload/ 文件夹
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadPath = System.getProperty("user.dir") + "/upload/";
        registry.addResourceHandler("/upload/**")
                .addResourceLocations("file:" + uploadPath);
    }
}
