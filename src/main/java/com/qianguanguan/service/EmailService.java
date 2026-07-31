package com.qianguanguan.service;

// --- MyBatis-Plus 查询条件构造器 ---
// 用于构建类型安全的 SQL WHERE 条件（如 eq、like）
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
// --- 用户实体类 ---
// User 包含 email 字段，用于检查邮箱是否已被注册
import com.qianguanguan.entity.User;
// --- 用户表 Mapper ---
// MyBatis-Plus 提供的数据访问接口，用于查询用户表
import com.qianguanguan.mapper.UserMapper;
// --- Lombok：自动生成构造器注入 ---
// @RequiredArgsConstructor 为所有 final 字段生成构造器
import lombok.RequiredArgsConstructor;
// --- Spring Data Redis ---
// StringRedisTemplate：操作 Redis 的 String 类型键值对
import org.springframework.data.redis.core.StringRedisTemplate;
// --- Spring Mail：发送纯文本邮件 ---
// SimpleMailMessage：构建简单邮件（发件人、收件人、主题、正文）
import org.springframework.mail.SimpleMailMessage;
// --- Spring Mail：JavaMailSender 邮件发送器 ---
// 底层封装了 JavaMail，自动读取 spring.mail.* 配置
import org.springframework.mail.javamail.JavaMailSender;
// --- Spring 服务层组件注解 ---
// @Service 标记该类为业务逻辑 Bean，会被 Spring 自动扫描并管理
import org.springframework.stereotype.Service;

// --- Java 安全随机数（线程安全） ---
// SecureRandom：使用加密强随机数算法，比 Random 更安全
import java.security.SecureRandom;
// --- 时间单位枚举 ---
// TimeUnit：定义 Redis 过期时间的单位（MINUTES、SECONDS 等）
import java.util.concurrent.TimeUnit;

/**
 * 邮箱验证码服务
 * <p>
 * 负责发送和校验邮箱验证码，用于注册、修改邮箱等场景。
 * <p>
 * 安全策略：
 * <ul>
 *   <li>60 秒内同一邮箱不可重复发送（频率限制）</li>
 *   <li>验证码 5 分钟有效</li>
 *   <li>校验成功后立即删除，防止重复使用</li>
 *   <li>6 位随机数字，使用 SecureRandom 生成</li>
 * </ul>
 */
@Service                         // 标记为 Spring Bean，由容器管理生命周期
@RequiredArgsConstructor          // Lombok：自动生成包含所有 final 字段的构造器，实现构造器注入
public class EmailService {

    /** 邮箱验证码 Redis key 前缀，完整 key = email_code:xxx@qq.com */
    private static final String EMAIL_CODE_PREFIX = "email_code:";
    /** 发送频率限制 Redis key 前缀，完整 key = email_rate:xxx@qq.com */
    private static final String EMAIL_RATE_LIMIT_PREFIX = "email_rate:";
    /** 验证码有效期：5 分钟（超过此时间验证码作废） */
    private static final long CODE_EXPIRE_MINUTES = 5;
    /** 发送频率限制：60 秒（同一邮箱两次发送的最小间隔） */
    private static final long RATE_LIMIT_SECONDS = 60;

    // --- 依赖注入（通过 @RequiredArgsConstructor 自动注入） ---

    /** JavaMailSender：Spring Boot 自动配置的邮件发送器，读取 yaml 中 spring.mail.* 配置 */
    private final JavaMailSender mailSender;
    /** StringRedisTemplate：操作 Redis String 类型，用于存储验证码和频率限制标记 */
    private final StringRedisTemplate stringRedisTemplate;
    /** UserMapper：MyBatis-Plus 用户表 Mapper，用于检查邮箱是否已被注册 */
    private final UserMapper userMapper;
    /** SecureRandom：加密安全的随机数生成器，用于生成 6 位验证码 */
    private final SecureRandom random = new SecureRandom();

