package com.qianguanguan.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qianguanguan.configuration.SecurityProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 微信 API 服务
 * <p>
 * 封装微信小程序相关的三个核心接口调用：
 * <ol>
 *   <li><b>jscode2session</b>：用临时 code 换取用户 OpenID（用于登录和绑定）</li>
 *   <li><b>获取 access_token</b>：调用微信服务端 API 的凭证（带 Redis 缓存，自动刷新）</li>
 *   <li><b>getwxacodeunlimit</b>：生成不限数量的小程序码（返回 base64 图片）</li>
 * </ol>
 * <p>
 * access_token 管理策略：
 * <ul>
 *   <li>微信 access_token 有效期 7200 秒（2 小时）</li>
 *   <li>Redis 缓存 7000 秒，留 200 秒缓冲防止边界过期</li>
 *   <li>调用小程序码接口时若返回 40001/40003（token 失效），自动清除缓存重试一次</li>
 * </ul>
 * <p>
 * 参考文档：<a href="https://developers.weixin.qq.com/miniprogram/dev/OpenApiDoc/">微信小程序服务端 API</a>
 */
// Lombok：自动生成 log 日志对象
@Slf4j
// 标记为 Spring Bean
@Service
// 自动生成构造器注入
@RequiredArgsConstructor
public class WxService {

    /** Redis key：存储微信 access_token */
    private static final String ACCESS_TOKEN_KEY = "wx_access_token";
    /**
     * access_token Redis 缓存时间：7000 秒
     * 微信实际有效期 7200 秒，留 200 秒缓冲，防止在使用过程中刚好过期
     */
    private static final long ACCESS_TOKEN_EXPIRE = 7000;

    // --- 依赖注入 ---

    /** 安全配置：读取 yaml 中的 wx.appId、wx.appSecret */
    private final SecurityProperties securityProperties;
    /** RestTemplate：Spring 的 HTTP 客户端，用于调用微信 API（GET / POST） */
    private final RestTemplate restTemplate;
    /** ObjectMapper：Jackson JSON 解析器，将微信返回的 JSON 字符串解析为 JsonNode */
    private final ObjectMapper objectMapper;
    /** Redis 操作：缓存 access_token */
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * 通过临时 code 换取微信用户的 OpenID
     * <p>
     * 调用微信小程序的 <b>jscode2session</b> 接口：
     * <ul>
     *   <li>URL：<a href="https://api.weixin.qq.com/sns/jscode2session">...</a></li>
     *   <li>参数：appid、secret、js_code、grant_type=authorization_code</li>
     *   <li>成功返回：{openid, session_key, unionid(可选)}</li>
     *   <li>失败返回：{errcode, errmsg}</li>
     * </ul>
     * <p>
     * OpenID 是微信用户在<strong>当前小程序</strong>下的唯一标识，
     * 同一用户在不同小程序的 OpenID 不同。
     *
     * @param code 小程序端 wx.login() 返回的临时登录凭证（有效期约 5 分钟）
     * @return 微信用户的 OpenID（如 "oABC123xyz..."）
     * @throws RuntimeException code 无效或微信接口调用失败
     */
    public String getOpenId(String code) {
        // --- 第1步：构建微信 jscode2session 接口 URL ---
        // 字符串拼接 GET 请求 URL，参数直接放查询串
        String url = "https://api.weixin.qq.com/sns/jscode2session"
                // 小程序 AppID
                + "?appid=" + securityProperties.getWx().getAppId()
                // 小程序 AppSecret（密钥）
                + "&secret=" + securityProperties.getWx().getAppSecret()
                // wx.login() 返回的临时 code
                + "&js_code=" + code
                // 固定值：授权类型
                + "&grant_type=authorization_code";

        // --- 第2步：发送 GET 请求 ---
        // getForObject 将响应体转为字符串，微信返回 JSON
        String response = restTemplate.getForObject(url, String.class);

        // --- 第3步：解析 JSON 响应，提取 openid ---
        try {
            // readTree：将 JSON 字符串解析为 Jackson 树模型
            JsonNode json = objectMapper.readTree(response);
            // 取 openid 字段，不存在时 asText() 返回空串
            String openId = json.get("openid").asText();
            // openid 为空说明微信返回错误（如 errcode=40029）
            if (openId == null || openId.isEmpty()) {
                throw new RuntimeException("获取微信openid失败: " + response);
            }
            // 返回微信用户的 OpenID
            return openId;
        } catch (Exception e) {
            // 记录日志
            log.error("微信登录解析失败: {}", response, e);
            throw new RuntimeException("微信登录失败");
        }
    }

