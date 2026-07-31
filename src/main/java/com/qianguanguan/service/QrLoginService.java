package com.qianguanguan.service;

// --- MyBatis-Plus 查询条件构造器 ---
// LambdaQueryWrapper：使用 lambda 表达式构建类型安全的 WHERE 条件
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
// --- JWT & 微信配置属性 ---
// SecurityProperties：从 application.yaml 读取 jwt.secret、wx.appId 等配置
import com.qianguanguan.configuration.SecurityProperties;
// --- 用户实体 ---
import com.qianguanguan.entity.User;
// --- 用户表 Mapper ---
import com.qianguanguan.mapper.UserMapper;
// --- 登录响应模型（含 Token + UserInfo） ---
import com.qianguanguan.model.LoginResponse;
// --- JWT 工具类：签发和解析 Token ---
import com.qianguanguan.util.Jwt;
// --- JJWT 密钥工具 ---
// Keys.hmacShaKeyFor：将字节数组转换为 HMAC-SHA256 签名密钥
import io.jsonwebtoken.security.Keys;
// --- Lombok ---
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
// --- Spring Data Redis ---
// StringRedisTemplate：操作 Redis String 类型键值对
import org.springframework.data.redis.core.StringRedisTemplate;
// --- Spring Security 密码编码器 ---
// PasswordEncoder：BCrypt 加密和比对（扫码登录新用户自动注册时加密密码）
import org.springframework.security.crypto.password.PasswordEncoder;
// --- Spring 服务层注解 ---
import org.springframework.stereotype.Service;

// --- Java 基础库 ---
import java.security.Key;           // JWT 签名密钥接口
import java.security.SecureRandom;  // 加密安全的随机数生成器
import java.util.*;                 // Date, HashMap, Map 等
import java.util.concurrent.TimeUnit; // Redis 过期时间单位

/**
 * 微信扫码登录核心服务
 * <p>
 * 实现跨端扫码登录的完整流程（Web 端展示二维码 → 手机微信扫码 → 小程序确认 → Web 端自动登录）：
 * <ol>
 *   <li><b>生成场景码</b>：Web 端调用 generateScene()，服务端生成 16 位随机场景 ID，
 *       存入 Redis Hash（状态=pending），调用微信接口生成小程序码图片</li>
 *   <li><b>用户扫码</b>：微信扫描小程序码后打开小程序，小程序读取 scene 参数（格式 "login:场景ID"）</li>
 *   <li><b>确认登录</b>：小程序端调用 confirmLogin()，通过微信 code 换取 OpenID，
 *       查找或自动注册用户，生成 JWT Token 写入 Redis Hash</li>
 *   <li><b>轮询检测</b>：Web 端每 2 秒调用 checkStatus()，检测到 confirmed 后取出 Token 完成登录</li>
 * </ol>
 * <p>
 * Redis 数据结构（Hash）：
 * <pre>
 *   Key:   qr_login:{场景ID}
 *   Field: status → "pending" | "confirmed"
 *   Field: token  → JWT Token（确认后填充）
 *   过期：  300 秒（5 分钟）
 * </pre>
 */
@Slf4j                            // Lombok：自动生成 log 日志对象
@Service                          // 标记为 Spring Bean
@RequiredArgsConstructor           // 自动生成构造器注入
public class QrLoginService {

    /** Redis key 前缀，完整 key = qr_login:{场景ID} */
    private static final String REDIS_PREFIX = "qr_login:";
    /** 场景码有效期：300 秒（5 分钟），超时未扫码则过期 */
    private static final int SCENE_EXPIRE_SECONDS = 300;
    /** 场景状态：等待扫码 */
    private static final String STATUS_PENDING = "pending";
    /** 场景状态：已确认登录 */
    private static final String STATUS_CONFIRMED = "confirmed";
    /** 小程序码跳转页面路径 */
    private static final String WXA_PAGE = "pages/bill/index";

    // --- 依赖注入 ---

    /** Redis 操作：存储场景状态和 Token */
    private final StringRedisTemplate stringRedisTemplate;
    /** 用户表 Mapper：查询/插入用户 */
    private final UserMapper userMapper;
    /** BCrypt 密码编码器：新用户自动注册时加密密码 */
    private final PasswordEncoder passwordEncoder;
    /** 安全配置：读取 JWT 密钥和过期时间 */
    private final SecurityProperties securityProperties;
    /** 微信 API 服务：获取小程序码 */
    private final WxService wxService;

