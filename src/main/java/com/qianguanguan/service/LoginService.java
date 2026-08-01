package com.qianguanguan.service;

// --- MyBatis-Plus 查询条件 ---
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
// --- 安全配置属性（JWT 密钥、过期时间） ---
import com.qianguanguan.configuration.SecurityProperties;
// --- JWT Claims 字段名常量 ---
import com.qianguanguan.constant.JwtClaims;
// --- 用户实体 ---
import com.qianguanguan.entity.User;
// --- 用户表 Mapper ---
import com.qianguanguan.mapper.UserMapper;
// --- 请求/响应模型 ---
import com.qianguanguan.model.LoginRequest;
import com.qianguanguan.model.LoginResponse;
import com.qianguanguan.model.RegisterRequest;
// --- JWT 工具类 ---
import com.qianguanguan.util.Jwt;
// --- JWT 解析 ---
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.Keys;
// --- Lombok ---
import lombok.RequiredArgsConstructor;
// --- Redis 操作 ---
import org.springframework.data.redis.core.StringRedisTemplate;
// --- 密码加密 ---
import org.springframework.security.crypto.password.PasswordEncoder;
// --- Spring ---
import org.springframework.stereotype.Service;

// --- Java 基础库 ---
import java.security.Key;          // JWT 签名密钥
import java.util.Date;             // 日期
import java.util.concurrent.TimeUnit; // 时间单位

/**
 * 登录业务服务
 * <p>
 * 处理邮箱+密码登录、微信小程序登录、用户注册、Token 刷新等认证相关逻辑。
 * 登录成功后生成 JWT Token 并缓存到 Redis，用于后续请求的身份校验。
 */
@Service
@RequiredArgsConstructor
public class LoginService {

    // --- 依赖注入 ---
    private final UserMapper userMapper;               // 用户表操作
    private final PasswordEncoder passwordEncoder;      // BCrypt 密码编码器
    private final CaptchaService captchaService;        // 图形验证码服务
    private final EmailService emailService;            // 邮箱验证码服务
    private final SecurityProperties securityProperties; // JWT 和微信配置
    private final StringRedisTemplate stringRedisTemplate; // Redis 操作
    private final WxService wxService;                  // 微信 API 服务

