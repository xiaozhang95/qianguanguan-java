package com.qianguanguan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qianguanguan.configuration.SecurityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class WxService {

    private final SecurityProperties securityProperties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

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
}
