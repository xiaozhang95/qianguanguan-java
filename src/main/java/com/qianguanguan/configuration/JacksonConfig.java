package com.qianguanguan.configuration;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson 序列化配置
 * <p>
 * 将 Long 类型序列化为字符串，防止前端 JavaScript 精度丢失
 */
@Configuration
public class JacksonConfig {

    /**
     * 自定义 Jackson ObjectMapper
     * Long 类型（包括基本类型 long）统一转为 String 输出
     */
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer() {
        return builder -> {
            builder.serializerByType(Long.class, ToStringSerializer.instance);
            builder.serializerByType(Long.TYPE, ToStringSerializer.instance);
        };
    }
}
