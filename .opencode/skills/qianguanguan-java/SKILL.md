---
name: qianguanguan-java
description: Use when writing or modifying Java backend code in qianguanguan-java/. Covers Spring Boot 3, MyBatis-Plus, JWT auth, and project conventions.
---

# 钱罐罐 Java 后端规范

## 项目栈

- Spring Boot 3.5.6, Java 17, Maven
- MyBatis-Plus 3.5.7 (LambdaQueryWrapper, BaseMapper)
- Spring Security + JWT (jjwt 0.11.5)
- MySQL + Redis (StringRedisTemplate)
- Lombok (@Data, @RequiredArgsConstructor, @Builder)
- Springdoc OpenAPI 2.7.0

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

## Controller 规范

```java
@Tag(name = "模块名", description = "描述")
@RestController
@RequestMapping("/api/business")
@RequiredArgsConstructor
public class XxxController {

    private final XxxService xxxService;

    @Operation(summary = "摘要", description = "详细描述")
    @PostMapping("/xxx/action")
    public ReturnType methodName(HttpServletRequest request, @RequestBody XxxRequest body) {
        Long userId = (Long) request.getAttribute(JwtClaims.ID);       // 当前用户ID
        String role = (String) request.getAttribute(JwtClaims.ROLE);   // 当前用户角色
        return xxxService.method(userId, body);
    }
}
```

## JWT 获取用户信息

- 用户ID: `(Long) request.getAttribute(JwtClaims.ID)`
- 角色: `(String) request.getAttribute(JwtClaims.ROLE)`
- 需要认证的接口**不需要**加入 application.yaml 白名单

## Service 规范

```java
@Service
@RequiredArgsConstructor
public class XxxService {
    private final XxxMapper xxxMapper;
    private final StringRedisTemplate stringRedisTemplate;
    // 按需注入其他依赖
}
```

- 异常处理: `throw new RuntimeException("错误信息")`，由 GlobalResponseHandler 统一捕获返回
- 密码加密: 注入 `PasswordEncoder`，调用 `passwordEncoder.encode(raw)` / `passwordEncoder.matches(raw, hash)`

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

## Token 失效

修改敏感信息（密码、角色、状态）后，清除 Redis 缓存使 JWT 立即失效：
```java
stringRedisTemplate.delete("user_" + userId);
```

## 安全白名单

公开接口（无需登录）需要在 `application.yaml` 的 `security.whitelist` 中添加：
```yaml
security:
  whitelist:
    - /upload/**
    - /api/business/login
    - /api/business/register
    - /api/business/captcha
```

## DTO 命名

请求体放在 `model/` 包下，命名规则：
- 请求: `XxxRequest` (如 `LoginRequest`, `RegisterRequest`) — 使用 `@Data`
- 响应: `XxxResponse` 或 `XxxVO` (如 `LoginResponse`, `BillStatsVO`) — 使用 `@Builder`

## 日志

SQL 日志输出到控制台 (`StdOutImpl`)。文件日志配置见 `logback-spring.xml`，按天滚动。
