package com.qianguanguan.service;

// --- MyBatis-Plus 查询条件构造器 ---
// LambdaQueryWrapper：构建类型安全的 WHERE 条件
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
// --- 用户实体 ---
import com.qianguanguan.entity.User;
// --- 用户表 Mapper ---
import com.qianguanguan.mapper.UserMapper;
// --- Lombok ---
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
// --- Spring Data Redis ---
// StringRedisTemplate：操作 Redis String 类型键值对
import org.springframework.data.redis.core.StringRedisTemplate;
// --- Spring 服务层注解 ---
import org.springframework.stereotype.Service;

// --- Java 基础库 ---
import java.security.SecureRandom;  // 加密安全的随机数生成器
import java.util.HashMap;           // 哈希表
import java.util.Map;               // 映射表接口
import java.util.concurrent.TimeUnit; // Redis 过期时间单位

/**
 * 微信绑定服务
 * <p>
 * 实现已登录用户绑定微信号的跨端流程：
 * <ol>
 *   <li><b>Web 端生成绑定码</b>：已登录用户调用 generateScene(userId)，
 *       服务端生成 16 位场景 ID + 小程序码，将 userId 存入 Redis</li>
 *   <li><b>手机微信扫码</b>：微信扫描小程序码后打开小程序</li>
 *   <li><b>小程序确认绑定</b>：调用 confirmBind(sceneId, code)，
 *       通过微信 code 换取 OpenID，更新用户表的 wid 字段</li>
 *   <li><b>Web 轮询检测</b>：Web 端轮询 checkStatus()，检测到 confirmed 后提示绑定成功</li>
 * </ol>
 * <p>
 * 与 {@link QrLoginService} 的区别：
 * <ul>
 *   <li>QrLoginService：用户未登录 → 扫码 → 自动注册/登录</li>
 *   <li>WechatBindService：用户已登录 → 扫码 → 绑定微信到已有账号</li>
 * </ul>
 * <p>
 * Redis 数据结构（Hash）：
 * <pre>
 *   Key:   wechat_bind:{场景ID}
 *   Field: status → "pending" | "confirmed"
 *   Field: userId → 用户ID（由 Web 端传入）
 *   过期：  300 秒（5 分钟）
 * </pre>
 */
@Slf4j                            // Lombok：自动生成 log 日志对象
@Service                          // 标记为 Spring Bean
@RequiredArgsConstructor           // 自动生成构造器注入
public class WechatBindService {

    /** Redis key 前缀，完整 key = wechat_bind:{场景ID} */
    private static final String REDIS_PREFIX = "wechat_bind:";
    /** 场景码有效期：300 秒（5 分钟），超时未扫码则过期 */
    private static final int SCENE_EXPIRE_SECONDS = 300;
    /** 场景状态：等待扫码 */
    private static final String STATUS_PENDING = "pending";
    /** 场景状态：已确认绑定 */
    private static final String STATUS_CONFIRMED = "confirmed";
    /** 小程序码跳转页面路径 */
    private static final String WXA_PAGE = "pages/bill/index";

    // --- 依赖注入 ---

    /** Redis 操作：存储场景状态 */
    private final StringRedisTemplate stringRedisTemplate;
    /** 用户表 Mapper：更新用户的 wid 字段 */
    private final UserMapper userMapper;
    /** 微信 API 服务：获取小程序码和 OpenID */
    private final WxService wxService;

    /**
     * 生成微信绑定场景码
     * <p>
     * 与扫码登录的 generateScene 类似，但额外存储了 userId，
     * 以便小程序确认绑定后知道是哪个用户发起的绑定请求。
     *
     * @param userId 当前已登录用户的 ID
     * @return Map 包含 sceneId（场景ID）和 qrImage（base64 小程序码图片）
     * @throws RuntimeException 微信接口调用失败
     */
    public Map<String, String> generateScene(Long userId) {
        // --- 第1步：生成 16 位随机场景 ID ---
        String sceneId = generateShortId(16);

        // --- 第2步：构建场景数据（存入 Redis Hash） ---
        Map<String, String> sceneData = new HashMap<>();
        sceneData.put("status", STATUS_PENDING);        // 初始状态：等待扫码
        sceneData.put("userId", String.valueOf(userId)); // 记录发起绑定请求的用户ID

        // --- 第3步：写入 Redis ---
        // putAll：批量写入 Hash 的多个 field-value
        stringRedisTemplate.opsForHash().putAll(REDIS_PREFIX + sceneId, sceneData);
        // expire：设置整个 Hash 的过期时间（300 秒后自动删除）
        stringRedisTemplate.expire(REDIS_PREFIX + sceneId, SCENE_EXPIRE_SECONDS, TimeUnit.SECONDS);

        // --- 第4步：调用微信接口生成小程序码 ---
        // scene 参数格式："bind:场景ID"，小程序解析后调用 confirmBind
        String qrImage = wxService.getWxaCode("bind:" + sceneId, WXA_PAGE);
        if (qrImage == null) {
            throw new RuntimeException("生成小程序码失败");
        }

        // --- 第5步：返回场景ID和小程序码 ---
        Map<String, String> result = new HashMap<>();
        result.put("sceneId", sceneId);
        result.put("qrImage", qrImage);
        return result;
    }

