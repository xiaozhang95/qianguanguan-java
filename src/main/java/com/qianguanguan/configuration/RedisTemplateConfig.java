package com.qianguanguan.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 序列化配置
 * <p>
 * 统一使用 StringRedisSerializer，避免 JDK 默认序列化导致的问题
 */
@Configuration
public class RedisTemplateConfig {

    /**
     * 配置 RedisTemplate
     * key/value/hashKey/hashValue 均使用 StringRedisSerializer，
     * 并开启事务支持
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
   
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // 设置 key 和 value 的序列化器为 StringRedisSerializer
        // 避免 JDK 默认序列化导致 increment 等命令报错：ERR value is not an integer or out of range
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);

        // 开启事务支持（关键）
        template.setEnableTransactionSupport(true);

        return template;
    }
}
