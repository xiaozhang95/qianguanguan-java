package com.qianguanguan.configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI (Swagger) 文档配置
 * <p>
 * 配置 API 文档标题、版本，以及全局 Bearer JWT 认证
 */
@Configuration
public class OpenApiConfig {

    /**
     * 构建 OpenAPI 文档对象
     * 添加全局 JWT Bearer 认证方案，方便在 Swagger UI 中调试需认证的接口
     */
    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("钱罐罐记账 API")
                        .version("1.0"))
                .addSecurityItem(new SecurityRequirement().addList("Bearer Authentication"))
                .components(new Components()
                        .addSecuritySchemes("Bearer Authentication", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .bearerFormat("JWT")
                                .scheme("bearer")));
    }
}