    /**
     * 构建登录响应（生成 JWT Token + 缓存到 Redis）
     * <p>
     * 这是所有登录方式统一使用的私有方法：
     * <ol>
     *   <li>生成当前时间戳和过期时间</li>
     *   <li>使用 HMAC-SHA256 签名算法生成 JWT Token</li>
     *   <li>将 Token 签发时间戳缓存到 Redis，用于后续 JWT 过滤器校验</li>
     *   <li>构建并返回包含 Token 和用户信息的 LoginResponse</li>
     * </ol>
     *
     * @param user 已认证的用户实体
     * @return 包含 JWT Token 和 UserInfo 的登录响应
     */
    private LoginResponse buildLoginResponse(User user) {
        // --- 1. 准备 JWT 生成参数 ---
        Date now = new Date(); // 当前时间作为签发时间
        // 从配置中获取 JWT 密钥，通过 HMAC-SHA256 算法创建签名 Key
        Key key = Keys.hmacShaKeyFor(securityProperties.getJwt().getSecret().getBytes());
        // 过期时间 = 当前时间 + 配置的过期时长（毫秒）
        Date expirationDate = new Date(now.getTime() + securityProperties.getJwt().getExpiration());

        // --- 2. 生成 JWT Token ---
        String token = Jwt.generateToken(
                user.getId(),   // 存入用户ID
                user.getRole(), // 存入用户角色
                key,            // 签名密钥
                now,            // 签发时间
                expirationDate  // 过期时间
        );

        // --- 3. 将签发时间戳缓存到 Redis ---
        // Key: user_{用户ID}  Value: 签发时间戳（毫秒）
        // 过期时间 = JWT 过期时间 + 24 小时，即 Redis 缓存比 Token 多活 24 小时
        // 这样即使用户 Token 过期，仍可在宽松期内刷新 Token
        stringRedisTemplate.opsForValue().set(
                "user_" + user.getId(),                 // Redis Key
                String.valueOf(now.getTime()),          // 签发时间戳（转为字符串）
                securityProperties.getJwt().getExpiration() + 24 * 60 * 60 * 1000, // TTL
                TimeUnit.MILLISECONDS                   // 时间单位：毫秒
        );

        // --- 4. 构建响应 ---
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
                        .wid(user.getWid())        // 微信 OpenID
                        .createtime(user.getCreatetime())
                        .build())
                .build();
    }

    /**
     * 邮箱 + 密码 + 图形验证码登录
     * <p>
     * 步骤：
     * <ol>
     *   <li>校验图形验证码</li>
     *   <li>根据邮箱查找用户</li>
     *   <li>BCrypt 密码比对</li>
     *   <li>生成 JWT Token</li>
     * </ol>
     *
     * @param request 登录请求（邮箱、密码、验证码）
     * @return 登录响应
     */
    public LoginResponse login(LoginRequest request) {
        // --- 1. 校验图形验证码 ---
        if (!captchaService.validate(request.getCaptchaKey(), request.getCaptchaCode())) {
            throw new RuntimeException("验证码错误或已过期");
        }

        // --- 2. 根据邮箱查找用户 ---
        // LambdaQueryWrapper：类型安全的查询条件构建
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getEmail, request.getEmail()) // WHERE email = ?
        );

        // --- 3. 校验用户是否存在 ---
        if (user == null) {
            throw new RuntimeException("邮箱或密码错误"); // 不明确提示"用户不存在"，防止枚举攻击
        }

        // --- 4. 校验密码 ---
        // matches(明文, BCrypt密文)：BCrypt 比对，不会反解出原文
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("邮箱或密码错误");
        }

        // --- 5. 构建登录响应 ---
        return buildLoginResponse(user);
    }

    /**
     * 微信小程序一键登录（code 换 OpenID → 自动注册/登录）
     * <p>
     * 流程：
     * <ol>
     *   <li>调用微信 jscode2session 接口，用临时 code 换取 OpenID</li>
     *   <li>根据 OpenID 查找系统用户</li>
     *   <li>若不存在则自动注册新用户（用户名、昵称基于 OpenID 生成）</li>
     *   <li>签发 JWT Token</li>
     * </ol>
     *
     * @param code 微信小程序 wx.login() 返回的临时登录凭证
     * @return 登录响应
     */
    public LoginResponse loginByWid(String code) {
        // --- 1. 用 code 向微信服务器换取 OpenID ---
        String openId = wxService.getOpenId(code);

        // --- 2. 根据 OpenID 查找系统用户 ---
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getWid, openId) // WHERE wid = ?
        );

        // --- 3. 新用户自动注册 ---
        if (user == null) {
            user = new User();
            user.setUsername("wx_" + openId.substring(0, 8));      // 用户名：wx_ + OpenID前8位
            user.setPassword(passwordEncoder.encode(openId));       // 密码 = BCrypt(OpenID)
            user.setName("钱罐罐记账用户_" + openId.substring(0, 4)); // 昵称
            user.setRole("USER");                                   // 普通用户角色
            user.setStatus(1);                                      // 启用状态
            user.setWid(openId);                                    // 绑定微信 OpenID
            userMapper.insert(user);                                // 插入数据库
        }

        // --- 4. 签发 Token ---
        return buildLoginResponse(user);
    }

    /**
     * 用户注册（邮箱 + 密码 + 图形验证码 + 邮箱验证码）
     * <p>
     * 双重验证码校验确保安全性，邮箱同时作为登录用户名。
     *
     * @param request 注册请求
     * @return 登录响应（注册成功自动登录）
     */
    public LoginResponse register(RegisterRequest request) {
        // --- 1. 校验图形验证码 ---
        if (!captchaService.validate(request.getCaptchaKey(), request.getCaptchaCode())) {
            throw new RuntimeException("验证码错误或已过期");
        }

        // --- 2. 校验邮箱验证码 ---
        if (!emailService.validate(request.getEmail(), request.getEmailCode())) {
            throw new RuntimeException("邮箱验证码错误或已过期");
        }

        // --- 3. 检查邮箱是否已被注册 ---
        User existEmailUser = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, request.getEmail())
        );
        if (existEmailUser != null) {
            throw new RuntimeException("该邮箱已被注册");
        }

        // --- 4. 创建新用户 ---
        User user = new User();
        user.setUsername(request.getEmail());                            // 用户名 = 邮箱
        user.setPassword(passwordEncoder.encode(request.getPassword())); // BCrypt 加密密码
        // 昵称取邮箱 @ 前面的部分，如 "test@qq.com" → "test"
        user.setName(request.getEmail().substring(0, request.getEmail().indexOf('@')));
        user.setEmail(request.getEmail());
        user.setRole("USER");  // 默认普通用户
        user.setStatus(1);     // 默认启用
        userMapper.insert(user);

        // --- 5. 注册成功自动登录 ---
        return buildLoginResponse(user);
    }

    /**
     * 刷新 Token（接受已过期的 Token，签发新 Token）
     * <p>
     * 使用宽松解析模式（允许过期），从旧 Token 中提取用户ID，
     * 验证用户状态后重新签发新 Token。
     *
     * @param token 旧的 JWT Token（可能已过期）
     * @return 新的登录响应（含新 Token）
     */
    public LoginResponse refreshToken(String token) {
        // --- 1. 获取 JWT 签名密钥 ---
        Key key = Keys.hmacShaKeyFor(securityProperties.getJwt().getSecret().getBytes());

        // --- 2. 宽松解析 Token（允许已过期） ---
        // parserTokenLenient：过期不抛异常，仍可提取 Claims 中的内容
        Claims claims = Jwt.parserTokenLenient(token, key);

        // --- 3. 从 Claims 中提取用户ID ---
        Long id = claims.get(JwtClaims.ID, Long.class);

        // --- 4. 校验用户是否存在且未被禁用 ---
        User user = userMapper.selectById(id);
        if (user == null || user.getStatus() != 1) {
            throw new RuntimeException("用户不存在或已禁用");
        }

        // --- 5. 重新签发 Token ---
        return buildLoginResponse(user);
    }
}
