package com.qianguanguan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qianguanguan.configuration.SecurityProperties;
import com.qianguanguan.entity.User;
import com.qianguanguan.mapper.UserMapper;
import com.qianguanguan.model.LoginResponse;
import com.qianguanguan.util.Jwt;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.TimeUnit;

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
// Lombok：自动生成 log 日志对象
@Slf4j
// 标记为 Spring Bean
@Service
// 自动生成构造器注入
@RequiredArgsConstructor
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
        // 生成随机场景 ID，如 "aB3xK9mQ2pL7rT5w"
        String sceneId = generateShortId(16);

        // --- 第2步：创建 Redis Hash，存储场景初始数据 ---
        // 先用 HashMap 组装场景初始数据
        Map<String, String> sceneData = new HashMap<>();
        // 初始状态：等待扫码
        sceneData.put("status", STATUS_PENDING);
        // Token 字段留空，确认后填充
        sceneData.put("token", "");

        // --- 第3步：写入 Redis ---
        // 批量写入 Hash 的各字段
        stringRedisTemplate.opsForHash().putAll(REDIS_PREFIX + sceneId, sceneData);
        // 设置整个 Hash 300 秒后自动过期
        stringRedisTemplate.expire(REDIS_PREFIX + sceneId, SCENE_EXPIRE_SECONDS, TimeUnit.SECONDS);

        // --- 第4步：调用微信接口生成小程序码 ---
        // 生成携带场景 ID 的小程序码 base64 图片
        String qrImage = wxService.getWxaCode("login:" + sceneId, WXA_PAGE);
        // 微信接口调用失败（如 access_token 过期且重试失败）
        if (qrImage == null) {
            // 抛出业务异常终止流程
            throw new RuntimeException("生成小程序码失败");
        }

        // --- 第5步：构建返回结果 ---
        // 构建返回结果 Map
        Map<String, String> result = new HashMap<>();
        // 前端保存 sceneId，用于轮询 checkStatus
        result.put("sceneId", sceneId);
        // base64 图片，可直接用于 <img src="...">
        result.put("qrImage", qrImage);
        // 返回场景 ID 和二维码图片
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
        // 读取状态字段（Object 强转 String）
        String status = (String) stringRedisTemplate.opsForHash().get(REDIS_PREFIX + sceneId, "status");
        // 读取 Token 字段
        String token = (String) stringRedisTemplate.opsForHash().get(REDIS_PREFIX + sceneId, "token");

        // --- 构建响应 ---
        // 构建查询结果
        Map<String, Object> result = new HashMap<>();
        // status 为 null 说明 key 不存在（已过期或已被删除）
        if (status == null) {
            // 标记为已过期
            result.put("status", "expired");
        } else {
            // 返回真实状态（pending / confirmed）
            result.put("status", status);
            // 已确认登录
            if (STATUS_CONFIRMED.equals(status)) {
                // 附带 Token 供 Web 端使用
                result.put("token", token);
                // 立即删除场景数据，防止 Token 被重复获取
                stringRedisTemplate.delete(REDIS_PREFIX + sceneId);
            }
        }
        // 返回状态查询结果
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
        // 读取场景状态
        String status = (String) stringRedisTemplate.opsForHash().get(REDIS_PREFIX + sceneId, "status");
        // 场景不存在：超过 300 秒过期，或已被 checkStatus 删除
        if (status == null) {
            // 拒绝确认
            throw new RuntimeException("二维码已过期");
        }
        // 已被确认过（防止重复确认）
        if (STATUS_CONFIRMED.equals(status)) {
            // 拒绝重复使用
            throw new RuntimeException("二维码已被使用");
        }

        // --- 第2步：通过微信 code 换取 OpenID ---
        // 用临时登录凭证 code 换取 OpenID
        String openId = wxService.getOpenId(code);

        // --- 第3步：根据 OpenID 查找系统用户 ---
        // 按 OpenID 查询用户
        User user = userMapper.selectOne(
                // 构建类型安全的查询条件
                new LambdaQueryWrapper<User>()
                        // 条件：wid = OpenID
                        .eq(User::getWid, openId)
        );

        // --- 第4步：用户不存在则自动注册 ---
        // 未注册过则自动创建
        if (user == null) {
            // 创建用户实体
            user = new User();
            // 用户名：wx_ + OpenID 前 8 位，保证唯一
            user.setUsername("wx_" + openId.substring(0, 8));
            // 密码：BCrypt(OpenID)，实际用户不靠密码登录
            user.setPassword(passwordEncoder.encode(openId));
            // 昵称取 OpenID 前 4 位
            user.setName("钱罐罐用户_" + openId.substring(0, 4));
            // 默认普通用户角色
            user.setRole("USER");
            // 启用状态
            user.setStatus(1);
            // 绑定微信 OpenID
            user.setWid(openId);
            // insert 后 MyBatis-Plus 会自动回填 user.id
            userMapper.insert(user);
        }

        // --- 第5步：生成 JWT Token ---
        // 当前时间作为签发时间（iat）
        Date now = new Date();
        // 由配置的密钥字节构建 HMAC-SHA256 签名 Key
        Key key = Keys.hmacShaKeyFor(securityProperties.getJwt().getSecret().getBytes());
        // 过期时刻 = 签发时间 + 配置的有效期
        Date expirationDate = new Date(now.getTime() + securityProperties.getJwt().getExpiration());

        // 将用户ID和角色写入 JWT Payload 并签名
        // 签发 JWT Token
        String token = Jwt.generateToken(
                // 用户ID（存入 JWT Claims）
                user.getId(),
                // 用户角色（存入 JWT Claims）
                user.getRole(),
                // 签名密钥
                key,
                // 签发时间
                now,
                // 过期时间
                expirationDate
        );

        // --- 第6步：签发时间戳缓存到 Redis（用于 JWT 过滤器二次校验） ---
        // 缓存签发时间戳
        stringRedisTemplate.opsForValue().set(
                // Redis Key：user_{用户ID}
                "user_" + user.getId(),
                // 签发时间戳（毫秒）
                String.valueOf(now.getTime()),
                // TTL = JWT 过期时长（毫秒）
                securityProperties.getJwt().getExpiration(),
                // 时间单位：毫秒
                TimeUnit.MILLISECONDS
        );

        // --- 第7步：更新场景状态为 confirmed，写入 Token ---
        // 状态改为已确认，Web 端轮询即可感知
        stringRedisTemplate.opsForHash().put(REDIS_PREFIX + sceneId, "status", STATUS_CONFIRMED);
        // 写入 Token 供 Web 端取出
        stringRedisTemplate.opsForHash().put(REDIS_PREFIX + sceneId, "token", token);

        // --- 第8步：构建登录响应 ---
        return LoginResponse.builder()
                // JWT Token（含 "Bearer " 前缀）
                .token(token)
                // 构建嵌套的用户信息
                .userInfo(LoginResponse.UserInfo.builder()
                        // 用户ID
                        .id(user.getId())
                        // 用户名
                        .username(user.getUsername())
                        // 昵称
                        .name(user.getName())
                        // 角色
                        .role(user.getRole())
                        // 手机号
                        .phone(user.getPhone())
                        // 邮箱
                        .email(user.getEmail())
                        // 头像
                        .avatar(user.getAvatar())
                        // 微信 OpenID
                        .wid(user.getWid())
                        // 创建时间
                        .createtime(user.getCreatetime())
                        // 完成 UserInfo
                        .build())
                // 完成 LoginResponse
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
        // 字符池：大写字母 + 小写字母 + 数字共 62 个
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        // 加密安全的随机数生成器
        SecureRandom random = new SecureRandom();
        // 预分配容量，避免扩容
        StringBuilder sb = new StringBuilder(length);
        // 循环 length 次，每次取一个随机字符
        for (int i = 0; i < length; i++) {
            // 从字符池随机取字符并拼接
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        // 返回随机字符串
        return sb.toString();
    }
}
