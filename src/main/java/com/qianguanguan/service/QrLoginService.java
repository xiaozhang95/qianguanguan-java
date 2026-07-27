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
 * 微信扫码登录核心逻辑
 *
 * 流程：
 * 1. Web 端请求生成小程序码，调用微信 getwxacodeunlimit 接口
 * 2. 用户微信扫码 → 自动打开小程序确认页
 * 3. 小程序 wx.login() 后调用 confirmLogin
 * 4. Web 轮询 checkStatus 获取 token
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QrLoginService {

    private static final String REDIS_PREFIX = "qr_login:";
    private static final int SCENE_EXPIRE_SECONDS = 300;
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_CONFIRMED = "confirmed";
    private static final String WXA_PAGE = "pages/qr-login/index";

    private final StringRedisTemplate stringRedisTemplate;
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final SecurityProperties securityProperties;
    private final WxService wxService;

    /**
     * 生成场景码及小程序码
     */
    public Map<String, String> generateScene() {
        String sceneId = generateShortId(16);

        Map<String, String> sceneData = new HashMap<>();
        sceneData.put("status", STATUS_PENDING);
        sceneData.put("token", "");

        stringRedisTemplate.opsForHash().putAll(REDIS_PREFIX + sceneId, sceneData);
        stringRedisTemplate.expire(REDIS_PREFIX + sceneId, SCENE_EXPIRE_SECONDS, TimeUnit.SECONDS);

        String qrImage = wxService.getWxaCode(sceneId, WXA_PAGE);
        if (qrImage == null) {
            throw new RuntimeException("生成小程序码失败");
        }

        Map<String, String> result = new HashMap<>();
        result.put("sceneId", sceneId);
        result.put("qrImage", qrImage);
        return result;
    }

    /**
     * 查询场景码状态，Web 端每 2 秒轮询
     */
    public Map<String, Object> checkStatus(String sceneId) {
        String status = (String) stringRedisTemplate.opsForHash().get(REDIS_PREFIX + sceneId, "status");
        String token = (String) stringRedisTemplate.opsForHash().get(REDIS_PREFIX + sceneId, "token");

        Map<String, Object> result = new HashMap<>();
        if (status == null) {
            result.put("status", "expired");
        } else {
            result.put("status", status);
            if (STATUS_CONFIRMED.equals(status)) {
                result.put("token", token);
                stringRedisTemplate.delete(REDIS_PREFIX + sceneId);
            }
        }
        return result;
    }

    /**
     * 小程序确认登录
     */
    public LoginResponse confirmLogin(String sceneId, String code) {
        String status = (String) stringRedisTemplate.opsForHash().get(REDIS_PREFIX + sceneId, "status");
        if (status == null) {
            throw new RuntimeException("二维码已过期");
        }
        if (STATUS_CONFIRMED.equals(status)) {
            throw new RuntimeException("二维码已被使用");
        }

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

        stringRedisTemplate.opsForHash().put(REDIS_PREFIX + sceneId, "status", STATUS_CONFIRMED);
        stringRedisTemplate.opsForHash().put(REDIS_PREFIX + sceneId, "token", token);

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
                        .wid(user.getWid())
                        .createtime(user.getCreatetime())
                        .build())
                .build();
    }

    private String generateShortId(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}