    /**
     * 生成扫码登录的小程序码
     * <p>
     * 流程：
     * <ol>
     *   <li>生成 16 位随机场景 ID</li>
     *   <li>创建 Redis Hash：{status: "pending", token: ""}</li>
     *   <li>设置 Hash 整体过期时间 300 秒</li>
     *   <li>调用微信 getwxacodeunlimit 接口生成小程序码 base64 图片</li>
     * </ol>
     *
     * @return Map 包含 sceneId（场景ID）和 qrImage（base64 小程序码图片）
     * @throws RuntimeException 微信接口调用失败
     */
    public Map<String, String> generateScene() {
        // --- 第1步：生成 16 位随机场景 ID ---
        // 如 "aB3xK9mQ2pL7rT5w"
        String sceneId = generateShortId(16);

        // --- 第2步：创建 Redis Hash，存储场景初始数据 ---
        // Java 中先用 HashMap 构建数据，再批量写入 Redis
        Map<String, String> sceneData = new HashMap<>();
        sceneData.put("status", STATUS_PENDING); // 初始状态：等待扫码
        sceneData.put("token", "");              // Token 字段留空，确认后填充

        // --- 第3步：写入 Redis ---
        // putAll：批量写入 Hash 的多个 field-value
        // Key = qr_login:{场景ID}
        stringRedisTemplate.opsForHash().putAll(REDIS_PREFIX + sceneId, sceneData);
        // expire：设置整个 Hash 的过期时间（300 秒后自动删除）
        stringRedisTemplate.expire(REDIS_PREFIX + sceneId, SCENE_EXPIRE_SECONDS, TimeUnit.SECONDS);

        // --- 第4步：调用微信接口生成小程序码 ---
        // scene 参数格式："login:场景ID"，小程序端通过 onLoad(options) 获取 options.scene
        String qrImage = wxService.getWxaCode("login:" + sceneId, WXA_PAGE);
        // 返回 null 说明微信接口调用失败（如 access_token 过期且重试也失败）
        if (qrImage == null) {
            throw new RuntimeException("生成小程序码失败");
        }

        // --- 第5步：构建返回结果 ---
        Map<String, String> result = new HashMap<>();
        result.put("sceneId", sceneId);   // 前端保存 sceneId，用于轮询 checkStatus
        result.put("qrImage", qrImage);   // base64 图片，可直接用于 <img src="...">
        return result;
    }

    /**
     * 查询场景码状态（Web 端轮询接口）
     * <p>
     * Web 端每 2 秒调用一次：
     * <ul>
     *   <li>返回 "pending"：继续等待（用户还未扫码或未确认）</li>
     *   <li>返回 "confirmed"：登录成功，附带 Token</li>
     *   <li>返回 "expired"：场景码已过期（超过 5 分钟或已被使用）</li>
     * </ul>
     * <p>
     * 确认后立即删除 Redis 中的场景数据，防止 Token 泄露。
     *
     * @param sceneId 场景 ID（由 generateScene 返回）
     * @return Map 包含 status 和 token（仅 confirmed 时返回）
     */
    public Map<String, Object> checkStatus(String sceneId) {
        // --- 从 Redis Hash 中读取状态和 Token ---
        // opsForHash().get(key, field)：获取 Hash 中指定 field 的值
        // 返回值类型是 Object，需要强制转换为 String
        String status = (String) stringRedisTemplate.opsForHash().get(REDIS_PREFIX + sceneId, "status");
        String token = (String) stringRedisTemplate.opsForHash().get(REDIS_PREFIX + sceneId, "token");

        // --- 构建响应 ---
        Map<String, Object> result = new HashMap<>();
        if (status == null) {
            // status 为 null 说明 Redis key 不存在（已过期或已被删除）
            result.put("status", "expired");
        } else {
            // 返回真实状态（pending / confirmed）
            result.put("status", status);
            if (STATUS_CONFIRMED.equals(status)) {
                // 已确认：附带 Token 给前端
                result.put("token", token);
                // 立即删除 Redis 数据，防止 Token 被重复获取
                stringRedisTemplate.delete(REDIS_PREFIX + sceneId);
            }
        }
        return result;
    }

