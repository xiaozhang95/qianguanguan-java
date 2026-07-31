package com.qianguanguan.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * RestTemplate 配置
 * <p>
 * 注入 RestTemplate Bean，用于发起 HTTP 请求（如调用微信 API）
 */
@Configuration
public class RestTemplateConfig {

    /**
     * 创建 RestTemplate 实例
     */
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