    /**
     * 发送邮箱验证码
     * <p>
     * 完整流程：
     * <ol>
     *   <li>检查频率限制：60 秒内是否已发送过</li>
     *   <li>检查邮箱是否已被注册</li>
     *   <li>生成 6 位随机数字验证码</li>
     *   <li>验证码存入 Redis（5 分钟有效）</li>
     *   <li>设置频率限制标记（60 秒）</li>
     *   <li>通过 QQ 邮箱 SMTP 发送邮件</li>
     * </ol>
     *
     * @param email 目标邮箱地址（如 test@qq.com）
     * @throws RuntimeException 频率限制未过、邮箱已被注册
     */
    public void sendVerificationCode(String email) {
        // --- 第1步：频率限制检查 ---
        // 构建频率限制的 Redis key，如 "email_rate:test@qq.com"
        String rateKey = EMAIL_RATE_LIMIT_PREFIX + email;
        // hasKey：检查 Redis 中是否存在该 key
        // 存在说明 60 秒内已经发过验证码，直接拒绝
        if (stringRedisTemplate.hasKey(rateKey)) {
            throw new RuntimeException("验证码已发送，请60秒后再试");
        }

        // --- 第2步：检查邮箱是否已被注册 ---
        // selectOne：查询符合条件的第一条记录
        // LambdaQueryWrapper：使用 lambda 表达式构建 WHERE 条件，避免字段名硬编码
        User existUser = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getEmail, email)  // WHERE email = ?
        );
        // 如果查询到用户，说明邮箱已存在，拒绝发送
        if (existUser != null) {
            throw new RuntimeException("该邮箱已被注册");
        }

        // --- 第3步：生成 6 位数字验证码 ---
        // SecureRandom.nextInt(1000000)：生成 0 ~ 999999 的随机整数
        // String.format("%06d", ...)：格式化为 6 位，不足补 0（如 123 → "000123"）
        String code = String.format("%06d", random.nextInt(1000000));

        // --- 第4步：验证码存入 Redis ---
        // Key: email_code:xxx@qq.com， Value: 6位数字验证码
        // 过期时间 5 分钟，超时后 Redis 自动删除
        stringRedisTemplate.opsForValue().set(
                EMAIL_CODE_PREFIX + email,  // Redis Key
                code,                        // 验证码值
                CODE_EXPIRE_MINUTES,         // 过期时长
                TimeUnit.MINUTES             // 时间单位：分钟
        );

        // --- 第5步：设置频率限制标记 ---
        // Key: email_rate:xxx@qq.com， Value: "1"
        // 过期时间 60 秒，期间再次发送会被第1步拦截
        stringRedisTemplate.opsForValue().set(
                rateKey,                    // Redis Key
                "1",                        // 任意值（只关心 key 是否存在）
                RATE_LIMIT_SECONDS,         // 过期时长
                TimeUnit.SECONDS            // 时间单位：秒
        );

        // --- 第6步：构建并发送邮件 ---
        // SimpleMailMessage：Spring 提供的简单邮件消息类
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("89981452@qq.com");               // 发件人 QQ 邮箱
        message.setTo(email);                              // 收件人
        message.setSubject("钱罐罐记账 - 邮箱验证码");       // 邮件主题
        // 邮件正文，包含验证码和有效期提示
        message.setText("您的验证码是：" + code + "，有效期为" + CODE_EXPIRE_MINUTES + "分钟，请勿泄露给他人。");
        // 调用 JavaMailSender 发送邮件（底层通过 SMTP 协议与 QQ 邮箱服务器通信）
        mailSender.send(message);
    }

    /**
     * 校验邮箱验证码
     * <p>
     * 校验逻辑：
     * <ol>
     *   <li>参数非空校验</li>
     *   <li>从 Redis 取出存储的验证码</li>
     *   <li>无论是否匹配，立即删除 Redis 中的验证码（一次性使用）</li>
     *   <li>比对用户输入与存储的验证码</li>
     * </ol>
     * <p>
     * 注意：先删除再返回结果，确保验证码只能使用一次，防止重放攻击。
     *
     * @param email 邮箱地址（作为 Redis key 的一部分）
     * @param code  用户输入的验证码
     * @return true=验证通过 / false=验证失败（过期、不存在、不匹配）
     */
    public boolean validate(String email, String code) {
        // --- 参数非空校验 ---
        // 任一参数为 null 直接返回 false，不做无意义的 Redis 查询
        if (email == null || code == null) {
            return false;
        }

        // --- 构建 Redis key ---
        // 如 "email_code:test@qq.com"
        String key = EMAIL_CODE_PREFIX + email;

        // --- 从 Redis 获取存储的验证码 ---
        String storedCode = stringRedisTemplate.opsForValue().get(key);
        // 如果取不到（key 不存在或已过期），说明验证码无效
        if (storedCode == null) {
            return false;
        }

        // --- 立即删除 Redis 中的验证码（关键安全步骤） ---
        // 无论比对结果如何都删除，确保：
        // 1. 正确验证码只能使用一次
        // 2. 错误尝试也会消耗验证码，增加暴力破解成本
        stringRedisTemplate.delete(key);

        // --- 比对验证码 ---
        // 使用 equals 精确比对（验证码是数字，不区分大小写无意义）
        return storedCode.equals(code);
    }
}
