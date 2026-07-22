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
    private final SecurityProperties securityProperties;
    private final StringRedisTemplate stringRedisTemplate;

    public LoginResponse.UserInfo getUserInfo(Long userId) {
        return userService.getUserInfo(userId);
    }

    public LoginResponse login(LoginRequest request) {
        if (!captchaService.validate(request.getCaptchaKey(), request.getCaptchaCode())) {
            throw new RuntimeException("验证码错误或已过期");
        }

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, request.getUsername())
        );

        if (user == null) {
            throw new RuntimeException("用户名或密码错误");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("用户名或密码错误");
        }

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
                        .createtime(user.getCreatetime())
                        .build())
                .build();
    }

    public void register(RegisterRequest request) {
        if (!captchaService.validate(request.getCaptchaKey(), request.getCaptchaCode())) {
            throw new RuntimeException("验证码错误或已过期");
        }

        User existUser = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, request.getUsername())
        );
        if (existUser != null) {
            throw new RuntimeException("用户名已存在");
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setName(request.getUsername());
        user.setRole("USER");
        user.setStatus(1);
        userMapper.insert(user);
    }
}
