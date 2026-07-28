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

@Service
@RequiredArgsConstructor
public class EmailService {

    private static final String EMAIL_CODE_PREFIX = "email_code:";
    private static final String EMAIL_RATE_LIMIT_PREFIX = "email_rate:";
    private static final long CODE_EXPIRE_MINUTES = 5;
    private static final long RATE_LIMIT_SECONDS = 60;

    private final JavaMailSender mailSender;
    private final StringRedisTemplate stringRedisTemplate;
    private final UserMapper userMapper;
    private final SecureRandom random = new SecureRandom();

    public void sendVerificationCode(String email) {
        String rateKey = EMAIL_RATE_LIMIT_PREFIX + email;
        if (stringRedisTemplate.hasKey(rateKey)) {
            throw new RuntimeException("验证码已发送，请60秒后再试");
        }

        User existUser = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, email)
        );
        if (existUser != null) {
            throw new RuntimeException("该邮箱已被注册");
        }

        String code = String.format("%06d", random.nextInt(1000000));

        stringRedisTemplate.opsForValue().set(
                EMAIL_CODE_PREFIX + email,
                code,
                CODE_EXPIRE_MINUTES,
                TimeUnit.MINUTES
        );

        stringRedisTemplate.opsForValue().set(
                rateKey,
                "1",
                RATE_LIMIT_SECONDS,
                TimeUnit.SECONDS
        );

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("89981452@qq.com");
        message.setTo(email);
        message.setSubject("钱罐罐记账 - 邮箱验证码");
        message.setText("您的验证码是：" + code + "，有效期为" + CODE_EXPIRE_MINUTES + "分钟，请勿泄露给他人。");
        mailSender.send(message);
    }

    public boolean validate(String email, String code) {
        if (email == null || code == null) {
            return false;
        }
        String key = EMAIL_CODE_PREFIX + email;
        String storedCode = stringRedisTemplate.opsForValue().get(key);
        if (storedCode == null) {
            return false;
        }
        stringRedisTemplate.delete(key);
        return storedCode.equals(code);
    }
}
