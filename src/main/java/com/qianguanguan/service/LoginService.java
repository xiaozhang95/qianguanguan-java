package com.qianguanguan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qianguanguan.configuration.SecurityProperties;
import com.qianguanguan.entity.User;
import com.qianguanguan.mapper.UserMapper;
import com.qianguanguan.model.LoginRequest;
import com.qianguanguan.model.LoginResponse;
import com.qianguanguan.model.RegisterRequest;
import com.qianguanguan.util.Jwt;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class LoginService {

    private final UserMapper userMapper;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final CaptchaService captchaService;
    private final EmailService emailService;
    private final SecurityProperties securityProperties;
    private final StringRedisTemplate stringRedisTemplate;
    private final WxService wxService;

    private LoginResponse buildLoginResponse(User user) {
        Date now = new Date();
        Key key = Keys.hmacShaKeyFor(securityProperties.getJwt().getSecret().getBytes());
        Date expirationDate = new Date(now.getTime() + securityProperties.getJwt().getExpiration());

        String token = Jwt.generateToken(user.getId(), user.getRole(), key, now, expirationDate);

        stringRedisTemplate.opsForValue().set(
                "user_" + user.getId(),
                String.valueOf(now.getTime()),
                securityProperties.getJwt().getExpiration(),
                TimeUnit.MILLISECONDS
        );

        return LoginResponse.builder()
                .token(token)
                .userInfo(LoginResponse.UserInfo.builder()
                        .id(user.getId())
                        .username(user.getUsername())
                        .name(user.getName())
                        .role(user.getRole())
                        .phone(user.getPhone())
                        .email(user.getEmail())
                        .avatar(user.getAvatar())
                        .createtime(user.getCreatetime())
                        .build())
                .build();
    }

    public LoginResponse.UserInfo getUserInfo(Long userId) {
        return userService.getUserInfo(userId);
    }

    public LoginResponse login(LoginRequest request) {
        if (!captchaService.validate(request.getCaptchaKey(), request.getCaptchaCode())) {
            throw new RuntimeException("验证码错误或已过期");
        }

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getEmail, request.getEmail())
        );

        if (user == null) {
            throw new RuntimeException("邮箱或密码错误");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("邮箱或密码错误");
        }

        return buildLoginResponse(user);
    }

    public LoginResponse loginByWid(String code) {
        String openId = wxService.getOpenId(code);

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getWid, openId)
        );

        if (user == null) {
            user = new User();
            user.setUsername("wx_" + openId.substring(0, 8));
            user.setPassword(passwordEncoder.encode(openId));
            user.setName("钱罐罐用户_" + openId.substring(0, 4));
            user.setRole("USER");
            user.setStatus(1);
            user.setWid(openId);
            userMapper.insert(user);
        }

        return buildLoginResponse(user);
    }

    public LoginResponse register(RegisterRequest request) {
        if (!captchaService.validate(request.getCaptchaKey(), request.getCaptchaCode())) {
            throw new RuntimeException("验证码错误或已过期");
        }

        if (!emailService.validate(request.getEmail(), request.getEmailCode())) {
            throw new RuntimeException("邮箱验证码错误或已过期");
        }

        User existEmailUser = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, request.getEmail())
        );
        if (existEmailUser != null) {
            throw new RuntimeException("该邮箱已被注册");
        }

        User user = new User();
        user.setUsername(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setName(request.getEmail().substring(0, request.getEmail().indexOf('@')));
        user.setEmail(request.getEmail());
        user.setRole("USER");
        user.setStatus(1);
        userMapper.insert(user);

        return buildLoginResponse(user);
    }
}
