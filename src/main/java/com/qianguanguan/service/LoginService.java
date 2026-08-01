package com.qianguanguan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qianguanguan.configuration.SecurityProperties;
import com.qianguanguan.constant.JwtClaims;
import com.qianguanguan.entity.User;
import com.qianguanguan.mapper.UserMapper;
import com.qianguanguan.model.LoginRequest;
import com.qianguanguan.model.LoginResponse;
import com.qianguanguan.model.RegisterRequest;
import com.qianguanguan.util.Jwt;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.concurrent.TimeUnit;

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
    // 用户表操作
    private final UserMapper userMapper;
    // BCrypt 密码编码器
    private final PasswordEncoder passwordEncoder;
    // 图形验证码服务
    private final CaptchaService captchaService;
    // 邮箱验证码服务
    private final EmailService emailService;
    // JWT 和微信配置
    private final SecurityProperties securityProperties;
    // Redis 操作
    private final StringRedisTemplate stringRedisTemplate;
    // 微信 API 服务
    private final WxService wxService;

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
        // 当前时间作为签发时间
        Date now = new Date();
        // 由配置的密钥字节构建 HMAC-SHA256 签名 Key
        Key key = Keys.hmacShaKeyFor(securityProperties.getJwt().getSecret().getBytes());
        // 过期时刻 = 签发时间 + 配置的有效期
        Date expirationDate = new Date(now.getTime() + securityProperties.getJwt().getExpiration());

        // --- 2. 生成 JWT Token ---
        // 签发 JWT Token
        String token = Jwt.generateToken(
                // 存入用户ID
                user.getId(),
                // 存入用户角色
                user.getRole(),
                // 签名密钥
                key,
                // 签发时间
                now,
                // 过期时间
                expirationDate
        );

        // --- 3. 将签发时间戳缓存到 Redis ---
        // 缓存签发时间戳，供 JWT 过滤器校验
        stringRedisTemplate.opsForValue().set(
                // Redis Key：user_{用户ID}
                "user_" + user.getId(),
                // 签发时间戳（转为字符串）
                String.valueOf(now.getTime()),
                // TTL = Token 有效期 + 24 小时，预留刷新期
                securityProperties.getJwt().getExpiration() + 24 * 60 * 60 * 1000,
                // 时间单位：毫秒
                TimeUnit.MILLISECONDS
        );

        // --- 4. 构建响应 ---
        // 构建登录响应
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
        // 图形验证码校验失败则拒绝
        if (!captchaService.validate(request.getCaptchaKey(), request.getCaptchaCode())) {
            // 终止登录流程
            throw new RuntimeException("验证码错误或已过期");
        }

        // --- 2. 根据邮箱查找用户 ---
        // 按邮箱查询用户
        User user = userMapper.selectOne(
                // 构建类型安全的查询条件
                new LambdaQueryWrapper<User>()
                        // 条件：email = 输入邮箱
                        .eq(User::getEmail, request.getEmail())
        );

        // --- 3. 校验用户是否存在 ---
        // 查不到用户
        if (user == null) {
            // 不明确提示"用户不存在"，防止枚举攻击
            throw new RuntimeException("邮箱或密码错误");
        }

        // --- 4. 校验密码 ---
        // BCrypt 比对密码，不匹配则拒绝
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            // 与"用户不存在"文案一致，防止暴露账号是否存在
            throw new RuntimeException("邮箱或密码错误");
        }

        // --- 5. 构建登录响应 ---
        // 生成 JWT 并返回登录响应
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
        // 通过微信 jscode2session 接口换取 OpenID
        String openId = wxService.getOpenId(code);

        // --- 2. 根据 OpenID 查找系统用户 ---
        // 按 OpenID 查询用户
        User user = userMapper.selectOne(
                // 条件：wid = OpenID
                new LambdaQueryWrapper<User>().eq(User::getWid, openId)
        );

        // --- 3. 新用户自动注册 ---
        // 未注册过则自动创建
        if (user == null) {
            // 创建新用户实体
            user = new User();
            // 用户名：wx_ + OpenID前8位
            user.setUsername("wx_" + openId.substring(0, 8));
            // 密码 = BCrypt(OpenID)
            user.setPassword(passwordEncoder.encode(openId));
            // 昵称
            user.setName("钱罐罐记账用户_" + openId.substring(0, 4));
            // 普通用户角色
            user.setRole("USER");
            // 启用状态
            user.setStatus(1);
            // 绑定微信 OpenID
            user.setWid(openId);
            // 插入数据库
            userMapper.insert(user);
        }

        // --- 4. 签发 Token ---
        // 生成 JWT 并返回登录响应
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
        // 图形验证码校验失败则拒绝
        if (!captchaService.validate(request.getCaptchaKey(), request.getCaptchaCode())) {
            // 终止注册流程
            throw new RuntimeException("验证码错误或已过期");
        }

        // --- 2. 校验邮箱验证码 ---
        // 邮箱验证码校验失败则拒绝
        if (!emailService.validate(request.getEmail(), request.getEmailCode())) {
            // 终止注册流程
            throw new RuntimeException("邮箱验证码错误或已过期");
        }

        // --- 3. 检查邮箱是否已被注册 ---
        // 按邮箱查询是否已注册
        User existEmailUser = userMapper.selectOne(
                // 条件：email = 输入邮箱
                new LambdaQueryWrapper<User>().eq(User::getEmail, request.getEmail())
        );
        // 已存在相同邮箱
        if (existEmailUser != null) {
            // 拒绝重复注册
            throw new RuntimeException("该邮箱已被注册");
        }

        // --- 4. 创建新用户 ---
        // 创建用户实体
        User user = new User();
        // 用户名 = 邮箱
        user.setUsername(request.getEmail());
        // BCrypt 加密密码
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        // 昵称取邮箱 @ 前缀，如 "test@qq.com" → "test"
        user.setName(request.getEmail().substring(0, request.getEmail().indexOf('@')));
        // 邮箱即登录名
        user.setEmail(request.getEmail());
        // 默认普通用户
        user.setRole("USER");
        // 默认启用
        user.setStatus(1);
        // 写入数据库
        userMapper.insert(user);

        // --- 5. 注册成功自动登录 ---
        // 注册后直接签发 Token 完成登录
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
        // 由配置的密钥字节构建 HMAC-SHA256 签名 Key
        Key key = Keys.hmacShaKeyFor(securityProperties.getJwt().getSecret().getBytes());

        // --- 2. 宽松解析 Token（允许已过期） ---
        // 宽松解析：过期也不抛异常，仍能提取 Claims
        Claims claims = Jwt.parserTokenLenient(token, key);

        // --- 3. 从 Claims 中提取用户ID ---
        // 取出旧 Token 中的用户ID
        Long id = claims.get(JwtClaims.ID, Long.class);

        // --- 4. 校验用户是否存在且未被禁用 ---
        // 按ID查询用户
        User user = userMapper.selectById(id);
        // 用户不存在或已被禁用
        if (user == null || user.getStatus() != 1) {
            // 拒绝签发新 Token
            throw new RuntimeException("用户不存在或已禁用");
        }

        // --- 5. 重新签发 Token ---
        // 基于有效用户重新签发新 Token
        return buildLoginResponse(user);
    }
}
