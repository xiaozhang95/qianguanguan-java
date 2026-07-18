package com.qianguanguan.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "security")
public class SecurityProperties {

    private List<String> whitelist;

    private List<String> swaggerPaths;

    private JwtConfig jwt;

    @Data
    public static class JwtConfig {
        private String secret;
        private Long expiration;
    }

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