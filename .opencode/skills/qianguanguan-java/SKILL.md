---
name: qianguanguan-java
description: Use when writing or modifying Java backend code in qianguanguan-java/. Covers Spring Boot 3, MyBatis-Plus, JWT auth, project conventions, and database management.
---

# 钱罐罐 后端规范

## 项目技术栈

Spring Boot 3.5.6 + MyBatis-Plus 3.5.7 + MySQL + Redis + Spring Security + JWT (jjwt 0.11.5)
Lombok, Springdoc OpenAPI 2.7.0, Java 17, Maven

## 数据库

- 初始化脚本: `sql/init.sql`
- 变更记录: `sql/migration.sql`
- 每次修改表结构后，在 `migration.sql` 追加一条带日期的 DDL 语句

## 项目结构

```
qianguanguan-java/src/main/java/com/qianguanguan/
├── configuration/   # @Configuration: Security, CORS, MyBatis-Plus, Redis, Jackson, OpenAPI
├── constant/        # 常量类: JwtClaims, Role, BillType
├── controller/      # @RestController, 路由前缀 /api/business
├── entity/          # @TableName, MyBatis-Plus 实体
├── filter/          # OncePerRequestFilter: JwtAuthenticationFilter
├── mapper/          # extends BaseMapper<T>
├── model/           # 请求/响应 DTO (@Data / @Builder)
├── response/        # 全局响应封装: Response<T>, GlobalResponseHandler
├── service/         # @Service 业务逻辑
└── util/            # 工具类: Jwt
```

## 实体（Entity）规范

- 所有实体使用 `@TableId(type = IdType.ASSIGN_ID)` 生成 Snowflake 64位 Long 型 ID
- 表名前缀 `t_` 由 MyBatis-Plus 全局配置统一处理，实体类 `@TableName` 不加 `t_` 前缀
- 实体放在 `com.qianguanguan.entity` 包下

## Jackson 序列化规范

- JacksonConfig 将 `Long` 类型全局序列化为 `String`（防止 JavaScript 精度丢失）
- 日期格式：`spring.jackson.date-format: yyyy-MM-dd`，时区 `GMT+8`

## 分类多用户隔离

- 分类表 `t_category` 有 `user_id` 字段：`NULL` = 系统默认（所有人可见），非 NULL = 用户自定义（仅自己可见）
- 列表查询：`WHERE user_id IS NULL OR user_id = ?`
- 新增：自动填入当前用户 ID
- 修改系统默认分类：自动创建用户副本，原默认不受影响
- 修改自定义分类：仅限创建者本人
- 删除：只允许删除自己的分类，系统默认分类和他人分类不可删除

## Controller / API 规范

```java
@Tag(name = "模块名", description = "描述")
@RestController
@RequestMapping("/api/business")
@RequiredArgsConstructor
public class XxxController {

    private final XxxService xxxService;

    @Operation(summary = "摘要")
    @PostMapping("/xxx/action")
    public ReturnType methodName(HttpServletRequest request, @RequestBody XxxRequest body) {
        Long userId = (Long) request.getAttribute(JwtClaims.ID);
        return xxxService.method(userId, body);
    }
}
```

- 所有接口统一前缀 `/api/business`
- URL 必须包含语义化动作后缀：
  - 新增：`POST /{resource}/save`
  - 更新：`PUT /{resource}/update/{id}`
  - 删除：`DELETE /{resource}/delete/{id}`
  - 列表：`POST /{resource}/list`
- Controller 仅做参数提取和调用，业务逻辑放 Service 层

## JWT 获取用户信息

- 用户ID: `(Long) request.getAttribute(JwtClaims.ID)`
- 角色: `(String) request.getAttribute(JwtClaims.ROLE)`

## Service 规范

```java
@Service
@RequiredArgsConstructor
public class XxxService {
    private final XxxMapper xxxMapper;
}
```

- 异常处理: `throw new RuntimeException("错误信息")`，由 GlobalResponseHandler 统一捕获返回
- 删除和更新操作必须先校验所有权（`userId` 匹配）
- 分页使用 MyBatis-Plus `IPage` / `Page`

## Mapper 规范

```java
@Mapper
public interface XxxMapper extends BaseMapper<Xxx> {
}
```

- 查询使用 LambdaQueryWrapper:
```java
LambdaQueryWrapper<Bill> wrapper = new LambdaQueryWrapper<Bill>()
    .eq(Bill::getUserId, userId)
    .likeRight(Bill::getBillDate, "2026-07");
```

## 响应规范

- 全局响应包装：`GlobalResponseHandler` 统一返回 `Response<T>`（code / data / msg）
- 全局异常处理：`@ExceptionHandler` 捕获所有异常
- 返回对象使用 VO（放在 `model/` 包），不直接暴露 Entity

## DTO 命名

请求体放在 `model/` 包下：
- 请求: `XxxRequest` (如 `LoginRequest`) — 使用 `@Data`
- 响应: `XxxVO` (如 `BillStatsVO`) — 使用 `@Builder`

## Token 失效

修改敏感信息（密码、角色、状态）后，清除 Redis 缓存使 JWT 立即失效：
```java
stringRedisTemplate.delete("user_" + userId);
```

## 安全白名单

公开接口（无需登录）在 `application.yaml` 的 `security.whitelist` 中添加：
```yaml
security:
  whitelist:
    - /upload/**
    - /api/business/login
    - /api/business/register
```

## 日志

SQL 日志输出到控制台 (`StdOutImpl`)。文件日志配置见 `logback-spring.xml`，按天滚动。
