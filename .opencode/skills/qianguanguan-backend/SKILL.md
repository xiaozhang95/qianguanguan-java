---
name: qianguanguan-backend
description: Use when working with the qianguanguan-java (钱罐罐) Spring Boot backend. Covers entity design, API conventions, Jackson config, MyBatis-Plus, security, and exception handling.
---

# qianguanguan-java 后端规范

## 项目技术栈

Spring Boot + MyBatis-Plus + MySQL + Redis + Spring Security + JWT

## 实体（Entity）规范

- 所有实体使用 `@TableId(type = IdType.ASSIGN_ID)` 生成 Snowflake 64位Long型ID
- 表名前缀 `t_` 在 MyBatis-Plus 全局配置 `mybatis-plus.global-config.db-config.table-prefix: t_` 中统一处理，实体类 `@TableName` 不加 `t_` 前缀
- 实体放在 `com.qianguanguan.entity` 包下

## Jackson 序列化规范

- **必须**配置 `JacksonConfig`，将 `Long` 类型全局序列化为 `String`（防止 JavaScript 精度丢失）:
```java
@Configuration
public class JacksonConfig {
    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jackson2ObjectMapperBuilderCustomizer() {
        return builder -> {
            builder.serializerByType(Long.class, ToStringSerializer.instance);
            builder.serializerByType(Long.TYPE, ToStringSerializer.instance);
        };
    }
}
```
- 日期格式：`spring.jackson.date-format: yyyy-MM-dd`，时区 `GMT+8`

## Controller / API 接口规范

- 所有接口统一前缀 `/api/business`
- **URL 必须包含语义化动作后缀**，禁止仅用 HTTP method 区分操作：
  - 新增：`POST /{resource}/save`
  - 更新：`PUT /{resource}/update/{id}`
  - 删除：`DELETE /{resource}/delete/{id}`
  - 列表：`POST /{resource}/list`
- 从 JWT 获取当前用户：`(Long) request.getAttribute(JwtClaimKey.ID.getKey())`

## Service 规范

- 删除和更新操作**必须**先校验账单所有权（`userId` 匹配），不存在或无权操作均抛出 `RuntimeException`
- 业务逻辑放在 Service 层，Controller 仅做参数提取和调用
- 分页使用 MyBatis-Plus `IPage` / `Page`

## 响应规范

- 全局响应包装：`GlobalResponseHandler` 实现 `ResponseBodyAdvice`，统一返回 `Response<T>`（code / data / msg）
- 全局异常处理：`@ExceptionHandler` 捕获所有异常，包装为 `Response.error(msg)`
- 返回对象使用 VO（放在 `com.qianguanguan.model`），不与 Entity 直接暴露

## Mapper 规范

- 继承 MyBatis-Plus `BaseMapper<T>`
- 复杂查询使用 `LambdaQueryWrapper`
