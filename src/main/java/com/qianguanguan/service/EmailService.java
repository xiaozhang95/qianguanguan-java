package com.qianguanguan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qianguanguan.entity.User;
import com.qianguanguan.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
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
// 标记为 Spring Bean，由容器管理生命周期
@Service
// Lombok：自动生成包含所有 final 字段的构造器，实现构造器注入
@RequiredArgsConstructor
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
        // 拼接频率限制 key，如 "email_rate:test@qq.com"
        String rateKey = EMAIL_RATE_LIMIT_PREFIX + email;
        // key 存在说明 60 秒内已发过，触发频率限制
        if (stringRedisTemplate.hasKey(rateKey)) {
            // 直接拒绝重复发送
            throw new RuntimeException("验证码已发送，请60秒后再试");
        }

        // --- 第2步：检查邮箱是否已被注册 ---
        // 按邮箱查询用户记录
        User existUser = userMapper.selectOne(
                // 构建类型安全的查询条件
                new LambdaQueryWrapper<User>()
                        // 条件：WHERE email = ?
                        .eq(User::getEmail, email)
        );
        // 查到用户说明邮箱已被注册
        if (existUser != null) {
            // 拒绝发送验证码
            throw new RuntimeException("该邮箱已被注册");
        }

        // --- 第3步：生成 6 位数字验证码 ---
        // 生成 0~999999 随机数并格式化为 6 位（不足补 0）
        String code = String.format("%06d", random.nextInt(1000000));

        // --- 第4步：验证码存入 Redis ---
        // 写入 Redis，5 分钟有效
        stringRedisTemplate.opsForValue().set(
                // Redis Key：email_code:{邮箱}
                EMAIL_CODE_PREFIX + email,
                // 验证码值
                code,
                // 过期时长
                CODE_EXPIRE_MINUTES,
                // 时间单位：分钟
                TimeUnit.MINUTES
        );

        // --- 第5步：设置频率限制标记 ---
        // 写入频率限制标记，60 秒有效
        stringRedisTemplate.opsForValue().set(
                // Redis Key：email_rate:{邮箱}
                rateKey,
                // 任意值（只关心 key 是否存在）
                "1",
                // 过期时长
                RATE_LIMIT_SECONDS,
                // 时间单位：秒
                TimeUnit.SECONDS
        );

        // --- 第6步：构建并发送邮件 ---
        // 创建简单邮件消息对象
        SimpleMailMessage message = new SimpleMailMessage();
        // 发件人 QQ 邮箱
        message.setFrom("89981452@qq.com");
        // 收件人
        message.setTo(email);
        // 邮件主题
        message.setSubject("钱罐罐记账 - 邮箱验证码");
        // 正文含验证码与有效期提示
        message.setText("您的验证码是：" + code + "，有效期为" + CODE_EXPIRE_MINUTES + "分钟，请勿泄露给他人。");
        // 通过 SMTP 发送邮件
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
        // 任一参数为 null 直接判失败，不做无意义的 Redis 查询
        if (email == null || code == null) {
            // 空参视为校验失败
            return false;
        }

        // --- 构建 Redis key ---
        // 拼接验证码 key，如 "email_code:test@qq.com"
        String key = EMAIL_CODE_PREFIX + email;

        // --- 从 Redis 获取存储的验证码 ---
        // 读取 Redis 中存储的验证码
        String storedCode = stringRedisTemplate.opsForValue().get(key);
        // 取不到说明 key 不存在或已过期，验证码无效
        if (storedCode == null) {
            // 校验失败
            return false;
        }

        // --- 立即删除 Redis 中的验证码（关键安全步骤） ---
        // 无论比对结果如何都删除，确保验证码一次性使用、防重放
        stringRedisTemplate.delete(key);

        // --- 比对验证码 ---
        // 精确比对后返回是否一致
        return storedCode.equals(code);
    }
}