    /**
     * 查询绑定状态（Web 端轮询接口）
     * <p>
     * Web 端调用此接口检测用户是否已在手机上确认绑定：
     * <ul>
     *   <li>返回 "pending"：继续等待</li>
     *   <li>返回 "confirmed"：绑定成功，删除 Redis 数据</li>
     *   <li>返回 "expired"：场景码已过期</li>
     * </ul>
     *
     * @param sceneId 场景 ID
     * @return Map 包含 status 字段
     */
    public Map<String, Object> checkStatus(String sceneId) {
        // --- 从 Redis Hash 获取状态 ---
        // opsForHash().get(key, field)：获取 Hash 中指定 field 的值
        // 返回值是 Object 类型，需强制转换为 String
        String status = (String) stringRedisTemplate.opsForHash().get(REDIS_PREFIX + sceneId, "status");

        // --- 构建响应 ---
        Map<String, Object> result = new HashMap<>();
        if (status == null) {
            // Redis key 不存在（已过期或已被 checkStatus 删除）
            result.put("status", "expired");
        } else {
            result.put("status", status);
            // 如果已确认，删除 Redis 数据（一次性消费）
            if (STATUS_CONFIRMED.equals(status)) {
                stringRedisTemplate.delete(REDIS_PREFIX + sceneId);
            }
        }
        return result;
    }

    /**
     * 小程序端确认绑定微信
     * <p>
     * 用户在手机上扫码后点击"确认绑定"调用此接口：
     * <ol>
     *   <li>校验场景码状态（是否存在、是否过期、是否已被使用）</li>
     *   <li>通过微信 code 换取 OpenID</li>
     *   <li>检查该微信号是否已绑定其他账号</li>
     *   <li>更新用户表的 wid 字段（完成绑定）</li>
     *   <li>更新 Redis 状态为 confirmed</li>
     * </ol>
     *
     * @param sceneId 场景 ID（从小程序码解析得到）
     * @param code    微信小程序 wx.login() 返回的临时登录凭证
     * @return Map 包含 status 和 msg
     * @throws RuntimeException 场景码过期/已使用、微信号已被绑定
     */
    public Map<String, Object> confirmBind(String sceneId, String code) {
        // --- 第1步：从 Redis 获取场景数据 ---
        // 同时获取 status 和 userId 两个 field
        String status = (String) stringRedisTemplate.opsForHash().get(REDIS_PREFIX + sceneId, "status");
        String userId = (String) stringRedisTemplate.opsForHash().get(REDIS_PREFIX + sceneId, "userId");

        // --- 第2步：校验场景码有效性 ---
        if (status == null || userId == null) {
            // 任一 field 为 null 说明 Redis key 不存在或数据不完整
            throw new RuntimeException("二维码已过期");
        }
        if (STATUS_CONFIRMED.equals(status)) {
            // 已被使用过，防止重复绑定
            throw new RuntimeException("二维码已被使用");
        }

        // --- 第3步：通过微信 code 换取 OpenID ---
        // code 是 wx.login() 的返回值，用于向微信服务器换取用户唯一标识
        String openId = wxService.getOpenId(code);

        // --- 第4步：检查该微信号是否已绑定其他账号 ---
        // 一个微信号只能绑定一个账号，防止重复绑定
        User existingUser = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getWid, openId)  // WHERE wid = ?
        );
        if (existingUser != null) {
            // 该 OpenID 已经关联了其他用户，不能重复绑定
            throw new RuntimeException("该微信号已绑定其他账号");
        }

        // --- 第5步：更新用户 wid 字段（核心绑定操作） ---
        // MyBatis-Plus updateById：只更新非 null 字段（使用字段级策略）
        User user = new User();
        user.setId(Long.parseLong(userId)); // 设置主键（userId 从 Redis 读取，是 String 类型，需转换）
        user.setWid(openId);                // 设置微信 OpenID
        userMapper.updateById(user);        // UPDATE user SET wid = ? WHERE id = ?

        // --- 第6步：更新 Redis 场景状态为 confirmed ---
        // Web 端的轮询会检测到这个状态变化，提示用户绑定成功
        stringRedisTemplate.opsForHash().put(REDIS_PREFIX + sceneId, "status", STATUS_CONFIRMED);

        // --- 第7步：构建成功响应 ---
        Map<String, Object> result = new HashMap<>();
        result.put("status", STATUS_CONFIRMED);
        result.put("msg", "绑定成功");
        return result;
    }

    /**
     * 生成指定长度的随机字符串（用于场景 ID）
     * <p>
     * 字符池包含大小写字母和数字共 62 个字符，
     * 使用 SecureRandom 保证加密安全性。
     *
     * @param length 字符串长度
     * @return 随机字符串
     */
    private String generateShortId(int length) {
        // 字符池：A-Z（26）+ a-z（26）+ 0-9（10）= 62 个字符
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        SecureRandom random = new SecureRandom(); // 加密安全的随机数生成器
        StringBuilder sb = new StringBuilder(length); // 预分配容量，避免扩容开销
        // 循环 length 次，每次从字符池随机取一个字符
        for (int i = 0; i < length; i++) {
            // random.nextInt(chars.length())：生成 0 ~ 61 的均匀分布随机索引
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString(); // 如 "aB3xK9mQ2pL7rT5w"
    }
}