    /**
     * 获取微信 access_token（带 Redis 缓存）
     * <p>
     * access_token 是调用微信服务端 API 的全局唯一凭证，获取流程：
     * <ol>
     *   <li>先检查 Redis 缓存，命中则直接返回</li>
     *   <li>缓存未命中则调用微信 /cgi-bin/token 接口获取新 token</li>
     *   <li>将新 token 存入 Redis，设置 7000 秒过期</li>
     * </ol>
     * <p>
     * 微信接口文档要求：access_token 每日调用上限 2000 次，
     * 因此必须缓存，避免频繁调用。
     *
     * @return 微信 access_token（如 "55_abc123..." ～512 位）
     * @throws RuntimeException 微信接口调用失败
     */
    public String getAccessToken() {
        // --- 第1步：检查 Redis 缓存 ---
        // 从 Redis 读取缓存的 token
        String cached = stringRedisTemplate.opsForValue().get(ACCESS_TOKEN_KEY);
        if (cached != null && !cached.isEmpty()) {
            // 缓存命中直接返回，避免调用微信 API 节省调用次数
            return cached;
        }

        // --- 第2步：缓存未命中，调用微信接口获取 ---
        // 拼接获取 token 的接口 URL
        String url = "https://api.weixin.qq.com/cgi-bin/token"
                // 固定值：客户端凭证
                + "?grant_type=client_credential"
                // 小程序 AppID
                + "&appid=" + securityProperties.getWx().getAppId()
                // 小程序 AppSecret
                + "&secret=" + securityProperties.getWx().getAppSecret();

        // 发送 GET 请求，返回 {"access_token":"...","expires_in":7200}
        String response = restTemplate.getForObject(url, String.class);
        try {
            // 解析 JSON 响应
            JsonNode json = objectMapper.readTree(response);
            // 提取 access_token 字段
            String accessToken = json.get("access_token").asText();
            // 获取失败
            if (accessToken == null || accessToken.isEmpty()) {
                // 记录错误日志
                log.error("获取access_token失败: {}", response);
                throw new RuntimeException("获取微信access_token失败");
            }

            // --- 第3步：存入 Redis 缓存 ---
            // 缓存 7000 秒（比微信有效期少 200 秒，确保使用中不过期）
            stringRedisTemplate.opsForValue().set(
                    // Key: wx_access_token
                    ACCESS_TOKEN_KEY,
                    // Value: access_token 字符串
                    accessToken,
                    // 过期时长
                    ACCESS_TOKEN_EXPIRE,
                    // 时间单位：秒
                    TimeUnit.SECONDS
            );
            // 返回新获取的 token
            return accessToken;
        } catch (Exception e) {
            log.error("解析access_token失败: {}", response, e);
            throw new RuntimeException("获取微信access_token失败");
        }
    }

    /**
     * 调用微信 getwxacodeunlimit 接口生成小程序码
     * <p>
     * 这是一个<b>不限数量</b>的小程序码生成接口（区别于有限数量的 createWXAQRCode）：
     * <ul>
     *   <li>URL：POST <a href="https://api.weixin.qq.com/wxa/getwxacodeunlimit">...</a></li>
     *   <li>请求体：{scene, page, check_path, width}</li>
     *   <li>成功返回：二进制图片数据</li>
     *   <li>失败返回：JSON 错误信息</li>
     * </ul>
     * <p>
     * <b>重要限制</b>：此接口要求小程序已发布上线，开发阶段无法使用（会返回 errcode 85075）。
     * <p>
     * token 失效自动重试：如果微信返回 40001（access_token 无效）或 40003（access_token 过期），
     * 会清除 Redis 缓存并重新获取 token 后重试一次。
     *
     * @param scene 场景值（最大 32 个字符），小程序通过 onLoad(options.scene) 获取
     * @param page  小程序页面路径（如 "pages/bill/index"），必须是已发布的页面
     * @return base64 编码的图片字符串（含 data:image/jpeg;base64, 前缀），可直接用于 &lt;img&gt; 标签
     */
    public String getWxaCode(String scene, String page) {
        // 委托执行，true 表示 token 失效时自动重试一次
        return doGetWxaCode(scene, page, true);
    }