    /**
     * 小程序端确认登录
     * <p>
     * 用户在小程序端点击"确认登录"后调用此接口：
     * <ol>
     *   <li>校验场景码状态（是否存在、是否已过期、是否已被使用）</li>
     *   <li>通过微信 code 换取 OpenID</li>
     *   <li>根据 OpenID 查找用户，不存在则自动注册</li>
     *   <li>生成 JWT Token 并缓存到 Redis</li>
     *   <li>更新场景状态为 confirmed，写入 Token</li>
     * </ol>
     *
     * @param sceneId 场景 ID（从扫码获取）
     * @param code    微信小程序 wx.login() 返回的临时登录凭证
     * @return 登录响应（含 JWT Token 和用户信息）
     * @throws RuntimeException 场景码过期、已被使用、微信接口失败
     */
    public LoginResponse confirmLogin(String sceneId, String code) {
        // --- 第1步：校验场景码状态 ---
        String status = (String) stringRedisTemplate.opsForHash().get(REDIS_PREFIX + sceneId, "status");
        if (status == null) {
            // Redis key 不存在（超过 300 秒自动过期，或已被 checkStatus 删除）
            throw new RuntimeException("二维码已过期");
        }
        if (STATUS_CONFIRMED.equals(status)) {
            // 已被确认过（防止重复确认）
            throw new RuntimeException("二维码已被使用");
        }

        // --- 第2步：通过微信 code 换取 OpenID ---
        // code 是 wx.login() 返回的临时凭证，有效期约 5 分钟
        String openId = wxService.getOpenId(code);

        // --- 第3步：根据 OpenID 查找系统用户 ---
        // LambdaQueryWrapper：构建 WHERE wid = ? 条件
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getWid, openId)  // wid 字段存储微信 OpenID
        );

        // --- 第4步：用户不存在则自动注册 ---
        if (user == null) {
            user = new User();
            // 用户名：wx_ + OpenID 前 8 位，保证唯一
            user.setUsername("wx_" + openId.substring(0, 8));
            // 密码：BCrypt 加密 OpenID（实际上用户不会用密码登录）
            user.setPassword(passwordEncoder.encode(openId));
            // 昵称：取 OpenID 前 4 位
            user.setName("钱罐罐用户_" + openId.substring(0, 4));
            user.setRole("USER");   // 默认普通用户角色
            user.setStatus(1);      // 启用状态
            user.setWid(openId);    // 绑定微信 OpenID
            userMapper.insert(user); // insert 后 MyBatis-Plus 会自动回填 user.id
        }

        // --- 第5步：生成 JWT Token ---
        Date now = new Date(); // 当前时间作为签发时间（iat）
        // 从配置的密钥字符串创建 HMAC-SHA256 签名 Key
        Key key = Keys.hmacShaKeyFor(securityProperties.getJwt().getSecret().getBytes());
        // 过期时间 = 当前时间 + 配置的 JWT 有效期
        Date expirationDate = new Date(now.getTime() + securityProperties.getJwt().getExpiration());

        // Jwt.generateToken：将用户ID和角色写入 JWT Payload，生成签名字符串
        String token = Jwt.generateToken(
                user.getId(),   // 用户ID（存入 JWT Claims）
                user.getRole(), // 用户角色（存入 JWT Claims）
                key,            // 签名密钥
                now,            // 签发时间
                expirationDate  // 过期时间
        );

        // --- 第6步：签发时间戳缓存到 Redis（用于 JWT 过滤器二次校验） ---
        // Key: user_{用户ID}  Value: 签发时间戳（毫秒）
        // 过期时间 = JWT 有效期 + 24 小时（比 Token 多活一天，支持刷新）
        stringRedisTemplate.opsForValue().set(
                "user_" + user.getId(),                 // Redis Key
                String.valueOf(now.getTime()),          // 签发时间戳
                securityProperties.getJwt().getExpiration(), // 有效期
                TimeUnit.MILLISECONDS                   // 时间单位：毫秒
        );

        // --- 第7步：更新场景状态为 confirmed，写入 Token ---
        // 小程序端确认后，Web 端的轮询会立即检测到状态变化
        stringRedisTemplate.opsForHash().put(REDIS_PREFIX + sceneId, "status", STATUS_CONFIRMED);
        stringRedisTemplate.opsForHash().put(REDIS_PREFIX + sceneId, "token", token);

        // --- 第8步：构建登录响应 ---
        // 使用 Builder 模式创建 LoginResponse，将用户信息封装为嵌套的 UserInfo
        return LoginResponse.builder()
                .token(token) // JWT Token（含 "Bearer " 前缀）
                .userInfo(LoginResponse.UserInfo.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .name(user.getName())
                        .role(user.getRole())
                        .phone(user.getPhone())
                        .email(user.getEmail())
                        .avatar(user.getAvatar())
                        .wid(user.getWid())
                        .createtime(user.getCreatetime())
                        .build())
                .build();
    }

    /**
     * 生成指定长度的随机字符串（用于场景 ID）
     * <p>
     * 字符池包含大小写字母和数字共 62 个字符，
     * 使用 SecureRandom 保证加密安全性。
     *
     * @param length 字符串长度
     * @return 随机字符串
     */
    private String generateShortId(int length) {
        // 字符池：A-Z、a-z、0-9（共 62 个字符）
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom(); // 加密安全的随机数生成器
        StringBuilder sb = new StringBuilder(length); // 预分配容量，避免扩容
        // 循环 length 次，每次从字符池随机取一个字符拼接
        for (int i = 0; i < length; i++) {
            // random.nextInt(chars.length())：生成 0 ~ 61 的随机索引
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString(); // 返回随机字符串
    }
}
