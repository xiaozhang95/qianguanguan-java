---
name: qianguanguan-backend
description: Use ONLY when working on qianguanguan-java (钱罐罐 Spring Boot后端). Covers API conventions, code patterns, entity/mapper/service/controller layers, MyBatis-Plus usage, and database conventions.
---

# 钱罐罐 后端开发规范

## 技术栈

- **框架**: Spring Boot 3.5.6, Java 17, Maven
- **ORM**: MyBatis-Plus 3.5.7
- **数据库**: MySQL 8.x (`qianguanguan` 库, 表前缀 `t_`)
- **认证**: Spring Security + JWT (jjwt 0.11.5) + BCrypt
- **缓存**: Redis (Spring Data Redis)
- **文档**: SpringDoc OpenAPI 2.7.0 (Swagger)

## API 设计规范

### 请求方法约定

**有请求参数的接口一律使用 POST**，通过 `@RequestBody` 接收 JSON 请求体：

```java
// 列表查询 —— 使用 POST + @RequestBody
@PostMapping("/bill/list")
public IPage<BillVO> billList(HttpServletRequest request, @RequestBody BillListRequest body) {
    Long userId = (Long) request.getAttribute(JwtClaimKey.ID.getKey());
    return billService.list(userId, body.getType(), ...);
}

// 统计查询 —— 使用 POST + @RequestBody
@PostMapping("/bill/stats")
public BillStatsVO billStats(HttpServletRequest request, @RequestBody BillStatsRequest body) { ... }

// 新增 —— POST
@PostMapping("/bill")
public BillVO billSave(HttpServletRequest request, @RequestBody BillSaveRequest billRequest) { ... }

// 更新 —— PUT + 路径参数
@PutMapping("/bill/{id}")
public BillVO billUpdate(HttpServletRequest request, @PathVariable Long id, @RequestBody BillSaveRequest billRequest) { ... }

// 删除 —— DELETE + 路径参数
@DeleteMapping("/bill/{id}")
public void billDelete(HttpServletRequest request, @PathVariable Long id) { ... }
```

### 请求参数 DTO

每个带参数的请求都需创建独立的 Request 类放在 `model/` 包下：

```java
@Data
public class BillListRequest {
    private String type;
    private Long categoryId;
    private String startDate;
    private String endDate;
    private Integer pageNum;
    private Integer pageSize;
}
```

### 获取当前用户

从 JWT filter 注入的 request attribute 中获取用户ID：

```java
Long userId = (Long) request.getAttribute(JwtClaimKey.ID.getKey());
```

## 常量定义规范

**所有领域枚举值必须定义为常量**，禁止在代码中硬编码字符串字面量：

```java
// constant/BillType.java
public class BillType {
    public static final String INCOME = "INCOME";
    public static final String EXPENSE = "EXPENSE";
}
```

使用示例：
```java
import com.qianguanguan.constant.BillType;

.filter(b -> BillType.INCOME.equals(b.getType()))
.filter(b -> BillType.EXPENSE.equals(b.getType()))
```

- 所有业务相关的固定字符串值（类型、状态、角色等）必须定义为 `constant/` 包下的常量
- 常量和对应的数据库字段值保持一致

## 分层结构

| 层 | 包路径 | 职责 |
|---|---|---|
| Controller | `controller/` | 接收请求、参数校验、调用 Service |
| Service | `service/` | 业务逻辑 |
| Mapper | `mapper/` | 数据访问，继承 MyBatis-Plus `BaseMapper<T>` |
| Entity | `entity/` | 数据库表映射，使用 `@TableName("t_xxx")` |
| Model | `model/` | 请求/响应 DTO、VO |
| Response | `response/` | 全局响应包装、异常处理 |
| Configuration | `configuration/` | Spring 配置类 |
| Constant | `constant/` | 领域常量（BillType、JwtClaimKey 等） |
| Filter | `filter/` | JWT 认证过滤器 |
| Util | `util/` | 工具类 |

## Entity 规范

```java
@Data
@TableName("t_xxx")
public class Xxx {
    @TableId(type = IdType.ASSIGN_ID)  // 雪花算法生成ID
    private Long id;
    private String name;
    private Date createtime;
}
```

- 使用 `@Data` (Lombok)
- `@TableName` 对应表名（MyBatis-Plus 全局配置已设置 `t_` 前缀）
- 主键用 `@TableId(type = IdType.ASSIGN_ID)`
- 日期类型用 `java.util.Date`

## Mapper 规范

```java
@Mapper
public interface XxxMapper extends BaseMapper<Xxx> {
}
```

无需自定义 SQL，CRUD 由 MyBatis-Plus 自动提供。复杂查询使用 `LambdaQueryWrapper`。

使用 `selectBatchIds` 时注意空集合检查，避免生成 `WHERE id IN ()` 非法 SQL：

```java
Map<Long, Category> categoryMap = categoryIds.isEmpty()
        ? Map.of()
        : categoryMapper.selectBatchIds(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, c -> c));
```

## Service 规范

```java
@Service
@RequiredArgsConstructor
public class XxxService {
    private final XxxMapper xxxMapper;
    // 使用 LambdaQueryWrapper 构建查询条件
}
```

## 数据库配置

- 数据库连接: `jdbc:mysql://127.0.0.1:3306/qianguanguan`
- 用户名/密码: `root` / `00000000`
- 初始化脚本: 项目根目录 `init.sql`

## 安全配置

- 白名单路径在 `application.yaml` 中配置 (`security.whitelist`)
- Swagger 文档路径自动放行
- JWT 私钥: `1234567890abcdef1234567890abcdef`
- Token 有效期: 1小时 (3600000ms)
- 密码加密: BCryptPasswordEncoder
