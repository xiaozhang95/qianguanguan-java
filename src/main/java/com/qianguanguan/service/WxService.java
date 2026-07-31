package com.qianguanguan.service;

// --- Jackson JSON 解析 ---
// JsonNode：Jackson 的树模型，可以层级访问 JSON 的任意节点
// ObjectMapper：Jackson 的核心类，用于 JSON → Java 对象的序列化/反序列化
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
// --- 安全配置属性（微信 appId、appSecret） ---
import com.qianguanguan.configuration.SecurityProperties;
// --- Lombok ---
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
// --- Spring Data Redis ---
// StringRedisTemplate：操作 Redis String 类型，用于缓存 access_token
import org.springframework.data.redis.core.StringRedisTemplate;
// --- Spring HTTP 请求/响应 ---
// HttpEntity：封装 HTTP 请求的 Header 和 Body
// HttpHeaders：构建 HTTP 请求头
// HttpMethod：HTTP 方法枚举（GET、POST 等）
// ResponseEntity：封装 HTTP 响应（状态码、响应头、响应体）
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
// --- Spring REST 客户端错误处理 ---
// ClientHttpResponse：HTTP 客户端响应接口
// ResponseErrorHandler：自定义 HTTP 错误处理器
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResponseErrorHandler;
// --- Spring 服务层注解 ---
import org.springframework.stereotype.Service;
// --- Spring REST 模板 ---
// RestTemplate：Spring 提供的同步 HTTP 客户端，用于调用微信 API
import org.springframework.web.client.RestTemplate;

