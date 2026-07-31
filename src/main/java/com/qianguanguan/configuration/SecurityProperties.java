package com.qianguanguan.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 安全相关配置属性
 * <p>
 * 从 application.yaml 中读取 security 前缀下的白名单、Swagger 路径、JWT 配置、微信配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "security")
public class SecurityProperties {

    /** 安全白名单路径列表（无需认证即可访问） */
    private List<String> whitelist;

    /** Swagger 文档路径列表（不拦截、不包装响应体） */
    private List<String> swaggerPaths;

    /** JWT 配置（密钥、过期时间） */
    private JwtConfig jwt;

    /** 微信小程序配置（AppID、AppSecret） */
    private WxConfig wx;

    /**
     * JWT 配置内部类
     */
    @Data
    public static class JwtConfig {
        /** JWT 签名密钥 */
        private String secret;
        /** JWT 过期时间（毫秒） */
        private Long expiration;
    }

    /**
     * 微信配置内部类
     */
    @Data
    public static class WxConfig {
        /** 微信小程序 AppID */
        private String appId;
        /** 微信小程序 AppSecret */
        private String appSecret;
    }

    /**
     * 合并白名单和 Swagger 路径，返回完整放行路径数组
     */
    public String[] getAllWhitelist() {
        List<String> allPaths = new ArrayList<>();
        if (whitelist != null) {
            allPaths.addAll(whitelist);
        }
        if (swaggerPaths != null) {
            allPaths.addAll(swaggerPaths);
        }
        return allPaths.toArray(new String[0]);
    }
}