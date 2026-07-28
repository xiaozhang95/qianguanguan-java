package com.qianguanguan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.qianguanguan.entity.User;
import com.qianguanguan.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class WechatBindService {

    private static final String REDIS_PREFIX = "wechat_bind:";
    private static final int SCENE_EXPIRE_SECONDS = 300;
    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_CONFIRMED = "confirmed";
    private static final String WXA_PAGE = "pages/bill/index";

    private final StringRedisTemplate stringRedisTemplate;
    private final UserMapper userMapper;
    private final WxService wxService;

    public Map<String, String> generateScene(Long userId) {
        String sceneId = generateShortId(16);
        Map<String, String> sceneData = new HashMap<>();
        sceneData.put("status", STATUS_PENDING);
        sceneData.put("userId", String.valueOf(userId));
        stringRedisTemplate.opsForHash().putAll(REDIS_PREFIX + sceneId, sceneData);
        stringRedisTemplate.expire(REDIS_PREFIX + sceneId, SCENE_EXPIRE_SECONDS, TimeUnit.SECONDS);

        String qrImage = wxService.getWxaCode("bind:" + sceneId, WXA_PAGE);
        if (qrImage == null) {
            throw new RuntimeException("生成小程序码失败");
        }

        Map<String, String> result = new HashMap<>();
        result.put("sceneId", sceneId);
        result.put("qrImage", qrImage);
        return result;
    }

    public Map<String, Object> checkStatus(String sceneId) {
        String status = (String) stringRedisTemplate.opsForHash().get(REDIS_PREFIX + sceneId, "status");
        Map<String, Object> result = new HashMap<>();
        if (status == null) {
            result.put("status", "expired");
        } else {
            result.put("status", status);
            if (STATUS_CONFIRMED.equals(status)) {
                stringRedisTemplate.delete(REDIS_PREFIX + sceneId);
            }
        }
        return result;
    }

    public Map<String, Object> confirmBind(String sceneId, String code) {
        String status = (String) stringRedisTemplate.opsForHash().get(REDIS_PREFIX + sceneId, "status");
        String userId = (String) stringRedisTemplate.opsForHash().get(REDIS_PREFIX + sceneId, "userId");

        if (status == null || userId == null) {
            throw new RuntimeException("二维码已过期");
        }
        if (STATUS_CONFIRMED.equals(status)) {
            throw new RuntimeException("二维码已被使用");
        }

        String openId = wxService.getOpenId(code);

        User existingUser = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getWid, openId)
        );
        if (existingUser != null) {
            throw new RuntimeException("该微信号已绑定其他账号");
        }

        User user = new User();
        user.setId(Long.parseLong(userId));
        user.setWid(openId);
        userMapper.updateById(user);

        stringRedisTemplate.opsForHash().put(REDIS_PREFIX + sceneId, "status", STATUS_CONFIRMED);

        Map<String, Object> result = new HashMap<>();
        result.put("status", STATUS_CONFIRMED);
        result.put("msg", "绑定成功");
        return result;
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