// --- Java 基础库 ---
import java.util.Base64;           // Base64 编解码（图片字节 → base64 字符串）
import java.util.Map;              // 映射表（构建微信 API 请求体）
import java.util.concurrent.TimeUnit; // 时间单位（Redis 过期时间）

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
@Slf4j                            // Lombok：自动生成 log 日志对象
@Service                          // 标记为 Spring Bean
@RequiredArgsConstructor           // 自动生成构造器注入
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
     *   <li>URL：https://api.weixin.qq.com/sns/jscode2session</li>
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
        // 使用字符串拼接构建 GET 请求 URL（参数直接拼接在 URL 上）
        String url = "https://api.weixin.qq.com/sns/jscode2session"
                + "?appid=" + securityProperties.getWx().getAppId()       // 小程序 AppID
                + "&secret=" + securityProperties.getWx().getAppSecret()   // 小程序 AppSecret（密钥）
                + "&js_code=" + code                                      // wx.login() 返回的临时 code
                + "&grant_type=authorization_code";                       // 固定值：授权类型

        // --- 第2步：发送 GET 请求 ---
        // getForObject(url, String.class)：以 GET 方式请求 URL，将响应体转为字符串
        // 微信返回 JSON 格式，如：{"openid":"oABC...","session_key":"xyz..."}
        String response = restTemplate.getForObject(url, String.class);

        // --- 第3步：解析 JSON 响应，提取 openid ---
        try {
            // readTree：将 JSON 字符串解析为 Jackson 树模型
            JsonNode json = objectMapper.readTree(response);
            // 获取 "openid" 字段的值，如果不存在则 asText() 返回空字符串 ""
            String openId = json.get("openid").asText();
            if (openId == null || openId.isEmpty()) {
                // openid 为空说明微信返回了错误（如 errcode=40029 "invalid code"）
                throw new RuntimeException("获取微信openid失败: " + response);
            }
            return openId;
        } catch (Exception e) {
            // 记录日志并抛出业务异常
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
        String cached = stringRedisTemplate.opsForValue().get(ACCESS_TOKEN_KEY);
        if (cached != null && !cached.isEmpty()) {
            // 缓存命中，直接返回（避免调用微信 API，节省调用次数）
            return cached;
        }

        // --- 第2步：缓存未命中，调用微信接口获取 ---
        String url = "https://api.weixin.qq.com/cgi-bin/token"
                + "?grant_type=client_credential"                        // 固定值：客户端凭证
                + "&appid=" + securityProperties.getWx().getAppId()       // 小程序 AppID
                + "&secret=" + securityProperties.getWx().getAppSecret(); // 小程序 AppSecret

        // 发送 GET 请求，微信返回：{"access_token":"...","expires_in":7200}
        String response = restTemplate.getForObject(url, String.class);
        try {
            // 解析 JSON，提取 access_token
            JsonNode json = objectMapper.readTree(response);
            String accessToken = json.get("access_token").asText();
            if (accessToken == null || accessToken.isEmpty()) {
                // 获取失败，记录错误日志
                log.error("获取access_token失败: {}", response);
                throw new RuntimeException("获取微信access_token失败");
            }

            // --- 第3步：存入 Redis 缓存 ---
            // 缓存 7000 秒（比微信实际有效期少 200 秒，确保使用时不过期）
            stringRedisTemplate.opsForValue().set(
                    ACCESS_TOKEN_KEY,      // Key: wx_access_token
                    accessToken,           // Value: access_token 字符串
                    ACCESS_TOKEN_EXPIRE,   // 过期时长
                    TimeUnit.SECONDS       // 时间单位：秒
            );
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
     *   <li>URL：POST https://api.weixin.qq.com/wxa/getwxacodeunlimit</li>
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
        // 委托给 doGetWxaCode，第三个参数 true 表示允许 token 失效时自动重试一次
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
        // getAccessToken() 优先从 Redis 取，缓存未命中才调用微信接口
        String accessToken = getAccessToken();
        // 拼接请求 URL，access_token 作为查询参数
        String url = "https://api.weixin.qq.com/wxa/getwxacodeunlimit?access_token=" + accessToken;

        // --- 第2步：构建 HTTP 请求头 ---
        HttpHeaders headers = new HttpHeaders();
        // 设置 Content-Type 为 application/json（微信接口要求 JSON 请求体）
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

        // --- 第3步：构建请求体（JSON 格式） ---
        // Map.of：Java 9+ 不可变 Map 工厂方法，创建只读 Map
        Map<String, Object> body = Map.of(
                "scene", scene,       // 场景值（如 "login:aB3xK9mQ2pL7rT5w"）
                "page", page,         // 小程序页面路径
                "check_path", false,  // 不检查 page 路径是否存在（避免开发阶段报错）
                "width", 280          // 小程序码宽度（像素），范围 280-1280
        );

        // --- 第4步：创建自定义 RestTemplate（忽略 HTTP 错误状态码） ---
        // 微信接口在失败时返回 HTTP 200 + JSON 错误信息，但某些情况下也可能返回非 200
        // 自定义 RestTemplate 的 errorHandler 不抛出异常，由我们手动判断
        RestTemplate noErrorRestTemplate = new RestTemplate();
        // setErrorHandler：设置自定义错误处理器
        noErrorRestTemplate.setErrorHandler(new ResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                // 永远返回 false：告诉 RestTemplate "没有错误"
                // 这样即使 HTTP 状态码不是 2xx 也不会抛异常
                return false;
            }

            @Override
            public void handleError(ClientHttpResponse response) {
                // 空实现：不处理（因为 hasError 返回 false，此方法不会被调用）
                // 但接口要求实现，所以留空
            }
        });

        try {
            // --- 第5步：序列化请求体为 JSON 字符串 ---
            String requestBody = objectMapper.writeValueAsString(body);
            log.info("获取小程序码请求: url={}, body={}", url, requestBody);

            // --- 第6步：构建 HttpEntity（Header + Body） ---
            HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);

            // --- 第7步：发送 POST 请求 ---
            // exchange：通用 HTTP 请求方法，可以指定 Method、RequestEntity、响应类型
            // byte[].class：期望返回二进制图片数据
            ResponseEntity<byte[]> responseEntity = noErrorRestTemplate.exchange(
                    url,                // 请求 URL
                    HttpMethod.POST,    // POST 方式
                    requestEntity,      // 请求实体（header + body）
                    byte[].class        // 响应体类型：字节数组
            );
            byte[] imageBytes = responseEntity.getBody(); // 获取响应体（图片二进制或错误 JSON）

            // --- 第8步：判断响应是否为图片 ---
            // 成功时微信返回图片二进制（通常 > 1000 字节），失败时返回 JSON 错误信息（通常 < 500 字节）
            if (!responseEntity.getStatusCode().is2xxSuccessful() || imageBytes == null || imageBytes.length < 1000) {
                // 不是图片 → 将字节数组转为字符串（微信返回的 JSON 错误信息）
                String errorJson = imageBytes != null ? new String(imageBytes) : "{}";
                log.error("获取小程序码失败: status={}, body={}", responseEntity.getStatusCode(), errorJson);

                // --- 解析错误码，决定后续处理 ---
                try {
                    JsonNode errorNode = objectMapper.readTree(errorJson);
                    JsonNode errcodeNode = errorNode.get("errcode"); // 微信错误码字段名
                    if (errcodeNode != null) {
                        int errcode = errcodeNode.asInt();

                        // access_token 失效（40001=无效, 40003=过期），且允许重试
                        if ((errcode == 40001 || errcode == 40003) && retryOnAuthError) {
                            log.info("access_token失效，清除缓存后重试");
                            // 删除 Redis 中的缓存 token，下次 getAccessToken() 会重新获取
                            stringRedisTemplate.delete(ACCESS_TOKEN_KEY);
                            // 递归调用，第三个参数 false 防止无限重试
                            return doGetWxaCode(scene, page, false);
                        }

                        // 接口调用频率超限 或 小程序未发布
                        // 45009：API 调用超出限额；85075：小程序未发布
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
                // 不是已知错误，抛出通用异常
                throw new RuntimeException("获取小程序码失败，错误: " + errorJson);
            }

            // --- 第9步：成功获取图片 → Base64 编码返回 ---
            // 编码为 Base64 并拼接 Data URI 前缀，前端可直接用于 <img src="data:image/jpeg;base64,...">
            return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(imageBytes);

        } catch (RuntimeException e) {
            // 重新抛出业务异常，不需要包装
            throw e;
        } catch (Exception e) {
            // 其他未知异常（网络错误、序列化错误等），记录日志后包装为 RuntimeException
            log.error("获取小程序码异常", e);
            throw new RuntimeException("获取小程序码失败");
        }
    }
}