    /**
     * 实际执行小程序码生成逻辑（支持 token 失效重试）
     *
     * @param scene           场景值
     * @param page            小程序页面路径
     * @param retryOnAuthError 当 access_token 失效时是否自动重试（防止无限递归）
     * @return base64 图片字符串
     */
    private String doGetWxaCode(String scene, String page, boolean retryOnAuthError) {
        // --- 第1步：获取 access_token ---
        // getAccessToken() 优先从 Redis 取，未命中才调微信接口
        String accessToken = getAccessToken();
        // 拼接请求 URL，token 作为查询参数
        String url = "https://api.weixin.qq.com/wxa/getwxacodeunlimit?access_token=" + accessToken;

        // --- 第2步：构建 HTTP 请求头 ---
        // 创建请求头对象
        HttpHeaders headers = new HttpHeaders();
        // 微信接口要求 JSON 请求体
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        // --- 第3步：构建请求体（JSON 格式） ---
        // Map.of：Java 9+ 不可变 Map，创建只读请求体
        Map<String, Object> body = Map.of(
                // 场景值（如 "login:aB3xK9mQ2pL7rT5w"）
                "scene", scene,
                // 小程序页面路径
                "page", page,
                // 不检查 page 路径是否存在（避免开发阶段报错）
                "check_path", false,
                // 小程序码宽度（像素），范围 280-1280
                "width", 280
        );

        // --- 第4步：创建自定义 RestTemplate（忽略 HTTP 错误状态码） ---
        // 微信失败时可能返回非 200，由 errorHandler 忽略后手动判断
        RestTemplate noErrorRestTemplate = getRestTemplate();

        try {
            // --- 第5步：序列化请求体为 JSON 字符串 ---
            // 请求体对象序列化为 JSON
            String requestBody = objectMapper.writeValueAsString(body);
            // 打印请求日志便于排查
            log.info("获取小程序码请求: url={}, body={}", url, requestBody);

            // --- 第6步：构建 HttpEntity（Header + Body） ---
            // 组装请求头与请求体
            HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);

            // --- 第7步：发送 POST 请求 ---
            // exchange：通用 HTTP 请求方法
            ResponseEntity<byte[]> responseEntity = noErrorRestTemplate.exchange(
                    // 请求 URL
                    url,
                    // POST 方式
                    HttpMethod.POST,
                    // 请求实体（header + body）
                    requestEntity,
                    // 期望响应二进制图片数据
                    byte[].class
            );
            // 获取响应体（图片二进制或错误 JSON）
            byte[] imageBytes = responseEntity.getBody();

            // --- 第8步：判断响应是否为图片 ---
            // 非2xx/空/过短均视为失败（错误 JSON 通常<500字节）
            if (!responseEntity.getStatusCode().is2xxSuccessful() || imageBytes == null || imageBytes.length < 1000) {
                // 将字节数组转为 JSON 错误文本
                String errorJson = imageBytes != null ? new String(imageBytes) : "{}";
                // 记录失败日志
                log.error("获取小程序码失败: status={}, body={}", responseEntity.getStatusCode(), errorJson);

                // --- 解析错误码，决定后续处理 ---
                try {
                    // 解析错误 JSON
                    JsonNode errorNode = objectMapper.readTree(errorJson);
                    // 微信错误码字段名
                    JsonNode errcodeNode = errorNode.get("errcode");
                    // 存在错误码才处理
                    if (errcodeNode != null) {
                        // 转为整数便于比较
                        int errcode = errcodeNode.asInt();

                        // access_token 失效（40001=无效, 40003=过期），且允许重试
                        if ((errcode == 40001 || errcode == 40003) && retryOnAuthError) {
                            log.info("access_token失效，清除缓存后重试");
                            // 删除缓存的token，下次重新获取
                            stringRedisTemplate.delete(ACCESS_TOKEN_KEY);
                            // 递归重试，false 防止无限递归
                            return doGetWxaCode(scene, page, false);
                        }

                        // 接口调用频率超限 或 小程序未发布（45009=超限；85075=未发布）
                        if (errcode == 45009 || errcode == 85075) {
                            throw new RuntimeException(
                                    "微信扫码登录暂不可用：小程序尚未发布上线，请先在微信公众平台提交审核并发布小程序");
                        }
                    }
                } catch (RuntimeException e) {
                    // 重新抛出已知的业务异常（如小程序未发布）
                    throw e;
                } catch (Exception ignored) {
                    // JSON 解析失败等非关键异常，忽略，继续抛出通用错误
                }
                // 未知错误抛出通用异常
                throw new RuntimeException("获取小程序码失败，错误: " + errorJson);
            }

            // --- 第9步：成功获取图片 → Base64 编码返回 ---
            // Base64 编码加 Data URI 前缀，前端可直接用于 <img>
            return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(imageBytes);

        } catch (RuntimeException e) {
            // 重新抛出业务异常，不需要包装
            throw e;
        } catch (Exception e) {
            // 记录未知异常（网络、序列化等）
            log.error("获取小程序码异常", e);
            throw new RuntimeException("获取小程序码失败");
        }
    }

    private static @NonNull RestTemplate getRestTemplate() {
        // 创建独立实例，避免污染全局 bean
        RestTemplate noErrorRestTemplate = new RestTemplate();
        // 设置自定义错误处理器
        noErrorRestTemplate.setErrorHandler(new ResponseErrorHandler() {
            @Override
            public boolean hasError(@NonNull ClientHttpResponse response) {
                // 永远返回 false：非 2xx 状态码也不抛异常，由调用方手动判断
                return false;
            }

            @Override
            public void handleError(@NonNull URI url, @NonNull HttpMethod method, @NonNull ClientHttpResponse response) {
                // 空实现：hasError 恒为 false，此方法不会被调用，但接口要求实现
            }
        });
        return noErrorRestTemplate;
    }
}
