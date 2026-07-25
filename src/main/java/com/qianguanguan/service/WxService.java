package com.qianguanguan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qianguanguan.configuration.SecurityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 微信相关服务
 * <p>
 * 封装微信小程序登录、access_token 管理、小程序码生成等能力
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WxService {

    private static final String ACCESS_TOKEN_KEY = "wx_access_token";
    private static final long ACCESS_TOKEN_EXPIRE = 7000;

    private final SecurityProperties securityProperties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 通过临时 code 换取微信用户的 openId
     * 用于小程序 wx.login() 后的身份识别
     */
    public String getOpenId(String code) {
        String url = "https://api.weixin.qq.com/sns/jscode2session"
                + "?appid=" + securityProperties.getWx().getAppId()
                + "&secret=" + securityProperties.getWx().getAppSecret()
                + "&js_code=" + code
                + "&grant_type=authorization_code";

        String response = restTemplate.getForObject(url, String.class);
        try {
            JsonNode json = objectMapper.readTree(response);
            String openId = json.get("openid").asText();
            if (openId == null || openId.isEmpty()) {
                throw new RuntimeException("获取微信openid失败: " + response);
            }
            return openId;
        } catch (Exception e) {
            log.error("微信登录解析失败: {}", response, e);
            throw new RuntimeException("微信登录失败");
        }
    }

    /**
     * 获取微信 access_token（带 Redis 缓存）
     * 有效期 7200 秒，缓存 7000 秒留缓冲，自动续期
     */
    public String getAccessToken() {
        String cached = stringRedisTemplate.opsForValue().get(ACCESS_TOKEN_KEY);
        if (cached != null && !cached.isEmpty()) {
            return cached;
        }

        String url = "https://api.weixin.qq.com/cgi-bin/token"
                + "?grant_type=client_credential"
                + "&appid=" + securityProperties.getWx().getAppId()
                + "&secret=" + securityProperties.getWx().getAppSecret();

        String response = restTemplate.getForObject(url, String.class);
        try {
            JsonNode json = objectMapper.readTree(response);
            String accessToken = json.get("access_token").asText();
            if (accessToken == null || accessToken.isEmpty()) {
                log.error("获取access_token失败: {}", response);
                throw new RuntimeException("获取微信access_token失败");
            }
            stringRedisTemplate.opsForValue().set(ACCESS_TOKEN_KEY, accessToken, ACCESS_TOKEN_EXPIRE, TimeUnit.SECONDS);
            return accessToken;
        } catch (Exception e) {
            log.error("解析access_token失败: {}", response, e);
            throw new RuntimeException("获取微信access_token失败");
        }
    }

    /**
     * 调用微信 getwxacodeunlimit 生成小程序码（base64 图片）
     * 注意：要求小程序已发布上线，开发阶段无法使用
     *
     * @param scene 场景值，扫码后传入小程序 onLoad(options.scene)，最长 32 字符
     * @param page  小程序页面路径，如 "pages/qr-login/index"
     * @return base64 图片 data URL，失败返回 null
     */
    public String getWxaCode(String scene, String page) {
        String accessToken = getAccessToken();
        String url = "https://api.weixin.qq.com/wxa/getwxacodeunlimit?access_token=" + accessToken;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "scene", scene,
                "page", page,
                "check_path", false,
                "width", 280
        );

        try {
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            byte[] imageBytes = restTemplate.postForObject(url, request, byte[].class);

            // 成功时返回图片二进制，失败时返回 JSON 错误信息（通常 < 1000 字节）
            if (imageBytes == null || imageBytes.length < 1000) {
                String errorJson = imageBytes != null ? new String(imageBytes) : "null";
                log.error("获取小程序码失败: {}", errorJson);
                return null;
            }

            return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(imageBytes);
        } catch (Exception e) {
            log.error("获取小程序码异常", e);
            return null;
        }
    }
}
